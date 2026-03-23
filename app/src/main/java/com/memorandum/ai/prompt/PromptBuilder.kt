package com.memorandum.ai.prompt

import com.memorandum.data.local.room.entity.MemoryEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class PromptBuilder {

    companion object {
        private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
    }

    fun buildSystemBase(): String = buildString {
        appendLine("你是一个智能个人规划助手。")
        appendLine("你的职责是帮助用户拆解任务、安排日程、管理提醒。")
        appendLine()
        appendLine("输出要求：")
        appendLine("- 严格输出 JSON 格式，不要输出任何其他文字")
        appendLine("- JSON key 使用英文 snake_case")
        appendLine("- 内容文字使用中文")
        appendLine("- 日期格式 yyyy-MM-dd，时间格式 HH:mm")
    }

    fun appendUserProfile(profileJson: String?): String {
        if (profileJson.isNullOrBlank()) return ""
        return buildString {
            appendLine()
            appendLine("用户画像：")
            appendLine(profileJson)
        }
    }

    fun appendMemories(memories: List<MemoryEntity>): String {
        if (memories.isEmpty()) return ""
        return buildString {
            appendLine()
            appendLine("用户记忆（按置信度排序）：")
            for (mem in memories.sortedByDescending { it.confidence }.take(10)) {
                appendLine("- [${mem.type.name}] ${mem.subject}: ${mem.content} (置信度: ${mem.confidence})")
            }
        }
    }

    fun appendTimeContext(): String {
        val now = LocalDate.now()
        val time = LocalTime.now()
        val dayOfWeek = now.dayOfWeek.name
        return buildString {
            appendLine()
            appendLine("当前时间上下文：")
            appendLine("- 日期: ${now.format(DATE_FMT)}")
            appendLine("- 时间: ${time.format(TIME_FMT)}")
            appendLine("- 星期: $dayOfWeek")
        }
    }
}
