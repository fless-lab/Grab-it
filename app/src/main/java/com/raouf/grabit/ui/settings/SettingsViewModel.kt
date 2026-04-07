package com.raouf.grabit.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raouf.grabit.data.DownloadRepository
import com.raouf.grabit.data.prefs.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val repository: DownloadRepository,
) : ViewModel() {

    private val _totalDownloads = MutableStateFlow(0)
    val totalDownloads = _totalDownloads.asStateFlow()

    private val _totalBytes = MutableStateFlow(0L)
    val totalBytes = _totalBytes.asStateFlow()

    init {
        viewModelScope.launch {
            _totalDownloads.value = repository.completedCount()
            _totalBytes.value = repository.totalDownloadedBytes()
        }
    }

    val darkTheme = prefs.darkTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val quickMode = prefs.quickMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoSubfolder = prefs.autoSubfolder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val clipboardMonitor = prefs.clipboardMonitor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val downloadDir = prefs.downloadDirUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setDarkTheme(v: Boolean) { viewModelScope.launch { prefs.setDarkTheme(v) } }
    fun setQuickMode(v: Boolean) { viewModelScope.launch { prefs.setQuickMode(v) } }
    fun setAutoSubfolder(v: Boolean) { viewModelScope.launch { prefs.setAutoSubfolder(v) } }
    fun setClipboardMonitor(v: Boolean) { viewModelScope.launch { prefs.setClipboardMonitor(v) } }
    fun setDownloadDir(uri: String) { viewModelScope.launch { prefs.setDownloadDir(uri) } }

    val appLock = prefs.appLock
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val hideFromGallery = prefs.hideFromGallery
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val autoUpdate = prefs.autoUpdate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val wifiOnly = prefs.wifiOnly
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setAppLock(v: Boolean) { viewModelScope.launch { prefs.setAppLock(v) } }
    fun setHideFromGallery(v: Boolean) { viewModelScope.launch { prefs.setHideFromGallery(v) } }
    fun setAutoUpdate(v: Boolean) { viewModelScope.launch { prefs.setAutoUpdate(v) } }
    fun setWifiOnly(v: Boolean) { viewModelScope.launch { prefs.setWifiOnly(v) } }
}
