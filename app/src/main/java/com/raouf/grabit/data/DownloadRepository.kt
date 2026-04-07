package com.raouf.grabit.data

import com.raouf.grabit.data.db.DownloadDao
import com.raouf.grabit.data.db.DownloadEntity
import com.raouf.grabit.domain.model.Download
import com.raouf.grabit.domain.model.DownloadStatus
import com.raouf.grabit.domain.model.VideoSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    private val dao: DownloadDao,
) {
    fun observeAll(): Flow<List<Download>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeActive(): Flow<List<Download>> =
        dao.observeActive().map { list -> list.map { it.toDomain() } }

    fun observeCompleted(): Flow<List<Download>> =
        dao.observeCompleted().map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: Long): Download? = dao.getById(id)?.toDomain()

    suspend fun createDownload(
        url: String,
        title: String,
        thumbnail: String?,
        source: VideoSource,
        isAudioOnly: Boolean,
        quality: String,
    ): Long {
        val entity = DownloadEntity(
            url = url,
            title = title,
            thumbnail = thumbnail,
            source = source.name,
            status = DownloadStatus.QUEUED.name,
            isAudioOnly = isAudioOnly,
            quality = quality,
        )
        return dao.insert(entity)
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun clearCompleted() = dao.clearCompleted()

    suspend fun markFailed(id: Long, error: String) = dao.markFailed(id, error)
}
