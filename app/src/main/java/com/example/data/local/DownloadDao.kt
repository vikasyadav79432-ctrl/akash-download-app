package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun observeTasks(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: String): DownloadTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: DownloadTaskEntity)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: String)

    @Query("SELECT * FROM download_history ORDER BY downloadedAt DESC")
    fun observeHistory(): Flow<List<DownloadHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: DownloadHistoryEntity)

    @Query("DELETE FROM download_history WHERE id = :id")
    suspend fun deleteHistoryById(id: String)
}
