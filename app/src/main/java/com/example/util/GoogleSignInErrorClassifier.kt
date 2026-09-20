package com.example.util

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.common.api.ApiException

sealed class GoogleSignInResult {
    object UserCancellation : GoogleSignInResult()
    object NoCredentials : GoogleSignInResult()
    data class ConfigurationError(val description: String) : GoogleSignInResult()
    data class Failure(val message: String, val cause: Throwable? = null) : GoogleSignInResult()
}

object GoogleSignInErrorClassifier {
    const val TAG = "GoogleSignIn"

    fun classifyError(e: Throwable): GoogleSignInResult {
        return when {
            e is GetCredentialCancellationException -> {
                GoogleSignInResult.UserCancellation
            }
            e is NoCredentialException -> {
                GoogleSignInResult.NoCredentials
            }
            e is GetCredentialProviderConfigurationException -> {
                GoogleSignInResult.ConfigurationError(
                    "Credential Manager configuration error. Verify Web OAuth Client ID and SHA-1 fingerprint in Firebase / Google Cloud console."
                )
            }
            e is ApiException -> {
                when (e.statusCode) {
                    12501, 12500 -> GoogleSignInResult.UserCancellation
                    10 -> GoogleSignInResult.ConfigurationError(
                        "Google Auth DEVELOPER_ERROR (Status Code 10). Package name or SHA-1 fingerprint mismatch in OAuth 2.0 configuration."
                    )
                    7 -> GoogleSignInResult.Failure("Network connection error during Google sign-in.", e)
                    else -> GoogleSignInResult.Failure("Google Play Services error (Code ${e.statusCode}): ${e.message ?: "Unknown API error"}", e)
                }
            }
            e.message?.contains("DEVELOPER_ERROR", ignoreCase = true) == true ||
            e.message?.contains("code 10", ignoreCase = true) == true -> {
                GoogleSignInResult.ConfigurationError(
                    "Google Auth DEVELOPER_ERROR (Code 10). Package name or SHA-1 fingerprint mismatch in OAuth 2.0 configuration."
                )
            }
            e.message?.contains("cancel", ignoreCase = true) == true -> {
                GoogleSignInResult.UserCancellation
            }
            e is GetCredentialUnknownException -> {
                GoogleSignInResult.Failure("Unknown Credential Manager failure: ${e.localizedMessage ?: e.message}", e)
            }
            e is GetCredentialException -> {
                GoogleSignInResult.Failure("Credential Manager exception: ${e.localizedMessage ?: e.message}", e)
            }
            else -> {
                GoogleSignInResult.Failure(e.localizedMessage ?: e.message ?: "Google Sign-In failed.", e)
            }
        }
    }
}
