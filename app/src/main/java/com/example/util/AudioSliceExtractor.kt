package com.example.util

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.math.sin

/**
 * Utility for extracting short reference audio slices (10-15s) from audio recordings.
 * Used for Few-Shot Multimodal Voice Biometrics and Golden Sample prompting with Gemini.
 */
object AudioSliceExtractor {
    private const val TAG = "AudioSliceExtractor"

    data class AudioSlice(
        val bytes: ByteArray,
        val mimeType: String,
        val durationMs: Int
    )

    /**
     * Extracts a clean 10-15s audio slice from an audio file/content URI.
     * Uses MediaExtractor and MediaMuxer to trim without re-encoding quality loss,
     * with graceful fallbacks.
     */
    @Suppress("WrongConstant")
    fun extractSlice(
        context: Context,
        audioUriStr: String,
        startMs: Int?,
        endMs: Int?,
        maxSliceDurationMs: Int? = null
    ): AudioSlice? {
        if (audioUriStr.isBlank()) return null
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var tempFile: File? = null

        try {
            val uri = Uri.parse(audioUriStr)
            val sMs = (startMs ?: 0).coerceAtLeast(0)
            val maxCap = (maxSliceDurationMs ?: 15000).coerceIn(3000, 30000)
            val targetDuration = maxCap.coerceAtMost(12000)
            val rawEMs = if (endMs != null && endMs > sMs) endMs else (sMs + targetDuration)
            // Clamp slice between 3 seconds and maxCap
            val eMs = if (rawEMs - sMs > maxCap) sMs + maxCap else if (rawEMs - sMs < 3000) sMs + 3000 else rawEMs
            val sliceDurationMs = eMs - sMs

            extractor = MediaExtractor()
            if (uri.scheme == "file" && uri.path != null) {
                extractor.setDataSource(uri.path!!)
            } else if (uri.scheme == "content") {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
                try {
                    extractor.setDataSource(pfd.fileDescriptor)
                } finally {
                    pfd.close()
                }
            } else if (audioUriStr.startsWith("/")) {
                extractor.setDataSource(audioUriStr)
            } else {
                extractor.setDataSource(context, uri, null)
            }

            var audioTrackIndex = -1
            var trackFormat: MediaFormat? = null
            var sourceMime = "audio/mp4"
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val trackMime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (trackMime.startsWith("audio/")) {
                    audioTrackIndex = i
                    trackFormat = format
                    sourceMime = trackMime
                    break
                }
            }

            if (audioTrackIndex < 0 || trackFormat == null) {
                return null
            }

            extractor.selectTrack(audioTrackIndex)
            tempFile = File.createTempFile("golden_slice_${UUID.randomUUID()}_", ".m4a", context.cacheDir)
            muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrack = muxer.addTrack(trackFormat)
            muxer.start()

            val startUs = sMs.toLong() * 1000L
            val endUs = eMs.toLong() * 1000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val maxBufferSize = try {
                trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } catch (e: Exception) {
                128 * 1024
            }.coerceAtLeast(64 * 1024)

            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            var samplesWritten = 0

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break
                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs > endUs) break

                if (sampleTimeUs >= (startUs - 200000L)) { // include sync margin
                    bufferInfo.presentationTimeUs = (sampleTimeUs - startUs).coerceAtLeast(0)
                    bufferInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
                    samplesWritten++
                }
                extractor.advance()
            }

            if (samplesWritten > 0) {
                try {
                    muxer.stop()
                } catch (e: Exception) {
                    Log.w(TAG, "Muxer stop warning: ${e.message}")
                }
            }

            if (tempFile.exists() && tempFile.length() > 200) {
                val bytes = tempFile.readBytes()
                val cleanMime = when {
                    sourceMime.contains("wav") -> "audio/wav"
                    sourceMime.contains("mp3") || sourceMime.contains("mpeg") -> "audio/mp3"
                    sourceMime.contains("ogg") || sourceMime.contains("opus") -> "audio/ogg"
                    sourceMime.contains("flac") -> "audio/flac"
                    else -> "audio/aac"
                }
                return AudioSlice(
                    bytes = bytes,
                    mimeType = cleanMime,
                    durationMs = sliceDurationMs
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaExtractor slice failed: ${e.message}, attempting stream fallback")
            // Stream Fallback: if audio file is small (< 1MB) or audioUriStr can be directly read
            try {
                val uri = Uri.parse(audioUriStr)
                val stream = if (uri.scheme == "file" && uri.path != null) {
                    File(uri.path!!).inputStream()
                } else {
                    context.contentResolver.openInputStream(uri)
                }
                val rawBytes = stream?.use { it.readBytes() }
                if (rawBytes != null && rawBytes.isNotEmpty() && rawBytes.size <= 800_000) {
                    return AudioSlice(
                        bytes = rawBytes,
                        mimeType = "audio/aac",
                        durationMs = 10000
                    )
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback stream read failed: ${e2.message}")
            }
        } finally {
            try { muxer?.release() } catch (e: Exception) {}
            try { extractor?.release() } catch (e: Exception) {}
            tempFile?.delete()
        }

        return null
    }

    /**
     * Synthesizes a valid, playable 4-second AAC audio reference file in the app cache directory.
     * Generates a calibrated harmonic speech-range vocal tone pattern for biometric reference testing.
     */
    fun createSyntheticGoldenSample(context: Context, fileName: String, baseFrequencyHz: Double = 220.0): File? {
        return try {
            val file = File(context.cacheDir, fileName)
            if (file.exists() && file.length() > 1000) return file

            val sampleRate = 16000
            val channelCount = 1
            val durationSec = 4
            val numSamples = sampleRate * durationSec

            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 64000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }

            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var audioTrackIndex = -1
            var muxerStarted = false

            val bufferInfo = MediaCodec.BufferInfo()
            var sampleIndex = 0
            var isEOS = false

            while (!isEOS) {
                val inputBufferIndex = codec.dequeueInputBuffer(10000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                    inputBuffer?.clear()
                    if (sampleIndex < numSamples) {
                        val samplesToGen = minOf(1024, numSamples - sampleIndex)
                        val bytesToGenerate = samplesToGen * 2
                        val pcmBytes = ByteArray(bytesToGenerate)
                        for (i in 0 until samplesToGen) {
                            val t = (sampleIndex + i).toDouble() / sampleRate
                            // Vocal tone harmonic blend
                            val sVal = (sin(2.0 * Math.PI * baseFrequencyHz * t) * 0.6 +
                                    sin(2.0 * Math.PI * (baseFrequencyHz * 2) * t) * 0.3 +
                                    sin(2.0 * Math.PI * (baseFrequencyHz * 3) * t) * 0.1) * 9000
                            val s = sVal.toInt().coerceIn(-32768, 32767).toShort()
                            pcmBytes[i * 2] = (s.toInt() and 0xFF).toByte()
                            pcmBytes[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
                        }
                        inputBuffer?.put(pcmBytes)
                        val ptsUs = (sampleIndex.toLong() * 1000000L) / sampleRate
                        codec.queueInputBuffer(inputBufferIndex, 0, bytesToGenerate, ptsUs, 0)
                        sampleIndex += samplesToGen
                    } else {
                        codec.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            0,
                            (sampleIndex.toLong() * 1000000L) / sampleRate,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        isEOS = true
                    }
                }

                var outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                while (outputBufferIndex >= 0) {
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size != 0 && muxerStarted) {
                        val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                        outputBuffer?.let {
                            it.position(bufferInfo.offset)
                            it.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(audioTrackIndex, it, bufferInfo)
                        }
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        break
                    }
                    outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                }
                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    audioTrackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
            }

            try { codec.stop() } catch (e: Exception) {}
            codec.release()
            if (muxerStarted) {
                try { muxer.stop() } catch (e: Exception) {}
            }
            muxer.release()
            if (file.exists() && file.length() > 500) file else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create synthetic golden sample: ${e.message}")
            null
        }
    }
}
