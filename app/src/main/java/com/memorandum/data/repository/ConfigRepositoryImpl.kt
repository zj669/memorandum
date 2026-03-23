package com.memorandum.data.repository

import com.memorandum.data.local.room.dao.LlmConfigDao
import com.memorandum.data.local.room.dao.McpServerDao
import com.memorandum.data.local.room.entity.LlmConfigEntity
import com.memorandum.data.local.room.entity.McpServerEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigRepositoryImpl @Inject constructor(
    private val llmConfigDao: LlmConfigDao,
    private val mcpServerDao: McpServerDao,
) : ConfigRepository {

    override fun observeLlmConfigs(): Flow<List<LlmConfigEntity>> =
        llmConfigDao.observeAll()

    override suspend fun getDefaultLlm(): Result<LlmConfigEntity?> = runCatching {
        llmConfigDao.getDefault()
    }

    override suspend fun getLlmById(id: String): Result<LlmConfigEntity?> = runCatching {
        llmConfigDao.getById(id)
    }

    override suspend fun saveLlm(config: LlmConfigEntity): Result<Unit> = runCatching {
        llmConfigDao.upsert(config)
    }

    override suspend fun deleteLlm(config: LlmConfigEntity): Result<Unit> = runCatching {
        llmConfigDao.delete(config)
    }

    override fun observeMcpServers(): Flow<List<McpServerEntity>> =
        mcpServerDao.observeAll()

    override suspend fun getEnabledMcpServers(): Result<List<McpServerEntity>> = runCatching {
        mcpServerDao.getEnabled()
    }

    override suspend fun getMcpById(id: String): Result<McpServerEntity?> = runCatching {
        mcpServerDao.getById(id)
    }

    override suspend fun saveMcp(server: McpServerEntity): Result<Unit> = runCatching {
        mcpServerDao.upsert(server)
    }

    override suspend fun deleteMcp(server: McpServerEntity): Result<Unit> = runCatching {
        mcpServerDao.delete(server)
    }

    override suspend fun updateMcpEnabled(id: String, enabled: Boolean): Result<Unit> = runCatching {
        mcpServerDao.updateEnabled(id, enabled, System.currentTimeMillis())
    }
}
