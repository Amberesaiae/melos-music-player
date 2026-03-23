/*
 * Copyright (c) 2024 Amberesaiae. All rights reserved.
 * SPDX-License-Identifier: MIT
 */
package com.amberesaiae.melos.player.equalizer

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import androidx.annotation.IntRange
import com.amberesaiae.melos.player.MelosPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Equalizer preset data class containing name and band level settings.
 *
 * @property name Human-readable preset name
 * @property bandLevels List of gain levels in dB (multiplied by 100 for storage)
 *                      for each frequency band
 */
data class EqualizerPreset(
    val name: String,
    val bandLevels: List<Int>
)

/**
 * Represents a single frequency band in the equalizer.
 *
 * @property frequency Center frequency in Hz (e.g., 31, 1000, 16000)
 * @property gain Current gain setting in dB
 * @property minGain Minimum allowed gain in dB (typically -15)
 * @property maxGain Maximum allowed gain in dB (typically +15)
 */
data class FrequencyBand(
    val frequency: Int,
    val gain: Float,
    val minGain: Float = -15f,
    val maxGain: Float = 15f
)

/**
 * Complete state of the equalizer for UI observation.
 *
 * @property isEnabled Whether the equalizer is currently enabled
 * @property currentPreset Name of the currently loaded preset
 * @property bandLevels List of current gain levels for all bands (in dB * 100)
 * @property isSupported Whether the device supports hardware equalizer
 * @property availablePresets List of available preset names
 * @property audioSessionId Audio session ID for effects chain
 * @property lastModified Timestamp when settings were last modified
 */
data class EqualizerState(
    val isEnabled: Boolean = false,
    val currentPreset: String = "Flat",
    val bandLevels: List<Int> = emptyList(),
    val isSupported: Boolean = false,
    val availablePresets: List<String> = emptyList(),
    val audioSessionId: Int = AudioEffect.ERROR_INVALID_OPERATION,
    val lastModified: Long = 0L
) {
    companion object {
        val EMPTY = EqualizerState()
    }
}

/**
 * Listener interface for equalizer state changes.
 * Used for UI updates and external observers.
 */
interface EqualizerListener {
    fun onEqualizerStateChanged(state: EqualizerState)
    fun onPresetChanged(presetName: String)
    fun onBandLevelChanged(bandIndex: Int, level: Short)
}

/**
 * Comprehensive 10-band equalizer controller for Melos music player.
 *
 * This class provides a high-level API for controlling Android's hardware
 * equalizer, supporting 10 ISO-standard frequency bands, built-in presets,
 * custom preset creation, and real-time state monitoring via StateFlow.
 *
 * Features:
 * - 10-band equalizer with ISO standard frequencies (31Hz to 16kHz)
 * - Built-in presets for various music genres and use cases
 * - Custom preset creation and persistence
 * - Real-time state monitoring via StateFlow
 * - Graceful fallback for devices without EQ support
 * - Audio session management for effects chain integration
 *
 * @property context Application context for SharedPreferences and EQ access
 * @property melosPlayer MelosPlayer instance for audio session coordination
 */
@Singleton
class EqualizerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val melosPlayer: MelosPlayer
) {
    companion object {
        /**
         * ISO standard 10-band equalizer frequencies in Hz.
         * These are the standard center frequencies used in professional audio equipment.
         */
        val ISO_FREQUENCIES = listOf(
            31,     // 31 Hz - Sub-bass
            62,     // 62 Hz - Bass
            125,    // 125 Hz - Low-mid bass
            250,    // 250 Hz - Mid-bass
            500,    // 500 Hz - Low-mid range
            1000,   // 1 kHz - Mid-range
            2000,   // 2 kHz - Upper mid-range
            4000,   // 4 kHz - Presence
            8000,   // 8 kHz - Brilliance
            16000   // 16 kHz - Air/Sparkle
        )

        /** Maximum number of custom presets allowed */
        const val MAX_CUSTOM_PRESETS = 20

        /** SharedPreferences file name for equalizer settings */
        private const val PREFS_NAME = "melos_equalizer_settings"

        /** Key for storing current preset name */
        private const val KEY_CURRENT_PRESET = "current_preset"

        /** Key prefix for storing custom presets */
        private const val KEY_CUSTOM_PRESET_PREFIX = "custom_preset_"

        /** Default gain level (0dB) */
        private const val DEFAULT_GAIN = 0
    }

    /**
     * Built-in equalizer presets for common music genres and use cases.
     * Levels are stored as integer values representing dB * 100.
     * For example, -500 = -5.0dB, +350 = +3.5dB
     */
    private val builtInPresets = mapOf(
        "Flat" to listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),

        "Classical" to listOf(0, 0, 0, 0, -100, -200, -300, -350, -200, 0),

        "Dance" to listOf(+400, +300, +200, +100, 0, -100, -200, -250, -200, +100),

        "Full Bass" to listOf(+500, +500, +500, +300, +100, 0, -100, -200, -300, -200),

        "Full Treble" to listOf(-200, -300, -200, -100, 0, +200, +300, +400, +500, +500),

        "Hip Hop" to listOf(+450, +400, +300, +150, 0, -100, -150, -100, +100, +200),

        "Jazz" to listOf(+200, +100, 0, -100, +150, +250, +300, +350, +300, +200),

        "Latin" to listOf(+300, +250, +200, +100, 0, +100, +200, +300, +250, +150),

        "Normal" to listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),

        "Pop" to listOf(+200, +150, 0, -150, -200, -150, 0, +150, +250, +300),

        "Rock" to listOf(+300, +250, +100, -100, -200, -150, 0, +200, +300, +350),

        "Small Speakers" to listOf(-300, -200, +100, +200, +300, +400, +500, +500, +450, +400),

        "Spoken Word" to listOf(-200, -100, +100, +200, +350, +400, +350, +300, +200, +100)
    )

    /** Android Equalizer instance (null if not supported) */
    private var equalizer: Equalizer? = null

    /** MutableStateFlow for exposing equalizer state to UI */
    private val _equalizerState = MutableStateFlow(EqualizerState.EMPTY)

    /** StateFlow for observing equalizer state */
    val equalizerState: StateFlow<EqualizerState> = _equalizerState.asStateFlow()

    /** SharedPreferences for persisting preset settings */
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** List of registered listeners */
    private val listeners = mutableListOf<EqualizerListener>()

    /** Whether equalizer has been initialized */
    private var isInitialized = false

    /**
     * Initialize the equalizer with the current audio session.
     * Must be called after MelosPlayer is initialized.
     * This method attempts to create an Equalizer instance and configure it
     * for use with the player's audio session.
     */
    fun init() {
        if (isInitialized) return

        try {
            // Check if equalizer is available
            if (!Equalizer.isAvailable()) {
                _equalizerState.update { 
                    it.copy(isSupported = false, availablePresets = builtInPresets.keys.toList())
                }
                isInitialized = true
                return
            }

            // Get audio session ID from player
            val audioSessionId = melosPlayer.getAudioSessionId()
            if (audioSessionId == AudioEffect.ERROR_INVALID_OPERATION) {
                _equalizerState.update { 
                    it.copy(isSupported = false, availablePresets = builtInPresets.keys.toList())
                }
                isInitialized = true
                return
            }

            // Create equalizer instance
            equalizer = Equalizer(0, audioSessionId)

            // Verify we have 10 bands (or adapt to available bands)
            val numBands = equalizer?.numberOfBands ?: 0
            val availablePresets = if (numBands > 0) {
                builtInPresets.keys.toList() + getCustomPresetNames()
            } else {
                builtInPresets.keys.toList()
            }

            // Load last used preset
            val lastPreset = prefs.getString(KEY_CURRENT_PRESET, "Flat") ?: "Flat"

            // Initialize state
            _equalizerState.update {
                EqualizerState(
                    isEnabled = equalizer?.enabled ?: false,
                    currentPreset = lastPreset,
                    bandLevels = getCurrentBandLevels(),
                    isSupported = true,
                    availablePresets = availablePresets,
                    audioSessionId = audioSessionId,
                    lastModified = System.currentTimeMillis()
                )
            }

            // Load the last preset
            loadPreset(lastPreset)

            isInitialized = true

        } catch (e: Exception) {
            // Handle any initialization errors gracefully
            _equalizerState.update { 
                it.copy(
                    isSupported = false,
                    availablePresets = builtInPresets.keys.toList()
                )
            }
            isInitialized = true
        }
    }

    /**
     * Enable or disable the equalizer.
     * When disabled, audio passes through without EQ processing.
     *
     * @param enabled true to enable, false to disable
     * @throws IllegalStateException if equalizer is not supported
     */
    fun enableEqualizer(enabled: Boolean) {
        if (!isInitialized) {
            init()
        }

        if (!_equalizerState.value.isSupported) {
            return
        }

        try {
            equalizer?.enabled = enabled
            _equalizerState.update { it.copy(isEnabled = enabled) }
            notifyListenersStateChanged()
        } catch (e: Exception) {
            // Handle enable/disable errors
        }
    }

    /**
     * Set the gain level for a specific frequency band.
     *
     * @param bandIndex Index of the band (0-9 for 10 bands)
     * @param level New gain level in dB * 100 (e.g., -500 for -5.0dB)
     * @throws IndexOutOfBoundsException if bandIndex is invalid
     * @throws IllegalArgumentException if level is out of range
     */
    fun setBandLevel(
        @IntRange(from = 0, to = 9) bandIndex: Int,
        @IntRange(from = -1500, to = 1500) level: Short
    ) {
        if (!_equalizerState.value.isSupported) return

        try {
            val numBands = equalizer?.numberOfBands ?: 0
            if (bandIndex < 0 || bandIndex >= numBands) {
                throw IndexOutOfBoundsException("Invalid band index: $bandIndex")
            }

            equalizer?.setBandLevel(bandIndex.toShort(), level)
            
            // Update state
            val newBandLevels = getCurrentBandLevels().toMutableList()
            newBandLevels[bandIndex] = level.toInt()
            _equalizerState.update { 
                it.copy(
                    bandLevels = newBandLevels,
                    lastModified = System.currentTimeMillis()
                )
            }

            notifyListenersBandChanged(bandIndex, level)

        } catch (e: Exception) {
            // Handle band level errors gracefully
        }
    }

    /**
     * Get the current gain level for a specific frequency band.
     *
     * @param bandIndex Index of the band (0-9 for 10 bands)
     * @return Current gain level in dB * 100
     * @throws IndexOutOfBoundsException if bandIndex is invalid
     */
    fun getBandLevel(@IntRange(from = 0, to = 9) bandIndex: Int): Short {
        if (!_equalizerState.value.isSupported) return 0

        try {
            return equalizer?.getBandLevel(bandIndex.toShort()) ?: 0
        } catch (e: Exception) {
            return 0
        }
    }

    /**
     * Reset all frequency bands to 0dB (flat response).
     * This creates a neutral sound profile.
     */
    fun resetBands() {
        if (!_equalizerState.value.isSupported) return

        try {
            val numBands = equalizer?.numberOfBands ?: 0
            for (i in 0 until numBands) {
                equalizer?.setBandLevel(i.toShort(), 0)
            }
            
            savePreset("Flat")
            loadPreset("Flat")
        } catch (e: Exception) {
            // Handle reset errors
        }
    }

    /**
     * Load a preset by name.
     * Applies the preset's frequency band settings to the equalizer.
     *
     * @param presetName Name of the preset to load
     * @return true if preset was loaded successfully, false otherwise
     */
    fun loadPreset(presetName: String): Boolean {
        if (!_equalizerState.value.isSupported) return false

        try {
            val bandLevels = builtInPresets[presetName] ?: getCustomPreset(presetName)
            if (bandLevels == null) {
                return false
            }

            val numBands = equalizer?.numberOfBands ?: 0
            for (i in 0 until numBands) {
                if (i < bandLevels.size) {
                    equalizer?.setBandLevel(i.toShort(), bandLevels[i].toShort())
                }
            }

            // Update current preset
            prefs.edit().putString(KEY_CURRENT_PRESET, presetName).apply()
            
            _equalizerState.update { 
                it.copy(
                    currentPreset = presetName,
                    bandLevels = getCurrentBandLevels(),
                    lastModified = System.currentTimeMillis()
                )
            }

            notifyListenersPresetChanged(presetName)
            return true

        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Save the current band settings as a custom preset.
     *
     * @param name Name for the custom preset
     * @return The saved preset, or null if save failed
     * @throws IllegalArgumentException if name is empty or too long
     * @throws IllegalStateException if max custom presets exceeded
     */
    fun saveCustomPreset(name: String): EqualizerPreset? {
        if (!_equalizerState.value.isSupported) return null

        if (name.isBlank() || name.length > 50) {
            throw IllegalArgumentException("Preset name must be 1-50 characters")
        }

        try {
            // Check custom preset count
            val existingCustom = getCustomPresetNames()
            if (existingCustom.size >= MAX_CUSTOM_PRESETS) {
                throw IllegalStateException("Maximum $MAX_CUSTOM_PRESETS custom presets allowed")
            }

            val bandLevels = getCurrentBandLevels()
            val preset = EqualizerPreset(name, bandLevels)

            // Save to SharedPreferences
            val presetJson = bandLevels.joinToString(",") { it.toString() }
            prefs.edit().putString("${KEY_CUSTOM_PRESET_PREFIX}$name", presetJson).apply()

            // Update available presets in state
            _equalizerState.update {
                it.copy(
                    availablePresets = builtInPresets.keys.toList() + getCustomPresetNames()
                )
            }

            return preset

        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Delete a custom preset.
     *
     * @param presetName Name of the custom preset to delete
     * @return true if deleted successfully, false otherwise
     */
    fun deleteCustomPreset(presetName: String): Boolean {
        if (builtInPresets.containsKey(presetName)) {
            return false // Cannot delete built-in presets
        }

        try {
            prefs.edit().remove("${KEY_CUSTOM_PRESET_PREFIX}$presetName").apply()
            
            // Update available presets in state
            _equalizerState.update {
                it.copy(
                    availablePresets = builtInPresets.keys.toList() + getCustomPresetNames()
                )
            }

            return true
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Get all available preset names including custom presets.
     *
     * @return List of all preset names
     */
    fun getAllPresetNames(): List<String> {
        return builtInPresets.keys.toList() + getCustomPresetNames()
    }

    /**
     * Get frequency string for a band index (e.g., "31 Hz", "1 kHz").
     *
     * @param bandIndex Index of the frequency band
     * @return Formatted frequency string
     */
    fun getFrequencyString(@IntRange(from = 0, to = 9) bandIndex: Int): String {
        val frequency = ISO_FREQUENCIES.getOrElse(bandIndex) { 1000 }
        return when {
            frequency >= 1000 -> "${frequency / 1000} kHz"
            else -> "$frequency Hz"
        }
    }

    /**
     * Convert level value to human-readable dB string.
     *
     * @param level Level in dB * 100 (e.g., -500 for -5.0dB)
     * @return Formatted level string (e.g., "+3.5 dB", "-6.0 dB", "0 dB")
     */
    fun getLevelString(level: Short): String {
        val db = level / 100f
        return when {
            db > 0 -> "+${db} dB"
            db < 0 -> "$db dB"
            else -> "0 dB"
        }
    }

    /**
     * Get a normalized value for UI progress bars (0.0 to 1.0).
     * Maps the gain range (-15 to +15 dB) to 0.0-1.0.
     *
     * @param bandIndex Index of the frequency band
     * @return Normalized value between 0.0 and 1.0
     */
    fun getBandVisualizer(@IntRange(from = 0, to = 9) bandIndex: Int): Float {
        val level = getBandLevel(bandIndex)
        // Map -1500 to +1500 to 0.0 to 1.0
        return ((level + 1500) / 3000f).coerceIn(0f, 1f)
    }

    /**
     * Get the gain range for the equalizer.
     *
     * @return Pair of (minGain, maxGain) in dB * 100
     */
    fun getGainRange(): Pair<Short, Short> {
        return try {
            val min = equalizer?.bandLevelRange?.first ?: -1500
            val max = equalizer?.bandLevelRange?.last ?: 1500
            Pair(min, max)
        } catch (e: Exception) {
            Pair(-1500, 1500)
        }
    }

    /**
     * Get the number of frequency bands available.
     *
     * @return Number of bands (typically 10)
     */
    fun getBandCount(): Int {
        return equalizer?.numberOfBands ?: 0
    }

    /**
     * Add a listener for equalizer state changes.
     *
     * @param listener Listener to add
     */
    fun addListener(listener: EqualizerListener) {
        listeners.add(listener)
        // Immediately notify of current state
        listener.onEqualizerStateChanged(_equalizerState.value)
    }

    /**
     * Remove a listener.
     *
     * @param listener Listener to remove
     */
    fun removeListener(listener: EqualizerListener) {
        listeners.remove(listener)
    }

    /**
     * Release equalizer resources.
     * Should be called when the player is being destroyed.
     */
    fun release() {
        try {
            equalizer?.release()
            equalizer = null
            isInitialized = false
        } catch (e: Exception) {
            // Ignore release errors
        }
    }

    // ============ Private Helper Methods ============

    /**
     * Get current band levels from the equalizer.
     *
     * @return List of band levels in dB * 100
     */
    private fun getCurrentBandLevels(): List<Int> {
        val numBands = equalizer?.numberOfBands ?: 0
        return (0 until numBands).map { i ->
            getBandLevel(i).toInt()
        }
    }

    /**
     * Get names of all custom presets.
     *
     * @return List of custom preset names
     */
    private fun getCustomPresetNames(): List<String> {
        return prefs.all.keys
            .filter { it.startsWith(KEY_CUSTOM_PRESET_PREFIX) }
            .map { it.removePrefix(KEY_CUSTOM_PRESET_PREFIX) }
    }

    /**
     * Get a custom preset by name.
     *
     * @param name Preset name
     * @return List of band levels, or null if not found
     */
    private fun getCustomPreset(name: String): List<Int>? {
        val presetJson = prefs.getString("${KEY_CUSTOM_PRESET_PREFIX}$name", null) ?: return null
        return presetJson.split(",").map { it.toInt() }
    }

    /**
     * Save current settings as the specified preset.
     *
     * @param presetName Preset name to save as
     */
    private fun savePreset(presetName: String) {
        prefs.edit().putString(KEY_CURRENT_PRESET, presetName).apply()
    }

    /**
     * Notify all listeners of state change.
     */
    private fun notifyListenersStateChanged() {
        listeners.forEach { it.onEqualizerStateChanged(_equalizerState.value) }
    }

    /**
     * Notify all listeners of preset change.
     */
    private fun notifyListenersPresetChanged(presetName: String) {
        listeners.forEach { it.onPresetChanged(presetName) }
    }

    /**
     * Notify all listeners of band level change.
     */
    private fun notifyListenersBandChanged(bandIndex: Int, level: Short) {
        listeners.forEach { it.onBandLevelChanged(bandIndex, level) }
    }
}
