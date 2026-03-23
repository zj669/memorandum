package com.memorandum.domain.usecase.memory

import android.util.Log
import com.memorandum.ai.schema.MemoryOutput
import com.memorandum.data.local.room.dao.MemoryDao
import com.memorandum.data.local.room.entity.MemoryEntity
import com.memorandum.data.local.room.enums.MemoryType
import java.util.UUID
import javax.inject.Inject

class ApplyMemoryOutputUseCase @Inject constructor(
    private val memoryDao: MemoryDao,
    private val aggregateProfileUseCase: AggregateProfileUseCase,
) {

    companion object {
        private const val TAG = "ApplyMemoryOutput"
    }

    suspend fun apply(output: MemoryOutput): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        var added = 0
        var updated = 0
        var downgraded = 0

        // 1. New memories
        for (newMem in output.newMemories) {
            val memType = parseMemoryType(newMem.type) ?: continue
            val entity = MemoryEntity(
                id = UUID.randomUUID().toString(),
                type = memType,
                subject = newMem.subject,
                content = newMem.content,
                confidence = newMem.confidence.coerceIn(0f, 1f),
                sourceRefsJson = newMem.sourceRefs,
                evidenceCount = newMem.sourceRefs.size,
                updatedAt = now,
                lastUsedAt = null,
            )
            memoryDao.upsert(entity)
            added++
        }

        // 2. Updates
        for (update in output.updates) {
            val existing = memoryDao.getById(update.memoryId) ?: continue
            val updatedEntity = existing.copy(
                content = update.content ?: existing.content,
                confidence = (update.confidence ?: existing.confidence).coerceIn(0f, 1f),
                sourceRefsJson = if (update.newSourceRefs.isNotEmpty()) {
                    (existing.sourceRefsJson + update.newSourceRefs).distinct()
                } else {
                    existing.sourceRefsJson
                },
                evidenceCount = existing.evidenceCount + update.newSourceRefs.size,
                updatedAt = now,
            )
            memoryDao.upsert(updatedEntity)
            updated++
        }

        // 3. Downgrades
        for (downgrade in output.downgrades) {
            memoryDao.updateConfidence(
                id = downgrade.memoryId,
                confidence = downgrade.newConfidence.coerceIn(0f, 1f),
                now = now,
            )
            downgraded++
        }

        // 4. Clean up very low confidence memories
        memoryDao.deleteByLowConfidence(threshold = 0.1f)

        // 5. Re-aggregate user profile
        aggregateProfileUseCase.aggregate()

        Log.i(TAG, "Applied: added=$added, updated=$updated, downgraded=$downgraded")
    }

    private fun parseMemoryType(type: String): MemoryType? {
        return try {
            MemoryType.valueOf(type.uppercase())
        } catch (_: IllegalArgumentException) {
            Log.w(TAG, "Unknown MemoryType: $type")
            MemoryType.TASK_CONTEXT
        }
    }
}
