package com.memorandum.data.repository

import com.memorandum.data.local.room.dao.MemoryDao
import com.memorandum.data.local.room.entity.MemoryEntity
import com.memorandum.data.local.room.enums.MemoryType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepositoryImpl @Inject constructor(
    private val memoryDao: MemoryDao,
) : MemoryRepository {

    override suspend fun upsert(memory: MemoryEntity): Result<Unit> = runCatching {
        memoryDao.upsert(memory)
    }

    override suspend fun getForPlanning(): Result<List<MemoryEntity>> = runCatching {
        memoryDao.getTopMemories()
    }

    override fun observeByType(type: MemoryType): Flow<List<MemoryEntity>> =
        memoryDao.observeByType(type)

    override suspend fun downgrade(id: String, newConfidence: Float): Result<Unit> = runCatching {
        memoryDao.updateConfidence(id, newConfidence, System.currentTimeMillis())
    }
}
