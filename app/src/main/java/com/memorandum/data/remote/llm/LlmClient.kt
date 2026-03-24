package com.memorandum.data.remote.llm

interface LlmClient {
    suspend fun chat(request: LlmRequest): Result<LlmResponse>
    suspend fun testConnection(): Result<Boolean>
    suspend fun getCapabilities(): Result<LlmCapabilities>
}

data class LlmCapabilities(
    val supportsTools: Boolean,
    val supportsRequiredToolChoice: Boolean,
)
