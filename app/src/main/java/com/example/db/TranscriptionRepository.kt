package com.example.db

import kotlinx.coroutines.flow.Flow
import com.example.ui.TranscriptionRecord

class TranscriptionRepository(private val transcriptionDao: TranscriptionDao) {
    val allTranscriptions: Flow<List<TranscriptionRecord>> = transcriptionDao.getAllTranscriptions()

    suspend fun insert(record: TranscriptionRecord) {
        transcriptionDao.insertTranscription(record)
    }

    suspend fun clear() {
        transcriptionDao.clearAll()
    }
}
