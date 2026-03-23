package com.memorandum.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.memorandum.data.local.room.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfileEntity)

    @Query("SELECT * FROM user_profile WHERE id = 'default'")
    suspend fun get(): UserProfileEntity?

    @Query("SELECT * FROM user_profile WHERE id = 'default'")
    fun observe(): Flow<UserProfileEntity?>
}
