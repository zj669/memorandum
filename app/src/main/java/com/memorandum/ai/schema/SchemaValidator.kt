package com.memorandum.ai.schema

import android.util.Log
import com.memorandum.data.local.room.enums.NotificationActionType
import com.memorandum.data.local.room.enums.NotificationType
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
)

@Singleton
class SchemaValidator @Inject constructor() {

    companion object {
        private const val TAG = "SchemaValidator"
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
        private const val MAX_MCP_QUERIES = 3
    }

    fun validatePlannerOutput(output: PlannerOutput): ValidationResult {
        val errors = mutableListOf<String>()

        if (output.needsClarification) {
            // When asking clarification, should not have a final plan
            if (output.taskTitle != null) {
                errors.add("needs_clarification=true but task_title is present")
            }
            if (output.steps.isNotEmpty()) {
                errors.add("needs_clarification=true but steps is not empty")
            }
        } else if (!output.shouldUseMcp) {
            // Final plan: must have title, summary, steps
            if (output.taskTitle.isNullOrBlank()) {
                errors.add("task_title is required when needs_clarification=false")
            }
            if (output.summary.isNullOrBlank()) {
                errors.add("summary is required when needs_clarification=false")
            }
            if (output.steps.isEmpty()) {
                errors.add("steps must have at least 1 item")
            }
        }

        // Validate schedule_blocks dates and times
        val today = LocalDate.now()
        for ((i, block) in output.scheduleBlocks.withIndex()) {
            try {
                val date = LocalDate.parse(block.date, DATE_FORMAT)
                if (date.isBefore(today)) {
                    errors.add("schedule_blocks[$i].date ${block.date} is before today")
                }
            } catch (_: DateTimeParseException) {
                errors.add("schedule_blocks[$i].date '${block.date}' is not valid yyyy-MM-dd")
            }

            if (!isValidTime(block.start)) {
                errors.add("schedule_blocks[$i].start '${block.start}' is not valid HH:mm")
            }
            if (!isValidTime(block.end)) {
                errors.add("schedule_blocks[$i].end '${block.end}' is not valid HH:mm")
            }
        }

        // mcp_queries max 3
        if (output.mcpQueries.size > MAX_MCP_QUERIES) {
            errors.add("mcp_queries has ${output.mcpQueries.size} items, max is $MAX_MCP_QUERIES")
        }

        if (errors.isNotEmpty()) {
            Log.e(TAG, "PlannerOutput validation failed: $errors")
        }
        return ValidationResult(isValid = errors.isEmpty(), errors = errors)
    }

    fun validateHeartbeatOutput(output: HeartbeatOutput): ValidationResult {
        val errors = mutableListOf<String>()

        if (!output.shouldNotify && output.notification != null) {
            errors.add("should_notify=false but notification is present")
        }
        if (output.shouldNotify && output.notification == null) {
            errors.add("should_notify=true but notification is null")
        }
        if (output.cooldownHours < 1) {
            errors.add("cooldown_hours must be >= 1, got ${output.cooldownHours}")
        }

        // Validate notification type if present
        output.notification?.let { notif ->
            if (!isValidNotificationType(notif.type)) {
                errors.add("notification.type '${notif.type}' is not a valid NotificationType")
            }
            if (!isValidActionType(notif.actionType)) {
                errors.add("notification.action_type '${notif.actionType}' is not a valid NotificationActionType")
            }
        }

        if (output.mcpQueries.size > MAX_MCP_QUERIES) {
            errors.add("mcp_queries has ${output.mcpQueries.size} items, max is $MAX_MCP_QUERIES")
        }

        if (errors.isNotEmpty()) {
            Log.e(TAG, "HeartbeatOutput validation failed: $errors")
        }
        return ValidationResult(isValid = errors.isEmpty(), errors = errors)
    }

    fun validateMemoryOutput(
        output: MemoryOutput,
        existingMemoryIds: Set<String>,
    ): ValidationResult {
        val errors = mutableListOf<String>()

        for ((i, mem) in output.newMemories.withIndex()) {
            if (mem.confidence < 0f || mem.confidence > 1f) {
                errors.add("new_memories[$i].confidence ${mem.confidence} not in [0,1]")
            }
            if (mem.sourceRefs.isEmpty()) {
                errors.add("new_memories[$i].source_refs must have at least 1 item")
            }
        }

        for ((i, update) in output.updates.withIndex()) {
            if (update.memoryId.isNotBlank() && update.memoryId !in existingMemoryIds) {
                errors.add("updates[$i].memory_id '${update.memoryId}' does not exist")
            }
            update.confidence?.let { conf ->
                if (conf < 0f || conf > 1f) {
                    errors.add("updates[$i].confidence $conf not in [0,1]")
                }
            }
        }

        for ((i, dg) in output.downgrades.withIndex()) {
            if (dg.memoryId.isNotBlank() && dg.memoryId !in existingMemoryIds) {
                errors.add("downgrades[$i].memory_id '${dg.memoryId}' does not exist")
            }
            if (dg.newConfidence < 0f || dg.newConfidence > 1f) {
                errors.add("downgrades[$i].new_confidence ${dg.newConfidence} not in [0,1]")
            }
        }

        if (errors.isNotEmpty()) {
            Log.e(TAG, "MemoryOutput validation failed: $errors")
        }
        return ValidationResult(isValid = errors.isEmpty(), errors = errors)
    }

    private fun isValidTime(time: String): Boolean {
        return try {
            LocalTime.parse(time, TIME_FORMAT)
            true
        } catch (_: DateTimeParseException) {
            false
        }
    }

    private fun isValidNotificationType(type: String): Boolean {
        return try {
            NotificationType.valueOf(type.uppercase())
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun isValidActionType(type: String): Boolean {
        return try {
            NotificationActionType.valueOf(type.uppercase())
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}
