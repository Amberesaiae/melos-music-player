@file:Suppress("kotlin:S6290")

package com.amberesaiae.melos.core.model

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class DownloadItem(
    val id: String,
    val title: String,
    val artist: String?,
    val albumArtUrl: android.net.Uri?,
    val url: String,
    val status: DownloadStatus,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val filePath: String?,
    val createdAt: Long,
    val completedAt: Long?
) {
    val progress: Int
        get() = if (totalBytes > 0) {
            ((downloadedBytes.toFloat() / totalBytes) * 100).toInt()
        } else 0
}
