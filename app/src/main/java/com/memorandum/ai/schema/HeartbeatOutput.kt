package com.memorandum.ai.schema

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HeartbeatOutput(
    @SerialName("should_use_mcp") val shouldUseMcp: Boolean = false,
    @SerialName("mcp_queries") val mcpQueries: List<String> = emptyList(),
    @SerialName("should_notify") val shouldNotify: Boolean = false,
    val notification: HeartbeatNotification? = null,
    val reason: String = "",
    @SerialName("task_ref") val taskRef: String? = null,
    @SerialName("cooldown_hours") val cooldownHours: Int = 4,
)

@Serializable
data class HeartbeatNotification(
    val type: String = "",
    @SerialName("action_type") val actionType: String = "",
    val title: String = "",
    val body: String = "",
)
