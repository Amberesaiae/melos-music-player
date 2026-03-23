package com.amberesaiae.melos.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Subsonic API response wrapper.
 * All Subsonic responses use this structure.
 */
@Serializable
data class SubsonicResponse(
    @SerialName("status") val status: String? = null,
    @SerialName("version") val version: String? = null,
    @SerialName("type") val type: String? = null
)

/**
 * Subsonic indexes response (artists/albums).
 */
@Serializable
data class SubsonicIndexesResponse(
    @SerialName("status") val status: String? = null,
    @SerialName("indexes") val indexes: SubsonicIndexes? = null
)

@Serializable
data class SubsonicIndexes(
    @SerialName("index") val index: List<SubsonicIndex>? = null,
    @SerialName("musicFolderId") val musicFolderId: Int? = null,
    @SerialName("musicFolderName") val musicFolderName: String? = null
)

@Serializable
data class SubsonicIndex(
    @SerialName("name") val name: String? = null,
    @SerialName("artist") val artist: List<SubsonicArtist>? = null
)

@Serializable
data class SubsonicArtist(
    @SerialName("id") val id: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("coverArt") val coverArt: String? = null,
    @SerialName("albumCount") val albumCount: Int? = null,
    @SerialName("starred") val starred: String? = null
)

/**
 * Subsonic album response.
 */
@Serializable
data class SubsonicAlbumResponse(
    @SerialName("status") val status: String? = null,
    @SerialName("album") val album: SubsonicAlbum? = null
)

@Serializable
data class SubsonicAlbum(
    @SerialName("id") val id: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("artist") val artist: String? = null,
    @SerialName("artistId") val artistId: String? = null,
    @SerialName("coverArt") val coverArt: String? = null,
    @SerialName("songCount") val songCount: Int? = null,
    @SerialName("duration") val duration: Int? = null,
    @SerialName("playCount") val playCount: Int? = null,
    @SerialName("created") val created: String? = null,
    @SerialName("year") val year: Int? = null,
    @SerialName("genre") val genre: String? = null,
    @SerialName("song") val song: List<SubsonicSong>? = null
)

/**
 * Subsonic song response.
 */
@Serializable
data class SubsonicSongResponse(
    @SerialName("status") val status: String? = null,
    @SerialName("song") val song: SubsonicSong? = null
)

@Serializable
data class SubsonicSong(
    @SerialName("id") val id: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("artist") val artist: String? = null,
    @SerialName("artistId") val artistId: String? = null,
    @SerialName("album") val album: String? = null,
    @SerialName("albumId") val albumId: String? = null,
    @SerialName("track") val track: Int? = null,
    @SerialName("duration") val duration: Int? = null,
    @SerialName("bitRate") val bitRate: Int? = null,
    @SerialName("size") val size: Long? = null,
    @SerialName("suffix") val suffix: String? = null,
    @SerialName("contentType") val contentType: String? = null,
    @SerialName("path") val path: String? = null,
    @SerialName("coverArt") val coverArt: String? = null,
    @SerialName("created") val created: String? = null,
    @SerialName("year") val year: Int? = null,
    @SerialName("genre") val genre: String? = null,
    @SerialName(" starred") val starred: String? = null
)

/**
 * Error response from Subsonic API.
 */
@Serializable
data class SubsonicErrorResponse(
    @SerialName("status") val status: String = "failed",
    @SerialName("error") val error: SubsonicError? = null
)

@Serializable
data class SubsonicError(
    @SerialName("code") val code: Int,
    @SerialName("message") val message: String
)
