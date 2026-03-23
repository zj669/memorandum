package com.memorandum.ai.orchestrator

import android.util.Log
import com.memorandum.ai.prompt.HeartbeatPrompt
import com.memorandum.ai.schema.HeartbeatOutput
import com.memorandum.ai.schema.SchemaValidator
import com.memorandum.data.local.datastore.AppPreferencesDataStore
import com.memorandum.data.local.room.dao.HeartbeatLogDao
import com.memorandum.data.local.room.dao.UserProfileDao
import com.memorandum.data.local.room.entity.HeartbeatLogEntity
import com.memorandum.data.local.room.entity.NotificationEntity
import com.memorandum.data.local.room.enums.NotificationActionType
import com.memorandum.data.local.room.enums.NotificationType
import com.memorandum.data.local.room.enums.TaskStatus
import com.memorandum.data.remote.llm.LlmClient
import com.memorandum.data.remote.llm.LlmResponse
import com.memorandum.data.repository.MemoryRepository
import com.memorandum.data.repository.NotificationRepository
import com.memorandum.data.repository.TaskRepository
import com.memorandum.util.RetryHelper
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface HeartbeatResult {
    data class Notified(val notificationId: String) : HeartbeatResult
    data class Skipped(val reason: String) : HeartbeatResult
    data class Failed(val error: String) : HeartbeatResult
}

@Singleton
class HeartbeatOrchestrator @Inject constructor(
    private val llmClient: LlmClient,
    private val taskRepository: TaskRepository,
    private val notificationRepository: NotificationRepository,
    private val memoryRepository: MemoryRepository,
    private val userProfileDao: UserProfileDao,
    private val heartbeatLogDao: HeartbeatLogDao,
    private val mcpOrchestrator: McpOrchestrator,
    private val schemaValidator: SchemaValidator,
    private val appPreferencesDataStore: AppPreferencesDataStore,
    private val retryHelper: RetryHelper,
    private val json: Json,
) {

    companion object {
        private const val TAG = "HeartbeatOrchestrator"
        private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
        private const val DEDUP_WINDOW_MS = 2L * 60 * 60 * 1000 // 2 hours
    }

    suspend fun executeHeartbeat(): HeartbeatResult {
        Log.i(TAG, "Heartbeat triggered")

        // Check quiet hours
        if (isInQuietHours()) {
            Log.i(TAG, "In quiet hours, skipping heartbeat")
            return HeartbeatResult.Skipped("In quiet hours")
        }

        // Gather context
        val activeTasks = taskRepository.observeActiveTasks().first()
        if (activeTasks.isEmpty()) {
            Log.i(TAG, "No active tasks, skipping heartbeat")
            writeLog(shouldNotify = false, reason = "No active tasks", taskRef = null, usedMcp = false)
            return HeartbeatResult.Skipped("No active tasks")
        }

        val recentNotifications = notificationRepository.observeAll().first().take(20)
        val userProfile = userProfileDao.get()
        val memories = memoryRepository.getForPlanning().getOrElse { emptyList() }
        val recentHeartbeats = heartbeatLogDao.getRecent(10)

        // Build prompt and call LLM
        val request = HeartbeatPrompt.build(
            activeTasks = activeTasks,
            recentNotifications = recentNotifications,
            userProfileJson = userProfile?.profileJson,
            memories = memories,
            recentHeartbeats = recentHeartbeats,
        )

        val llmResult = retryHelper.retryWithBackoff(maxRetries = 2) {
            llmClient.chat(request).getOrThrow()
        }

        val response = llmResult.getOrElse { e ->
            Log.e(TAG, "Heartbeat LLM call failed: ${e.message}")
            writeLog(shouldNotify = false, reason = "LLM failed: ${e.message}", taskRef = null, usedMcp = false)
            return HeartbeatResult.Failed("AI call failed: ${e.message}")
        }

        val output = parseHeartbeatOutput(response)
        if (output == null) {
            writeLog(shouldNotify = false, reason = "Parse failed", taskRef = null, usedMcp = false)
            return HeartbeatResult.Failed("Failed to parse AI response")
        }

        // Handle MCP round if needed
        val finalOutput = if (output.shouldUseMcp && output.mcpQueries.isNotEmpty()) {
            Log.i(TAG, "MCP requested, queries=${output.mcpQueries.size}")
            val mcpResult = mcpOrchestrator.executeQueries(output.mcpQueries)
            val mcpSummary = when (mcpResult) {
                is McpExecutionResult.Success -> mcpResult.summary
                is McpExecutionResult.PartialSuccess -> mcpResult.summary
                else -> null
            }

            if (mcpSummary != null) {
                // Re-run with MCP results injected into user message
                val secondRequest = request.copy(
                    userMessage = request.userMessage + "\n\n联网搜索结果：\n$mcpSummary\n\n请基于以上信息做出最终决策，should_use_mcp 必须为 false。",
                )
                val secondResult = retryHelper.retryWithBackoff(maxRetries = 1) {
                    llmClient.chat(secondRequest).getOrThrow()
                }
                secondResult.getOrNull()?.let { parseHeartbeatOutput(it) } ?: output
            } else {
                output
            }
        } else {
            output
        }

        // Validate
        val validation = schemaValidator.validateHeartbeatOutput(finalOutput)
        if (!validation.isValid) {
            Log.e(TAG, "Heartbeat validation failed: ${validation.errors}")
            writeLog(shouldNotify = false, reason = "Validation failed: ${validation.errors}", taskRef = null, usedMcp = false)
            return HeartbeatResult.Failed("Invalid AI response: ${validation.errors.joinToString()}")
        }

        // Process decision
        if (!finalOutput.shouldNotify) {
            Log.i(TAG, "Heartbeat decided not to notify: ${finalOutput.reason}")
            writeLog(
                shouldNotify = false,
                reason = finalOutput.reason,
                taskRef = finalOutput.taskRef,
                usedMcp = output.shouldUseMcp,
            )
            return HeartbeatResult.Skipped(finalOutput.reason)
        }

        val notif = finalOutput.notification
            ?: run {
                writeLog(shouldNotify = false, reason = "Notification object missing", taskRef = null, usedMcp = false)
                return HeartbeatResult.Failed("should_notify=true but notification is null")
            }

        // Client-side dedup and cooldown checks
        val taskRef = finalOutput.taskRef
        if (taskRef != null) {
            if (isTaskInCooldown(taskRef)) {
                Log.i(TAG, "Task $taskRef in cooldown, skipping notification")
                writeLog(shouldNotify = false, reason = "Task in cooldown", taskRef = taskRef, usedMcp = false)
                return HeartbeatResult.Skipped("Task in cooldown")
            }

            val notifType = parseNotificationType(notif.type)
            if (notifType != null && isDuplicateNotification(taskRef, notifType)) {
                Log.i(TAG, "Duplicate notification for task=$taskRef, type=$notifType")
                writeLog(shouldNotify = false, reason = "Duplicate notification", taskRef = taskRef, usedMcp = false)
                return HeartbeatResult.Skipped("Duplicate notification")
            }
        }

        // Save notification
        val notificationId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val notificationType = parseNotificationType(notif.type) ?: NotificationType.HEARTBEAT_CHECK
        val actionType = parseActionType(notif.actionType) ?: NotificationActionType.OPEN_TASK

        val entity = NotificationEntity(
            id = notificationId,
            type = notificationType,
            actionType = actionType,
            title = notif.title,
            body = notif.body,
            taskRef = taskRef,
            createdAt = now,
            clickedAt = null,
            dismissedAt = null,
            snoozedUntil = null,
        )

        notificationRepository.save(entity)

        // Cooldown is tracked via the notification dedup window
        // No explicit cooldown update needed since isDuplicateNotification checks recent notifications

        writeLog(
            shouldNotify = true,
            reason = finalOutput.reason,
            taskRef = taskRef,
            usedMcp = output.shouldUseMcp,
            notificationType = notificationType,
        )

        // Update last heartbeat timestamp
        appPreferencesDataStore.updateLastHeartbeat(now)

        Log.i(TAG, "Heartbeat notified: id=$notificationId, type=$notificationType, task=$taskRef")
        return HeartbeatResult.Notified(notificationId)
    }

    private suspend fun isInQuietHours(): Boolean {
        val prefs = appPreferencesDataStore.preferences.first()
        val now = LocalTime.now()
        return try {
            val start = LocalTime.parse(prefs.quietHoursStart, TIME_FMT)
            val end = LocalTime.parse(prefs.quietHoursEnd, TIME_FMT)
            if (start.isBefore(end)) {
                // e.g., 23:00 - 07:00 wraps around midnight
                // This case: start < end means no wrap, e.g., 09:00 - 17:00
                now.isAfter(start) && now.isBefore(end)
            } else {
                // Wraps around midnight: 23:00 - 07:00
                now.isAfter(start) || now.isBefore(end)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse quiet hours: ${e.message}")
            false
        }
    }

    private suspend fun isTaskInCooldown(taskRef: String): Boolean {
        val now = System.currentTimeMillis()
        // Check recent notifications for this task within dedup window
        return notificationRepository.isDuplicate(
            taskRef = taskRef,
            type = NotificationType.HEARTBEAT_CHECK,
            windowMs = DEDUP_WINDOW_MS,
        ).getOrElse { false }
    }

    private suspend fun isDuplicateNotification(taskRef: String, type: NotificationType): Boolean {
        return notificationRepository.isDuplicate(
            taskRef = taskRef,
            type = type,
            windowMs = DEDUP_WINDOW_MS,
        ).getOrElse { false }
    }

    private suspend fun writeLog(
        shouldNotify: Boolean,
        reason: String,
        taskRef: String?,
        usedMcp: Boolean,
        notificationType: NotificationType? = null,
        mcpSummary: String? = null,
    ) {
        try {
            heartbeatLogDao.insert(
                HeartbeatLogEntity(
                    id = UUID.randomUUID().toString(),
                    checkedAt = System.currentTimeMillis(),
                    shouldNotify = shouldNotify,
                    notificationType = notificationType,
                    reason = reason,
                    taskRef = taskRef,
                    usedMcp = usedMcp,
                    mcpSummary = mcpSummary,
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write heartbeat log: ${e.message}")
        }
    }

    private fun parseHeartbeatOutput(response: LlmResponse): HeartbeatOutput? {
        return try {
            json.decodeFromString<HeartbeatOutput>(response.content)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse HeartbeatOutput: ${response.content.take(200)}")
            null
        }
    }

    private fun parseNotificationType(type: String): NotificationType? {
        return try {
            NotificationType.valueOf(type.uppercase())
        } catch (_: IllegalArgumentException) {
            Log.w(TAG, "Unknown NotificationType from AI: $type")
            null
        }
    }

    private fun parseActionType(type: String): NotificationActionType? {
        return try {
            NotificationActionType.valueOf(type.uppercase())
        } catch (_: IllegalArgumentException) {
            Log.w(TAG, "Unknown NotificationActionType from AI: $type")
            null
        }
    }
}
