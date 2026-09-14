package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainViewModel
import com.example.ui.theme.MyApplicationTheme
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
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import com.example.ui.SettingsScreen
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sync

import androidx.compose.material3.Checkbox
import androidx.compose.foundation.clickable
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

@Composable
fun ProcessingWaveformVisualizer(
    modifier: Modifier = Modifier,
    barCount: Int = 28
) {
    val infiniteTransition = rememberInfiniteTransition(label = "processing_waveform")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveform_phase"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveform_pulse"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.tertiary

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        val barWidth = size.width / barCount
        val gap = barWidth * 0.28f
        val actualBarWidth = (barWidth - gap).coerceAtLeast(2f)
        val maxHeight = size.height

        for (i in 0 until barCount) {
            val normalizedX = i.toFloat() / (barCount - 1).coerceAtLeast(1)
            // Bell-shaped window to taper outer edges
            val window = kotlin.math.sin(normalizedX * Math.PI.toFloat()).coerceAtLeast(0.2f)
            
            // Dual frequency traveling wave
            val wave1 = kotlin.math.sin(phase + i * 0.45f)
            val wave2 = kotlin.math.cos(phase * 1.5f + i * 0.25f)
            val combined = (wave1 + wave2 + 2f) / 4f
            
            val amplitude = (combined * window * pulse).coerceIn(0.12f, 1.0f)
            val barHeight = (maxHeight * amplitude).coerceAtLeast(actualBarWidth)
            val x = i * barWidth + gap / 2
            val y = (size.height - barHeight) / 2

            val barColor = androidx.compose.ui.graphics.lerp(primaryColor, secondaryColor, normalizedX)

            drawRoundRect(
                color = barColor,
                topLeft = androidx.compose.ui.geometry.Offset(x, y),
                size = androidx.compose.ui.geometry.Size(actualBarWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(actualBarWidth / 2, actualBarWidth / 2)
            )
        }
    }
}

@Composable
fun TranscriptView(transcript: String, keywords: List<String>) {
    val speakerRegex = Regex("(?m)^\\s*(\\[\\d{2}:\\d{2}\\]\\s*)?(?:\\*\\*)?([A-Za-z0-9 _\\-]{2,30})(?:\\*\\*)?:")
    val matches = speakerRegex.findAll(transcript).toList()
    
    if (matches.isEmpty()) {
        Text(highlightKeywords(transcript, keywords))
        return
    }
    
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.error,
        androidx.compose.ui.graphics.Color(0xFFE91E63),
        androidx.compose.ui.graphics.Color(0xFF9C27B0),
        androidx.compose.ui.graphics.Color(0xFF00BCD4),
        androidx.compose.ui.graphics.Color(0xFFFF9800)
    )
    val speakerColors = remember { mutableMapOf<String, androidx.compose.ui.graphics.Color>() }
    var colorIndex by remember { mutableIntStateOf(0) }
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (i in matches.indices) {
            val match = matches[i]
            val timestamp = match.groupValues[1].trim()
            val speaker = match.groupValues[2].trim()
            val start = match.range.last + 1
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else transcript.length
            val content = transcript.substring(start, end).trim()
            
            val color = speakerColors.getOrPut(speaker) {
                colors[colorIndex++ % colors.size]
            }
            
            val displayText = if (timestamp.isNotEmpty()) "$timestamp $speaker" else speaker
            
            Column {
                Surface(
                    color = color.copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = displayText,
                        color = color,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(highlightKeywords(content, keywords), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    var showSettings by remember { mutableStateOf(false) }
    
    if (showSettings) {
        SettingsScreen(
            uiState = uiState,
            onThemeModeChange = { viewModel.setThemeMode(it) },
            onSaveApiKey = { viewModel.saveCustomApiKey(context, it) },
            onSaveOpenRouterApiKey = { viewModel.saveOpenRouterApiKey(context, it) },
            onSaveGroqApiKey = { viewModel.saveGroqApiKey(context, it) },
            onAiProviderChange = { viewModel.setAiProvider(context, it) },
            onSaveWebClientId = { viewModel.saveWebClientId(context, it) },
            onNavigateBack = { showSettings = false }
        )
        return
    }

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
        viewModel.initDatabase(context)
        viewModel.initApiKey(context)
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    var currentTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (currentTab == 0) "Transcribe Audio" else "Journal History") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.AudioFile, contentDescription = "Transcribe") },
                    label = { Text("Transcribe") },
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.History, contentDescription = "Journal") },
                    label = { Text("Journal History") },
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 }
                )
            }
        }
    ) { padding ->
        val defaultKeywords = remember {
            listOf(
                "ai", "api", "architecture", "android", "kotlin", "compose", "gemini",
                "meeting", "action item", "deadline", "roadmap", "revenue", "q1", "q2", "q3", "q4",
                "backend", "frontend", "database", "server", "cloud"
            )
        }

        if (currentTab == 1) {
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                TranscriptionHistoryScreen(
                    uiState = uiState,
                    onUpdateCategory = { record, category -> viewModel.updateCategory(record, category) },
                    onUpdateSpeakerName = { record, name -> viewModel.updateSpeakerName(record, name) },
                    onDeleteTranscriptions = { ids -> viewModel.deleteTranscriptions(ids) },
                    onExportJson = { record ->
                        recordToExport = record
                        exportLauncher.launch("transcription_${record.timestamp}.json")
                    },
                    onSync = { viewModel.syncTranscriptions() },
                    defaultKeywords = defaultKeywords,
                    audioPlayerContent = { uri -> AudioPlayerComponent(uri) }
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                    val activeKey = viewModel.getActiveApiKey()
                    if (activeKey.isBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "API Key Missing",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "No Gemini API key found. Please configure your API key in Settings to use transcription and AI features.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { showSettings = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    )
                                ) {
                                    Text("Open Settings")
                                }
                            }
                        }
                    }
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AudioFile,
                                        contentDescription = "Audio File",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Transcribe Audio File",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Select an audio file (AAC, M4A, MP3, WAV, etc.) to transcribe speech into text using AI.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = {
                                    audioPickerLauncher.launch("audio/*")
                                },
                                enabled = !uiState.isLoading,
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.UploadFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Choose Audio File")
                            }
                        }
                    }

                    if (uiState.isLoading) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = uiState.statusMessage ?: "Processing audio...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                
                                ProcessingWaveformVisualizer(
                                    modifier = Modifier.fillMaxWidth()
                                )
                                
                                val progressValue = uiState.progress
                                if (progressValue != null) {
                                    LinearProgressIndicator(
                                        progress = { progressValue },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
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
                                
                                TranscriptView(text, defaultKeywords)
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

    var playbackSpeed by remember { mutableStateOf(1.0f) }

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

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        if (hasError) {
            Text(
                text = "Audio unavailable",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(8.dp)
            )
        } else {
            val primaryColor = MaterialTheme.colorScheme.primary
            val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
            
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                val barWidth = size.width / waveformAmplitudes.size
                val gap = barWidth * 0.2f
                val actualBarWidth = barWidth - gap
                
                waveformAmplitudes.forEachIndexed { index, amplitude ->
                    val isPlayed = index.toFloat() / waveformAmplitudes.size <= progress
                    val barHeight = size.height * amplitude
                    val x = index * barWidth + gap / 2
                    val y = (size.height - barHeight) / 2
                    
                    drawRoundRect(
                        color = if (isPlayed) primaryColor else surfaceVariantColor,
                        topLeft = androidx.compose.ui.geometry.Offset(x, y),
                        size = androidx.compose.ui.geometry.Size(actualBarWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(actualBarWidth / 2, actualBarWidth / 2)
                    )
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    val nextSpeed = when (playbackSpeed) {
                        1.0f -> 1.5f
                        1.5f -> 2.0f
                        2.0f -> 0.5f
                        0.5f -> 1.0f
                        else -> 1.0f
                    }
                    playbackSpeed = nextSpeed
                    mediaPlayer?.let {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            try {
                                it.playbackParams = it.playbackParams.setSpeed(nextSpeed)
                            } catch (e: Exception) {
                                // Ignore
                            }
                        }
                    }
                }) {
                    Text("${playbackSpeed}x")
                }
                
                IconButton(onClick = {
                    mediaPlayer?.let {
                        it.seekTo((it.currentPosition - 10000).coerceAtLeast(0))
                        if (it.duration > 0) {
                            progress = it.currentPosition.toFloat() / it.duration
                        }
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Rewind 10s"
                    )
                }
                
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
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                        try {
                                            player.playbackParams = player.playbackParams.setSpeed(playbackSpeed)
                                        } catch (e: Exception) {
                                            // Ignore
                                        }
                                    }
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
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
                
                IconButton(onClick = {
                    mediaPlayer?.let {
                        it.seekTo((it.currentPosition + 10000).coerceAtMost(it.duration))
                        if (it.duration > 0) {
                            progress = it.currentPosition.toFloat() / it.duration
                        }
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Forward 10s"
                    )
                }
            }
        }
    }
}
