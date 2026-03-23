/*
 * Copyright (c) 2026 Amberesaiae. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amberesaiae.melos.core.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sealed class representing the playback state of the player.
 */
sealed class PlaybackState {
    /** Player is idle with no media loaded or after release. */
    data object Idle : PlaybackState()
    
    /** Player is buffering media content. */
    data object Buffering : PlaybackState()
    
    /** Player is ready and playing or paused. */
    data object Ready : PlaybackState()
    
    /** Playback has ended. */
    data object Ended : PlaybackState()
}

/**
 * Data class representing track metadata.
 *
 * @param id Unique identifier for the track
 * @param title Track title
 * @param artist Artist or creator name
 * @param album Album name (optional)
 * @param duration Track duration in milliseconds
 * @param artUri URI to album art or cover image (optional)
 */
data class TrackMetadata(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val duration: Long,
    val artUri: String? = null
)

/**
 * Sealed class for player-related exceptions.
 */
sealed class PlayerException(message: String, val code: Int) : Exception(message) {
    
    /** Error during media loading or preparation. */
    class MediaLoadError(message: String, code: Int = 1001) : PlayerException(message, code)
    
    /** Error during playback (e.g., decoder failure). */
    class PlaybackError(message: String, code: Int = 1002) : PlayerException(message, code)
    
    /** Error related to media source (e.g., unavailable URI). */
    class SourceError(message: String, code: Int = 1003) : PlayerException(message, code)
    
    /** General unrecognized player error. */
    class UnknownError(message: String, code: Int = 1000) : PlayerException(message, code)
    
    companion object {
        /**
         * Factory method to create appropriate PlayerException from ExoPlayer's PlaybackException.
         */
        fun from(playbackException: PlaybackException): PlayerException {
            return when (playbackException.errorCode) {
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ->
                    SourceError(playbackException.message ?: "Source not available", 1003)
                
                PlaybackException.ERROR_CODE_DECODING_FAILED,
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ->
                    PlaybackError(playbackException.message ?: "Decoder failure", 1002)
                
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_FORMAT_NOT_SUPPORTED,
                PlaybackException.ERROR_CODE_PARSING_MALFORMED ->
                    MediaLoadError(playbackException.message ?: "Media format error", 1001)
                
                else -> UnknownError(playbackException.message ?: "Unknown player error", 1000)
            }
        }
    }
}

/**
 * Represents the current playback state with additional metadata.
 *
 * @param state Current playback state (Idle, Buffering, Ready, Ended)
 * @param isPlaying Whether media is currently playing
 * @param currentPosition Current playback position in milliseconds
 * @param duration Total duration of current media in milliseconds
 * @param bufferedPosition Buffered position in milliseconds
 * @param currentTrack Current track metadata (null if no track)
 * @param queuePosition Current position in the playlist (0-based index)
 * @param queueSize Total number of items in the playlist
 * @param playbackSpeed Current playback speed (1.0 = normal)
 * @param error Optional error if in error state
 */
data class PlayerState(
    val state: PlaybackState = PlaybackState.Idle,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L,
    val currentTrack: TrackMetadata? = null,
    val queuePosition: Int = -1,
    val queueSize: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val error: PlayerException? = null
)

/**
 * Core player wrapper class providing a high-level interface to ExoPlayer.
 *
 * This class wraps Android's Media3 ExoPlayer with lazy initialization,
 * exposing playback state and controls through a clean, testable API.
 * All state changes are emitted via StateFlow for reactive UI updates.
 *
 * Features:
 * - Lazy ExoPlayer initialization for resource efficiency
 * - Reactive state management with StateFlow
 * - Comprehensive playback controls
 * - Error handling with typed exceptions
 * - Audio attributes optimized for music playback
 * - Hilt dependency injection support
 *
 * @param context Application context for player initialization
 *
 * Usage:
 * ```
 * val player = MelosPlayer(context)
 * player.init()
 * player.setMediaItems(listOf(mediaItem1, mediaItem2))
 * player.prepare()
 * player.play()
 *
 * // Observe state
 * lifecycleScope.launch {
 *     player.playerState.collect { state ->
 *         // Update UI
 *     }
 * }
 *
 * player.release() // Clean up when done
 * ```
 */
@OptIn(UnstableApi::class)
@Singleton
class MelosPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Lazy ExoPlayer instance for deferred initialization.
     * Initialized on first use or when [init] is called.
     */
    private val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setSeekForwardIncrementMs(DEFAULT_SEEK_INCREMENT_MS)
            .setSeekBackIncrementMs(DEFAULT_SEEK_INCREMENT_MS)
            .build()
    }

    /**
     * Audio attributes configured for music playback.
     * Optimized for continuous music streaming with appropriate usage and content type.
     */
    private val audioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    /**
     * MutableStateFlow holding the current player state.
     * Updated automatically via Player.Listener callbacks.
     */
    private val _playerState = MutableStateFlow(PlayerState())

    /**
     * StateFlow exposing current player state for UI observation.
     * Use with lifecycleScope or Compose StateFlow collectors.
     */
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    /**
     * MutableStateFlow exposing just the playback state.
     * Use when you only need to track Idle/Buffering/Ready/Ended.
     */
    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    /**
     * StateFlow exposing the current track metadata.
     * Null when no track is loaded.
     */
    private val _currentTrack = MutableStateFlow<TrackMetadata?>(null)
    val currentTrack: StateFlow<TrackMetadata?> = _currentTrack.asStateFlow()

    /**
     * StateFlow exposing the current queue position (0-based index).
     * -1 when no media items are loaded.
     */
    private val _queuePosition = MutableStateFlow(-1)
    val queuePosition: StateFlow<Int> = _queuePosition.asStateFlow()

    /**
     * Coroutine scope for async operations.
     * Uses SupervisorJob to prevent child failures from cancelling siblings.
     */
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Flag tracking whether player has been initialized.
     */
    private var isInitialized = false

    /**
     * Flag tracking whether player has been released.
     */
    private var isReleased = false

    /**
     * Player listener for capturing playback events and updating state.
     */
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            updatePlayerState { copy(state = playbackState.toPlaybackState()) }
            _playbackState.value = playbackState.toPlaybackState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayerState { copy(isPlaying = isPlaying) }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            updatePlayerState {
                copy(
                    currentPosition = newPosition.positionMs,
                    duration = newPosition.durationMs.coerceAtLeast(0L)
                )
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            exoPlayer.currentMediaItem?.let { item ->
                val metadata = item.mediaMetadata
                _currentTrack.value = TrackMetadata(
                    id = item.mediaId,
                    title = metadata.title?.toString() ?: "Unknown",
                    artist = metadata.artist?.toString() ?: "Unknown Artist",
                    album = metadata.albumTitle?.toString(),
                    duration = item.mediaMetadata.durationMs ?: C.TIME_UNSET,
                    artUri = metadata.artworkUri?.toString()
                )
            }
            
            updatePlayerState {
                copy(
                    queuePosition = exoPlayer.currentMediaItemIndex,
                    queueSize = exoPlayer.mediaItemCount
                )
            }
            _queuePosition.value = exoPlayer.currentMediaItemIndex
        }

        override fun onTracksChanged(tracks: Tracks) {
            // Can be used for track selection or audio format logging
        }

        override fun onBufferedPositionChanged(bufferedPosition: Long) {
            updatePlayerState { copy(bufferedPosition = bufferedPosition) }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            updatePlayerState { copy(playbackSpeed = playbackParameters.speed) }
        }

        override fun onPlayerError(error: PlaybackException) {
            val playerException = PlayerException.from(error)
            updatePlayerState { copy(error = playerException) }
        }
    }

    /**
     * Initialize the player and prepare ExoPlayer instance.
     * Must be called before any playback operations.
     *
     * This method:
     * - Lazily initializes the ExoPlayer instance
     * - Registers the player listener for state updates
     * - Sets initial player state
     *
     * @throws IllegalStateException if player is already released
     */
    fun init() {
        check(!isReleased) { "Cannot initialize a released player. Create a new instance." }
        
        if (isInitialized) {
            return // Already initialized
        }

        // Force lazy initialization by accessing exoPlayer
        val player = exoPlayer
        player.addListener(playerListener)
        
        // Update initial state
        updatePlayerState {
            copy(
                state = when (player.playbackState) {
                    Player.STATE_IDLE -> PlaybackState.Idle
                    Player.STATE_BUFFERING -> PlaybackState.Buffering
                    Player.STATE_READY -> PlaybackState.Ready
                    Player.STATE_ENDED -> PlaybackState.Ended
                    else -> PlaybackState.Idle
                }
            )
        }
        
        isInitialized = true
    }

    /**
     * Prepare the player with loaded media items.
     * Call after [setMediaItems] or [addMediaItem] to prepare for playback.
     *
     * This method starts the preparation process which may involve:
     * - Downloading or buffering media data
     * - Initializing decoders
     * - Analyzing audio/video streams
     *
     * The player will transition to [PlaybackState.Ready] when preparation completes.
     */
    fun prepare() {
        ensureInitialized()
        exoPlayer.prepare()
    }

    /**
     * Start or resume playback.
     * If preparation is pending, calling this will auto-prepare.
     *
     * Does nothing if already playing or in error state.
     */
    fun play() {
        ensureInitialized()
        if (_playerState.value.error != null) {
            return // Don't play if in error state
        }
        exoPlayer.play()
    }

    /**
     * Pause playback.
     * Maintains current position for later resumption.
     *
     * Does nothing if already paused or idle.
     */
    fun pause() {
        ensureInitialized()
        exoPlayer.pause()
    }

    /**
     * Stop playback and reset position to beginning.
     * Unlike pause, this resets the playback position to 0.
     *
     * Call [prepare] again to restart playback.
     */
    fun stop() {
        ensureInitialized()
        exoPlayer.stop()
    }

    /**
     * Seek to absolute position in milliseconds.
     *
     * @param positionMs Position in milliseconds to seek to (must be >= 0)
     *
     * @throws IllegalArgumentException if positionMs is negative
     */
    fun seekTo(positionMs: Long) {
        ensureInitialized()
        require(positionMs >= 0) { "Position must be non-negative, got $positionMs" }
        exoPlayer.seekTo(positionMs)
    }

    /**
     * Seek to position relative to current playback.
     *
     * @param offsetMs Offset in milliseconds from current position (positive or negative)
     */
    fun seekRelative(offsetMs: Long) {
        ensureInitialized()
        val newPosition = (exoPlayer.currentPosition + offsetMs).coerceIn(0, exoPlayer.duration)
        seekTo(newPosition)
    }

    /**
     * Seek forward by default increment (10 seconds).
     */
    fun seekForward() {
        ensureInitialized()
        seekRelative(DEFAULT_SEEK_INCREMENT_MS)
    }

    /**
     * Seek backward by default increment (10 seconds).
     */
    fun seekBack() {
        ensureInitialized()
        seekRelative(-DEFAULT_SEEK_INCREMENT_MS)
    }

    /**
     * Set the media items to play, replacing any existing items.
     *
     * @param tracks List of track URIs to play
     * @param startIndex Index to start playback from (default: 0)
     * @param startPositionMs Starting position in milliseconds (default: 0)
     *
     * Usage:
     * ```
     * player.setMediaItems(listOf(
     *     "https://example.com/song1.mp3",
     *     "https://example.com/song2.mp3"
     * ))
     * player.prepare()
     * ```
     */
    fun setMediaItems(
        tracks: List<String>,
        startIndex: Int = 0,
        startPositionMs: Long = 0L
    ) {
        ensureInitialized()
        require(tracks.isNotEmpty()) { "Tracks list cannot be empty" }
        
        val mediaItems = tracks.map { uri ->
            MediaItem.fromUri(uri)
        }
        
        exoPlayer.setMediaItems(mediaItems, startIndex, startPositionMs)
        
        updatePlayerState {
            copy(
                queuePosition = startIndex,
                queueSize = mediaItems.size,
                error = null // Clear error on new media
            )
        }
        _queuePosition.value = startIndex
    }

    /**
     * Set media items from pre-configured MediaItem objects.
     * Use this for advanced configuration with metadata, DRM, etc.
     *
     * @param items List of MediaItem objects
     * @param startIndex Index to start playback from (default: 0)
     * @param startPositionMs Starting position in milliseconds (default: 0)
     */
    fun setMediaItems(
        items: List<MediaItem>,
        startIndex: Int = 0,
        startPositionMs: Long = 0L
    ) {
        ensureInitialized()
        require(items.isNotEmpty()) { "Media items list cannot be empty" }
        
        exoPlayer.setMediaItems(items, startIndex, startPositionMs)
        
        updatePlayerState {
            copy(
                queuePosition = startIndex,
                queueSize = items.size,
                error = null
            )
        }
        _queuePosition.value = startIndex
    }

    /**
     * Add a single media item to the end of the playlist.
     *
     * @param trackUri URI of the track to add
     */
    fun addMediaItem(trackUri: String) {
        ensureInitialized()
        exoPlayer.addMediaItem(MediaItem.fromUri(trackUri))
        updatePlayerState { copy(queueSize = exoPlayer.mediaItemCount) }
        _queuePosition.value = exoPlayer.currentMediaItemIndex
    }

    /**
     * Add a pre-configured MediaItem to the playlist.
     *
     * @param item MediaItem to add
     */
    fun addMediaItem(item: MediaItem) {
        ensureInitialized()
        exoPlayer.addMediaItem(item)
        updatePlayerState { copy(queueSize = exoPlayer.mediaItemCount) }
        _queuePosition.value = exoPlayer.currentMediaItemIndex
    }

    /**
     * Add multiple media items at the specified index.
     *
     * @param index Index to insert items at (use mediaItemCount to append)
     * @param items List of MediaItem objects to add
     */
    fun addMediaItems(index: Int, items: List<MediaItem>) {
        ensureInitialized()
        exoPlayer.addMediaItems(index, items)
        updatePlayerState { copy(queueSize = exoPlayer.mediaItemCount) }
        _queuePosition.value = exoPlayer.currentMediaItemIndex
    }

    /**
     * Remove a media item from the playlist.
     *
     * @param index Index of item to remove (0-based)
     *
     * @throws IndexOutOfBoundsException if index is invalid
     */
    fun removeMediaItem(index: Int) {
        ensureInitialized()
        require(index in 0 until exoPlayer.mediaItemCount) {
            "Index $index out of range [0, ${exoPlayer.mediaItemCount})"
        }
        exoPlayer.removeItem(index)
        updatePlayerState { copy(queueSize = exoPlayer.mediaItemCount) }
        _queuePosition.value = exoPlayer.currentMediaItemIndex
    }

    /**
     * Clear all media items from the playlist.
     */
    fun clearMediaItems() {
        ensureInitialized()
        exoPlayer.clearMediaItems()
        updatePlayerState {
            copy(
                queuePosition = -1,
                queueSize = 0,
                currentTrack = null
            )
        }
        _queuePosition.value = -1
        _currentTrack.value = null
    }

    /**
     * Skip to next track in playlist.
     * Does nothing if no next track exists.
     */
    fun skipToNext() {
        ensureInitialized()
        exoPlayer.seekToNextMediaItem()
    }

    /**
     * Skip to previous track in playlist.
     * If current position > 3 seconds, seeks to beginning of current track instead.
     */
    fun skipToPrevious() {
        ensureInitialized()
        if (exoPlayer.currentPosition > 3000) {
            seekTo(0)
        } else {
            exoPlayer.seekToPreviousMediaItem()
        }
    }

    /**
     * Set playback speed.
     *
     * @param speed Playback speed multiplier (0.5f = half speed, 2.0f = double speed)
     *
     * @throws IllegalArgumentException if speed is <= 0 or > max speed
     */
    fun setPlaybackSpeed(speed: Float) {
        ensureInitialized()
        require(speed > 0f && speed <= MAX_PLAYBACK_SPEED) {
            "Speed must be between 0 and $MAX_PLAYBACK_SPEED, got $speed"
        }
        exoPlayer.setPlaybackParameters(PlaybackParameters(speed))
    }

    /**
     * Set playback pitch without affecting speed (if codec supports it).
     *
     * @param pitch Pitch multiplier (1.0f = normal pitch)
     */
    fun setPlaybackPitch(pitch: Float) {
        ensureInitialized()
        exoPlayer.setPlaybackParameters(PlaybackParameters(
            exoPlayer.playbackParameters.speed,
            pitch
        ))
    }

    /**
     * Get the current ExoPlayer instance for advanced operations.
     * Use sparingly and prefer the provided wrapper methods.
     *
     * @return The underlying ExoPlayer instance
     *
     * @throws IllegalStateException if player not initialized
     */
    fun getExoPlayer(): ExoPlayer {
        ensureInitialized()
        return exoPlayer
    }

    /**
     * Check if player is currently playing.
     *
     * @return true if playing, false otherwise
     */
    fun isPlaying(): Boolean {
        return exoPlayer.isPlaying
    }

    /**
     * Check if player has a next item.
     *
     * @return true if there's a next media item
     */
    fun hasNext(): Boolean {
        return exoPlayer.hasNextMediaItem()
    }

    /**
     * Check if player has a previous item.
     *
     * @return true if there's a previous media item
     */
    fun hasPrevious(): Boolean {
        return exoPlayer.hasPreviousMediaItem()
    }

    /**
     * Get current playback position in milliseconds.
     *
     * @return Current position in milliseconds
     */
    fun getCurrentPosition(): Long {
        return exoPlayer.currentPosition
    }

    /**
     * Get total duration of current media in milliseconds.
     *
     * @return Duration in milliseconds, or C.TIME_UNSET if unknown
     */
    fun getDuration(): Long {
        return exoPlayer.duration
    }

    /**
     * Get buffered position in milliseconds.
     *
     * @return Buffered position in milliseconds
     */
    fun getBufferedPosition(): Long {
        return exoPlayer.bufferedPosition
    }

    /**
     * Release the player and free all resources.
     * Must be called when player is no longer needed (e.g., in onDestroy).
     *
     * After calling this method, the player instance cannot be reused.
     * Create a new instance for further playback.
     */
    fun release() {
        if (isReleased) {
            return // Already released
        }
        
        if (isInitialized) {
            exoPlayer.removeListener(playerListener)
            exoPlayer.release()
        }
        
        coroutineScope.launch {
            _playerState.update { it.copy(state = PlaybackState.Idle) }
            _playbackState.emit(PlaybackState.Idle)
            _currentTrack.emit(null)
            _queuePosition.emit(-1)
        }
        
        isInitialized = false
        isReleased = true
    }

    /**
     * Update player state atomically.
     *
     * @param transform Lambda to transform current state
     */
    private fun updatePlayerState(transform: PlayerState.() -> PlayerState) {
        _playerState.update(transform)
    }

    /**
     * Ensure player is initialized before operations.
     *
     * @throws IllegalStateException if not initialized
     */
    private fun ensureInitialized() {
        check(isInitialized) {
            "Player not initialized. Call init() before playback operations."
        }
        check(!isReleased) {
            "Player has been released. Create a new instance."
        }
    }

    /**
     * Convert ExoPlayer state integer to PlaybackState sealed class.
     */
    private fun @receiver:Player.State Int.toPlaybackState(): PlaybackState {
        return when (this) {
            Player.STATE_IDLE -> PlaybackState.Idle
            Player.STATE_BUFFERING -> PlaybackState.Buffering
            Player.STATE_READY -> PlaybackState.Ready
            Player.STATE_ENDED -> PlaybackState.Ended
            else -> PlaybackState.Idle
        }
    }

    companion object {
        /** Default seek increment in milliseconds (10 seconds) */
        private const val DEFAULT_SEEK_INCREMENT_MS = 10_000L
        
        /** Maximum playback speed multiplier */
        private const val MAX_PLAYBACK_SPEED = 3.0f
    }
}
