@file:Suppress("kotlin:S6290", "kotlin:S6701")

package com.amberesaiae.melos.feature.downloads.ui.data

import android.content.Context
import android.content.SharedPreferences
import com.amberesaiae.melos.core.model.DownloadItem
import com.amberesaiae.melos.core.model.DownloadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : DownloadRepository {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "download_prefs",
        Context.MODE_PRIVATE
    )

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    private val downloadsState: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    init {
        loadDownloads()
    }

    private fun loadDownloads() {
        val downloadsJson = prefs.getString("downloads", null)
        if (downloadsJson != null) {
            try {
                val downloads = json.decodeFromString<List<DownloadItem>>(downloadsJson)
                _downloads.value = downloads
            } catch (e: Exception) {
                _downloads.value = emptyList()
            }
        }
    }

    private fun saveDownloads() {
        val downloadsJson = json.encodeToString(_downloads.value)
        prefs.edit().putString("downloads", downloadsJson).apply()
    }

    override fun getPendingDownloads(): Flow<List<DownloadItem>> {
        return downloadsState.map { downloads ->
            downloads.filter { it.status in listOf(
                DownloadStatus.QUEUED,
                DownloadStatus.DOWNLOADING,
                DownloadStatus.PAUSED
            ) }
        }
    }

    override suspend fun getActiveDownloads(): List<DownloadItem> {
        return _downloads.value.filter { it.status.isActive() }
    }

    override suspend fun getCompletedDownloads(): List<DownloadItem> {
        return _downloads.value.filter { it.status == DownloadStatus.COMPLETED }
    }

    override suspend fun getDownload(id: String): DownloadItem? {
        return _downloads.value.find { it.id == id }
    }

    override suspend fun updateDownloadStatus(id: String, status: DownloadStatus) {
        _downloads.update { downloads ->
            downloads.map { download ->
                if (download.id == id) {
                    download.copy(status = status)
                } else {
                    download
                }
            }
        }
        saveDownloads()
    }

    override suspend fun updateDownloadProgress(id: String, downloaded: Long, total: Long) {
        _downloads.update { downloads ->
            downloads.map { download ->
                if (download.id == id) {
                    download.copy(
                        downloadedBytes = downloaded,
                        totalBytes = total
                    )
                } else {
                    download
                }
            }
        }
    }

    override suspend fun markAsCompleted(id: String, filePath: String) {
        _downloads.update { downloads ->
            downloads.map { download ->
                if (download.id == id) {
                    download.copy(
                        status = DownloadStatus.COMPLETED,
                        filePath = filePath,
                        completedAt = System.currentTimeMillis()
                    )
                } else {
                    download
                }
            }
        }
        saveDownloads()
    }

    override suspend fun pauseAllActiveDownloads() {
        _downloads.update { downloads ->
            downloads.map { download ->
                if (download.status.isActive() && download.status != DownloadStatus.PAUSED) {
                    download.copy(status = DownloadStatus.PAUSED)
                } else {
                    download
                }
            }
        }
        saveDownloads()
    }

    override suspend fun addDownload(item: DownloadItem) {
        _downloads.update { downloads ->
            if (downloads.any { it.id == item.id }) {
                downloads
            } else {
                downloads + item
            }
        }
        saveDownloads()
    }
}

private fun DownloadStatus.isActive(): Boolean {
    return this in listOf(
        DownloadStatus.QUEUED,
        DownloadStatus.DOWNLOADING,
        DownloadStatus.PAUSED
    )
}
