package com.memorandum.ai.orchestrator

import android.util.Log
import com.memorandum.ai.prompt.MemoryPrompt
import com.memorandum.ai.schema.MemoryOutput
import com.memorandum.ai.schema.SchemaValidator
import com.memorandum.ai.schema.StructuredOutputTools
import com.memorandum.data.local.room.dao.TaskEventDao
import com.memorandum.data.local.room.dao.UserProfileDao
import com.memorandum.data.local.room.entity.MemoryEntity
import com.memorandum.data.local.room.entity.UserProfileEntity
import com.memorandum.data.local.room.enums.MemoryType
import com.memorandum.data.remote.llm.LlmClient
import com.memorandum.data.remote.llm.LlmResponse
import com.memorandum.data.remote.llm.LlmToolChoice
import com.memorandum.data.repository.MemoryRepository
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
    private val json: Json,
) {

    companion object {
        private const val TAG = "MemoryOrchestrator"
        private const val MIN_EVENTS_FOR_TRIGGER = 5
    }

    suspend fun updateMemories(force: Boolean = false): MemoryUpdateResult {
        Log.i(TAG, "Memory update triggered: force=$force")

        // 1. Check if we should trigger
        if (!force && !shouldTrigger()) {
            Log.i(TAG, "Not enough events to trigger memory update")
            return MemoryUpdateResult.Skipped
        }

        // 2. Collect evidence
        val recentEvents = taskEventDao.getRecent(50)
        if (recentEvents.isEmpty()) {
            return MemoryUpdateResult.Skipped
        }

        // 3. Read existing memories
        val existingMemories = memoryRepository.getForPlanning().getOrElse { emptyList() }
        val userProfile = userProfileDao.get()

        // 4. Call AI
        val request = MemoryPrompt.build(
            recentEvents = recentEvents,
            existingMemories = existingMemories,
            userProfileJson = userProfile?.profileJson,
        )

        val supportsTools = llmClient.getCapabilities().getOrNull()?.supportsTools == true
        if (!supportsTools) {
            Log.w(TAG, "Memory provider lacks tool support, falling back to text parsing")
        }
        val chatRequest = if (supportsTools) {
            request
        } else {
            request.copy(tools = emptyList(), toolChoice = LlmToolChoice.None)
        }

        val llmResult = retryHelper.retryWithBackoff(maxRetries = 1) {
            llmClient.chat(chatRequest).getOrThrow()
        }

        val response = llmResult.getOrElse { e ->
            Log.e(TAG, "Memory LLM call failed: ${e.message}")
            return MemoryUpdateResult.Failed("AI call failed: ${e.message}")
        }

        val output = parseMemoryOutput(response, supportsTools)
        if (output == null) {
            Log.e(TAG, "Memory output parsing failed across tool/text paths")
            return MemoryUpdateResult.Failed("Failed to parse AI response")
        }

        // 5. Validate
        val existingIds = existingMemories.map { it.id }.toSet()
        val validation = schemaValidator.validateMemoryOutput(output, existingIds)
        if (!validation.isValid) {
            Log.e(TAG, "Memory validation failed: ${validation.errors}")
            return MemoryUpdateResult.Failed("Invalid AI response: ${validation.errors.joinToString()}")
        }

        // 6. Apply changes
        val now = System.currentTimeMillis()
        var added = 0
        var updated = 0
        var downgraded = 0

        // New memories
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

        // Updates
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

        // Downgrades
        for (dg in output.downgrades) {
            if (dg.memoryId.isBlank() || dg.memoryId !in existingIds) continue
            memoryRepository.downgrade(
                id = dg.memoryId,
                newConfidence = dg.newConfidence.coerceIn(0f, 1f),
            ).onSuccess { downgraded++ }
        }

        // 7. Aggregate user profile
        val allMemories = memoryRepository.getForPlanning().getOrElse { emptyList() }
        aggregateUserProfile(allMemories)

        Log.i(TAG, "Memory update completed: added=$added, updated=$updated, downgraded=$downgraded")
        return MemoryUpdateResult.Updated(added = added, updated = updated, downgraded = downgraded)
    }

    private suspend fun shouldTrigger(): Boolean {
        val recentEvents = taskEventDao.getRecent(MIN_EVENTS_FOR_TRIGGER)
        return recentEvents.size >= MIN_EVENTS_FOR_TRIGGER
    }

    private suspend fun aggregateUserProfile(memories: List<MemoryEntity>) {
        if (memories.isEmpty()) return

        try {
            val profileJson = buildString {
                appendLine("{")
                val preferences = memories.filter { it.type == MemoryType.PREFERENCE && it.confidence >= 0.5f }
                val patterns = memories.filter { it.type == MemoryType.PATTERN && it.confidence >= 0.5f }
                val goals = memories.filter { it.type == MemoryType.LONG_TERM_GOAL && it.confidence >= 0.5f }

                if (preferences.isNotEmpty()) {
                    appendLine("  \"preferences\": [")
                    appendLine(preferences.joinToString(",\n") { "    \"${it.subject}: ${it.content}\"" })
                    appendLine("  ],")
                }
                if (patterns.isNotEmpty()) {
                    appendLine("  \"patterns\": [")
                    appendLine(patterns.joinToString(",\n") { "    \"${it.subject}: ${it.content}\"" })
                    appendLine("  ],")
                }
                if (goals.isNotEmpty()) {
                    appendLine("  \"goals\": [")
                    appendLine(goals.joinToString(",\n") { "    \"${it.subject}: ${it.content}\"" })
                    appendLine("  ]")
                }
                appendLine("}")
            }

            val now = System.currentTimeMillis()
            val existing = userProfileDao.get()
            val entity = UserProfileEntity(
                id = "default",
                profileJson = profileJson,
                version = (existing?.version ?: 0) + 1,
                updatedAt = now,
            )
            userProfileDao.upsert(entity)
            Log.d(TAG, "User profile aggregated, version=${entity.version}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to aggregate user profile: ${e.message}")
        }
    }

    private fun parseMemoryOutput(response: LlmResponse, preferTools: Boolean): MemoryOutput? {
        if (preferTools) {
            response.toolCalls.firstOrNull { it.name == StructuredOutputTools.memory.name }?.arguments?.let { toolArgs ->
                return try {
                    json.decodeFromString<MemoryOutput>(toolArgs.toString())
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decode memory tool args: ${e.message}, raw=${toolArgs.toString().take(300)}")
                    null
                }
            }
            Log.w(TAG, "Memory tool call missing, falling back to text response parsing")
        }

        val cleaned = extractJsonObject(response.content)
        return try {
            json.decodeFromString<MemoryOutput>(cleaned)
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Failed to parse MemoryOutput fallback: error=${e.message}, raw=${response.content.take(300)}, cleaned=${cleaned.take(300)}",
            )
            null
        }
    }

    private fun extractJsonObject(text: String): String {
        val stripped = stripMarkdownCodeBlock(text)
        val start = stripped.indexOfFirst { it == '{' }
        if (start < 0) return stripped.trim()

        var depth = 0
        var inString = false
        var escaping = false
        for (index in start until stripped.length) {
            val char = stripped[index]
            if (escaping) {
                escaping = false
                continue
            }
            when (char) {
                '\\' -> if (inString) escaping = true
                '"' -> inString = !inString
                '{' -> if (!inString) depth++
                '}' -> if (!inString) {
                    depth--
                    if (depth == 0) {
                        return stripped.substring(start, index + 1).trim()
                    }
                }
            }
        }

        return stripped.substring(start).trim()
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

    private fun parseMemoryType(type: String): MemoryType? {
        return try {
            MemoryType.valueOf(type.uppercase())
        } catch (_: IllegalArgumentException) {
            Log.w(TAG, "Unknown MemoryType from AI: $type, defaulting to TASK_CONTEXT")
            MemoryType.TASK_CONTEXT
        }
    }
}
