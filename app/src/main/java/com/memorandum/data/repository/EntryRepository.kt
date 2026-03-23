package com.memorandum.data.repository

import com.memorandum.data.local.room.entity.EntryEntity
import com.memorandum.data.local.room.enums.PlanningState
import kotlinx.coroutines.flow.Flow

interface EntryRepository {
    suspend fun create(entry: EntryEntity): Result<String>
    fun observeById(id: String): Flow<EntryEntity?>
    fun observeActivePlanning(): Flow<List<EntryEntity>>
    suspend fun updatePlanningState(id: String, state: PlanningState): Result<Unit>
    suspend fun saveClarification(id: String, question: String, answer: String?): Result<Unit>
    suspend fun delete(id: String): Result<Unit>
}
