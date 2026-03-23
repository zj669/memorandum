package com.memorandum.ai.prompt

import com.memorandum.data.local.room.entity.HeartbeatLogEntity
import com.memorandum.data.local.room.entity.MemoryEntity
import com.memorandum.data.local.room.entity.NotificationEntity
import com.memorandum.data.local.room.entity.TaskEntity
import com.memorandum.data.remote.llm.LlmRequest
import com.memorandum.data.remote.llm.ResponseFormat

object HeartbeatPrompt {

    private val promptBuilder = PromptBuilder()

    fun build(
        activeTasks: List<TaskEntity>,
        recentNotifications: List<NotificationEntity>,
        userProfileJson: String?,
        memories: List<MemoryEntity>,
        recentHeartbeats: List<HeartbeatLogEntity>,
    ): LlmRequest {
        val systemPrompt = buildString {
            append(promptBuilder.buildSystemBase())
            append(promptBuilder.appendUserProfile(userProfileJson))
            append(promptBuilder.appendMemories(memories))
            append(promptBuilder.appendTimeContext())

            appendLine()
            appendLine("心跳巡检指令：")
            appendLine("评估当前活跃任务的状态，决定是否需要发送通知提醒用户。")
            appendLine()
            appendLine("规则：")
            appendLine("- 避免重复通知（参考最近通知历史）")
            appendLine("- 通知理由必须具体，不要泛泛而谈")
            appendLine("- cooldown_hours 建议值：普通提醒 4h，紧急提醒 1h")
            appendLine("- should_notify=false 时 notification 必须为 null")
            appendLine("- should_notify=true 时 notification 不能为 null")
            appendLine("- notification.type 可选值: PLAN_READY, TIME_TO_START, DEADLINE_RISK, PREP_NEEDED, STALE_TASK, HEARTBEAT_CHECK")
            appendLine("- notification.action_type 可选值: OPEN_TASK, OPEN_TODAY, SNOOZE, MARK_DONE")
            appendLine()
            appendLine("如果你认为需要联网搜索补充信息，设置 should_use_mcp=true 并在 mcp_queries 中列出最多3条搜索查询。")
            appendLine()
            appendLine("输出 JSON 格式：")
            appendLine("""
{
  "should_use_mcp": false,
  "mcp_queries": [],
  "should_notify": false,
  "notification": null,
  "reason": "不需要通知的原因",
  "task_ref": null,
  "cooldown_hours": 4
}
            """.trimIndent())
            appendLine()
            appendLine("或通知时：")
            appendLine("""
{
  "should_use_mcp": false,
  "mcp_queries": [],
  "should_notify": true,
  "notification": {"type": "DEADLINE_RISK", "action_type": "OPEN_TASK", "title": "标题", "body": "内容"},
  "reason": "具体原因",
  "task_ref": "task_id",
  "cooldown_hours": 4
}
            """.trimIndent())
        }

        val userMessage = buildString {
            appendLine("当前活跃任务：")
            if (activeTasks.isEmpty()) {
                appendLine("（无活跃任务）")
            } else {
                for (task in activeTasks) {
                    appendLine("- [${task.status.name}] ${task.title} (id=${task.id}, 风险=${task.riskLevel})")
                    task.nextAction?.let { appendLine("  下一步: $it") }
                    task.notificationCooldownUntil?.let { appendLine("  冷却至: $it") }
                }
            }

            if (recentNotifications.isNotEmpty()) {
                appendLine()
                appendLine("最近通知历史：")
                for (notif in recentNotifications.take(10)) {
                    appendLine("- [${notif.type.name}] ${notif.title} (task=${notif.taskRef}, time=${notif.createdAt})")
                }
            }

            if (recentHeartbeats.isNotEmpty()) {
                appendLine()
                appendLine("最近心跳记录：")
                for (hb in recentHeartbeats.take(5)) {
                    appendLine("- notify=${hb.shouldNotify}, reason=${hb.reason} (time=${hb.checkedAt})")
                }
            }
        }

        return LlmRequest(
            systemPrompt = systemPrompt,
            userMessage = userMessage,
            responseFormat = ResponseFormat.JSON,
        )
    }
}
