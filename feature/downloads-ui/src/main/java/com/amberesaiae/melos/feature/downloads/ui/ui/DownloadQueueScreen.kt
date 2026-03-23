@file:Suppress("kotlin:S6290", "kotlin:S6701")

package com.amberesaiae.melos.feature.downloads.ui.ui

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.amberesaiae.melos.core.model.DownloadItem
import com.amberesaiae.melos.core.model.DownloadStatus

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DownloadQueueScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var expandedMenu by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadDownloads()
    }

    Scaffold(
        topBar = {
            DownloTopAppBar()
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.activeDownloads.isNotEmpty()) {
                ActiveDownloadsSection(
                    downloads = uiState.activeDownloads,
                    onPauseClick = viewModel::pauseDownload,
                    onResumeClick = viewModel::resumeDownload,
                    onCancelClick = viewModel::cancelDownload,
                    onMenuClick = { expandedMenu = it }
                )
            }

            DownloBottomSheet(extendedId = expandedMenu) { downloadId, action ->
                when (action) {
                    DownloadAction.PAUSE -> viewModel.pauseDownload(downloadId)
                    DownloadAction.RESUME -> viewModel.resumeDownload(downloadId)
                    DownloadAction.CANCEL -> viewModel.cancelDownload(downloadId)
                }
                expandedMenu = null
            }

            if (uiState.completedDownloads.isNotEmpty()) {
                CompletedDownloadsSection(
                    downloads = uiState.completedDownloads,
                    onDeleteClick = viewModel::removeCompletedDownload
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadTopAppBar() {
    TopAppBar(
        title = { Text("Downloads") },
        navigationIcon = {
            IconButton(onClick = { /* Navigate back */ }) {
                Icon(Icons.Default.Download, contentDescription = "Back")
            }
        }
    )
}

@Composable
private fun ActiveDownloadsSection(
    downloads: List<DownloadItem>,
    onPauseClick: (String) -> Unit,
    onResumeClick: (String) -> Unit,
    onCancelClick: (String) -> Unit,
    onMenuClick: (String) -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Active Downloads",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row {
                IconButton(onClick = { /* Pause all */ }) {
                    Icon(Icons.Default.Pause, contentDescription = "Pause All")
                }
                IconButton(onClick = { /* Resume all */ }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Resume All")
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
        ) {
            items(downloads, key = { it.id }) { download ->
                DownloadItemCard(
                    download = download,
                    onPauseClick = { onPauseClick(download.id) },
                    onResumeClick = { onResumeClick(download.id) },
                    onCancelClick = { onCancelClick(download.id) },
                    onMenuClick = { onMenuClick(download.id) }
                )
            }
        }
    }
}

@Composable
private fun DownloadItemCard(
    download: DownloadItem,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onCancelClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArt(
                imageUrl = download.albumArtUrl,
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialTheme.shapes.small)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = download.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = download.artist ?: "Unknown Artist",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = (download.downloadedBytes.toFloat() / download.totalBytes.coerceAtLeast(1)).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = MaterialTheme.colorScheme.outlineVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${formatFileSize(download.downloadedBytes)} / ${formatFileSize(download.totalBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DownloadActions(
                download = download,
                onPauseClick = onPauseClick,
                onResumeClick = onResumeClick,
                onCancelClick = onCancelClick,
                onMenuClick = onMenuClick
            )
        }
    }
}

@Composable
private fun DownloadActions(
    download: DownloadItem,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onCancelClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    when (download.status) {
        DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
            IconButton(onClick = onPauseClick) {
                Icon(Icons.Default.Pause, contentDescription = "Pause")
            }
        }
        DownloadStatus.PAUSED -> {
            IconButton(onClick = onResumeClick) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
            }
        }
        else -> {}
    }

    if (download.status !in listOf(DownloadStatus.COMPLETED, DownloadStatus.CANCELLED)) {
        IconButton(onClick = onCancelClick) {
            Icon(Icons.Default.Cancel, contentDescription = "Cancel")
        }
    } else if (download.status == DownloadStatus.COMPLETED) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Completed",
            tint = Color.Green,
            modifier = Modifier.padding(8.dp)
        )
    }

    if (download.status != DownloadStatus.COMPLETED) {
        Box {
            var showDropdown by remember { mutableStateOf(false) }
            IconButton(onClick = { showDropdown = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More")
            }
            DropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false }
            ) {
                DropdownMenuItem(
                    text = { Text(if (download.status == DownloadStatus.PAUSED) "Resume" else "Pause") },
                    onClick = {
                        if (download.status == DownloadStatus.PAUSED) onResumeClick() else onPauseClick()
                        showDropdown = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Cancel") },
                    onClick = {
                        onCancelClick()
                        showDropdown = false
                    }
                )
            }
        }
    }
}

@Composable
private fun CompletedDownloadsSection(
    downloads: List<DownloadItem>,
    onDeleteClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp)
    ) {
        Text(
            text = "Completed Downloads",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp,
                vertical = 8.dp
            )
        ) {
            items(downloads, key = { it.id }) { download ->
                CompletedDownloadItem(
                    download = download,
                    onDeleteClick = { onDeleteClick(download.id) }
                )
            }
        }
    }
}

@Composable
private fun CompletedDownloadItem(
    download: DownloadItem,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArt(
                imageUrl = download.albumArtUrl,
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = download.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatFileSize(download.totalBytes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Cancel, contentDescription = "Remove")
            }
        }
    }
}

@Composable
private fun AlbumArt(
    imageUrl: Uri?,
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader? = null
) {
    AsyncImage(
        model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
            .data(imageUrl)
            .crossfade(true)
            .build(),
        contentDescription = "Album Art",
        modifier = modifier,
        contentScale = ContentScale.Crop,
        imageLoader = imageLoader
    )
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
    }
}

enum class DownloadAction {
    PAUSE, RESUME, CANCEL
}
