package com.memorandum.domain.usecase.memory

import android.util.Log
import com.memorandum.data.local.room.dao.MemoryDao
import com.memorandum.data.local.room.dao.UserProfileDao
import com.memorandum.data.local.room.entity.MemoryEntity
import com.memorandum.data.local.room.entity.UserProfileEntity
import com.memorandum.data.local.room.enums.MemoryType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MemoryDisplayUseCase @Inject constructor(
    private val memoryDao: MemoryDao,
    private val userProfileDao: UserProfileDao,
    private val aggregateProfileUseCase: AggregateProfileUseCase,
) {

    companion object {
        private const val TAG = "MemoryDisplayUseCase"
    }

    fun observeMemories(typeFilter: MemoryType?): Flow<List<MemoryEntity>> {
        return if (typeFilter != null) {
            memoryDao.observeByType(typeFilter)
        } else {
            memoryDao.observeAll()
        }
    }

    fun observeProfile(): Flow<UserProfileEntity?> = userProfileDao.observe()

    suspend fun deleteMemory(memoryId: String) {
        memoryDao.deleteById(memoryId)
        Log.i(TAG, "Memory deleted: id=$memoryId, re-aggregating profile")
        aggregateProfileUseCase.aggregate()
    }
}
