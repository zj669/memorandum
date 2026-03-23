package com.memorandum.data.local.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.memorandum.data.local.room.entity.McpServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface McpServerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(server: McpServerEntity)

    @Query("SELECT * FROM mcp_servers ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<McpServerEntity>>

    @Query("SELECT * FROM mcp_servers WHERE id = :id")
    suspend fun getById(id: String): McpServerEntity?

    @Query("SELECT * FROM mcp_servers WHERE enabled = 1")
    suspend fun getEnabled(): List<McpServerEntity>

    @Query("UPDATE mcp_servers SET enabled = :enabled, updated_at = :now WHERE id = :id")
    suspend fun updateEnabled(id: String, enabled: Boolean, now: Long)

    @Delete
    suspend fun delete(server: McpServerEntity)
}
