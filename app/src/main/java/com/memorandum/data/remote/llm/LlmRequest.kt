package com.memorandum.data.remote.llm

import kotlinx.serialization.json.JsonObject

data class LlmRequest(
    val systemPrompt: String,
    val userMessage: String,
    val images: List<ImageInput> = emptyList(),
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val responseFormat: ResponseFormat = ResponseFormat.JSON,
    val tools: List<LlmToolDefinition> = emptyList(),
    val toolChoice: LlmToolChoice = LlmToolChoice.Auto,
)

data class ImageInput(
    val uri: String,
    val base64Data: String,
    val mimeType: String,
)

data class LlmToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
)

sealed interface LlmToolChoice {
    data object Auto : LlmToolChoice
    data object None : LlmToolChoice
    data object Required : LlmToolChoice
    data class Specific(val toolName: String) : LlmToolChoice
}

enum class ResponseFormat { JSON, TEXT }
