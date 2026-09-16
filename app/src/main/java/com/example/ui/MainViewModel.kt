package com.example.ui

import android.content.Context
import android.media.MediaMetadataRetriever
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
import org.json.JSONObject
import com.example.db.Transcription

typealias TranscriptionRecord = com.example.db.Transcription

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
                val sample1 = Transcription(
                    id = "sample_1",
                    userId = "local_user",
                    transcription = "[00:00] Speaker A: Good morning, team. Let's review the marketing launch date for the new productivity app. I think October 15th works best.\n[00:08] Speaker B: Good morning. October 15th gives us enough runway to finalize the beta feedback. I agree.",
                    speakerLabels = "Marketing Team",
                    audioFilePath = null,
                    timestamp = now - 2 * 3600 * 1000L, // 2 hours ago (Today)
                    summary = "The team discussed the marketing launch date for the new productivity app and agreed on October 15th to allow sufficient time for beta feedback.",
                    category = "Meeting",
                    modelName = "Gemini 3.5 Flash"
                )
                val sample2 = Transcription(
                    id = "sample_2",
                    userId = "local_user",
                    transcription = "[00:00] Speaker A: Remind me to buy fresh milk, organic eggs, and some whole-wheat bread on my way back from the gym tonight. Oh, and pick up the dry cleaning as well.",
                    speakerLabels = "Self",
                    audioFilePath = null,
                    timestamp = now - 28 * 3600 * 1000L, // ~28 hours ago (Yesterday)
                    summary = "A personal reminder to buy milk, eggs, bread, and pick up dry cleaning after returning from the gym.",
                    category = "Personal",
                    modelName = "Gemini 3.5 Flash"
                )
                val sample3 = Transcription(
                    id = "sample_3",
                    userId = "local_user",
                    transcription = "[00:00] Speaker A: We need to optimize the database queries. The landing page load time is currently averaging 4.2 seconds, which is unacceptable.\n[00:10] Speaker B: I'll profile the SQL joins and add indexes on the foreign keys. We should easily get it under 1.5 seconds.",
                    speakerLabels = "Engineering Team",
                    audioFilePath = null,
                    timestamp = now - 5 * 24 * 3600 * 1000L, // 5 days ago (This Week)
                    summary = "Discussion on optimizing database queries to reduce landing page load time from 4.2s to under 1.5s by adding indexes on foreign keys.",
                    category = "Work",
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
                                                val record = Transcription(
                                                    id = transcriptionId,
                                                    userId = userId,
                                                    transcription = accumulatedText,
                                                    speakerLabels = null,
                                                    audioFilePath = audioUriString,
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
                val finalRecord = Transcription(
                    id = transcriptionId,
                    userId = userId,
                    transcription = resultText,
                    speakerLabels = null,
                    audioFilePath = audioUriString,
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

    private fun getAudioDuration(context: Context, uri: Uri): Int {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            time?.toIntOrNull() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private fun compareNatural(a: String, b: String): Int {
        val regex = "(\\d+)|(\\D+)".toRegex()
        val aMatches = regex.findAll(a).map { it.value }.toList()
        val bMatches = regex.findAll(b).map { it.value }.toList()
        val minSize = minOf(aMatches.size, bMatches.size)
        for (i in 0 until minSize) {
            val aToken = aMatches[i]
            val bToken = bMatches[i]
            val aNum = aToken.toLongOrNull()
            val bNum = bToken.toLongOrNull()
            if (aNum != null && bNum != null) {
                val cmp = aNum.compareTo(bNum)
                if (cmp != 0) return cmp
            } else {
                val cmp = aToken.compareTo(bToken, ignoreCase = true)
                if (cmp != 0) return cmp
            }
        }
        return aMatches.size.compareTo(bMatches.size)
    }

    fun analyzeSelectedFilesForSequence(context: Context, uris: List<Uri>): List<SequentialAudioFile> {
        val items = uris.map { uri ->
            val (displayName, lastModified) = getFileMetadata(context, uri)
            val name = displayName ?: uri.lastPathSegment ?: "Recording"
            val parsedTime = parseTimestampFromFileName(name) ?: lastModified
            val dur = getAudioDuration(context, uri)
            SequentialAudioFile(
                uri = uri,
                displayName = name,
                parsedTimestamp = parsedTime,
                durationMs = dur
            )
        }

        return items.sortedWith(Comparator { a, b ->
            if (a.parsedTimestamp != null && b.parsedTimestamp != null && a.parsedTimestamp != b.parsedTimestamp) {
                a.parsedTimestamp.compareTo(b.parsedTimestamp)
            } else {
                compareNatural(a.displayName, b.displayName)
            }
        })
    }

    fun transcribeSequentialSession(
        context: Context,
        files: List<SequentialAudioFile>,
        sessionTitle: String
    ) {
        if (files.isEmpty()) return
        
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            statusMessage = "Starting session: $sessionTitle (0/${files.size})...",
            progress = 0.05f
        )

        viewModelScope.launch {
            try {
                val apiKey = getActiveApiKey()
                val sessionId = UUID.randomUUID().toString()
                var userId = "local_user"
                try {
                    userId = getAuthSafe()?.currentUser?.uid ?: "local_user"
                } catch (e: Exception) {}

                val partResults = mutableListOf<Transcription>()
                val totalParts = files.size

                for (index in files.indices) {
                    val fileItem = files[index]
                    val partNumber = index + 1

                    _uiState.value = _uiState.value.copy(
                        statusMessage = "Reading Part $partNumber of $totalParts: ${fileItem.displayName}...",
                        progress = (index.toFloat() / totalParts) * 0.85f
                    )

                    val inputStream = context.contentResolver.openInputStream(fileItem.uri)
                    val bytes = inputStream?.use { it.readBytes() }
                    if (bytes == null || bytes.isEmpty()) {
                        continue
                    }

                    val customTimestamp = fileItem.parsedTimestamp ?: System.currentTimeMillis()
                    val localFile = File(context.cacheDir, "session_${sessionId}_part${partNumber}_${customTimestamp}.aac")
                    localFile.writeBytes(bytes)
                    val localUriString = Uri.fromFile(localFile).toString()

                    _uiState.value = _uiState.value.copy(
                        statusMessage = "Transcribing Part $partNumber of $totalParts: ${fileItem.displayName}...",
                        progress = ((index.toFloat() + 0.3f) / totalParts) * 0.85f
                    )

                    val base64Audio = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val prompt = "Please transcribe this audio (Part $partNumber of a $totalParts-part recording session titled '$sessionTitle'). Identify the different speakers and include timestamps for when each speaker speaks. Output the result formatted with speaker labels, for example:\n[00:12] Speaker A: Hello\n[00:15] Speaker B: Hi there"

                    val request = GenerateContentRequest(
                        contents = listOf(
                            Content(
                                parts = listOf(
                                    Part(text = prompt),
                                    Part(
                                        inlineData = InlineData(
                                            mimeType = "audio/aac",
                                            data = base64Audio
                                        )
                                    )
                                )
                            )
                        )
                    )

                    val response = RetrofitClient.service.generateContentStream(
                        model = "gemini-3.5-flash",
                        apiKey = apiKey,
                        request = request
                    )

                    var partAccumulatedText = ""
                    val partTranscriptionId = UUID.randomUUID().toString()

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
                                                partAccumulatedText += textPart
                                                _uiState.value = _uiState.value.copy(
                                                    lastTranscription = partAccumulatedText
                                                )
                                            }
                                        }
                                    } catch (e: Exception) {
                                        // Ignore partial chunk parsing errors
                                    }
                                }
                            }
                        }
                    }

                    val finalPartText = partAccumulatedText.ifEmpty { "No speech detected in Part $partNumber." }

                    val partRecord = Transcription(
                        id = partTranscriptionId,
                        userId = userId,
                        transcription = finalPartText,
                        speakerLabels = null,
                        audioFilePath = localUriString,
                        timestamp = customTimestamp,
                        summary = null,
                        modelName = "Gemini 3.5 Flash",
                        sessionId = sessionId,
                        partIndex = index,
                        totalParts = totalParts,
                        sessionTitle = sessionTitle,
                        partDurationMs = if (fileItem.durationMs > 0) fileItem.durationMs else null
                    )

                    repository?.insert(partRecord)
                    partResults.add(partRecord)
                }

                if (partResults.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        statusMessage = null,
                        progress = null,
                        error = "Failed to process audio files for session."
                    )
                    return@launch
                }

                // Generate Consolidated Master AI Summary across all session parts
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Synthesizing Master AI Summary across all $totalParts parts...",
                    progress = 0.92f
                )

                val combinedTranscripts = partResults.mapIndexed { idx, part ->
                    val fileInfo = files.getOrNull(idx)
                    val header = "--- Part ${idx + 1} of $totalParts: ${fileInfo?.displayName ?: "Recording"} ---"
                    "$header\n${part.transcription}"
                }.joinToString("\n\n")

                val masterSummaryPrompt = """
                    You are an expert executive scribe. Please provide a comprehensive Master Executive Summary for this multi-part sequential recording session.
                    Session Title: $sessionTitle
                    Total Parts: $totalParts
                    
                    Include:
                    1. High-Level Executive Overview
                    2. Chronological Key Discussion Points & Takeaways
                    3. Action Items & Decisions
                    
                    Transcript Content:
                    $combinedTranscripts
                """.trimIndent()

                val summaryRequest = GenerateContentRequest(
                    contents = listOf(
                        Content(parts = listOf(Part(text = masterSummaryPrompt)))
                    )
                )

                val summaryResponse = RetrofitClient.service.generateContent(
                    model = "gemini-3.5-flash",
                    apiKey = apiKey,
                    request = summaryRequest
                )

                val masterSummaryResult = summaryResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "Sequential session completed with $totalParts parts."

                // Update each part with the master summary so every view gets the consolidated takeaways
                for (part in partResults) {
                    val updatedPart = part.copy(summary = masterSummaryResult)
                    repository?.insert(updatedPart)
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    statusMessage = null,
                    progress = null,
                    lastTranscription = combinedTranscripts,
                    lastSummary = masterSummaryResult,
                    infoMessage = "Sequential session '$sessionTitle' ($totalParts parts) transcribed and summarized successfully!"
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    statusMessage = null,
                    progress = null,
                    error = "Sequential transcription error: ${e.localizedMessage ?: e.message}"
                )
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
            val updatedRecord = record.copy(speakerLabels = newSpeakerName)
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

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository?.deleteSession(sessionId)
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
        val record = Transcription(
            userId = userId,
            transcription = text,
            speakerLabels = null,
            audioFilePath = audioUriString,
            summary = summary,
            modelName = "Gemini 3.5 Flash"
        )
        
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
                json.put("transcription", record.transcription)
                json.put("text", record.transcription)
                if (record.summary != null) {
                    json.put("summary", record.summary)
                }
                if (record.category != null) {
                    json.put("category", record.category)
                }
                if (record.speakerLabels != null) {
                    json.put("speakerLabels", record.speakerLabels)
                    json.put("speakerName", record.speakerLabels)
                }
                if (record.audioFilePath != null) {
                    json.put("audioFilePath", record.audioFilePath)
                    json.put("audioUri", record.audioFilePath)
                }
                if (record.modelName != null) {
                    json.put("modelName", record.modelName)
                }
                if (record.sessionId != null) {
                    json.put("sessionId", record.sessionId)
                }
                if (record.partIndex != null) {
                    json.put("partIndex", record.partIndex)
                }
                if (record.totalParts != null) {
                    json.put("totalParts", record.totalParts)
                }
                if (record.sessionTitle != null) {
                    json.put("sessionTitle", record.sessionTitle)
                }
                if (record.partDurationMs != null) {
                    json.put("partDurationMs", record.partDurationMs)
                }
                
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toString(4).toByteArray())
                }
                _uiState.value = _uiState.value.copy(
                    infoMessage = "Transcription exported successfully as JSON!",
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to export JSON: ${e.localizedMessage ?: e.message}")
            }
        }
    }

    fun exportSessionToJson(context: Context, uri: Uri, sessionTitle: String, parts: List<TranscriptionRecord>) {
        viewModelScope.launch {
            try {
                if (parts.isEmpty()) {
                    _uiState.value = _uiState.value.copy(error = "No session parts to export.")
                    return@launch
                }

                val first = parts.first()
                val rootJson = org.json.JSONObject()
                rootJson.put("version", 1)
                rootJson.put("type", "sequential_session")
                rootJson.put("sessionId", first.sessionId ?: UUID.randomUUID().toString())
                rootJson.put("sessionTitle", sessionTitle)
                rootJson.put("totalParts", parts.size)
                rootJson.put("masterSummary", first.summary ?: "")
                rootJson.put("exportedAt", System.currentTimeMillis())

                val partsArray = org.json.JSONArray()
                for (part in parts) {
                    val partJson = org.json.JSONObject()
                    partJson.put("id", part.id)
                    partJson.put("sessionId", part.sessionId)
                    partJson.put("partIndex", part.partIndex)
                    partJson.put("totalParts", part.totalParts)
                    partJson.put("sessionTitle", part.sessionTitle)
                    partJson.put("partDurationMs", part.partDurationMs)
                    partJson.put("timestamp", part.timestamp)
                    partJson.put("transcription", part.transcription)
                    partJson.put("text", part.transcription)
                    partJson.put("speakerLabels", part.speakerLabels)
                    partJson.put("audioFilePath", part.audioFilePath)
                    partJson.put("modelName", part.modelName)
                    partsArray.put(partJson)
                }
                rootJson.put("parts", partsArray)

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(rootJson.toString(2).toByteArray(Charsets.UTF_8))
                }

                _uiState.value = _uiState.value.copy(
                    infoMessage = "Session '$sessionTitle' (${parts.size} parts) exported successfully!",
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to export session JSON: ${e.localizedMessage ?: e.message}"
                )
            }
        }
    }

    fun exportAllTranscriptionsToJson(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val list = _uiState.value.history
                if (list.isEmpty()) {
                    _uiState.value = _uiState.value.copy(error = "No transcriptions available to export.")
                    return@launch
                }

                val rootJson = org.json.JSONObject()
                rootJson.put("version", 1)
                rootJson.put("exportedAt", System.currentTimeMillis())
                rootJson.put("exportedDate", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()))
                rootJson.put("count", list.size)

                val array = org.json.JSONArray()
                for (record in list) {
                    val itemJson = org.json.JSONObject()
                    itemJson.put("id", record.id)
                    itemJson.put("userId", record.userId)
                    itemJson.put("timestamp", record.timestamp)
                    itemJson.put("transcription", record.transcription)
                    itemJson.put("text", record.transcription)
                    if (record.summary != null) itemJson.put("summary", record.summary)
                    if (record.category != null) itemJson.put("category", record.category)
                    if (record.speakerLabels != null) {
                        itemJson.put("speakerLabels", record.speakerLabels)
                        itemJson.put("speakerName", record.speakerLabels)
                    }
                    if (record.audioFilePath != null) {
                        itemJson.put("audioFilePath", record.audioFilePath)
                        itemJson.put("audioUri", record.audioFilePath)
                    }
                    if (record.modelName != null) itemJson.put("modelName", record.modelName)
                    if (record.sessionId != null) itemJson.put("sessionId", record.sessionId)
                    if (record.partIndex != null) itemJson.put("partIndex", record.partIndex)
                    if (record.totalParts != null) itemJson.put("totalParts", record.totalParts)
                    if (record.sessionTitle != null) itemJson.put("sessionTitle", record.sessionTitle)
                    if (record.partDurationMs != null) itemJson.put("partDurationMs", record.partDurationMs)
                    array.put(itemJson)
                }
                rootJson.put("transcriptions", array)

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(rootJson.toString(2).toByteArray(Charsets.UTF_8))
                }
                _uiState.value = _uiState.value.copy(
                    infoMessage = "Successfully exported ${list.size} transcriptions to JSON backup.",
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to export JSON backup: ${e.localizedMessage ?: e.message}"
                )
            }
        }
    }

    fun importTranscriptionsFromJson(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = "Reading JSON backup file...")
                val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                }

                if (content.isNullOrBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        statusMessage = null,
                        error = "Selected JSON file is empty or cannot be read."
                    )
                    return@launch
                }

                val trimmed = content.trim()
                val parsedRecords = mutableListOf<Transcription>()
                val currentUserId = getAuthSafe()?.currentUser?.uid ?: "local_user"

                fun parseRecordObject(obj: org.json.JSONObject, defaultSummary: String? = null, defaultSessionId: String? = null, defaultSessionTitle: String? = null): Transcription {
                    val id = if (obj.has("id") && !obj.isNull("id")) obj.getString("id") else UUID.randomUUID().toString()
                    val userId = if (obj.has("userId") && !obj.isNull("userId")) obj.getString("userId") else currentUserId
                    val text = when {
                        obj.has("transcription") && !obj.isNull("transcription") -> obj.getString("transcription")
                        obj.has("text") && !obj.isNull("text") -> obj.getString("text")
                        else -> ""
                    }
                    val timestamp = if (obj.has("timestamp") && !obj.isNull("timestamp")) obj.getLong("timestamp") else System.currentTimeMillis()
                    val summary = when {
                        obj.has("summary") && !obj.isNull("summary") -> obj.getString("summary")
                        defaultSummary != null -> defaultSummary
                        else -> null
                    }
                    val category = if (obj.has("category") && !obj.isNull("category")) obj.getString("category") else null
                    val speaker = when {
                        obj.has("speakerLabels") && !obj.isNull("speakerLabels") -> obj.getString("speakerLabels")
                        obj.has("speakerName") && !obj.isNull("speakerName") -> obj.getString("speakerName")
                        else -> null
                    }
                    val audio = when {
                        obj.has("audioFilePath") && !obj.isNull("audioFilePath") -> obj.getString("audioFilePath")
                        obj.has("audioUri") && !obj.isNull("audioUri") -> obj.getString("audioUri")
                        else -> null
                    }
                    val model = if (obj.has("modelName") && !obj.isNull("modelName")) obj.getString("modelName") else null
                    val sessionId = when {
                        obj.has("sessionId") && !obj.isNull("sessionId") -> obj.getString("sessionId")
                        obj.has("session_id") && !obj.isNull("session_id") -> obj.getString("session_id")
                        else -> defaultSessionId
                    }
                    val partIndex = when {
                        obj.has("partIndex") && !obj.isNull("partIndex") -> obj.getInt("partIndex")
                        obj.has("part_index") && !obj.isNull("part_index") -> obj.getInt("part_index")
                        else -> null
                    }
                    val totalParts = when {
                        obj.has("totalParts") && !obj.isNull("totalParts") -> obj.getInt("totalParts")
                        obj.has("total_parts") && !obj.isNull("total_parts") -> obj.getInt("total_parts")
                        else -> null
                    }
                    val sessionTitle = when {
                        obj.has("sessionTitle") && !obj.isNull("sessionTitle") -> obj.getString("sessionTitle")
                        obj.has("session_title") && !obj.isNull("session_title") -> obj.getString("session_title")
                        else -> defaultSessionTitle
                    }
                    val partDurationMs = when {
                        obj.has("partDurationMs") && !obj.isNull("partDurationMs") -> obj.getInt("partDurationMs")
                        obj.has("part_duration_ms") && !obj.isNull("part_duration_ms") -> obj.getInt("part_duration_ms")
                        else -> null
                    }

                    return Transcription(
                        id = id,
                        userId = userId,
                        transcription = text,
                        speakerLabels = speaker,
                        audioFilePath = audio,
                        timestamp = timestamp,
                        summary = summary,
                        category = category,
                        modelName = model,
                        sessionId = sessionId,
                        partIndex = partIndex,
                        totalParts = totalParts,
                        sessionTitle = sessionTitle,
                        partDurationMs = partDurationMs
                    )
                }

                if (trimmed.startsWith("{")) {
                    val rootObj = org.json.JSONObject(trimmed)
                    if (rootObj.has("parts")) {
                        val array = rootObj.getJSONArray("parts")
                        val defSummary = rootObj.optString("masterSummary", "").ifBlank { null }
                        val defSessionId = rootObj.optString("sessionId", "").ifBlank { null }
                        val defSessionTitle = rootObj.optString("sessionTitle", "").ifBlank { null }
                        for (i in 0 until array.length()) {
                            val item = array.getJSONObject(i)
                            parsedRecords.add(parseRecordObject(item, defSummary, defSessionId, defSessionTitle))
                        }
                    } else if (rootObj.has("transcriptions")) {
                        val array = rootObj.getJSONArray("transcriptions")
                        for (i in 0 until array.length()) {
                            val item = array.getJSONObject(i)
                            parsedRecords.add(parseRecordObject(item))
                        }
                    } else if (rootObj.has("transcription") || rootObj.has("text")) {
                        parsedRecords.add(parseRecordObject(rootObj))
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            statusMessage = null,
                            error = "Invalid JSON format: missing 'transcriptions' or 'parts' list."
                        )
                        return@launch
                    }
                } else if (trimmed.startsWith("[")) {
                    val array = org.json.JSONArray(trimmed)
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        parsedRecords.add(parseRecordObject(item))
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        statusMessage = null,
                        error = "Invalid file: selected file does not contain valid JSON data."
                    )
                    return@launch
                }

                if (parsedRecords.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        statusMessage = null,
                        error = "No valid transcription records found in the JSON file."
                    )
                    return@launch
                }

                // Insert into Room DB
                repository?.insertAll(parsedRecords)

                // Sync to Firestore if signed in
                val user = getAuthSafe()?.currentUser
                if (user != null) {
                    try {
                        parsedRecords.forEach { rec ->
                            firestore.collection("transcriptions")
                                .document(rec.id)
                                .set(rec)
                        }
                    } catch (e: Exception) {
                        // ignore cloud error if offline
                    }
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    statusMessage = null,
                    infoMessage = "Successfully imported ${parsedRecords.size} transcription records into your journal!",
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    statusMessage = null,
                    error = "Failed to import JSON: ${e.localizedMessage ?: e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearInfoMessage() {
        _uiState.value = _uiState.value.copy(infoMessage = null)
    }

    fun setError(message: String) {
        _uiState.value = _uiState.value.copy(error = message)
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

data class SequentialAudioFile(
    val uri: Uri,
    val displayName: String,
    val parsedTimestamp: Long? = null,
    val durationMs: Int = 0,
    val sizeBytes: Long = 0
)

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
    val infoMessage: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val customApiKey: String = "",
    val openRouterApiKey: String = "",
    val groqApiKey: String = "",
    val aiProvider: AiProvider = AiProvider.GEMINI,
    val webClientId: String = ""
)
