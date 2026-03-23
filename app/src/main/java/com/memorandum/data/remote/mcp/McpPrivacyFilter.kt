package com.memorandum.data.remote.mcp

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class McpPrivacyFilter @Inject constructor() {

    companion object {
        // Simple patterns for common PII
        private val PHONE_REGEX = Regex("""1[3-9]\d{9}""")
        private val EMAIL_REGEX = Regex("""[\w.+-]+@[\w-]+\.[\w.]+""")
        private val ID_CARD_REGEX = Regex("""\d{17}[\dXx]""")
    }

    fun sanitizeQuery(query: String): String {
        return query
            .replace(PHONE_REGEX, "[PHONE]")
            .replace(EMAIL_REGEX, "[EMAIL]")
            .replace(ID_CARD_REGEX, "[ID]")
            .trim()
    }

    fun buildCallSummary(
        query: String,
        serverName: String,
        toolName: String,
        resultPreview: String,
    ): McpCallSummary {
        return McpCallSummary(
            timestamp = System.currentTimeMillis(),
            serverName = serverName,
            toolName = toolName,
            queryPreview = query.take(100),
            resultPreview = resultPreview.take(200),
            purpose = "AI辅助查询",
        )
    }
}

data class McpCallSummary(
    val timestamp: Long,
    val serverName: String,
    val toolName: String,
    val queryPreview: String,
    val resultPreview: String,
    val purpose: String,
)
