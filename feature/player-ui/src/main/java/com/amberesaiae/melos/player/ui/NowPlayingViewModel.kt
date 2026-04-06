/*
 * Copyright (C) 2024 Amberesaiae. All rights reserved.
 * SPDX-License-Identifier: MIT
 */
package com.amberesaiae.melos.player.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amberesaiae.melos.player.MelosPlayer
import com.amberesaiae.melos.player.queue.QueueItem
import com.amberesaiae.melos.player.queue.RepeatMode
import com.amberesaiae.melos.player.queue.ShuffleMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Now Playing screen.
 * 
 * Observes player state and exposes actions for UI interactions.
 */
@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val application: Application,
    private val player: MelosPlayer
) : ViewModel() {

    private val _state = MutableStateFlow(NowPlayingState())
    val state: StateFlow<NowPlayingState> = _state.asStateFlow()

    private val _queueSheetVisible = MutableStateFlow(false)
    val queueSheetVisible: StateFlow<Boolean> = _queueSheetVisible.asStateFlow()

    init {
        observePlayerState()
    }

    /**
     * Observe player state updates.
     */
    private fun observePlayerState() {
        viewModelScope.launch {
            player.playerState.collect { playerState ->
                _state.update { currentState ->
                    currentState.copy(
                        currentTrack = playerState.currentTrack,
                        isPlaying = playerState.isPlaying,
                        currentPosition = playerState.currentPosition,
                        duration = playerState.duration,
                        bufferedPosition = playerState.bufferedPosition,
                        shuffleMode = playerState.shuffleMode,
                        repeatMode = playerState.repeatMode,
                        playbackSpeed = playerState.playbackSpeed,
                        isBuffering = playerState.isPlaying && playerState.isLoading,
                        error = playerState.error,
                        queue = playerState.queueItems,
                        currentIndex = playerState.currentTrackIndex
                    )
                }
            }
        }
    }

    /**
     * Handle UI actions.
     */
    fun onAction(action: NowPlayingAction) {
        when (action) {
            is NowPlayingAction.PlayPause -> {
                if (_state.value.isPlaying) {
                    player.pause()
                } else {
                    player.play()
                }
            }
            is NowPlayingAction.Play -> player.play()
            is NowPlayingAction.Pause -> player.pause()
            is NowPlayingAction.Next -> player.skipToNext()
            is NowPlayingAction.Previous -> player.skipToPrevious()
            is NowPlayingAction.Stop -> player.stop()
            is NowPlayingAction.Seek -> player.seekTo(action.positionMs)
            is NowPlayingAction.SetShuffleMode -> {
                player.setShuffleMode(action.mode)
                _state.update { it.copy(shuffleMode = action.mode) }
            }
            is NowPlayingAction.SetRepeatMode -> {
                player.setRepeatMode(action.mode)
                _state.update { it.copy(repeatMode = action.mode) }
            }
            is NowPlayingAction.SetPlaybackSpeed -> {
                player.setPlaybackSpeed(action.speed)
                _state.update { it.copy(playbackSpeed = action.speed) }
            }
            is NowPlayingAction.PlayTrack -> {
                player.playTrack(action.track)
            }
            is NowPlayingAction.RemoveFromQueue -> {
                player.removeFromQueue(action.trackId)
            }
            is NowPlayingAction.MoveInQueue -> {
                player.moveQueueItem(action.fromIndex, action.toIndex)
            }
            is NowPlayingAction.ShowEqualizer -> {
                // TODO: Navigate to equalizer screen
            }
            is NowPlayingAction.ShowQueue -> {
                _queueSheetVisible.value = true
            }
            is NowPlayingAction.DismissQueue -> {
                _queueSheetVisible.value = false
            }
        }
    }

    /**
     * Toggle shuffle mode.
     */
    fun toggleShuffle() {
        val currentMode = _state.value.shuffleMode
        val newMode = when (currentMode) {
            ShuffleMode.OFF -> ShuffleMode.ALL
            ShuffleMode.ALL -> ShuffleMode.GROUP
            ShuffleMode.GROUP -> ShuffleMode.OFF
        }
        onAction(NowPlayingAction.SetShuffleMode(newMode))
    }

    /**
     * Toggle repeat mode.
     */
    fun toggleRepeat() {
        val currentMode = _state.value.repeatMode
        val newMode = when (currentMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        onAction(NowPlayingAction.SetRepeatMode(newMode))
    }

    /**
     * Set playback speed.
     */
    fun setPlaybackSpeed(speed: Float) {
        onAction(NowPlayingAction.SetPlaybackSpeed(speed))
    }

    /**
     * Seek to position.
     */
    fun seekTo(positionMs: Long) {
        onAction(NowPlayingAction.Seek(positionMs))
    }

    /**
     * Play a specific track from queue.
     */
    fun playTrack(track: QueueItem) {
        onAction(NowPlayingAction.PlayTrack(track))
    }

    /**
     * Remove track from queue.
     */
    fun removeFromQueue(trackId: String) {
        onAction(NowPlayingAction.RemoveFromQueue(trackId))
    }

    /**
     * Move track in queue.
     */
    fun moveInQueue(fromIndex: Int, toIndex: Int) {
        onAction(NowPlayingAction.MoveInQueue(fromIndex, toIndex))
    }

    /**
     * Show queue bottom sheet.
     */
    fun showQueue() {
        onAction(NowPlayingAction.ShowQueue)
    }

    /**
     * Hide queue bottom sheet.
     */
    fun dismissQueue() {
        onAction(NowPlayingAction.DismissQueue)
    }

    override fun onCleared() {
        super.onCleared()
        // Player lifecycle is managed by Hilt/Application
    }
}
