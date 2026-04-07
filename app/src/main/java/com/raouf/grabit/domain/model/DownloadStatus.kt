package com.raouf.grabit.domain.model

enum class DownloadStatus {
    EXTRACTING,
    QUEUED,
    DOWNLOADING,
    PAUSED,
    WAITING_NETWORK,
    COMPLETED,
    FAILED
}
