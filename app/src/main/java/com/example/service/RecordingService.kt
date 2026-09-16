package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.*
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RecordingService : Service() {
    private val binder = LocalBinder()
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var currentOutputFile: File? = null
    private var isRecording = false

    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    inner class LocalBinder : Binder() {
        fun getService(): RecordingService = this@RecordingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START_RECORDING -> {
                val useBluetooth = intent.getBooleanExtra(EXTRA_USE_BLUETOOTH, false)
                startForegroundService(useBluetooth)
                startRecording(useBluetooth)
            }
            ACTION_STOP_RECORDING -> {
                stopRecording()
                stopForeground(true)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundService(useBluetooth: Boolean) {
        val channelId = "recording_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Audio Recording",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val micType = if (useBluetooth) "Bluetooth Earbud" else "Phone Microphone"
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Recording Audio")
            .setContentText("Capturing from $micType")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startRecording(useBluetooth: Boolean) {
        if (isRecording) return
        isRecording = true

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (useBluetooth) {
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            val outputDir = File(filesDir, "recordings")
            if (!outputDir.exists()) outputDir.mkdirs()
            val fileName = "recording_${System.currentTimeMillis()}.pcm"
            currentOutputFile = File(outputDir, fileName)

            audioRecord?.startRecording()

            recordingJob = serviceScope.launch {
                val buffer = ByteArray(bufferSize)
                FileOutputStream(currentOutputFile).use { fos ->
                    while (isRecording && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (read > 0) {
                            fos.write(buffer, 0, read)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("RecordingService", "Failed to start recording", e)
            isRecording = false
        }
    }

    private fun stopRecording() {
        isRecording = false
        
        audioRecord?.apply {
            try {
                stop()
            } catch (e: Exception) {
                Log.e("RecordingService", "Error stopping audioRecord", e)
            }
            release()
        }
        audioRecord = null

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.stopBluetoothSco()
        audioManager.isBluetoothScoOn = false

        runBlocking {
            try {
                recordingJob?.join()
            } catch (e: Exception) {
                Log.e("RecordingService", "Error waiting for recording coroutine", e)
            }
        }

        // Finalize PCM to WAV
        currentOutputFile?.let { pcmFile ->
            val wavFile = File(pcmFile.absolutePath.replace(".pcm", ".wav"))
            pcmToWav(pcmFile, wavFile)
            
            // Automatically save a copy to the phone's public Music/TranscribeAI folder so search finds it
            saveWavToPublicMusicFolder(wavFile)

            // Notify system or ViewModel about the new file with explicit package targeting
            val intent = Intent(ACTION_RECORDING_FINISHED).apply {
                putExtra(EXTRA_WAV_PATH, wavFile.absolutePath)
                setPackage(packageName)
            }
            sendBroadcast(intent)
        }
    }

    private fun saveWavToPublicMusicFolder(wavFile: File) {
        try {
            if (!wavFile.exists() || wavFile.length() == 0L) return
            val fileName = wavFile.name
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                    put(android.provider.MediaStore.Audio.Media.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_MUSIC}/TranscribeAI")
                    put(android.provider.MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        wavFile.inputStream().use { input -> input.copyTo(out) }
                    }
                    values.clear()
                    values.put(android.provider.MediaStore.Audio.Media.IS_PENDING, 0)
                    contentResolver.update(uri, values, null, null)
                }
            } else {
                val musicDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC), "TranscribeAI")
                if (!musicDir.exists()) musicDir.mkdirs()
                val destFile = File(musicDir, fileName)
                wavFile.copyTo(destFile, overwrite = true)
                android.media.MediaScannerConnection.scanFile(this, arrayOf(destFile.absolutePath), arrayOf("audio/wav"), null)
            }
        } catch (e: Exception) {
            Log.e("RecordingService", "Failed to save audio file to public Music directory", e)
        }
    }

    private fun pcmToWav(pcmFile: File, wavFile: File) {
        if (!pcmFile.exists()) return
        val pcmData = pcmFile.readBytes()
        val totalAudioLen = pcmData.size.toLong()
        val totalDataLen = totalAudioLen + 36
        val longSampleRate = sampleRate.toLong()
        val channels = 1
        val byteRate = 16 * sampleRate * channels / 8

        FileOutputStream(wavFile).use { out ->
            writeWavHeader(out, totalAudioLen, totalDataLen, longSampleRate, channels, byteRate.toLong())
            out.write(pcmData)
        }
    }

    private fun writeWavHeader(out: FileOutputStream, totalAudioLen: Long, totalDataLen: Long, longSampleRate: Long, channels: Int, byteRate: Long) {
        val header = ByteArray(44)
        header[0] = 'R'.toByte()
        header[1] = 'I'.toByte()
        header[2] = 'F'.toByte()
        header[3] = 'F'.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.toByte()
        header[9] = 'A'.toByte()
        header[10] = 'V'.toByte()
        header[11] = 'E'.toByte()
        header[12] = 'f'.toByte()
        header[13] = 'm'.toByte()
        header[14] = 't'.toByte()
        header[15] = ' '.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (longSampleRate and 0xff).toByte()
        header[25] = (longSampleRate shr 8 and 0xff).toByte()
        header[26] = (longSampleRate shr 16 and 0xff).toByte()
        header[27] = (longSampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * 16 / 8).toByte()
        header[33] = 0
        header[34] = 16
        header[35] = 0
        header[36] = 'd'.toByte()
        header[37] = 'a'.toByte()
        header[38] = 't'.toByte()
        header[39] = 'a'.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte()
        header[43] = (totalAudioLen shr 24 and 0xff).toByte()
        out.write(header, 0, 44)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRecording = false
        recordingJob?.cancel()
        serviceScope.cancel()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_RECORDING = "com.example.action.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.example.action.STOP_RECORDING"
        const val ACTION_RECORDING_FINISHED = "com.example.action.RECORDING_FINISHED"
        const val EXTRA_USE_BLUETOOTH = "com.example.extra.USE_BLUETOOTH"
        const val EXTRA_WAV_PATH = "com.example.extra.WAV_PATH"
    }
}
