@file:Suppress("kotlin:S6290")

package com.amberesaiae.melos.feature.downloads.ui.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amberesaiae.melos.core.model.DownloadItem
import com.amberesaiae.melos.core.model.DownloadStatus
import com.amberesaiae.melos.feature.downloads.ui.data.DownloadRepository
import com.amberesaiae.melos.feature.downloads.ui.service.DownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    private var downloadService: DownloadService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as DownloadService.LocalBinder
            downloadService = binder.getService()
            serviceBound = true
            observeServiceState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            downloadService = null
            serviceBound = false
        }
    }

    fun loadDownloads() {
        viewModelScope.launch {
            downloadRepository.getPendingDownloads()
                .combine(
                    downloadRepository.getCompletedDownloads()
                ) { pending, completed ->
                    DownloadsUiState(
                        activeDownloads = pending.filter { it.status.isActive() },
                        completedDownloads = completed
                    )
                }
                .collect { state ->
                    _uiState.update { state }
                }
        }
    }

    private fun observeServiceState() {
        viewModelScope.launch {
            downloadService?.serviceState?.collect { serviceState ->
                _uiState.update { current ->
                    current.copy(
                        isDownloading = serviceState.isDownloading,
                        overallProgress = serviceState.overallProgress
                    )
                }
            }
        }
    }

    fun pauseDownload(downloadId: String) {
        downloadService?.pauseDownload(downloadId)
    }

    fun resumeDownload(downloadId: String) {
        downloadService?.resumeDownload(downloadId)
    }

    fun cancelDownload(downloadId: String) {
        downloadService?.cancelDownload(downloadId)
    }

    fun pauseAllDownloads() {
        downloadService?.pauseAllDownloads()
    }

    fun resumeAllDownloads() {
        downloadService?.resumePendingDownloads()
    }

    fun removeCompletedDownload(downloadId: String) {
        viewModelScope.launch {
            // Implementation depends on repository
        }
    }

    fun bindService(context: Context) {
        val intent = DownloadService.getStartIntent(context)
        context.startService(intent)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun unbindService(context: Context) {
        if (serviceBound) {
            context.unbindService(serviceConnection)
            serviceBound = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Service unbinding handled by Activity/Fragment
    }
}

data class DownloadsUiState(
    val activeDownloads: List<DownloadItem> = emptyList(),
    val completedDownloads: List<DownloadItem> = emptyList(),
    val isDownloading: Boolean = false,
    val overallProgress: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null
)

private fun DownloadStatus.isActive(): Boolean {
    return this in listOf(
        DownloadStatus.QUEUED,
        DownloadStatus.DOWNLOADING,
        DownloadStatus.PAUSED
    )
}
