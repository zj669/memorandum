package com.memorandum.data.repository

import com.memorandum.data.local.room.entity.LlmConfigEntity
import com.memorandum.data.local.room.entity.McpServerEntity
import kotlinx.coroutines.flow.Flow

interface ConfigRepository {
    fun observeLlmConfigs(): Flow<List<LlmConfigEntity>>
    suspend fun getDefaultLlm(): Result<LlmConfigEntity?>
    suspend fun getLlmById(id: String): Result<LlmConfigEntity?>
    suspend fun saveLlm(config: LlmConfigEntity): Result<Unit>
    suspend fun deleteLlm(config: LlmConfigEntity): Result<Unit>
    fun observeMcpServers(): Flow<List<McpServerEntity>>
    suspend fun getEnabledMcpServers(): Result<List<McpServerEntity>>
    suspend fun getMcpById(id: String): Result<McpServerEntity?>
    suspend fun saveMcp(server: McpServerEntity): Result<Unit>
    suspend fun deleteMcp(server: McpServerEntity): Result<Unit>
    suspend fun updateMcpEnabled(id: String, enabled: Boolean): Result<Unit>
}
