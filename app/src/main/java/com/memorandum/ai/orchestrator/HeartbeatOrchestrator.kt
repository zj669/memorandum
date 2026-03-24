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
import com.memorandum.data.remote.llm.LlmClient
import com.memorandum.data.remote.llm.LlmResponse
import com.memorandum.data.repository.MemoryRepository
import com.memorandum.data.repository.NotificationRepository
import com.memorandum.data.repository.TaskRepository
import com.memorandum.scheduler.CooldownManager
import com.memorandum.scheduler.NotificationHelper
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
    private val cooldownManager: CooldownManager,
    private val notificationHelper: NotificationHelper,
    private val retryHelper: RetryHelper,
    private val json: Json,
) {

    companion object {
        private const val TAG = "HeartbeatOrchestrator"
        private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
        private const val DEDUP_WINDOW_MS = 2L * 60 * 60 * 1000
    }

    suspend fun executeHeartbeat(): HeartbeatResult {
        Log.i(TAG, "Heartbeat triggered")

        if (isInQuietHours()) {
            Log.i(TAG, "In quiet hours, skipping heartbeat")
            writeLog(shouldNotify = false, reason = "In quiet hours", taskRef = null, usedMcp = false)
            return HeartbeatResult.Skipped("In quiet hours")
        }

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

        val finalOutput = if (output.shouldUseMcp && output.mcpQueries.isNotEmpty()) {
            Log.i(TAG, "MCP requested, queries=${output.mcpQueries.size}")
            val mcpResult = mcpOrchestrator.executeQueries(output.mcpQueries)
            val mcpSummary = when (mcpResult) {
                is McpExecutionResult.Success -> mcpResult.summary
                is McpExecutionResult.PartialSuccess -> mcpResult.summary
                else -> null
            }

            if (mcpSummary != null) {
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

        val validation = schemaValidator.validateHeartbeatOutput(finalOutput)
        if (!validation.isValid) {
            Log.e(TAG, "Heartbeat validation failed: ${validation.errors}")
            writeLog(shouldNotify = false, reason = "Validation failed: ${validation.errors}", taskRef = null, usedMcp = false)
            return HeartbeatResult.Failed("Invalid AI response: ${validation.errors.joinToString()}")
        }

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

        val taskRef = finalOutput.taskRef
        if (taskRef != null) {
            if (cooldownManager.isInCooldown(taskRef)) {
                Log.i(TAG, "Task $taskRef in cooldown window, skipping heartbeat notification")
                writeLog(
                    shouldNotify = false,
                    reason = "Task in cooldown",
                    taskRef = taskRef,
                    usedMcp = output.shouldUseMcp,
                )
                return HeartbeatResult.Skipped("Task in cooldown")
            }

            if (isDuplicateNotification(taskRef, NotificationType.HEARTBEAT_CHECK)) {
                Log.i(TAG, "Task $taskRef in cooldown/dedup window, skipping notification")
                writeLog(shouldNotify = false, reason = "Duplicate notification", taskRef = taskRef, usedMcp = false)
                return HeartbeatResult.Skipped("Duplicate notification")
            }

            val notifType = parseNotificationType(notif.type)
            if (notifType != null && isDuplicateNotification(taskRef, notifType)) {
                Log.i(TAG, "Duplicate notification for task=$taskRef, type=$notifType")
                writeLog(shouldNotify = false, reason = "Duplicate notification", taskRef = taskRef, usedMcp = false)
                return HeartbeatResult.Skipped("Duplicate notification")
            }
        }

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
            deliveryFailedAt = null,
        )

        notificationRepository.save(entity).getOrElse { error ->
            writeLog(shouldNotify = false, reason = "Save notification failed: ${error.message}", taskRef = taskRef, usedMcp = false)
            return HeartbeatResult.Failed("Save notification failed: ${error.message}")
        }

        val delivered = notificationHelper.send(
            id = notificationId.hashCode(),
            notificationRecordId = notificationId,
            title = notif.title,
            body = notif.body,
            channelId = notificationHelper.channelForType(notificationType),
            taskRef = taskRef,
            actionType = actionType,
        )
        if (!delivered) {
            notificationRepository.markDeliveryFailed(notificationId).getOrElse { error ->
                Log.e(
                    TAG,
                    "Failed to persist heartbeat delivery failure: notificationId=$notificationId, error=${error.message}",
                )
            }
            Log.w(
                TAG,
                "Heartbeat notification delivery failed: notificationId=$notificationId, taskRef=$taskRef, type=$notificationType",
            )
            writeLog(
                shouldNotify = false,
                reason = "Notification permission missing or notifications disabled",
                taskRef = taskRef,
                usedMcp = output.shouldUseMcp,
                notificationType = notificationType,
            )
            return HeartbeatResult.Failed("Notification permission missing or notifications disabled")
        }

        if (taskRef != null) {
            cooldownManager.setCooldown(taskRef, finalOutput.cooldownHours)
        }

        writeLog(
            shouldNotify = true,
            reason = finalOutput.reason,
            taskRef = taskRef,
            usedMcp = output.shouldUseMcp,
            notificationType = notificationType,
        )

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
                now.isAfter(start) && now.isBefore(end)
            } else {
                now.isAfter(start) || now.isBefore(end)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse quiet hours: ${e.message}")
            false
        }
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
                ),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write heartbeat log: ${e.message}")
        }
    }

    private fun parseHeartbeatOutput(response: LlmResponse): HeartbeatOutput? {
        return try {
            json.decodeFromString<HeartbeatOutput>(stripMarkdownCodeBlock(response.content))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse HeartbeatOutput: ${response.content.take(200)}")
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
