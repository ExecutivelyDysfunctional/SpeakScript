package com.example.service

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.*
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.util.AudioConfigHelper
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

class RecordingService : Service() {
    private val binder = LocalBinder()
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var currentOutputFile: File? = null
    @Volatile private var isRecording = false
    @Volatile private var isPaused = false
    private var currentUseBluetooth = false
    private var actualSampleRate = 16000
    private var isBluetoothRouteActive = false
    private val recordingLock = Any()

    inner class LocalBinder : Binder() {
        fun getService(): RecordingService = this@RecordingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START_RECORDING -> {
                val useBluetooth = intent?.getBooleanExtra(EXTRA_USE_BLUETOOTH, false) ?: false
                currentUseBluetooth = useBluetooth
                isPaused = false
                startForegroundNotification(useBluetooth, isPaused = false)
                startRecording(useBluetooth)
            }
            ACTION_PAUSE_RECORDING -> {
                pauseRecording()
            }
            ACTION_RESUME_RECORDING -> {
                resumeRecording()
            }
            ACTION_STOP_RECORDING -> {
                stopRecording()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audio Recording Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing audio recording controls and status notifications"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildRecordingNotification(useBluetooth: Boolean, isPaused: Boolean): Notification {
        val openAppIntent = Intent(this, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            10,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, RecordingService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            11,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val togglePauseIntent = Intent(this, RecordingService::class.java).apply {
            action = if (isPaused) ACTION_RESUME_RECORDING else ACTION_PAUSE_RECORDING
        }
        val togglePausePendingIntent = PendingIntent.getService(
            this,
            12,
            togglePauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val micName = if (useBluetooth) "Bluetooth headset" else "Phone microphone"
        val title = if (isPaused) "Recording Paused" else "Recording in Progress"
        val contentText = if (isPaused) "Paused capturing from $micName" else "Capturing from $micName"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (isPaused) {
            builder.addAction(
                android.R.drawable.ic_media_play,
                "Resume",
                togglePausePendingIntent
            )
        } else {
            builder.addAction(
                android.R.drawable.ic_media_pause,
                "Pause",
                togglePausePendingIntent
            )
        }

        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop",
            stopPendingIntent
        )

        return builder.build()
    }

    private fun startForegroundNotification(useBluetooth: Boolean, isPaused: Boolean) {
        createNotificationChannel()
        val notification = buildRecordingNotification(useBluetooth, isPaused)
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateRecordingNotification(useBluetooth: Boolean, isPaused: Boolean) {
        val notification = buildRecordingNotification(useBluetooth, isPaused)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun pauseRecording() {
        synchronized(recordingLock) {
            if (isRecording && !isPaused) {
                isPaused = true
                updateRecordingNotification(currentUseBluetooth, isPaused = true)
                sendBroadcast(Intent(ACTION_RECORDING_PAUSED).setPackage(packageName))
            }
        }
    }

    private fun resumeRecording() {
        synchronized(recordingLock) {
            if (isRecording && isPaused) {
                isPaused = false
                updateRecordingNotification(currentUseBluetooth, isPaused = false)
                sendBroadcast(Intent(ACTION_RECORDING_RESUMED).setPackage(packageName))
            }
        }
    }

    private fun configureBluetoothAudioRoute(useBluetooth: Boolean): Boolean {
        if (!useBluetooth) {
            releaseBluetoothAudioRoute()
            return false
        }

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false

        // Check BLUETOOTH_CONNECT permission on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "BLUETOOTH_CONNECT permission not granted, falling back to phone mic")
                releaseBluetoothAudioRoute()
                return false
            }
        }

        return try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val btDevice = selectBluetoothCommunicationDevice(audioManager)
                if (btDevice != null) {
                    val success = audioManager.setCommunicationDevice(btDevice)
                    Log.i(TAG, "setCommunicationDevice (${btDevice.productName ?: btDevice.type}) result: $success")
                    isBluetoothRouteActive = success
                    success
                } else {
                    Log.w(TAG, "No Bluetooth communication device found in availableCommunicationDevices")
                    isBluetoothRouteActive = false
                    false
                }
            } else {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                isBluetoothRouteActive = true
                waitForBluetoothSco(audioManager)
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure Bluetooth audio route", e)
            releaseBluetoothAudioRoute()
            false
        }
    }

    private fun selectBluetoothCommunicationDevice(audioManager: AudioManager): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = audioManager.availableCommunicationDevices
            return devices.firstOrNull { device ->
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                device.type == AudioDeviceInfo.TYPE_HEARING_AID
            }
        }
        return null
    }

    private fun waitForBluetoothSco(audioManager: AudioManager) {
        val startTime = System.currentTimeMillis()
        while (!audioManager.isBluetoothScoOn && (System.currentTimeMillis() - startTime) < 2000) {
            try {
                Thread.sleep(100)
            } catch (e: InterruptedException) {
                break
            }
        }
    }

    private fun releaseBluetoothAudioRoute() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
            }
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing Bluetooth audio route", e)
        } finally {
            isBluetoothRouteActive = false
        }
    }

    private fun createAudioRecord(useBluetooth: Boolean): Pair<AudioRecord, Int>? {
        val preferredSource = if (useBluetooth) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.MIC
        val fallbackSources = if (useBluetooth) {
            listOf(preferredSource, MediaRecorder.AudioSource.MIC, MediaRecorder.AudioSource.DEFAULT)
        } else {
            listOf(preferredSource, MediaRecorder.AudioSource.DEFAULT)
        }

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        for (source in fallbackSources) {
            val rate = AudioConfigHelper.selectSupportedSampleRate(
                audioSource = source,
                channelConfig = channelConfig,
                audioFormat = audioFormat
            )
            if (rate > 0) {
                val bufSize = AudioConfigHelper.calculateValidBufferSize(rate, channelConfig, audioFormat)
                if (bufSize > 0) {
                    try {
                        val record = AudioRecord(source, rate, channelConfig, audioFormat, bufSize)
                        if (record.state == AudioRecord.STATE_INITIALIZED) {
                            Log.i(TAG, "AudioRecord initialized with source=$source, sampleRate=$rate, bufferSize=$bufSize")
                            return Pair(record, rate)
                        } else {
                            record.release()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "AudioRecord creation failed for source=$source, rate=$rate", e)
                    }
                }
            }
        }
        return null
    }

    private fun startRecording(useBluetooth: Boolean) {
        synchronized(recordingLock) {
            if (isRecording) {
                Log.w(TAG, "startRecording called while already recording")
                return
            }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "RECORD_AUDIO permission missing, aborting recording")
                sendBroadcast(Intent(ACTION_RECORDING_FAILED).apply {
                    putExtra(EXTRA_ERROR_MESSAGE, "RECORD_AUDIO permission missing.")
                    setPackage(packageName)
                })
                stopForeground(true)
                stopSelf()
                return
            }

            val btConfigured = configureBluetoothAudioRoute(useBluetooth)
            val recordPair = createAudioRecord(useBluetooth = btConfigured)

            if (recordPair == null) {
                Log.e(TAG, "Could not initialize AudioRecord with any configuration")
                releaseBluetoothAudioRoute()
                sendBroadcast(Intent(ACTION_RECORDING_FAILED).apply {
                    putExtra(EXTRA_ERROR_MESSAGE, "Could not initialize microphone recorder.")
                    setPackage(packageName)
                })
                stopForeground(true)
                stopSelf()
                return
            }

            val (record, rate) = recordPair
            audioRecord = record
            actualSampleRate = rate
            isRecording = true

            sendBroadcast(Intent(ACTION_RECORDING_STARTED).apply {
                putExtra(EXTRA_USE_BLUETOOTH, useBluetooth)
                setPackage(packageName)
            })

            val outputDir = File(filesDir, "recordings")
            if (!outputDir.exists()) outputDir.mkdirs()
            val fileName = "recording_${System.currentTimeMillis()}.pcm"
            currentOutputFile = File(outputDir, fileName)

            recordingJob = serviceScope.launch {
                val bufSize = AudioConfigHelper.calculateValidBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val buffer = ByteArray(if (bufSize > 0) bufSize else 2048)
                var totalBytesRead = 0L

                try {
                    record.startRecording()
                    FileOutputStream(currentOutputFile).use { fos ->
                        while (isRecording && record.recordingState == AudioRecord.RECORDSTATE_RECORDING && isActive) {
                            if (isPaused) {
                                delay(100)
                                continue
                            }
                            val read = record.read(buffer, 0, buffer.size)
                            if (read > 0) {
                                fos.write(buffer, 0, read)
                                totalBytesRead += read
                            } else if (read < 0) {
                                Log.e(TAG, "AudioRecord read error code: $read")
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in audio recording loop", e)
                } finally {
                    Log.i(TAG, "Recording loop ended. Total bytes read: $totalBytesRead")
                }
            }
        }
    }

    private fun stopRecording() {
        synchronized(recordingLock) {
            if (!isRecording && audioRecord == null) {
                return
            }
            isRecording = false
            isPaused = false

            sendBroadcast(Intent(ACTION_RECORDING_STOPPED).setPackage(packageName))

            try {
                if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord?.stop()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping AudioRecord", e)
            }

            runBlocking {
                try {
                    recordingJob?.join()
                } catch (e: Exception) {
                    Log.e(TAG, "Error waiting for recording job", e)
                }
            }
            recordingJob = null

            try {
                audioRecord?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing AudioRecord", e)
            }
            audioRecord = null

            releaseBluetoothAudioRoute()

            val pcmFile = currentOutputFile
            if (pcmFile != null && pcmFile.exists() && pcmFile.length() > 0) {
                val wavFile = File(pcmFile.absolutePath.replace(".pcm", ".wav"))
                pcmToWav(pcmFile, wavFile, actualSampleRate)

                if (wavFile.exists() && wavFile.length() > 44L) {
                    saveWavToPublicMusicFolder(wavFile)
                    val intent = Intent(ACTION_RECORDING_FINISHED).apply {
                        putExtra(EXTRA_WAV_PATH, wavFile.absolutePath)
                        setPackage(packageName)
                    }
                    sendBroadcast(intent)
                } else {
                    Log.w(TAG, "Final WAV file is empty or header-only")
                }
                pcmFile.delete()
            } else {
                Log.w(TAG, "PCM output file is missing or empty")
                pcmFile?.delete()
            }
            currentOutputFile = null
        }
    }

    private fun saveWavToPublicMusicFolder(wavFile: File) {
        try {
            if (!wavFile.exists() || wavFile.length() <= 44L) return
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
            Log.e(TAG, "Failed to save audio file to public Music directory", e)
        }
    }

    private fun pcmToWav(pcmFile: File, wavFile: File, sampleRateToUse: Int) {
        if (!pcmFile.exists()) return
        val pcmData = pcmFile.readBytes()
        val totalAudioLen = pcmData.size.toLong()
        val totalDataLen = totalAudioLen + 36
        val longSampleRate = sampleRateToUse.toLong()
        val channels = 1
        val byteRate = 16 * sampleRateToUse * channels / 8

        FileOutputStream(wavFile).use { out ->
            writeWavHeader(out, totalAudioLen, totalDataLen, longSampleRate, channels, byteRate.toLong())
            out.write(pcmData)
        }
    }

    private fun writeWavHeader(
        out: FileOutputStream,
        totalAudioLen: Long,
        totalDataLen: Long,
        longSampleRate: Long,
        channels: Int,
        byteRate: Long
    ) {
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
        stopRecording()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "RecordingService"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "recording_channel"

        const val ACTION_START_RECORDING = "com.example.action.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.example.action.STOP_RECORDING"
        const val ACTION_PAUSE_RECORDING = "com.example.action.PAUSE_RECORDING"
        const val ACTION_RESUME_RECORDING = "com.example.action.RESUME_RECORDING"

        const val ACTION_RECORDING_STARTED = "com.example.action.RECORDING_STARTED"
        const val ACTION_RECORDING_PAUSED = "com.example.action.RECORDING_PAUSED"
        const val ACTION_RECORDING_RESUMED = "com.example.action.RECORDING_RESUMED"
        const val ACTION_RECORDING_FAILED = "com.example.action.RECORDING_FAILED"
        const val ACTION_RECORDING_STOPPED = "com.example.action.RECORDING_STOPPED"
        const val ACTION_RECORDING_FINISHED = "com.example.action.RECORDING_FINISHED"

        const val EXTRA_USE_BLUETOOTH = "com.example.extra.USE_BLUETOOTH"
        const val EXTRA_WAV_PATH = "com.example.extra.WAV_PATH"
        const val EXTRA_ERROR_MESSAGE = "com.example.extra.ERROR_MESSAGE"
    }
}
