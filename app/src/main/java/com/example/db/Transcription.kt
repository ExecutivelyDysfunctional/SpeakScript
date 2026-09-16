package com.example.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Room Entity representing an audio transcription item.
 * Stores the transcription text, speaker labels, and associated audio file path.
 */
@Entity(tableName = "transcriptions")
data class Transcription(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    @ColumnInfo(name = "transcription")
    val transcription: String = "",
    @ColumnInfo(name = "speaker_labels")
    val speakerLabels: String? = null,
    @ColumnInfo(name = "audio_file_path")
    val audioFilePath: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val summary: String? = null,
    val category: String? = null,
    val modelName: String? = null,
    @ColumnInfo(name = "session_id")
    val sessionId: String? = null,
    @ColumnInfo(name = "part_index")
    val partIndex: Int? = null,
    @ColumnInfo(name = "total_parts")
    val totalParts: Int? = null,
    @ColumnInfo(name = "session_title")
    val sessionTitle: String? = null,
    @ColumnInfo(name = "part_duration_ms")
    val partDurationMs: Int? = null,
    @ColumnInfo(name = "drive_file_id")
    val driveFileId: String? = null
) {
    // Backwards-compatibility convenience properties
    val text: String
        @com.google.firebase.firestore.Exclude
        @Ignore get() = transcription

    val speakerName: String?
        @com.google.firebase.firestore.Exclude
        @Ignore get() = speakerLabels

    val audioUri: String?
        @com.google.firebase.firestore.Exclude
        @Ignore get() = audioFilePath

    @Ignore
    constructor(
        id: String = UUID.randomUUID().toString(),
        userId: String = "",
        text: String,
        timestamp: Long = System.currentTimeMillis(),
        audioUri: String? = null,
        summary: String? = null,
        category: String? = null,
        speakerName: String? = null,
        modelName: String? = null
    ) : this(
        id = id,
        userId = userId,
        transcription = text,
        speakerLabels = speakerName,
        audioFilePath = audioUri,
        timestamp = timestamp,
        summary = summary,
        category = category,
        modelName = modelName,
        sessionId = null,
        partIndex = null,
        totalParts = null,
        sessionTitle = null,
        partDurationMs = null
    )
}

typealias TranscriptionRecord = Transcription
