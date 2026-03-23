package com.amberesaiae.melos.feature.playlists.ui

import com.amberesaiae.melos.model.Track
import com.amberesaiae.melos.model.Playlist

/**
 * Represents the UI state for the playlists screen
 */
data class PlaylistsState(
    val playlists: List<PlaylistUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showDeleteConfirmation: Boolean = false,
    val playlistToDelete: PlaylistUiModel? = null,
    val showCreateDialog: Boolean = false
)

/**
 * UI-optimized playlist model
 */
data class PlaylistUiModel(
    val id: Long,
    val name: String,
    val description: String? = null,
    val trackCount: Int = 0,
    val totalDurationMs: Long = 0L,
    val coverArtTrackIds: List<Long> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Represents the UI state for a single playlist detail screen
 */
data class PlaylistDetailState(
    val playlistId: Long,
    val playlist: PlaylistUiModel? = null,
    val tracks: List<PlaylistTrackItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isEditing: Boolean = false,
    val editedName: String = "",
    val editedDescription: String = "",
    val showAddTracksDialog: Boolean = false,
    val draggingTrackId: Long? = null
)

/**
 * Track item for playlist detail
 */
data class PlaylistTrackItem(
    val track: Track,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis()
)

/**
 * Sealed class for all possible actions on the playlists screen
 */
sealed class PlaylistsAction {
    data object LoadPlaylists : PlaylistsAction()
    data object RefreshPlaylists : PlaylistsAction()
    data class DeletePlaylist(val playlist: PlaylistUiModel) : PlaylistsAction()
    data object ConfirmDelete : PlaylistsAction()
    data object CancelDelete : PlaylistsAction()
    data object ShowCreateDialog : PlaylistsAction()
    data object HideCreateDialog : PlaylistsAction()
    data class NavigateToDetail(val playlistId: Long) : PlaylistsAction()
    data class CreatePlaylist(val name: String, val description: String?) : PlaylistsAction()
}

/**
 * Sealed class for playlist detail screen actions
 */
sealed class PlaylistDetailAction {
    data class LoadPlaylist(val playlistId: Long) : PlaylistDetailAction()
    data object ToggleEditMode : PlaylistDetailAction()
    data object SaveEdit : PlaylistDetailAction()
    data object CancelEdit : PlaylistDetailAction()
    data class UpdateName(val name: String) : PlaylistDetailAction()
    data class UpdateDescription(val description: String) : PlaylistDetailAction()
    data class RemoveTrack(val trackId: Long, val position: Int) : PlaylistDetailAction()
    data class StartDrag(val trackId: Long) : PlaylistDetailAction()
    data class StopDrag : PlaylistDetailAction()
    data class MoveTrack(val fromPosition: Int, val toPosition: Int) : PlaylistDetailAction()
    data object ShowAddTracksDialog : PlaylistDetailAction()
    data object HideAddTracksDialog : PlaylistDetailAction()
    data class AddTrack(val track: Track) : PlaylistDetailAction()
}
