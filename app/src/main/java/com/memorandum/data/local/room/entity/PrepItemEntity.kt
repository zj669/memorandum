package com.memorandum.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.memorandum.data.local.room.enums.PrepStatus

@Entity(
    tableName = "prep_items",
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class,
        parentColumns = ["id"],
        childColumns = ["task_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("task_id")],
)
data class PrepItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    val content: String,
    val status: PrepStatus,
    @ColumnInfo(name = "source_memory_id") val sourceMemoryId: String?,
)
