package com.example.ui

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
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import com.example.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: UiState,
    onThemeModeChange: (ThemeMode) -> Unit,
    onSaveApiKey: (String) -> Unit,
    onSaveOpenRouterApiKey: (String) -> Unit,
    onSaveGroqApiKey: (String) -> Unit,
    onAiProviderChange: (AiProvider) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

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
                text = "Account",
                style = MaterialTheme.typography.titleLarge
            )
            
            val isAnonymous = uiState.isAnonymous

            if (!uiState.isAuthenticated || isAnonymous) {
                Text(
                    text = "Sign in with Google to sync your transcriptions across devices securely.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                val credentialManager = CredentialManager.create(context)
                                val googleIdOption = GetGoogleIdOption.Builder()
                                    .setFilterByAuthorizedAccounts(false)
                                    .setServerClientId(context.getString(R.string.default_web_client_id))
                                    .setAutoSelectEnabled(false)
                                    .build()
                                
                                val request = GetCredentialRequest.Builder()
                                    .addCredentialOption(googleIdOption)
                                    .build()

                                val result = credentialManager.getCredential(context, request)
                                val credential = result.credential
                                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                                    val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                                    FirebaseAuth.getInstance().signInWithCredential(authCredential)
                                        .addOnSuccessListener {
                                            android.widget.Toast.makeText(context, "Signed in successfully", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        .addOnFailureListener { e ->
                                            android.widget.Toast.makeText(context, "Sign in failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                android.widget.Toast.makeText(context, "Google Sign-In Error: ${e.localizedMessage ?: e.message}", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sign In with Google")
                }
            } else {
                Text("Signed in as ${uiState.userEmail}", style = MaterialTheme.typography.bodyMedium)
                
                Button(
                    onClick = {
                        try {
                            FirebaseAuth.getInstance().signOut()
                            android.widget.Toast.makeText(context, "Signed out successfully", android.widget.Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Sign Out")
                }
            }
        }
    }
}
