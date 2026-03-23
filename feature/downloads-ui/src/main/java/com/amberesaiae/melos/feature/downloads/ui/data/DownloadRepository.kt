@file:Suppress("kotlin:S6290")

package com.amberesaiae.melos.feature.downloads.ui.data

import com.amberesaiae.melos.core.model.DownloadItem
import com.amberesaiae.melos.core.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun getPendingDownloads(): Flow<List<DownloadItem>>
    suspend fun getActiveDownloads(): List<DownloadItem>
    suspend fun getCompletedDownloads(): List<DownloadItem>
    suspend fun getDownload(id: String): DownloadItem?
    suspend fun updateDownloadStatus(id: String, status: DownloadStatus)
    suspend fun updateDownloadProgress(id: String, downloaded: Long, total: Long)
    suspend fun markAsCompleted(id: String, filePath: String)
    suspend fun pauseAllActiveDownloads()
    suspend fun addDownload(item: DownloadItem)
}
