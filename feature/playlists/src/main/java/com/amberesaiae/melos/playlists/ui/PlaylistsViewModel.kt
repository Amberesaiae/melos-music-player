package com.amberesaiae.melos.feature.playlists.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amberesaiae.melos.database.PlaylistDao
import com.amberesaiae.melos.database.PlaylistTrackDao
import com.amberesaiae.melos.database.TrackDao
import com.amberesaiae.melos.model.Playlist
import com.amberesaiae.melos.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val playlistTrackDao: PlaylistTrackDao,
    private val trackDao: TrackDao
) : ViewModel() {

    private val _state = MutableStateFlow(PlaylistsState())
    val state: StateFlow<PlaylistsState> = _state.asStateFlow()

    init {
        loadPlaylists()
    }

    fun onAction(action: PlaylistsAction) {
        when (action) {
            is PlaylistsAction.LoadPlaylists -> loadPlaylists()
            is PlaylistsAction.RefreshPlaylists -> refreshPlaylists()
            is PlaylistsAction.DeletePlaylist -> requestDelete(action.playlist)
            is PlaylistsAction.ConfirmDelete -> confirmDelete()
            is PlaylistsAction.CancelDelete -> cancelDelete()
            is PlaylistsAction.ShowCreateDialog -> showDialog()
            is PlaylistsAction.HideCreateDialog -> hideDialog()
            is PlaylistsAction.CreatePlaylist -> createPlaylist(action.name, action.description)
            is PlaylistsAction.NavigateToDetail -> { /* Handled by navigation */ }
        }
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val playlists = playlistDao.getAllPlaylists()
                val uiModels = playlists.map { playlist ->
                    val tracks = playlistTrackDao.getTracksForPlaylist(playlist.id)
                    val duration = tracks.sumOf { it.durationMs ?: 0L }
                    val coverArtIds = tracks.take(4).map { it.id }
                    PlaylistUiModel(
                        id = playlist.id,
                        name = playlist.name,
                        description = playlist.description,
                        trackCount = tracks.size,
                        totalDurationMs = duration,
                        coverArtTrackIds = coverArtIds,
                        createdAt = playlist.createdAt,
                        updatedAt = playlist.updatedAt
                    )
                }
                _state.update {
                    it.copy(
                        playlists = uiModels.sortedByDescending { p -> p.updatedAt },
                        isLoading = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load playlists: ${e.message}"
                    )
                }
            }
        }
    }

    private fun refreshPlaylists() {
        loadPlaylists()
    }

    private fun requestDelete(playlist: PlaylistUiModel) {
        _state.update {
            it.copy(
                showDeleteConfirmation = true,
                playlistToDelete = playlist
            )
        }
    }

    private fun confirmDelete() {
        val playlistToDelete = _state.value.playlistToDelete ?: return
        _state.update { it.copy(showDeleteConfirmation = false) }
        
        viewModelScope.launch {
            try {
                playlistTrackDao.deleteAllTracksFromPlaylist(playlistToDelete.id)
                playlistDao.deletePlaylist(playlistToDelete.id)
                loadPlaylists()
            } catch (e: Exception) {
                _state.update {
                    it.copy(error = "Failed to delete playlist: ${e.message}")
                }
            }
        }
    }

    private fun cancelDelete() {
        _state.update {
            it.copy(
                showDeleteConfirmation = false,
                playlistToDelete = null,
                error = null
            )
        }
    }

    private fun showDialog() {
        _state.update { it.copy(showCreateDialog = true) }
    }

    private fun hideDialog() {
        _state.update { it.copy(showCreateDialog = false, error = null) }
    }

    private fun createPlaylist(name: String, description: String?) {
        if (name.isBlank()) {
            _state.update { it.copy(error = "Playlist name is required") }
            return
        }
        
        viewModelScope.launch {
            try {
                val playlist = Playlist(
                    name = name.trim(),
                    description = description?.trim(),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                val playlistId = playlistDao.insertPlaylist(playlist)
                
                _state.update { it.copy(showCreateDialog = false) }
                loadPlaylists()
            } catch (e: Exception) {
                _state.update {
                    it.copy(error = "Failed to create playlist: ${e.message}")
                }
            }
        }
    }
}
