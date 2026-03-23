package com.memorandum.data.remote.llm

interface LlmClient {
    suspend fun chat(request: LlmRequest): Result<LlmResponse>
    suspend fun testConnection(): Result<Boolean>
}
