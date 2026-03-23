package com.memorandum.domain.usecase.memory

import com.memorandum.data.local.room.enums.EntryType
import com.memorandum.data.local.room.enums.NotificationType

data class EvidenceSummary(
    val completedTasks: List<CompletedTaskEvidence>,
    val acceptedPlans: List<PlanAcceptanceEvidence>,
    val rejectedPlans: List<PlanRejectionEvidence>,
    val notificationResponses: List<NotificationResponseEvidence>,
    val scheduleAdherence: List<ScheduleAdherenceRecord>,
    val statusChanges: List<StatusChangeEvidence>,
) {
    fun toPromptText(): String {
        val sb = StringBuilder()

        if (completedTasks.isNotEmpty()) {
            sb.appendLine("## 已完成任务")
            completedTasks.forEach { t ->
                sb.appendLine("- ${t.title} (${t.type}, ${t.totalDurationDays}天, ${t.stepsCompleted}/${t.stepsTotal}步)")
            }
        }

        if (acceptedPlans.isNotEmpty()) {
            sb.appendLine("## 接受的计划")
            acceptedPlans.forEach { p ->
                sb.appendLine("- ${p.taskTitle}")
            }
        }

        if (rejectedPlans.isNotEmpty()) {
            sb.appendLine("## 拒绝的计划")
            rejectedPlans.forEach { p ->
                sb.appendLine("- ${p.taskTitle}: ${p.reason}")
            }
        }

        if (notificationResponses.isNotEmpty()) {
            sb.appendLine("## 通知响应")
            notificationResponses.forEach { n ->
                sb.appendLine("- ${n.notificationType}: ${n.action} (${n.taskTitle ?: "无任务"})")
            }
        }

        if (scheduleAdherence.isNotEmpty()) {
            sb.appendLine("## 排程遵守")
            scheduleAdherence.forEach { s ->
                val status = if (s.wasFollowed) "已遵守" else "未遵守"
                sb.appendLine("- ${s.blockDate} ${s.startTime}-${s.endTime} ${s.taskTitle}: $status")
            }
        }

        if (statusChanges.isNotEmpty()) {
            sb.appendLine("## 状态变更")
            statusChanges.forEach { c ->
                sb.appendLine("- ${c.taskTitle}: ${c.fromStatus} -> ${c.toStatus}")
            }
        }

        return sb.toString().take(2000)
    }
}

data class CompletedTaskEvidence(
    val taskId: String,
    val title: String,
    val type: EntryType,
    val totalDurationDays: Int,
    val stepsCompleted: Int,
    val stepsTotal: Int,
    val completedAt: Long,
)

data class PlanAcceptanceEvidence(
    val taskId: String,
    val taskTitle: String,
    val acceptedAt: Long,
)

data class PlanRejectionEvidence(
    val taskId: String,
    val taskTitle: String,
    val reason: String,
    val rejectedAt: Long,
)

data class NotificationResponseEvidence(
    val notificationType: NotificationType,
    val action: String,
    val taskTitle: String?,
    val responseDelayMinutes: Long?,
)

data class ScheduleAdherenceRecord(
    val blockDate: String,
    val startTime: String,
    val endTime: String,
    val taskTitle: String,
    val wasFollowed: Boolean,
    val actualProgressAt: Long?,
)

data class StatusChangeEvidence(
    val taskId: String,
    val taskTitle: String,
    val fromStatus: String,
    val toStatus: String,
    val changedAt: Long,
)
