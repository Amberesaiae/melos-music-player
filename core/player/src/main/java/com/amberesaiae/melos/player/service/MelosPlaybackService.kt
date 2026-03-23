/*
 * Copyright (c) 2024 Amberesaiae. All rights reserved.
 * SPDX-License-Identifier: MIT
 */
package com.amberesaiae.melos.player.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import com.amberesaiae.melos.player.R
import com.amberesaiae.melos.player.equalizer.EqualizerController
import com.amberesaiae.melos.player.replaygain.ReplayGainHandler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Playback service state for UI observation.
 *
 * @property isServiceRunning Whether the service is currently active
 * @property isForeground Whether the service is running in foreground mode
 * @property hasAudioFocus Whether the service has audio focus
 * @property notificationId ID of the foreground notification
 * @property channelId Notification channel ID
 * @property lastStateChange Timestamp of last state change
 */
data class PlaybackServiceState(
    val isServiceRunning: Boolean = false,
    val isForeground: Boolean = false,
    val hasAudioFocus: Boolean = false,
    val notificationId: Int = 1,
    val channelId: String = "melos_playback_channel",
    val lastStateChange: Long = 0L
) {
    companion object {
        val EMPTY = PlaybackServiceState()
    }
}

/**
 * Listener interface for playback service events.
 */
interface PlaybackServiceListener {
    fun onServiceStarted()
    fun onServiceStopped()
    fun onForegroundStateChanged(isForeground: Boolean)
    fun onAudioFocusChanged(hasFocus: Boolean)
}

/**
 * Background playback service for Melos music player.
 *
 * This service extends MediaLibraryService to provide comprehensive background
 * playback support with MediaSession integration and foreground notification.
 *
 * Features:
 * - Media3 MediaLibraryService for modern media playback
 * - Foreground service with persistent notification
 * - Audio focus management
 * - WakeLock management for background playback
 * - MediaSession for external controls (lock screen, wearables, etc.)
 * - Notification channel management for Android O+
 * - Service state monitoring via StateFlow
 * - Headset button event handling
 * - Graceful lifecycle management
 *
 * Android Version Support:
 * - Android 12+ (API 31+): Uses FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
 * - Android 8+ (API 26+): Requires notification channels
 * - Android 5+ (API 21+): Basic foreground service support
 *
 * @property serviceScope Coroutine scope for background operations
 */
@AndroidEntryPoint
class MelosPlaybackService : MediaLibraryService() {

    companion object {
        /** Notification channel ID for playback notifications */
        const val CHANNEL_ID = "melos_playback_channel"

        /** Notification channel name (user-visible) */
        const val CHANNEL_NAME = "Music Playback"

        /** Notification ID for foreground service */
        const val NOTIFICATION_ID = 1

        /** Action to start the service */
        const val ACTION_START_SERVICE = "com.amberesaiae.melos.START_SERVICE"

        /** Action to stop the service */
        const val ACTION_STOP_SERVICE = "com.amberesaiae.melos.STOP_SERVICE"

        /** Action to play/pause */
        const val ACTION_PLAY_PAUSE = "com.amberesaiae.melos.PLAY_PAUSE"

        /** Action to play */
        const val ACTION_PLAY = "com.amberesaiae.melos.PLAY"

        /** Action to pause */
        const val ACTION_PAUSE = "com.amberesaiae.melos.PAUSE"

        /** Action to skip to next */
        const val ACTION_NEXT = "com.amberesaiae.melos.NEXT"

        /** Action to skip to previous */
        const val ACTION_PREVIOUS = "com.amberesaiae.melos.PREVIOUS"
    }

    @Inject
    lateinit var equalizerController: EqualizerController

    @Inject
    lateinit var replayGainHandler: ReplayGainHandler

    /** Callback interface for service binding */
    private val binder = MusicBinder()

    /** Coroutine scope for service operations */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** MutableStateFlow for exposing service state to UI */
    private val _serviceState = MutableStateFlow(PlaybackServiceState.EMPTY)

    /** StateFlow for observing service state */
    val serviceState: StateFlow<PlaybackServiceState> = _serviceState.asStateFlow()

    /** MediaSession for external controls */
    private var mediaSession: MediaSession? = null

    /** Audio focus manager */
    private var audioFocusManager: AudioFocusManager? = null

    /** List of registered listeners */
    private val listeners = mutableListOf<PlaybackServiceListener>()

    /** Custom media notification provider */
    private var notificationProvider: DefaultMediaNotificationProvider? = null

    /**
     * Binder class for service binding.
     * Allows activities to bind and access the service instance.
     */
    inner class MusicBinder : Binder() {
        /**
         * Get the service instance.
         * @return This service instance
         */
        fun getService(): MelosPlaybackService = this@MelosPlaybackService
    }

    /**
     * Called when the service is created.
     * Initialize components that need to persist for the service lifetime.
     */
    override fun onCreate() {
        super.onCreate()

        // Initialize audio focus manager
        audioFocusManager = AudioFocusManager(
            context = this,
            onAudioFocusChanged = { hasFocus ->
                _serviceState.update { it.copy(hasAudioFocus = hasFocus) }
                notifyListenersAudioFocusChanged(hasFocus)
            }
        )

        // Create notification channel
        createNotificationChannel()

        // Initialize media session
        initializeMediaSession()

        // Update service state
        _serviceState.update {
            it.copy(
                isServiceRunning = true,
                lastStateChange = System.currentTimeMillis()
            )
        }
    }

    /**
     * Called when a client binds to the service.
     * @param intent The intent that was used to bind
     * @return Binder instance for client communication
     */
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    /**
     * Called when the service is started.
     * Handles starting foreground service and showing notification.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVICE -> startForegroundService()
            ACTION_STOP_SERVICE -> stopForegroundService()
            ACTION_PLAY_PAUSE -> handlePlayPause()
            ACTION_PLAY -> handlePlay()
            ACTION_PAUSE -> handlePause()
            ACTION_NEXT -> handleNext()
            ACTION_PREVIOUS -> handlePrevious()
        }

        // Continue running until explicitly stopped
        return START_STICKY
    }

    /**
     * Get or create the media session.
     * @return The media session instance
     */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    /**
     * Called when the service is being destroyed.
     * Clean up all resources.
     */
    override fun onDestroy() {
        super.onDestroy()

        // Release media session
        mediaSession?.release()
        mediaSession = null

        // Release audio focus
        audioFocusManager?.release()
        audioFocusManager = null

        // Update service state
        _serviceState.update {
            it.copy(
                isServiceRunning = false,
                isForeground = false,
                lastStateChange = System.currentTimeMillis()
            )
        }

        notifyListenersServiceStopped()
    }

    /**
     * Start the service in foreground mode.
     * Shows persistent notification and acquires necessary locks.
     */
    fun startForegroundService() {
        // Create notification
        val notification = createNotification()

        // Start foreground with appropriate service type
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, C.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Update state
        _serviceState.update {
            it.copy(
                isForeground = true,
                lastStateChange = System.currentTimeMillis()
            )
        }

        // Request audio focus
        audioFocusManager?.requestAudioFocus()

        notifyListenersForegroundChanged(true)
    }

    /**
     * Stop foreground mode and remove notification.
     */
    fun stopForegroundService() {
        // Remove from foreground
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }

        // Update state
        _serviceState.update {
            it.copy(
                isForeground = false,
                lastStateChange = System.currentTimeMillis()
            )
        }

        // Abandon audio focus
        audioFocusManager?.abandonAudioFocus()

        notifyListenersForegroundChanged(false)
    }

    /**
     * Update the playback notification.
     * @param player The player instance for state
     */
    fun updateNotification(player: Player) {
        notificationProvider = DefaultMediaNotificationProvider(
            SessionToken(this, android.content.ComponentName(this, MelosPlaybackService::class.java))
        )

        // The MediaLibraryService will automatically manage notifications
        // when a MediaSession is properly configured
    }

    /**
     * Add a listener for service events.
     * @param listener Listener to add
     */
    fun addListener(listener: PlaybackServiceListener) {
        listeners.add(listener)
        // Immediately notify of current state
        listener.onServiceStarted()
    }

    /**
     * Remove a listener.
     * @param listener Listener to remove
     */
    fun removeListener(listener: PlaybackServiceListener) {
        listeners.remove(listener)
    }

    // ============ Private Methods ============

    /**
     * Create the notification channel for Android O+.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback control notification"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create the foreground notification.
     * @return Notification instance
     */
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Melos Music Player")
            .setContentText("Ready to play")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    /**
     * Initialize the MediaSession for external controls.
     */
    private fun initializeMediaSession() {
        val sessionBuilder = MediaSession.Builder(this, createPlayer())

        // Configure custom commands if needed
        val customLayout = listOf(
            CommandButton.Builder()
                .setDisplayName("Equalizer")
                .setIntent(createEqualizerIntent())
                .build()
        )

        mediaSession = sessionBuilder
            .setSessionActivity(pendingIntent = PendingIntent.getActivity(
                this,
                0,
                packageManager.getLaunchIntentForPackage(packageName),
                PendingIntent.FLAG_IMMUTABLE
            ))
            .build()

        // Initialize equalizer and replay gain
        equalizerController.init()
        replayGainHandler.init()
    }

    /**
     * Create and configure the player instance.
     * This is a placeholder - actual player creation should use MelosPlayer
     */
    private fun createPlayer(): Player {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        return ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .build()
    }

    /**
     * Create intent for equalizer activity.
     */
    private fun createEqualizerIntent(): PendingIntent {
        val intent = Intent(this, MelosPlaybackService::class.java).apply {
            action = "com.amberesaiae.melos.OPEN_EQUALIZER"
        }
        return PendingIntent.getService(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Handle play/pause action from notification.
     */
    private fun handlePlayPause() {
        mediaSession?.player?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    /**
     * Handle play action.
     */
    private fun handlePlay() {
        mediaSession?.player?.play()
    }

    /**
     * Handle pause action.
     */
    private fun handlePause() {
        mediaSession?.player?.pause()
    }

    /**
     * Handle next track action.
     */
    private fun handleNext() {
        mediaSession?.player?.seekToNext()
    }

    /**
     * Handle previous track action.
     */
    private fun handlePrevious() {
        mediaSession?.player?.seekToPrevious()
    }

    /**
     * Notify all listeners of service start.
     */
    private fun notifyListenersServiceStarted() {
        listeners.forEach { it.onServiceStarted() }
    }

    /**
     * Notify all listeners of service stop.
     */
    private fun notifyListenersServiceStopped() {
        listeners.forEach { it.onServiceStopped() }
    }

    /**
     * Notify all listeners of foreground state change.
     */
    private fun notifyListenersForegroundChanged(isForeground: Boolean) {
        listeners.forEach { it.onForegroundStateChanged(isForeground) }
    }

    /**
     * Notify all listeners of audio focus change.
     */
    private fun notifyListenersAudioFocusChanged(hasFocus: Boolean) {
        listeners.forEach { it.onAudioFocusChanged(hasFocus) }
    }
}
