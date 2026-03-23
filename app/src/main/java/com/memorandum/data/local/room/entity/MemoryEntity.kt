package com.memorandum.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.memorandum.data.local.room.enums.MemoryType

@Entity(
    tableName = "memories",
    indices = [
        Index("type", "confidence"),
        Index("subject"),
    ],
)
data class MemoryEntity(
    @PrimaryKey val id: String,
    val type: MemoryType,
    val subject: String,
    val content: String,
    val confidence: Float,
    @ColumnInfo(name = "source_refs_json") val sourceRefsJson: List<String>,
    @ColumnInfo(name = "evidence_count") val evidenceCount: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long?,
)
