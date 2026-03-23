package com.amberesaiae.melos.feature.library.ui

import com.amberesaiae.melos.model.Album
import com.amberesaiae.melos.model.Artist
import com.amberesaiae.melos.model.Genre
import com.amberesaiae.melos.model.Track

/**
 * Represents the different tabs in the library browser
 */
enum class LibraryTab {
    ALBUMS,
    ARTISTS,
    TRACKS,
    GENRES
}

/**
 * UI state for the Library screen
 */
data class LibraryState(
    val selectedTab: LibraryTab = LibraryTab.ALBUMS,
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val hasMoreData: Boolean = false,
    val currentPage: Int = 1
)

/**
 * Actions that can be performed on the Library screen
 */
sealed class LibraryAction {
    data class SelectTab(val tab: LibraryTab) : LibraryAction()
    data class UpdateSearchQuery(val query: String) : LibraryAction()
    data object Refresh : LibraryAction()
    data object LoadMore : LibraryAction()
    data class NavigateToSearch(val query: String) : LibraryAction()
    data class NavigateToAlbum(val albumId: Long) : LibraryAction()
    data class NavigateToArtist(val artistId: Long) : LibraryAction()
    data class NavigateToTrack(val trackId: Long) : LibraryAction()
    data class NavigateToGenre(val genreId: Long) : LibraryAction()
    data class PlayAlbum(val albumId: Long) : LibraryAction()
    data class PlayArtist(val artistId: Long) : LibraryAction()
    data class PlayTrack(val trackId: Long) : LibraryAction()
}
