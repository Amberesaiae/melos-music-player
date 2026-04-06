/*
 * Copyright (C) 2024 Amberesaiae. All rights reserved.
 * SPDX-License-Identifier: MIT
 */
package com.amberesaiae.melos.player.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetValue
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Now Playing screen with full-screen album art and playback controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    viewModel: NowPlayingViewModel = hiltViewModel(),
    onNavigateToEqualizer: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val queueSheetVisible by viewModel.queueSheetVisible.collectAsState()
    val scaffoldState = rememberBottomSheetScaffoldState()
    val scope = rememberCoroutineScope()

    var showSpeedMenu by remember { mutableStateOf(false) }
    var isTrackInfoExpanded by remember { mutableStateOf(true) }

    // Animation for play/pause button
    val playPauseScale = remember { Animatable(1f) }

    LaunchedEffect(state.isPlaying) {
        playPauseScale.animateTo(
            targetValue = if (state.isPlaying) 1.2f else 1f,
            animationSpec = tween(150)
        )
        playPauseScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(150)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Blur background from album art
        state.currentTrack?.artUri?.let { artUri ->
            AsyncImage(
                model = artUri,
                contentDescription = "Album art blur background",
                modifier = Modifier
                    .fillMaxSize()
                    .blur(50.dp)
                    .alpha(0.5f),
                contentScale = ContentScale.Crop
            )
        }

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.6f),
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.8f)
                        )
                    )
                )
        )

        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar with queue button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { 
                        scope.launch {
                            scaffoldState.bottomSheetState.expand()
                            viewModel.showQueue()
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))
                ) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "Show queue",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Collapsible track info
                AnimatedVisibility(
                    visible = isTrackInfoExpanded,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    state.currentTrack?.let { track ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            track.album?.let { album ->
                                Text(
                                    text = album,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = { isTrackInfoExpanded = !isTrackInfoExpanded },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                ) {
                    Icon(
                        imageVector = if (isTrackInfoExpanded) 
                            Icons.Default.Speed else Icons.Outlined.Speed,
                        contentDescription = if (isTrackInfoExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onNavigateToEqualizer,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = "Equalizer",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Album art
            state.currentTrack?.artUri?.let { artUri ->
                val screenMinDimension = minOf(
                    LocalConfiguration.current.screenWidthDp.dp,
                    LocalConfiguration.current.screenHeightDp.dp
                )
                val artSize = screenMinDimension * 0.7f

                AsyncImage(
                    model = artUri,
                    contentDescription = "Album art",
                    modifier = Modifier
                        .size(artSize)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Seek bar with buffer progress
            if (state.hasContent) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    // Buffer progress
                    if (state.bufferedPosition > state.currentPosition) {
                        LinearProgressIndicator(
                            progress = state.bufferPercent / 100f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            trackColor = Color.Transparent,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    }

                    // Seek slider
                    Slider(
                        value = state.currentPosition.toFloat(),
                        onValueChange = { viewModel.seekTo(it.toLong()) },
                        valueRange = 0f..if (state.duration > 0) state.duration.toFloat() else 100f,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    )
                }

                // Time stamps
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = state.currentPositionFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = state.durationFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Playback controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle button
                IconButton(
                    onClick = { viewModel.toggleShuffle() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlaylistPlay,
                        contentDescription = "Shuffle",
                        tint = if (state.shuffleMode != ShuffleMode.OFF) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Previous button
                IconButton(
                    onClick = { viewModel.onAction(NowPlayingAction.Previous) },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Play/Pause button
                IconButton(
                    onClick = { viewModel.onAction(NowPlayingAction.PlayPause) },
                    modifier = Modifier
                        .size(72.dp)
                        .graphicsLayer {
                            scaleX = playPauseScale.value
                            scaleY = playPauseScale.value
                        },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.Play,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Next button
                IconButton(
                    onClick = { viewModel.onAction(NowPlayingAction.Next) },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Repeat button
                IconButton(
                    onClick = { viewModel.toggleRepeat() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = when (state.repeatMode) {
                            RepeatMode.ONE -> Icons.Default.RepeatOne
                            RepeatMode.ALL -> Icons.Default.Repeat
                            RepeatMode.OFF -> Icons.Outlined.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (state.repeatMode != RepeatMode.OFF) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Speed control
            Box {
                IconButton(
                    onClick = { showSpeedMenu = true },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))
                ) {
                    Icon(
                        imageVector = if (state.playbackSpeed != 1.0f) 
                            Icons.Default.Speed else Icons.Outlined.Speed,
                        contentDescription = "Playback speed",
                        tint = if (state.playbackSpeed != 1.0f) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                DropdownMenu(
                    expanded = showSpeedMenu,
                    onDismissRequest = { showSpeedMenu = false },
                    modifier = Modifier.wrapContentSize()
                ) {
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { speed ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = String.format("%.2fx", speed),
                                        style = if (speed == state.playbackSpeed) 
                                            MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold) 
                                        else 
                                            MaterialTheme.typography.bodyMedium
                                    )
                                    if (speed == state.playbackSpeed) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            },
                            onClick = {
                                viewModel.setPlaybackSpeed(speed)
                                showSpeedMenu = false
                            }
                        )
                    }
                }
            }
        }

        // Queue bottom sheet
        if (queueSheetVisible) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.dismissQueue() },
                sheetState = rememberModalBottomSheetState(
                    skipPartiallyExpanded = false
                )
            ) {
                QueueBottomSheet(
                    state = state,
                    onDismiss = { viewModel.dismissQueue() },
                    onPlayTrack = { viewModel.playTrack(it) },
                    onRemoveFromQueue = { viewModel.removeFromQueue(it) },
                    onMoveInQueue = { from, to -> viewModel.moveInQueue(from, to) }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NowPlayingScreenPreview() {
    MaterialTheme {
        NowPlayingScreen()
    }
}
