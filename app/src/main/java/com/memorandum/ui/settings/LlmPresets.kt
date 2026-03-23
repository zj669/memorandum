package com.memorandum.ui.settings

data class LlmPreset(
    val name: String,
    val baseUrl: String,
    val suggestedModels: List<String>,
    val supportsImage: Boolean,
)

object LlmPresets {
    val presets = listOf(
        LlmPreset(
            name = "OpenAI",
            baseUrl = "https://api.openai.com",
            suggestedModels = listOf("gpt-4o", "gpt-4o-mini"),
            supportsImage = true,
        ),
        LlmPreset(
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com",
            suggestedModels = listOf("deepseek-chat", "deepseek-reasoner"),
            supportsImage = false,
        ),
        LlmPreset(
            name = "自定义",
            baseUrl = "",
            suggestedModels = emptyList(),
            supportsImage = false,
        ),
    )
}
