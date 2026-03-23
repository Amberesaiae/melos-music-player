/*
 * Copyright (c) 2024 Amberesaiae. All rights reserved.
 * SPDX-License-Identifier: MIT
 */
package com.amberesaiae.melos.equalizer.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amberesaiae.melos.player.equalizer.EqualizerController
import com.amberesaiae.melos.player.equalizer.EqualizerState as ControllerEqualizerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Equalizer screen.
 * 
 * Collects state from EqualizerController and handles UI actions.
 */
@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val equalizerController: EqualizerController
) : ViewModel() {
    
    companion object {
        private const val TAG = "EqualizerViewModel"
    }
    
    private val _state = MutableStateFlow(EqualizerState.EMPTY)
    val state: StateFlow<EqualizerState> = _state.asStateFlow()
    
    init {
        observeEqualizerState()
    }
    
    /**
     * Observe equalizer state from controller.
     */
    private fun observeEqualizerState() {
        viewModelScope.launch {
            equalizerController.state.collect { controllerState ->n                _state.update { currentState ->
                    currentState.copy(
                        isEnabled = controllerState.isEnabled,
                        currentPreset = controllerState.currentPreset,
                        bandLevels = controllerState.bandLevels,
                        isSupported = controllerState.isSupported,
                        availablePresets = controllerState.availablePresets,
                        audioSessionId = controllerState.audioSessionId,
                        lastModified = controllerState.lastModified,
                        isLoading = false
                    )
                }
            }
        }
    }
    
    /**
     * Process UI actions.
     */
    fun onAction(action: EqualizerAction) {
        when (action) {
            is EqualizerAction.ToggleEnabled -> toggleEnabled()
            is EqualizerAction.SetBandLevel -> setBandLevel(action.bandIndex, action.level)
            is EqualizerAction.LoadPreset -> loadPreset(action.presetName)
            is EqualizerAction.ResetToFlat -> resetToFlat()
            is EqualizerAction.ShowSavePresetDialog -> showSavePresetDialog()
            is EqualizerAction.HideSavePresetDialog -> hideSavePresetDialog()
            is EqualizerAction.SaveCustomPreset -> saveCustomPreset(action.presetName)
            is EqualizerAction.DismissError -> dismissError()
        }
    }
    
    private fun toggleEnabled() {
        viewModelScope.launch {
            try {
                val newState = !_state.value.isEnabled
                equalizerController.setEnabled(newState)
                Log.d(TAG, "Equalizer ${if (newState) "enabled" else "disabled"}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle equalizer", e)
                _state.update { it.copy(errorMessage = "Failed to toggle equalizer: ${e.message}") }
            }
        }
    }
    
    private fun setBandLevel(bandIndex: Int, level: Int) {
        viewModelScope.launch {
            try {
                equalizerController.setBandLevel(bandIndex, level.toShort())
                Log.d(TAG, "Band $bandIndex level set to $level")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set band level", e)
            }
        }
    }
    
    private fun loadPreset(presetName: String) {
        viewModelScope.launch {
            try {
                equalizerController.loadPreset(presetName)
                Log.d(TAG, "Loaded preset: $presetName")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load preset", e)
                _state.update { it.copy(errorMessage = "Failed to load preset: ${e.message}") }
            }
        }
    }
    
    private fun resetToFlat() {
        viewModelScope.launch {
            try {
                equalizerController.loadPreset("Flat")
                Log.d(TAG, "Reset to flat")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reset to flat", e)
                _state.update { it.copy(errorMessage = "Failed to reset: ${e.message}") }
            }
        }
    }
    
    private fun showSavePresetDialog() {
        _state.update { it.copy(showSavePresetDialog = true) }
    }
    
    private fun hideSavePresetDialog() {
        _state.update { it.copy(showSavePresetDialog = false) }
    }
    
    private fun saveCustomPreset(presetName: String) {
        viewModelScope.launch {
            try {
                val bandLevels = _state.value.bandLevels
                equalizerController.saveCustomPreset(presetName, bandLevels)
                _state.update { it.copy(showSavePresetDialog = false) }
                Log.d(TAG, "Saved custom preset: $presetName")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save preset", e)
                _state.update { 
                    it.copy(
                        showSavePresetDialog = false,
                        errorMessage = "Failed to save preset: ${e.message}"
                    ) 
                }
            }
        }
    }
    
    private fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }
    
    override fun onCleared() {
        super.onCleared()
        // Controller lifecycle is managed by Hilt/Application
    }
}
