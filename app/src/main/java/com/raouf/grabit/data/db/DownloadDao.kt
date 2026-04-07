package com.raouf.grabit.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN ('EXTRACTING','QUEUED','DOWNLOADING','PAUSED') ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' ORDER BY completedAt DESC")
    fun observeCompleted(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: Long): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadEntity): Long

    @Update
    suspend fun update(entity: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, progress = :progress WHERE id = :id")
    suspend fun updateProgress(id: Long, status: String, progress: Float)

    @Query("UPDATE downloads SET status = 'COMPLETED', progress = 1.0, filePath = :path, fileSize = :size, completedAt = :time WHERE id = :id")
    suspend fun markCompleted(id: Long, path: String, size: Long, time: Long = System.currentTimeMillis())

    @Query("UPDATE downloads SET status = 'FAILED', errorMessage = :error WHERE id = :id")
    suspend fun markFailed(id: Long, error: String)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM downloads WHERE status = 'COMPLETED'")
    suspend fun clearCompleted()
}
