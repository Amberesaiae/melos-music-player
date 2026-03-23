package com.amberesaiae.melos.core.network.api

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Jellyfin API service interface.
 * Jellyfin uses REST with Bearer token authentication.
 * 
 * Authentication:
 * - Use "X-Emby-Authorization" header or Bearer token
 * - Format: MediaBrowser Client="Melos", Device="Android", DeviceId="xxx", Version="1.0.0", Token="xxx"
 */
interface JellyfinApiService {

    /**
     * Test connectivity with the Jellyfin server (ping).
     */
    @GET("System/Ping")
    suspend fun ping(
        @Header("X-Emby-Authorization") authorization: String
    ): JellyfinPingResponse

    /**
     * Get all items from the library (similar to getIndexes).
     */
    @GET("Items")
    suspend fun getItems(
        @Header("X-Emby-Authorization") authorization: String,
        @Query("parentId") parentId: String? = null,
        @Query("includeItemTypes") includeItemTypes: String = "MusicArtist,MusicAlbum,Audio",
        @Query("recursive") recursive: Boolean = true,
        @Query("sortBy") sortBy: String = "SortName",
        @Query("sortOrder") sortOrder: String = "Ascending"
    ): JellyfinItemsResponse

    /**
     * Get an album by ID.
     */
    @GET("Items/{itemId}")
    suspend fun getAlbum(
        @Header("X-Emby-Authorization") authorization: String,
        @Path("itemId") itemId: String
    ): JellyfinAlbumResponse

    /**
     * Get songs/tracks.
     */
    @GET("Items")
    suspend fun getSongs(
        @Header("X-Emby-Authorization") authorization: String,
        @Query("parentId") parentId: String? = null,
        @Query("includeItemTypes") includeItemTypes: String = "Audio",
        @Query("recursive") recursive: Boolean = true
    ): JellyfinItemsResponse

    /**
     * Stream/download audio.
     * Returns raw audio stream.
     */
    @GET("Audio/{itemId}/stream")
    suspend fun stream(
        @Header("X-Emby-Authorization") authorization: String,
        @Path("itemId") itemId: String,
        @Query("Container") container: String? = null,
        @Query("AudioCodec") audioCodec: String? = null,
        @Query("MaxAudioBitrate") maxAudioBitrate: Int? = null
    ): okhttp3.ResponseBody
}
