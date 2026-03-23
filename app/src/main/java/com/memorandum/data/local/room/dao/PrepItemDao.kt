package com.memorandum.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.memorandum.data.local.room.entity.PrepItemEntity
import com.memorandum.data.local.room.enums.PrepStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PrepItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PrepItemEntity>)

    @Query("SELECT * FROM prep_items WHERE task_id = :taskId")
    fun observeByTask(taskId: String): Flow<List<PrepItemEntity>>

    @Query("UPDATE prep_items SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: PrepStatus)

    @Query("DELETE FROM prep_items WHERE task_id = :taskId")
    suspend fun deleteByTask(taskId: String)
}
