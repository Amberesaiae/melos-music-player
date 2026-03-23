@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.amberesaiae.melos.feature.playlists.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.launch

/**
 * A track row component for playlist detail screen with drag handle and swipe actions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistItem(
    trackItem: PlaylistTrackItem,
    index: Int,
    onRemoveClick: () -> Unit,
    onPlayClick: () -> Unit = {},
    isDragging: Boolean = false,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val track = trackItem.track
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .combinedClickable(
                onClick = onPlayClick,
                onLongClick = onRemoveClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag handle
            IconButton(
                onClick = { },
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDrag = { _, _ -> }
                        )
                    }
            ) {
                Icon(
                    imageVector = Icons.Filled.DragHandle,
                    contentDescription = "Drag to reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Track thumbnail or placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (track.albumArtUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(track.albumArtUri)
                            .build(),
                        contentDescription = "Album art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Track info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = track.title ?: "Unknown Title",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Text(
                    text = track.artist ?: "Unknown Artist",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Duration
            Text(
                text = formatDuration(track.durationMs ?: 0L),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            
            // More options
            IconButton(
                onClick = onRemoveClick
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Simple duration formatter
 */
private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
