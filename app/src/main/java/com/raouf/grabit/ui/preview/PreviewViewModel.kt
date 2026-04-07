package com.raouf.grabit.ui.preview

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raouf.grabit.data.DownloadRepository
import com.raouf.grabit.data.downloader.VideoExtractor
import com.raouf.grabit.domain.model.VideoFormat
import com.raouf.grabit.domain.model.VideoInfo
import com.raouf.grabit.domain.model.VideoSource
import com.raouf.grabit.service.DownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PreviewState(
    val isLoading: Boolean = true,
    val info: VideoInfo? = null,
    val error: String? = null,
    val selectedFormat: VideoFormat? = null,
    val downloadStarted: Boolean = false,
)

@HiltViewModel
class PreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val extractor: VideoExtractor,
    private val repository: DownloadRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val url: String = run {
        val raw = savedStateHandle.get<String>("url") ?: ""
        try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (_: Exception) { raw }
    }

    private val _state = MutableStateFlow(PreviewState())
    val state = _state.asStateFlow()

    init {
        extractInfo()
    }

    private fun extractInfo() {
        viewModelScope.launch {
            _state.value = PreviewState(isLoading = true)
            try {
                val info = extractor.extract(url)
                _state.value = PreviewState(
                    isLoading = false,
                    info = info,
                    selectedFormat = info.formats.firstOrNull(),
                )
            } catch (e: Exception) {
                _state.value = PreviewState(
                    isLoading = false,
                    error = e.message ?: "Failed to extract video info",
                )
            }
        }
    }

    fun selectFormat(format: VideoFormat) {
        _state.value = _state.value.copy(selectedFormat = format)
    }

    fun startDownload() {
        val info = _state.value.info ?: return
        val format = _state.value.selectedFormat ?: return

        viewModelScope.launch {
            val id = repository.createDownload(
                url = info.url,
                title = info.title,
                thumbnail = info.thumbnail,
                source = info.source,
                isAudioOnly = format.isAudioOnly,
                quality = format.quality,
            )

            DownloadService.startDownload(
                context = appContext,
                id = id,
                url = info.url,
                title = info.title,
                source = info.source,
                formatId = format.formatId,
                isAudioOnly = format.isAudioOnly,
            )

            _state.value = _state.value.copy(downloadStarted = true)
        }
    }

    fun retry() = extractInfo()
}
