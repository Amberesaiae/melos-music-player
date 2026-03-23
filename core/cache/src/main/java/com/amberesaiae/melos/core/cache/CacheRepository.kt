@file:Suppress("kotlin:S6290", "kotlin:S6701")

package com.amberesaiae.melos.core.cache

import android.content.Context
import android.util.LruCache
import com.amberesaiae.melos.core.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing cache operations with intelligent policies.
 */
@Singleton
class CacheRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cacheDao: CacheDao
) {
    companion object {
        private const val MAX_CACHE_SIZE_MB = 500L
        private const val MAX_CACHE_SIZE_BYTES = MAX_CACHE_SIZE_MB * 1024 * 1024
        private const val EVICTION_BATCH_SIZE = 10
        private const val PREFETCH_THRESHOLD = 5
    }

    private val songCache = LruCache<String, CachedSong>(50)
    private val albumCache = LruCache<String, CachedAlbum>(20)
    private val databaseMutex = Mutex()

    // ==================== Song Cache Operations ====================

    suspend fun getCachedSong(songId: String): CachedSong? {
        // Check memory cache first
        songCache.get(songId)?.let { return it }

        // Check database
        return databaseMutex.withLock {
            val song = cacheDao.getCachedSongById(songId)
            song?.let { songCache.put(songId, it) }
            song
        }
    }

    suspend fun getAllCachedSongs(): Flow<List<CachedSong>> {
        return cacheDao.getAllCachedSongs()
    }

    suspend fun getCachedSongsByAlbum(albumId: String): Flow<List<CachedSong>> {
        return cacheDao.getCachedSongsByAlbum(albumId)
    }

    suspend fun isSongCached(songId: String): Boolean {
        return songCache.get(songId) != null || cacheDao.isSongCached(songId)
    }

    suspend fun cacheSong(song: CachedSong) {
        databaseMutex.withLock {
            cacheDao.insertCachedSong(song)
            songCache.put(song.id, song)
            updateCacheStats()
        }
    }

    suspend fun cacheSongs(songs: List<CachedSong>) {
        databaseMutex.withLock {
            cacheDao.insertCachedSongs(songs)
            songs.forEach { songCache.put(it.id, it) }
            updateCacheStats()
        }
    }

    suspend fun removeCachedSong(songId: String) {
        databaseMutex.withLock {
            cacheDao.deleteCachedSongById(songId)
            songCache.remove(songId)
            updateCacheStats()
        }
    }

    suspend fun updateLastPlayed(songId: String) {
        databaseMutex.withLock {
            cacheDao.updateLastPlayed(songId)
            songCache.get(songId)?.let {
                val updated = it.copy(
                    lastPlayedAt = System.currentTimeMillis(),
                    playCount = it.playCount + 1
                )
                songCache.put(songId, updated)
            }
        }
    }

    // ==================== Album Cache Operations ====================

    suspend fun getCachedAlbum(albumId: String): CachedAlbum? {
        albumCache.get(albumId)?.let { return it }

        return databaseMutex.withLock {
            val album = cacheDao.getCachedAlbumById(albumId)
            album?.let { albumCache.put(albumId, it) }
            album
        }
    }

    suspend fun getAllCachedAlbums(): Flow<List<CachedAlbum>> {
        return cacheDao.getAllCachedAlbums()
    }

    suspend fun isAlbumCached(albumId: String): Boolean {
        return albumCache.get(albumId) != null || cacheDao.isAlbumCached(albumId)
    }

    suspend fun cacheAlbum(album: CachedAlbum) {
        databaseMutex.withLock {
            cacheDao.insertCachedAlbum(album)
            albumCache.put(album.id, album)
            updateCacheStats()
        }
    }

    suspend fun removeCachedAlbum(albumId: String) {
        databaseMutex.withLock {
            cacheDao.deleteCachedAlbumById(albumId)
            albumCache.remove(albumId)
            updateCacheStats()
        }
    }

    // ==================== Download Queue Operations ====================

    suspend fun addToDownloadQueue(song: Song, serverId: String, serverType: String) {
        val queueItem = DownloadQueue(
            songId = song.id,
            songTitle = song.title,
            serverId = serverId,
            serverType = serverType,
            status = DownloadStatus.PENDING
        )
        cacheDao.insertDownloadQueueItem(queueItem)
    }

    suspend fun updateDownloadStatus(item: DownloadQueue) {
        cacheDao.updateDownloadQueueItem(item)
    }

    suspend fun getDownloadQueue(): Flow<List<DownloadQueue>> {
        return cacheDao.getAllDownloadQueueItems()
    }

    suspend fun getActiveDownloadCount(): Int {
        return cacheDao.getActiveDownloadCount()
    }

    suspend fun removeFromDownloadQueue(songId: String) {
        cacheDao.deleteDownloadQueueItemBySongId(songId)
    }

    // ==================== Cache Size Management ====================

    suspend fun getCacheSize(): Long {
        return cacheDao.getCacheSize()
    }

    suspend fun getCachedSongCount(): Int {
        return cacheDao.getCachedSongCount()
    }

    suspend fun isCacheFull(): Boolean {
        return getCacheSize() >= MAX_CACHE_SIZE_BYTES
    }

    suspend fun getAvailableCacheSpace(): Long {
        return MAX_CACHE_SIZE_BYTES - getCacheSize()
    }

    private suspend fun updateCacheStats() {
        val totalSize = cacheDao.getCacheSize()
        val songCount = cacheDao.getCachedSongCount()
        cacheDao.updateCacheStats(totalSize, songCount, 0)
    }

    // ==================== LRU Eviction Policy ====================

    suspend fun evictLeastRecentlyUsed(count: Int = EVICTION_BATCH_SIZE) {
        databaseMutex.withLock {
            val deletedCount = cacheDao.deleteLeastRecentlyUsed(count)
            clearMemoryCache()
            updateCacheStats()
        }
    }

    suspend fun evictToFreeSpace(requiredSpace: Long) {
        var freedSpace = 0L
        var iterations = 0
        val maxIterations = 50

        while (freedSpace < requiredSpace && iterations < maxIterations) {
            val currentSize = getCacheSize()
            if (currentSize + freedSpace >= requiredSpace) break

            val songsToDelete = cacheDao.deleteLeastRecentlyUsed(EVICTION_BATCH_SIZE)
            if (songsToDelete == 0) break

            iterations++
        }

        clearMemoryCache()
        updateCacheStats()
    }

    private fun clearMemoryCache() {
        songCache.evictAll()
        albumCache.evictAll()
    }

    // ==================== Intelligent Prefetching ====================

    suspend fun getSongsForPrefetch(limit: Int = PREFETCH_THRESHOLD): List<String> {
        // Get recently played songs and prefetch songs from same album/artist
        val recentSongs = cacheDao.getCachedSongsByLastPlayed().first()
            .take(10)

        val prefetchCandidates = mutableListOf<String>()

        // Get other songs from same albums
        recentSongs.forEach { song ->
            song.albumId?.let { albumId ->
                val albumSongs = cacheDao.getCachedSongsByAlbum(albumId).first()
                albumSongs.filter { it.id != song.id }
                    .take(2)
                    .forEach { prefetchCandidates.add(it.id) }
            }
        }

        return prefetchCandidates.distinct().take(limit)
    }

    // Used by CacheManager for intelligent prefetching based on listening history
    suspend fun getRecommendedSongsForOffline(): List<Song> {
        val recentSongs = cacheDao.getCachedSongsByLastPlayed().first()
            .take(20)

        // Group by artist and album to find patterns
        val artistCounts = recentSongs.groupingBy { it.artist }.eachCount()
        val albumCounts = recentSongs.groupingBy { it.albumId }.eachCount()

        // Get all cached songs and score them
        val allSongs = cacheDao.getAllCachedSongs().first()

        return allSongs
            .map { song ->
                var score = 0
                song.artist?.let { artist -> score += artistCounts[artist] ?: 0 * 3 }
                song.albumId?.let { album -> score += albumCounts[album] ?: 0 * 2 }
                song.lastPlayedAt?.let { score += (System.currentTimeMillis() - it).toInt() / 86400000 }
                Triple(score, song)
            }
            .sortedByDescending { it.first }
            .take(50)
            .map { it.second.toSong() }
    }

    // ==================== Cache Cleanup ====================

    suspend fun deleteExpiredCache(expiryDays: Int = 30): Int {
        val expiryTime = System.currentTimeMillis() - (expiryDays.toLong() * 24 * 60 * 60 * 1000)
        return cacheDao.deleteExpiredSongs(expiryTime)
    }

    suspend fun clearCache() {
        databaseMutex.withLock {
            cacheDao.clearCache()
            clearMemoryCache()
            updateCacheStats()
        }
    }

    suspend fun getCacheHitRate(): Float {
        // This would require tracking cache hits/misses over time
        // For now, return a placeholder
        return 0.85f
    }
}
