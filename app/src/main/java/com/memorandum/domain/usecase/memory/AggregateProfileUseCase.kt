package com.memorandum.domain.usecase.memory

import android.util.Log
import com.memorandum.data.local.room.dao.MemoryDao
import com.memorandum.data.local.room.dao.UserProfileDao
import com.memorandum.data.local.room.entity.MemoryEntity
import com.memorandum.data.local.room.entity.UserProfileEntity
import com.memorandum.data.local.room.enums.MemoryType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

@Serializable
data class UserProfileJson(
    val availableTimeSlots: List<String> = emptyList(),
    val preferredWorkHours: List<String> = emptyList(),
    val taskGranularity: List<String> = emptyList(),
    val notificationTolerance: List<String> = emptyList(),
    val procrastinationPatterns: List<String> = emptyList(),
    val commonBlockers: List<String> = emptyList(),
    val longTermGoals: List<String> = emptyList(),
    val executionHabits: List<String> = emptyList(),
    val intensiveTaskDuration: List<String> = emptyList(),
    val prepPreferences: List<String> = emptyList(),
)

class AggregateProfileUseCase @Inject constructor(
    private val memoryDao: MemoryDao,
    private val userProfileDao: UserProfileDao,
) {

    companion object {
        private const val TAG = "AggregateProfile"
    }

    suspend fun aggregate() {
        try {
            val memories = memoryDao.getHighConfidence(minConfidence = 0.4f)

            val profile = UserProfileJson(
                availableTimeSlots = extractFromMemories(memories, MemoryType.PREFERENCE, "时间"),
                preferredWorkHours = extractFromMemories(memories, MemoryType.PREFERENCE, "工作时段"),
                taskGranularity = extractFromMemories(memories, MemoryType.PREFERENCE, "粒度"),
                notificationTolerance = extractFromMemories(memories, MemoryType.PREFERENCE, "提醒"),
                procrastinationPatterns = extractFromMemories(memories, MemoryType.PATTERN, "拖延"),
                commonBlockers = extractFromMemories(memories, MemoryType.PATTERN, "阻塞"),
                longTermGoals = extractFromMemories(memories, MemoryType.LONG_TERM_GOAL),
                executionHabits = extractFromMemories(memories, MemoryType.PATTERN, "习惯"),
                intensiveTaskDuration = extractFromMemories(memories, MemoryType.PREFERENCE, "时长"),
                prepPreferences = extractFromMemories(memories, MemoryType.PREP_TEMPLATE),
            )

            val existing = userProfileDao.get()
            val newVersion = (existing?.version ?: 0) + 1

            userProfileDao.upsert(
                UserProfileEntity(
                    id = "default",
                    profileJson = Json.encodeToString(profile),
                    version = newVersion,
                    updatedAt = System.currentTimeMillis(),
                )
            )
            Log.i(TAG, "Profile aggregated: version=$newVersion, memories=${memories.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to aggregate profile: ${e.message}")
        }
    }

    private fun extractFromMemories(
        memories: List<MemoryEntity>,
        type: MemoryType,
        subjectKeyword: String? = null,
    ): List<String> {
        return memories
            .filter { it.type == type }
            .filter { subjectKeyword == null || it.subject.contains(subjectKeyword) }
            .sortedByDescending { it.confidence }
            .take(3)
            .map { "${it.subject}: ${it.content} (置信度: ${it.confidence})" }
    }
}
