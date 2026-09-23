package com.example.util

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.common.api.ApiException

sealed class GoogleSignInResult {
    object UserCancellation : GoogleSignInResult()
    object NoCredentials : GoogleSignInResult()
    data class ConfigurationError(val description: String) : GoogleSignInResult()
    data class Failure(val message: String) : GoogleSignInResult()
}

object GoogleSignInErrorClassifier {
    fun classifyError(throwable: Throwable): GoogleSignInResult {
        if (throwable is GetCredentialCancellationException) {
            return GoogleSignInResult.UserCancellation
        }
        if (throwable is NoCredentialException) {
            return GoogleSignInResult.NoCredentials
        }

        if (throwable is ApiException) {
            if (throwable.statusCode == 12501) {
                return GoogleSignInResult.UserCancellation
            }
            if (throwable.statusCode == 10) {
                return GoogleSignInResult.ConfigurationError(
                    "Google Sign-In misconfigured (DEVELOPER_ERROR code 10). Check SHA-1 fingerprint in Firebase Console."
                )
            }
        }

        val message = throwable.message.orEmpty()
        val lowerMessage = message.lowercase()

        if (lowerMessage.contains("canceled") || lowerMessage.contains("cancelled") || lowerMessage.contains("user cancel")) {
            return GoogleSignInResult.UserCancellation
        }

        if (lowerMessage.contains("developer_error") || lowerMessage.contains("code 10")) {
            return GoogleSignInResult.ConfigurationError(
                "Google Sign-In misconfigured ($message). Check SHA-1 fingerprint in Firebase Console."
            )
        }

        return GoogleSignInResult.Failure(if (message.isBlank()) "Google Sign-In failed" else message)
    }
}
