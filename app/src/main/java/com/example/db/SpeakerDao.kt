package com.example.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeakerDao {
    @Query("SELECT * FROM speaker_profiles ORDER BY name ASC")
    fun getAllSpeakers(): Flow<List<SpeakerProfile>>

    @Query("SELECT * FROM speaker_profiles ORDER BY name ASC")
    suspend fun getAllSpeakersSync(): List<SpeakerProfile>

    @Query("SELECT * FROM speaker_profiles WHERE user_id = :userId OR user_id = '' OR user_id = 'local_user' ORDER BY name ASC")
    fun getSpeakersForUser(userId: String): Flow<List<SpeakerProfile>>

    @Query("SELECT * FROM speaker_profiles WHERE user_id = :userId OR user_id = '' OR user_id = 'local_user' ORDER BY name ASC")
    suspend fun getSpeakersForUserSync(userId: String): List<SpeakerProfile>

    @Query("UPDATE speaker_profiles SET user_id = :newUserId WHERE user_id = :oldUserId OR user_id = ''")
    suspend fun updateUserIdForLocalRecords(oldUserId: String = "local_user", newUserId: String)

    @Query("SELECT * FROM speaker_profiles WHERE id = :id")
    suspend fun getSpeakerById(id: String): SpeakerProfile?

    @Query("SELECT * FROM speaker_profiles WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getSpeakerByName(name: String): SpeakerProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpeaker(speaker: SpeakerProfile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpeakers(speakers: List<SpeakerProfile>)

    @Update
    suspend fun updateSpeaker(speaker: SpeakerProfile)

    @Delete
    suspend fun deleteSpeaker(speaker: SpeakerProfile)

    @Query("DELETE FROM speaker_profiles WHERE id = :id")
    suspend fun deleteSpeakerById(id: String)

    @Query("DELETE FROM speaker_profiles")
    suspend fun clearAll()
}
