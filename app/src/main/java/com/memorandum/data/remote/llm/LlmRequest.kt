package com.memorandum.data.remote.llm

data class LlmRequest(
    val systemPrompt: String,
    val userMessage: String,
    val images: List<ImageInput> = emptyList(),
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val responseFormat: ResponseFormat = ResponseFormat.JSON,
)

data class ImageInput(
    val uri: String,
    val base64Data: String,
    val mimeType: String,
)

enum class ResponseFormat { JSON, TEXT }
