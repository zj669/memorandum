package com.memorandum.ai.orchestrator

import android.util.Log
import com.memorandum.ai.prompt.ClarifierPrompt
import com.memorandum.ai.prompt.PlannerPrompt
import com.memorandum.ai.schema.ClarifierOutput
import com.memorandum.ai.schema.PlannerOutput
import com.memorandum.ai.schema.SchemaValidator
import com.memorandum.data.local.room.dao.UserProfileDao
import com.memorandum.data.local.room.entity.EntryEntity
import com.memorandum.data.local.room.entity.MemoryEntity
import com.memorandum.data.local.room.entity.PlanStepEntity
import com.memorandum.data.local.room.entity.PrepItemEntity
import com.memorandum.data.local.room.entity.ScheduleBlockEntity
import com.memorandum.data.local.room.entity.TaskEntity
import com.memorandum.data.local.room.enums.PlanningState
import com.memorandum.data.local.room.enums.PrepStatus
import com.memorandum.data.local.room.enums.ScheduleSource
import com.memorandum.data.local.room.enums.StepStatus
import com.memorandum.data.local.room.enums.TaskStatus
import com.memorandum.data.remote.llm.ImageInput
import com.memorandum.data.remote.llm.ImageProcessor
import com.memorandum.data.remote.llm.LlmClient
import com.memorandum.data.remote.llm.LlmResponse
import com.memorandum.data.repository.EntryRepository
import com.memorandum.data.repository.MemoryRepository
import com.memorandum.data.repository.TaskRepository
import com.memorandum.scheduler.AlarmScheduler
import com.memorandum.util.RetryHelper
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PlanningResult {
    data class NeedsClarification(val question: String, val reason: String) : PlanningResult
    data class Success(val taskId: String) : PlanningResult
    data class Failed(val error: String) : PlanningResult
}

data class PlanningContext(
    val userProfileJson: String?,
    val memories: List<MemoryEntity>,
    val similarTasks: List<TaskEntity>,
    val images: List<ImageInput>,
)

@Singleton
class PlanningOrchestrator @Inject constructor(
    private val llmClient: LlmClient,
    private val entryRepository: EntryRepository,
    private val taskRepository: TaskRepository,
    private val memoryRepository: MemoryRepository,
    private val userProfileDao: UserProfileDao,
    private val mcpOrchestrator: McpOrchestrator,
    private val imageProcessor: ImageProcessor,
    private val schemaValidator: SchemaValidator,
    private val retryHelper: RetryHelper,
    private val json: Json,
    private val alarmScheduler: AlarmScheduler,
) {

    companion object {
        private const val TAG = "PlanningOrchestrator"
    }

    suspend fun startPlanning(entryId: String): PlanningResult {
        Log.i(TAG, "Planning started for entry: $entryId")

        val entry = getEntry(entryId) ?: return PlanningResult.Failed("Entry not found: $entryId")

        val context = buildPlanningContext(entry)

        // Step 1: Check if clarification is needed (only if not already used)
        if (!entry.clarificationUsed) {
            entryRepository.updatePlanningState(entryId, PlanningState.CLARIFYING)
            val clarifierResult = executeClarifier(entry, context)
            if (clarifierResult != null && clarifierResult.ask && !clarifierResult.question.isNullOrBlank()) {
                Log.i(TAG, "Clarification needed for entry: $entryId")
                entryRepository.saveClarification(entryId, clarifierResult.question, null)
                entryRepository.updatePlanningState(entryId, PlanningState.ASKING)
                return PlanningResult.NeedsClarification(
                    question = clarifierResult.question,
                    reason = clarifierResult.reason.orEmpty(),
                )
            }
        }

        // Step 2: Execute planner
        entryRepository.updatePlanningState(entryId, PlanningState.PLANNING)
        return executePlannerFlow(entry, context, null)
    }

    suspend fun continueAfterClarification(entryId: String, answer: String?): PlanningResult {
        Log.i(TAG, "Continuing after clarification for entry: $entryId")

        val entry = getEntry(entryId) ?: return PlanningResult.Failed("Entry not found: $entryId")

        entryRepository.updatePlanningState(entryId, PlanningState.PLANNING)

        val context = buildPlanningContext(entry)
        return executePlannerFlow(entry, context, answer)
    }

    suspend fun replan(taskId: String): PlanningResult {
        Log.i(TAG, "Replanning for task: $taskId")

        // Find the entry associated with this task - we need to look it up
        // For replan, we create a new planning cycle on the same entry
        return PlanningResult.Failed("Replan requires entry lookup from task - not yet wired")
    }

    private suspend fun executePlannerFlow(
        entry: EntryEntity,
        context: PlanningContext,
        clarificationAnswer: String?,
    ): PlanningResult {
        // First round
        val plannerOutput = executePlanner(
            entry = entry,
            context = context,
            clarificationAnswer = clarificationAnswer ?: entry.clarificationAnswer,
            mcpResults = null,
        ) ?: return failPlanning(entry.id, "AI call failed")

        // Check if MCP is needed
        if (plannerOutput.shouldUseMcp && plannerOutput.mcpQueries.isNotEmpty()) {
            Log.i(TAG, "MCP requested for entry: ${entry.id}, queries=${plannerOutput.mcpQueries.size}")
            entryRepository.updatePlanningState(entry.id, PlanningState.ENRICHING_MCP)
            val mcpResult = mcpOrchestrator.executeQueries(plannerOutput.mcpQueries)
            val mcpSummary = when (mcpResult) {
                is McpExecutionResult.Success -> mcpResult.summary
                is McpExecutionResult.PartialSuccess -> mcpResult.summary
                else -> null
            }

            // Second round with MCP results
            entryRepository.updatePlanningState(entry.id, PlanningState.PLANNING)
            val finalOutput = executePlanner(
                entry = entry,
                context = context,
                clarificationAnswer = clarificationAnswer ?: entry.clarificationAnswer,
                mcpResults = mcpSummary,
            ) ?: return failPlanning(entry.id, "AI call failed on second round")

            return processPlannerOutput(entry.id, finalOutput)
        }

        return processPlannerOutput(entry.id, plannerOutput)
    }

    private suspend fun processPlannerOutput(entryId: String, output: PlannerOutput): PlanningResult {
        // Validate
        val validation = schemaValidator.validatePlannerOutput(output)
        if (!validation.isValid) {
            Log.e(TAG, "Schema validation failed: entryId=$entryId, errors=${validation.errors}")
            return failPlanning(entryId, "Invalid AI response: ${validation.errors.joinToString()}")
        }

        if (output.needsClarification) {
            return PlanningResult.Failed("Unexpected clarification request in planner output")
        }

        // Save to database
        return try {
            entryRepository.updatePlanningState(entryId, PlanningState.SAVING)
            val taskId = savePlanToDatabase(entryId, output)
            entryRepository.updatePlanningState(entryId, PlanningState.READY)
            Log.i(TAG, "Planning completed: entryId=$entryId, taskId=$taskId, steps=${output.steps.size}")
            PlanningResult.Success(taskId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save plan: entryId=$entryId, error=${e.message}")
            failPlanning(entryId, "Failed to save plan: ${e.message}")
        }
    }

    private suspend fun executePlanner(
        entry: EntryEntity,
        context: PlanningContext,
        clarificationAnswer: String?,
        mcpResults: String?,
    ): PlannerOutput? {
        val request = PlannerPrompt.build(
            entry = entry,
            userProfileJson = context.userProfileJson,
            memories = context.memories,
            similarTasks = context.similarTasks,
            clarificationAnswer = clarificationAnswer,
            mcpResults = mcpResults,
            clarificationUsed = entry.clarificationUsed,
        )

        val result = retryHelper.retryWithBackoff(maxRetries = 2) {
            llmClient.chat(request).getOrThrow()
        }

        return result.fold(
            onSuccess = { response -> parsePlannerOutput(response) },
            onFailure = { e ->
                Log.e(TAG, "Planner LLM call failed: ${e.message}")
                null
            },
        )
    }

    private suspend fun executeClarifier(
        entry: EntryEntity,
        context: PlanningContext,
    ): ClarifierOutput? {
        val request = ClarifierPrompt.build(
            entry = entry,
            userProfileJson = context.userProfileJson,
            memories = context.memories,
        )

        val result = retryHelper.retryWithBackoff(maxRetries = 1) {
            llmClient.chat(request).getOrThrow()
        }

        return result.fold(
            onSuccess = { response -> parseClarifierOutput(response) },
            onFailure = { e ->
                Log.w(TAG, "Clarifier LLM call failed, skipping: ${e.message}")
                null
            },
        )
    }

    private suspend fun savePlanToDatabase(entryId: String, output: PlannerOutput): String {
        val now = System.currentTimeMillis()
        val taskId = UUID.randomUUID().toString()

        val task = TaskEntity(
            id = taskId,
            entryId = entryId,
            title = output.taskTitle.orEmpty(),
            status = TaskStatus.PLANNED,
            summary = output.summary.orEmpty(),
            goalId = null,
            nextAction = output.steps.firstOrNull()?.title,
            riskLevel = if (output.risks.isNotEmpty()) output.risks.size.coerceAtMost(5) else 0,
            planVersion = 1,
            planReady = true,
            lastHeartbeatAt = null,
            lastProgressAt = now,
            notificationCooldownUntil = null,
        )

        val steps = output.steps.map { step ->
            PlanStepEntity(
                id = UUID.randomUUID().toString(),
                taskId = taskId,
                stepIndex = step.index,
                title = step.title,
                description = step.description,
                status = StepStatus.TODO,
                needsMcp = step.needsMcp,
                createdAt = now,
                updatedAt = now,
            )
        }

        val blocks = output.scheduleBlocks.map { block ->
            ScheduleBlockEntity(
                id = UUID.randomUUID().toString(),
                taskId = taskId,
                blockDate = block.date,
                startTime = block.start,
                endTime = block.end,
                reason = block.reason,
                source = ScheduleSource.PLANNER,
                accepted = false,
                createdAt = now,
            )
        }

        val preps = output.prepItems.map { item ->
            PrepItemEntity(
                id = UUID.randomUUID().toString(),
                taskId = taskId,
                content = item,
                status = PrepStatus.TODO,
                sourceMemoryId = null,
            )
        }

        taskRepository.saveFromPlan(task, steps, blocks, preps).getOrThrow()

        // Schedule alarms for future schedule blocks
        for (block in blocks) {
            try {
                val triggerAtMillis = parseBlockTimeToMillis(block.blockDate, block.startTime)
                if (triggerAtMillis != null && triggerAtMillis > System.currentTimeMillis()) {
                    alarmScheduler.scheduleTaskAlarm(
                        taskId = block.id,  // Use block ID so each block gets its own alarm
                        taskTitle = task.title,
                        triggerAtMillis = triggerAtMillis,
                        notificationTitle = "即将开始: ${task.title}",
                        notificationBody = block.reason,
                    )
                    Log.i(TAG, "Alarm scheduled for block: blockId=${block.id}, triggerAt=$triggerAtMillis")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to schedule alarm for block ${block.id}: ${e.message}")
            }
        }

        return taskId
    }

    private suspend fun buildPlanningContext(entry: EntryEntity): PlanningContext {
        val userProfile = userProfileDao.get()
        val memories = memoryRepository.getForPlanning().getOrElse { emptyList() }
        val similarTasks = findSimilarTasks(entry)
        val images = imageProcessor.processOrSkip(
            uris = entry.imageUrisJson,
            supportsImage = true, // Will be determined by config in real usage
        )

        return PlanningContext(
            userProfileJson = userProfile?.profileJson,
            memories = memories,
            similarTasks = similarTasks,
            images = images,
        )
    }

    private suspend fun findSimilarTasks(entry: EntryEntity): List<TaskEntity> {
        // Simple approach: return recent tasks as "similar" context
        // A more sophisticated approach would use text similarity
        return emptyList()
    }

    private fun parseBlockTimeToMillis(blockDate: String, startTime: String): Long? {
        return try {
            val dateTime = java.time.LocalDateTime.parse(
                "${blockDate}T${startTime}",
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
            )
            dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse block time: date=$blockDate, time=$startTime, error=${e.message}")
            null
        }
    }

    private suspend fun getEntry(entryId: String): EntryEntity? {
        return try {
            entryRepository.observeById(entryId).firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get entry: $entryId, error=${e.message}")
            null
        }
    }

    private suspend fun failPlanning(entryId: String, error: String): PlanningResult.Failed {
        entryRepository.updatePlanningState(entryId, PlanningState.FAILED)
        return PlanningResult.Failed(error)
    }

    private fun stripMarkdownCodeBlock(text: String): String {
        val trimmed = text.trim()
        // Find the first ``` line and strip everything before/after
        val lines = trimmed.lines()
        val firstCodeFence = lines.indexOfFirst { it.trim().startsWith("```") }
        if (firstCodeFence >= 0) {
            val lastCodeFence = lines.indexOfLast { it.trim() == "```" && lines.indexOf(it) != firstCodeFence }
            val start = firstCodeFence + 1
            val end = if (lastCodeFence > firstCodeFence) lastCodeFence else lines.size
            return lines.subList(start, end).joinToString("\n").trim()
        }
        return trimmed
    }

    private fun parsePlannerOutput(response: LlmResponse): PlannerOutput? {
        return try {
            val cleaned = stripMarkdownCodeBlock(response.content)
            Log.d(TAG, "Parsing PlannerOutput: ${cleaned.take(100)}")
            json.decodeFromString<PlannerOutput>(cleaned)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse PlannerOutput: ${e.message}, raw=${response.content.take(300)}")
            null
        }
    }

    private fun parseClarifierOutput(response: LlmResponse): ClarifierOutput? {
        return try {
            json.decodeFromString<ClarifierOutput>(stripMarkdownCodeBlock(response.content))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ClarifierOutput: ${response.content.take(200)}")
            null
        }
    }
}
