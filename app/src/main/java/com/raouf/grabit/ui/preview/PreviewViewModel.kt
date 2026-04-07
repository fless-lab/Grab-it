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
    val subtitlesEnabled: Boolean = false,
    val selectedSubLangs: Set<String> = emptySet(),
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

    private fun extractInfo(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _state.value = PreviewState(isLoading = true)
            try {
                val info = extractor.extract(url, forceRefresh = forceRefresh)
                _state.value = PreviewState(
                    isLoading = false,
                    info = info,
                    selectedFormat = info.formats.firstOrNull(),
                )
            } catch (e: Exception) {
                _state.value = PreviewState(
                    isLoading = false,
                    error = com.raouf.grabit.data.downloader.ErrorParser.friendlyMessage(e.message),
                )
            }
        }
    }

    fun selectFormat(format: VideoFormat) {
        _state.value = _state.value.copy(selectedFormat = format)
    }

    fun toggleSubtitles(enabled: Boolean) {
        _state.value = _state.value.copy(
            subtitlesEnabled = enabled,
            selectedSubLangs = if (enabled) {
                // Auto-select common languages if available
                val available = _state.value.info?.subtitleLanguages ?: emptyList()
                val defaults = listOf("en", "fr", "ar", "es")
                available.filter { it in defaults }.toSet().ifEmpty { available.take(3).toSet() }
            } else emptySet(),
        )
    }

    fun toggleSubLang(lang: String) {
        val current = _state.value.selectedSubLangs
        _state.value = _state.value.copy(
            selectedSubLangs = if (lang in current) current - lang else current + lang,
        )
    }

    fun startDownload() {
        val info = _state.value.info ?: return
        val format = _state.value.selectedFormat ?: return
        val subLangs = if (_state.value.subtitlesEnabled) _state.value.selectedSubLangs else emptySet()

        viewModelScope.launch {
            val subLangsStr = subLangs.joinToString(",").ifBlank { null }
            val id = repository.createDownload(
                url = info.url,
                title = info.title,
                thumbnail = info.thumbnail,
                source = info.source,
                isAudioOnly = format.isAudioOnly,
                quality = format.quality,
                formatId = format.formatId,
                subLangs = subLangsStr,
            )

            DownloadService.startDownload(
                context = appContext,
                id = id,
                url = info.url,
                title = info.title,
                source = info.source,
                formatId = format.formatId,
                isAudioOnly = format.isAudioOnly,
                subLangs = subLangs.joinToString(","),
            )

            _state.value = _state.value.copy(downloadStarted = true)
        }
    }

    fun retry() = extractInfo()

    fun refresh() = extractInfo(forceRefresh = true)
}
