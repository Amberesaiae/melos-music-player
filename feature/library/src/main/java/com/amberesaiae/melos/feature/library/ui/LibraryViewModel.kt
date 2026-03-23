package com.amberesaiae.melos.feature.library.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amberesaiae.melos.core.database.LocalLibraryDataSource
import com.amberesaiae.melos.model.Album
import com.amberesaiae.melos.model.Artist
import com.amberesaiae.melos.model.Genre
import com.amberesaiae.melos.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PAGE_SIZE = 50

/**
 * ViewModel for the Library screen
 * Manages UI state and handles user interactions
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryDataSource: LocalLibraryDataSource
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    init {
        loadInitialData()
    }

    /**
     * Load initial data for all tabs
     */
    private fun loadInitialData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                // Load data in parallel
                val albums = libraryDataSource.getAlbums(limit = PAGE_SIZE)
                val artists = libraryDataSource.getArtists(limit = PAGE_SIZE)
                val tracks = libraryDataSource.getTracks(limit = PAGE_SIZE)
                val genres = libraryDataSource.getGenres(limit = PAGE_SIZE)

                _state.update {
                    it.copy(
                        albums = albums,
                        artists = artists,
                        tracks = tracks,
                        genres = genres,
                        isLoading = false,
                        hasMoreData = albums.size == PAGE_SIZE || 
                                      artists.size == PAGE_SIZE || 
                                      tracks.size == PAGE_SIZE || 
                                      genres.size == PAGE_SIZE
                    )
                }
            } catch (e: Exception) {
                _state.update { 
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load library"
                    )
                }
            }
        }
    }

    /**
     * Handle UI actions
     */
    fun onAction(action: LibraryAction) {
        when (action) {
            is LibraryAction.SelectTab -> selectTab(action.tab)
            is LibraryAction.UpdateSearchQuery -> updateSearchQuery(action.query)
            is LibraryAction.Refresh -> refresh()
            is LibraryAction.LoadMore -> loadMore()
            is LibraryAction.NavigateToSearch -> navigateToSearch(action.query)
            is LibraryAction.NavigateToAlbum -> navigateToAlbum(action.albumId)
            is LibraryAction.NavigateToArtist -> navigateToArtist(action.artistId)
            is LibraryAction.NavigateToTrack -> navigateToTrack(action.trackId)
            is LibraryAction.NavigateToGenre -> navigateToGenre(action.genreId)
            is LibraryAction.PlayAlbum -> playAlbum(action.albumId)
            is LibraryAction.PlayArtist -> playArtist(action.artistId)
            is LibraryAction.PlayTrack -> playTrack(action.trackId)
        }
    }

    private fun selectTab(tab: LibraryTab) {
        _state.update { it.copy(selectedTab = tab) }
    }

    private fun updateSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true, error = null) }
            try {
                val albums = libraryDataSource.getAlbums(limit = PAGE_SIZE)
                val artists = libraryDataSource.getArtists(limit = PAGE_SIZE)
                val tracks = libraryDataSource.getTracks(limit = PAGE_SIZE)
                val genres = libraryDataSource.getGenres(limit = PAGE_SIZE)

                _state.update {
                    it.copy(
                        albums = albums,
                        artists = artists,
                        tracks = tracks,
                        genres = genres,
                        isRefreshing = false,
                        currentPage = 1,
                        hasMoreData = albums.size == PAGE_SIZE || 
                                      artists.size == PAGE_SIZE || 
                                      tracks.size == PAGE_SIZE || 
                                      genres.size == PAGE_SIZE
                    )
                }
            } catch (e: Exception) {
                _state.update { 
                    it.copy(
                        isRefreshing = false,
                        error = e.message ?: "Failed to refresh library"
                    )
                }
            }
        }
    }

    private fun loadMore() {
        if (_state.value.isLoading || !_state.value.hasMoreData) return
        
        viewModelScope.launch {
            val currentState = _state.value
            val nextPage = currentState.currentPage + 1
            _state.update { it.copy(isLoading = true) }
            
            try {
                val offset = currentState.currentPage * PAGE_SIZE
                
                // Load more data based on current tab
                when (currentState.selectedTab) {
                    LibraryTab.ALBUMS -> {
                        val moreAlbums = libraryDataSource.getAlbums(limit = PAGE_SIZE, offset = offset)
                        _state.update { 
                            it.copy(
                                albums = it.albums + moreAlbums,
                                isLoading = false,
                                currentPage = nextPage,
                                hasMoreData = moreAlbums.size == PAGE_SIZE
                            )
                        }
                    }
                    LibraryTab.ARTISTS -> {
                        val moreArtists = libraryDataSource.getArtists(limit = PAGE_SIZE, offset = offset)
                        _state.update { 
                            it.copy(
                                artists = it.artists + moreArtists,
                                isLoading = false,
                                currentPage = nextPage,
                                hasMoreData = moreArtists.size == PAGE_SIZE
                            )
                        }
                    }
                    LibraryTab.TRACKS -> {
                        val moreTracks = libraryDataSource.getTracks(limit = PAGE_SIZE, offset = offset)
                        _state.update { 
                            it.copy(
                                tracks = it.tracks + moreTracks,
                                isLoading = false,
                                currentPage = nextPage,
                                hasMoreData = moreTracks.size == PAGE_SIZE
                            )
                        }
                    }
                    LibraryTab.GENRES -> {
                        val moreGenres = libraryDataSource.getGenres(limit = PAGE_SIZE, offset = offset)
                        _state.update { 
                            it.copy(
                                genres = it.genres + moreGenres,
                                isLoading = false,
                                currentPage = nextPage,
                                hasMoreData = moreGenres.size == PAGE_SIZE
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update { 
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load more"
                    )
                }
            }
        }
    }

    private fun navigateToSearch(query: String) {
        // Navigation will be handled by the UI layer
    }

    private fun navigateToAlbum(albumId: Long) {
        // Navigation will be handled by the UI layer
    }

    private fun navigateToArtist(artistId: Long) {
        // Navigation will be handled by the UI layer
    }

    private fun navigateToTrack(trackId: Long) {
        // Navigation will be handled by the UI layer
    }

    private fun navigateToGenre(genreId: Long) {
        // Navigation will be handled by the UI layer
    }

    private fun playAlbum(albumId: Long) {
        // Playback will be handled by the player repository
    }

    private fun playArtist(artistId: Long) {
        // Playback will be handled by the player repository
    }

    private fun playTrack(trackId: Long) {
        // Playback will be handled by the player repository
    }
}
