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
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
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

@OptIn(ExperimentalMaterial3Api::class)
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
                        android.widget.Toast.makeText(context, "Signed in successfully as $email", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        android.widget.Toast.makeText(context, "Sign in failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Failed to parse Google credentials: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
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
                        text = "Enter your OpenRouter API key to access free models like google/gemini-2.0-flash-lite:free or deepseek/deepseek-chat:free.",
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
        }
    }
}
