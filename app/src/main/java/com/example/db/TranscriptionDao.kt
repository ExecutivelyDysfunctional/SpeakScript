package com.example.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.example.ui.TranscriptionRecord

@Dao
interface TranscriptionDao {
    @Query("SELECT * FROM transcriptions ORDER BY timestamp DESC")
    fun getAllTranscriptions(): Flow<List<TranscriptionRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscription(record: TranscriptionRecord)

    @Query("DELETE FROM transcriptions")
    suspend fun clearAll()

    @Query("DELETE FROM transcriptions WHERE id IN (:ids)")
    suspend fun deleteTranscriptions(ids: List<String>)
}
