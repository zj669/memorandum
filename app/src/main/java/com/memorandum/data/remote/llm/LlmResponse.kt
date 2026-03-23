package com.memorandum.data.remote.llm

data class LlmResponse(
    val content: String,
    val usage: TokenUsage?,
    val finishReason: String?,
)

data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
)
