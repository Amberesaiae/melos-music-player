package com.amberesaiae.melos.feature.library.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArtTrack
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.PullRefreshIndicator
import androidx.compose.material3.pullrefresh.pullRefresh
import androidx.compose.material3.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amberesaiae.melos.feature.library.ui.components.AlbumListItem
import com.amberesaiae.melos.feature.library.ui.components.ArtistListItem
import com.amberesaiae.melos.feature.library.ui.components.GenreListItem
import com.amberesaiae.melos.feature.library.ui.components.TrackListItem
import com.amberesaiae.melos.ui.theme.MelosTheme

/**
 * Main Library Screen with tabs for Albums, Artists, Tracks, and Genres
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    onNavigateToSearch: (String) -> Unit = {},
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (Long) -> Unit = {},
    onNavigateToTrack: (Long) -> Unit = {},
    onNavigateToGenre: (Long) -> Unit = {},
    onPlayAlbum: (Long) -> Unit = {},
    onPlayArtist: (Long) -> Unit = {},
    onPlayTrack: (Long) -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isRefreshing,
        onRefresh = { viewModel.onAction(LibraryAction.Refresh) }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    IconButton(onClick = { 
                        viewModel.onAction(LibraryAction.NavigateToSearch(state.searchQuery))
                        onNavigateToSearch(state.searchQuery)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pullRefresh(pullRefreshState)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Tab Row
                PrimaryScrollableTabRow(
                    selectedTabIndex = state.selectedTab.ordinal,
                    edgePadding = 0.dp
                ) {
                    LibraryTab.entries.forEach { tab ->
                        Tab(
                            selected = state.selectedTab == tab,
                            onClick = { viewModel.onAction(LibraryAction.SelectTab(tab)) },
                            icon = {
                                Icon(
                                    imageVector = when (tab) {
                                        LibraryTab.ALBUMS -> Icons.Default.Album
                                        LibraryTab.ARTISTS -> Icons.Default.Mic
                                        LibraryTab.TRACKS -> Icons.Default.ArtTrack
                                        LibraryTab.GENRES -> Icons.Default.LibraryMusic
                                    },
                                    contentDescription = null
                                )
                            },
                            text = {
                                Text(
                                    text = when (tab) {
                                        LibraryTab.ALBUMS -> "Albums"
                                        LibraryTab.ARTISTS -> "Artists"
                                        LibraryTab.TRACKS -> "Tracks"
                                        LibraryTab.GENRES -> "Genres"
                                    },
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }

                HorizontalDivider()

                // Content based on selected tab
                when (state.selectedTab) {
                    LibraryTab.ALBUMS -> AlbumsTabContent(
                        albums = state.albums,
                        isLoading = state.isLoading,
                        onAlbumClick = { viewModel.onAction(LibraryAction.NavigateToAlbum(it))
                                        onNavigateToAlbum(it) },
                        onPlayAlbum = { viewModel.onAction(LibraryAction.PlayAlbum(it))
                                       onPlayAlbum(it) }
                    )
                    LibraryTab.ARTISTS -> ArtistsTabContent(
                        artists = state.artists,
                        isLoading = state.isLoading,
                        onArtistClick = { viewModel.onAction(LibraryAction.NavigateToArtist(it))
                                         onNavigateToArtist(it) },
                        onPlayArtist = { viewModel.onAction(LibraryAction.PlayArtist(it))
                                        onPlayArtist(it) }
                    )
                    LibraryTab.TRACKS -> TracksTabContent(
                        tracks = state.tracks,
                        isLoading = state.isLoading,
                        onTrackClick = { viewModel.onAction(LibraryAction.NavigateToTrack(it))
                                        onNavigateToTrack(it) },
                        onPlayTrack = { viewModel.onAction(LibraryAction.PlayTrack(it))
                                       onPlayTrack(it) }
                    )
                    LibraryTab.GENRES -> GenresTabContent(
                        genres = state.genres,
                        isLoading = state.isLoading,
                        onGenreClick = { viewModel.onAction(LibraryAction.NavigateToGenre(it))
                                        onNavigateToGenre(it) }
                    )
                }
            }

            // Pull to refresh indicator
            PullRefreshIndicator(
                refreshing = state.isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // Loading indicator
            if (state.isLoading && !state.isRefreshing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            // Error state
            state.error?.let { error ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumsTabContent(
    albums: List<com.amberesaiae.melos.model.Album>,
    isLoading: Boolean,
    onAlbumClick: (Long) -> Unit,
    onPlayAlbum: (Long) -> Unit
) {
    if (albums.isEmpty() && !isLoading) {
        EmptyState(title = "No Albums", message = "Your music library is empty")
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        contentPadding = PaddingValues(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = albums,
            key = { it.id }
        ) { album ->
            AlbumListItem(
                album = album,
                onClick = { onAlbumClick(album.id) },
                onPlay = { onPlayAlbum(album.id) }
            )
        }
    }
}

@Composable
private fun ArtistsTabContent(
    artists: List<com.amberesaiae.melos.model.Artist>,
    isLoading: Boolean,
    onArtistClick: (Long) -> Unit,
    onPlayArtist: (Long) -> Unit
) {
    if (artists.isEmpty() && !isLoading) {
        EmptyState(title = "No Artists", message = "No artists in your library")
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = artists,
            key = { it.id }
        ) { artist ->
            ArtistListItem(
                artist = artist,
                onClick = { onArtistClick(artist.id) },
                onPlay = { onPlayArtist(artist.id) }
            )
        }
    }
}

@Composable
private fun TracksTabContent(
    tracks: List<com.amberesaiae.melos.model.Track>,
    isLoading: Boolean,
    onTrackClick: (Long) -> Unit,
    onPlayTrack: (Long) -> Unit
) {
    if (tracks.isEmpty() && !isLoading) {
        EmptyState(title = "No Tracks", message = "No tracks in your library")
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = tracks,
            key = { it.id }
        ) { track ->
            TrackListItem(
                track = track,
                onClick = { onTrackClick(track.id) },
                onPlay = { onPlayTrack(track.id) }
            )
        }
    }
}

@Composable
private fun GenresTabContent(
    genres: List<com.amberesaiae.melos.model.Genre>,
    isLoading: Boolean,
    onGenreClick: (Long) -> Unit
) {
    if (genres.isEmpty() && !isLoading) {
        EmptyState(title = "No Genres", message = "No genres in your library")
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = genres,
            key = { it.id }
        ) { genre ->
            GenreListItem(
                genre = genre,
                onClick = { onGenreClick(genre.id) }
            )
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    message: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.LibraryMusic,
                contentDescription = null,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .fillMaxWidth(0.5f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LibraryScreenPreview() {
    MelosTheme {
        LibraryScreen()
    }
}
