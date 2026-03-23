package com.memorandum.data.repository

import com.memorandum.data.local.room.dao.EntryDao
import com.memorandum.data.local.room.entity.EntryEntity
import com.memorandum.data.local.room.enums.PlanningState
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntryRepositoryImpl @Inject constructor(
    private val entryDao: EntryDao,
) : EntryRepository {

    override suspend fun create(entry: EntryEntity): Result<String> = runCatching {
        entryDao.upsert(entry)
        entry.id
    }

    override fun observeById(id: String): Flow<EntryEntity?> =
        entryDao.observeById(id)

    override fun observeActivePlanning(): Flow<List<EntryEntity>> =
        entryDao.observeByPlanningStates(
            listOf(
                PlanningState.CLARIFYING,
                PlanningState.ASKING,
                PlanningState.PLANNING,
                PlanningState.ENRICHING_MCP,
                PlanningState.SAVING,
                PlanningState.FAILED,
            ),
        )

    override suspend fun updatePlanningState(id: String, state: PlanningState): Result<Unit> =
        runCatching {
            entryDao.updatePlanningState(id, state, System.currentTimeMillis())
        }

    override suspend fun saveClarification(
        id: String,
        question: String,
        answer: String?,
    ): Result<Unit> = runCatching {
        entryDao.saveClarification(id, question, answer, System.currentTimeMillis())
    }

    override suspend fun delete(id: String): Result<Unit> = runCatching {
        entryDao.deleteById(id)
    }
}
