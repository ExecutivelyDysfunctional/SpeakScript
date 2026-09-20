package com.example.util

import android.media.AudioFormat
import android.media.AudioRecord
import android.util.Log

object AudioConfigHelper {
    private const val TAG = "AudioConfigHelper"
    val PREFERRED_SAMPLE_RATES = listOf(16000, 44100, 22050, 11025, 8000)

    fun calculateValidBufferSize(sampleRate: Int, channelConfig: Int, audioFormat: Int): Int {
        val minBuffer = try {
            AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        } catch (e: Exception) {
            AudioRecord.ERROR_BAD_VALUE
        }
        if (minBuffer <= 0 || minBuffer == AudioRecord.ERROR || minBuffer == AudioRecord.ERROR_BAD_VALUE) {
            return -1
        }
        return minBuffer * 2
    }

    fun selectSupportedSampleRate(
        audioSource: Int,
        channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
        audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
        candidateSampleRates: List<Int> = PREFERRED_SAMPLE_RATES
    ): Int {
        for (rate in candidateSampleRates) {
            val bufferSize = calculateValidBufferSize(rate, channelConfig, audioFormat)
            if (bufferSize > 0) {
                try {
                    val tempRecord = AudioRecord(audioSource, rate, channelConfig, audioFormat, bufferSize)
                    val isInitialized = tempRecord.state == AudioRecord.STATE_INITIALIZED
                    tempRecord.release()
                    if (isInitialized) {
                        return rate
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Sample rate $rate unsupported for audio source $audioSource", e)
                }
            }
        }
        return -1
    }
}
