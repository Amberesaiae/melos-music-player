package com.amberesaiae.melos.core.network.api

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Subsonic API service interface.
 * Subsonic uses GET requests with authentication parameters in the query string.
 * 
 * Authentication:
 * - u: username
 * - p: password (MD5 hash) or plain password
 * - v: API version (1.16.1 or higher)
 * - c: client name
 * - f: response format (json)
 * - t: salt (for token-based auth)
 * - s: token (MD5 hash of password + salt)
 */
interface SubsonicApiService {

    /**
     * Test connectivity with the Subsonic server.
     * Returns a SubsonicResponse with status "ok" if successful.
     */
    @GET("rest/ping.view")
    suspend fun ping(
        @Query("u") username: String,
        @Query("p") password: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "Melos",
        @Query("f") format: String = "json"
    ): SubsonicResponse

    /**
     * Get all indexes (artists/albums) from the library.
     */
    @GET("rest/getIndexes.view")
    suspend fun getIndexes(
        @Query("u") username: String,
        @Query("p") password: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "Melos",
        @Query("f") format: String = "json",
        @Query("musicFolderId") musicFolderId: Int? = null
    ): SubsonicIndexesResponse

    /**
     * Get songs by index (first letter of artist name).
     */
    @GET("rest/getIndexes.view")
    suspend fun getSongsByIndex(
        @Query("u") username: String,
        @Query("p") password: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "Melos",
        @Query("f") format: String = "json",
        @Query("musicFolderId") musicFolderId: Int? = null
    ): SubsonicIndexesResponse

    /**
     * Get an album by ID.
     */
    @GET("rest/getAlbum.view")
    suspend fun getAlbum(
        @Query("id") albumId: String,
        @Query("u") username: String,
        @Query("p") password: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "Melos",
        @Query("f") format: String = "json"
    ): SubsonicAlbumResponse

    /**
     * Get song by ID.
     */
    @GET("rest/getSong.view")
    suspend fun getSongs(
        @Query("id") songId: String,
        @Query("u") username: String,
        @Query("p") password: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "Melos",
        @Query("f") format: String = "json"
    ): SubsonicSongResponse

    /**
     * Stream/download a song.
     * This returns the raw audio stream, not JSON.
     * URL format: /rest/stream.view?id={songId}&u={username}&p={password}&v=1.16.1&c=Melos&f=json
     */
    @GET("rest/stream.view")
    suspend fun stream(
        @Query("id") songId: String,
        @Query("u") username: String,
        @Query("p") password: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "Melos",
        @Query("maxBitRate") maxBitRate: Int? = null,
        @Query("format") format: String? = null
    ): okhttp3.ResponseBody
}
