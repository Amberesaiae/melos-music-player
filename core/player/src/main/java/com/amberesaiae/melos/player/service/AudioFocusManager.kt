/*
 * Copyright (c) 2024 Amberesaiae. All rights reserved.
 * SPDX-License-Identifier: MIT
 */
package com.amberesaiae.melos.player.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Audio focus state for monitoring focus changes.
 *
 * @property hasFocus Whether audio focus is currently held
 * @property focusType Type of focus currently held (full, transient, etc.)
 * @property canDuck Whether ducking is allowed when focus is temporarily lost
 * @property lastFocusChange Timestamp of last focus change
 */
data class AudioFocusState(
    val hasFocus: Boolean = false,
    val focusType: AudioFocusType = AudioFocusType.NONE,
    val canDuck: Boolean = false,
    val lastFocusChange: Long = 0L
) {
    companion object {
        val EMPTY = AudioFocusState()
    }
}

/**
 * Types of audio focus.
 */
enum class AudioFocusType {
    /** No audio focus */
    NONE,

    /** Full permanent focus */
    GAIN,

    /** Temporary focus (e.g., for navigation prompts) */
    GAIN_TRANSIENT,

    /** Temporary focus with ducking allowed */
    GAIN_TRANSIENT_MAY_DUCK,

    /** Temporary focus exclusive (others must pause) */
    GAIN_TRANSIENT_EXCLUSIVE
}

/**
 * Listener interface for audio focus changes.
 */
fun interface OnAudioFocusChangedListener {
    fun onFocusChanged(hasFocus: Boolean)
}

/**
 * Manages audio focus for the Melos music player.
 *
 * Audio focus is an Android system mechanism that ensures only one app
 * plays audio at a time. When your app gains focus, it can play audio.
 * When it loses focus, it should pause or duck (lower volume).
 *
 * This class handles:
 * - Requesting audio focus before playback
 * - Responding to focus changes (pausing, ducking, resuming)
 * - Abandoning focus when playback stops
 * - State monitoring via StateFlow
 *
 * Audio Focus Behavior:
 * - GAIN: Full focus for music playback
 * - GAIN_TRANSIENT: Temporary focus (e.g., navigation announcement)
 * - GAIN_TRANSIENT_MAY_DUCK: Temporary focus, allows background music to continue at lower volume
 * - GAIN_TRANSIENT_EXCLUSIVE: Exclusive temporary focus (all other audio must stop)
 *
 * @property context Application context
 * @property onAudioFocusChanged Callback for focus changes
 */
class AudioFocusManager(
    private val context: Context,
    private val onAudioFocusChanged: (Boolean) -> Unit = {}
) {

    companion object {
        /** Default volume level for ducking (30% of normal) */
        private const val DUCK_VOLUME = 0.3f

        /** Delay before resuming after transient focus loss */
        private const val RESUME_DELAY_MS = 1000L
    }

    /** Audio manager system service */
    private val audioManager: AudioManager = 
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** MutableStateFlow for exposing audio focus state */
    private val _audioFocusState = MutableStateFlow(AudioFocusState.EMPTY)

    /** StateFlow for observing audio focus state */
    val audioFocusState: StateFlow<AudioFocusState> = _audioFocusState.asStateFlow()

    /** Registered focus change listeners */
    private val focusListeners = mutableListOf<OnAudioFocusChangedListener>()

    /** Handler for delayed operations */
    private val handler = Handler(Looper.getMainLooper())

    /** Whether audio focus has been requested */
    private var isFocusRequested = false

    /** Runnable for delayed resume after focus loss */
    private var resumeRunnable: Runnable? = null

    /**
     * Audio focus change listener for Android O and above.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private val audioFocusRequest: AudioFocusRequest? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    handleFocusChange(focusChange)
                }
                .build()
        } else {
            null
        }
    }

    /**
     * Audio focus change listener for pre-O versions.
     */
    @Suppress("DEPRECATION")
    private val preOFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        handleFocusChange(focusChange)
    }

    /**
     * Request audio focus for playback.
     * @return true if focus was granted, false otherwise
     */
    fun requestAudioFocus(): Boolean {
        if (isFocusRequested) {
            return _audioFocusState.value.hasFocus
        }

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { request ->
                audioManager.requestAudioFocus(request)
            } ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                preOFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }

        isFocusRequested = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        
        if (isFocusRequested) {
            _audioFocusState.update {
                it.copy(
                    hasFocus = true,
                    focusType = AudioFocusType.GAIN,
                    lastFocusChange = System.currentTimeMillis()
                )
            }
            onFocusChanged(true)
        }

        return isFocusRequested
    }

    /**
     * Abandon audio focus when playback is complete.
     * Should be called when stopping playback.
     */
    fun abandonAudioFocus() {
        if (!isFocusRequested) return

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { request ->
                audioManager.abandonAudioFocusRequest(request)
            } ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(preOFocusChangeListener)
        }

        isFocusRequested = false
        
        _audioFocusState.update {
            it.copy(
                hasFocus = false,
                focusType = AudioFocusType.NONE,
                canDuck = false,
                lastFocusChange = System.currentTimeMillis()
            )
        }

        // Cancel any pending resume
        resumeRunnable?.let { handler.removeCallbacks(it) }

        onFocusChanged(false)
    }

    /**
     * Add a focus change listener.
     * @param listener Listener to add
     */
    fun addFocusListener(listener: OnAudioFocusChangedListener) {
        focusListeners.add(listener)
        // Immediately notify of current state
        listener.onFocusChanged(_audioFocusState.value.hasFocus)
    }

    /**
     * Remove a focus change listener.
     * @param listener Listener to remove
     */
    fun removeFocusListener(listener: OnAudioFocusChangedListener) {
        focusListeners.remove(listener)
    }

    /**
     * Release all resources.
     * Should be called when the manager is no longer needed.
     */
    fun release() {
        abandonAudioFocus()
        focusListeners.clear()
        handler.removeCallbacksAndMessages(null)
    }

    // ============ Private Methods ============

    /**
     * Handle audio focus change events.
     * @param focusChange The type of focus change
     */
    private fun handleFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Permanent focus gain - resume normal playback
                handleFocusGain()
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent focus loss - stop playback
                handleFocusLoss()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Temporary focus loss - pause playback
                handleFocusLossTransient()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Temporary focus loss with ducking - lower volume
                handleFocusLossCanDuck()
            }
        }

        // Notify all listeners
        val hasFocus = focusChange == AudioManager.AUDIOFOCUS_GAIN
        onFocusChanged(hasFocus)
    }

    /**
     * Handle permanent focus gain.
     */
    private fun handleFocusGain() {
        // Cancel any pending resume
        resumeRunnable?.let { handler.removeCallbacks(it) }

        _audioFocusState.update {
            it.copy(
                hasFocus = true,
                focusType = AudioFocusType.GAIN,
                canDuck = false,
                lastFocusChange = System.currentTimeMillis()
            )
        }

        // Notify listeners
        focusListeners.forEach { it.onFocusChanged(true) }
    }

    /**
     * Handle permanent focus loss.
     */
    private fun handleFocusLoss() {
        _audioFocusState.update {
            it.copy(
                hasFocus = false,
                focusType = AudioFocusType.NONE,
                canDuck = false,
                lastFocusChange = System.currentTimeMillis()
            )
        }

        // Player should stop completely
        focusListeners.forEach { it.onFocusChanged(false) }
    }

    /**
     * Handle temporary focus loss.
     */
    private fun handleFocusLossTransient() {
        _audioFocusState.update {
            it.copy(
                hasFocus = false,
                focusType = AudioFocusType.GAIN_TRANSIENT,
                canDuck = false,
                lastFocusChange = System.currentTimeMillis()
            )
        }

        // Player should pause
        focusListeners.forEach { it.onFocusChanged(false) }
    }

    /**
     * Handle temporary focus loss with ducking.
     */
    private fun handleFocusLossCanDuck() {
        _audioFocusState.update {
            it.copy(
                hasFocus = true,
                focusType = AudioFocusType.GAIN_TRANSIENT_MAY_DUCK,
                canDuck = true,
                lastFocusChange = System.currentTimeMillis()
            )
        }

        // Player should duck (lower volume)
        focusListeners.forEach { it.onFocusChanged(true) }
    }

    /**
     * Notify the main callback of focus change.
     */
    private fun onFocusChanged(hasFocus: Boolean) {
        onAudioFocusChanged(hasFocus)
    }
}
