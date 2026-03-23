package com.memorandum.data.repository

import com.memorandum.data.local.room.entity.MemoryEntity
import com.memorandum.data.local.room.enums.MemoryType
import kotlinx.coroutines.flow.Flow

interface MemoryRepository {
    suspend fun upsert(memory: MemoryEntity): Result<Unit>
    suspend fun getForPlanning(): Result<List<MemoryEntity>>
    fun observeByType(type: MemoryType): Flow<List<MemoryEntity>>
    suspend fun downgrade(id: String, newConfidence: Float): Result<Unit>
}
