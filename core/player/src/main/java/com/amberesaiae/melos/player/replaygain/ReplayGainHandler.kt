/*
 * Copyright (c) 2024 Amberesaiae. All rights reserved.
 * SPDX-License-Identifier: MIT
 */
package com.amberesaiae.melos.player.replaygain

import android.content.Context
import android.content.SharedPreferences
import com.amberesaiae.melos.player.MelosPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ReplayGain metadata for a single track.
 *
 * ReplayGain is a standard for normalizing audio playback volume.
 * It analyzes audio files to determine perceived loudness and stores
 * gain values that can be applied during playback.
 *
 * @property trackGain Gain adjustment for this track in dB (typically -60 to +60)
 * @property trackPeak Peak amplitude of this track (0.0 to 1.0+, can exceed 1.0)
 * @property albumGain Gain adjustment for the entire album in dB
 * @property albumPeak Peak amplitude of the album (0.0 to 1.0+)
 * @property hasTrackGain Whether track gain metadata is available
 * @property hasAlbumGain Whether album gain metadata is available
 */
data class ReplayGainInfo(
    val trackGain: Float? = null,
    val trackPeak: Float? = null,
    val albumGain: Float? = null,
    val albumPeak: Float? = null,
    val hasTrackGain: Boolean = trackGain != null,
    val hasAlbumGain: Boolean = albumGain != null
) {
    companion object {
        val EMPTY = ReplayGainInfo()
    }
}

/**
 * ReplayGain processing mode.
 * Determines how gain normalization is applied during playback.
 */
enum class ReplayGainMode {
    /**
     * Disable ReplayGain processing entirely.
     * Audio plays at original volume.
     */
    OFF,

    /**
     * Apply track-level gain only.
     * Each track is normalized individually for consistent volume.
     * Best for shuffled playlists and mixed albums.
     */
    TRACK,

    /**
     * Apply album-level gain only.
     * Preserves relative volume differences between tracks on the same album
     * while normalizing overall album volume.
     * Best for listening to complete albums.
     */
    ALBUM,

    /**
     * Smart mode: Apply album gain when tracks are from the same album,
     * fall back to track gain for mixed content.
     * Provides the best of both modes automatically.
     */
    SMART
}

/**
 * ReplayGain state for UI observation.
 *
 * @property isEnabled Whether ReplayGain processing is active
 * @property mode Current processing mode
 * @property currentTrackGain Current track's gain value in dB
 * @property currentTrackPeak Current track's peak value
 * @property currentAlbumGain Current album's gain value in dB
 * @property currentAlbumPeak Current album's peak value
 * @property appliedGain The actual gain being applied (after mode selection)
 * @property clippingPrevention Whether clipping prevention is active
 * @property targetLevel Target replay level in dB (typically 89dB SPL)
 * @property lastModified Timestamp when settings were last modified
 */
data class ReplayGainState(
    val isEnabled: Boolean = false,
    val mode: ReplayGainMode = ReplayGainMode.SMART,
    val currentTrackGain: Float? = null,
    val currentTrackPeak: Float? = null,
    val currentAlbumGain: Float? = null,
    val currentAlbumPeak: Float? = null,
    val appliedGain: Float? = null,
    val clippingPrevention: Boolean = true,
    val targetLevel: Float = 89f,
    val lastModified: Long = 0L
) {
    companion object {
        val EMPTY = ReplayGainState()
    }
}

/**
 * Listener interface for ReplayGain state changes.
 */
interface ReplayGainListener {
    fun onReplayGainStateChanged(state: ReplayGainState)
    fun onModeChanged(mode: ReplayGainMode)
    fun onGainApplied(gainDb: Float)
}

/**
 * ReplayGain handler for audio volume normalization.
 *
 * This class provides comprehensive ReplayGain support for the Melos music player,
 * enabling consistent playback volume across different tracks and albums.
 *
 * Features:
 * - Track-level and album-level gain normalization
 * - Smart mode for automatic mode selection
 * - Clipping prevention using peak metadata
 * - Configurable target replay level
 * - StateFlow for real-time UI observation
 * - Persistence of settings across app restarts
 * - Integration with MelosPlayer for gain application
 *
 * ReplayGain Analysis:
 * ReplayGain analyzes audio files to determine their perceived loudness
 * (not peak levels) and stores gain adjustment values in metadata tags.
 * During playback, these values are applied to normalize volume.
 *
 * Metadata Standards:
 * - ID3v2 (MP3): TXXX frames with "REPLAYGAIN_TRACK_GAIN", etc.
 * - Vorbis Comments (FLAC, OGG): REPLAYGAIN_TRACK_GAIN, etc.
 * - MP4/AAC: ---- mean com.apple.iTunes frames
 *
 * @property context Application context for SharedPreferences
 * @property melosPlayer MelosPlayer instance for gain application
 */
@Singleton
class ReplayGainHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val melosPlayer: MelosPlayer
) {
    companion object {
        /** Default target replay level (89dB SPL - ReplayGain standard) */
        const val DEFAULT_TARGET_LEVEL = 89f

        /** Maximum gain adjustment allowed (prevents extreme adjustments) */
        const val MAX_GAIN_ADJUSTMENT_DB = 15f

        /** Minimum gain adjustment allowed */
        const val MIN_GAIN_ADJUSTMENT_DB = -15f

        /** SharedPreferences file name */
        private const val PREFS_NAME = "melos_replaygain_settings"

        /** Key for storing ReplayGain enabled state */
        private const val KEY_ENABLED = "replaygain_enabled"

        /** Key for storing ReplayGain mode */
        private const val KEY_MODE = "replaygain_mode"

        /** Key for storing target level */
        private const val KEY_TARGET_LEVEL = "target_level"

        /** Key for storing clipping prevention setting */
        private const val KEY_CLIP_PREVENTION = "clip_prevention"
    }

    /** MutableStateFlow for exposing ReplayGain state to UI */
    private val _replayGainState = MutableStateFlow(ReplayGainState.EMPTY)

    /** StateFlow for observing ReplayGain state */
    val replayGainState: StateFlow<ReplayGainState> = _replayGainState.asStateFlow()

    /** SharedPreferences for persisting settings */
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** List of registered listeners */
    private val listeners = mutableListOf<ReplayGainListener>()

    /** Current track's ReplayGain info */
    private var currentTrackInfo: ReplayGainInfo = ReplayGainInfo.EMPTY

    /** Current album identifier for album gain tracking */
    private var currentAlbumId: String? = null

    /** Whether ReplayGain has been initialized */
    private var isInitialized = false

    /**
     * Initialize ReplayGain handler.
     * Loads saved settings and prepares for gain processing.
     */
    fun init() {
        if (isInitialized) return

        // Load saved preferences
        val enabled = prefs.getBoolean(KEY_ENABLED, true)
        val modeName = prefs.getString(KEY_MODE, ReplayGainMode.SMART.name) ?: ReplayGainMode.SMART.name
        val mode = ReplayGainMode.valueOf(modeName)
        val targetLevel = prefs.getFloat(KEY_TARGET_LEVEL, DEFAULT_TARGET_LEVEL)
        val clippingPrevention = prefs.getBoolean(KEY_CLIP_PREVENTION, true)

        // Initialize state
        _replayGainState.update {
            ReplayGainState(
                isEnabled = enabled,
                mode = mode,
                targetLevel = targetLevel,
                clippingPrevention = clippingPrevention,
                lastModified = System.currentTimeMillis()
            )
        }

        isInitialized = true
    }

    /**
     * Enable or disable ReplayGain processing.
     *
     * @param enabled true to enable, false to disable
     */
    fun enableReplayGain(enabled: Boolean) {
        if (!isInitialized) init()

        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _replayGainState.update { it.copy(isEnabled = enabled) }
        notifyListenersStateChanged()

        // Apply or remove gain based on enabled state
        if (enabled) {
            applyCurrentGain()
        } else {
            melosPlayer.setVolume(1.0f)
        }
    }

    /**
     * Set the ReplayGain processing mode.
     *
     * @param mode The mode to use (OFF, TRACK, ALBUM, SMART)
     */
    fun setReplayGainMode(mode: ReplayGainMode) {
        if (!isInitialized) init()

        prefs.edit().putString(KEY_MODE, mode.name).apply()
        _replayGainState.update { it.copy(mode = mode, lastModified = System.currentTimeMillis()) }
        notifyListenersModeChanged(mode)

        // Re-apply gain with new mode
        if (_replayGainState.value.isEnabled) {
            applyCurrentGain()
        }
    }

    /**
     * Set the target replay level.
     * This is the reference loudness level in dB SPL.
     *
     * @param levelDb Target level in dB (typically 89dB)
     */
    fun setTargetLevel(levelDb: Float) {
        if (!isInitialized) init()

        prefs.edit().putFloat(KEY_TARGET_LEVEL, levelDb).apply()
        _replayGainState.update { 
            it.copy(
                targetLevel = levelDb,
                lastModified = System.currentTimeMillis()
            )
        }

        // Re-apply gain with new target level
        if (_replayGainState.value.isEnabled) {
            applyCurrentGain()
        }
    }

    /**
     * Enable or disable clipping prevention.
     * When enabled, gain is reduced if applying the full gain would cause clipping.
     *
     * @param enabled true to enable clipping prevention
     */
    fun setClippingPrevention(enabled: Boolean) {
        if (!isInitialized) init()

        prefs.edit().putBoolean(KEY_CLIP_PREVENTION, enabled).apply()
        _replayGainState.update { 
            it.copy(
                clippingPrevention = enabled,
                lastModified = System.currentTimeMillis()
            )
        }

        // Re-apply gain with new clipping setting
        if (_replayGainState.value.isEnabled) {
            applyCurrentGain()
        }
    }

    /**
     * Set ReplayGain info for the current track.
     * Should be called when a new track starts playing.
     *
     * @param trackInfo ReplayGain metadata for the track
     * @param albumId Unique identifier for the album (for album gain tracking)
     */
    fun setCurrentTrackInfo(trackInfo: ReplayGainInfo, albumId: String? = null) {
        if (!isInitialized) init()

        currentTrackInfo = trackInfo
        val albumChanged = albumId != currentAlbumId
        currentAlbumId = albumId

        // Update state with new track info
        _replayGainState.update {
            it.copy(
                currentTrackGain = trackInfo.trackGain,
                currentTrackPeak = trackInfo.trackPeak,
                currentAlbumGain = trackInfo.albumGain,
                currentAlbumPeak = trackInfo.albumPeak,
                lastModified = System.currentTimeMillis()
            )
        }

        // Apply gain if enabled and track/album changed
        if (_replayGainState.value.isEnabled && albumChanged) {
            applyCurrentGain()
        }
    }

    /**
     * Clear ReplayGain info (e.g., when playback stops).
     */
    fun clearCurrentInfo() {
        currentTrackInfo = ReplayGainInfo.EMPTY
        currentAlbumId = null

        _replayGainState.update {
            it.copy(
                currentTrackGain = null,
                currentTrackPeak = null,
                currentAlbumGain = null,
                currentAlbumPeak = null,
                appliedGain = null,
                lastModified = System.currentTimeMillis()
            )
        }
    }

    /**
     * Get the gain to apply based on current mode and track info.
     *
     * @return Gain value in dB, or null if no gain info available
     */
    fun getGainForCurrentMode(): Float? {
        if (!_replayGainState.value.isEnabled) return null

        val state = _replayGainState.value
        return when (state.mode) {
            ReplayGainMode.OFF -> null
            ReplayGainMode.TRACK -> state.currentTrackGain
            ReplayGainMode.ALBUM -> state.currentAlbumGain ?: state.currentTrackGain
            ReplayGainMode.SMART -> {
                // Use album gain if available and we're in album context, otherwise track gain
                if (state.currentAlbumGain != null && currentAlbumId != null) {
                    state.currentAlbumGain
                } else {
                    state.currentTrackGain
                }
            }
        }
    }

    /**
     * Calculate the final gain with clipping prevention.
     *
     * @param gainDb The base gain in dB
     * @param peak The peak value for the audio
     * @return Adjusted gain that prevents clipping
     */
    private fun calculateGainWithClippingPrevention(gainDb: Float, peak: Float?): Float {
        if (!_replayGainState.value.clippingPrevention || peak == null) {
            return gainDb.coerceIn(MIN_GAIN_ADJUSTMENT_DB, MAX_GAIN_ADJUSTMENT_DB)
        }

        // Calculate the maximum gain that won't cause clipping
        // If peak * gain > 1.0, we have clipping
        // So max_gain_db = 20 * log10(1.0 / peak)
        val maxGainDb = if (peak > 0f) {
            20f * kotlin.math.log10(1.0f / peak)
        } else {
            0f
        }

        // Apply the minimum of the requested gain and the max safe gain
        return gainDb.coerceIn(MIN_GAIN_ADJUSTMENT_DB, maxGainDb)
    }

    /**
     * Apply the current ReplayGain to the player.
     */
    private fun applyCurrentGain() {
        val gainDb = getGainForCurrentMode() ?: run {
            // No gain info available, reset to normal volume
            melosPlayer.setVolume(1.0f)
            _replayGainState.update { it.copy(appliedGain = null) }
            return
        }

        // Apply clipping prevention if enabled
        val peak = when (_replayGainState.value.mode) {
            ReplayGainMode.ALBUM -> _replayGainState.value.currentAlbumPeak
            ReplayGainMode.SMART -> {
                if (_replayGainState.value.currentAlbumGain != null && currentAlbumId != null) {
                    _replayGainState.value.currentAlbumPeak
                } else {
                    _replayGainState.value.currentTrackPeak
                }
            }
            else -> _replayGainState.value.currentTrackPeak
        }

        val finalGainDb = calculateGainWithClippingPrevention(gainDb, peak)

        // Convert dB gain to linear gain multiplier
        // gain_multiplier = 10^(gain_db / 20)
        val gainMultiplier = kotlin.math.pow(10.0, finalGainDb / 20.0).toFloat()

        // Apply gain to player
        melosPlayer.setVolume(gainMultiplier)

        // Update state
        _replayGainState.update { 
            it.copy(
                appliedGain = finalGainDb,
                lastModified = System.currentTimeMillis()
            )
        }

        notifyListenersGainApplied(finalGainDb)
    }

    /**
     * Parse ReplayGain info from metadata tags.
     * This is a helper method for extracting ReplayGain data from various formats.
     *
     * @param trackGainStr Track gain string (e.g., "-3.5 dB" or "-3.5")
     * @param trackPeakStr Track peak string (e.g., "0.98" or "1.05")
     * @param albumGainStr Album gain string
     * @param albumPeakStr Album peak string
     * @return ReplayGainInfo with parsed values
     */
    fun parseReplayGainInfo(
        trackGainStr: String? = null,
        trackPeakStr: String? = null,
        albumGainStr: String? = null,
        albumPeakStr: String? = null
    ): ReplayGainInfo {
        val trackGain = trackGainStr?.let { parseGainValue(it) }
        val trackPeak = trackPeakStr?.let { parsePeakValue(it) }
        val albumGain = albumGainStr?.let { parseGainValue(it) }
        val albumPeak = albumPeakStr?.let { parsePeakValue(it) }

        return ReplayGainInfo(
            trackGain = trackGain,
            trackPeak = trackPeak,
            albumGain = albumGain,
            albumPeak = albumPeak
        )
    }

    /**
     * Add a listener for ReplayGain state changes.
     *
     * @param listener Listener to add
     */
    fun addListener(listener: ReplayGainListener) {
        listeners.add(listener)
        // Immediately notify of current state
        listener.onReplayGainStateChanged(_replayGainState.value)
    }

    /**
     * Remove a listener.
     *
     * @param listener Listener to remove
     */
    fun removeListener(listener: ReplayGainListener) {
        listeners.remove(listener)
    }

    // ============ Private Helper Methods ============

    /**
     * Parse a gain value string to Float.
     * Handles formats like "-3.5 dB", "-3.5dB", "-3.5", etc.
     */
    private fun parseGainValue(value: String): Float? {
        return try {
            // Remove "dB" suffix and whitespace
            val cleaned = value.replace(Regex("dB", RegexOption.IGNORE_CASE), "").trim()
            cleaned.toFloat()
        } catch (e: NumberFormatException) {
            null
        }
    }

    /**
     * Parse a peak value string to Float.
     */
    private fun parsePeakValue(value: String): Float? {
        return try {
            value.trim().toFloat()
        } catch (e: NumberFormatException) {
            null
        }
    }

    /**
     * Notify all listeners of state change.
     */
    private fun notifyListenersStateChanged() {
        listeners.forEach { it.onReplayGainStateChanged(_replayGainState.value) }
    }

    /**
     * Notify all listeners of mode change.
     */
    private fun notifyListenersModeChanged(mode: ReplayGainMode) {
        listeners.forEach { it.onModeChanged(mode) }
    }

    /**
     * Notify all listeners when gain is applied.
     */
    private fun notifyListenersGainApplied(gainDb: Float) {
        listeners.forEach { it.onGainApplied(gainDb) }
    }
}
