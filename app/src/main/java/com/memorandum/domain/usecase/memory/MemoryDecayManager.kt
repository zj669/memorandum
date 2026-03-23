package com.memorandum.domain.usecase.memory

import android.util.Log
import com.memorandum.data.local.room.dao.MemoryDao
import javax.inject.Inject

class MemoryDecayManager @Inject constructor(
    private val memoryDao: MemoryDao,
) {

    companion object {
        private const val TAG = "MemoryDecayManager"
        private const val DAYS_90_MS = 90L * 24 * 3600_000
        private const val DAYS_180_MS = 180L * 24 * 3600_000
        private const val DECAY_90 = 0.9f
        private const val DECAY_180 = 0.7f
        private const val LOW_CONFIDENCE_THRESHOLD = 0.1f
    }

    suspend fun applyDecay() {
        val now = System.currentTimeMillis()
        val day90 = now - DAYS_90_MS
        val day180 = now - DAYS_180_MS

        val allMemories = memoryDao.getAll()
        var decayed = 0

        allMemories.forEach { mem ->
            val lastUsed = mem.lastUsedAt ?: mem.updatedAt
            val newConfidence = when {
                lastUsed < day180 -> mem.confidence * DECAY_180
                lastUsed < day90 -> mem.confidence * DECAY_90
                else -> mem.confidence
            }
            if (newConfidence != mem.confidence) {
                memoryDao.updateConfidence(mem.id, newConfidence, now)
                decayed++
            }
        }

        memoryDao.deleteByLowConfidence(LOW_CONFIDENCE_THRESHOLD)
        Log.i(TAG, "Decay applied: decayed=$decayed memories, cleaned low-confidence")
    }
}
