package com.raouf.grabit.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raouf.grabit.data.DownloadRepository
import com.raouf.grabit.domain.model.Download
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HomeTab { ALL, ACTIVE, COMPLETED }

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: DownloadRepository,
) : ViewModel() {

    private val _tab = MutableStateFlow(HomeTab.ALL)
    val tab = _tab.asStateFlow()

    val allDownloads = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeDownloads = repository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedDownloads = repository.observeCompleted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setTab(tab: HomeTab) { _tab.value = tab }

    fun deleteDownload(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun clearCompleted() {
        viewModelScope.launch { repository.clearCompleted() }
    }
}
