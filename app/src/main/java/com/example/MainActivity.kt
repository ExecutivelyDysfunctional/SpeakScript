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
import com.example.ui.PlaybackScreen
import com.example.ui.SpeakerManagementScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import com.example.ui.formatDuration
import kotlinx.coroutines.delay
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.draw.clip

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.ui.ThemeMode
import com.example.ui.AiProvider
import com.example.ui.UiState
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.clickable
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.text.style.TextAlign
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.example.service.RecordingService

@Composable
fun SystemStatusHeaderBar(
    uiState: UiState,
    onOpenSettings: () -> Unit,
    onToggleMic: () -> Unit,
    onOpenSpeakers: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = modifier.fillMaxWidth()
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Account / Auth Pill
            item {
                val isAuth = uiState.isAuthenticated && !uiState.isAnonymous
                AssistChip(
                    onClick = onOpenSettings,
                    label = {
                        Text(
                            text = if (isAuth) (uiState.userEmail ?: "Signed In") else "Guest Mode",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isAuth) Icons.Default.AccountCircle else Icons.Default.Person,
                            contentDescription = null,
                            tint = if (isAuth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.height(32.dp)
                )
            }

            // 2. Cloud Sync Pill
            item {
                val isCloudSynced = uiState.isAuthenticated && !uiState.isAnonymous
                AssistChip(
                    onClick = onOpenSettings,
                    label = {
                        Text(
                            text = if (isCloudSynced) "Cloud Sync Active" else "Offline Storage",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isCloudSynced) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (isCloudSynced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.height(32.dp)
                )
            }

            // 3. Active AI Provider Pill
            item {
                val providerLabel = when (uiState.aiProvider) {
                    AiProvider.GEMINI -> "Gemini AI"
                    AiProvider.OPENROUTER -> "OpenRouter"
                    AiProvider.GROQ -> "Groq AI"
                }
                AssistChip(
                    onClick = onOpenSettings,
                    label = {
                        Text(
                            text = providerLabel,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.height(32.dp)
                )
            }

            // 4. Voice Biometrics Roster Pill
            item {
                val goldenCount = uiState.speakers.count { !it.goldenSampleAudioUri.isNullOrBlank() }
                AssistChip(
                    onClick = onOpenSpeakers,
                    label = {
                        Text(
                            text = "$goldenCount Golden Samples",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.height(32.dp)
                )
            }

            // 5. Mic Selection Toggle Pill (Interactive!)
            item {
                FilterChip(
                    selected = uiState.useBluetoothMic,
                    onClick = onToggleMic,
                    label = {
                        Text(
                            text = if (uiState.useBluetoothMic) "BT Mic Active" else "Device Mic",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (uiState.useBluetoothMic) Icons.Default.BluetoothConnected else Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.height(32.dp)
                )
            }
        }
    }
}

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

@Composable
fun VisualErrorBanner(
    errorMessage: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Error Notice",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss error",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
fun VisualInfoBanner(
    infoMessage: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Success Notice",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = infoMessage,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss message",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val audioGranted = perms[Manifest.permission.RECORD_AUDIO] == true
        if (!audioGranted) {
            Toast.makeText(context, "Microphone permission is required for live recording.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    val receiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == RecordingService.ACTION_RECORDING_FINISHED) {
                    val path = intent.getStringExtra(RecordingService.EXTRA_WAV_PATH)
                    path?.let { viewModel.processRecordedFile(context, it) }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val filter = IntentFilter(RecordingService.ACTION_RECORDING_FINISHED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
    
    var showSettings by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf(0) }
    var activePlaybackRecord by remember { mutableStateOf<com.example.db.TranscriptionRecord?>(null) }
    var pendingSequentialFiles by remember { mutableStateOf<List<com.example.ui.SequentialAudioFile>?>(null) }
    var pendingBatchUris by remember { mutableStateOf<List<Uri>?>(null) }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            if (uris.size == 1) {
                viewModel.transcribeSelectedAudio(context, uris.first())
            } else {
                pendingBatchUris = uris
            }
        }
    }

    var recordToExport by remember { mutableStateOf<com.example.db.Transcription?>(null) }
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

    var sessionToExport by remember { mutableStateOf<Pair<String, List<com.example.db.Transcription>>?>(null) }
    val exportSessionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            sessionToExport?.let { (title, parts) ->
                viewModel.exportSessionToJson(context, it, title, parts)
            }
        }
        sessionToExport = null
    }

    val exportAllLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            viewModel.exportAllTranscriptionsToJson(context, it)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importTranscriptionsFromJson(context, it)
        }
    }
    
    if (showSettings) {
        SettingsScreen(
            uiState = uiState,
            onThemeModeChange = { viewModel.setThemeMode(it) },
            onSaveApiKey = { viewModel.saveCustomApiKey(context, it) },
            onSaveOpenRouterApiKey = { viewModel.saveOpenRouterApiKey(context, it) },
            onSaveGroqApiKey = { viewModel.saveGroqApiKey(context, it) },
            onAiProviderChange = { viewModel.setAiProvider(context, it) },
            onSyncCloud = { viewModel.syncTranscriptionsWithCloud() },
            onExportAllJson = {
                exportAllLauncher.launch("transcriptions_backup_${System.currentTimeMillis()}.json")
            },
            onImportJson = {
                importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
            },
            onOpenSpeakers = {
                showSettings = false
                currentTab = 2
            },
            onSaveLocation = { viewModel.saveLocation(it) },
            onDeleteLocation = { viewModel.deleteLocation(it) },
            onUpdateBiometricCalibration = { sens, sec, maxSpk, mode ->
                viewModel.updateBiometricCalibration(context, sens, sec, maxSpk, mode)
            },
            onRunBiometricCalibrationBenchmark = {
                viewModel.runBiometricCalibrationBenchmark(context)
            },
            onClearBiometricCalibrationReport = {
                viewModel.clearBiometricCalibrationReport()
            },
            onNavigateBack = { showSettings = false }
        )
        return
    }

    if (activePlaybackRecord != null) {
        val parts = if (activePlaybackRecord!!.sessionId != null) {
            uiState.history.filter { it.sessionId == activePlaybackRecord!!.sessionId }
        } else {
            emptyList()
        }
        PlaybackScreen(
            record = activePlaybackRecord!!,
            sessionParts = parts,
            speakers = uiState.speakers,
            onAssignGoldenSample = { speakerId, audioUri, startMs, endMs, title ->
                viewModel.assignGoldenSample(speakerId, audioUri, startMs, endMs, title)
            },
            onSaveNewSpeaker = { newProfile ->
                viewModel.saveSpeaker(newProfile)
            },
            onUpdateConfidenceAndSpeakers = { id, labels, conf ->
                viewModel.updateTranscriptionConfidenceAndSpeaker(id, labels, conf)
            },
            onNavigateBack = { activePlaybackRecord = null }
        )
        return
    }

    LaunchedEffect(Unit) {
        viewModel.initDatabase(context)
        viewModel.initApiKey(context)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (currentTab) {
                            0 -> "Transcribe Audio"
                            1 -> "Journal History"
                            else -> "Speakers Directory"
                        }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    IconButton(
                        onClick = { showSettings = true },
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
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
                    onClick = { currentTab = 0 },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.History, contentDescription = "Journal") },
                    label = { Text("Journal History") },
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.RecordVoiceOver, contentDescription = "Speakers") },
                    label = { Text("Speakers") },
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    modifier = Modifier.heightIn(min = 48.dp)
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

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Persistent System Status & Configuration Header Bar
            SystemStatusHeaderBar(
                uiState = uiState,
                onOpenSettings = { showSettings = true },
                onToggleMic = { viewModel.toggleBluetoothMic() },
                onOpenSpeakers = { currentTab = 2 }
            )

            // Visual Error Banner (Zero silent failures in console)
            uiState.error?.let { err ->
                VisualErrorBanner(
                    errorMessage = err,
                    onDismiss = { viewModel.clearError() }
                )
            }

            // Visual Info/Success Banner
            uiState.infoMessage?.let { info ->
                VisualInfoBanner(
                    infoMessage = info,
                    onDismiss = { viewModel.clearInfoMessage() }
                )
            }

            // Non-obtrusive background batch queue status indicator
            if (uiState.isLoading) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
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
                                    text = uiState.statusMessage ?: "Processing background task...",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            uiState.progress?.let { progressVal ->
                                Text(
                                    text = "${(progressVal * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        uiState.progress?.let { progressVal ->
                            LinearProgressIndicator(
                                progress = { progressVal },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                        } ?: LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                    }
                }
            }

            when (currentTab) {
                1 -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        TranscriptionHistoryScreen(
                            uiState = uiState,
                            onUpdateCategory = { record, category -> viewModel.updateCategory(record, category) },
                            onUpdateSpeakerName = { record, name -> viewModel.updateSpeakerName(record, name) },
                            onDeleteTranscriptions = { ids -> viewModel.deleteTranscriptions(ids) },
                            onDeleteSession = { sessionId -> viewModel.deleteSession(sessionId) },
                            onExportJson = { record ->
                                recordToExport = record
                                exportLauncher.launch("transcription_${record.timestamp}.json")
                            },
                            onExportSessionJson = { sessionId, title, parts ->
                                sessionToExport = Pair(title, parts)
                                val cleanTitle = title.replace(Regex("[^A-Za-z0-9_]"), "_").lowercase()
                                exportSessionLauncher.launch("session_${cleanTitle}_${System.currentTimeMillis()}.json")
                            },
                            onExportAllJson = {
                                exportAllLauncher.launch("transcriptions_backup_${System.currentTimeMillis()}.json")
                            },
                            onImportJson = {
                                importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                            },
                            onSync = { viewModel.syncTranscriptions() },
                            defaultKeywords = defaultKeywords,
                            onOpenPlayback = { record -> activePlaybackRecord = record },
                            onSaveSpeaker = { viewModel.saveSpeaker(it) },
                            onRemoveGoldenSample = { viewModel.removeGoldenSample(it) },
                            audioPlayerContent = { uri -> AudioPlayerComponent(uri) }
                        )
                    }
                }
                2 -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        SpeakerManagementScreen(
                            speakers = uiState.speakers,
                            onSaveSpeaker = { viewModel.saveSpeaker(it) },
                            onDeleteSpeaker = { viewModel.deleteSpeaker(it) },
                            onRemoveGoldenSample = { viewModel.removeGoldenSample(it) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp)
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
                                text = "Transcribe Audio File(s)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Select a single audio file or select multiple sequential parts to transcribe as a connected session.",
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
                                Text("Choose Audio File(s)")
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
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
                                color = if (uiState.isRecording) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (uiState.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                        contentDescription = if (uiState.isRecording) "Stop Recording" else "Start Recording",
                                        tint = if (uiState.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            
                            Text(
                                text = if (uiState.isRecording) "Recording in Progress..." else "Capture Live Audio",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (uiState.useBluetoothMic) Icons.Default.Bluetooth else Icons.Default.Smartphone,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (uiState.useBluetoothMic) "Using Bluetooth Earbud Mic" else "Using Phone Microphone",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { viewModel.toggleBluetoothMic() }
                            ) {
                                Checkbox(
                                    checked = uiState.useBluetoothMic,
                                    onCheckedChange = { viewModel.toggleBluetoothMic() }
                                )
                                Text("Prefer Bluetooth Earbud Mic", style = MaterialTheme.typography.bodySmall)
                            }

                            Button(
                                onClick = {
                                    if (uiState.isRecording) {
                                        viewModel.stopRecording(context)
                                    } else {
                                        viewModel.startRecording(context)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (uiState.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(if (uiState.isRecording) "Stop & Finalize" else "Start Recording")
                            }
                            
                            if (uiState.isRecording) {
                                Text(
                                    "Recording is saved 'Direct-to-Disk' every few ms for fail-safe protection.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center
                                )
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Latest Transcription:", 
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    FilledTonalButton(
                                        onClick = {
                                            val matchingRecord = uiState.history.firstOrNull { it.text == text }
                                                ?: com.example.db.Transcription(
                                                    text = text,
                                                    summary = uiState.lastSummary,
                                                    audioUri = uiState.history.firstOrNull()?.audioUri
                                                )
                                            activePlaybackRecord = matchingRecord
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.heightIn(min = 36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Audiotrack,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Open Player", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
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

        if (pendingBatchUris != null) {
            BatchSelectionBottomSheet(
                uris = pendingBatchUris!!,
                onDismiss = { pendingBatchUris = null },
                onSequentialOption = {
                    val uris = pendingBatchUris!!
                    pendingBatchUris = null
                    val analyzed = viewModel.analyzeSelectedFilesForSequence(context, uris)
                    pendingSequentialFiles = analyzed
                },
                onSeparateOption = {
                    val uris = pendingBatchUris!!
                    pendingBatchUris = null
                    viewModel.transcribeBatchAudio(context, uris)
                }
            )
        }

        if (pendingSequentialFiles != null) {
            SequenceConfirmationSheet(
                files = pendingSequentialFiles!!,
                onDismiss = { pendingSequentialFiles = null },
                onConfirm = { orderedFiles, title ->
                    pendingSequentialFiles = null
                    viewModel.transcribeSequentialSession(context, orderedFiles, title)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchSelectionBottomSheet(
    uris: List<Uri>,
    onDismiss: () -> Unit,
    onSequentialOption: () -> Unit,
    onSeparateOption: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.LibraryMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "Batch Audio Selection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${uris.size} audio files selected • Choose processing strategy",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "How would you like to process these recordings?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Strategy Cards
            Card(
                onClick = onSequentialOption,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Multi-Part Sequential Session",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Stitch files into a single chronological session group. Perfect for multi-part continuous lectures, interviews, or voice notes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Card(
                onClick = onSeparateOption,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AudioFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Separate Standalone Recordings",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Process as separate independent recordings in the background queue. Perfect for unrelated individual files.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SequenceConfirmationSheet(
    files: List<com.example.ui.SequentialAudioFile>,
    onDismiss: () -> Unit,
    onConfirm: (List<com.example.ui.SequentialAudioFile>, String) -> Unit
) {
    var orderedFiles by remember(files) { mutableStateOf(files) }
    val defaultTitle = remember(files) {
        val first = files.firstOrNull()?.displayName ?: "Recording Session"
        val clean = first.substringBeforeLast(".")
            .replace(Regex("[_\\-]+part[\\-_\\d]+", RegexOption.IGNORE_CASE), "")
            .replace("_", " ")
            .replace("-", " ")
            .trim()
        clean.ifBlank { "Recording Session" }
    }
    var sessionTitle by remember { mutableStateOf(defaultTitle) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "Sequential Recording Session",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${orderedFiles.size} audio parts detected • Verify playback order",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = sessionTitle,
                onValueChange = { sessionTitle = it },
                label = { Text("Session Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )

            Text(
                text = "Audio Parts Sequence (Processed & linked in this order):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                orderedFiles.forEachIndexed { index, file ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                if (file.durationMs > 0) {
                                    Text(
                                        text = formatDuration(file.durationMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        val mutable = orderedFiles.toMutableList()
                                        val item = mutable.removeAt(index)
                                        mutable.add(index - 1, item)
                                        orderedFiles = mutable
                                    }
                                },
                                enabled = index > 0,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Move Up",
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (index < orderedFiles.size - 1) {
                                        val mutable = orderedFiles.toMutableList()
                                        val item = mutable.removeAt(index)
                                        mutable.add(index + 1, item)
                                        orderedFiles = mutable
                                    }
                                },
                                enabled = index < orderedFiles.size - 1,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "Move Down",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        onConfirm(orderedFiles, sessionTitle.ifBlank { "Sequential Session" })
                    },
                    modifier = Modifier.weight(1.6f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Transcribe (${orderedFiles.size} Parts)")
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
                TextButton(
                    onClick = {
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
                    },
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Text("${playbackSpeed}x")
                }
                
                IconButton(
                    onClick = {
                        mediaPlayer?.let {
                            it.seekTo((it.currentPosition - 10000).coerceAtLeast(0))
                            if (it.duration > 0) {
                                progress = it.currentPosition.toFloat() / it.duration
                            }
                        }
                    },
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                ) {
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
                    },
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
                
                IconButton(
                    onClick = {
                        mediaPlayer?.let {
                            it.seekTo((it.currentPosition + 10000).coerceAtMost(it.duration))
                            if (it.duration > 0) {
                                progress = it.currentPosition.toFloat() / it.duration
                            }
                        }
                    },
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Forward 10s"
                    )
                }
            }
        }
    }
}
