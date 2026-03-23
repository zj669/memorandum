package com.memorandum.ai.schema

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlannerOutput(
    @SerialName("needs_clarification") val needsClarification: Boolean = false,
    @SerialName("clarification_question") val clarificationQuestion: String? = null,
    @SerialName("clarification_reason") val clarificationReason: String? = null,
    @SerialName("should_use_mcp") val shouldUseMcp: Boolean = false,
    @SerialName("mcp_queries") val mcpQueries: List<String> = emptyList(),
    @SerialName("task_title") val taskTitle: String? = null,
    val summary: String? = null,
    val steps: List<PlanStep> = emptyList(),
    @SerialName("schedule_blocks") val scheduleBlocks: List<ScheduleBlock> = emptyList(),
    @SerialName("prep_items") val prepItems: List<String> = emptyList(),
    val risks: List<String> = emptyList(),
    @SerialName("notification_candidates") val notificationCandidates: List<NotificationCandidate> = emptyList(),
)

@Serializable
data class PlanStep(
    val index: Int = 0,
    val title: String = "",
    val description: String = "",
    @SerialName("needs_mcp") val needsMcp: Boolean = false,
)

@Serializable
data class ScheduleBlock(
    val date: String = "",
    val start: String = "",
    val end: String = "",
    val reason: String = "",
)

@Serializable
data class NotificationCandidate(
    val type: String = "",
    val title: String = "",
    val body: String = "",
    @SerialName("action_type") val actionType: String = "",
)
