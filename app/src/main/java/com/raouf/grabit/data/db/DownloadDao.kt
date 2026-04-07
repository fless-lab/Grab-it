package com.raouf.grabit.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads WHERE isHidden = 0 ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE isHidden = 0 AND status IN ('EXTRACTING','QUEUED','DOWNLOADING','PAUSED','WAITING_NETWORK') ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE isHidden = 0 AND status = 'COMPLETED' ORDER BY completedAt DESC")
    fun observeCompleted(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE isHidden = 1 ORDER BY createdAt DESC")
    fun observeHidden(): Flow<List<DownloadEntity>>

    @Query("UPDATE downloads SET isHidden = 1 WHERE id = :id")
    suspend fun hide(id: Long)

    @Query("UPDATE downloads SET isHidden = 0 WHERE id = :id")
    suspend fun unhide(id: Long)

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status = 'WAITING_NETWORK' ORDER BY createdAt DESC")
    suspend fun getWaitingNetwork(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE status = 'QUEUED' ORDER BY createdAt ASC")
    suspend fun getQueued(): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadEntity): Long

    @Update
    suspend fun update(entity: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, progress = :progress WHERE id = :id")
    suspend fun updateProgress(id: Long, status: String, progress: Float)

    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE downloads SET status = 'COMPLETED', progress = 1.0, filePath = :path, fileSize = :size, completedAt = :time WHERE id = :id")
    suspend fun markCompleted(id: Long, path: String, size: Long, time: Long = System.currentTimeMillis())

    @Query("UPDATE downloads SET status = 'FAILED', errorMessage = :error WHERE id = :id")
    suspend fun markFailed(id: Long, error: String)

    @Query("UPDATE downloads SET status = 'QUEUED', errorMessage = NULL, progress = 0 WHERE id = :id")
    suspend fun resetForRetry(id: Long)

    @Query("UPDATE downloads SET status = 'QUEUED' WHERE id = :id")
    suspend fun resetForResume(id: Long)

    @Query("UPDATE downloads SET filePath = :path WHERE id = :id")
    suspend fun updateFilePath(id: Long, path: String)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM downloads WHERE status = 'COMPLETED'")
    suspend fun clearCompleted()
}
