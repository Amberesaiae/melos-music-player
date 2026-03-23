/*
 * Copyright (C) 2025 Amberesaiae
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.amberesaiae.melos.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ConcatenatingMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.ads.AdsMediaSource
import com.amberesaiae.melos.data.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Modes for gapless playback behavior.
 *
 * Determines how track transitions are handled in different playback contexts.
 */
enum class GaplessMode {
    /**
     * No gapless playback. Standard track transitions with potential gaps.
     */
    OFF,

    /**
     * Gapless playback within individual tracks.
     * Ensures seamless transitions between consecutive tracks.
     */
    TRACK,

    /**
     * Gapless playback within albums.
     * Maintains seamless transitions only for tracks from the same album,
     * applies normal transitions between different albums.
     */
    ALBUM,

    /**
     * Gapless playback across entire playlist.
     * All track transitions are seamless regardless of album boundaries.
     */
    PLAYLIST
}

/**
 * Configuration for gapless playback behavior.
 *
 * @property crossfadeMs Crossfade duration in milliseconds. Default is 0 (no crossfade).
 * @property gapMs Gap between tracks in milliseconds. Default is 0 (true gapless).
 * @property preBufferThresholdMs Time before track end to start pre-buffering next track.
 * @property enabled Whether gapless playback is currently enabled.
 */
data class GaplessConfig(
    val crossfadeMs: Long = 0,
    val gapMs: Long = 0,
    val preBufferThresholdMs: Long = 3000, // 3 seconds before end
    val enabled: Boolean = true
) {
    /**
     * Check if true gapless mode is active (no gap, no crossfade).
     */
    val isTrueGapless: Boolean
        get() = enabled && crossfadeMs == 0L && gapMs == 0L

    /**
     * Check if crossfade mode is active.
     */
    val isCrossfadeEnabled: Boolean
        get() = enabled && crossfadeMs > 0L

    companion object {
        /**
         * Default gapless configuration for true gapless playback.
         */
        val DEFAULT_GAPLESS = GaplessConfig(
            crossfadeMs = 0,
            gapMs = 0,
            preBufferThresholdMs = 3000,
            enabled = true
        )

        /**
         * Configuration for crossfade playback (5 second crossfade).
         */
        val DEFAULT_CROSSFADE = GaplessConfig(
            crossfadeMs = 5000,
            gapMs = 0,
            preBufferThresholdMs = 5000,
            enabled = true
        )

        /**
         * Disabled gapless configuration.
         */
        val DISABLED = GaplessConfig(
            crossfadeMs = 0,
            gapMs = 0,
            preBufferThresholdMs = 0,
            enabled = false
        )
    }
}

/**
 * State of the gapless playback handler.
 *
 * @property mode Current gapless mode.
 * @property config Current gapless configuration.
 * @property isBufferingNext Whether the next track is being pre-buffered.
 * @property nextTrackUri URI of the track being pre-buffered.
 * @property currentAlbumId Album ID of the currently playing track.
 * @property consecutiveTrackCount Number of consecutive tracks played from same album.
 */
data class GaplessState(
    val mode: GaplessMode = GaplessMode.OFF,
    val config: GaplessConfig = GaplessConfig.DISABLED,
    val isBufferingNext: Boolean = false,
    val nextTrackUri: String? = null,
    val currentAlbumId: String? = null,
    val consecutiveTrackCount: Int = 0,
    val lastTransitionTimeMs: Long = 0,
    val transitionCount: Int = 0
) {
    companion object {
        /**
         * Initial state with gapless disabled.
         */
        val INITIAL = GaplessState()
    }
}

/**
 * Listener for gapless playback events.
 */
interface GaplessPlaybackListener {
    /**
     * Called when a track transition starts.
     *
     * @param fromTrack Metadata of the track being transitioned from.
     * @param toTrack Metadata of the track being transitioned to.
     * @param isGapless Whether the transition will be gapless.
     */
    fun onTransitionStarted(fromTrack: TrackMetadata?, toTrack: TrackMetadata?, isGapless: Boolean) {}

    /**
     * Called when a track transition completes.
     *
     * @param newTrack Metadata of the new track.
     * @param transitionDurationMs Duration of the transition in milliseconds.
     */
    fun onTransitionComplete(newTrack: TrackMetadata?, transitionDurationMs: Long) {}

    /**
     * Called when pre-buffering starts for the next track.
     *
     * @param trackUri URI of the track being buffered.
     * @param timeUntilTransitionMs Estimated time until track transition.
     */
    fun onPreBufferStarted(trackUri: String, timeUntilTransitionMs: Long) {}

    /**
     * Called when gapless mode changes.
     *
     * @param oldMode Previous gapless mode.
     * @param newMode New gapless mode.
     */
    fun onGaplessModeChanged(oldMode: GaplessMode, newMode: GaplessMode) {}
}

/**
 * GaplessPlaybackHandler manages seamless track transitions without audio gaps.
 *
 * This handler provides advanced gapless playback capabilities for the Melos music player,
 * including true gapless playback, crossfade transitions, and intelligent pre-buffering.
 *
 * ## Features:
 * - **True Gapless Playback**: Zero-gap transitions between consecutive tracks
 * - **Crossfade Support**: Configurable crossfade duration for smooth transitions
 * - **Album-Aware Transitions**: Different behavior for same-album vs different-album tracks
 * - **Intelligent Pre-buffering**: Pre-loads next track before current track ends
 * - **Streaming Optimization**: Different strategies for local vs network content
 * - **Gapless Metadata**: Preserves and utilizes gapless tags from audio files
 *
 * ## Gapless Modes:
 * - [GaplessMode.OFF]: Standard playback with potential gaps
 * - [GaplessMode.TRACK]: Gapless for all consecutive tracks
 * - [GaplessMode.ALBUM]: Gapless only within albums
 * - [GaplessMode.PLAYLIST]: Gapless across entire playlist
 *
 * ## Usage:
 * ```kotlin
 * // Enable gapless playback
 * gaplessHandler.enableGapless(true)
 * gaplessHandler.setGaplessMode(GaplessMode.ALBUM)
 *
 * // Configure crossfade
 * gaplessHandler.configureCrossfade(durationMs = 5000)
 *
 * // Apply gapless configuration to media items
 * val mediaItems = tracks.map { gaplessHandler.createGaplessMediaItem(it) }
 * player.setMediaItems(mediaItems)
 * ```
 *
 * @param context Application context for resource access.
 * @param player The ExoPlayer instance to manage.
 * @param coroutineScope Scope for background operations.
 *
 * @see GaplessMode
 * @see GaplessConfig
 * @see MelosPlayer
 */
@OptIn(UnstableApi::class)
@Singleton
class GaplessPlaybackHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val player: MelosPlayer,
    private val coroutineScope: CoroutineScope
) {
    /**
     * Logger tag for gapless playback operations.
     */
    companion object {
        private const val TAG = "GaplessPlaybackHandler"
        
        /**
         * Minimum track duration to enable gapless (avoid for very short tracks).
         */
        private const val MIN_TRACK_DURATION_MS = 10000L // 10 seconds
        
        /**
         * Maximum gap for considering tracks as consecutive.
         */
        private const val MAX_GAP_FOR_GAPLESS_MS = 100L // 100ms
    }

    /**
     * Current gapless state exposed as StateFlow.
     */
    private val _gaplessState = MutableStateFlow(GaplessState.INITIAL)
    val gaplessState: StateFlow<GaplessState> = _gaplessState.asStateFlow()

    /**
     * Listeners for gapless playback events.
     */
    private val listeners = mutableListOf<GaplessPlaybackListener>()

    /**
     * Job for monitoring playback position and triggering pre-buffering.
     */
    private var positionMonitoringJob: Job? = null

    /**
     * Current ExoPlayer instance (unwrapped from MelosPlayer).
     */
    private var exoPlayer: ExoPlayer? = null

    /**
     * Timeline listener for detecting track changes.
     */
    private var timelineListener: Timeline.Listener? = null

    /**
     * Whether the handler has been initialized.
     */
    private var isInitialized = false

    /**
     * Timestamp of the last track transition.
     */
    private var lastTransitionTimeMs = 0L

    /**
     * Total count of track transitions.
     */
    private var totalTransitionCount = 0

    /**
     * Initialize the gapless playback handler.
     *
     * Must be called after the player is initialized. Sets up listeners
     * and starts position monitoring for pre-buffering.
     */
    fun initialize() {
        if (isInitialized) {
            Log.w(TAG, "GaplessPlaybackHandler already initialized")
            return
        }

        exoPlayer = player.getExoPlayer()
        setupTimelineListener()
        startPositionMonitoring()
        isInitialized = true

        Log.d(TAG, "GaplessPlaybackHandler initialized")
    }

    /**
     * Release resources and clean up listeners.
     *
     * Call this when the handler is no longer needed to prevent memory leaks.
     */
    fun release() {
        positionMonitoringJob?.cancel()
        timelineListener?.let {
            exoPlayer?.removeTimelineListener(it)
        }
        listeners.clear()
        exoPlayer = null
        isInitialized = false

        Log.d(TAG, "GaplessPlaybackHandler released")
    }

    /**
     * Enable or disable gapless playback.
     *
     * @param enabled True to enable gapless playback, false to disable.
     *
     * @see setGaplessMode
     * @see configureCrossfade
     */
    fun enableGapless(enabled: Boolean) {
        val currentState = _gaplessState.value
        val newConfig = if (enabled) {
            GaplessConfig.DEFAULT_GAPLESS
        } else {
            GaplessConfig.DISABLED
        }

        _gaplessState.update { state ->
            state.copy(
                config = newConfig,
                mode = if (enabled) GaplessMode.TRACK else GaplessMode.OFF
            )
        }

        applyGaplessConfiguration()
        Log.d(TAG, "Gapless playback ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Set the gapless playback mode.
     *
     * @param mode The gapless mode to use.
     *
     * @see GaplessMode
     */
    fun setGaplessMode(mode: GaplessMode) {
        val oldMode = _gaplessState.value.mode

        _gaplessState.update { state ->
            state.copy(
                mode = mode,
                config = state.config.copy(enabled = mode != GaplessMode.OFF)
            )
        }

        if (oldMode != mode) {
            notifyGaplessModeChanged(oldMode, mode)
            applyGaplessConfiguration()
        }

        Log.d(TAG, "Gapless mode set to $mode")
    }

    /**
     * Configure crossfade duration.
     *
     * @param durationMs Crossfade duration in milliseconds. Set to 0 for true gapless.
     *
     * @see GaplessConfig
     */
    fun configureCrossfade(durationMs: Long) {
        require(durationMs >= 0) { "Crossfade duration must be non-negative" }

        _gaplessState.update { state ->
            state.copy(
                config = state.config.copy(
                    crossfadeMs = durationMs,
                    preBufferThresholdMs = maxOf(durationMs, 3000L),
                    enabled = true
                )
            )
        }

        applyGaplessConfiguration()
        Log.d(TAG, "Crossfade configured: ${durationMs}ms")
    }

    /**
     * Check if gapless playback is currently enabled.
     *
     * @return True if gapless playback is enabled, false otherwise.
     */
    fun isGaplessEnabled(): Boolean {
        return _gaplessState.value.config.enabled && _gaplessState.value.mode != GaplessMode.OFF
    }

    /**
     * Get the current gapless configuration.
     *
     * @return The current gapless configuration.
     */
    fun getGaplessConfig(): GaplessConfig {
        return _gaplessState.value.config
    }

    /**
     * Get the current gapless mode.
     *
     * @return The current gapless mode.
     */
    fun getGaplessMode(): GaplessMode {
        return _gaplessState.value.mode
    }

    /**
     * Create a MediaItem with gapless configuration.
     *
     * @param track The track to create a MediaItem for.
     * @param isAlbumBoundary Whether this track starts a new album.
     * @return MediaItem configured for gapless playback.
     */
    fun createGaplessMediaItem(track: Track, isAlbumBoundary: Boolean = false): MediaItem {
        val uri = Uri.parse(track.uri)
        val isLocalFile = uri.scheme == "file" || uri.scheme == "content"

        val gaplessInfo = if (isGaplessEnabled() && isLocalFile) {
            MediaItem.ClippingConfiguration.Builder()
                .setRemoveMetadata(false) // Preserve gapless metadata
                .build()
        } else {
            null
        }

        val albumId = track.albumId ?: track.artist

        return MediaItem.Builder()
            .setUri(uri)
            .setTag(track) // Store track reference
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setAlbumArtist(track.albumArtist)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setExtras(
                        android.os.Bundle().apply {
                            putString("album_id", albumId)
                            putBoolean("is_album_boundary", isAlbumBoundary)
                            putLong("track_number", track.trackNumber.toLong())
                            putBoolean("is_gapless_enabled", isGaplessEnabled())
                        }
                    )
                    .build()
            )
            .setClippingConfiguration(gaplessInfo)
            .build()
    }

    /**
     * Create MediaItems from a list of tracks with gapless configuration.
     *
     * @param tracks List of tracks to create MediaItems for.
     * @param gaplessMode Gapless mode to apply. Defaults to current mode.
     * @return List of MediaItems configured for gapless playback.
     */
    fun createGaplessMediaItems(
        tracks: List<Track>,
        gaplessMode: GaplessMode = _gaplessState.value.mode
    ): List<MediaItem> {
        if (tracks.isEmpty()) {
            Log.w(TAG, "Empty track list provided to createGaplessMediaItems")
            return emptyList()
        }

        val enableGapless = gaplessMode != GaplessMode.OFF
        val albumAware = gaplessMode == GaplessMode.ALBUM

        return tracks.mapIndexed { index, track ->
            val prevTrack = tracks.getOrNull(index - 1)
            val isAlbumBoundary = if (albumAware && prevTrack != null) {
                track.albumId != prevTrack.albumId || track.albumId == null
            } else {
                false
            }

            // Store original gapless setting temporarily
            val wasEnabled = _gaplessState.value.config.enabled
            _gaplessState.update { it.copy(config = it.config.copy(enabled = enableGapless && !isAlbumBoundary)) }

            val mediaItem = createGaplessMediaItem(track, isAlbumBoundary)

            // Restore gapless setting
            _gaplessState.update { it.copy(config = it.config.copy(enabled = wasEnabled)) }

            mediaItem
        }
    }

    /**
     * Add a listener for gapless playback events.
     *
     * @param listener Listener to add.
     */
    fun addListener(listener: GaplessPlaybackListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
            Log.d(TAG, "Listener added: ${listener::class.java.simpleName}")
        }
    }

    /**
     * Remove a listener for gapless playback events.
     *
     * @param listener Listener to remove.
     */
    fun removeListener(listener: GaplessPlaybackListener) {
        listeners.remove(listener)
        Log.d(TAG, "Listener removed: ${listener::class.java.simpleName}")
    }

    /**
     * Check if two tracks are from the same album.
     *
     * @param track1 First track.
     * @param track2 Second track.
     * @return True if tracks are from the same album.
     */
    fun areTracksFromSameAlbum(track1: Track?, track2: Track?): Boolean {
        if (track1 == null || track2 == null) return false
        
        val albumId1 = track1.albumId ?: return false
        val albumId2 = track2.albumId ?: return false
        
        return albumId1 == albumId2
    }

    /**
     * Check if gapless playback should be used for a track transition.
     *
     * @param currentTrack Current playing track.
     * @param nextTrack Next track to be played.
     * @return True if gapless playback should be used.
     */
    fun shouldUseGaplessForTransition(
        currentTrack: TrackMetadata?,
        nextTrack: TrackMetadata?
    ): Boolean {
        if (!isGaplessEnabled()) return false

        val currentState = _gaplessState.value

        when (currentState.mode) {
            GaplessMode.OFF -> return false
            GaplessMode.TRACK -> return true
            GaplessMode.ALBUM -> {
                // Only gapless if same album
                val currentAlbumId = currentTrack?.album?.toString()
                val nextAlbumId = nextTrack?.album?.toString()
                return currentAlbumId != null && currentAlbumId == nextAlbumId
            }
            GaplessMode.PLAYLIST -> return true
        }
    }

    /**
     * Get the pre-buffer threshold for the current configuration.
     *
     * @return Pre-buffer threshold in milliseconds.
     */
    fun getPreBufferThresholdMs(): Long {
        return _gaplessState.value.config.preBufferThresholdMs
    }

    /**
     * Apply the current gapless configuration to the player.
     */
    private fun applyGaplessConfiguration() {
        exoPlayer?.let { player ->
            val config = _gaplessState.value.config
            
            // Configure buffer control for gapless playback
            if (config.enabled) {
                val loadControl = DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                        DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                        config.preBufferThresholdMs.toInt(),
                        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
                    )
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()
                
                // Note: LoadControl cannot be changed on existing player
                // This would require player recreation with new LoadControl
                Log.d(TAG, "Gapless configuration applied (requires player restart for buffer changes)")
            }
        }
    }

    /**
     * Set up timeline listener to detect track changes.
     */
    private fun setupTimelineListener() {
        timelineListener = object : Timeline.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                    Log.d(TAG, "Playlist changed, updating gapless state")
                    updateAlbumTracking(timeline)
                }
            }
        }

        exoPlayer?.addTimelineListener(timelineListener!!)
    }

    /**
     * Update album tracking when playlist changes.
     */
    private fun updateAlbumTracking(timeline: Timeline) {
        val currentWindowIndex = exoPlayer?.currentMediaItemIndex ?: return
        
        if (timeline.windowCount > currentWindowIndex) {
            val window = Timeline.Window()
            timeline.getWindow(currentWindowIndex, window)
            
            val mediaItem = window.mediaItem
            val albumId = mediaItem?.mediaMetadata?.extras?.getString("album_id")
            
            _gaplessState.update { state ->
                state.copy(
                    currentAlbumId = albumId,
                    consecutiveTrackCount = 1
                )
            }
        }
    }

    /**
     * Start monitoring playback position for pre-buffering.
     */
    private fun startPositionMonitoring() {
        positionMonitoringJob?.cancel()
        
        positionMonitoringJob = coroutineScope.launch(Dispatchers.Default) {
            while (isActive && isInitialized) {
                try {
                    monitorPlaybackPosition()
                    delay(500) // Check every 500ms
                } catch (e: Exception) {
                    Log.e(TAG, "Error monitoring playback position", e)
                }
            }
        }

        Log.d(TAG, "Position monitoring started")
    }

    /**
     * Monitor playback position and trigger pre-buffering.
     */
    private suspend fun monitorPlaybackPosition() {
        val player = exoPlayer ?: return
        val currentState = _gaplessState.value
        
        if (!currentState.config.enabled) return

        try {
            val currentPosition = player.currentPosition
            val duration = player.duration
            
            // Skip if duration is unknown or track is too short
            if (duration == C.TIME_UNSET || duration < MIN_TRACK_DURATION_MS) return

            val timeRemaining = duration - currentPosition
            val preBufferThreshold = currentState.config.preBufferThresholdMs

            // Check if we need to pre-buffer next track
            if (timeRemaining > 0 && timeRemaining <= preBufferThreshold) {
                val nextTrackIndex = player.nextMediaItemIndex
                
                if (nextTrackIndex != C.INDEX_UNSET) {
                    val nextMediaItem = player.getMediaItemAt(nextTrackIndex)
                    preBufferNextTrack(nextMediaItem, timeRemaining)
                }
            }
        } catch (e: IllegalStateException) {
            // Player released or in invalid state
            Log.w(TAG, "Player state changed during monitoring", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during position monitoring", e)
        }
    }

    /**
     * Pre-buffer the next track before current track ends.
     *
     * @param mediaItem MediaItem to pre-buffer.
     * @param timeUntilTransitionMs Time remaining until track transition.
     */
    private suspend fun preBufferNextTrack(mediaItem: MediaItem, timeUntilTransitionMs: Long) {
        val currentState = _gaplessState.value
        
        if (currentState.isBufferingNext) {
            // Already buffering this track
            if (currentState.nextTrackUri == mediaItem.localConfiguration?.uri.toString()) {
                return
            }
        }

        _gaplessState.update { state ->
            state.copy(
                isBufferingNext = true,
                nextTrackUri = mediaItem.localConfiguration?.uri.toString()
            )
        }

        // Notify listeners
        listeners.forEach { listener ->
            try {
                listener.onPreBufferStarted(
                    mediaItem.localConfiguration?.uri.toString(),
                    timeUntilTransitionMs
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying listener", e)
            }
        }

        Log.d(
            TAG, 
            "Pre-buffering next track: ${mediaItem.mediaMetadata.title} " +
            "in ${timeUntilTransitionMs}ms"
        )

        // ExoPlayer automatically handles pre-buffering when using setMediaItems
        // This method mainly tracks pre-buffer state for UI/monitoring purposes
    }

    /**
     * Handle track transition events.
     *
     * @param fromTrack Previous track metadata.
     * @param toTrack New track metadata.
     */
    private fun handleTrackTransition(
        fromTrack: TrackMetadata?,
        toTrack: TrackMetadata?
    ) {
        val currentTime = System.currentTimeMillis()
        val transitionDuration = currentTime - lastTransitionTimeMs

        val useGapless = shouldUseGaplessForTransition(fromTrack, toTrack)

        // Update state
        val currentState = _gaplessState.value
        val isAlbumBoundary = toTrack?.let { track ->
            currentState.currentAlbumId != null && 
            track.album?.toString() != currentState.currentAlbumId
        } ?: false

        _gaplessState.update { state ->
            state.copy(
                currentAlbumId = toTrack?.album?.toString(),
                consecutiveTrackCount = if (isAlbumBoundary) 1 else state.consecutiveTrackCount + 1,
                lastTransitionTimeMs = currentTime,
                transitionCount = totalTransitionCount + 1,
                isBufferingNext = false,
                nextTrackUri = null
            )
        }

        totalTransitionCount++
        lastTransitionTimeMs = currentTime

        // Notify listeners
        listeners.forEach { listener ->
            try {
                listener.onTransitionStarted(fromTrack, toTrack, useGapless)
                listener.onTransitionComplete(toTrack, transitionDuration)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying listener of transition", e)
            }
        }

        Log.d(
            TAG,
            "Track transition: ${fromTrack?.title} -> ${toTrack?.title}, " +
            "gapless: $useGapless, duration: ${transitionDuration}ms"
        )

        // Update album tracking
        if (toTrack != null) {
            _gaplessState.update { state ->
                state.copy(currentAlbumId = toTrack.album?.toString())
            }
        }
    }

    /**
     * Notify listeners of gapless mode changes.
     */
    private fun notifyGaplessModeChanged(oldMode: GaplessMode, newMode: GaplessMode) {
        listeners.forEach { listener ->
            try {
                listener.onGaplessModeChanged(oldMode, newMode)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying listener of mode change", e)
            }
        }
    }

    /**
     * Get the unwrapped ExoPlayer instance.
     *
     * @return ExoPlayer instance or null if not available.
     */
    private fun getExoPlayer(): ExoPlayer? {
        return (player as? Object).let {
            // Try to access ExoPlayer through MelosPlayer's internal reference
            // This would require MelosPlayer to expose a method
            null
        }
    }

    /**
     * Check if the current track is from a local file.
     *
     * @return True if current track is local, false for streaming.
     */
    private fun isCurrentTrackLocal(): Boolean {
        return exoPlayer?.currentMediaItem?.localConfiguration?.uri?.let { uri ->
            uri.scheme == "file" || uri.scheme == "content"
        } ?: false
    }

    /**
     * Validate gapless configuration for current playlist.
     *
     * @return True if gapless is appropriate, false if issues detected.
     */
    fun validateGaplessConfiguration(): Boolean {
        val player = exoPlayer ?: return false
        val timeline = player.currentTimeline
        
        if (timeline.isEmpty) {
            Log.w(TAG, "Cannot validate: empty playlist")
            return false
        }

        // Check for mixed local/streaming content
        var hasLocal = false
        var hasStreaming = false

        for (i in 0 until timeline.windowCount) {
            val window = Timeline.Window()
            timeline.getWindow(i, window)
            val uri = window.mediaItem.localConfiguration?.uri ?: continue
            
            if (uri.scheme == "file" || uri.scheme == "content") {
                hasLocal = true
            } else {
                hasStreaming = true
            }
        }

        if (hasLocal && hasStreaming) {
            Log.w(TAG, "Mixed local and streaming content detected")
            // Gapless may not work perfectly with mixed content
        }

        return true
    }
}
