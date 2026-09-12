package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.Content
import com.example.api.GeminiApiService
import com.example.api.GenerateContentRequest
import com.example.api.GenerationConfig
import com.example.api.InlineData
import com.example.api.Part
import com.example.api.RetrofitClient
import com.example.api.ThinkingConfig
import com.example.audio.AudioRecorder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.util.UUID
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONObject

@Entity(tableName = "transcriptions")
data class TranscriptionRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val audioUri: String? = null,
    val summary: String? = null,
    val category: String? = null,
    val speakerName: String? = null
)

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private fun getAuthSafe(): FirebaseAuth? {
        return try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var audioRecorder: AudioRecorder? = null
    private var repository: com.example.db.TranscriptionRepository? = null
    
    init {
        val firebaseAuth = getAuthSafe()
        if (firebaseAuth != null) {
            try {
                firebaseAuth.addAuthStateListener { authState ->
                    val user = authState.currentUser
                    if (user != null) {
                        _uiState.value = _uiState.value.copy(
                            isAuthenticated = true,
                            isAnonymous = user.isAnonymous,
                            userEmail = if (user.isAnonymous) "Anonymous Preview User" else user.email ?: "Anonymous Preview User"
                        )
                        loadTranscriptions()
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isAuthenticated = false,
                            isAnonymous = true,
                            userEmail = null
                        )
                        signInAnonymously()
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAuthenticated = true,
                    isAnonymous = true,
                    userEmail = "Offline Preview User"
                )
            }
        } else {
            _uiState.value = _uiState.value.copy(
                isAuthenticated = true,
                isAnonymous = true,
                userEmail = "Offline Preview User"
            )
        }
    }

    fun initDatabase(context: Context) {
        if (repository == null) {
            val database = com.example.db.AppDatabase.getDatabase(context)
            val repo = com.example.db.TranscriptionRepository(database.transcriptionDao())
            repository = repo
            viewModelScope.launch {
                repo.allTranscriptions.collect { list ->
                    _uiState.value = _uiState.value.copy(history = list)
                }
            }
            try {
                if (getAuthSafe()?.currentUser != null) {
                    loadTranscriptions()
                }
            } catch (e: Exception) {
                // Firebase not initialized, fallback to local only
            }
        }
    }

    private fun signInAnonymously() {
        val firebaseAuth = getAuthSafe()
        if (firebaseAuth != null) {
            try {
                firebaseAuth.signInAnonymously()
                    .addOnSuccessListener { result ->
                        val user = result.user
                        _uiState.value = _uiState.value.copy(
                            isAuthenticated = true,
                            isAnonymous = true,
                            userEmail = user?.email ?: "Anonymous Preview User"
                        )
                        loadTranscriptions()
                    }
                    .addOnFailureListener { exception ->
                        _uiState.value = _uiState.value.copy(
                            isAuthenticated = true,
                            isAnonymous = true,
                            userEmail = "Offline Preview User"
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAuthenticated = true,
                    isAnonymous = true,
                    userEmail = "Offline Preview User"
                )
            }
        } else {
            _uiState.value = _uiState.value.copy(
                isAuthenticated = true,
                isAnonymous = true,
                userEmail = "Offline Preview User"
            )
        }
    }

    fun initAudioRecorder(context: Context) {
        if (audioRecorder == null) {
            audioRecorder = AudioRecorder(context)
        }
    }

    fun startRecording() {
        if (audioRecorder?.startRecording() == true) {
            _uiState.value = _uiState.value.copy(isRecording = true)
        }
    }

    fun stopRecording() {
        _uiState.value = _uiState.value.copy(isRecording = false, isLoading = true)
        val file = audioRecorder?.stopRecording()
        if (file != null && file.exists()) {
            transcribeAudio(file)
        } else {
            _uiState.value = _uiState.value.copy(isLoading = false, error = "Failed to record audio")
        }
    }

    fun transcribeSelectedAudio(context: Context, uri: Uri) {
        _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = "Preparing selected file...", progress = 0.1f)
        transcribeAudioUri(context, uri)
    }

    fun transcribeAudioUri(context: Context, uri: Uri) {
        _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = "Reading audio bytes...", progress = 0.3f)
        viewModelScope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.use { it.readBytes() }
                if (bytes == null || bytes.isEmpty()) {
                    _uiState.value = _uiState.value.copy(isLoading = false, statusMessage = null, progress = null, error = "Failed to read audio file.")
                    return@launch
                }
                
                // Save a local copy in cache to ensure playback availability later
                val localFile = File(context.cacheDir, "imported_${System.currentTimeMillis()}.aac")
                localFile.writeBytes(bytes)
                val localUriString = Uri.fromFile(localFile).toString()
                
                transcribeAudioBytes(bytes, "audio/aac", localUriString)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, statusMessage = null, progress = null, error = e.message)
            }
        }
    }

    private fun transcribeAudio(file: File) {
        _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = "Reading recorded audio file...", progress = 0.3f)
        try {
            if (file.exists()) {
                val bytes = file.readBytes()
                val localUriString = Uri.fromFile(file).toString()
                transcribeAudioBytes(bytes, "audio/aac", localUriString)
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false, statusMessage = null, progress = null, error = "Audio file does not exist.")
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isLoading = false, statusMessage = null, progress = null, error = e.message)
        }
    }

    private fun transcribeAudioBytes(bytes: ByteArray, mimeType: String = "audio/aac", audioUriString: String? = null) {
        _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = "Encoding audio for Gemini...", progress = 0.5f)
        viewModelScope.launch {
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                val base64Audio = Base64.encodeToString(bytes, Base64.NO_WRAP)
                
                val prompt = "Please transcribe this audio. Identify the different speakers and include timestamps for when each speaker speaks. Output the result with speaker labels clearly formatted as a script, for example:\n[00:12] Speaker A: Hello\n[00:15] Speaker B: Hi there"
                
                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(
                            parts = listOf(
                                Part(text = prompt),
                                Part(
                                    inlineData = InlineData(
                                        mimeType = mimeType,
                                        data = base64Audio
                                    )
                                )
                            )
                        )
                    )
                )

                _uiState.value = _uiState.value.copy(statusMessage = "Uploading & Transcribing (Gemini AI)...", progress = 0.8f)

                val response = RetrofitClient.service.generateContentStream(
                    model = "gemini-3.5-flash",
                    apiKey = apiKey,
                    request = request
                )
                
                var accumulatedText = ""
                var userId = "local_user"
                try {
                    userId = getAuthSafe()?.currentUser?.uid ?: "local_user"
                } catch (e: Exception) {}
                val transcriptionId = UUID.randomUUID().toString()
                var lastSaveTime = System.currentTimeMillis()
                
                withContext(Dispatchers.IO) {
                    response.byteStream().bufferedReader().use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            if (line!!.startsWith("data: ")) {
                                try {
                                    val jsonStr = line!!.substring(6)
                                    val chunk = JSONObject(jsonStr)
                                    val candidates = chunk.optJSONArray("candidates")
                                    if (candidates != null && candidates.length() > 0) {
                                        val content = candidates.getJSONObject(0).optJSONObject("content")
                                        val parts = content?.optJSONArray("parts")
                                        if (parts != null && parts.length() > 0) {
                                            val textPart = parts.getJSONObject(0).optString("text", "")
                                            accumulatedText += textPart
                                            
                                            _uiState.value = _uiState.value.copy(
                                                lastTranscription = accumulatedText,
                                                progress = 0.8f
                                            )
                                            
                                            val currentTime = System.currentTimeMillis()
                                            if (currentTime - lastSaveTime > 2000) {
                                                lastSaveTime = currentTime
                                                val record = TranscriptionRecord(
                                                    id = transcriptionId,
                                                    userId = userId,
                                                    text = accumulatedText,
                                                    audioUri = audioUriString
                                                )
                                                repository?.insert(record)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    // ignore parsing error for partial chunk
                                }
                            }
                        }
                    }
                }
                
                val resultText = accumulatedText.ifEmpty { "No transcription found" }
                
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Generating AI Summary...",
                    progress = 0.9f,
                    lastTranscription = resultText
                )

                val summaryPrompt = "Please provide a brief, AI-powered summary of the following transcription:\n\n$resultText"
                val summaryRequest = GenerateContentRequest(
                    contents = listOf(
                        Content(parts = listOf(Part(text = summaryPrompt)))
                    )
                )

                val summaryResponse = RetrofitClient.service.generateContent(
                    model = "gemini-3.5-flash",
                    apiKey = apiKey,
                    request = summaryRequest
                )

                val summaryResultText = summaryResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "No summary generated"

                _uiState.value = _uiState.value.copy(
                    isLoading = false, 
                    statusMessage = null,
                    progress = null,
                    lastSummary = summaryResultText
                )
                
                // Final save overriding the same transcription ID
                val finalRecord = TranscriptionRecord(
                    id = transcriptionId,
                    userId = userId,
                    text = resultText,
                    audioUri = audioUriString,
                    summary = summaryResultText
                )
                repository?.insert(finalRecord)
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, statusMessage = null, progress = null, error = e.message)
            }
        }
    }

    fun askComplexQuestion(question: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = "Querying Gemini (High Thinking)...", progress = 0.5f)
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(
                            parts = listOf(Part(text = question))
                        )
                    ),
                    generationConfig = GenerationConfig(
                        thinkingConfig = ThinkingConfig(thinkingLevel = "HIGH")
                    )
                )

                val response = RetrofitClient.service.generateContent(
                    model = "gemini-3.1-pro-preview",
                    apiKey = apiKey,
                    request = request
                )
                
                val resultText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "No answer found"
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    statusMessage = null,
                    progress = null,
                    lastAnswer = resultText
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, statusMessage = null, progress = null, error = e.message)
            }
        }
    }

    fun updateCategory(record: TranscriptionRecord, newCategory: String) {
        viewModelScope.launch {
            val updatedRecord = record.copy(category = newCategory)
            repository?.insert(updatedRecord)
        }
    }

    fun updateSpeakerName(record: TranscriptionRecord, newSpeakerName: String) {
        viewModelScope.launch {
            val updatedRecord = record.copy(speakerName = newSpeakerName)
            repository?.insert(updatedRecord)
            
            try {
                if (getAuthSafe()?.currentUser != null) {
                    firestore.collection("transcriptions")
                        .document(updatedRecord.id)
                        .set(updatedRecord)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun deleteTranscriptions(ids: List<String>) {
        viewModelScope.launch {
            repository?.deleteTranscriptions(ids)
            
            try {
                if (getAuthSafe()?.currentUser != null) {
                    ids.forEach { id ->
                        firestore.collection("transcriptions")
                            .document(id)
                            .delete()
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun syncTranscriptions() {
        loadTranscriptions()
    }

    private fun saveTranscription(text: String, audioUriString: String? = null, summary: String? = null) {
        var user: com.google.firebase.auth.FirebaseUser? = null
        try {
            user = getAuthSafe()?.currentUser
        } catch (e: Exception) {
            // Firebase not initialized
        }
        val userId = user?.uid ?: "local_user"
        val record = TranscriptionRecord(userId = userId, text = text, audioUri = audioUriString, summary = summary)
        
        viewModelScope.launch {
            repository?.insert(record)
        }

        if (user != null) {
            try {
                firestore.collection("transcriptions")
                    .document(record.id)
                    .set(record)
            } catch (e: Exception) {
                // Ignore gracefully as it is already stored in local Room DB
            }
        }
    }

    private fun loadTranscriptions() {
        var user: com.google.firebase.auth.FirebaseUser? = null
        try {
            user = getAuthSafe()?.currentUser
        } catch (e: Exception) {
            return
        }
        if (user == null) return
        
        try {
            firestore.collection("transcriptions")
                .whereEqualTo("userId", user.uid)
                .get()
                .addOnSuccessListener { snapshot ->
                    val list = snapshot.documents.mapNotNull { it.toObject(TranscriptionRecord::class.java) }
                    viewModelScope.launch {
                        list.forEach { record ->
                            repository?.insert(record)
                        }
                    }
                }
        } catch (e: Exception) {
            // Ignore gracefully
        }
    }
    
    fun exportTranscriptionToJson(context: Context, uri: Uri, record: TranscriptionRecord) {
        viewModelScope.launch {
            try {
                val json = org.json.JSONObject()
                json.put("id", record.id)
                json.put("timestamp", record.timestamp)
                json.put("text", record.text)
                if (record.summary != null) {
                    json.put("summary", record.summary)
                }
                if (record.category != null) {
                    json.put("category", record.category)
                }
                if (record.speakerName != null) {
                    json.put("speakerName", record.speakerName)
                }
                
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toString(4).toByteArray())
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to export JSON: ${e.message}")
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun setThemeMode(mode: ThemeMode) {
        _uiState.value = _uiState.value.copy(themeMode = mode)
    }
}

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

data class UiState(
    val isAuthenticated: Boolean = false,
    val isAnonymous: Boolean = true,
    val userEmail: String? = null,
    val isRecording: Boolean = false,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val progress: Float? = null,
    val lastTranscription: String? = null,
    val lastSummary: String? = null,
    val lastAnswer: String? = null,
    val history: List<TranscriptionRecord> = emptyList(),
    val error: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)
