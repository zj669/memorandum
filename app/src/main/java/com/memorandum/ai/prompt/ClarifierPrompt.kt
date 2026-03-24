package com.memorandum.ai.prompt

import com.memorandum.ai.schema.StructuredOutputTools
import com.memorandum.data.local.room.entity.EntryEntity
import com.memorandum.data.local.room.entity.MemoryEntity
import com.memorandum.data.remote.llm.LlmRequest
import com.memorandum.data.remote.llm.LlmToolChoice
import com.memorandum.data.remote.llm.ResponseFormat

object ClarifierPrompt {

    private val promptBuilder = PromptBuilder()

    fun build(
        entry: EntryEntity,
        userProfileJson: String?,
        memories: List<MemoryEntity>,
    ): LlmRequest {
        val systemPrompt = buildString {
            append(promptBuilder.buildSystemBase())
            append(promptBuilder.appendUserProfile(userProfileJson))
            append(promptBuilder.appendMemories(memories))
            append(promptBuilder.appendTimeContext())

            appendLine()
            appendLine("补充提问指令：")
            appendLine("判断用户输入的信息是否足够进行任务规划。")
            appendLine("如果信息不足，提出一个关键问题来补充。")
            appendLine()
            appendLine("规则：")
            appendLine("- 只允许问一个问题")
            appendLine("- 问题必须直接影响排程或拆解质量")
            appendLine("- 如果信息已经足够，设置 ask=false")
            appendLine("- 不要问无关紧要的问题")
            appendLine()
            appendLine("请通过工具 submit_clarifier_output 返回结构化结果；不要输出 JSON 文本。")
            appendLine("""
{
  "ask": true,
  "question": "你的问题",
  "reason": "为什么需要问这个问题"
}
            """.trimIndent())
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
        }

        return LlmRequest(
            systemPrompt = systemPrompt,
            userMessage = userMessage,
            responseFormat = ResponseFormat.JSON,
            tools = listOf(StructuredOutputTools.clarifier),
            toolChoice = LlmToolChoice.Specific(StructuredOutputTools.clarifier.name),
        )
    }
}
