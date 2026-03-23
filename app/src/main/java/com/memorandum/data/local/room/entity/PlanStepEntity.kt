package com.memorandum.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.memorandum.data.local.room.enums.StepStatus

@Entity(
    tableName = "plan_steps",
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class,
        parentColumns = ["id"],
        childColumns = ["task_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("task_id")],
)
data class PlanStepEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "step_index") val stepIndex: Int,
    val title: String,
    val description: String,
    val status: StepStatus,
    @ColumnInfo(name = "needs_mcp") val needsMcp: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
