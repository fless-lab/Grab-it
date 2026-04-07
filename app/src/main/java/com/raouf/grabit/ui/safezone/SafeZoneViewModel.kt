package com.raouf.grabit.ui.safezone

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raouf.grabit.data.DownloadRepository
import com.raouf.grabit.domain.model.Download
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SafeZoneViewModel @Inject constructor(
    private val repository: DownloadRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    val hiddenDownloads = repository.observeHidden()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun unhide(id: Long) {
        viewModelScope.launch { repository.unhideDownload(id) }
    }

    fun delete(id: Long, deleteFile: Boolean) {
        viewModelScope.launch {
            if (deleteFile) repository.deleteWithFile(id)
            else repository.deleteHistoryOnly(id)
        }
    }
}
