package com.memorandum.data.local.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.memorandum.data.local.room.entity.MemoryEntity
import com.memorandum.data.local.room.enums.MemoryType
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: MemoryEntity)

    @Query("SELECT * FROM memories ORDER BY confidence DESC")
    suspend fun getAll(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getById(id: String): MemoryEntity?

    @Query("SELECT * FROM memories ORDER BY confidence DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE type = :type ORDER BY confidence DESC")
    fun observeByType(type: MemoryType): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE confidence >= :minConfidence ORDER BY updated_at DESC")
    suspend fun getHighConfidence(minConfidence: Float = 0.5f): List<MemoryEntity>

    @Query("SELECT * FROM memories ORDER BY confidence DESC LIMIT :limit")
    suspend fun getTopMemories(limit: Int = 20): List<MemoryEntity>

    @Query("UPDATE memories SET confidence = :confidence, updated_at = :now WHERE id = :id")
    suspend fun updateConfidence(id: String, confidence: Float, now: Long)

    @Query("UPDATE memories SET last_used_at = :now WHERE id = :id")
    suspend fun markUsed(id: String, now: Long)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM memories WHERE confidence < :threshold")
    suspend fun deleteByLowConfidence(threshold: Float)

    @Delete
    suspend fun delete(memory: MemoryEntity)

    @Query("DELETE FROM memories")
    suspend fun deleteAll()
}
