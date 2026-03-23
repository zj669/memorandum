package com.memorandum.domain.usecase.memory

import android.util.Log
import com.memorandum.data.local.room.dao.MemoryDao
import com.memorandum.data.local.room.dao.UserProfileDao
import com.memorandum.data.local.room.entity.EntryEntity
import com.memorandum.data.local.room.entity.MemoryEntity
import com.memorandum.data.local.room.entity.UserProfileEntity
import com.memorandum.data.local.room.enums.MemoryType
import javax.inject.Inject

data class MemoryContext(
    val profileSummary: String,
    val relevantMemories: List<MemoryEntity>,
    val totalTokenEstimate: Int,
) {
    fun toPromptText(): String {
        val sb = StringBuilder()
        sb.appendLine("## 用户画像")
        sb.appendLine(profileSummary)
        sb.appendLine()
        sb.appendLine("## 相关记忆")
        relevantMemories.forEach { mem ->
            sb.appendLine("- [${mem.type}] ${mem.subject}: ${mem.content} (置信度: ${mem.confidence})")
        }
        return sb.toString().take(1500)
    }
}

class MemoryInjector @Inject constructor(
    private val memoryDao: MemoryDao,
    private val userProfileDao: UserProfileDao,
) {

    companion object {
        private const val TAG = "MemoryInjector"
        private const val MAX_MEMORIES = 10
        private const val CHARS_PER_TOKEN_ESTIMATE = 2
    }

    suspend fun prepareForPlanning(entry: EntryEntity): MemoryContext {
        val profile = userProfileDao.get()
        val allMemories = memoryDao.getHighConfidence(0.4f)

        val relevant = allMemories
            .sortedByDescending { relevanceScore(it, entry) }
            .take(MAX_MEMORIES)

        // Mark usage time
        val now = System.currentTimeMillis()
        relevant.forEach { memoryDao.markUsed(it.id, now) }

        Log.d(TAG, "Prepared ${relevant.size} memories for entry: ${entry.id}")

        return MemoryContext(
            profileSummary = profile?.profileJson ?: "{}",
            relevantMemories = relevant,
            totalTokenEstimate = estimateTokens(profile, relevant),
        )
    }

    private fun relevanceScore(memory: MemoryEntity, entry: EntryEntity): Float {
        var score = 0f

        // Type match bonus
        score += when (memory.type) {
            MemoryType.PREFERENCE -> 0.3f
            MemoryType.PATTERN -> 0.2f
            MemoryType.LONG_TERM_GOAL -> 0.25f
            MemoryType.TASK_CONTEXT -> 0.15f
            MemoryType.PREP_TEMPLATE -> 0.1f
        }

        // Keyword match bonus
        val entryWords = entry.text.lowercase().toSet()
        val subjectWords = memory.subject.lowercase().toSet()
        val overlap = entryWords.intersect(subjectWords)
        if (overlap.isNotEmpty()) {
            score += 0.3f * (overlap.size.toFloat() / subjectWords.size.coerceAtLeast(1))
        }

        // Confidence weight
        score += memory.confidence * 0.2f

        // Recency bonus
        val daysSinceUsed = memory.lastUsedAt?.let {
            (System.currentTimeMillis() - it) / (24 * 3600_000f)
        } ?: 30f
        score += (1f / (1f + daysSinceUsed / 30f)) * 0.1f

        return score
    }

    private fun estimateTokens(
        profile: UserProfileEntity?,
        memories: List<MemoryEntity>,
    ): Int {
        val profileChars = profile?.profileJson?.length ?: 2
        val memoryChars = memories.sumOf { it.subject.length + it.content.length + 20 }
        return (profileChars + memoryChars) / CHARS_PER_TOKEN_ESTIMATE
    }
}
