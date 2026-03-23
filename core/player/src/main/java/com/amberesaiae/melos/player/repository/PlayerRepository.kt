package com.amberesaiae.melos.player.repository

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import com.amberesaiae.melos.player.GaplessPlaybackHandler
import com.amberesaiae.melos.player.GaplessMode
import com.amberesaiae.melos.player.MelosPlayer
import com.amberesaiae.melos.player.PlaybackState
import com.amberesaiae.melos.player.TrackMetadata
import com.amberesaiae.melos.player.effects.AudioEffectsProcessor
import com.amberesaiae.melos.player.equalizer.EqualizerController
import com.amberesaiae.melos.player.equalizer.EqualizerPreset
import com.amberesaiae.melos.player.queue.QueueManager
import com.amberesaiae.melos.player.replaygain.ReplayGainHandler
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
 * Comprehensive player state exposed to the UI layer.
 *
 * Combines state from all player components into a single immutable data class
 * for consistent and predictable UI updates via StateFlow.
 *
 * @property isInitialized Whether the player has been initialized
 * @property isPlaying Current playback state (playing/paused)
 * @property playbackState Detailed playback state (Idle, Buffering, Ready, Ended)
 * @property currentPositionMs Current playback position in milliseconds
 * @property durationMs Total duration of current media in milliseconds
 * @property bufferedPositionMs Position up to which content is buffered
 * @property currentTrack Metadata of the currently playing track
 * @property currentTrackIndex Index of current track in the queue
 * @property queueSize Total number of tracks in the queue
 * @property shuffleModeEnabled Whether shuffle mode is active
 * @property repeatMode Repeat mode (OFF, ONE, ALL)
 * @property playbackSpeed Current playback speed (0.5x - 2.0x)
 * @property playbackPitch Current playback pitch (0.5x - 2.0x)
 * @property volume Current volume level (0.0f - 1.0f)
 * @property isGaplessEnabled Whether gapless playback is enabled
 * @property gaplessMode Current gapless mode (OFF, TRACK, ALBUM, PLAYLIST)
 * @property equalizerEnabled Whether equalizer is active
 * @property equalizerPreset Current equalizer preset name
 * @property bassBoostEnabled Whether bass boost is active
 * @property bassBoostStrength Bass boost strength (0-1000)
 * @property loudnessEnhancementEnabled Whether loudness enhancement is active
 * @property virtualizerEnabled Whether virtualizer is active
 * @property replayGainEnabled Whether ReplayGain is active
 * @property replayGainMode ReplayGain mode (TRACK, ALBUM, OFF)
 * @property error Current playback error if any
 * @property isLoading Whether the player is in a loading state
 */
data class PlayerRepositoryState(
    val isInitialized: Boolean = false,
    val isPlaying: Boolean = false,
    val playbackState: PlaybackState = PlaybackState.Idle,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val currentTrack: TrackMetadata? = null,
    val currentTrackIndex: Int = 0,
    val queueSize: Int = 0,
    val shuffleModeEnabled: Boolean = false,
    val repeatMode: Int = 0, // 0=OFF, 1=ONE, 2=ALL
    val playbackSpeed: Float = 1.0f,
    val playbackPitch: Float = 1.0f,
    val volume: Float = 1.0f,
    val isGaplessEnabled: Boolean = false,
    val gaplessMode: GaplessMode = GaplessMode.OFF,
    val equalizerEnabled: Boolean = false,
    val equalizerPreset: String? = null,
    val equalizerBands: List<Float> = emptyList(),
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: Int = 0,
    val loudnessEnhancementEnabled: Boolean = false,
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Int = 0,
    val replayGainEnabled: Boolean = false,
    val replayGainMode: String = "OFF", // TRACK, ALBUM, OFF
    val error: PlayerException? = null,
    val isLoading: Boolean = false
)

/**
 * Sealed class hierarchy for player exceptions.
 */
sealed class PlayerException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    
    /**
     * Error loading media content.
     */
    class MediaLoadError(message: String, cause: Throwable? = null) : PlayerException(message, cause)
    
    /**
     * Error during playback operation.
     */
    class PlaybackError(message: String, cause: Throwable? = null) : PlayerException(message, cause)
    
    /**
     * Error with media source.
     */
    class SourceError(message: String, cause: Throwable? = null) : PlayerException(message, cause)
    
    /**
     * Unknown or unspecified error.
     */
    class UnknownError(message: String, cause: Throwable? = null) : PlayerException(message, cause)
    
    companion object {
        /**
         * Create a PlayerException from an ExoPlayer PlaybackException.
         */
        fun from(exception: PlaybackException): PlayerException {
            return when {
                exception.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                exception.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                exception.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                    MediaLoadError("Failed to load media: ${exception.message}", exception)
                
                exception.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
                exception.errorCode == PlaybackError.ERROR_CODE_DECODING_FAILED ->
                    PlaybackError("Playback failed: ${exception.message}", exception)
                
                else -> UnknownError("Player error: ${exception.message}", exception)
            }
        }
    }
}

/**
 * Unified repository for all player-related operations.
 *
 * This singleton class combines multiple player components (MelosPlayer, EqualizerController,
 * QueueManager, AudioEffectsProcessor, GaplessPlaybackHandler, ReplayGainHandler) into a
 * single cohesive interface for the UI layer. It exposes a comprehensive StateFlow that
 * updates reactively as player state changes, making it ideal for Jetpack Compose or
 * other reactive UI frameworks.
 *
 * ## Architecture
 *
 * This repository follows the Repository pattern, providing:
 * - **Unified State**: Single StateFlow<PlayerRepositoryState> with all player information
 * - **Delegation**: Clean delegation to specialized components for each functionality
 * - **Lifecycle Management**: Centralized initialization and resource cleanup
 * - **Error Handling**: Consistent error reporting across all operations
 * - **Thread Safety**: All state updates happen on the IO dispatcher
 *
 * ## Usage
 *
 * ```kotlin
 * @HiltViewModel
class PlayerViewModel @Inject constructor(
     private val playerRepository: PlayerRepository
 ) : ViewModel() {
     
     // Observe complete player state
     val playerState = playerRepository.playerState
         .stateIn(viewModelScope, SharingStarted.Lazily, PlayerRepositoryState())
     
     fun play() = playerRepository.play()
     fun pause() = playerRepository.pause()
     fun seekTo(position: Long) = playerRepository.seekTo(position)
     
     override fun onCleared() {
         super.onCleared()
         playerRepository.release()
     }
 }
 * ```
 *
 * @property context Application context for player initialization
 * @property melosPlayer Core player wrapper around ExoPlayer
 * @property equalizerController 10-band equalizer with presets
 * @property queueManager Playlist and queue management
 * @property audioEffectsProcessor Audio effects (bass boost, loudness, virtualizer)
 * @property gaplessPlaybackHandler Gapless playback and crossfade handling
 * @property replayGainHandler ReplayGain normalization
 * @property ioScope Coroutine scope for background operations
 *
 * @see MelosPlayer
 * @see EqualizerController
 * @see QueueManager
 * @see AudioEffectsProcessor
 * @see GaplessPlaybackHandler
 * @see ReplayGainHandler
 */
@Singleton
class PlayerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val melosPlayer: MelosPlayer,
    private val equalizerController: EqualizerController,
    private val queueManager: QueueManager,
    private val audioEffectsProcessor: AudioEffectsProcessor,
    private val gaplessPlaybackHandler: GaplessPlaybackHandler,
    private val replayGainHandler: ReplayGainHandler
) {
    
    /**
     * Mutable state flow holding the complete player state.
     * All components update this state, which is observed by the UI layer.
     */
    private val _playerState = MutableStateFlow(PlayerRepositoryState())
    
    /**
     * Immutable state flow for UI observation.
     * Emits a new PlayerRepositoryState whenever any player property changes.
     */
    val playerState: StateFlow<PlayerRepositoryState> = _playerState.asStateFlow()
    
    /**
     * Coroutine scope for asynchronous player operations.
     * Uses SupervisorJob to ensure child coroutines don't cancel each other.
     */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * Observer callbacks for state updates from underlying components.
     */
    private val stateUpdateListener = {
        updateStateFromComponents()
    }
    
    /**
     * Initialize the player and all components.
     *
     * This method must be called before any playback operations.
     * It initializes the underlying ExoPlayer instance and registers
     * all necessary listeners for state updates.
     *
     * ## Side Effects
     * - Creates ExoPlayer instance (lazy initialization)
     * - Registers Player.Listener callbacks
     * - Initializes equalizer and audio effects
     * - Sets up state update observers
     * - Updates playerState to isInitialized = true
     *
     * ## Thread Safety
     * This method is thread-safe and can be called from any thread.
     *
     * @see release
     */
    fun init() {
        ioScope.launch {
            try {
                // Initialize core player
                melosPlayer.init()
                
                // Initialize equalizer
                equalizerController.init()
                
                // Initialize audio effects
                audioEffectsProcessor.init()
                
                // Initialize gapless playback
                gaplessPlaybackHandler.enableGapless(false)
                
                // Initialize ReplayGain
                replayGainHandler.enableReplayGain(false)
                
                // Set up state update observers
                setupStateObservers()
                
                // Update state to initialized
                _playerState.update { it.copy(isInitialized = true, isLoading = false) }
                
            } catch (e: Exception) {
                _playerState.update { 
                    it.copy(
                        isInitialized = false,
                        isLoading = false,
                        error = PlayerException.UnknownError("Initialization failed: ${e.message}", e)
                    )
                }
            }
        }
    }
    
    /**
     * Release all player resources.
     *
     * Call this method when the player is no longer needed to free up
     * system resources. After calling release(), the player cannot be
     * reused - call init() again to reinitialize.
     *
     * ## Side Effects
     * - Stops playback
     * - Releases ExoPlayer instance
     * - Releases equalizer and audio effects
     * - Unregisters all listeners
     * - Updates playerState to isInitialized = false
     *
     * ## Memory Management
     * This method MUST be called to prevent memory leaks.
     *
     * @see init
     */
    fun release() {
        ioScope.launch {
            try {
                // Release all components
                melosPlayer.release()
                equalizerController.release()
                audioEffectsProcessor.release()
                
                // Clear state
                _playerState.update { PlayerRepositoryState() }
                
            } catch (e: Exception) {
                // Log error but don't rethrow
            }
        }
    }
    
    // ====================
    // Playback Control
    // ====================
    
    /**
     * Prepare the player for playback.
     *
     * Loads the current media items and prepares the player without
     * starting playback. Call this after setting media items.
     *
     * @see play
     */
    fun prepare() {
        _playerState.update { it.copy(isLoading = true) }
        melosPlayer.prepare()
    }
    
    /**
     * Start or resume playback.
     *
     * If the player is paused, resumes from the current position.
     * If the player is idle with media prepared, starts playback.
     */
    fun play() {
        melosPlayer.play()
    }
    
    /**
     * Pause playback.
     *
     * Pauses the current track at the current position.
     * Call play() to resume.
     */
    fun pause() {
        melosPlayer.pause()
    }
    
    /**
     * Stop playback.
     *
     * Stops playback and resets position to the beginning.
     * Unlike pause(), this resets the playback position.
     */
    fun stop() {
        melosPlayer.stop()
    }
    
    /**
     * Seek to a specific position.
     *
     * @param positionMs Position in milliseconds to seek to
     */
    fun seekTo(positionMs: Long) {
        melosPlayer.seekTo(positionMs)
    }
    
    /**
     * Seek relative to the current position.
     *
     * @param offsetMs Milliseconds to seek forward (positive) or backward (negative)
     */
    fun seekRelative(offsetMs: Long) {
        melosPlayer.seekRelative(offsetMs)
    }
    
    /**
     * Seek forward by 10 seconds.
     */
    fun seekForward() {
        melosPlayer.seekForward()
    }
    
    /**
     * Seek backward by 10 seconds.
     */
    fun seekBack() {
        melosPlayer.seekBack()
    }
    
    // ====================
    // Queue Management
    // ====================
    
    /**
     * Set the media items for playback.
     *
     * Replaces the current queue with the provided tracks.
     * The queue will start with the first track.
     *
     * @param tracks List of track URIs to add to the queue
     * @see addMediaItems
     */
    fun setMediaItems(tracks: List<String>) {
        ioScope.launch {
            queueManager.setQueue(tracks.map { uri ->
                com.amberesaiae.melos.player.queue.QueueItem(
                    id = "",
                    title = "",
                    artist = "",
                    uri = uri
                )
            })
            val mediaItems = tracks.map { MediaItem.fromUri(it) }
            melosPlayer.setMediaItems(mediaItems)
            updateStateFromComponents()
        }
    }
    
    /**
     * Set the media items for playback with MediaItem objects.
     *
     * Provides more control over media configuration.
     *
     * @param mediaItems List of MediaItem objects
     */
    fun setMediaItems(mediaItems: List<MediaItem>) {
        melosPlayer.setMediaItems(mediaItems)
    }
    
    /**
     * Skip to the next track in the queue.
     */
    fun skipToNext() {
        melosPlayer.skipToNext()
    }
    
    /**
     * Skip to the previous track in the queue.
     *
     * If more than 3 seconds into the current track, seeks to
     * the beginning of the current track instead.
     */
    fun skipToPrevious() {
        melosPlayer.skipToPrevious()
    }
    
    /**
     * Enable or disable shuffle mode.
     *
     * @param enabled true to enable shuffle, false to disable
     */
    fun setShuffleModeEnabled(enabled: Boolean) {
        queueManager.setShuffleMode(enabled)
        _playerState.update { it.copy(shuffleModeEnabled = enabled) }
    }
    
    /**
     * Set the repeat mode.
     *
     * @param repeatMode 0=OFF, 1=ONE (repeat current track), 2=ALL (repeat entire queue)
     */
    fun setRepeatMode(repeatMode: Int) {
        melosPlayer.setRepeatMode(repeatMode)
        _playerState.update { it.copy(repeatMode = repeatMode) }
    }
    
    // ====================
    // Playback Parameters
    // ====================
    
    /**
     * Set the playback speed.
     *
     * @param speed Playback speed from 0.5f (half speed) to 2.0f (double speed)
     */
    fun setPlaybackSpeed(speed: Float) {
        melosPlayer.setPlaybackSpeed(speed)
    }
    
    /**
     * Set the playback pitch.
     *
     * @param pitch Playback pitch from 0.5f to 2.0f
     */
    fun setPlaybackPitch(pitch: Float) {
        melosPlayer.setPlaybackPitch(pitch)
    }
    
    /**
     * Set the player volume.
     *
     * @param volume Volume level from 0.0f (mute) to 1.0f (maximum)
     */
    fun setVolume(volume: Float) {
        melosPlayer.setVolume(volume)
        _playerState.update { it.copy(volume = volume) }
    }
    
    // ====================
    // Equalizer
    // ====================
    
    /**
     * Enable or disable the equalizer.
     *
     * @param enabled true to enable, false to disable
     */
    fun setEqualizerEnabled(enabled: Boolean) {
        equalizerController.setEnabled(enabled)
        _playerState.update { it.copy(equalizerEnabled = enabled) }
    }
    
    /**
     * Apply an equalizer preset.
     *
     * @param preset The preset to apply (FLAT, BASS, TREBLE, etc.)
     */
    fun setEqualizerPreset(preset: EqualizerPreset) {
        equalizerController.applyPreset(preset)
        _playerState.update { 
            it.copy(
                equalizerEnabled = equalizerController.isEnabled(),
                equalizerPreset = preset.name
            )
        }
    }
    
    /**
     * Set the gain for a specific equalizer band.
     *
     * @param band The band index (0-9 for 10-band equalizer)
     * @param gain The gain in dB (-15 to +15)
     */
    fun setEqualizerBandGain(band: Int, gain: Short) {
        equalizerController.setBandGain(band, gain)
        updateEqualizerBands()
    }
    
    // ====================
    // Audio Effects
    // ====================
    
    /**
     * Enable or disable bass boost.
     *
     * @param enabled true to enable, false to disable
     */
    fun setBassBoostEnabled(enabled: Boolean) {
        audioEffectsProcessor.setBassBoostEnabled(enabled)
        _playerState.update { it.copy(bassBoostEnabled = enabled) }
    }
    
    /**
     * Set the bass boost strength.
     *
     * @param strength Strength from 0 (minimum) to 1000 (maximum)
     */
    fun setBassBoostStrength(strength: Int) {
        audioEffectsProcessor.setBassBoostStrength(strength)
        _playerState.update { it.copy(bassBoostStrength = strength) }
    }
    
    /**
     * Enable or disable loudness enhancement.
     *
     * @param enabled true to enable, false to disable
     */
    fun setLoudnessEnhancementEnabled(enabled: Boolean) {
        audioEffectsProcessor.setLoudnessEnhancementEnabled(enabled)
        _playerState.update { it.copy(loudnessEnhancementEnabled = enabled) }
    }
    
    /**
     * Enable or disable virtualizer.
     *
     * @param enabled true to enable, false to disable
     */
    fun setVirtualizerEnabled(enabled: Boolean) {
        audioEffectsProcessor.setVirtualizerEnabled(enabled)
        _playerState.update { it.copy(virtualizerEnabled = enabled) }
    }
    
    /**
     * Set the virtualizer strength.
     *
     * @param strength Strength from 0 (minimum) to 1000 (maximum)
     */
    fun setVirtualizerStrength(strength: Int) {
        audioEffectsProcessor.setVirtualizerStrength(strength)
        _playerState.update { it.copy(virtualizerStrength = strength) }
    }
    
    // ====================
    // Gapless Playback
    // ====================
    
    /**
     * Enable or disable gapless playback.
     *
     * @param enabled true to enable gapless, false to disable
     */
    fun setGaplessEnabled(enabled: Boolean) {
        gaplessPlaybackHandler.enableGapless(enabled)
        _playerState.update { it.copy(isGaplessEnabled = enabled) }
    }
    
    /**
     * Set the gapless playback mode.
     *
     * @param mode GaplessMode (OFF, TRACK, ALBUM, PLAYLIST)
     */
    fun setGaplessMode(mode: GaplessMode) {
        gaplessPlaybackHandler.setGaplessMode(mode)
        _playerState.update { it.copy(gaplessMode = mode) }
    }
    
    /**
     * Configure crossfade duration.
     *
     * @param durationMs Crossfade duration in milliseconds
     */
    fun configureCrossfade(durationMs: Long) {
        gaplessPlaybackHandler.configureCrossfade(durationMs)
    }
    
    // ====================
    // ReplayGain
    // ====================
    
    /**
     * Enable or disable ReplayGain.
     *
     * @param enabled true to enable ReplayGain, false to disable
     */
    fun setReplayGainEnabled(enabled: Boolean) {
        replayGainHandler.enableReplayGain(enabled)
        _playerState.update { it.copy(replayGainEnabled = enabled) }
    }
    
    /**
     * Set the ReplayGain mode.
     *
     * @param mode "TRACK" for track gain, "ALBUM" for album gain, "OFF" to disable
     */
    fun setReplayGainMode(mode: String) {
        when (mode.uppercase()) {
            "TRACK" -> replayGainHandler.setReplayGainMode(ReplayGainHandler.ReplayGainMode.TRACK)
            "ALBUM" -> replayGainHandler.setReplayGainMode(ReplayGainHandler.ReplayGainMode.ALBUM)
            else -> replayGainHandler.disableReplayGain()
        }
        _playerState.update { it.copy(replayGainMode = mode.uppercase()) }
    }
    
    // ====================
    // State Updates
    // ====================
    
    /**
     * Set up observers on all component state flows.
     *
     * Each component's StateFlow is collected and triggers a full
     * state update when it changes.
     */
    private fun setupStateObservers() {
        // Observe player state
        ioScope.launch {
            melosPlayer.playerState.collect { _ ->
                updateStateFromComponents()
            }
        }
        
        // Observe equalizer state
        ioScope.launch {
            equalizerController.equalizerState.collect { _ ->
                updateStateFromComponents()
            }
        }
        
        // Observe audio effects state
        ioScope.launch {
            audioEffectsProcessor.effectsState.collect { _ ->
                updateStateFromComponents()
            }
        }
        
        // Observe gapless state
        ioScope.launch {
            gaplessPlaybackHandler.gaplessState.collect { _ ->
                updateStateFromComponents()
            }
        }
        
        // Observe ReplayGain state
        ioScope.launch {
            replayGainHandler.replayGainState.collect { _ ->
                updateStateFromComponents()
            }
        }
    }
    
    /**
     * Update the repository state from all component states.
     *
     * This method is called whenever any underlying component's state
     * changes. It collects the current state from all components and
     * creates a new PlayerRepositoryState.
     */
    private fun updateStateFromComponents() {
        val playerState = melosPlayer.playerState.value
        val eqState = equalizerController.equalizerState.value
        val effectsState = audioEffectsProcessor.effectsState.value
        val gaplessState = gaplessPlaybackHandler.gaplessState.value
        val replayGainState = replayGainHandler.replayGainState.value
        
        _playerState.update { currentState ->
            currentState.copy(
                isPlaying = playerState.isPlaying,
                playbackState = playerState.playbackState,
                currentPositionMs = playerState.currentPosition,
                durationMs = playerState.duration,
                bufferedPositionMs = playerState.bufferedPosition,
                currentTrack = playerState.currentTrack,
                currentTrackIndex = playerState.queuePosition,
                queueSize = playerState.queueSize,
                playbackSpeed = playerState.playbackParameters.speed,
                playbackPitch = playerState.playbackParameters.pitch,
                error = playerState.error?.let { PlayerException.from(it) },
                isLoading = playerState.isLoading,
                
                equalizerEnabled = eqState.isEnabled,
                equalizerPreset = eqState.currentPreset,
                equalizerBands = eqState.bandGains,
                
                bassBoostEnabled = effectsState.bassBoostEnabled,
                bassBoostStrength = effectsState.bassBoostStrength,
                loudnessEnhancementEnabled = effectsState.loudnessEnhancementEnabled,
                virtualizerEnabled = effectsState.virtualizerEnabled,
                virtualizerStrength = effectsState.virtualizerStrength,
                
                isGaplessEnabled = gaplessState.config.enabled,
                gaplessMode = gaplessState.mode,
                
                replayGainEnabled = replayGainState.isEnabled,
                replayGainMode = replayGainState.mode.name
            )
        }
    }
    
    /**
     * Update the equalizer bands in the state.
     */
    private fun updateEqualizerBands() {
        val bands = equalizerController.equalizerState.value.bandGains
        _playerState.update { it.copy(equalizerBands = bands) }
    }
}
