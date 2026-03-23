@file:Suppress("kotlin:S6290")

package com.amberesaiae.melos.core.player

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.amberesaiae.melos.core.model.Song
import com.amberesaiae.melos.core.network.api.SubsonicApiService
import com.amberesaiae.melos.core.network.api.JellyfinApiService
import com.amberesaiae.melos.core.network.util.AuthUtils
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Handles creation and management of MediaItem instances for remote streams.
 * Supports both Subsonic and Jellyfin server types.
 */
class RemoteMediaSource(
    private val context: Context,
    private val subsonicApiService: SubsonicApiService,
    private val jellyfinApiService: JellyfinApiService
) {
    companion object {
        private const val STREAM_BUFFER_CONFIG = 512 * 1024 // 512KB
        private const val PREFETCH_BUFFER_CONFIG = 1024 * 1024 // 1MB
        private const val BACK_BUFFER_CONFIG = 512 * 1024 // 512KB
    }

    /**
     * Buffer configuration for streaming.
     */
    data class BufferConfig(
        val streamBufferMs: Long = 15000L, // 15 seconds
        val prefetchBufferMs: Long = 30000L, // 30 seconds
        val backBufferMs: Long = 10000L, // 10 seconds
        val minBufferMs: Long = 5000L, // 5 seconds
        val maxBufferMs: Long = 60000L // 60 seconds
    )

    /**
     * Creates a MediaItem for a Subsonic song.
     */
    suspend fun createSubsonicMediaItem(
        songId: String,
        song: Song
    ): MediaItem {
        val username = "admin" // Should be from auth storage
        val password = "admin123" // Should be from auth storage
        val salt = AuthUtils.generateSalt()
        val token = AuthUtils.generateSubsonicToken(salt, password)

        val streamUrl = buildSubsonicStreamUrl(songId, username, salt, token)

        return MediaItem.Builder()
            .setMediaId(songId)
            .setUri(streamUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist?.name ?: "Unknown Artist")
                    .setAlbumTitle(song.album?.name ?: "Unknown Album")
                    .setArtworkUri(Uri.parse(buildSubsonicArtworkUrl(songId, username, salt, token)))
                    .build()
            )
            .build()
    }

    /**
     * Creates a MediaItem for a Jellyfin song.
     */
    suspend fun createJellyfinMediaItem(
        itemId: String,
        song: Song,
        accessToken: String
    ): MediaItem {
        val streamUrl = buildJellyfinStreamUrl(itemId, accessToken)

        return MediaItem.Builder()
            .setMediaId(itemId)
            .setUri(streamUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist?.name ?: "Unknown Artist")
                    .setAlbumTitle(song.album?.name ?: "Unknown Album")
                    .setArtworkUri(Uri.parse(buildJellyfinArtworkUrl(itemId, accessToken)))
                    .build()
            )
            .build()
    }

    /**
     * Builds a direct stream URL for Subsonic.
     */
    private fun buildSubsonicStreamUrl(
        songId: String,
        username: String,
        salt: String,
        token: String
    ): String {
        return StringBuilder().apply {
            append("http://192.168.1.100:4040/rest/stream.view")
            append("?u=$username")
            append("&s=$salt")
            append("&t=$token")
            append("&v=1.16.1")
            append("&c=MelosPlayer")
            append("&f=json")
            append("&id=$songId")
        }.toString()
    }

    /**
     * Builds a direct stream URL for Jellyfin.
     */
    private fun buildJellyfinStreamUrl(
        itemId: String,
        accessToken: String
    ): String {
        return "http://192.168.1.100:8096/Audio/$itemId/stream" +
            "?UserId={userId}" +
            "&DeviceId={deviceId}" +
            "&api_key=$accessToken"
    }

    /**
     * Builds an artwork URL for Subsonic.
     */
    private fun buildSubsonicArtworkUrl(
        songId: String,
        username: String,
        salt: String,
        token: String
    ): String {
        return StringBuilder().apply {
            append("http://192.168.1.100:4040/rest/getCoverArt.view")
            append("?u=$username")
            append("&s=$salt")
            append("&t=$token")
            append("&v=1.16.1")
            append("&c=MelosPlayer")
            append("&f=json")
            append("&id=$songId")
        }.toString()
    }

    /**
     * Builds an artwork URL for Jellyfin.
     */
    private fun buildJellyfinArtworkUrl(
        itemId: String,
        accessToken: String
    ): String {
        return "http://192.168.1.100:8096/Items/$itemId/Images/Primary" +
            "?api_key=$accessToken"
    }

    /**
     * Gets buffer configuration based on network quality.
     */
    fun getNetworkAwareBufferConfig(networkQuality: NetworkQuality): BufferConfig {
        return when (networkQuality) {
            NetworkQuality.WIFI -> BufferConfig(
                streamBufferMs = 20000L,
                prefetchBufferMs = 45000L,
                backBufferMs = 15000L,
                minBufferMs = 8000L,
                maxBufferMs = 90000L
            )
            NetworkQuality.CELLULAR_GOOD -> BufferConfig(
                streamBufferMs = 15000L,
                prefetchBufferMs = 30000L,
                backBufferMs = 10000L,
                minBufferMs = 5000L,
                maxBufferMs = 60000L
            )
            NetworkQuality.CELLULAR_POOR -> BufferConfig(
                streamBufferMs = 10000L,
                prefetchBufferMs = 20000L,
                backBufferMs = 5000L,
                minBufferMs = 3000L,
                maxBufferMs = 40000L
            )
            NetworkQuality.OFFLINE -> BufferConfig(
                streamBufferMs = 5000L,
                prefetchBufferMs = 0L,
                backBufferMs = 0L,
                minBufferMs = 1000L,
                maxBufferMs = 15000L
            )
        }
    }
}
