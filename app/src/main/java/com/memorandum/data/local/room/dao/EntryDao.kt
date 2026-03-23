package com.memorandum.data.local.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.memorandum.data.local.room.entity.EntryEntity
import com.memorandum.data.local.room.enums.EntryType
import com.memorandum.data.local.room.enums.PlanningState
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: EntryEntity)

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getById(id: String): EntryEntity?

    @Query("SELECT * FROM entries WHERE id = :id")
    fun observeById(id: String): Flow<EntryEntity?>

    @Query("SELECT * FROM entries WHERE type = :type ORDER BY created_at DESC")
    fun observeByType(type: EntryType): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE planning_state IN (:states) ORDER BY updated_at ASC")
    suspend fun getByPlanningStates(states: List<PlanningState>): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE planning_state IN (:states) ORDER BY updated_at DESC")
    fun observeByPlanningStates(states: List<PlanningState>): Flow<List<EntryEntity>>

    @Query("UPDATE entries SET planning_state = :state, updated_at = :now WHERE id = :id")
    suspend fun updatePlanningState(id: String, state: PlanningState, now: Long)

    @Query(
        """UPDATE entries SET
        clarification_used = 1,
        clarification_question = :question,
        clarification_answer = :answer,
        updated_at = :now
        WHERE id = :id""",
    )
    suspend fun saveClarification(id: String, question: String, answer: String?, now: Long)

    @Delete
    suspend fun delete(entry: EntryEntity)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteById(id: String)
}
