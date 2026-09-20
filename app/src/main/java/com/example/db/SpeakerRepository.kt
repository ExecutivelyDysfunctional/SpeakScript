package com.example.db

import kotlinx.coroutines.flow.Flow

class SpeakerRepository(private val speakerDao: SpeakerDao) {
    val allSpeakers: Flow<List<SpeakerProfile>> = speakerDao.getAllSpeakers()

    fun getSpeakersForUser(userId: String): Flow<List<SpeakerProfile>> = speakerDao.getSpeakersForUser(userId)

    suspend fun getSpeakersForUserSync(userId: String): List<SpeakerProfile> = speakerDao.getSpeakersForUserSync(userId)

    suspend fun updateUserIdForLocalRecords(oldUserId: String = "local_user", newUserId: String) {
        speakerDao.updateUserIdForLocalRecords(oldUserId, newUserId)
    }

    suspend fun getAllSpeakersSync(): List<SpeakerProfile> = speakerDao.getAllSpeakersSync()

    suspend fun getById(id: String): SpeakerProfile? = speakerDao.getSpeakerById(id)

    suspend fun getByName(name: String): SpeakerProfile? = speakerDao.getSpeakerByName(name)

    suspend fun insert(speaker: SpeakerProfile) {
        speakerDao.insertSpeaker(speaker)
    }

    suspend fun insertAll(speakers: List<SpeakerProfile>) {
        speakerDao.insertSpeakers(speakers)
    }

    suspend fun update(speaker: SpeakerProfile) {
        speakerDao.updateSpeaker(speaker)
    }

    suspend fun delete(speaker: SpeakerProfile) {
        speakerDao.deleteSpeaker(speaker)
    }

    suspend fun deleteById(id: String) {
        speakerDao.deleteSpeakerById(id)
    }

    suspend fun clear() {
        speakerDao.clearAll()
    }

    /**
     * Cross-references an extracted speaker name against the roster.
     * Updates recording count and last heard timestamp if found, or creates a basic profile if needed.
     */
    suspend fun recordSpeakerHeard(name: String, timestamp: Long): SpeakerProfile {
        val cleanName = name.trim()
        val existing = speakerDao.getSpeakerByName(cleanName)
        if (existing != null) {
            val updated = existing.copy(
                totalRecordingsCount = existing.totalRecordingsCount + 1,
                lastHeardTimestamp = timestamp
            )
            speakerDao.updateSpeaker(updated)
            return updated
        } else {
            val newProfile = SpeakerProfile(
                name = cleanName,
                totalRecordingsCount = 1,
                lastHeardTimestamp = timestamp
            )
            speakerDao.insertSpeaker(newProfile)
            return newProfile
        }
    }
}
