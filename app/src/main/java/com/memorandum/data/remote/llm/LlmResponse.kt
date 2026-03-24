package com.memorandum.data.remote.llm

import kotlinx.serialization.json.JsonObject

data class LlmResponse(
    val content: String,
    val usage: TokenUsage?,
    val finishReason: String?,
    val toolCalls: List<LlmToolCall> = emptyList(),
)

data class LlmToolCall(
    val id: String,
    val name: String,
    val arguments: JsonObject,
)

data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
)
