package com.memorandum.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.memorandum.data.local.room.entity.NotificationEntity
import com.memorandum.data.local.room.enums.NotificationType
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    @Insert
    suspend fun insert(notification: NotificationEntity)

    @Query("SELECT * FROM notifications ORDER BY created_at DESC")
    fun observeAll(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE task_ref = :taskRef ORDER BY created_at DESC")
    fun observeByTask(taskRef: String): Flow<List<NotificationEntity>>

    @Query(
        """SELECT * FROM notifications
        WHERE task_ref = :taskRef AND type = :type AND created_at > :since""",
    )
    suspend fun getRecentForTaskAndType(taskRef: String, type: NotificationType, since: Long): List<NotificationEntity>

    @Query("UPDATE notifications SET clicked_at = :now WHERE id = :id")
    suspend fun markClicked(id: String, now: Long)

    @Query("UPDATE notifications SET dismissed_at = :now WHERE id = :id")
    suspend fun markDismissed(id: String, now: Long)

    @Query("UPDATE notifications SET snoozed_until = :until WHERE id = :id")
    suspend fun markSnoozed(id: String, until: Long)

    @Query("SELECT * FROM notifications WHERE created_at >= :since ORDER BY created_at DESC")
    suspend fun getNotificationsSince(since: Long): List<NotificationEntity>

    @Query(
        """SELECT * FROM notifications
        WHERE type = :type AND created_at > :since
        ORDER BY created_at DESC""",
    )
    suspend fun getRecentByType(type: NotificationType, since: Long): List<NotificationEntity>

    @Query("DELETE FROM notifications")
    suspend fun deleteAll()
}
