@file:Suppress("kotlin:S6290", "kotlin:S6701", "kotlin:S1511")

package com.amberesaiae.melos.core.cache

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cache_settings")

/**
 * Manager for cache operations including download management, cleanup, and monitoring.
 */
@Singleton
class CacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cacheRepository: CacheRepository,
    private val cacheDao: CacheDao
) {
    companion object {
        private const val TAG = "CacheManager"
        private const val MAX_CACHE_SIZE_MB = 500L
        private const val LOW_STORAGE_THRESHOLD_MB = 100L
        private const val CLEANUP_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    private val cacheDir by lazy {
        context.externalCacheDir ?: context.cacheDir
    }

    private val musicCacheDir by lazy {
        cacheDir.resolve("music").apply { mkdirs() }
    }

    private val artworkCacheDir by lazy {
        cacheDir.resolve("artwork").apply { mkdirs() }
    }

    // ==================== Storage Paths ====================

    fun getSongCachePath(songId: String): String {
        return musicCacheDir.resolve("$songId.m4a").absolutePath
    }

    fun getArtworkCachePath(songId: String): String {
        return artworkCacheDir.resolve("$songId.jpg").absolutePath
    }

    fun getAlbumArtworkPath(albumId: String): String {
        return artworkCacheDir.resolve("album_$albumId.jpg").absolutePath
    }

    // ==================== Cache Statistics ====================

    suspend fun getCacheStatistics(): CacheStatistics {
        val totalSize = cacheRepository.getCacheSize()
        val songCount = cacheRepository.getCachedSongCount()
        val availableSpace = getAvailableStorageSpace()
        val cacheSpace = getCacheDirectorySize()

        return CacheStatistics(
            totalCachedSize = totalSize,
            cachedSongCount = songCount,
            availableStorageSpace = availableSpace,
            cacheDirectorySize = cacheSpace,
            maxCacheSize = MAX_CACHE_SIZE_MB * 1024 * 1024,
            isCacheFull = totalSize >= MAX_CACHE_SIZE_MB * 1024 * 1024,
            isStorageLow = availableSpace < LOW_STORAGE_THRESHOLD_MB * 1024 * 1024
        )
    }

    suspend fun getAvailableStorageSpace(): Long {
        return try {
            val statFs = StatFs(Environment.getExternalStorageDirectory().path)
            statFs.availableBytes
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available storage", e)
            0L
        }
    }

    private fun getCacheDirectorySize(): Long {
        return cacheDir.walk().filter { it.isFile }
            .sumOf { it.length() }
    }

    // ==================== Download Management ====================

    suspend fun startSongDownload(songId: String, serverId: String, serverType: String) {
        val queueItem = cacheDao.getDownloadQueueItemBySongId(songId)
        if (queueItem != null) {
            cacheDao.updateDownloadQueueItem(
                queueItem.copy(
                    status = DownloadStatus.DOWNLOADING,
                    startedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun completeSongDownload(songId: String, fileSize: Long, localPath: String) {
        val queueItem = cacheDao.getDownloadQueueItemBySongId(songId)
        if (queueItem != null) {
            // Update queue item
            cacheDao.updateDownloadQueueItem(
                queueItem.copy(
                    status = DownloadStatus.COMPLETED,
                    progress = 100,
                    completedAt = System.currentTimeMillis(),
                    fileSize = fileSize,
                    localPath = localPath
                )
            )

            // Update cached song with file size
            cacheDao.updateFileSize(songId, fileSize)

            // Clean up completed queue items periodically
            cacheDao.deleteCompletedQueueItems()
        }
    }

    suspend fun failSongDownload(songId: String, errorMessage: String) {
        val queueItem = cacheDao.getDownloadQueueItemBySongId(songId)
        if (queueItem != null) {
            val retryCount = queueItem.retryCount + 1
            val newStatus = if (retryCount >= 3) DownloadStatus.FAILED else DownloadStatus.PENDING

            cacheDao.updateDownloadQueueItem(
                queueItem.copy(
                    status = newStatus,
                    retryCount = retryCount,
                    errorMessage = errorMessage
                )
            )
        }
    }

    suspend fun cancelSongDownload(songId: String) {
        val queueItem = cacheDao.getDownloadQueueItemBySongId(songId)
        if (queueItem != null) {
            cacheDao.updateDownloadQueueItem(
                queueItem.copy(
                    status = DownloadStatus.CANCELLED
                )
            )
        }
    }

    suspend fun updateDownloadProgress(songId: String, progress: Int) {
        val queueItem = cacheDao.getDownloadQueueItemBySongId(songId)
        if (queueItem != null) {
            cacheDao.updateDownloadQueueItem(
                queueItem.copy(
                    progress = progress
                )
            )
        }
    }

    // ==================== Automatic Cache Cleanup ====================

    suspend fun performCacheCleanup() {
        Log.d(TAG, "Performing cache cleanup")

        try {
            // Delete expired cache
            val deletedCount = cacheRepository.deleteExpiredCache()
            Log.d(TAG, "Deleted $deletedCount expired cache entries")

            // Check if cache is over capacity
            val cacheSize = cacheRepository.getCacheSize()
            val maxSize = MAX_CACHE_SIZE_MB * 1024 * 1024

            if (cacheSize > maxSize * 0.9) { // 90% full
                Log.d(TAG, "Cache is 90% full, triggering eviction")
                cacheRepository.evictLeastRecentlyUsed(20)
            }

            // Update last cleanup time
            val metadata = cacheDao.getCacheMetadata()
            if (metadata != null) {
                cacheDao.updateCacheMetadata(
                    metadata.copy(
                        lastCleanup = System.currentTimeMillis()
                    )
                )
            } else {
                cacheDao.insertCacheMetadata(
                    CacheMetadata(lastCleanup = System.currentTimeMillis())
                )
            }

            Log.d(TAG, "Cache cleanup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cache cleanup", e)
        }
    }

    suspend fun shouldPerformCleanup(): Boolean {
        val metadata = cacheDao.getCacheMetadata()
        return metadata?.lastCleanup?.let { lastCleanup ->
            System.currentTimeMillis() - lastCleanup > CLEANUP_INTERVAL_MS
        } ?: true
    }

    // ==================== Storage Monitoring ====================

    suspend fun checkStorageStatus(): StorageStatus {
        val availableSpace = getAvailableStorageSpace()
        val lowStorageThreshold = LOW_STORAGE_THRESHOLD_MB * 1024 * 1024

        return when {
            availableSpace < lowStorageThreshold * 0.1 -> StorageStatus.CRITICAL
            availableSpace < lowStorageThreshold -> StorageStatus.LOW
            else -> StorageStatus.NORMAL
        }
    }

    suspend fun getStorageStatusFlow(): Flow<StorageStatus> {
        // This would ideally use WorkManager or similar for periodic updates
        // For now, return a simple flow
        return kotlinx.coroutines.flow.flowOf(checkStorageStatus())
    }

    // ==================== Cache Settings ====================

    suspend fun setMaxCacheSize(sizeMb: Long) {
        context.dataStore.edit { preferences ->
            preferences[MAX_CACHE_SIZE_KEY] = sizeMb
        }
    }

    fun getMaxCacheSizeFlow(): Flow<Long> {
        return context.dataStore.data.map { preferences ->
            preferences[MAX_CACHE_SIZE_KEY] ?: MAX_CACHE_SIZE_MB
        }
    }

    suspend fun clearAllCache() {
        Log.d(TAG, "Clearing all cache")

        // Clear database
        cacheRepository.clearCache()

        // Clear files
        try {
            musicCacheDir.deleteRecursively()
            artworkCacheDir.deleteRecursively()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache files", e)
        }

        Log.d(TAG, "All cache cleared")
    }

    suspend fun deleteFile(path: String): Boolean {
        return try {
            val file = java.io.File(path)
            file.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file: $path", e)
            false
        }
    }
}

/**
 * Cache statistics data class.
 */
data class CacheStatistics(
    val totalCachedSize: Long,
    val cachedSongCount: Int,
    val availableStorageSpace: Long,
    val cacheDirectorySize: Long,
    val maxCacheSize: Long,
    val isCacheFull: Boolean,
    val isStorageLow: Boolean
) {
    val cacheUsagePercent: Float
        get() = (totalCachedSize.toFloat() / maxCacheSize.toFloat()) * 100f
}

/**
 * Storage status enum.
 */
enum class StorageStatus {
    NORMAL,
    LOW,
    CRITICAL
}

/**
 * Preferences keys.
 */
val MAX_CACHE_SIZE_KEY = longPreferencesKey("max_cache_size_mb")
val LAST_CLEANUP_KEY = longPreferencesKey("last_cleanup_time")
val CACHE_ENABLED_KEY = stringPreferencesKey("cache_enabled")
