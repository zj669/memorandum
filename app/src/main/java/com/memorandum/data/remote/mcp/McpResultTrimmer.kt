package com.memorandum.data.remote.mcp

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class McpResultTrimmer @Inject constructor() {

    fun trim(rawResult: String, maxChars: Int = 2000): String {
        val cleaned = stripHtml(rawResult).trim()
        if (cleaned.length <= maxChars) return cleaned
        return cleaned.take(maxChars - 3) + "..."
    }

    fun mergeResults(results: List<McpQueryResult>, maxTotalChars: Int = 4000): String {
        if (results.isEmpty()) return ""

        val perResultBudget = maxTotalChars / results.size
        val parts = results.map { result ->
            val trimmed = trim(result.trimmedResult, perResultBudget)
            "[${result.serverName}/${result.toolName}] ${result.query}:\n$trimmed"
        }

        val merged = parts.joinToString("\n\n")
        if (merged.length <= maxTotalChars) return merged
        return merged.take(maxTotalChars - 3) + "..."
    }

    private fun stripHtml(text: String): String {
        return text
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
    }
}

data class McpQueryResult(
    val query: String,
    val serverName: String,
    val toolName: String,
    val rawResult: String,
    val trimmedResult: String,
)
