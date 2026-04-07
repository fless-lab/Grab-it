package com.raouf.grabit.ui.playlist

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raouf.grabit.data.DownloadRepository
import com.raouf.grabit.data.downloader.VideoExtractor
import com.raouf.grabit.domain.model.PlaylistEntry
import com.raouf.grabit.domain.model.PlaylistInfo
import com.raouf.grabit.domain.model.VideoSource
import com.raouf.grabit.service.DownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistState(
    val isLoading: Boolean = true,
    val info: PlaylistInfo? = null,
    val error: String? = null,
    val selectedIds: Set<Int> = emptySet(), // indices
    val downloadStarted: Boolean = false,
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val extractor: VideoExtractor,
    private val repository: DownloadRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val url: String = run {
        val raw = savedStateHandle.get<String>("url") ?: ""
        try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (_: Exception) { raw }
    }

    private val _state = MutableStateFlow(PlaylistState())
    val state = _state.asStateFlow()

    init {
        extractPlaylist()
    }

    private fun extractPlaylist() {
        viewModelScope.launch {
            _state.value = PlaylistState(isLoading = true)
            try {
                val info = extractor.extractPlaylist(url)
                _state.value = PlaylistState(
                    isLoading = false,
                    info = info,
                    selectedIds = info.entries.indices.toSet(), // all selected by default
                )
            } catch (e: Exception) {
                _state.value = PlaylistState(
                    isLoading = false,
                    error = e.message ?: "Failed to extract playlist",
                )
            }
        }
    }

    fun toggleEntry(index: Int) {
        val current = _state.value.selectedIds.toMutableSet()
        if (index in current) current.remove(index) else current.add(index)
        _state.value = _state.value.copy(selectedIds = current)
    }

    fun selectAll() {
        val info = _state.value.info ?: return
        _state.value = _state.value.copy(selectedIds = info.entries.indices.toSet())
    }

    fun deselectAll() {
        _state.value = _state.value.copy(selectedIds = emptySet())
    }

    fun downloadSelected() {
        val info = _state.value.info ?: return
        val selected = _state.value.selectedIds
        if (selected.isEmpty()) return

        val source = VideoSource.fromUrl(url)

        viewModelScope.launch {
            for (index in selected.sorted()) {
                val entry = info.entries[index]
                val id = repository.createDownload(
                    url = entry.url,
                    title = entry.title,
                    thumbnail = entry.thumbnail,
                    source = source,
                    isAudioOnly = false,
                    quality = "Best",
                    formatId = "bestvideo+bestaudio/best",
                    playlistId = url,
                    playlistTitle = info.title,
                )
                DownloadService.startDownload(
                    context = appContext,
                    id = id,
                    url = entry.url,
                    title = entry.title,
                    source = source,
                    formatId = "bestvideo+bestaudio/best",
                    isAudioOnly = false,
                )
            }
            _state.value = _state.value.copy(downloadStarted = true)
        }
    }

    fun retry() = extractPlaylist()
}
