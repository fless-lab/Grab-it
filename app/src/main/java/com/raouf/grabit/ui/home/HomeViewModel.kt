package com.raouf.grabit.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raouf.grabit.data.DownloadRepository
import com.raouf.grabit.data.downloader.GrabitDownloadManager
import com.raouf.grabit.data.downloader.VideoExtractor
import com.raouf.grabit.data.prefs.UserPreferences
import com.raouf.grabit.domain.model.Download
import com.raouf.grabit.domain.model.DownloadStatus
import com.raouf.grabit.service.DownloadService
import kotlinx.coroutines.flow.first
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HomeTab { ALL, ACTIVE, COMPLETED }

sealed class DownloadItem {
    data class Single(val download: Download) : DownloadItem()
    data class PlaylistGroup(
        val playlistId: String,
        val title: String,
        val thumbnail: String?,
        val downloads: List<Download>,
    ) : DownloadItem() {
        val totalCount get() = downloads.size
        val completedCount get() = downloads.count { it.status == DownloadStatus.COMPLETED }
        val activeCount get() = downloads.count {
            it.status in listOf(DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED, DownloadStatus.EXTRACTING)
        }
        val overallProgress get(): Float {
            if (downloads.isEmpty()) return 0f
            return downloads.sumOf { it.progress.toDouble() }.toFloat() / downloads.size
        }
        val isAllCompleted get() = completedCount == totalCount
        val latestCreatedAt get() = downloads.maxOfOrNull { it.createdAt } ?: 0L
    }
}

sealed class QuickDownloadEvent {
    data object Extracting : QuickDownloadEvent()
    data class Started(val title: String) : QuickDownloadEvent()
    data class Failed(val error: String) : QuickDownloadEvent()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: DownloadRepository,
    private val downloadManager: GrabitDownloadManager,
    private val extractor: VideoExtractor,
    private val prefs: UserPreferences,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _tab = MutableStateFlow(HomeTab.ALL)
    val tab = _tab.asStateFlow()

    val quickMode = prefs.quickMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _quickEvent = MutableSharedFlow<QuickDownloadEvent>()
    val quickEvent = _quickEvent.asSharedFlow()

    private val allDownloadsRaw = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val activeDownloadsRaw = repository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val completedDownloadsRaw = repository.observeCompleted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Grouped items: singles + playlist groups */
    val allItems = allDownloadsRaw.map { groupDownloads(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeItems = activeDownloadsRaw.map { groupDownloads(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedItems = completedDownloadsRaw.map { groupDownloads(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val progressMap = downloadManager.activeProgress

    private fun groupDownloads(downloads: List<Download>): List<DownloadItem> {
        val singles = mutableListOf<DownloadItem>()
        val playlistMap = mutableMapOf<String, MutableList<Download>>()

        for (dl in downloads) {
            if (dl.playlistId != null) {
                playlistMap.getOrPut(dl.playlistId) { mutableListOf() }.add(dl)
            } else {
                singles.add(DownloadItem.Single(dl))
            }
        }

        val playlists = playlistMap.map { (id, dls) ->
            DownloadItem.PlaylistGroup(
                playlistId = id,
                title = dls.firstOrNull()?.playlistTitle ?: "Playlist",
                thumbnail = dls.firstOrNull()?.thumbnail,
                downloads = dls,
            )
        }

        // Merge and sort by latest timestamp
        return (singles + playlists).sortedByDescending { item ->
            when (item) {
                is DownloadItem.Single -> item.download.createdAt
                is DownloadItem.PlaylistGroup -> item.latestCreatedAt
            }
        }
    }

    fun setTab(tab: HomeTab) { _tab.value = tab }

    fun deleteDownload(id: Long, deleteFile: Boolean = false) {
        viewModelScope.launch {
            if (deleteFile) {
                repository.deleteWithFile(id)
            } else {
                repository.deleteHistoryOnly(id)
            }
        }
    }

    fun deletePlaylistGroup(downloads: List<Download>, deleteFiles: Boolean = false) {
        viewModelScope.launch {
            for (dl in downloads) {
                if (deleteFiles) repository.deleteWithFile(dl.id)
                else repository.deleteHistoryOnly(dl.id)
            }
        }
    }

    fun clearCompleted() {
        viewModelScope.launch { repository.clearCompleted() }
    }

    fun pauseDownload(id: Long) {
        DownloadService.pauseDownload(appContext, id)
    }

    fun resumeDownload(download: Download) {
        viewModelScope.launch {
            repository.resetForResume(download.id) // PAUSED -> QUEUED without resetting progress
            DownloadService.startDownload(
                context = appContext,
                id = download.id,
                url = download.url,
                title = download.title,
                source = download.source,
                formatId = download.formatId,
                isAudioOnly = download.isAudioOnly,
            )
        }
    }

    fun retryDownload(download: Download) {
        viewModelScope.launch {
            repository.resetForRetry(download.id)
            DownloadService.startDownload(
                context = appContext,
                id = download.id,
                url = download.url,
                title = download.title,
                source = download.source,
                formatId = download.formatId,
                isAudioOnly = download.isAudioOnly,
            )
        }
    }

    suspend fun isQuickModeEnabled(): Boolean = prefs.quickMode.first()

    fun hideDownload(id: Long) {
        viewModelScope.launch { repository.hideDownload(id) }
    }

    fun quickDownload(url: String) {
        viewModelScope.launch {
            _quickEvent.emit(QuickDownloadEvent.Extracting)

            // Try extraction up to 3 times (handles flaky networks)
            var lastError: Exception? = null
            for (attempt in 1..3) {
                try {
                    val info = extractor.extract(url, forceRefresh = attempt > 1)
                    val id = repository.createDownload(
                        url = info.url,
                        title = info.title,
                        thumbnail = info.thumbnail,
                        source = info.source,
                        isAudioOnly = false,
                        quality = "Best",
                        formatId = "bestvideo+bestaudio/best",
                    )
                    DownloadService.startDownload(
                        context = appContext,
                        id = id,
                        url = info.url,
                        title = info.title,
                        source = info.source,
                        formatId = "bestvideo+bestaudio/best",
                        isAudioOnly = false,
                    )
                    _quickEvent.emit(QuickDownloadEvent.Started(info.title))
                    return@launch
                } catch (e: Exception) {
                    lastError = e
                    if (attempt < 3) kotlinx.coroutines.delay(3000)
                }
            }

            // All 3 attempts failed
            _quickEvent.emit(QuickDownloadEvent.Failed(
                com.raouf.grabit.data.downloader.ErrorParser.friendlyMessage(lastError?.message)
            ))
        }
    }
}
