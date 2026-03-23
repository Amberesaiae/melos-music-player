@file:Suppress("kotlin:S6290")

package com.amberesaiae.melos.core.cache

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.amberesaiae.melos.core.model.Song
import java.util.Date

/**
 * Represents a cached song in local storage.
 */
@Entity(tableName = "cached_songs")
data class CachedSong(
    @PrimaryKey
    val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumId: String?,
    val duration: Long,
    val localPath: String,
    val downloadTime: Long,
    val fileSize: Long,
    val serverId: String,
    val serverType: String,
    val artworkPath: String?,
    val trackNumber: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val lastPlayedAt: Long? = null,
    val playCount: Int = 0
) {
    fun toSong(): Song {
        return Song(
            id = id,
            title = title,
            artist = artist?.let { com.amberesaiae.melos.core.model.Artist(name = it) },
            album = album?.let { com.amberesaiae.melos.core.model.Album(name = it) },
            duration = duration,
            trackNumber = trackNumber,
            year = year
        )
    }

    companion object {
        fun fromSong(song: Song, localPath: String, serverId: String, serverType: String): CachedSong {
            return CachedSong(
                id = song.id,
                title = song.title,
                artist = song.artist?.name,
                album = song.album?.name,
                albumId = song.album?.id,
                duration = song.duration ?: 0L,
                localPath = localPath,
                downloadTime = System.currentTimeMillis(),
                fileSize = 0, // Will be updated after download
                serverId = serverId,
                serverType = serverType,
                artworkPath = null,
                trackNumber = song.trackNumber,
                year = song.year
            )
        }
    }
}

/**
 * Represents a cached album in local storage.
 */
@Entity(tableName = "cached_albums")
data class CachedAlbum(
    @PrimaryKey
    val id: String,
    val title: String,
    val artist: String?,
    val coverArtPath: String?,
    val songCount: Int,
    val serverId: String,
    val serverType: String,
    val downloadTime: Long,
    val year: Int? = null,
    val genre: String? = null
)

/**
 * Represents a song in the download queue.
 */
@Entity(tableName = "download_queue")
data class DownloadQueue(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val songId: String,
    val songTitle: String,
    val serverId: String,
    val serverType: String,
    val status: DownloadStatus,
    val progress: Int = 0,
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val errorMessage: String? = null,
    val fileSize: Long? = null,
    val localPath: String? = null
)

/**
 * Download status states.
 */
enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    PAUSED,
    CANCELLED
}

/**
 * Cache metadata for tracking overall cache state.
 */
@Entity(tableName = "cache_metadata")
data class CacheMetadata(
    @PrimaryKey
    val id: Int = 1,
    val totalSize: Long = 0L,
    val lastCleanup: Long = 0L,
    val songCount: Int = 0,
    val albumCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
