package com.amberesaiae.melos.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Jellyfin API response models.
 */

/**
 * Jellyfin ping response.
 */
@Serializable
data class JellyfinPingResponse(
    @SerialName("RequestId") val requestId: String? = null
)

/**
 * Jellyfin items response (for artists, albums, songs).
 */
@Serializable
data class JellyfinItemsResponse(
    @SerialName("Items") val items: List<JellyfinItem>? = null,
    @SerialName("TotalRecordCount") val totalRecordCount: Int? = null,
    @SerialName("StartIndex") val startIndex: Int? = null
)

@Serializable
data class JellyfinItem(
    @SerialName("Id") val id: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("MediaType") val mediaType: String? = null,
    @SerialName("CollectionType") val collectionType: String? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("Size") val size: Long? = null,
    @SerialName("Container") val container: String? = null,
    @SerialName("Path") val path: String? = null,
    @SerialName("HasAlbumCover") val hasAlbumCover: Boolean? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String>? = null,
    @SerialName("ImageBlurHashes") val imageBlurHashes: Map<String, Map<String, String>>? = null,
    @SerialName("ArtistItems") val artistItems: List<JellyfinArtistRef>? = null,
    @SerialName("Artists") val artists: List<String>? = null,
    @SerialName("AlbumId") val albumId: String? = null,
    @SerialName("Album") val album: String? = null,
    @SerialName("ParentId") val parentId: String? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("PremiereDate") val premiereDate: String? = null,
    @SerialName("DateCreated") val dateCreated: String? = null,
    @SerialName("GenreItems") val genreItems: List<JellyfinGenreRef>? = null,
    @SerialName("Genres") val genres: List<String>? = null,
    @SerialName("UserData") val userData: JellyfinUserData? = null,
    @SerialName("MediaSources") val mediaSources: List<JellyfinMediaSource>? = null,
    @SerialName("ChildCount") val childCount: Int? = null,
    @SerialName("AlbumPrimaryImageTag") val albumPrimaryImageTag: String? = null
)

@Serializable
data class JellyfinArtistRef(
    @SerialName("Id") val id: String? = null,
    @SerialName("Name") val name: String? = null
)

@Serializable
data class JellyfinGenreRef(
    @SerialName("Id") val id: String? = null,
    @SerialName("Name") val name: String? = null
)

/**
 * Jellyfin album response.
 */
@Serializable
data class JellyfinAlbumResponse(
    @SerialName("Id") val id: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("ArtistItems") val artistItems: List<JellyfinArtistRef>? = null,
    @SerialName("Artists") val artists: List<String>? = null,
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String>? = null,
    @SerialName("ChildCount") val childCount: Int? = null
)

/**
 * Jellyfin user data (play status, favorites, etc.).
 */
@Serializable
data class JellyfinUserData(
    @SerialName("Played") val played: Boolean? = null,
    @SerialName("PlayCount") val playCount: Int? = null,
    @SerialName("Favorite") val favorite: Boolean? = null,
    @SerialName("Rating") val rating: Double? = null,
    @SerialName("LastPlayedDate") val lastPlayedDate: String? = null,
    @SerialName("PlaybackPositionTicks") val playbackPositionTicks: Long? = null,
    @SerialName("UnplayedItemCount") val unplayedItemCount: Int? = null,
    @SerialName("PlayedPercentage") val playedPercentage: Double? = null,
    @SerialName("IsFavorite") val isFavorite: Boolean? = null,
    @SerialName("Likes") val likes: Boolean? = null,
    @SerialName("Rating") val rating: Int? = null
)

/**
 * Jellyfin media source (file/stream info).
 */
@Serializable
data class JellyfinMediaSource(
    @SerialName("Id") val id: String? = null,
    @SerialName("Path") val path: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("Container") val container: String? = null,
    @SerialName("Size") val size: Long? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("Bitrate") val bitrate: Int? = null,
    @SerialName("IsDirectStream") val isDirectStream: Boolean? = null,
    @SerialName("MediaStreams") val mediaStreams: List<JellyfinMediaStream>? = null,
    @SerialName("SupportsDirectPlay") val supportsDirectPlay: Boolean? = null,
    @SerialName("SupportsDirectStream") val supportsDirectStream: Boolean? = null,
    @SerialName("SupportsTranscoding") val supportsTranscoding: Boolean? = null
)

@Serializable
data class JellyfinMediaStream(
    @SerialName("Codec") val codec: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("Language") val language: String? = null,
    @SerialName("BitRate") val bitRate: Int? = null,
    @SerialName("Channels") val channels: Int? = null,
    @SerialName("SampleRate") val sampleRate: Int? = null,
    @SerialName("IsDefault") val isDefault: Boolean? = null,
    @SerialName("IsForced") val isForced: Boolean? = null,
    @SerialName("IsExternal") val isExternal: Boolean? = null
)
