package com.raouf.grabit.ui.playlist

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raouf.grabit.data.DownloadRepository
import com.raouf.grabit.data.downloader.GrabitDownloadManager
import com.raouf.grabit.domain.model.Download
import com.raouf.grabit.service.DownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DownloadRepository,
    private val downloadManager: GrabitDownloadManager,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    val playlistId: String = run {
        val raw = savedStateHandle.get<String>("playlistId") ?: ""
        try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (_: Exception) { raw }
    }

    val downloads = repository.observeAll()
        .map { all -> all.filter { it.playlistId == playlistId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlistTitle = downloads.map { list ->
        list.firstOrNull()?.playlistTitle ?: "Playlist"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Playlist")

    val progressMap = downloadManager.activeProgress

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

    fun deleteDownload(id: Long, deleteFile: Boolean) {
        viewModelScope.launch {
            if (deleteFile) repository.deleteWithFile(id)
            else repository.deleteHistoryOnly(id)
        }
    }
}
