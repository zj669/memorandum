package com.memorandum.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mcp_servers")
data class McpServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "base_url") val baseUrl: String,
    @ColumnInfo(name = "auth_type") val authType: String,
    @ColumnInfo(name = "auth_value_encrypted") val authValueEncrypted: String?,
    val enabled: Boolean,
    @ColumnInfo(name = "tool_whitelist_json") val toolWhitelistJson: List<String>,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
