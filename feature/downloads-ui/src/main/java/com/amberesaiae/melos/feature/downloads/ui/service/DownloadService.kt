@file:Suppress("kotlin:S6290")

package com.amberesaiae.melos.feature.downloads.ui.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.amberesaiae.melos.core.model.DownloadItem
import com.amberesaiae.melos.core.model.DownloadStatus
import com.amberesaiae.melos.feature.downloads.ui.R
import com.amberesaiae.melos.feature.downloads.ui.data.DownloadRepository
import com.amberesaiae.melos.feature.downloads.ui.ui.DownloadsActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService : LifecycleService() {

    companion object {
        private const val TAG = "DownloadService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "download_channel"
        private const val CHANNEL_NAME = "Downloads"
        private const val MAX_PARALLEL_DOWNLOADS = 3
        private const val BUFFER_SIZE = 8192

        fun getStartIntent(context: Context): Intent {
            return Intent(context, DownloadService::class.java)
        }
    }

    @Inject
    lateinit var downloadRepository: DownloadRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadMutex = Mutex()
    private val activeDownloads = mutableMapOf<String, Job>()

    private val _serviceState = MutableStateFlow(DownloadServiceState())
    val serviceState: StateFlow<DownloadServiceState> = _serviceState.asStateFlow()

    private var notificationManager: NotificationManager? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        observeDownloads()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForeground(NOTIFICATION_ID, createNotification(0, 0, 0))
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        activeDownloads.values.forEach { it.cancel() }
        activeDownloads.clear()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Download progress notifications"
                setShowBadge(false)
            }
            notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(downloaded: Int, total: Int, progressPercent: Int): android.app.Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, DownloadsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading...")
            .setContentText("$downloaded of $total items ($progressPercent%)")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setProgress(100, progressPercent, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun observeDownloads() {
        serviceScope.launch {
            downloadRepository.getPendingDownloads().collectLatest { pendingItems ->
                downloadMutex.withLock {
                    pendingItems
                        .filter { it.id !in activeDownloads }
                        .take(MAX_PARALLEL_DOWNLOADS - activeDownloads.size)
                        .forEach { downloadItem ->
                            activeDownloads[downloadItem.id] = launch {
                                executeDownload(downloadItem)
                            }
                        }
                }
            }
        }
    }

    private suspend fun executeDownload(downloadItem: DownloadItem) {
        try {
            updateDownloadStatus(downloadItem.id, DownloadStatus.DOWNLOADING)
            val url = URL(downloadItem.url)
            val connection = url.openConnection() as HttpURLConnection

            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 30000
                connection.readTimeout = 30000

                val contentLength = connection.contentLength
                updateDownloadProgress(downloadItem.id, 0, contentLength)

                val outputFile = File(getDownloadDirectory(), "${downloadItem.id}.m4a")
                FileOutputStream(outputFile).use { outputStream ->
                    connection.inputStream.use { inputStream ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var downloadedBytes = 0L

                        while (true) {
                            val bytesRead = inputStream.read(buffer)
                            if (bytesRead == -1) break

                            outputStream.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            updateDownloadProgress(downloadItem.id, downloadedBytes, contentLength)
                        }
                    }
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    updateDownloadStatus(downloadItem.id, DownloadStatus.COMPLETED)
                    onDownloadCompleted(downloadItem, outputFile)
                } else {
                    updateDownloadStatus(downloadItem.id, DownloadStatus.FAILED)
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            updateDownloadStatus(downloadItem.id, DownloadStatus.FAILED)
        } finally {
            downloadMutex.withLock {
                activeDownloads.remove(downloadItem.id)
            }
        }
    }

    private suspend fun updateDownloadStatus(id: String, status: DownloadStatus) {
        downloadRepository.updateDownloadStatus(id, status)
        updateNotification()
    }

    private suspend fun updateDownloadProgress(id: String, downloaded: Long, total: Long) {
        downloadRepository.updateDownloadProgress(id, downloaded, total)
        updateNotification()
    }

    private suspend fun updateNotification() {
        val active = downloadRepository.getActiveDownloads()
        val (downloaded, total, progress) = calculateProgress(active)
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading...")
            .setContentText("$downloaded of $total items ($progress%)")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .build()
            .let { notificationManager?.notify(NOTIFICATION_ID, it) }
    }

    private fun calculateProgress(downloads: List<DownloadItem>): Triple<Int, Int, Int> {
        val total = downloads.size
        val completed = downloads.count { it.status == DownloadStatus.COMPLETED }
        val progress = if (total > 0) {
            (downloads.sumOf { it.progress } / total).toInt()
        } else 0
        return Triple(completed, total, progress)
    }

    private fun onDownloadCompleted(item: DownloadItem, file: File) {
        serviceScope.launch {
            downloadRepository.markAsCompleted(item.id, file.absolutePath)
        }
    }

    private fun getDownloadDirectory(): File {
        val dir = File(getExternalFilesDir(null), "downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun pauseDownload(downloadId: String) {
        serviceScope.launch {
            downloadMutex.withLock {
                activeDownloads[downloadId]?.cancel()
                activeDownloads.remove(downloadId)
                updateDownloadStatus(downloadId, DownloadStatus.PAUSED)
            }
        }
    }

    fun resumeDownload(downloadId: String) {
        serviceScope.launch {
            downloadMutex.withLock {
                if (downloadId !in activeDownloads && activeDownloads.size < MAX_PARALLEL_DOWNLOADS) {
                    val item = downloadRepository.getDownload(downloadId)
                    item?.let {
                        activeDownloads[downloadId] = launch {
                            executeDownload(it)
                        }
                    }
                }
            }
        }
    }

    fun cancelDownload(downloadId: String) {
        serviceScope.launch {
            downloadMutex.withLock {
                activeDownloads[downloadId]?.cancel()
                activeDownloads.remove(downloadId)
                updateDownloadStatus(downloadId, DownloadStatus.CANCELLED)
                File(getDownloadDirectory(), "$downloadId.m4a").delete()
            }
        }
    }

    fun pauseAllDownloads() {
        serviceScope.launch {
            downloadMutex.withLock {
                activeDownloads.values.forEach { it.cancel() }
                activeDownloads.clear()
                downloadRepository.pauseAllActiveDownloads()
            }
        }
    }

    fun resumePendingDownloads() {
        serviceScope.launch {
            observeDownloads()
        }
    }
}

data class DownloadServiceState(
    val isDownloading: Boolean = false,
    val activeDownloadCount: Int = 0,
    val overallProgress: Int = 0,
    val totalDownloaded: Long = 0,
    val totalBytes: Long = 0
)
