package com.example.db

import kotlinx.coroutines.flow.Flow

class TranscriptionRepository(private val transcriptionDao: TranscriptionDao) {
    val allTranscriptions: Flow<List<Transcription>> = transcriptionDao.getAllTranscriptions()

    suspend fun getAllTranscriptionsSync(): List<Transcription> = transcriptionDao.getAllTranscriptionsSync()

    suspend fun updateUserIdForLocalRecords(oldUserId: String = "local_user", newUserId: String) {
        transcriptionDao.updateUserIdForLocalRecords(oldUserId, newUserId)
    }

    suspend fun getById(id: String): Transcription? = transcriptionDao.getTranscriptionById(id)

    fun getBySession(sessionId: String): Flow<List<Transcription>> = transcriptionDao.getTranscriptionsBySession(sessionId)

    suspend fun getBySessionSync(sessionId: String): List<Transcription> = transcriptionDao.getTranscriptionsBySessionSync(sessionId)

    suspend fun deleteSession(sessionId: String) {
        transcriptionDao.deleteSession(sessionId)
    }

    suspend fun insert(transcription: Transcription) {
        transcriptionDao.insertTranscription(transcription)
    }

    suspend fun insertAll(transcriptions: List<Transcription>) {
        transcriptionDao.insertTranscriptions(transcriptions)
    }

    suspend fun update(transcription: Transcription) {
        transcriptionDao.updateTranscription(transcription)
    }

    suspend fun clear() {
        transcriptionDao.clearAll()
    }

    suspend fun delete(transcription: Transcription) {
        transcriptionDao.deleteTranscription(transcription)
    }

    suspend fun deleteTranscriptions(ids: List<String>) {
        transcriptionDao.deleteTranscriptions(ids)
    }
}
