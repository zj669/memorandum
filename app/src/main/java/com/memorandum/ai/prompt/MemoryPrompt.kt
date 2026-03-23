package com.memorandum.ai.prompt

import com.memorandum.data.local.room.entity.MemoryEntity
import com.memorandum.data.local.room.entity.TaskEventEntity
import com.memorandum.data.remote.llm.LlmRequest
import com.memorandum.data.remote.llm.ResponseFormat

object MemoryPrompt {

    private val promptBuilder = PromptBuilder()

    fun build(
        recentEvents: List<TaskEventEntity>,
        existingMemories: List<MemoryEntity>,
        userProfileJson: String?,
    ): LlmRequest {
        val systemPrompt = buildString {
            append(promptBuilder.buildSystemBase())
            append(promptBuilder.appendUserProfile(userProfileJson))
            append(promptBuilder.appendTimeContext())

            appendLine()
            appendLine("记忆沉淀指令：")
            appendLine("从用户的行为证据中提炼稳定的偏好和模式。")
            appendLine()
            appendLine("规则：")
            appendLine("- 新增记忆需要足够的行为证据支撑")
            appendLine("- 可以更新已有记忆的置信度和内容")
            appendLine("- 可以降权不再成立的记忆")
            appendLine("- source_refs 至少 1 个（引用事件 ID）")
            appendLine("- confidence 范围 0.0 ~ 1.0")
            appendLine("- type 可选值: PREFERENCE, PATTERN, LONG_TERM_GOAL, TASK_CONTEXT, PREP_TEMPLATE")
            appendLine()
            appendLine("输出 JSON 格式：")
            appendLine("""
{
  "new_memories": [
    {"type": "PREFERENCE", "subject": "主题", "content": "内容", "confidence": 0.7, "source_refs": ["event_id"]}
  ],
  "updates": [
    {"memory_id": "id", "content": "更新内容", "confidence": 0.8, "new_source_refs": ["event_id"]}
  ],
  "downgrades": [
    {"memory_id": "id", "new_confidence": 0.2, "reason": "降权原因"}
  ]
}
            """.trimIndent())
        }

        val userMessage = buildString {
            appendLine("最近行为事件：")
            if (recentEvents.isEmpty()) {
                appendLine("（无最近事件）")
            } else {
                for (event in recentEvents.take(30)) {
                    appendLine("- [${event.eventType}] task=${event.taskId}, time=${event.createdAt}")
                    event.payloadJson?.let { payload ->
                        appendLine("  payload: ${payload.take(100)}")
                    }
                }
            }

            if (existingMemories.isNotEmpty()) {
                appendLine()
                appendLine("现有记忆：")
                for (mem in existingMemories) {
                    appendLine("- id=${mem.id} [${mem.type.name}] ${mem.subject}: ${mem.content} (置信度=${mem.confidence}, 证据数=${mem.evidenceCount})")
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
