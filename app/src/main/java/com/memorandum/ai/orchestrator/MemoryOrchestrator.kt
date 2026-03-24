package com.memorandum.ai.orchestrator

import android.util.Log
import com.memorandum.ai.prompt.MemoryPrompt
import com.memorandum.ai.schema.MemoryOutput
import com.memorandum.ai.schema.SchemaValidator
import com.memorandum.data.local.room.dao.TaskEventDao
import com.memorandum.data.local.room.dao.UserProfileDao
import com.memorandum.data.local.room.entity.MemoryEntity
import com.memorandum.data.remote.llm.LlmClient
import com.memorandum.data.remote.llm.LlmResponse
import com.memorandum.data.repository.MemoryRepository
import com.memorandum.domain.usecase.memory.AggregateProfileUseCase
import com.memorandum.util.RetryHelper
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface MemoryUpdateResult {
    data class Updated(val added: Int, val updated: Int, val downgraded: Int) : MemoryUpdateResult
    data object Skipped : MemoryUpdateResult
    data class Failed(val error: String) : MemoryUpdateResult
}

@Singleton
class MemoryOrchestrator @Inject constructor(
    private val llmClient: LlmClient,
    private val memoryRepository: MemoryRepository,
    private val userProfileDao: UserProfileDao,
    private val taskEventDao: TaskEventDao,
    private val schemaValidator: SchemaValidator,
    private val retryHelper: RetryHelper,
    private val aggregateProfileUseCase: AggregateProfileUseCase,
    private val json: Json,
) {

    companion object {
        private const val TAG = "MemoryOrchestrator"
    }

    suspend fun updateMemories(force: Boolean = false): MemoryUpdateResult {
        Log.i(TAG, "Memory update triggered, force=$force")

        val recentEvents = taskEventDao.getRecent(50)
        if (recentEvents.isEmpty()) {
            Log.i(TAG, "No recent events, skipping memory update")
            return MemoryUpdateResult.Skipped
        }

        val existingMemories = memoryRepository.getForPlanning().getOrElse { emptyList() }
        val userProfile = userProfileDao.get()

        val request = MemoryPrompt.build(
            recentEvents = recentEvents,
            existingMemories = existingMemories,
            userProfileJson = userProfile?.profileJson,
        )

        val llmResult = retryHelper.retryWithBackoff(maxRetries = 1) {
            llmClient.chat(request).getOrThrow()
        }

        val response = llmResult.getOrElse { e ->
            Log.e(TAG, "Memory LLM call failed: ${e.message}")
            return MemoryUpdateResult.Failed("AI call failed: ${e.message}")
        }

        val output = parseMemoryOutput(response)
        if (output == null) {
            return MemoryUpdateResult.Failed("Failed to parse AI response")
        }

        val existingIds = existingMemories.map { it.id }.toSet()
        val validation = schemaValidator.validateMemoryOutput(output, existingIds)
        if (!validation.isValid) {
            Log.e(TAG, "Memory validation failed: ${validation.errors}")
            return MemoryUpdateResult.Failed("Invalid AI response: ${validation.errors.joinToString()}")
        }

        val now = System.currentTimeMillis()
        var added = 0
        var updated = 0
        var downgraded = 0

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
            memoryRepository.upsert(entity).onSuccess { added++ }
        }

        for (update in output.updates) {
            if (update.memoryId.isBlank() || update.memoryId !in existingIds) continue
            val existing = existingMemories.find { it.id == update.memoryId } ?: continue
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
            memoryRepository.upsert(updatedEntity).onSuccess { updated++ }
        }

        for (dg in output.downgrades) {
            if (dg.memoryId.isBlank() || dg.memoryId !in existingIds) continue
            memoryRepository.downgrade(
                id = dg.memoryId,
                newConfidence = dg.newConfidence.coerceIn(0f, 1f),
            ).onSuccess { downgraded++ }
        }

        aggregateProfileUseCase.aggregate()

        Log.i(TAG, "Memory update completed: added=$added, updated=$updated, downgraded=$downgraded")
        return MemoryUpdateResult.Updated(added = added, updated = updated, downgraded = downgraded)
    }

    private fun parseMemoryOutput(response: LlmResponse): MemoryOutput? {
        return try {
            json.decodeFromString<MemoryOutput>(stripMarkdownCodeBlock(response.content))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse MemoryOutput: ${response.content.take(200)}")
            null
        }
    }

    private fun stripMarkdownCodeBlock(text: String): String {
        val trimmed = text.trim()
        val lines = trimmed.lines()
        val firstCodeFence = lines.indexOfFirst { it.trim().startsWith("```") }
        if (firstCodeFence >= 0) {
            val lastCodeFence = lines.indexOfLast { it.trim() == "```" }
            val start = firstCodeFence + 1
            val end = if (lastCodeFence > firstCodeFence) lastCodeFence else lines.size
            return lines.subList(start, end).joinToString("\n").trim()
        }
        return trimmed
    }

    private fun parseMemoryType(type: String): com.memorandum.data.local.room.enums.MemoryType? {
        return try {
            com.memorandum.data.local.room.enums.MemoryType.valueOf(type.uppercase())
        } catch (_: IllegalArgumentException) {
            Log.w(TAG, "Unknown MemoryType from AI: $type, defaulting to TASK_CONTEXT")
            com.memorandum.data.local.room.enums.MemoryType.TASK_CONTEXT
        }
    }
}
