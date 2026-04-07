package com.raouf.grabit.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.raouf.grabit.domain.model.Download
import com.raouf.grabit.domain.model.DownloadStatus
import com.raouf.grabit.domain.model.VideoSource

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val title: String,
    val thumbnail: String?,
    val source: String,
    val status: String,
    val progress: Float = 0f,
    val filePath: String? = null,
    val fileSize: Long? = null,
    val isAudioOnly: Boolean = false,
    val quality: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val errorMessage: String? = null,
) {
    fun toDomain(): Download = Download(
        id = id,
        url = url,
        title = title,
        thumbnail = thumbnail,
        source = try { VideoSource.valueOf(source) } catch (_: Exception) { VideoSource.OTHER },
        status = try { DownloadStatus.valueOf(status) } catch (_: Exception) { DownloadStatus.FAILED },
        progress = progress,
        filePath = filePath,
        fileSize = fileSize,
        isAudioOnly = isAudioOnly,
        quality = quality,
        createdAt = createdAt,
        completedAt = completedAt,
        errorMessage = errorMessage,
    )

    companion object {
        fun fromDomain(d: Download) = DownloadEntity(
            id = d.id,
            url = d.url,
            title = d.title,
            thumbnail = d.thumbnail,
            source = d.source.name,
            status = d.status.name,
            progress = d.progress,
            filePath = d.filePath,
            fileSize = d.fileSize,
            isAudioOnly = d.isAudioOnly,
            quality = d.quality,
            createdAt = d.createdAt,
            completedAt = d.completedAt,
            errorMessage = d.errorMessage,
        )
    }
}
