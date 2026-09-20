package com.example.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Room Entity representing a known speaker in the user's directory.
 * Stores personal profile details, stats, role, color badge, and golden sample audio reference.
 */
@Entity(tableName = "speaker_profiles")
data class SpeakerProfile(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "user_id", defaultValue = "")
    val userId: String = "",
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "relationship_or_role")
    val relationshipOrRole: String? = null,
    @ColumnInfo(name = "avatar_uri")
    val avatarUri: String? = null,
    @ColumnInfo(name = "color_hex")
    val colorHex: String? = null,
    @ColumnInfo(name = "golden_sample_audio_uri")
    val goldenSampleAudioUri: String? = null,
    @ColumnInfo(name = "golden_sample_start_ms")
    val goldenSampleStartMs: Int? = null,
    @ColumnInfo(name = "golden_sample_end_ms")
    val goldenSampleEndMs: Int? = null,
    @ColumnInfo(name = "golden_sample_recording_title")
    val goldenSampleRecordingTitle: String? = null,
    @ColumnInfo(name = "total_recordings_count")
    val totalRecordingsCount: Int = 0,
    @ColumnInfo(name = "last_heard_timestamp")
    val lastHeardTimestamp: Long? = null,
    @ColumnInfo(name = "notes")
    val notes: String? = null
) {
    val hasGoldenSample: Boolean
        get() = !goldenSampleAudioUri.isNullOrBlank()
}
