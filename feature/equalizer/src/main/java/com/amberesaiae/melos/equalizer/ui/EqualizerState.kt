/*
 * Copyright (c) 2024 Amberesaiae. All rights reserved.
 * SPDX-License-Identifier: MIT
 */
package com.amberesaiae.melos.equalizer.ui

import com.amberesaiae.melos.player.equalizer.EqualizerPreset

/**
 * UI state for the Equalizer screen.
 *
 * @property isEnabled Whether the equalizer is currently enabled
 * @property currentPreset Name of the currently loaded preset
 * @property bandLevels List of current gain levels for all bands (in dB * 100)
 * @property isSupported Whether the device supports hardware equalizer
 * @property availablePresets List of available preset names
 * @property audioSessionId Audio session ID for effects chain
 * @property lastModified Timestamp when settings were last modified
 * @property isLoading Whether the equalizer is currently loading
 * @property errorMessage Error message if initialization failed
 * @property showSavePresetDialog Whether to show the save preset dialog
 */
data class EqualizerState(
    val isEnabled: Boolean = false,
    val currentPreset: String = "Flat",
    val bandLevels: List<Int> = emptyList(),
    val isSupported: Boolean = false,
    val availablePresets: List<String> = emptyList(),
    val audioSessionId: Int = -1,
    val lastModified: Long = 0L,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val showSavePresetDialog: Boolean = false
) {
    companion object {
        val EMPTY = EqualizerState()
    }
}

/**
 * UI model for a single frequency band.
 *
 * @property index Band index (0-9)
 * @property frequency Center frequency in Hz
 * @property frequencyLabel Formatted frequency label (e.g., "31 Hz", "1 kHz")
 * @property level Current gain level in dB * 100
 * @property levelDb Current gain level in dB (float)
 * @property minLevel Minimum allowed level in dB * 100
 * @property maxLevel Maximum allowed level in dB * 100
 */
data class FrequencyBandUi(
    val index: Int,
    val frequency: Int,
    val frequencyLabel: String,
    val level: Int,
    val levelDb: Float,
    val minLevel: Int = -1200,
    val maxLevel: Int = 1200
) {
    companion object {
        /** ISO standard 10-band equalizer frequencies */
        val FREQUENCIES = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        
        /** Pre-computed level range: +/- 12dB * 100 */
        const val MIN_LEVEL = -1200
        const val MAX_LEVEL = 1200
        const val FLAT_LEVEL = 0
        
        /**
         * Format frequency for display.
         */
        fun formatFrequency(frequency: Int): String {
            return when {
                frequency >= 1000 -> {
                    val khz = frequency / 1000.0
                    if (khz % 1 == 0.0) {
                        "${khz.toInt()} kHz"
                    } else {
                        "${khz.toString().removeSuffix(".0")} kHz"
                    }
                }
                else -> "$frequency Hz"
            }
        }
    }
}

/**
 * Actions for the Equalizer screen.
 */
sealed class EqualizerAction {
    /** Toggle equalizer enabled/disabled */
    data object ToggleEnabled : EqualizerAction()
    
    /** Set a specific band level */
    data class SetBandLevel(val bandIndex: Int, val level: Int) : EqualizerAction()
    
    /** Load a preset by name */
    data class LoadPreset(val presetName: String) : EqualizerAction()
    
    /** Reset all bands to flat (0 dB) */
    data object ResetToFlat : EqualizerAction()
    
    /** Show save preset dialog */
    data object ShowSavePresetDialog : EqualizerAction()
    
    /** Hide save preset dialog */
    data object HideSavePresetDialog : EqualizerAction()
    
    /** Save custom preset with given name */
    data class SaveCustomPreset(val presetName: String) : EqualizerAction()
    
    /** Dismiss error message */
    data object DismissError : EqualizerAction()
}
