package com.memorandum.ai.prompt

import com.memorandum.data.local.room.entity.EntryEntity
import com.memorandum.data.local.room.entity.MemoryEntity
import com.memorandum.data.local.room.entity.TaskEntity
import com.memorandum.data.remote.llm.LlmRequest
import com.memorandum.data.remote.llm.ResponseFormat

object PlannerPrompt {

    private val promptBuilder = PromptBuilder()

    fun build(
        entry: EntryEntity,
        userProfileJson: String?,
        memories: List<MemoryEntity>,
        similarTasks: List<TaskEntity>,
        clarificationAnswer: String?,
        mcpResults: String?,
        clarificationUsed: Boolean,
    ): LlmRequest {
        val systemPrompt = buildString {
            append(promptBuilder.buildSystemBase())
            append(promptBuilder.appendUserProfile(userProfileJson))
            append(promptBuilder.appendMemories(memories))
            append(promptBuilder.appendTimeContext())

            appendLine()
            appendLine("任务规划指令：")
            appendLine("1. 分析用户输入，拆解为可执行的步骤")
            appendLine("2. 为每个步骤安排合理的时间块")
            appendLine("3. 识别需要提前准备的事项")
            appendLine("4. 评估潜在风险")
            appendLine("5. 生成通知候选项")
            appendLine()
            appendLine("约束条件：")
            appendLine("- schedule_blocks 的日期必须 >= 今天")
            appendLine("- 时间格式 HH:mm，日期格式 yyyy-MM-dd")
            appendLine("- risks 数组可以为空但必须存在")
            appendLine("- steps 至少 1 个")
            if (clarificationUsed) {
                appendLine("- 已经进行过补充提问，不得再次提问（needs_clarification 必须为 false）")
            }
            appendLine()
            appendLine("如果你认为需要联网搜索补充信息，设置 should_use_mcp=true 并在 mcp_queries 中列出最多3条搜索查询。")
            appendLine()
            appendLine("输出 JSON 格式：")
            appendLine("""
{
  "needs_clarification": false,
  "clarification_question": null,
  "clarification_reason": null,
  "should_use_mcp": false,
  "mcp_queries": [],
  "task_title": "任务标题",
  "summary": "任务摘要",
  "steps": [{"index": 1, "title": "步骤标题", "description": "步骤描述", "needs_mcp": false}],
  "schedule_blocks": [{"date": "yyyy-MM-dd", "start": "HH:mm", "end": "HH:mm", "reason": "原因"}],
  "prep_items": ["准备事项"],
  "risks": ["风险项"],
  "notification_candidates": [{"type": "PLAN_READY", "title": "标题", "body": "内容", "action_type": "OPEN_TASK"}]
}
            """.trimIndent())

            if (similarTasks.isNotEmpty()) {
                appendLine()
                appendLine("相似历史任务参考（最多3条）：")
                for (task in similarTasks.take(3)) {
                    appendLine("- ${task.title}: ${task.summary}")
                }
            }
        }

        val userMessage = buildString {
            appendLine("用户输入：")
            appendLine(entry.text)
            if (entry.priority != null) {
                appendLine("优先级: ${entry.priority}")
            }
            if (entry.deadlineAt != null) {
                appendLine("截止时间: ${entry.deadlineAt}")
            }
            if (entry.estimatedMinutes != null) {
                appendLine("预估时长: ${entry.estimatedMinutes}分钟")
            }
            if (!clarificationAnswer.isNullOrBlank()) {
                appendLine()
                appendLine("用户对补充提问的回答：$clarificationAnswer")
            }
            if (!mcpResults.isNullOrBlank()) {
                appendLine()
                appendLine("联网搜索结果：")
                appendLine(mcpResults)
                appendLine()
                appendLine("请基于以上搜索结果完善计划，should_use_mcp 必须为 false。")
            }
        }

        return LlmRequest(
            systemPrompt = systemPrompt,
            userMessage = userMessage,
            responseFormat = ResponseFormat.JSON,
        )
    }
}
