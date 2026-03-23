package com.memorandum.ai.schema

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MemoryOutput(
    @SerialName("new_memories") val newMemories: List<NewMemory> = emptyList(),
    val updates: List<MemoryUpdate> = emptyList(),
    val downgrades: List<MemoryDowngrade> = emptyList(),
)

@Serializable
data class NewMemory(
    val type: String = "",
    val subject: String = "",
    val content: String = "",
    val confidence: Float = 0.5f,
    @SerialName("source_refs") val sourceRefs: List<String> = emptyList(),
)

@Serializable
data class MemoryUpdate(
    @SerialName("memory_id") val memoryId: String = "",
    val content: String? = null,
    val confidence: Float? = null,
    @SerialName("new_source_refs") val newSourceRefs: List<String> = emptyList(),
)

@Serializable
data class MemoryDowngrade(
    @SerialName("memory_id") val memoryId: String = "",
    @SerialName("new_confidence") val newConfidence: Float = 0f,
    val reason: String = "",
)
