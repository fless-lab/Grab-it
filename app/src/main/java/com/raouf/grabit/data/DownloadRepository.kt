package com.raouf.grabit.data

import android.content.Context
import android.net.Uri
import com.raouf.grabit.data.db.DownloadDao
import com.raouf.grabit.data.db.DownloadEntity
import com.raouf.grabit.domain.model.Download
import com.raouf.grabit.domain.model.DownloadStatus
import com.raouf.grabit.domain.model.VideoSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    private val dao: DownloadDao,
    @ApplicationContext private val context: Context,
) {
    fun observeAll(): Flow<List<Download>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeActive(): Flow<List<Download>> =
        dao.observeActive().map { list -> list.map { it.toDomain() } }

    fun observeCompleted(): Flow<List<Download>> =
        dao.observeCompleted().map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: Long): Download? = dao.getById(id)?.toDomain()

    suspend fun getWaitingNetwork(): List<Download> = dao.getWaitingNetwork().map { it.toDomain() }

    suspend fun getQueued(): List<Download> = dao.getQueued().map { it.toDomain() }

    suspend fun createDownload(
        url: String,
        title: String,
        thumbnail: String?,
        source: VideoSource,
        isAudioOnly: Boolean,
        quality: String,
        formatId: String = "best",
        playlistId: String? = null,
        playlistTitle: String? = null,
        subLangs: String? = null,
    ): Long {
        val entity = DownloadEntity(
            url = url,
            title = title,
            thumbnail = thumbnail,
            source = source.name,
            status = DownloadStatus.QUEUED.name,
            isAudioOnly = isAudioOnly,
            quality = quality,
            formatId = formatId,
            playlistId = playlistId,
            playlistTitle = playlistTitle,
            subLangs = subLangs,
        )
        return dao.insert(entity)
    }

    suspend fun createPlaceholder(url: String, source: VideoSource): Long {
        val entity = DownloadEntity(
            url = url,
            title = "Extracting video info...",
            thumbnail = null,
            source = source.name,
            status = DownloadStatus.EXTRACTING.name,
            quality = "Best",
            formatId = "bestvideo+bestaudio/best",
        )
        return dao.insert(entity)
    }

    suspend fun updateFromExtraction(
        id: Long, title: String, thumbnail: String?, source: VideoSource,
    ) {
        val entity = dao.getById(id) ?: return
        dao.update(entity.copy(
            title = title,
            thumbnail = thumbnail,
            source = source.name,
            status = DownloadStatus.QUEUED.name,
        ))
    }

    suspend fun deleteHistoryOnly(id: Long) {
        dao.delete(id)
    }

    suspend fun deleteWithFile(id: Long) {
        val entity = dao.getById(id)
        if (entity?.filePath != null) {
            try {
                val path = entity.filePath
                if (path.startsWith("content://")) {
                    context.contentResolver.delete(Uri.parse(path), null, null)
                } else {
                    File(path).delete()
                }
            } catch (_: Exception) { }
        }
        dao.delete(id)
    }

    suspend fun resetForRetry(id: Long) = dao.resetForRetry(id)

    suspend fun resetForResume(id: Long) = dao.resetForResume(id)

    suspend fun clearCompleted() = dao.clearCompleted()

    suspend fun markFailed(id: Long, error: String) = dao.markFailed(id, error)

    suspend fun setWaitingNetwork(id: Long) =
        dao.updateStatus(id, DownloadStatus.WAITING_NETWORK.name)

    // Safe Zone
    fun observeHidden(): Flow<List<Download>> =
        dao.observeHidden().map { list -> list.map { it.toDomain() } }

    suspend fun hideDownload(id: Long) = dao.hide(id)

    suspend fun unhideDownload(id: Long) = dao.unhide(id)

    // Stats
    suspend fun completedCount(): Int = dao.completedCount()
    suspend fun totalDownloadedBytes(): Long = dao.totalDownloadedBytes()
}
