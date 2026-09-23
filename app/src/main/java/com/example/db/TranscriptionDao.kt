package com.example.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptionDao {
    @Query("SELECT * FROM transcriptions ORDER BY timestamp DESC")
    fun getAllTranscriptions(): Flow<List<Transcription>>

    @Query("SELECT * FROM transcriptions ORDER BY timestamp DESC")
    suspend fun getAllTranscriptionsSync(): List<Transcription>

    @Query("UPDATE transcriptions SET user_id = :newUserId WHERE user_id = :oldUserId OR user_id IS NULL OR user_id = ''")
    suspend fun updateUserIdForLocalRecords(oldUserId: String, newUserId: String)

    @Query("SELECT * FROM transcriptions WHERE id = :id")
    suspend fun getTranscriptionById(id: String): Transcription?

    @Query("SELECT * FROM transcriptions WHERE session_id = :sessionId ORDER BY part_index ASC")
    fun getTranscriptionsBySession(sessionId: String): Flow<List<Transcription>>

    @Query("SELECT * FROM transcriptions WHERE session_id = :sessionId ORDER BY part_index ASC")
    suspend fun getTranscriptionsBySessionSync(sessionId: String): List<Transcription>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscription(transcription: Transcription)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscriptions(transcriptions: List<Transcription>)

    @Update
    suspend fun updateTranscription(transcription: Transcription)

    @Delete
    suspend fun deleteTranscription(transcription: Transcription)

    @Query("DELETE FROM transcriptions WHERE session_id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM transcriptions")
    suspend fun clearAll()

    @Query("DELETE FROM transcriptions WHERE id IN (:ids)")
    suspend fun deleteTranscriptions(ids: List<String>)
}
