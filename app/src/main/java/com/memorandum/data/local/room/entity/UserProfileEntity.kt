package com.memorandum.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "profile_json") val profileJson: String,
    val version: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
