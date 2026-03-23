/*
 * Copyright (C) 2024 Amberesaiae. All rights reserved.
 * SPDX-License-Identifier: MIT
 */
package com.amberesaiae.melos.player.ui

import androidx.media3.common.PlaybackException
import com.amberesaiae.melos.player.queue.QueueItem
import com.amberesaiae.melos.player.queue.RepeatMode
import com.amberesaiae.melos.player.queue.ShuffleMode

/**
 * UI state for the Now Playing screen.
 *
 * @param currentTrack Currently playing track
 * @param isPlaying Whether playback is active
 * @param currentPosition Current playback position in milliseconds
 * @param duration Total track duration in milliseconds
 * @param bufferedPosition Buffered position in milliseconds
 * @param shuffleMode Current shuffle mode
 * @param repeatMode Current repeat mode
 * @param playbackSpeed Current playback speed (0.5x - 2.0x)
 * @param isBuffering Whether the player is buffering
 * @param error Any playback error
 * @param queue Queue of upcoming tracks
 * @param currentIndex Index of current track in queue
 */
data class NowPlayingState(
    val currentTrack: QueueItem? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L,
    val shuffleMode: ShuffleMode = ShuffleMode.OFF,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val playbackSpeed: Float = 1.0f,
    val isBuffering: Boolean = false,
    val error: PlaybackException? = null,
    val queue: List<QueueItem> = emptyList(),
    val currentIndex: Int = -1
) {
    /**
     * Check if there's content to display.
     */
    val hasContent: Boolean get() = currentTrack != null

    /**
     * Progress percentage (0-100).
     */
    val progressPercent: Float
        get() = if (duration > 0) {
            (currentPosition.toFloat() / duration.toFloat()) * 100f
        } else 0f

    /**
     * Buffer progress percentage (0-100).
     */
    val bufferPercent: Float
        get() = if (duration > 0) {
            (bufferedPosition.toFloat() / duration.toFloat()) * 100f
        } else 0f

    /**
     * Format current position as mm:ss.
     */
    val currentPositionFormatted: String
        get() = formatTime(currentPosition)

    /**
     * Format duration as mm:ss.
     */
    val durationFormatted: String
        get() = formatTime(duration)

    /**
     * Get next track in queue.
     */
    val nextTrack: QueueItem?
        get() = if (currentIndex >= 0 && currentIndex < queue.size - 1) {
            queue[currentIndex + 1]
        } else null

    /**
     * Get previous track in queue.
     */
    val previousTrack: QueueItem?
        get() = if (currentIndex > 0) {
            queue[currentIndex - 1]
        } else null

    companion object {
        /**
         * Format milliseconds as mm:ss or hh:mm:ss.
         */
        fun formatTime(ms: Long): String {
            val totalSeconds = ms / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60

            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
    }
}

/**
 * Actions for the Now Playing screen.
 */
sealed class NowPlayingAction {
    data object PlayPause : NowPlayingAction()
    data object Play : NowPlayingAction()
    data object Pause : NowPlayingAction()
    data object Next : NowPlayingAction()
    data object Previous : NowPlayingAction()
    data object Stop : NowPlayingAction()
    data class Seek(val positionMs: Long) : NowPlayingAction()
    data class SetShuffleMode(val mode: ShuffleMode) : NowPlayingAction()
    data class SetRepeatMode(val mode: RepeatMode) : NowPlayingAction()
    data class SetPlaybackSpeed(val speed: Float) : NowPlayingAction()
    data class PlayTrack(val track: QueueItem) : NowPlayingAction()
    data class RemoveFromQueue(val trackId: String) : NowPlayingAction()
    data class MoveInQueue(val fromIndex: Int, val toIndex: Int) : NowPlayingAction()
    data object ShowEqualizer : NowPlayingAction()
    data object ShowQueue : NowPlayingAction()
    data object DismissQueue : NowPlayingAction()
}
