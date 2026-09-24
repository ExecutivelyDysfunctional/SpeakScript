package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.db.TranscriptionRecord
import com.example.db.SpeakerProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

/**
 * Formats milliseconds into a readable mm:ss or hh:mm:ss string.
 */
fun formatDuration(millis: Int): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes % 60, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

/**
 * Parses timestamp string like "[01:23]", "(01:23)", or "01:23" into milliseconds.
 */
fun parseTimestampToMillis(rawTimestamp: String): Int? {
    val clean = rawTimestamp.replace("[", "").replace("]", "").replace("(", "").replace(")", "").trim()
    val parts = clean.split(":")
    return try {
        when (parts.size) {
            2 -> {
                val mins = parts[0].toInt()
                val secs = parts[1].toInt()
                (mins * 60 + secs) * 1000
            }
            3 -> {
                val hours = parts[0].toInt()
                val mins = parts[1].toInt()
                val secs = parts[2].toInt()
                (hours * 3600 + mins * 60 + secs) * 1000
            }
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Structured transcript line representing a single line of text with exact audio timestamp boundaries.
 */
data class TranscriptLine(
    val id: Int,
    val timestampStr: String?,
    val startMs: Int,
    val endMs: Int,
    val speaker: String?,
    val text: String,
    val hasExplicitTimestamp: Boolean
)

/**
 * Unified line across multi-part sequential sessions with cumulative global timestamp tracking.
 */
data class CumulativeTranscriptLine(
    val globalId: Int,
    val partIndex: Int,
    val partNumber: Int,
    val totalParts: Int,
    val localStartMs: Int,
    val localEndMs: Int,
    val globalStartMs: Int,
    val globalEndMs: Int,
    val displayTimestamp: String,
    val speaker: String?,
    val text: String,
    val isFirstLineOfPart: Boolean,
    val partTitle: String
)

/**
 * Splits raw transcript into structured, line-by-line items with precise start/end timestamps.
 * If some or all lines lack timestamps, intelligently interpolates across total audio duration.
 */
fun parseTranscriptLines(rawText: String, totalAudioDurationMs: Int = 0): List<TranscriptLine> {
    if (rawText.isBlank()) return emptyList()

    val timestampRegex = Regex("""(?:\[|\()(\d{1,2}:\d{2}(?::\d{2})?)(?:\]|\))""")
    val speakerLineRegex = Regex("""^(?:\[\d{1,2}:\d{2}(?::\d{2})?\]|\(\d{1,2}:\d{2}(?::\d{2})?\))?\s*(?:\*\*)?([A-Za-z0-9 _\-]{2,30})(?:\*\*)?:?\s*(.*)$""")

    val rawLines = rawText.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    if (rawLines.isEmpty()) {
        return listOf(
            TranscriptLine(
                id = 0,
                timestampStr = "00:00",
                startMs = 0,
                endMs = if (totalAudioDurationMs > 0) totalAudioDurationMs else 5000,
                speaker = null,
                text = rawText.trim(),
                hasExplicitTimestamp = false
            )
        )
    }

    data class RawLineInfo(
        val originalIndex: Int,
        val timestampStr: String?,
        val explicitMs: Int?,
        val speaker: String?,
        val cleanText: String
    )

    var currentSpeaker: String? = null
    val parsedRaw = rawLines.mapIndexed { idx, line ->
        val tsMatch = timestampRegex.find(line)
        val extractedTsStr = tsMatch?.value
        val extractedMs = tsMatch?.groupValues?.get(1)?.let { parseTimestampToMillis(it) }

        val withoutTs = if (tsMatch != null) {
            line.removeRange(tsMatch.range).trim()
        } else {
            line
        }

        val speakerMatch = speakerLineRegex.find(line)
        val speaker = if (speakerMatch != null && speakerMatch.groupValues[1].isNotBlank()) {
            val spk = speakerMatch.groupValues[1].trim()
            currentSpeaker = spk
            spk
        } else {
            currentSpeaker
        }

        var body = withoutTs
        if (speaker != null && body.startsWith("$speaker:", ignoreCase = true)) {
            body = body.substring("$speaker:".length).trim()
        } else if (speaker != null && body.startsWith("**$speaker**:", ignoreCase = true)) {
            body = body.substring("**$speaker**:".length).trim()
        } else if (speaker != null && body.startsWith("**$speaker**", ignoreCase = true)) {
            body = body.substring("**$speaker**".length).trim()
        }
        if (body.isBlank()) {
            body = line
        }

        RawLineInfo(
            originalIndex = idx,
            timestampStr = extractedTsStr,
            explicitMs = extractedMs,
            speaker = speaker,
            cleanText = body
        )
    }

    val lineCount = parsedRaw.size
    val totalEstimatedDuration = if (totalAudioDurationMs > 0) {
        totalAudioDurationMs
    } else {
        lineCount * 4000
    }

    val computedStarts = IntArray(lineCount)
    val hasExplicitArray = BooleanArray(lineCount)

    for (i in 0 until lineCount) {
        val exp = parsedRaw[i].explicitMs
        if (exp != null) {
            computedStarts[i] = exp
            hasExplicitArray[i] = true
        } else {
            computedStarts[i] = -1
            hasExplicitArray[i] = false
        }
    }

    if (computedStarts[0] == -1) {
        computedStarts[0] = 0
    }

    var lastKnownIdx = 0
    for (i in 1 until lineCount) {
        if (hasExplicitArray[i]) {
            val startVal = computedStarts[lastKnownIdx]
            val endVal = computedStarts[i]
            val span = i - lastKnownIdx
            for (j in (lastKnownIdx + 1) until i) {
                val fraction = (j - lastKnownIdx).toFloat() / span
                computedStarts[j] = (startVal + fraction * (endVal - startVal)).toInt()
            }
            lastKnownIdx = i
        }
    }

    if (lastKnownIdx < lineCount - 1) {
        val startVal = computedStarts[lastKnownIdx]
        val remaining = lineCount - 1 - lastKnownIdx
        val endVal = maxOf(startVal + (remaining + 1) * 3500, totalEstimatedDuration)
        for (j in (lastKnownIdx + 1) until lineCount) {
            val fraction = (j - lastKnownIdx).toFloat() / (remaining + 1)
            computedStarts[j] = (startVal + fraction * (endVal - startVal)).toInt()
        }
    }

    for (i in 1 until lineCount) {
        if (computedStarts[i] <= computedStarts[i - 1]) {
            computedStarts[i] = computedStarts[i - 1] + 1500
        }
    }

    return parsedRaw.mapIndexed { idx, raw ->
        val startMs = computedStarts[idx]
        val endMs = if (idx < lineCount - 1) {
            computedStarts[idx + 1]
        } else {
            maxOf(startMs + 4000, totalEstimatedDuration)
        }
        val displayTs = raw.timestampStr ?: "[${formatDuration(startMs)}]"

        TranscriptLine(
            id = idx,
            timestampStr = displayTs,
            startMs = startMs,
            endMs = maxOf(endMs, startMs + 1000),
            speaker = raw.speaker,
            text = raw.cleanText,
            hasExplicitTimestamp = hasExplicitArray[idx]
        )
    }
}

/**
 * Interactive Waveform Visualizer supporting touch-to-seek and drag scrubbing with multi-part segment markers.
 */
@Composable
fun InteractiveWaveformVisualizer(
    progress: Float,
    amplitudes: List<Float>,
    onSeek: (Float) -> Unit,
    onScrubbingChange: (Boolean, Float) -> Unit,
    isScrubbing: Boolean,
    scrubFraction: Float,
    partCutFractions: List<Float> = emptyList(),
    isClippingMode: Boolean = false,
    clipStartFraction: Float = 0f,
    clipEndFraction: Float = 1f,
    onClipBoundsChange: ((Float, Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val markerColor = MaterialTheme.colorScheme.tertiary

    val displayProgress = if (isScrubbing) scrubFraction else progress

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek(fraction)
                }
            }
            .pointerInput(isClippingMode, clipStartFraction, clipEndFraction, progress, isScrubbing, scrubFraction) {
                var activeHandle = 0 // 0 = none, 1 = start, 2 = end, 3 = playhead/scrub
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        if (isClippingMode) {
                            val distStart = kotlin.math.abs(fraction - clipStartFraction)
                            val distEnd = kotlin.math.abs(fraction - clipEndFraction)
                            val threshold = 0.08f // 8% of width touch zone
                            if (distStart < threshold && distStart < distEnd) {
                                activeHandle = 1
                            } else if (distEnd < threshold) {
                                activeHandle = 2
                            } else {
                                activeHandle = 3
                                onScrubbingChange(true, fraction)
                            }
                        } else {
                            activeHandle = 3
                            onScrubbingChange(true, fraction)
                        }
                    },
                    onDragEnd = {
                        if (activeHandle == 3) {
                            onScrubbingChange(false, displayProgress)
                            onSeek(displayProgress)
                        }
                        activeHandle = 0
                    },
                    onDragCancel = {
                        if (activeHandle == 3) {
                            onScrubbingChange(false, progress)
                        }
                        activeHandle = 0
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        if (isClippingMode) {
                            if (activeHandle == 1) {
                                val newStart = fraction.coerceIn(0f, clipEndFraction - 0.03f)
                                onClipBoundsChange?.invoke(newStart, clipEndFraction)
                            } else if (activeHandle == 2) {
                                val newEnd = fraction.coerceIn(clipStartFraction + 0.03f, 1f)
                                onClipBoundsChange?.invoke(clipStartFraction, newEnd)
                            } else if (activeHandle == 3) {
                                onScrubbingChange(true, fraction)
                            }
                        } else {
                            onScrubbingChange(true, fraction)
                        }
                    }
                )
            }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalBars = amplitudes.size.coerceAtLeast(1)
            val barWidth = size.width / totalBars
            val gap = barWidth * 0.24f
            val actualBarWidth = (barWidth - gap).coerceAtLeast(2.5f)
            val maxHeight = size.height

            val scrubberX = displayProgress * size.width

            for (i in 0 until totalBars) {
                val amp = amplitudes[i].coerceIn(0.12f, 1.0f)
                val barHeight = (maxHeight * amp).coerceAtLeast(actualBarWidth)
                val x = i * barWidth + gap / 2
                val y = (size.height - barHeight) / 2

                val barMiddleX = x + actualBarWidth / 2
                val barFraction = barMiddleX / size.width

                val isPlayed = barMiddleX <= scrubberX
                val barColor = if (isClippingMode) {
                    val inClipRange = barFraction in clipStartFraction..clipEndFraction
                    if (inClipRange) {
                        if (isPlayed) primaryColor else secondaryColor
                    } else {
                        surfaceVariantColor.copy(alpha = 0.35f)
                    }
                } else {
                    if (isPlayed) {
                        val progressRatio = (i.toFloat() / totalBars).coerceIn(0f, 1f)
                        androidx.compose.ui.graphics.lerp(primaryColor, secondaryColor, progressRatio * 0.5f)
                    } else {
                        surfaceVariantColor
                    }
                }

                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, y),
                    size = Size(actualBarWidth, barHeight),
                    cornerRadius = CornerRadius(actualBarWidth / 2, actualBarWidth / 2)
                )
            }

            // Draw Part Boundary Segment Markers
            partCutFractions.forEach { cutFrac ->
                val cutX = cutFrac * size.width
                drawLine(
                    color = markerColor.copy(alpha = 0.8f),
                    start = Offset(cutX, 2f),
                    end = Offset(cutX, size.height - 2f),
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            if (isClippingMode) {
                val startX = clipStartFraction * size.width
                val endX = clipEndFraction * size.width

                // Tint the selected range background lightly
                drawRect(
                    color = primaryColor.copy(alpha = 0.08f),
                    topLeft = Offset(startX, 0f),
                    size = Size(endX - startX, size.height)
                )

                // Draw start handle line
                drawLine(
                    color = markerColor,
                    start = Offset(startX, 0f),
                    end = Offset(startX, size.height),
                    strokeWidth = 3.dp.toPx()
                )
                // Start handle thumb
                drawCircle(
                    color = markerColor,
                    radius = 7.dp.toPx(),
                    center = Offset(startX, size.height / 2)
                )
                drawRect(
                    color = Color.White,
                    topLeft = Offset(startX - 1.dp.toPx(), size.height / 2 - 4.dp.toPx()),
                    size = Size(2.dp.toPx(), 8.dp.toPx())
                )

                // Draw end handle line
                drawLine(
                    color = markerColor,
                    start = Offset(endX, 0f),
                    end = Offset(endX, size.height),
                    strokeWidth = 3.dp.toPx()
                )
                // End handle thumb
                drawCircle(
                    color = markerColor,
                    radius = 7.dp.toPx(),
                    center = Offset(endX, size.height / 2)
                )
                drawRect(
                    color = Color.White,
                    topLeft = Offset(endX - 1.dp.toPx(), size.height / 2 - 4.dp.toPx()),
                    size = Size(2.dp.toPx(), 8.dp.toPx())
                )
            }

            // Draw Playhead Cursor (only if not clipping or playhead is in bounds)
            val playheadX = scrubberX.coerceIn(0f, size.width)
            drawLine(
                color = primaryColor,
                start = Offset(playheadX, 0f),
                end = Offset(playheadX, size.height),
                strokeWidth = 3.dp.toPx()
            )

            // Scrubber Cap Indicator
            drawCircle(
                color = primaryColor,
                radius = if (isScrubbing) 6.dp.toPx() else 4.dp.toPx(),
                center = Offset(playheadX, size.height / 2)
            )
        }
    }
}

/**
 * Dedicated Playback Screen that provides a clean, focused UI for reviewing transcriptions
 * with rich audio playback controls, gapless multi-part session auto-advance,
 * and a unified cumulative transcript engine.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackScreen(
    record: TranscriptionRecord,
    sessionParts: List<TranscriptionRecord> = emptyList(),
    speakers: List<SpeakerProfile> = emptyList(),
    onAssignGoldenSample: ((speakerId: String, audioUri: String, startMs: Int, endMs: Int, recordingTitle: String) -> Unit)? = null,
    onSaveNewSpeaker: ((SpeakerProfile) -> Unit)? = null,
    onUpdateConfidenceAndSpeakers: ((recordId: String, newSpeakers: String, newConfidence: String) -> Unit)? = null,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("MMMM dd, yyyy • hh:mm a", Locale.getDefault()) }
    var candidateGoldenLine by remember { mutableStateOf<CumulativeTranscriptLine?>(null) }
    var showVerifyConfidenceDialog by remember { mutableStateOf(false) }

    var isClippingMode by remember { mutableStateOf(false) }
    var clippingLine by remember { mutableStateOf<CumulativeTranscriptLine?>(null) }
    var clipStartFraction by remember { mutableFloatStateOf(0f) }
    var clipEndFraction by remember { mutableFloatStateOf(1f) }

    // Normalize parts list (if single record, create list of 1)
    val orderedParts = remember(record, sessionParts) {
        if (sessionParts.isNotEmpty()) {
            sessionParts.sortedBy { it.partIndex ?: 0 }
        } else {
            listOf(record)
        }
    }

    val isMultiPartSession = orderedParts.size > 1
    val initialPartIndex = remember(record, orderedParts) {
        val found = orderedParts.indexOfFirst { it.id == record.id }
        if (found >= 0) found else 0
    }

    var currentPartIndex by remember { mutableIntStateOf(initialPartIndex) }
    val currentPartRecord = orderedParts.getOrElse(currentPartIndex) { orderedParts.first() }

    // Session Titles & Summaries
    val sessionTitle = remember(orderedParts) {
        orderedParts.mapNotNull { it.sessionTitle }.firstOrNull { it.isNotBlank() }
            ?: (if (orderedParts.size > 1) "Multi-Part Session" else "Personal Voice Recording")
    }

    val masterSummary = remember(orderedParts) {
        orderedParts.mapNotNull { it.summary }.firstOrNull { it.isNotBlank() }
    }

    // Audio Playback states for active part
    var isPlaying by remember { mutableStateOf(false) }
    var currentPartPositionMs by remember { mutableIntStateOf(0) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var hasAudioError by remember { mutableStateOf(false) }
    var audioErrorMessage by remember { mutableStateOf<String?>(null) }

    // Map of part index to verified/estimated duration in ms
    val partDurationsMap = remember(orderedParts) {
        val map = mutableStateMapOf<Int, Int>()
        orderedParts.forEachIndexed { idx, p ->
            val d = p.partDurationMs ?: (p.text.lines().size * 3500).coerceAtLeast(5000)
            map[idx] = d
        }
        map
    }

    // Durations fingerprint for efficient remember keying without intermediate map creation
    val durationsFingerprint = remember(partDurationsMap) {
        partDurationsMap.values.sum()
    }

    // Cumulative Offsets: offset for part i is sum of durations of parts 0 until i
    val cumulativeOffsets = remember(orderedParts, durationsFingerprint) {
        val offsets = IntArray(orderedParts.size)
        var sum = 0
        for (i in orderedParts.indices) {
            offsets[i] = sum
            sum += partDurationsMap[i] ?: 5000
        }
        offsets
    }

    val totalCumulativeDurationMs = remember(orderedParts, durationsFingerprint) {
        var sum = 0
        for (i in orderedParts.indices) {
            sum += partDurationsMap[i] ?: 5000
        }
        maxOf(sum, 1000)
    }

    // Waveform scrubbing states
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    // Search query inside transcript
    var transcriptSearchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var autoScrollEnabled by remember { mutableStateOf(true) }

    // Unified Cumulative Transcript Lines Engine
    val cumulativeLines = remember(orderedParts, durationsFingerprint, cumulativeOffsets) {
        val allLines = mutableListOf<CumulativeTranscriptLine>()
        var globalLineCounter = 0

        orderedParts.forEachIndexed { partIdx, partRec ->
            val partDur = partDurationsMap[partIdx] ?: 5000
            val partOffset = cumulativeOffsets.getOrElse(partIdx) { 0 }
            val localLines = parseTranscriptLines(partRec.text, partDur)
            val pNumber = (partRec.partIndex ?: partIdx) + 1
            val pTitle = partRec.sessionTitle ?: "Part $pNumber"

            localLines.forEachIndexed { lineIdx, locLine ->
                val gStart = partOffset + locLine.startMs
                val gEnd = partOffset + locLine.endMs
                allLines.add(
                    CumulativeTranscriptLine(
                        globalId = globalLineCounter++,
                        partIndex = partIdx,
                        partNumber = pNumber,
                        totalParts = orderedParts.size,
                        localStartMs = locLine.startMs,
                        localEndMs = locLine.endMs,
                        globalStartMs = gStart,
                        globalEndMs = gEnd,
                        displayTimestamp = if (isMultiPartSession) {
                            "[${formatDuration(gStart)} • P$pNumber]"
                        } else {
                            locLine.timestampStr ?: "[${formatDuration(locLine.startMs)}]"
                        },
                        speaker = locLine.speaker ?: partRec.speakerName,
                        text = locLine.text,
                        isFirstLineOfPart = (lineIdx == 0),
                        partTitle = pTitle
                    )
                )
            }
        }
        allLines
    }

    // Boundary cuts for waveform visualizer
    val partCutFractions = remember(cumulativeOffsets, totalCumulativeDurationMs, orderedParts.size) {
        if (orderedParts.size <= 1) emptyList()
        else {
            cumulativeOffsets.drop(1).map { offset ->
                (offset.toFloat() / totalCumulativeDurationMs).coerceIn(0f, 1f)
            }
        }
    }

    // Waveform pseudo-amplitudes generated seamlessly across whole session
    val waveformAmplitudes = remember(orderedParts.map { it.id }.joinToString("_")) {
        val seed = orderedParts.map { it.id }.joinToString("_").hashCode().toLong()
        val random = Random(seed)
        val count = 52
        val rawAmps = List(count) { 0.15f + random.nextFloat() * 0.85f }
        rawAmps.mapIndexed { idx, amp ->
            val prev = if (idx > 0) rawAmps[idx - 1] else amp
            val next = if (idx < count - 1) rawAmps[idx + 1] else amp
            ((prev + amp * 2 + next) / 4f).coerceIn(0.12f, 1.0f)
        }
    }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    // Core Gapless Player Initializer
    fun initPlayerForPart(partIdx: Int, autoPlay: Boolean = false, startPosMs: Int = 0) {
        val targetPart = orderedParts.getOrNull(partIdx)
        if (targetPart == null) return

        val uriStr = targetPart.audioUri

        var localFileExists = false
        if (!uriStr.isNullOrBlank()) {
            try {
                val parsedUri = Uri.parse(uriStr)
                if (parsedUri.scheme == "file") {
                    val path = parsedUri.path
                    if (path != null) {
                        val file = java.io.File(path)
                        localFileExists = file.exists()
                    }
                } else {
                    localFileExists = true
                }
            } catch (e: java.lang.Exception) {
                // Ignore
            }
        }

        if (uriStr.isNullOrBlank()) {
            hasAudioError = true
            audioErrorMessage = "No audio recording file found for Part ${(targetPart.partIndex ?: partIdx) + 1}."
            isPlaying = false
            return
        }

        try {
            mediaPlayer?.release()
            val player = MediaPlayer()
            val uri = Uri.parse(uriStr)
            player.setDataSource(context, uri)
            player.setOnPreparedListener { mp ->
                val actualDur = mp.duration
                if (actualDur > 0) {
                    partDurationsMap[partIdx] = actualDur
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        mp.playbackParams = mp.playbackParams.setSpeed(playbackSpeed)
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
                if (startPosMs > 0) {
                    mp.seekTo(startPosMs.coerceIn(0, actualDur))
                }
                if (autoPlay) {
                    mp.start()
                    isPlaying = true
                }
            }

            // Gapless auto-advance engine on part completion
            player.setOnCompletionListener {
                if (partIdx < orderedParts.size - 1) {
                    val nextIdx = partIdx + 1
                    currentPartIndex = nextIdx
                    currentPartPositionMs = 0
                    initPlayerForPart(nextIdx, autoPlay = true, startPosMs = 0)
                    Toast.makeText(
                        context,
                        "Advancing seamlessly to Part ${nextIdx + 1} of ${orderedParts.size}",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    isPlaying = false
                    currentPartPositionMs = partDurationsMap[partIdx] ?: 0
                }
            }

            player.setOnErrorListener { _, what, extra ->
                hasAudioError = true
                audioErrorMessage = "Playback error on Part ${partIdx + 1} ($what, $extra)"
                isPlaying = false
                false
            }

            player.prepareAsync()
            mediaPlayer = player
            hasAudioError = false
            audioErrorMessage = null
        } catch (e: Exception) {
            hasAudioError = true
            audioErrorMessage = "Unable to load audio for Part ${partIdx + 1}: ${e.localizedMessage ?: e.message}"
            isPlaying = false
            mediaPlayer = null
        }
    }


    // Auto-init on initial composition or part index change
    LaunchedEffect(currentPartIndex) {
        initPlayerForPart(currentPartIndex, autoPlay = isPlaying, startPosMs = currentPartPositionMs)
    }

    // Release player on disposal
    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            } catch (e: Exception) {
                // Ignore
            }
            mediaPlayer = null
        }
    }

    // Active position polling loop
    LaunchedEffect(isPlaying, currentPartIndex) {
        while (isPlaying) {
            mediaPlayer?.let { player ->
                try {
                    if (player.isPlaying) {
                        currentPartPositionMs = player.currentPosition
                        if (player.duration > 0 && player.duration != partDurationsMap[currentPartIndex]) {
                            partDurationsMap[currentPartIndex] = player.duration
                        }
                    }
                } catch (e: Exception) {
                    // Ignore transient exceptions
                }
            }
            delay(120)
        }
    }

    // Calculate Global Position across all parts
    val currentGlobalPositionMs = remember(currentPartIndex, currentPartPositionMs, cumulativeOffsets) {
        val offset = cumulativeOffsets.getOrElse(currentPartIndex) { 0 }
        offset + currentPartPositionMs
    }

    val currentGlobalProgress = remember(currentGlobalPositionMs, totalCumulativeDurationMs) {
        if (totalCumulativeDurationMs > 0) {
            (currentGlobalPositionMs.toFloat() / totalCumulativeDurationMs).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    // Seeking directly to a Global Millisecond timestamp
    fun seekToGlobalMillis(targetGlobalMs: Int) {
        val clampedGlobal = targetGlobalMs.coerceIn(0, totalCumulativeDurationMs)

        // Find which part contains this global millisecond
        var targetPartIdx = 0
        for (i in orderedParts.indices) {
            val partStart = cumulativeOffsets.getOrElse(i) { 0 }
            val partDur = partDurationsMap[i] ?: 5000
            if (clampedGlobal >= partStart && (clampedGlobal < partStart + partDur || i == orderedParts.size - 1)) {
                targetPartIdx = i
                break
            }
        }

        val partStart = cumulativeOffsets.getOrElse(targetPartIdx) { 0 }
        val localTargetMs = (clampedGlobal - partStart).coerceAtLeast(0)

        if (targetPartIdx == currentPartIndex && mediaPlayer != null) {
            currentPartPositionMs = localTargetMs
            try {
                mediaPlayer?.seekTo(localTargetMs)
                if (!isPlaying) {
                    mediaPlayer?.start()
                    isPlaying = true
                }
            } catch (e: Exception) {
                initPlayerForPart(targetPartIdx, autoPlay = true, startPosMs = localTargetMs)
            }
        } else {
            currentPartIndex = targetPartIdx
            currentPartPositionMs = localTargetMs
            initPlayerForPart(targetPartIdx, autoPlay = true, startPosMs = localTargetMs)
        }
    }

    fun seekToGlobalFraction(fraction: Float) {
        val targetMs = (fraction * totalCumulativeDurationMs).toInt()
        seekToGlobalMillis(targetMs)
    }

    // Effective position handling live scrubbing
    val effectiveGlobalPositionMs = if (isScrubbing) {
        (scrubFraction * totalCumulativeDurationMs).toInt()
    } else {
        currentGlobalPositionMs
    }

    // Determine Active Transcript Line across whole session
    val activeLineIndex = remember(cumulativeLines, effectiveGlobalPositionMs) {
        if (cumulativeLines.isEmpty()) -1
        else {
            val match = cumulativeLines.indexOfFirst { line ->
                effectiveGlobalPositionMs in line.globalStartMs until line.globalEndMs
            }
            if (match != -1) {
                match
            } else {
                val lastPassed = cumulativeLines.indexOfLast { it.globalStartMs <= effectiveGlobalPositionMs }
                if (lastPassed != -1) lastPassed else 0
            }
        }
    }

    // Filter lines by search query
    val filteredLines = remember(cumulativeLines, transcriptSearchQuery) {
        if (transcriptSearchQuery.isBlank()) {
            cumulativeLines
        } else {
            cumulativeLines.filter {
                it.text.contains(transcriptSearchQuery, ignoreCase = true) ||
                        (it.speaker?.contains(transcriptSearchQuery, ignoreCase = true) == true) ||
                        (it.displayTimestamp.contains(transcriptSearchQuery, ignoreCase = true)) ||
                        (it.partTitle.contains(transcriptSearchQuery, ignoreCase = true))
            }
        }
    }

    var lastScrolledIndex by remember { mutableStateOf<Int?>(null) }

    // Smooth auto-scroll to current active playing line
    LaunchedEffect(activeLineIndex, isPlaying, isScrubbing, autoScrollEnabled, filteredLines) {
        if ((isPlaying || isScrubbing) && autoScrollEnabled && activeLineIndex >= 0) {
            val activeLine = cumulativeLines.getOrNull(activeLineIndex)
            if (activeLine != null) {
                val filteredIdx = filteredLines.indexOfFirst { it.globalId == activeLine.globalId }
                if (filteredIdx >= 0) {
                    val headerOffset = if (masterSummary != null) 3 else 2
                    val targetListIndex = headerOffset + filteredIdx
                    val isAlreadyVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == targetListIndex }
                    if (!isAlreadyVisible && targetListIndex != lastScrolledIndex) {
                        try {
                            listState.animateScrollToItem(targetListIndex.coerceAtLeast(0))
                            lastScrolledIndex = targetListIndex
                        } catch (e: Exception) {
                            // Ignore scroll animation cancellations
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isMultiPartSession) sessionTitle else "Playback & Review",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isMultiPartSession) {
                                "${orderedParts.size} Sequential Parts • ${formatDuration(totalCumulativeDurationMs)} total"
                            } else {
                                dateFormat.format(Date(record.timestamp))
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Journal"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val combinedTranscript = if (isMultiPartSession) {
                                orderedParts.mapIndexed { idx, p ->
                                    "--- Part ${(p.partIndex ?: idx) + 1} of ${orderedParts.size} ---\n${p.text}"
                                }.joinToString("\n\n")
                            } else {
                                record.text
                            }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clip = ClipData.newPlainText("Transcription", combinedTranscript)
                            clipboard?.setPrimaryClip(clip)
                            Toast.makeText(context, "Full transcript copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Full Transcript"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            // Hero Audio Player Deck (Unified for Single & Multi-Part)
            item(key = "player_hero_deck") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Header & Part Badges
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isMultiPartSession) Icons.Default.QueueMusic else Icons.Default.Audiotrack,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = if (isMultiPartSession) {
                                            "Part ${(currentPartRecord.partIndex ?: currentPartIndex) + 1} of ${orderedParts.size}"
                                        } else {
                                            currentPartRecord.speakerName?.takeIf { it.isNotBlank() } ?: "Voice Recording"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isMultiPartSession) {
                                            "Gapless multi-part session active"
                                        } else {
                                            "Touch waveform to seek"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Model Badge
                            currentPartRecord.modelName?.takeIf { it.isNotBlank() }?.let { modelName ->
                                SuggestionChip(
                                    onClick = {},
                                    label = {
                                        Text(
                                            text = modelName,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    border = null,
                                    modifier = Modifier.height(24.dp)
                                )
                            }

                            // Diarization Confidence Badge
                            currentPartRecord.diarizationConfidence?.takeIf { it.isNotBlank() }?.let { conf ->
                                val confColor = when {
                                    conf.contains("High", true) -> MaterialTheme.colorScheme.primary
                                    conf.contains("Med", true) -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.error
                                }
                                SuggestionChip(
                                    onClick = { showVerifyConfidenceDialog = true },
                                    icon = {
                                        Icon(
                                            imageVector = if (conf.contains("High", true)) Icons.Default.Verified else Icons.Default.Warning,
                                            contentDescription = "Verify or Correct Speaker Labels",
                                            modifier = Modifier.size(14.dp),
                                            tint = confColor
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = "Conf: $conf (Verify/Correct)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = confColor
                                        )
                                    },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = confColor.copy(alpha = 0.15f)
                                    ),
                                    border = null,
                                    modifier = Modifier.height(24.dp)
                                )
                            }
                        }

                        // Multi-Part Interactive Sequence Selector Pills
                        if (isMultiPartSession) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 2.dp)
                            ) {
                                itemsIndexed(orderedParts) { idx, partItem ->
                                    val isSelected = (idx == currentPartIndex)
                                    val partNumber = (partItem.partIndex ?: idx) + 1
                                    val pDur = partDurationsMap[idx] ?: 0

                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            if (idx != currentPartIndex) {
                                                currentPartIndex = idx
                                                currentPartPositionMs = 0
                                                initPlayerForPart(idx, autoPlay = isPlaying, startPosMs = 0)
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = "Part $partNumber (${if (pDur > 0) formatDuration(pDur) else "--:--"})",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        },
                                        leadingIcon = {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = if (isPlaying) Icons.Default.VolumeUp else Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                            }
                        }

                        // Audio Error Warning
                        if (hasAudioError) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = audioErrorMessage ?: "Audio file could not be loaded for playback.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        } else {
                            // Unified Interactive Waveform Component
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                InteractiveWaveformVisualizer(
                                    progress = currentGlobalProgress,
                                    amplitudes = waveformAmplitudes,
                                    partCutFractions = partCutFractions,
                                    onSeek = { fraction ->
                                        seekToGlobalFraction(fraction)
                                    },
                                    onScrubbingChange = { scrubbing, fraction ->
                                        isScrubbing = scrubbing
                                        scrubFraction = fraction
                                    },
                                    isScrubbing = isScrubbing,
                                    scrubFraction = scrubFraction,
                                    isClippingMode = isClippingMode,
                                    clipStartFraction = clipStartFraction,
                                    clipEndFraction = clipEndFraction,
                                    onClipBoundsChange = { startFrac, endFrac ->
                                        clipStartFraction = startFrac
                                        clipEndFraction = endFrac
                                    }
                                )

                                if (isClippingMode) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                        ),
                                        shape = MaterialTheme.shapes.medium,
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ContentCut,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.tertiary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Text(
                                                        text = "Golden Sample Bounds Editor",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { isClippingMode = false },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Cancel Editing",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }

                                            val activeStartMs = (clipStartFraction * totalCumulativeDurationMs).toInt()
                                            val activeEndMs = (clipEndFraction * totalCumulativeDurationMs).toInt()
                                            val activeDurationMs = activeEndMs - activeStartMs

                                            Text(
                                                text = "Drag handles on waveform to trim. Clip: ${formatDuration(activeStartMs)} - ${formatDuration(activeEndMs)} (${activeDurationMs / 1000}s)",
                                                style = MaterialTheme.typography.bodySmall
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                TextButton(
                                                    onClick = { isClippingMode = false },
                                                    colors = ButtonDefaults.textButtonColors(
                                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                                    )
                                                ) {
                                                    Text("Cancel")
                                                }
                                                Button(
                                                    onClick = {
                                                        clippingLine?.let { originalLine ->
                                                            val partIdx = originalLine.partIndex
                                                            val partOffset = cumulativeOffsets.getOrElse(partIdx) { 0 }
                                                            val partDur = partDurationsMap[partIdx] ?: 5000

                                                            val globalStartMs = (clipStartFraction * totalCumulativeDurationMs).toInt()
                                                            val globalEndMs = (clipEndFraction * totalCumulativeDurationMs).toInt()

                                                            val localStartMs = (globalStartMs - partOffset).coerceIn(0, partDur)
                                                            val localEndMs = (globalEndMs - partOffset).coerceIn(localStartMs + 500, partDur)

                                                            val updatedLine = originalLine.copy(
                                                                localStartMs = localStartMs,
                                                                localEndMs = localEndMs,
                                                                globalStartMs = globalStartMs,
                                                                globalEndMs = globalEndMs
                                                            )
                                                            candidateGoldenLine = updatedLine
                                                        }
                                                        isClippingMode = false
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.tertiary,
                                                        contentColor = MaterialTheme.colorScheme.onTertiary
                                                    )
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Select Speaker")
                                                }
                                            }
                                        }
                                    }
                                }

                                // Global Time & Scrub Feedback
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val displayGlobalMs = if (isScrubbing) {
                                        (scrubFraction * totalCumulativeDurationMs).toInt()
                                    } else {
                                        currentGlobalPositionMs
                                    }

                                    Column {
                                        Text(
                                            text = formatDuration(displayGlobalMs),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (isMultiPartSession) {
                                            Text(
                                                text = "Part ${currentPartIndex + 1}: ${formatDuration(currentPartPositionMs)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (isScrubbing) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = MaterialTheme.shapes.extraSmall
                                        ) {
                                            Text(
                                                text = "Scrubbing: ${formatDuration(displayGlobalMs)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = formatDuration(totalCumulativeDurationMs),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (isMultiPartSession) {
                                            Text(
                                                text = "Total Session (${orderedParts.size} parts)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            // Controls Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Speed Chip
                                TextButton(
                                    onClick = {
                                        val nextSpeed = when (playbackSpeed) {
                                            1.0f -> 1.25f
                                            1.25f -> 1.5f
                                            1.5f -> 2.0f
                                            2.0f -> 0.75f
                                            else -> 1.0f
                                        }
                                        playbackSpeed = nextSpeed
                                        mediaPlayer?.let {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                try {
                                                    it.playbackParams = it.playbackParams.setSpeed(nextSpeed)
                                                } catch (e: Exception) {
                                                    // Ignore
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                                ) {
                                    Text(
                                        text = "${playbackSpeed}x",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Previous Part Button (in Multi-Part mode)
                                if (isMultiPartSession) {
                                    IconButton(
                                        onClick = {
                                            if (currentPartIndex > 0) {
                                                val prevIdx = currentPartIndex - 1
                                                currentPartIndex = prevIdx
                                                currentPartPositionMs = 0
                                                initPlayerForPart(prevIdx, autoPlay = isPlaying, startPosMs = 0)
                                            }
                                        },
                                        enabled = currentPartIndex > 0,
                                        modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SkipPrevious,
                                            contentDescription = "Previous Part",
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }

                                // Rewind 10s
                                IconButton(
                                    onClick = {
                                        seekToGlobalMillis(currentGlobalPositionMs - 10000)
                                    },
                                    modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FastRewind,
                                        contentDescription = "Rewind 10 seconds",
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                // Hero Play / Pause Button
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    shadowElevation = 4.dp,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clickable {
                                            if (mediaPlayer == null) {
                                                initPlayerForPart(currentPartIndex, autoPlay = true, startPosMs = currentPartPositionMs)
                                            } else {
                                                mediaPlayer?.let { player ->
                                                    try {
                                                        if (isPlaying) {
                                                            player.pause()
                                                            isPlaying = false
                                                        } else {
                                                            player.start()
                                                            isPlaying = true
                                                        }
                                                    } catch (e: Exception) {
                                                        initPlayerForPart(currentPartIndex, autoPlay = true, startPosMs = currentPartPositionMs)
                                                    }
                                                }
                                            }
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "Pause" else "Play",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }
                                }

                                // Forward 10s
                                IconButton(
                                    onClick = {
                                        seekToGlobalMillis(currentGlobalPositionMs + 10000)
                                    },
                                    modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FastForward,
                                        contentDescription = "Forward 10 seconds",
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                // Next Part Button (in Multi-Part mode)
                                if (isMultiPartSession) {
                                    IconButton(
                                        onClick = {
                                            if (currentPartIndex < orderedParts.size - 1) {
                                                val nextIdx = currentPartIndex + 1
                                                currentPartIndex = nextIdx
                                                currentPartPositionMs = 0
                                                initPlayerForPart(nextIdx, autoPlay = isPlaying, startPosMs = 0)
                                            }
                                        },
                                        enabled = currentPartIndex < orderedParts.size - 1,
                                        modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SkipNext,
                                            contentDescription = "Next Part",
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                } else {
                                    // Restart to start for single recordings
                                    IconButton(
                                        onClick = {
                                            seekToGlobalMillis(0)
                                        },
                                        modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Replay,
                                            contentDescription = "Restart audio",
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Consolidated Master Summary / Highlights Card
            masterSummary?.let { summaryText ->
                item(key = "master_summary_deck") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isMultiPartSession) "Master AI Summary (Consolidated)" else "AI Key Highlights & Takeaways",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Text(
                                text = summaryText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                            )
                        }
                    }
                }
            }

            // Transcript Header & Auto-scroll / Search Controls
            item(key = "transcript_controls_header") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isMultiPartSession) "Unified Cumulative Transcript" else "Interactive Transcript",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${cumulativeLines.size} lines across ${orderedParts.size} ${if (orderedParts.size > 1) "parts" else "recording"} • Tap any line to seek",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Auto-scroll toggle chip
                        FilterChip(
                            selected = autoScrollEnabled,
                            onClick = { autoScrollEnabled = !autoScrollEnabled },
                            label = {
                                Text(
                                    text = if (autoScrollEnabled) "Auto-Scroll ON" else "Auto-Scroll OFF",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (autoScrollEnabled) Icons.Default.Sync else Icons.Default.SyncDisabled,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }

                    OutlinedTextField(
                        value = transcriptSearchQuery,
                        onValueChange = { transcriptSearchQuery = it },
                        placeholder = { Text("Search words across transcript...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            if (transcriptSearchQuery.isNotBlank()) {
                                IconButton(
                                    onClick = { transcriptSearchQuery = "" },
                                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    )
                }
            }

            // Cumulative Interactive Transcript Lines
            if (filteredLines.isEmpty()) {
                item(key = "empty_transcript_filter") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No matching transcript lines found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                items(filteredLines, key = { "line_${it.globalId}" }) { line ->
                    val isCurrentLine = activeLineIndex >= 0 && line.globalId == cumulativeLines.getOrNull(activeLineIndex)?.globalId

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Part Transition Header when entering a new part in multi-part mode
                        if (line.isFirstLineOfPart && isMultiPartSession) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FolderZip,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Part ${line.partNumber} of ${line.totalParts}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Text(
                                            text = "• ${formatDuration(line.globalStartMs)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                        )
                                    }

                                    TextButton(
                                        onClick = {
                                            seekToGlobalMillis(line.globalStartMs)
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.heightIn(min = 32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Play Part",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                            }
                        }

                        // Transcript Line Card
                        val cardBgColor by animateColorAsState(
                            targetValue = if (isCurrentLine) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            label = "cardBgColor"
                        )

                        val cardElevation by animateDpAsState(
                            targetValue = if (isCurrentLine) 2.dp else 0.dp,
                            label = "cardElevation"
                        )

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    seekToGlobalMillis(line.globalStartMs)
                                },
                            colors = CardDefaults.cardColors(containerColor = cardBgColor),
                            elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
                            border = if (isCurrentLine) {
                                BorderStroke(
                                    1.5.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                            } else null,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (isCurrentLine) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .padding(vertical = 4.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp),
                                            modifier = Modifier
                                                .width(4.dp)
                                                .fillMaxHeight()
                                        ) {}
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            start = if (isCurrentLine) 16.dp else 14.dp,
                                            top = 12.dp,
                                            end = 14.dp,
                                            bottom = 12.dp
                                        ),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Timestamp Pill
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isCurrentLine) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant
                                                },
                                                modifier = Modifier.clickable {
                                                    seekToGlobalMillis(line.globalStartMs)
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isCurrentLine && isPlaying) Icons.Default.VolumeUp else Icons.Default.PlayArrow,
                                                        contentDescription = "Jump to timestamp",
                                                        tint = if (isCurrentLine) {
                                                            MaterialTheme.colorScheme.onPrimary
                                                        } else {
                                                            MaterialTheme.colorScheme.primary
                                                        },
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text(
                                                        text = line.displayTimestamp,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isCurrentLine) {
                                                            MaterialTheme.colorScheme.onPrimary
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                        }
                                                    )
                                                }
                                            }

                                            if (isCurrentLine) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = if (isPlaying) "PLAYING" else "CURRENT",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // Speaker tag & Golden Sample bookmark
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            line.speaker?.let { speakerName ->
                                                Text(
                                                    text = speakerName,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isCurrentLine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                                )
                                            }

                                            val partAudioPath = orderedParts.getOrNull(line.partIndex)?.audioFilePath ?: record.audioFilePath
                                            if (!partAudioPath.isNullOrBlank() && onAssignGoldenSample != null) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                                                    modifier = Modifier.clickable {
                                                        clippingLine = line
                                                        clipStartFraction = line.globalStartMs.toFloat() / totalCumulativeDurationMs.toFloat()
                                                        clipEndFraction = line.globalEndMs.toFloat() / totalCumulativeDurationMs.toFloat()
                                                        isClippingMode = true
                                                    }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Verified,
                                                            contentDescription = "Tag as Golden Sample",
                                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Text(
                                                            text = "Golden Clip",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Line text with highlighted search keywords
                                    val annotatedText = remember(line.text, transcriptSearchQuery, isCurrentLine) {
                                        if (transcriptSearchQuery.isBlank()) {
                                            buildAnnotatedString { append(line.text) }
                                        } else {
                                            val pattern = Regex(Regex.escape(transcriptSearchQuery), RegexOption.IGNORE_CASE)
                                            buildAnnotatedString {
                                                append(line.text)
                                                pattern.findAll(line.text).forEach { matchResult ->
                                                    addStyle(
                                                        style = SpanStyle(
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF1B5E20),
                                                            background = Color(0xFFA5D6A7)
                                                        ),
                                                        start = matchResult.range.first,
                                                        end = matchResult.range.last + 1
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Text(
                                        text = annotatedText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isCurrentLine) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isCurrentLine) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                        },
                                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                                    )

                                    // Intra-line progress indicator for active line
                                    if (isCurrentLine && totalCumulativeDurationMs > 0) {
                                        val lineDuration = (line.globalEndMs - line.globalStartMs).coerceAtLeast(1000)
                                        val lineProgress = ((effectiveGlobalPositionMs - line.globalStartMs).toFloat() / lineDuration).coerceIn(0f, 1f)
                                        LinearProgressIndicator(
                                            progress = { lineProgress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(3.dp)
                                                .clip(RoundedCornerShape(2.dp)),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showVerifyConfidenceDialog) {
        var editedSpeakers by remember(currentPartRecord) { mutableStateOf(currentPartRecord.activeSpeakersCsv ?: currentPartRecord.speakerLabels ?: "") }
        var selectedConfidence by remember(currentPartRecord) { mutableStateOf(currentPartRecord.diarizationConfidence ?: "High") }

        AlertDialog(
            onDismissRequest = { showVerifyConfidenceDialog = false },
            title = { Text("Verify Diarization Labels") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Manually verify or correct the detected speakers and diarization confidence score for this audio part.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    OutlinedTextField(
                        value = editedSpeakers,
                        onValueChange = { editedSpeakers = it },
                        label = { Text("Active Speakers (comma-separated)") },
                        placeholder = { Text("e.g. Austin Grindy, Speaker A") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Diarization Confidence Level",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("High", "Medium", "Low").forEach { level ->
                            val isSelected = selectedConfidence.contains(level, true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedConfidence = level },
                                label = { Text(level) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateConfidenceAndSpeakers?.invoke(
                            currentPartRecord.id,
                            editedSpeakers.trim(),
                            selectedConfidence
                        )
                        showVerifyConfidenceDialog = false
                    }
                ) {
                    Text("Verify & Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showVerifyConfidenceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal dialog to designate candidate line as a Golden Reference Audio Sample
    if (candidateGoldenLine != null) {
        val line = candidateGoldenLine!!
        val audioPath = orderedParts.getOrNull(line.partIndex)?.audioFilePath ?: record.audioFilePath ?: ""
        AssignGoldenSampleDialog(
            line = line,
            audioUri = audioPath,
            recordingTitle = sessionTitle,
            speakers = speakers,
            onDismiss = { candidateGoldenLine = null },
            onAssign = { speakerId ->
                onAssignGoldenSample?.invoke(
                    speakerId,
                    audioPath,
                    line.localStartMs,
                    line.localEndMs,
                    sessionTitle
                )
                candidateGoldenLine = null
            },
            onCreateNewSpeaker = { name ->
                val newProfile = SpeakerProfile(
                    name = name,
                    goldenSampleAudioUri = audioPath,
                    goldenSampleStartMs = line.localStartMs,
                    goldenSampleEndMs = line.localEndMs,
                    goldenSampleRecordingTitle = sessionTitle
                )
                onSaveNewSpeaker?.invoke(newProfile)
                candidateGoldenLine = null
            }
        )
    }
}

/**
 * Dialog for selecting/creating a Speaker Profile to assign a designated Golden Audio Reference clip.
 */
@Composable
fun AssignGoldenSampleDialog(
    line: CumulativeTranscriptLine,
    audioUri: String,
    recordingTitle: String,
    speakers: List<SpeakerProfile>,
    onDismiss: () -> Unit,
    onAssign: (speakerId: String) -> Unit,
    onCreateNewSpeaker: (name: String) -> Unit
) {
    val context = LocalContext.current
    val matchedSpeaker = remember(line.speaker, speakers) {
        line.speaker?.let { spk ->
            speakers.find { it.name.equals(spk, ignoreCase = true) }
        }
    }

    var selectedSpeakerId by remember {
        mutableStateOf(matchedSpeaker?.id ?: speakers.firstOrNull()?.id ?: "")
    }
    var isCreatingNew by remember {
        mutableStateOf(speakers.isEmpty() || matchedSpeaker == null && line.speaker != null)
    }
    var newSpeakerName by remember {
        mutableStateOf(if (matchedSpeaker == null && line.speaker != null) line.speaker else "")
    }

    var isPreviewPlaying by remember { mutableStateOf(false) }
    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    val durationMs = (line.localEndMs - line.localStartMs).coerceAtLeast(1000)

    DisposableEffect(audioUri) {
        onDispose {
            previewPlayer?.stop()
            previewPlayer?.release()
            previewPlayer = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text("Designate Golden Sample", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Line preview excerpt
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "\"${line.text}\"",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Clip: ${formatDuration(line.localStartMs)} - ${formatDuration(line.localEndMs)} (${durationMs / 1000}s)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = {
                                    try {
                                        if (previewPlayer == null) {
                                            val p = MediaPlayer().apply {
                                                setDataSource(context, Uri.parse(audioUri))
                                                prepare()
                                                seekTo(line.localStartMs)
                                                setOnCompletionListener {
                                                    isPreviewPlaying = false
                                                }
                                            }
                                            previewPlayer = p
                                            p.start()
                                            isPreviewPlaying = true
                                        } else {
                                            if (isPreviewPlaying) {
                                                previewPlayer?.pause()
                                                isPreviewPlaying = false
                                            } else {
                                                previewPlayer?.seekTo(line.localStartMs)
                                                previewPlayer?.start()
                                                isPreviewPlaying = true
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Could not preview: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPreviewPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                    contentDescription = "Preview Clip",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                // Speaker Selection
                Text(
                    text = "Assign Reference Sample To:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                if (speakers.isNotEmpty()) {
                    speakers.forEach { speaker ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedSpeakerId = speaker.id
                                    isCreatingNew = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(
                                selected = !isCreatingNew && selectedSpeakerId == speaker.id,
                                onClick = {
                                    selectedSpeakerId = speaker.id
                                    isCreatingNew = false
                                }
                            )
                            Column {
                                Text(
                                    text = speaker.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (!speaker.relationshipOrRole.isNullOrBlank()) {
                                    Text(
                                        text = speaker.relationshipOrRole,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Option: Create New Profile
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isCreatingNew = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(
                        selected = isCreatingNew,
                        onClick = { isCreatingNew = true }
                    )
                    Text(
                        text = "+ Create New Speaker Profile",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (isCreatingNew) {
                    OutlinedTextField(
                        value = newSpeakerName,
                        onValueChange = { newSpeakerName = it },
                        label = { Text("New Speaker Name *") },
                        placeholder = { Text("e.g. Austin Grindy") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isCreatingNew) {
                        if (newSpeakerName.isNotBlank()) {
                            onCreateNewSpeaker(newSpeakerName.trim())
                        } else {
                            Toast.makeText(context, "Please enter speaker name", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        if (selectedSpeakerId.isNotBlank()) {
                            onAssign(selectedSpeakerId)
                        }
                    }
                }
            ) {
                Text("Set as Golden Sample")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
