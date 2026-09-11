package com.example

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainViewModel
import com.example.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import android.media.MediaPlayer
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.delay
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.AnnotatedString

import androidx.compose.foundation.isSystemInDarkTheme
import com.example.ui.ThemeMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sync

import androidx.compose.material3.Checkbox
import androidx.compose.foundation.clickable
import androidx.compose.material3.TextButton

@Composable
fun highlightKeywords(text: String, keywords: List<String>): AnnotatedString {
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.primaryContainer
    
    return remember(text, keywords, primaryColor, backgroundColor) {
        if (keywords.isEmpty() || text.isBlank()) return@remember AnnotatedString(text)
        
        val pattern = keywords.joinToString("|") { Regex.escape(it) }
        val regex = Regex("\\b($pattern)\\b", RegexOption.IGNORE_CASE)
        
        buildAnnotatedString {
            append(text)
            regex.findAll(text).forEach { matchResult ->
                addStyle(
                    style = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                        background = backgroundColor
                    ),
                    start = matchResult.range.first,
                    end = matchResult.range.last + 1
                )
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsState()
            
            val isDarkTheme = when (uiState.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            
            MyApplicationTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppScreen(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    val recordPermissionState = rememberPermissionState(
        Manifest.permission.RECORD_AUDIO
    )

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.transcribeSelectedAudio(context, it)
        }
    }

    var recordToExport by remember { mutableStateOf<com.example.ui.TranscriptionRecord?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            recordToExport?.let { record ->
                viewModel.exportTranscriptionToJson(context, it, record)
            }
        }
        recordToExport = null
    }

    LaunchedEffect(Unit) {
        viewModel.initAudioRecorder(context)
        viewModel.initDatabase(context)
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transcribe AI") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    IconButton(onClick = {
                        val nextMode = when (uiState.themeMode) {
                            ThemeMode.SYSTEM -> ThemeMode.LIGHT
                            ThemeMode.LIGHT -> ThemeMode.DARK
                            ThemeMode.DARK -> ThemeMode.SYSTEM
                        }
                        viewModel.setThemeMode(nextMode)
                    }) {
                        val icon = when (uiState.themeMode) {
                            ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                            ThemeMode.LIGHT -> Icons.Default.LightMode
                            ThemeMode.DARK -> Icons.Default.DarkMode
                        }
                        Icon(icon, contentDescription = "Toggle Theme")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!uiState.isAuthenticated) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val credentialManager = CredentialManager.create(context)
                            val googleIdOption = GetGoogleIdOption.Builder()
                                .setFilterByAuthorizedAccounts(false)
                                .setServerClientId(context.getString(R.string.default_web_client_id))
                                .setAutoSelectEnabled(true)
                                .build()
                            
                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(googleIdOption)
                                .build()

                            try {
                                val result = credentialManager.getCredential(context, request)
                                val credential = result.credential
                                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                                    val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                                    FirebaseAuth.getInstance().signInWithCredential(authCredential).addOnCompleteListener { task ->
                                        if (task.isSuccessful) {
                                            // Real app would reload state via auth listener
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sign In with Google")
                }
            } else {
                Text("Welcome, ${uiState.userEmail}")
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            if (!recordPermissionState.status.isGranted) {
                                recordPermissionState.launchPermissionRequest()
                            } else {
                                if (uiState.isRecording) {
                                    viewModel.stopRecording()
                                } else {
                                    viewModel.startRecording()
                                }
                            }
                        },
                        containerColor = if (uiState.isRecording) MaterialTheme.colorScheme.errorContainer else FloatingActionButtonDefaults.containerColor,
                        contentColor = if (uiState.isRecording) MaterialTheme.colorScheme.onErrorContainer else contentColorFor(FloatingActionButtonDefaults.containerColor),
                        icon = {
                            Icon(
                                imageVector = if (uiState.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = if (uiState.isRecording) "Stop Recording" else "Start Recording"
                            )
                        },
                        text = {
                            Text(if (uiState.isRecording) "Stop Recording" else "Record Audio")
                        }
                    )

                    OutlinedButton(
                        onClick = {
                            audioPickerLauncher.launch("audio/*")
                        },
                        enabled = !uiState.isLoading && !uiState.isRecording
                    ) {
                        Text("Pick AAC Audio")
                    }
                }

                if (uiState.isLoading) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.statusMessage?.let { msg ->
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        val progressValue = uiState.progress
                        if (progressValue != null) {
                            LinearProgressIndicator(
                                progress = progressValue,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                val defaultKeywords = remember {
                    listOf(
                        "ai", "api", "architecture", "android", "kotlin", "compose", "gemini",
                        "meeting", "action item", "deadline", "roadmap", "revenue", "q1", "q2", "q3", "q4",
                        "backend", "frontend", "database", "server", "cloud"
                    )
                }

                uiState.lastTranscription?.let { text ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Latest Transcription:", style = MaterialTheme.typography.titleMedium)
                            uiState.lastSummary?.let { summaryText ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = "Highlights",
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "Highlights", 
                                                style = MaterialTheme.typography.titleSmall, 
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            summaryText, 
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                            
                            Text(highlightKeywords(text, defaultKeywords))
                        }
                    }
                }

                HorizontalDivider()

                var question by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Ask a complex query (High Thinking)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { viewModel.askComplexQuestion(question) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = question.isNotBlank() && !uiState.isLoading
                ) {
                    Text("Ask Gemini")
                }

                uiState.lastAnswer?.let { ans ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Answer:", style = MaterialTheme.typography.titleMedium)
                            Text(ans)
                        }
                    }
                }
                
                var historySearchQuery by remember { mutableStateOf("") }
                var isSelectionMode by remember { mutableStateOf(false) }
                var selectedIds by remember { mutableStateOf(setOf<String>()) }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("History", style = MaterialTheme.typography.titleLarge)
                    if (isSelectionMode) {
                        Row {
                            TextButton(onClick = {
                                isSelectionMode = false
                                selectedIds = emptySet()
                            }) {
                                Text("Cancel")
                            }
                            TextButton(onClick = {
                                viewModel.deleteTranscriptions(selectedIds.toList())
                                isSelectionMode = false
                                selectedIds = emptySet()
                            }, enabled = selectedIds.isNotEmpty()) {
                                Text("Delete (${selectedIds.size})", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.syncTranscriptions() }) {
                                Icon(Icons.Default.Sync, contentDescription = "Sync with Cloud")
                            }
                            if (uiState.history.isNotEmpty()) {
                                TextButton(onClick = { isSelectionMode = true }) {
                                    Text("Select")
                                }
                            }
                        }
                    }
                }
                
                OutlinedTextField(
                    value = historySearchQuery,
                    onValueChange = { historySearchQuery = it },
                    label = { Text("Search by date or speaker/keyword...") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                val dateFormat = remember { java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()) }
                val filteredHistory = uiState.history.filter { record ->
                    val dateString = dateFormat.format(java.util.Date(record.timestamp))
                    val query = historySearchQuery.lowercase()
                    dateString.lowercase().contains(query) || 
                    record.text.lowercase().contains(query) || 
                    (record.summary?.lowercase()?.contains(query) == true) ||
                    (record.category?.lowercase()?.contains(query) == true) ||
                    (record.speakerName?.lowercase()?.contains(query) == true)
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filteredHistory) { record ->
                        val isSelected = selectedIds.contains(record.id)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isSelectionMode) {
                                        Modifier.clickable {
                                            if (isSelected) {
                                                selectedIds = selectedIds - record.id
                                            } else {
                                                selectedIds = selectedIds + record.id
                                            }
                                        }
                                    } else Modifier
                                ),
                            colors = if (isSelected) CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            ) else CardDefaults.cardColors()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isSelectionMode) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                selectedIds = selectedIds + record.id
                                            } else {
                                                selectedIds = selectedIds - record.id
                                            }
                                        },
                                        modifier = Modifier.padding(start = 16.dp)
                                    )
                                }
                                Column(modifier = Modifier.padding(16.dp).weight(1f)) {
                                    var editSpeakerMode by remember { mutableStateOf(false) }
                                var tempSpeakerName by remember { mutableStateOf(record.speakerName ?: "") }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (editSpeakerMode) {
                                        OutlinedTextField(
                                            value = tempSpeakerName,
                                            onValueChange = { tempSpeakerName = it },
                                            modifier = Modifier.weight(1f),
                                            label = { Text("Speaker") },
                                            singleLine = true,
                                            trailingIcon = {
                                                IconButton(onClick = {
                                                    viewModel.updateSpeakerName(record, tempSpeakerName)
                                                    editSpeakerMode = false
                                                }) {
                                                    Icon(Icons.Default.Check, "Save")
                                                }
                                            }
                                        )
                                    } else {
                                        Column {
                                            Text(
                                                text = dateFormat.format(java.util.Date(record.timestamp)),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (!record.speakerName.isNullOrBlank()) {
                                                Text(
                                                    text = "Speaker: ${record.speakerName}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        TextButton(onClick = { editSpeakerMode = true }) {
                                            Text(if (record.speakerName.isNullOrBlank()) "Add Speaker" else "Edit")
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                record.summary?.let { summaryText ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.AutoAwesome,
                                                    contentDescription = "Highlights",
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    "Highlights", 
                                                    style = MaterialTheme.typography.labelMedium, 
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                summaryText, 
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }

                                Text(highlightKeywords(record.text, defaultKeywords))
                                
                                record.audioUri?.let { uriString ->
                                    Spacer(modifier = Modifier.height(4.dp))
                                    AudioPlayerComponent(uriString)
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val categories = listOf("Work", "Personal", "Meeting")
                                    categories.forEach { cat ->
                                        FilterChip(
                                            selected = record.category == cat,
                                            onClick = { 
                                                val newCategory = if (record.category == cat) null else cat
                                                viewModel.updateCategory(record, newCategory ?: "")
                                            },
                                            label = { Text(cat) }
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        recordToExport = record
                                        exportLauncher.launch("transcription_${record.timestamp}.json")
                                    },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Export JSON")
                                }
                            }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AudioPlayerComponent(uriString: String) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var hasError by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }

    // Generate a consistent pseudo-random waveform based on the URI
    val waveformAmplitudes = remember(uriString) {
        val random = java.util.Random(uriString.hashCode().toLong())
        List(40) { 0.2f + random.nextFloat() * 0.8f }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            mediaPlayer?.let {
                if (it.duration > 0) {
                    progress = it.currentPosition.toFloat() / it.duration
                }
            }
            delay(50)
        }
    }

    DisposableEffect(uriString) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        IconButton(
            onClick = {
                try {
                    if (isPlaying) {
                        mediaPlayer?.pause()
                        isPlaying = false
                    } else {
                        if (mediaPlayer == null) {
                            val player = MediaPlayer()
                            player.setDataSource(context, Uri.parse(uriString))
                            player.setOnCompletionListener {
                                isPlaying = false
                                progress = 1f
                            }
                            player.prepare()
                            mediaPlayer = player
                        }
                        mediaPlayer?.start()
                        isPlaying = true
                        hasError = false
                    }
                } catch (e: Exception) {
                    hasError = true
                    isPlaying = false
                    mediaPlayer?.release()
                    mediaPlayer = null
                }
            }
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        if (hasError) {
            Text(
                text = "Audio unavailable",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                waveformAmplitudes.forEachIndexed { index, amplitude ->
                    val isPlayed = index.toFloat() / waveformAmplitudes.size <= progress
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(amplitude)
                            .padding(horizontal = 1.dp)
                            .background(
                                color = if (isPlayed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}
