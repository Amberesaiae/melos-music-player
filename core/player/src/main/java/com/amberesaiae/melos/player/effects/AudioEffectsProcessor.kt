/*
 * Copyright (c) 2024 Amberesaiae. All rights reserved.
 * SPDX-License-Identifier: MIT
 */
package com.amberesaiae.melos.player.effects

import android.content.Context
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import com.amberesaiae.melos.player.MelosPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bass boost settings.
 *
 * @property enabled Whether bass boost is active
 * @property strength Bass boost strength (0-1000)
 * @property supported Whether the device supports bass boost
 */
data class BassBoostSettings(
    val enabled: Boolean = false,
    val strength: Int = 0,
    val supported: Boolean = false
)

/**
 * Loudness enhancer settings.
 *
 * @property enabled Whether loudness enhancement is active
 * @property targetLevel Target gain in millibels (mB)
 * @property supported Whether the device supports loudness enhancement
 */
data class LoudnessEnhancerSettings(
    val enabled: Boolean = false,
    val targetLevel: Int = 0,
    val supported: Boolean = false
)

/**
 * Virtualizer settings for 3D audio effect.
 *
 * @property enabled Whether virtualizer is active
 * @property strength Virtualizer strength (0-1000)
 * @property supported Whether the device supports virtualizer
 */
data class VirtualizerSettings(
    val enabled: Boolean = false,
    val strength: Int = 0,
    val supported: Boolean = false
)

/**
 * Complete audio effects state for UI observation.
 *
 * @property bassBoost Bass boost configuration
 * @property loudnessEnhancer Loudness enhancer configuration
 * @property virtualizer Virtualizer configuration
 * @property preset Current effects preset name
 * @property audioSessionId Audio session ID for effects chain
 * @property lastModified Timestamp of last modification
 */
data class AudioEffectsState(
    val bassBoost: BassBoostSettings = BassBoostSettings(),
    val loudnessEnhancer: LoudnessEnhancerSettings = LoudnessEnhancerSettings(),
    val virtualizer: VirtualizerSettings = VirtualizerSettings(),
    val preset: String = "Custom",
    val audioSessionId: Int = AudioEffect.ERROR_INVALID_OPERATION,
    val lastModified: Long = 0L
) {
    companion object {
        val EMPTY = AudioEffectsState()
    }
}

/**
 * Audio effects preset definition.
 *
 * @property name Preset name
 * @property bassBoostStrength Bass boost strength (0-1000)
 * @property loudnessTarget Target loudness in mB
 * @property virtualizerStrength Virtualizer strength (0-1000)
 */
data class EffectsPreset(
    val name: String,
    val bassBoostStrength: Int,
    val loudnessTarget: Int,
    val virtualizerStrength: Int
) {
    companion object {
        /** Flat/Neutral - No effects */
        val FLAT = EffectsPreset("Flat", 0, 0, 0)

        /** Bass Boost - Enhanced low frequencies */
        val BASS_BOOST = EffectsPreset("Bass Boost", 800, 0, 0)

        /** Vocal Boost - Enhanced clarity for vocals */
        val VOCAL_BOOST = EffectsPreset("Vocal Boost", 200, 300, 100)

        /** Live/Concert - Simulates live performance */
        val LIVE = EffectsPreset("Live", 400, 200, 600)

        /** Movie/Cinema - Enhanced for film audio */
        val CINEMA = EffectsPreset("Cinema", 500, 400, 800)

        /** Night Mode - Compressed dynamics for quiet listening */
        val NIGHT = EffectsPreset("Night", 100, 500, 100)

        /** Party - Loud and energetic */
        val PARTY = EffectsPreset("Party", 600, 600, 400)
    }
}

/**
 * Listener interface for audio effects changes.
 */
interface AudioEffectsListener {
    fun onEffectsStateChanged(state: AudioEffectsState)
    fun onPresetChanged(preset: String)
    fun onBassBoostChanged(enabled: Boolean, strength: Int)
    fun onLoudnessChanged(enabled: Boolean, targetLevel: Int)
    fun onVirtualizerChanged(enabled: Boolean, strength: Int)
}

/**
 * Audio effects processor for Melos music player.
 *
 * This class provides comprehensive audio effects processing using Android's
 * AudioEffect API, including:
 * - Bass Boost: Enhances low-frequency response
 * - Loudness Enhancer: Increases perceived volume without clipping
 * - Virtualizer: Creates a spatial/3D audio effect
 *
 * Features:
 * - Individual effect control (enable/disable, strength adjustment)
 * - Built-in presets for common use cases
 * - Custom preset support
 * - StateFlow for real-time UI observation
 * - Audio session management for effects chain
 * - Graceful fallback for unsupported effects
 *
 * Audio Session:
 * All effects are tied to an audio session ID, which must match the
 * player's audio session. This ensures effects are applied to the
 * correct audio stream.
 *
 * Effect Strength:
 * Strength values range from 0 to 1000, where:
 * - 0 = No effect
 * - 1000 = Maximum effect
 * - 500 = Moderate effect (typical default)
 *
 * @property context Application context
 * @property melosPlayer MelosPlayer instance for audio session
 */
@Singleton
class AudioEffectsProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val melosPlayer: MelosPlayer
) {

    companion object {
        /** SharedPreferences file name for effects settings */
        private const val PREFS_NAME = "melos_audio_effects"

        /** Key for storing current preset */
        private const val KEY_PRESET = "current_preset"

        /** Key for storing bass boost strength */
        private const val KEY_BASS_BOOST = "bass_boost_strength"

        /** Key for storing loudness target */
        private const val KEYLOUDNESS = "loudness_target"

        /** Key for storing virtualizer strength */
        private const val KEY_VIRTUALIZER = "virtualizer_strength"

        /** Default loudness target in millibels */
        const val DEFAULT_LOUDNESS_TARGET = 0

        /** Maximum loudness target in millibels */
        const val MAX_LOUDNESS_TARGET = 2000

        /** Minimum loudness target in millibels */
        const val MIN_LOUDNESS_TARGET = -2000
    }

    /** MutableStateFlow for exposing effects state to UI */
    private val _effectsState = MutableStateFlow(AudioEffectsState.EMPTY)

    /** StateFlow for observing effects state */
    val effectsState: StateFlow<AudioEffectsState> = _effectsState.asStateFlow()

    /** BassBoost effect instance */
    private var bassBoost: BassBoost? = null

    /** LoudnessEnhancer effect instance */
    private var loudnessEnhancer: LoudnessEnhancer? = null

    /** Virtualizer effect instance */
    private var virtualizer: Virtualizer? = null

    /** List of registered listeners */
    private val listeners = mutableListOf<AudioEffectsListener>()

    /** Whether effects have been initialized */
    private var isInitialized = false

    /** Built-in presets */
    private val builtInPresets = listOf(
        EffectsPreset.FLAT,
        EffectsPreset.BASS_BOOST,
        EffectsPreset.VOCAL_BOOST,
        EffectsPreset.LIVE,
        EffectsPreset.CINEMA,
        EffectsPreset.NIGHT,
        EffectsPreset.PARTY
    )

    /**
     * Initialize the audio effects processor.
     * Creates and configures all available audio effects.
     */
    fun init() {
        if (isInitialized) return

        try {
            // Get audio session ID from player
            val audioSessionId = melosPlayer.getAudioSessionId()
            if (audioSessionId == AudioEffect.ERROR_INVALID_OPERATION) {
                isInitialized = true
                return
            }

            // Initialize BassBoost
            bassBoost = try {
                BassBoost(0, audioSessionId).apply {
                    enabled = false
                }
            } catch (e: Exception) {
                null
            }

            // Initialize LoudnessEnhancer
            loudnessEnhancer = try {
                LoudnessEnhancer(audioSessionId).apply {
                    enabled = false
                }
            } catch (e: Exception) {
                null
            }

            // Initialize Virtualizer
            virtualizer = try {
                Virtualizer(0, audioSessionId).apply {
                    enabled = false
                }
            } catch (e: Exception) {
                null
            }

            // Update state with supported effects
            _effectsState.update {
                it.copy(
                    bassBoost = it.bassBoost.copy(supported = bassBoost != null),
                    loudnessEnhancer = it.loudnessEnhancer.copy(supported = loudnessEnhancer != null),
                    virtualizer = it.virtualizer.copy(supported = virtualizer != null),
                    audioSessionId = audioSessionId
                )
            }

            isInitialized = true

        } catch (e: Exception) {
            // Handle initialization errors gracefully
        }
    }

    /**
     * Enable or disable bass boost.
     *
     * @param enabled true to enable, false to disable
     * @param strength Bass boost strength (0-1000)
     */
    fun setBassBoost(enabled: Boolean, strength: Int = _effectsState.value.bassBoost.strength) {
        if (!isInitialized || bassBoost == null) return

        try {
            val clampedStrength = strength.coerceIn(0, 1000)
            bassBoost?.strength = clampedStrength.toShort()
            bassBoost?.enabled = enabled

            _effectsState.update {
                it.copy(
                    bassBoost = it.bassBoost.copy(
                        enabled = enabled,
                        strength = clampedStrength
                    ),
                    lastModified = System.currentTimeMillis()
                )
            }

            notifyListenersBassBoostChanged(enabled, clampedStrength)
            notifyListenersStateChanged()

        } catch (e: Exception) {
            // Handle bass boost errors gracefully
        }
    }

    /**
     * Set loudness enhancer target level.
     *
     * @param enabled true to enable, false to disable
     * @param targetLevel Target gain in millibels (mB), typically -2000 to +2000
     */
    fun setLoudnessEnhancer(
        enabled: Boolean,
        targetLevel: Int = _effectsState.value.loudnessEnhancer.targetLevel
    ) {
        if (!isInitialized || loudnessEnhancer == null) return

        try {
            val clampedLevel = targetLevel.coerceIn(MIN_LOUDNESS_TARGET, MAX_LOUDNESS_TARGET)
            loudnessEnhancer?.setTargetGain(clampedLevel)
            loudnessEnhancer?.enabled = enabled

            _effectsState.update {
                it.copy(
                    loudnessEnhancer = it.loudnessEnhancer.copy(
                        enabled = enabled,
                        targetLevel = clampedLevel
                    ),
                    lastModified = System.currentTimeMillis()
                )
            }

            notifyListenersLoudnessChanged(enabled, clampedLevel)
            notifyListenersStateChanged()

        } catch (e: Exception) {
            // Handle loudness enhancer errors
        }
    }

    /**
     * Set virtualizer strength.
     *
     * @param enabled true to enable, false to disable
     * @param strength Virtualizer strength (0-1000)
     */
    fun setVirtualizer(enabled: Boolean, strength: Int = _effectsState.value.virtualizer.strength) {
        if (!isInitialized || virtualizer == null) return

        try {
            val clampedStrength = strength.coerceIn(0, 1000)
            virtualizer?.strength = clampedStrength.toShort()
            virtualizer?.enabled = enabled

            _effectsState.update {
                it.copy(
                    virtualizer = it.virtualizer.copy(
                        enabled = enabled,
                        strength = clampedStrength
                    ),
                    lastModified = System.currentTimeMillis()
                )
            }

            notifyListenersVirtualizerChanged(enabled, clampedStrength)
            notifyListenersStateChanged()

        } catch (e: Exception) {
            // Handle virtualizer errors
        }
    }

    /**
     * Load a preset configuration.
     *
     * @param preset The preset to load
     * @return true if preset was loaded successfully
     */
    fun loadPreset(preset: EffectsPreset): Boolean {
        if (!isInitialized) return false

        try {
            // Apply bass boost
            setBassBoost(
                enabled = preset.bassBoostStrength > 0,
                strength = preset.bassBoostStrength
            )

            // Apply loudness enhancer
            setLoudnessEnhancer(
                enabled = preset.loudnessTarget != 0,
                targetLevel = preset.loudnessTarget
            )

            // Apply virtualizer
            setVirtualizer(
                enabled = preset.virtualizerStrength > 0,
                strength = preset.virtualizerStrength
            )

            // Update preset name in state
            _effectsState.update {
                it.copy(
                    preset = preset.name,
                    lastModified = System.currentTimeMillis()
                )
            }

            notifyListenersPresetChanged(preset.name)
            return true

        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Load a preset by name.
     *
     * @param name Name of the preset to load
     * @return true if preset was found and loaded
     */
    fun loadPresetByName(name: String): Boolean {
        val preset = builtInPresets.find { it.name.equals(name, ignoreCase = true) }
        return preset?.let { loadPreset(it) } ?: false
    }

    /**
     * Get all available built-in presets.
     *
     * @return List of preset names
     */
    fun getAvailablePresets(): List<String> {
        return builtInPresets.map { it.name }
    }

    /**
     * Get a preset by name.
     *
     * @param name Preset name
     * @return EffectsPreset or null if not found
     */
    fun getPreset(name: String): EffectsPreset? {
        return builtInPresets.find { it.name.equals(name, ignoreCase = true) }
    }

    /**
     * Reset all effects to default (flat) settings.
     */
    fun resetToFlat() {
        loadPreset(EffectsPreset.FLAT)
    }

    /**
     * Enable all effects with moderate settings.
     */
    fun enableAllEffects() {
        setBassBoost(enabled = true, strength = 500)
        setLoudnessEnhancer(enabled = true, targetLevel = 300)
        setVirtualizer(enabled = true, strength = 400)
    }

    /**
     * Disable all effects.
     */
    fun disableAllEffects() {
        setBassBoost(enabled = false)
        setLoudnessEnhancer(enabled = false)
        setVirtualizer(enabled = false)
    }

    /**
     * Get the current effects state.
     */
    fun getEffectsState(): AudioEffectsState = _effectsState.value

    /**
     * Check if any effect is currently enabled.
     */
    fun isAnyEffectEnabled(): Boolean {
        val state = _effectsState.value
        return state.bassBoost.enabled ||
                state.loudnessEnhancer.enabled ||
                state.virtualizer.enabled
    }

    /**
     * Add a listener for effects changes.
     *
     * @param listener Listener to add
     */
    fun addListener(listener: AudioEffectsListener) {
        listeners.add(listener)
        // Immediately notify of current state
        listener.onEffectsStateChanged(_effectsState.value)
    }

    /**
     * Remove a listener.
     *
     * @param listener Listener to remove
     */
    fun removeListener(listener: AudioEffectsListener) {
        listeners.remove(listener)
    }

    /**
     * Release all audio effect resources.
     * Should be called when the player is being destroyed.
     */
    fun release() {
        try {
            bassBoost?.release()
            bassBoost = null

            loudnessEnhancer?.release()
            loudnessEnhancer = null

            virtualizer?.release()
            virtualizer = null

            isInitialized = false
        } catch (e: Exception) {
            // Ignore release errors
        }
    }

    // ============ Private Methods ============

    /**
     * Notify all listeners of state change.
     */
    private fun notifyListenersStateChanged() {
        listeners.forEach { it.onEffectsStateChanged(_effectsState.value) }
    }

    /**
     * Notify all listeners of preset change.
     */
    private fun notifyListenersPresetChanged(preset: String) {
        listeners.forEach { it.onPresetChanged(preset) }
    }

    /**
     * Notify all listeners of bass boost change.
     */
    private fun notifyListenersBassBoostChanged(enabled: Boolean, strength: Int) {
        listeners.forEach { it.onBassBoostChanged(enabled, strength) }
    }

    /**
     * Notify all listeners of loudness change.
     */
    private fun notifyListenersLoudnessChanged(enabled: Boolean, targetLevel: Int) {
        listeners.forEach { it.onLoudnessChanged(enabled, targetLevel) }
    }

    /**
     * Notify all listeners of virtualizer change.
     */
    private fun notifyListenersVirtualizerChanged(enabled: Boolean, strength: Int) {
        listeners.forEach { it.onVirtualizerChanged(enabled, strength) }
    }
}
