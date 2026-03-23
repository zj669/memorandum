package com.memorandum.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.memorandum.data.local.room.enums.EntryType
import com.memorandum.data.local.room.enums.PlanningState

@Entity(
    tableName = "entries",
    indices = [
        Index("type", "created_at"),
        Index("planning_state", "updated_at"),
        Index("deadline_at"),
    ],
)
data class EntryEntity(
    @PrimaryKey val id: String,
    val type: EntryType,
    val text: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val priority: Int?,
    @ColumnInfo(name = "deadline_at") val deadlineAt: Long?,
    @ColumnInfo(name = "estimated_minutes") val estimatedMinutes: Int?,
    @ColumnInfo(name = "image_uris_json") val imageUrisJson: List<String>,
    @ColumnInfo(name = "planning_state") val planningState: PlanningState,
    @ColumnInfo(name = "clarification_used") val clarificationUsed: Boolean,
    @ColumnInfo(name = "clarification_question") val clarificationQuestion: String?,
    @ColumnInfo(name = "clarification_answer") val clarificationAnswer: String?,
    @ColumnInfo(name = "last_planned_at") val lastPlannedAt: Long?,
)
