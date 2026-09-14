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
    val speakerName: String? = null,
    val modelName: String? = null
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

    private var customApiKey: String = ""
    private var openRouterApiKey: String = ""
    private var groqApiKey: String = ""
    private var webClientId: String = ""
    private var aiProvider: AiProvider = AiProvider.GEMINI

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var repository: com.example.db.TranscriptionRepository? = null

    fun initApiKey(context: Context) {
        val prefs = context.getSharedPreferences("transcribe_prefs", Context.MODE_PRIVATE)
        customApiKey = prefs.getString("custom_api_key", "") ?: ""
        openRouterApiKey = prefs.getString("openrouter_api_key", "") ?: ""
        groqApiKey = prefs.getString("groq_api_key", "") ?: ""
        webClientId = prefs.getString("custom_web_client_id", "") ?: ""
        val providerName = prefs.getString("ai_provider", AiProvider.GEMINI.name) ?: AiProvider.GEMINI.name
        aiProvider = try { AiProvider.valueOf(providerName) } catch (e: Exception) { AiProvider.GEMINI }

        _uiState.value = _uiState.value.copy(
            customApiKey = customApiKey,
            openRouterApiKey = openRouterApiKey,
            groqApiKey = groqApiKey,
            webClientId = webClientId,
            aiProvider = aiProvider
        )
    }

    fun saveWebClientId(context: Context, id: String) {
        webClientId = id.trim()
        val prefs = context.getSharedPreferences("transcribe_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("custom_web_client_id", webClientId).apply()
        _uiState.value = _uiState.value.copy(webClientId = webClientId)
    }

    fun saveCustomApiKey(context: Context, key: String) {
        customApiKey = key.trim()
        val prefs = context.getSharedPreferences("transcribe_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("custom_api_key", customApiKey).apply()
        _uiState.value = _uiState.value.copy(customApiKey = customApiKey)
    }

    fun saveOpenRouterApiKey(context: Context, key: String) {
        openRouterApiKey = key.trim()
        val prefs = context.getSharedPreferences("transcribe_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("openrouter_api_key", openRouterApiKey).apply()
        _uiState.value = _uiState.value.copy(openRouterApiKey = openRouterApiKey)
    }

    fun saveGroqApiKey(context: Context, key: String) {
        groqApiKey = key.trim()
        val prefs = context.getSharedPreferences("transcribe_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("groq_api_key", groqApiKey).apply()
        _uiState.value = _uiState.value.copy(groqApiKey = groqApiKey)
    }

    fun setAiProvider(context: Context, provider: AiProvider) {
        aiProvider = provider
        val prefs = context.getSharedPreferences("transcribe_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("ai_provider", provider.name).apply()
        _uiState.value = _uiState.value.copy(aiProvider = provider)
    }

    fun getActiveApiKey(): String {
        return when (aiProvider) {
            AiProvider.OPENROUTER -> openRouterApiKey
            AiProvider.GROQ -> groqApiKey
            AiProvider.GEMINI -> if (customApiKey.isNotBlank()) customApiKey else BuildConfig.GEMINI_API_KEY
        }
    }
    
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
            insertSampleTranscriptions(context)
            try {
                if (getAuthSafe()?.currentUser != null) {
                    loadTranscriptions()
                }
            } catch (e: Exception) {
                // Firebase not initialized, fallback to local only
            }
        }
    }

    private fun insertSampleTranscriptions(context: Context) {
        val prefs = context.getSharedPreferences("transcribe_prefs", Context.MODE_PRIVATE)
        val inserted = prefs.getBoolean("samples_inserted_v2", false)
        if (!inserted) {
            viewModelScope.launch {
                val now = System.currentTimeMillis()
                val sample1 = TranscriptionRecord(
                    id = "sample_1",
                    userId = "local_user",
                    text = "[00:00] Speaker A: Good morning, team. Let's review the marketing launch date for the new productivity app. I think October 15th works best.\n[00:08] Speaker B: Good morning. October 15th gives us enough runway to finalize the beta feedback. I agree.",
                    timestamp = now - 2 * 3600 * 1000L, // 2 hours ago (Today)
                    summary = "The team discussed the marketing launch date for the new productivity app and agreed on October 15th to allow sufficient time for beta feedback.",
                    category = "Meeting",
                    speakerName = "Marketing Team",
                    modelName = "Gemini 3.5 Flash"
                )
                val sample2 = TranscriptionRecord(
                    id = "sample_2",
                    userId = "local_user",
                    text = "[00:00] Speaker A: Remind me to buy fresh milk, organic eggs, and some whole-wheat bread on my way back from the gym tonight. Oh, and pick up the dry cleaning as well.",
                    timestamp = now - 28 * 3600 * 1000L, // ~28 hours ago (Yesterday)
                    summary = "A personal reminder to buy milk, eggs, bread, and pick up dry cleaning after returning from the gym.",
                    category = "Personal",
                    speakerName = "Self",
                    modelName = "Gemini 3.5 Flash"
                )
                val sample3 = TranscriptionRecord(
                    id = "sample_3",
                    userId = "local_user",
                    text = "[00:00] Speaker A: We need to optimize the database queries. The landing page load time is currently averaging 4.2 seconds, which is unacceptable.\n[00:10] Speaker B: I'll profile the SQL joins and add indexes on the foreign keys. We should easily get it under 1.5 seconds.",
                    timestamp = now - 5 * 24 * 3600 * 1000L, // 5 days ago (This Week)
                    summary = "Discussion on optimizing database queries to reduce landing page load time from 4.2s to under 1.5s by adding indexes on foreign keys.",
                    category = "Work",
                    speakerName = "Engineering Team",
                    modelName = "Gemini 3.1 Pro"
                )
                
                repository?.insert(sample1)
                repository?.insert(sample2)
                repository?.insert(sample3)
                
                prefs.edit().putBoolean("samples_inserted_v2", true).apply()
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

    fun transcribeSelectedAudio(context: Context, uri: Uri) {
        _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = "Preparing selected file...", progress = 0.1f)
        transcribeAudioUri(context, uri)
    }

    private fun parseTimestampFromFileName(fileName: String): Long? {
        val regex = "(\\d{4})[\\.\\-](\\d{2})[\\.\\-](\\d{2})_(\\d{2})[\\.\\-:](\\d{2})[\\.\\-:](\\d{2})".toRegex()
        val matchResult = regex.find(fileName) ?: return null
        return try {
            val (yearStr, monthStr, dayStr, hourStr, minuteStr, secondStr) = matchResult.destructured
            val calendar = java.util.Calendar.getInstance()
            calendar.set(java.util.Calendar.YEAR, yearStr.toInt())
            calendar.set(java.util.Calendar.MONTH, monthStr.toInt() - 1)
            calendar.set(java.util.Calendar.DAY_OF_MONTH, dayStr.toInt())
            calendar.set(java.util.Calendar.HOUR_OF_DAY, hourStr.toInt())
            calendar.set(java.util.Calendar.MINUTE, minuteStr.toInt())
            calendar.set(java.util.Calendar.SECOND, secondStr.toInt())
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            calendar.timeInMillis
        } catch (e: Exception) {
            null
        }
    }

    private fun getFileMetadata(context: Context, uri: Uri): Pair<String?, Long?> {
        var displayName: String? = null
        var lastModified: Long? = null
        try {
            val contentResolver = context.contentResolver
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        displayName = it.getString(nameIndex)
                    }
                    val lastModifiedIndex = it.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    if (lastModifiedIndex != -1) {
                        val lm = it.getLong(lastModifiedIndex)
                        if (lm > 0) {
                            lastModified = lm
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        if (displayName == null) {
            displayName = uri.lastPathSegment
        }

        if (lastModified == null) {
            try {
                if (uri.scheme == "file") {
                    val file = uri.path?.let { File(it) }
                    if (file != null && file.exists()) {
                        lastModified = file.lastModified()
                    }
                } else if (uri.scheme == "content") {
                    val cursor = context.contentResolver.query(
                        uri,
                        arrayOf(android.provider.MediaStore.MediaColumns.DATE_MODIFIED),
                        null,
                        null,
                        null
                    )
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val dateModifiedIndex = it.getColumnIndex(android.provider.MediaStore.MediaColumns.DATE_MODIFIED)
                            if (dateModifiedIndex != -1) {
                                val secs = it.getLong(dateModifiedIndex)
                                if (secs > 0) {
                                    lastModified = secs * 1000L
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        return Pair(displayName, lastModified)
    }

    fun transcribeAudioUri(context: Context, uri: Uri) {
        _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = "Reading audio bytes...", progress = 0.3f)
        viewModelScope.launch {
            try {
                val (displayName, lastModified) = getFileMetadata(context, uri)
                val parsedTimestamp = displayName?.let { parseTimestampFromFileName(it) }
                val customTimestamp = parsedTimestamp ?: lastModified ?: System.currentTimeMillis()

                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.use { it.readBytes() }
                if (bytes == null || bytes.isEmpty()) {
                    _uiState.value = _uiState.value.copy(isLoading = false, statusMessage = null, progress = null, error = "Failed to read audio file.")
                    return@launch
                }
                
                // Save a local copy in cache to ensure playback availability later using customTimestamp
                val localFile = File(context.cacheDir, "imported_${customTimestamp}.aac")
                localFile.writeBytes(bytes)
                val localUriString = Uri.fromFile(localFile).toString()
                
                transcribeAudioBytes(bytes, "audio/aac", localUriString, customTimestamp)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, statusMessage = null, progress = null, error = e.message)
            }
        }
    }

    private fun transcribeAudioBytes(
        bytes: ByteArray,
        mimeType: String = "audio/aac",
        audioUriString: String? = null,
        customTimestamp: Long? = null
    ) {
        _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = "Encoding audio for Gemini...", progress = 0.5f)
        viewModelScope.launch {
            try {
                val apiKey = getActiveApiKey()
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
                val recordTimestamp = customTimestamp ?: System.currentTimeMillis()
                
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
                                                    audioUri = audioUriString,
                                                    timestamp = recordTimestamp,
                                                    modelName = "Gemini 3.5 Flash"
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
                    summary = summaryResultText,
                    timestamp = recordTimestamp,
                    modelName = "Gemini 3.5 Flash"
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
                val apiKey = getActiveApiKey()
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
        val record = TranscriptionRecord(userId = userId, text = text, audioUri = audioUriString, summary = summary, modelName = "Gemini 3.5 Flash")
        
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

enum class AiProvider {
    GEMINI, OPENROUTER, GROQ
}

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

data class UiState(
    val isAuthenticated: Boolean = false,
    val isAnonymous: Boolean = true,
    val userEmail: String? = null,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val progress: Float? = null,
    val lastTranscription: String? = null,
    val lastSummary: String? = null,
    val lastAnswer: String? = null,
    val history: List<TranscriptionRecord> = emptyList(),
    val error: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val customApiKey: String = "",
    val openRouterApiKey: String = "",
    val groqApiKey: String = "",
    val aiProvider: AiProvider = AiProvider.GEMINI,
    val webClientId: String = ""
)
