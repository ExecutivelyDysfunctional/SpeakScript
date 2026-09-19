package com.example.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import com.example.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation

import com.example.VisualErrorBanner
import com.example.VisualInfoBanner
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.AutoAwesome

private const val APP_PACKAGE_NAME = "com.aistudio.transcribeai.xyzq"
private const val APP_DEBUG_SHA1 = "B5:9F:F2:77:10:96:D0:E2:EF:73:0C:86:98:1F:DE:D4:4C:D6:66:63"

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    uiState: UiState,
    onThemeModeChange: (ThemeMode) -> Unit,
    onSaveApiKey: (String) -> Unit,
    onSaveOpenRouterApiKey: (String) -> Unit,
    onSaveGroqApiKey: (String) -> Unit,
    onAiProviderChange: (AiProvider) -> Unit,
    onSaveWebClientId: (String) -> Unit,
    onExportAllJson: () -> Unit = {},
    onImportJson: () -> Unit = {},
    onDriveConnected: (String?) -> Unit = {},
    onSignInSuccess: (String) -> Unit = {},
    onOpenSpeakers: (() -> Unit)? = null,
    onSaveLocation: (com.example.db.LocationProfile) -> Unit = {},
    onDeleteLocation: (com.example.db.LocationProfile) -> Unit = {},
    onUpdateBiometricCalibration: (sensitivity: String, sliceSec: Int, maxSpeakers: Int, mode: String) -> Unit = { _, _, _, _ -> },
    onRunBiometricCalibrationBenchmark: () -> Unit = {},
    onClearBiometricCalibrationReport: () -> Unit = {},
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showNoCredentialsDialog by remember { mutableStateOf(false) }
    var noCredentialsErrorMessage by remember { mutableStateOf("") }
    var showEmailAuthDialog by remember { mutableStateOf(false) }
    var showCloudSettingsDialog by remember { mutableStateOf(false) }
    var isSigningIn by remember { mutableStateOf(false) }

    val copyToClipboard: (String, String) -> Unit = { label, text ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        android.widget.Toast.makeText(context, "$label copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
    }

    val serverClientId = if (uiState.webClientId.isNotBlank()) {
        uiState.webClientId
    } else {
        context.getString(R.string.default_web_client_id)
    }

    val handleCredentialResult: (androidx.credentials.Credential) -> Unit = { credential ->
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                FirebaseAuth.getInstance().signInWithCredential(authCredential)
                    .addOnSuccessListener {
                        val email = it.user?.email ?: "Google Account"
                        onSignInSuccess(email)
                        android.widget.Toast.makeText(context, "Signed in successfully as $email", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        android.widget.Toast.makeText(context, "Sign in failed: ${e.localizedMessage ?: e.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Failed to parse Google credentials: ${e.localizedMessage ?: e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        } else {
            android.widget.Toast.makeText(context, "Unsupported credential type: ${credential.type}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Visual Error Banner inside Settings
            uiState.error?.let { err ->
                VisualErrorBanner(
                    errorMessage = err,
                    onDismiss = { }
                )
            }

            // Visual Info/Success Banner inside Settings
            uiState.infoMessage?.let { info ->
                VisualInfoBanner(
                    infoMessage = info,
                    onDismiss = { }
                )
            }

            // System Setup & Persistent Feedback Overview Deck
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "System Setup & Feedback Overview",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Text(
                        text = "Persistent indicators of your system configuration, active AI credentials, cloud sync status, and biometric profiles.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Account Status
                        AssistChip(
                            onClick = { showEmailAuthDialog = true },
                            label = {
                                val isAuth = uiState.isAuthenticated && !uiState.isAnonymous
                                Text(if (isAuth) (uiState.userEmail ?: "Signed In") else "Guest Mode (Local Only)")
                            },
                            leadingIcon = {
                                val isAuth = uiState.isAuthenticated && !uiState.isAnonymous
                                Icon(
                                    imageVector = if (isAuth) Icons.Default.AccountCircle else Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (isAuth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }
                        )

                        // 2. Drive Sync Status
                        AssistChip(
                            onClick = { showCloudSettingsDialog = true },
                            label = { Text(if (uiState.isDriveConnected) "Google Drive Connected" else "Drive Sync Offline") },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (uiState.isDriveConnected) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = if (uiState.isDriveConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }
                        )

                        // 3. AI Provider & Key Status
                        val aiModelLabel = when (uiState.aiProvider) {
                            AiProvider.GEMINI -> if (uiState.customApiKey.isNotBlank()) "Gemini 3.6 Flash (Custom Key)" else "Gemini 3.6 Flash (Default Key)"
                            AiProvider.OPENROUTER -> if (uiState.openRouterApiKey.isNotBlank()) "OpenRouter (Configured)" else "OpenRouter (No Key)"
                            AiProvider.GROQ -> if (uiState.groqApiKey.isNotBlank()) "Groq Whisper & Llama 3.3 (Configured)" else "Groq (No Key)"
                        }
                        AssistChip(
                            onClick = { },
                            label = { Text(aiModelLabel) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        )

                        // 4. Voice Biometrics Status
                        val goldenCount = uiState.speakers.count { !it.goldenSampleAudioUri.isNullOrBlank() }
                        AssistChip(
                            onClick = { onOpenSpeakers?.invoke() },
                            label = { Text("${uiState.speakers.size} Speakers ($goldenCount Golden Samples)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        )

                        // 5. Saved Locations / Venue Presets
                        AssistChip(
                            onClick = { },
                            label = { Text("${uiState.locations.size} Venue Presets") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        )
                    }
                }
            }

            Text(
                text = "AI Provider & API Keys",
                style = MaterialTheme.typography.titleLarge
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Active AI Provider",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Select which AI provider/backend to use for transcription, summaries, and queries.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val providers = listOf(
                            AiProvider.GEMINI to "Gemini",
                            AiProvider.OPENROUTER to "OpenRouter",
                            AiProvider.GROQ to "Groq"
                        )
                        providers.forEachIndexed { index, (provider, label) ->
                            SegmentedButton(
                                selected = uiState.aiProvider == provider,
                                onClick = { onAiProviderChange(provider) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = providers.size)
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }

            // Gemini BYOK Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Google Gemini API Key",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Enter your Google Gemini API key to enable AI transcription and analysis. Get your free key from Google AI Studio.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    var apiKeyInput by remember(uiState.customApiKey) { mutableStateOf(uiState.customApiKey) }
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("Gemini API Key") },
                        placeholder = { Text("AIzaSy...") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                onSaveApiKey(apiKeyInput)
                                android.widget.Toast.makeText(context, "Gemini API Key saved", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save Gemini Key")
                        }
                        if (uiState.customApiKey.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    apiKeyInput = ""
                                    onSaveApiKey("")
                                    android.widget.Toast.makeText(context, "Gemini API Key cleared", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("Clear")
                            }
                        }
                    }

                    Text(
                        text = if (uiState.customApiKey.isNotBlank()) "Status: Using custom Gemini key" else "Status: Using build-time configuration (or missing)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (uiState.customApiKey.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }

            // OpenRouter API Key Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "OpenRouter (Free Models)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Enter your OpenRouter API key to access free models like google/gemini-2.5-flash:free or deepseek/deepseek-chat:free.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    var openRouterInput by remember(uiState.openRouterApiKey) { mutableStateOf(uiState.openRouterApiKey) }
                    OutlinedTextField(
                        value = openRouterInput,
                        onValueChange = { openRouterInput = it },
                        label = { Text("OpenRouter API Key") },
                        placeholder = { Text("sk-or-v1-...") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                onSaveOpenRouterApiKey(openRouterInput)
                                android.widget.Toast.makeText(context, "OpenRouter API Key saved", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save OpenRouter Key")
                        }
                        if (uiState.openRouterApiKey.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    openRouterInput = ""
                                    onSaveOpenRouterApiKey("")
                                    android.widget.Toast.makeText(context, "OpenRouter API Key cleared", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("Clear")
                            }
                        }
                    }

                    Text(
                        text = if (uiState.openRouterApiKey.isNotBlank()) "Status: OpenRouter API key configured" else "Status: No OpenRouter key entered",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (uiState.openRouterApiKey.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Groq API Key Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Groq API (Free Key)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Enter your Groq API key for ultra-fast free inference with Llama 3.3 / Llama 3.1 models.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    var groqInput by remember(uiState.groqApiKey) { mutableStateOf(uiState.groqApiKey) }
                    OutlinedTextField(
                        value = groqInput,
                        onValueChange = { groqInput = it },
                        label = { Text("Groq API Key") },
                        placeholder = { Text("gsk_...") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                onSaveGroqApiKey(groqInput)
                                android.widget.Toast.makeText(context, "Groq API Key saved", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save Groq Key")
                        }
                        if (uiState.groqApiKey.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    groqInput = ""
                                    onSaveGroqApiKey("")
                                    android.widget.Toast.makeText(context, "Groq API Key cleared", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("Clear")
                            }
                        }
                    }

                    Text(
                        text = if (uiState.groqApiKey.isNotBlank()) "Status: Groq API key configured" else "Status: No Groq key entered",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (uiState.groqApiKey.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleLarge
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Theme Mode",
                        style = MaterialTheme.typography.titleMedium
                    )

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val modes = listOf(
                            Triple(ThemeMode.SYSTEM, "System", Icons.Default.BrightnessAuto),
                            Triple(ThemeMode.LIGHT, "Light", Icons.Default.LightMode),
                            Triple(ThemeMode.DARK, "Dark", Icons.Default.DarkMode)
                        )
                        modes.forEachIndexed { index, (mode, label, icon) ->
                            SegmentedButton(
                                selected = uiState.themeMode == mode,
                                onClick = { onThemeModeChange(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                                icon = {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                                    )
                                }
                            ) {
                                Text(label)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DarkMode,
                                contentDescription = "Dark Theme",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Dark Theme",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = when (uiState.themeMode) {
                                        ThemeMode.DARK -> "Enabled"
                                        ThemeMode.LIGHT -> "Disabled"
                                        ThemeMode.SYSTEM -> "System default"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = uiState.themeMode == ThemeMode.DARK,
                            onCheckedChange = { isChecked ->
                                onThemeModeChange(if (isChecked) ThemeMode.DARK else ThemeMode.LIGHT)
                            }
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = "Account & Cloud Sync",
                style = MaterialTheme.typography.titleLarge
            )
            
            val isAnonymous = uiState.isAnonymous

            if (!uiState.isAuthenticated || isAnonymous) {
                Text(
                    text = "Sign in to sync your transcriptions across devices. (All transcription features work offline without an account).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Sign In with Google Button
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isSigningIn = true
                            try {
                                val activity = context.findActivity() ?: (context as? Activity)
                                if (activity == null) {
                                    android.widget.Toast.makeText(context, "Cannot open sign-in: Activity context unavailable", android.widget.Toast.LENGTH_SHORT).show()
                                    isSigningIn = false
                                    return@launch
                                }

                                val credentialManager = CredentialManager.create(activity)

                                // Attempt 1: GetSignInWithGoogleOption (Standard explicit button-click flow)
                                val signInOption = GetSignInWithGoogleOption.Builder(serverClientId)
                                    .build()

                                val request = GetCredentialRequest.Builder()
                                    .addCredentialOption(signInOption)
                                    .build()

                                try {
                                    val result = credentialManager.getCredential(activity, request)
                                    handleCredentialResult(result.credential)
                                } catch (initialEx: Exception) {
                                    if (initialEx is GetCredentialCancellationException || 
                                        initialEx.javaClass.simpleName.contains("Cancellation", ignoreCase = true) ||
                                        (initialEx.message?.contains("cancel", ignoreCase = true) == true)) {
                                        android.widget.Toast.makeText(context, "Google Sign-In canceled by user", android.widget.Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }

                                    // Attempt 2: Fallback to GetGoogleIdOption
                                    try {
                                        val googleIdOption = GetGoogleIdOption.Builder()
                                            .setFilterByAuthorizedAccounts(false)
                                            .setServerClientId(serverClientId)
                                            .setAutoSelectEnabled(false)
                                            .build()

                                        val fallbackRequest = GetCredentialRequest.Builder()
                                            .addCredentialOption(googleIdOption)
                                            .build()

                                        val fallbackResult = credentialManager.getCredential(activity, fallbackRequest)
                                        handleCredentialResult(fallbackResult.credential)
                                    } catch (fallbackEx: Exception) {
                                        if (fallbackEx is GetCredentialCancellationException || 
                                            fallbackEx.javaClass.simpleName.contains("Cancellation", ignoreCase = true) ||
                                            (fallbackEx.message?.contains("cancel", ignoreCase = true) == true)) {
                                            android.widget.Toast.makeText(context, "Google Sign-In canceled by user", android.widget.Toast.LENGTH_SHORT).show()
                                            return@launch
                                        }
                                        fallbackEx.printStackTrace()
                                        noCredentialsErrorMessage = fallbackEx.localizedMessage ?: fallbackEx.message ?: "No credentials available"
                                        showNoCredentialsDialog = true
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                if (e !is GetCredentialCancellationException && !e.javaClass.simpleName.contains("Cancellation", ignoreCase = true)) {
                                    noCredentialsErrorMessage = e.localizedMessage ?: e.message ?: "Authentication error"
                                    showNoCredentialsDialog = true
                                }
                            } finally {
                                isSigningIn = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSigningIn
                ) {
                    if (isSigningIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connecting to Google...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Google",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign In with Google")
                    }
                }

                var showDriveError10Dialog by remember { mutableStateOf(false) }

                val driveSignInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == android.app.Activity.RESULT_CANCELED) {
                        onDriveConnected(null)
                        android.widget.Toast.makeText(context, "Google Drive connection canceled", android.widget.Toast.LENGTH_SHORT).show()
                        return@rememberLauncherForActivityResult
                    }
                    val intentData = result.data
                    if (intentData == null) {
                        onDriveConnected(null)
                        android.widget.Toast.makeText(context, "Google Drive connection failed: No response data received", android.widget.Toast.LENGTH_SHORT).show()
                        return@rememberLauncherForActivityResult
                    }
                    val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(intentData)
                    try {
                        val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                        if (account != null) {
                            onDriveConnected(account.email)
                            android.widget.Toast.makeText(context, "Drive connected: ${account.email}", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            onDriveConnected(null)
                            android.widget.Toast.makeText(context, "Google Drive connection failed: Unable to retrieve account details", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: com.google.android.gms.common.api.ApiException) {
                        onDriveConnected(null)
                        if (e.statusCode == 10) {
                            showDriveError10Dialog = true
                        } else if (e.statusCode == 12501 || e.statusCode == 12500) {
                            android.widget.Toast.makeText(context, "Google Drive connection canceled by user (Code ${e.statusCode})", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(context, "Drive connection failed (Code ${e.statusCode}): ${e.message ?: "API Error"}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        onDriveConnected(null)
                        android.widget.Toast.makeText(context, "Drive connection failed: ${e.localizedMessage ?: e.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }

                if (uiState.isDriveConnected) {
                    Button(
                        onClick = {
                            val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .build()
                            val mGoogleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
                            mGoogleSignInClient.signOut().addOnCompleteListener {
                                onDriveConnected(null)
                                android.widget.Toast.makeText(context, "Drive disconnected", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = "Drive disconnected",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Disconnect Google Drive (${uiState.driveEmail ?: "Connected"})")
                    }
                } else {
                    Button(
                        onClick = {
                            val gsoBuilder = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestEmail()
                                .requestScopes(com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.file"))
                            if (serverClientId.isNotBlank() && !serverClientId.contains("default_web_client_id")) {
                                gsoBuilder.requestIdToken(serverClientId)
                            }
                            val mGoogleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gsoBuilder.build())
                            driveSignInLauncher.launch(mGoogleSignInClient.signInIntent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = "Drive",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connect Google Drive")
                    }
                }

                if (showDriveError10Dialog) {
                    AlertDialog(
                        onDismissRequest = { showDriveError10Dialog = false },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Info",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Google Drive Setup (Error 10)")
                            }
                        },
                        text = {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Why Drive connection returned Error 10:",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Error 10 (DEVELOPER_ERROR) indicates that Google Cloud / Firebase requires registering this app's package name and SHA-1 certificate fingerprint under OAuth 2.0 Client IDs.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "To enable Google Drive Cloud Auto-Sync, register this build's SHA-1 key in your Google Cloud / Firebase Console.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("Package: $APP_PACKAGE_NAME", style = MaterialTheme.typography.labelSmall)
                                        Text("SHA-1: $APP_DEBUG_SHA1", style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                Text(
                                    text = "Local Device Features Active:\n" +
                                            "• Audio recordings automatically save to Music/TranscribeAI on your phone.\n" +
                                            "• Transcriptions and AI summaries work 100% locally.\n" +
                                            "• You can export your data and audio files anytime.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        confirmButton = {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = {
                                        copyToClipboard("App Credentials", "Package: $APP_PACKAGE_NAME\nSHA-1: $APP_DEBUG_SHA1")
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy SHA-1")
                                }
                                Button(onClick = { showDriveError10Dialog = false }) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    )
                }


                // Sign In with Email Button
                OutlinedButton(
                    onClick = { showEmailAuthDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "Email",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign In with Email / Password")
                }

                // Cloud / Firebase Config Button
                TextButton(
                    onClick = { showCloudSettingsDialog = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("OAuth & Firebase Credentials Setup", style = MaterialTheme.typography.bodySmall)
                }

                // Dialog: Why "No credentials available" occurs & how to resolve
                if (showNoCredentialsDialog) {
                    AlertDialog(
                        onDismissRequest = { showNoCredentialsDialog = false },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Info",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Google Sign-In Credentials")
                            }
                        },
                        text = {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Why you are seeing 'No credentials available':",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Even if you are logged into multiple Google accounts on your phone, Google Play Services verifies whether this app's package name and SHA-1 certificate fingerprint are registered in Google Cloud / Firebase.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "If the build's SHA-1 fingerprint has not been added to your Google Cloud Console / Firebase project under an Android OAuth 2.0 Client ID, Google will not present your accounts to the app.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "App Identification Details:",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text("Package: $APP_PACKAGE_NAME", style = MaterialTheme.typography.labelSmall)
                                        Text("SHA-1: $APP_DEBUG_SHA1", style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                Text(
                                    text = "Options to continue:\n" +
                                            "1. Local/Offline Mode: You can transcribe and analyze audio right now without signing in!\n" +
                                            "2. Email Sign-In: Use Email/Password to sync across devices.\n" +
                                            "3. Register SHA-1: In your Google Cloud / Firebase Console, add this SHA-1 to authorize Google Sign-In.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        confirmButton = {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = {
                                        copyToClipboard("App Credentials", "Package: $APP_PACKAGE_NAME\nSHA-1: $APP_DEBUG_SHA1")
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy SHA-1")
                                }
                                Button(onClick = { showNoCredentialsDialog = false }) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    )
                }

                // Dialog: Email / Password Sign In & Registration
                if (showEmailAuthDialog) {
                    var emailInput by remember { mutableStateOf("") }
                    var passwordInput by remember { mutableStateOf("") }
                    var isRegisterMode by remember { mutableStateOf(false) }
                    var emailLoading by remember { mutableStateOf(false) }
                    var emailErrorMessage by remember { mutableStateOf<String?>(null) }

                    AlertDialog(
                        onDismissRequest = { if (!emailLoading) showEmailAuthDialog = false },
                        title = {
                            Text(if (isRegisterMode) "Create Account" else "Sign In with Email")
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = if (isRegisterMode) 
                                        "Enter your email and password to create a sync account."
                                    else 
                                        "Enter your email and password to sign in and sync your history.",
                                    style = MaterialTheme.typography.bodySmall
                                )

                                OutlinedTextField(
                                    value = emailInput,
                                    onValueChange = { emailInput = it; emailErrorMessage = null },
                                    label = { Text("Email") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = passwordInput,
                                    onValueChange = { passwordInput = it; emailErrorMessage = null },
                                    label = { Text("Password") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (emailErrorMessage != null) {
                                    Text(
                                        text = emailErrorMessage ?: "",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                TextButton(
                                    onClick = { isRegisterMode = !isRegisterMode; emailErrorMessage = null },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text(
                                        text = if (isRegisterMode) "Already have an account? Sign In" else "Need an account? Create one",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val email = emailInput.trim()
                                    val password = passwordInput.trim()
                                    if (email.isBlank() || password.isBlank()) {
                                        emailErrorMessage = "Email and password cannot be empty."
                                        return@Button
                                    }
                                    if (password.length < 6) {
                                        emailErrorMessage = "Password must be at least 6 characters."
                                        return@Button
                                    }
                                    emailLoading = true
                                    val auth = FirebaseAuth.getInstance()
                                    if (isRegisterMode) {
                                        auth.createUserWithEmailAndPassword(email, password)
                                            .addOnSuccessListener {
                                                emailLoading = false
                                                showEmailAuthDialog = false
                                                android.widget.Toast.makeText(context, "Account created: $email", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                            .addOnFailureListener { e ->
                                                emailLoading = false
                                                emailErrorMessage = e.localizedMessage ?: "Registration failed"
                                            }
                                    } else {
                                        auth.signInWithEmailAndPassword(email, password)
                                            .addOnSuccessListener {
                                                emailLoading = false
                                                showEmailAuthDialog = false
                                                android.widget.Toast.makeText(context, "Signed in as $email", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                            .addOnFailureListener { e ->
                                                emailLoading = false
                                                emailErrorMessage = e.localizedMessage ?: "Sign in failed"
                                            }
                                    }
                                },
                                enabled = !emailLoading
                            ) {
                                if (emailLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(if (isRegisterMode) "Create Account" else "Sign In")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showEmailAuthDialog = false },
                                enabled = !emailLoading
                            ) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                // Dialog: Cloud / OAuth Settings
                if (showCloudSettingsDialog) {
                    var customClientIdInput by remember(uiState.webClientId) { mutableStateOf(uiState.webClientId) }

                    AlertDialog(
                        onDismissRequest = { showCloudSettingsDialog = false },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Cloud Settings",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("OAuth & Cloud Credentials")
                            }
                        },
                        text = {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "To enable Google Sign-In with your own Firebase / Google Cloud project, ensure the Android App certificate and Web Client ID match:",
                                    style = MaterialTheme.typography.bodySmall
                                )

                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Package Name:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            IconButton(
                                                onClick = { copyToClipboard("Package Name", APP_PACKAGE_NAME) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        Text(APP_PACKAGE_NAME, style = MaterialTheme.typography.bodySmall)

                                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("SHA-1 Fingerprint:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            IconButton(
                                                onClick = { copyToClipboard("SHA-1 Fingerprint", APP_DEBUG_SHA1) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        Text(APP_DEBUG_SHA1, style = MaterialTheme.typography.bodySmall)
                                    }
                                }

                                Text(
                                    text = "Custom Web Client ID (Optional):",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                OutlinedTextField(
                                    value = customClientIdInput,
                                    onValueChange = { customClientIdInput = it },
                                    placeholder = { Text(context.getString(R.string.default_web_client_id)) },
                                    label = { Text("Web Client ID") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            customClientIdInput = ""
                                            onSaveWebClientId("")
                                            android.widget.Toast.makeText(context, "Reset to default Web Client ID", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Reset", style = MaterialTheme.typography.bodySmall)
                                    }

                                    Button(
                                        onClick = {
                                            onSaveWebClientId(customClientIdInput.trim())
                                            android.widget.Toast.makeText(context, "Web Client ID saved", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Save", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(onClick = { showCloudSettingsDialog = false }) {
                                Text("Done")
                            }
                        }
                    )
                }

            } else {
                Text(
                    text = "Signed in as ${uiState.userEmail}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "Transcriptions and summaries are automatically backed up to your account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Button(
                    onClick = {
                        try {
                            FirebaseAuth.getInstance().signOut()
                            android.widget.Toast.makeText(context, "Signed out successfully", android.widget.Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Sign out error: ${e.localizedMessage ?: e.message}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Sign Out")
                }
            }

            Text(
                text = "Data Portability & Backup (JSON)",
                style = MaterialTheme.typography.titleLarge
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Offline JSON Backup & Restore",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = "Export your entire journal database to a single portable JSON file, or restore a backup. This allows complete offline data ownership and smooth data transfers between mobile and desktop without cloud dependencies.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Local Database: ${uiState.history.size} transcription records stored",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onExportAllJson,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export JSON")
                        }

                        OutlinedButton(
                            onClick = onImportJson,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import JSON")
                        }
                    }
                }
            }

            Text(
                text = "Voice Biometrics & Known Speakers",
                style = MaterialTheme.typography.titleLarge
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Known Speakers Roster",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = "Manage registered speaker profiles, personalize custom color badges, and maintain verified Golden Sample reference audio clips for voice recognition.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Registered: ${uiState.speakers.size} speakers (${uiState.speakers.count { it.hasGoldenSample }} with Golden Sample)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (onOpenSpeakers != null) {
                        Button(
                            onClick = onOpenSpeakers,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Manage Speakers & Golden Samples")
                        }
                    }
                }
            }

            // Biometric Calibration & Fine-Tuning Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Acoustic Matching & Fine-Tuning",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = "Fine-tune voice profile matching sensitivity thresholds, reference slice extraction durations, and acoustic spectrum analysis profiles.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 1. Acoustic Sensitivity Selector
                    Text(
                        text = "Acoustic Matching Sensitivity",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val sensitivityOptions = listOf("Strict", "Balanced", "High Recall")
                        sensitivityOptions.forEach { opt ->
                            FilterChip(
                                selected = uiState.biometricSensitivity == opt,
                                onClick = {
                                    onUpdateBiometricCalibration(
                                        opt,
                                        uiState.biometricSliceDurationSec,
                                        uiState.biometricMaxSpeakers,
                                        uiState.biometricAcousticMode
                                    )
                                },
                                label = { Text(opt, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 2. Reference Audio Slice Duration
                    Text(
                        text = "Reference Audio Slice Length",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val durationOptions = listOf(5 to "5s (Fast)", 10 to "10s (Std)", 15 to "15s (Precise)")
                        durationOptions.forEach { (sec, label) ->
                            FilterChip(
                                selected = uiState.biometricSliceDurationSec == sec,
                                onClick = {
                                    onUpdateBiometricCalibration(
                                        uiState.biometricSensitivity,
                                        sec,
                                        uiState.biometricMaxSpeakers,
                                        uiState.biometricAcousticMode
                                    )
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 3. Max Bundled Reference Speakers
                    Text(
                        text = "Max Bundled Speaker Profiles",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val maxOptions = listOf(3 to "3 Speakers", 5 to "5 (Default)", 10 to "10 Max")
                        maxOptions.forEach { (cap, label) ->
                            FilterChip(
                                selected = uiState.biometricMaxSpeakers == cap,
                                onClick = {
                                    onUpdateBiometricCalibration(
                                        uiState.biometricSensitivity,
                                        uiState.biometricSliceDurationSec,
                                        cap,
                                        uiState.biometricAcousticMode
                                    )
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 4. Acoustic Spectrum Mode
                    Text(
                        text = "Acoustic Analysis Spectrum",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val modeOptions = listOf("Standard", "Enhanced Harmonic", "Noise Suppressed")
                        modeOptions.forEach { mode ->
                            FilterChip(
                                selected = uiState.biometricAcousticMode == mode,
                                onClick = {
                                    onUpdateBiometricCalibration(
                                        uiState.biometricSensitivity,
                                        uiState.biometricSliceDurationSec,
                                        uiState.biometricMaxSpeakers,
                                        mode
                                    )
                                },
                                label = { Text(if (mode == "Enhanced Harmonic") "Harmonics" else if (mode == "Noise Suppressed") "Noise Filter" else "Standard", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Calibration Benchmark Button
                    OutlinedButton(
                        onClick = onRunBiometricCalibrationBenchmark,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Run Calibration Benchmark")
                    }

                    // Calibration Report Card
                    if (uiState.biometricCalibrationReport != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Biometric Calibration Report",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    IconButton(
                                        onClick = onClearBiometricCalibrationReport,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Clear Report", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = uiState.biometricCalibrationReport,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Location Tiers & Venue Directory",
                style = MaterialTheme.typography.titleLarge
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Saved Venue Presets",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = "Preset locations automatically matched against Gemini summary location extractions. Track visits and manage tiers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    var showAddLocationDialog by remember { mutableStateOf(false) }

                    Button(
                        onClick = { showAddLocationDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Venue Preset")
                    }

                    if (uiState.locations.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        uiState.locations.forEach { loc ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = loc.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer
                                            ) {
                                                Text(
                                                    text = loc.tier,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        if (!loc.address.isNullOrBlank()) {
                                            Text(
                                                text = loc.address!!,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = "Visits: ${loc.visitCount}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteLocation(loc) },
                                        modifier = Modifier.sizeIn(minWidth = 40.dp, minHeight = 40.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete location",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (showAddLocationDialog) {
                        var nameInput by remember { mutableStateOf("") }
                        var addressInput by remember { mutableStateOf("") }
                        var selectedTier by remember { mutableStateOf("Primary") }

                        AlertDialog(
                            onDismissRequest = { showAddLocationDialog = false },
                            title = { Text("Add Venue Preset") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = nameInput,
                                        onValueChange = { nameInput = it },
                                        label = { Text("Location Name (e.g. Client Office)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = addressInput,
                                        onValueChange = { addressInput = it },
                                        label = { Text("Address / GPS notes") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        if (nameInput.isNotBlank()) {
                                            val newLoc = com.example.db.LocationProfile(
                                                name = nameInput.trim(),
                                                address = addressInput.trim().takeIf { it.isNotBlank() },
                                                tier = selectedTier,
                                                visitCount = 1
                                            )
                                            onSaveLocation(newLoc)
                                            showAddLocationDialog = false
                                        }
                                    }
                                ) {
                                    Text("Save")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showAddLocationDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
