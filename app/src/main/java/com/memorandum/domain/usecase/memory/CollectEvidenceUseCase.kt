package com.memorandum.domain.usecase.memory

import android.util.Log
import com.memorandum.data.local.room.dao.NotificationDao
import com.memorandum.data.local.room.dao.ScheduleBlockDao
import com.memorandum.data.local.room.dao.TaskDao
import com.memorandum.data.local.room.dao.TaskEventDao
import com.memorandum.data.local.room.entity.NotificationEntity
import com.memorandum.data.local.room.entity.ScheduleBlockEntity
import com.memorandum.data.local.room.entity.TaskEntity
import com.memorandum.data.local.room.entity.TaskEventEntity
import com.memorandum.data.local.room.enums.EntryType
import com.memorandum.data.local.room.enums.TaskStatus
import javax.inject.Inject

class CollectEvidenceUseCase @Inject constructor(
    private val taskEventDao: TaskEventDao,
    private val taskDao: TaskDao,
    private val notificationDao: NotificationDao,
    private val scheduleBlockDao: ScheduleBlockDao,
) {

    companion object {
        private const val TAG = "CollectEvidence"
    }

    suspend fun collect(sinceTimestamp: Long): EvidenceSummary {
        val events = taskEventDao.getEventsSince(sinceTimestamp)
        val taskIds = events.map { it.taskId }.distinct()
        val tasks = taskIds.mapNotNull { taskDao.getById(it) }
        val taskMap = tasks.associateBy { it.id }

        Log.d(TAG, "Collecting evidence since=$sinceTimestamp, events=${events.size}, tasks=${tasks.size}")

        return EvidenceSummary(
            completedTasks = extractCompletedTasks(events, taskMap),
            acceptedPlans = extractAcceptedPlans(events, taskMap),
            rejectedPlans = extractRejectedPlans(events, taskMap),
            notificationResponses = collectNotificationResponses(sinceTimestamp),
            scheduleAdherence = analyzeScheduleAdherence(tasks, sinceTimestamp),
            statusChanges = extractStatusChanges(events, taskMap),
        )
    }

    private fun extractCompletedTasks(
        events: List<TaskEventEntity>,
        taskMap: Map<String, TaskEntity>,
    ): List<CompletedTaskEvidence> {
        return events
            .filter { it.eventType == "DONE" }
            .mapNotNull { event ->
                val task = taskMap[event.taskId] ?: return@mapNotNull null
                val durationDays = ((event.createdAt - (task.lastProgressAt ?: event.createdAt))
                    / (24 * 3600_000L)).toInt().coerceAtLeast(0)
                CompletedTaskEvidence(
                    taskId = task.id,
                    title = task.title,
                    type = EntryType.TASK,
                    totalDurationDays = durationDays,
                    stepsCompleted = 0,
                    stepsTotal = 0,
                    completedAt = event.createdAt,
                )
            }
    }

    private fun extractAcceptedPlans(
        events: List<TaskEventEntity>,
        taskMap: Map<String, TaskEntity>,
    ): List<PlanAcceptanceEvidence> {
        return events
            .filter { it.eventType == "ACCEPTED_PLAN" }
            .mapNotNull { event ->
                val task = taskMap[event.taskId] ?: return@mapNotNull null
                PlanAcceptanceEvidence(
                    taskId = task.id,
                    taskTitle = task.title,
                    acceptedAt = event.createdAt,
                )
            }
    }

    private fun extractRejectedPlans(
        events: List<TaskEventEntity>,
        taskMap: Map<String, TaskEntity>,
    ): List<PlanRejectionEvidence> {
        return events
            .filter { it.eventType == "REJECTED_PLAN" }
            .mapNotNull { event ->
                val task = taskMap[event.taskId] ?: return@mapNotNull null
                PlanRejectionEvidence(
                    taskId = task.id,
                    taskTitle = task.title,
                    reason = event.payloadJson ?: "",
                    rejectedAt = event.createdAt,
                )
            }
    }

    private suspend fun collectNotificationResponses(
        sinceTimestamp: Long,
    ): List<NotificationResponseEvidence> {
        val notifications = notificationDao.getNotificationsSince(sinceTimestamp)
        return notifications.mapNotNull { notif ->
            val action = resolveNotificationAction(notif) ?: return@mapNotNull null
            val responseDelay = resolveResponseDelay(notif)
            NotificationResponseEvidence(
                notificationType = notif.type,
                action = action,
                taskTitle = notif.title,
                responseDelayMinutes = responseDelay,
            )
        }
    }

    private fun resolveNotificationAction(notif: NotificationEntity): String? {
        return when {
            notif.clickedAt != null -> "CLICKED"
            notif.dismissedAt != null -> "DISMISSED"
            notif.snoozedUntil != null -> "SNOOZED"
            else -> null
        }
    }

    private fun resolveResponseDelay(notif: NotificationEntity): Long? {
        val respondedAt = notif.clickedAt ?: notif.dismissedAt ?: notif.snoozedUntil
        return respondedAt?.let { (it - notif.createdAt) / 60_000L }
    }

    private suspend fun analyzeScheduleAdherence(
        tasks: List<TaskEntity>,
        since: Long,
    ): List<ScheduleAdherenceRecord> {
        val blocks = scheduleBlockDao.getBlocksSince(since)
        val taskMap = tasks.associateBy { it.id }

        return blocks.mapNotNull { block ->
            val task = taskMap[block.taskId] ?: taskDao.getById(block.taskId)
            val taskTitle = task?.title ?: return@mapNotNull null
            val wasFollowed = task.status in setOf(TaskStatus.DOING, TaskStatus.DONE)
                && task.lastProgressAt != null

            ScheduleAdherenceRecord(
                blockDate = block.blockDate,
                startTime = block.startTime,
                endTime = block.endTime,
                taskTitle = taskTitle,
                wasFollowed = wasFollowed,
                actualProgressAt = task.lastProgressAt,
            )
        }
    }

    private fun extractStatusChanges(
        events: List<TaskEventEntity>,
        taskMap: Map<String, TaskEntity>,
    ): List<StatusChangeEvidence> {
        return events
            .filter { it.eventType == "STATUS_CHANGE" }
            .mapNotNull { event ->
                val task = taskMap[event.taskId] ?: return@mapNotNull null
                val payload = event.payloadJson ?: return@mapNotNull null
                val parts = payload.split("->").map { it.trim() }
                if (parts.size != 2) return@mapNotNull null
                StatusChangeEvidence(
                    taskId = task.id,
                    taskTitle = task.title,
                    fromStatus = parts[0],
                    toStatus = parts[1],
                    changedAt = event.createdAt,
                )
            }
    }
}
