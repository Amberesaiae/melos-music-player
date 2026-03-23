@file:Suppress("kotlin:S6290", "kotlin:S6701")

package com.amberesaiae.melos.core.cache

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for cache-related database operations.
 */
@Dao
interface CacheDao {

    // ==================== Cached Songs ====================

    @Query("SELECT * FROM cached_songs ORDER BY downloadTime DESC")
    fun getAllCachedSongs(): Flow<List<CachedSong>>

    @Query("SELECT * FROM cached_songs ORDER BY lastPlayedAt DESC NULLS LAST")
    fun getCachedSongsByLastPlayed(): Flow<List<CachedSong>>

    @Query("SELECT * FROM cached_songs WHERE id = :songId")
    suspend fun getCachedSongById(songId: String): CachedSong?

    @Query("SELECT * FROM cached_songs WHERE id IN (:songIds)")
    suspend fun getCachedSongsByIds(songIds: List<String>): List<CachedSong>

    @Query("SELECT * FROM cached_songs WHERE albumId = :albumId ORDER BY trackNumber")
    fun getCachedSongsByAlbum(albumId: String): Flow<List<CachedSong>>

    @Query("SELECT * FROM cached_songs WHERE artist = :artist ORDER BY album, trackNumber")
    fun getCachedSongsByArtist(artist: String): Flow<List<CachedSong>>

    @Query("SELECT EXISTS(SELECT 1 FROM cached_songs WHERE id = :songId)")
    suspend fun isSongCached(songId: String): Boolean

    @Query("SELECT SUM(fileSize) FROM cached_songs")
    suspend fun getCacheSize(): Long

    @Query("SELECT COUNT(*) FROM cached_songs")
    suspend fun getCachedSongCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedSong(song: CachedSong)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedSongs(songs: List<CachedSong>)

    @Delete
    suspend fun deleteCachedSong(song: CachedSong)

    @Query("DELETE FROM cached_songs WHERE id = :songId")
    suspend fun deleteCachedSongById(songId: String)

    @Query("UPDATE cached_songs SET lastPlayedAt = :timestamp, playCount = playCount + 1 WHERE id = :songId")
    suspend fun updateLastPlayed(songId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE cached_songs SET fileSize = :size WHERE id = :songId")
    suspend fun updateFileSize(songId: String, size: Long)

    // ==================== Cached Albums ====================

    @Query("SELECT * FROM cached_albums ORDER BY downloadTime DESC")
    fun getAllCachedAlbums(): Flow<List<CachedAlbum>>

    @Query("SELECT * FROM cached_albums WHERE id = :albumId")
    suspend fun getCachedAlbumById(albumId: String): CachedAlbum?

    @Query("SELECT EXISTS(SELECT 1 FROM cached_albums WHERE id = :albumId)")
    suspend fun isAlbumCached(albumId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedAlbum(album: CachedAlbum)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedAlbums(albums: List<CachedAlbum>)

    @Delete
    suspend fun deleteCachedAlbum(album: CachedAlbum)

    @Query("DELETE FROM cached_albums WHERE id = :albumId")
    suspend fun deleteCachedAlbumById(albumId: String)

    // ==================== Download Queue ====================

    @Query("SELECT * FROM download_queue ORDER BY createdAt ASC")
    fun getAllDownloadQueueItems(): Flow<List<DownloadQueue>>

    @Query("SELECT * FROM download_queue WHERE status = :status ORDER BY createdAt ASC")
    fun getDownloadQueueByStatus(status: DownloadStatus): Flow<List<DownloadQueue>>

    @Query("SELECT * FROM download_queue WHERE songId = :songId")
    suspend fun getDownloadQueueItemBySongId(songId: String): DownloadQueue?

    @Query("SELECT * FROM download_queue WHERE status = 'DOWNLOADING' LIMIT 1")
    suspend fun getCurrentDownloadingItem(): DownloadQueue?

    @Query("SELECT COUNT(*) FROM download_queue WHERE status = 'PENDING' OR status = 'DOWNLOADING'")
    suspend fun getActiveDownloadCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloadQueueItem(item: DownloadQueue)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloadQueueItems(items: List<DownloadQueue>)

    @Update
    suspend fun updateDownloadQueueItem(item: DownloadQueue)

    @Delete
    suspend fun deleteDownloadQueueItem(item: DownloadQueue)

    @Query("DELETE FROM download_queue WHERE songId = :songId")
    suspend fun deleteDownloadQueueItemBySongId(songId: String)

    @Query("DELETE FROM download_queue WHERE status = 'COMPLETED' OR status = 'CANCELLED'")
    suspend fun deleteCompletedQueueItems()

    // ==================== Cache Metadata ====================

    @Query("SELECT * FROM cache_metadata WHERE id = 1")
    suspend fun getCacheMetadata(): CacheMetadata?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCacheMetadata(metadata: CacheMetadata)

    @Update
    suspend fun updateCacheMetadata(metadata: CacheMetadata)

    @Query("UPDATE cache_metadata SET totalSize = :size, songCount = :songs, albumCount = :albums, updatedAt = :timestamp WHERE id = 1")
    suspend fun updateCacheStats(size: Long, songs: Int, albums: Int, timestamp: Long = System.currentTimeMillis())

    // ==================== Cache Cleanup ====================

    @Query("DELETE FROM cached_songs WHERE downloadTime < :expiryTime")
    suspend fun deleteExpiredSongs(expiryTime: Long): Int

    @Query("DELETE FROM cached_songs WHERE id NOT IN (
        SELECT id FROM cached_songs ORDER BY lastPlayedAt DESC NULLS LAST, downloadTime DESC
        LIMIT :limit
    )")
    suspend fun deleteLeastRecentlyUsed(limit: Int): Int

    @Query("SELECT * FROM cached_songs WHERE fileSize = 0 OR localPath IS NULL ORDER BY downloadTime ASC")
    suspend fun getIncompleteDownloads(): List<CachedSong>

    @Query("DELETE FROM cached_songs")
    suspend fun clearAllCachedSongs()

    @Query("DELETE FROM cached_albums")
    suspend fun clearAllCachedAlbums()

    @Query("DELETE FROM download_queue")
    suspend fun clearDownloadQueue()

    @Transaction
    suspend fun clearCache() {
        clearAllCachedSongs()
        clearAllCachedAlbums()
        clearDownloadQueue()
    }
}
