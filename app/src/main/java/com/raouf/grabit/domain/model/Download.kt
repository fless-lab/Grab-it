package com.raouf.grabit.domain.model

data class Download(
    val id: Long = 0,
    val url: String,
    val title: String,
    val thumbnail: String?,
    val source: VideoSource,
    val status: DownloadStatus,
    val progress: Float = 0f,
    val filePath: String? = null,
    val fileSize: Long? = null,
    val isAudioOnly: Boolean = false,
    val quality: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val errorMessage: String? = null,
)
