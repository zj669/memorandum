package com.memorandum.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "llm_configs")
data class LlmConfigEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "provider_name") val providerName: String,
    @ColumnInfo(name = "base_url") val baseUrl: String,
    @ColumnInfo(name = "model_name") val modelName: String,
    @ColumnInfo(name = "api_key_encrypted") val apiKeyEncrypted: String,
    @ColumnInfo(name = "supports_image") val supportsImage: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
