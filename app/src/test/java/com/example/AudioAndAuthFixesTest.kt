package com.example

import android.media.AudioFormat
import android.media.MediaRecorder
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import com.example.util.AudioConfigHelper
import com.example.util.GoogleSignInErrorClassifier
import com.example.util.GoogleSignInResult
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AudioAndAuthFixesTest {

    @Test
    fun testAudioConfigHelper_validBufferSizeCalculation() {
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val bufferSize = AudioConfigHelper.calculateValidBufferSize(sampleRate, channelConfig, audioFormat)
        assertTrue("Buffer size should be greater than 0", bufferSize > 0)
    }

    @Test
    fun testAudioConfigHelper_selectSupportedSampleRate() {
        val source = MediaRecorder.AudioSource.MIC
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val selectedRate = AudioConfigHelper.selectSupportedSampleRate(source, channelConfig, audioFormat)
        assertTrue("Selected rate should be > 0", selectedRate > 0)
        assertTrue("Selected rate should be one of 16000, 44100, 48000, 22050, 8000",
            listOf(16000, 44100, 48000, 22050, 8000).contains(selectedRate))
    }

    @Test
    fun testGoogleSignInErrorClassifier_cancellation() {
        val cancelEx = GetCredentialCancellationException("User canceled")
        val result = GoogleSignInErrorClassifier.classifyError(cancelEx)
        assertTrue("Should be UserCancellation", result is GoogleSignInResult.UserCancellation)

        val apiCancelEx = ApiException(Status(12501, "User canceled"))
        val apiResult = GoogleSignInErrorClassifier.classifyError(apiCancelEx)
        assertTrue("Should be UserCancellation", apiResult is GoogleSignInResult.UserCancellation)

        val cancelMsgEx = RuntimeException("The user canceled the operation")
        val resultMsg = GoogleSignInErrorClassifier.classifyError(cancelMsgEx)
        assertTrue("Should be UserCancellation", resultMsg is GoogleSignInResult.UserCancellation)
    }

    @Test
    fun testGoogleSignInErrorClassifier_noCredentials() {
        val noCredEx = NoCredentialException("No credentials found")
        val result = GoogleSignInErrorClassifier.classifyError(noCredEx)
        assertTrue("Should be NoCredentials", result is GoogleSignInResult.NoCredentials)
    }

    @Test
    fun testGoogleSignInErrorClassifier_configurationError() {
        val devErrEx = RuntimeException("Google Sign-In failed with DEVELOPER_ERROR (code 10)")
        val result = GoogleSignInErrorClassifier.classifyError(devErrEx)
        assertTrue("Should be ConfigurationError", result is GoogleSignInResult.ConfigurationError)
        val configErr = result as GoogleSignInResult.ConfigurationError
        assertTrue(configErr.description.contains("SHA-1") || configErr.description.contains("10"))

        val apiDevErr = ApiException(Status(10, "DEVELOPER_ERROR"))
        val apiResult = GoogleSignInErrorClassifier.classifyError(apiDevErr)
        assertTrue("Should be ConfigurationError", apiResult is GoogleSignInResult.ConfigurationError)
    }

    @Test
    fun testGoogleSignInErrorClassifier_genericFailure() {
        val genericEx = RuntimeException("Network timeout occurred")
        val result = GoogleSignInErrorClassifier.classifyError(genericEx)
        assertTrue("Should be Failure", result is GoogleSignInResult.Failure)
        val failure = result as GoogleSignInResult.Failure
        assertEquals("Network timeout occurred", failure.message)
    }
}
