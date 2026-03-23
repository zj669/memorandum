package com.memorandum.data.local.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.memorandum.data.local.room.entity.LlmConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LlmConfigDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: LlmConfigEntity)

    @Query("SELECT * FROM llm_configs ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<LlmConfigEntity>>

    @Query("SELECT * FROM llm_configs WHERE id = :id")
    suspend fun getById(id: String): LlmConfigEntity?

    @Query("SELECT * FROM llm_configs ORDER BY updated_at DESC LIMIT 1")
    suspend fun getDefault(): LlmConfigEntity?

    @Delete
    suspend fun delete(config: LlmConfigEntity)
}
