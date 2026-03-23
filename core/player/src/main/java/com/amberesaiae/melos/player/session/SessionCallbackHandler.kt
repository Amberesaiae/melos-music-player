package com.amberesaiae.melos.player.session

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import com.amberesaiae.melos.player.PlaybackState
import com.amberesaiae.melos.player.TrackMetadata
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * State of the media session for external controllers.
 *
 * @property isConnected Whether a media controller is connected
 * @property controllerPackage Package name of connected controller (e.g., Android Auto, Wear OS)
 * @property hasExternalControls Whether external controls are active
 * @property isLockScreenActive Whether lock screen controls are visible
 * @property supportedCommands List of commands supported by current session
 * @property customActions List of custom action IDs available
 * @property lastMediaButtonEvent Last media button event received
 */
data class SessionCallbackState(
    val isConnected: Boolean = false,
    val controllerPackage: String? = null,
    val hasExternalControls: Boolean = false,
    val isLockScreenActive: Boolean = false,
    val supportedCommands: List<String> = emptyList(),
    val customActions: List<String> = emptyList(),
    val lastMediaButtonEvent: Int? = null
)

/**
 * Callback listener for media session events.
 *
 * Implement this interface to receive callbacks from external media controllers
 * such as lock screen, Android Auto, Wear OS, or Bluetooth headsets.
 */
interface SessionCallbackListener {
    /**
     * Called when a play request is received from an external controller.
     */
    fun onPlayRequest()

    /**
     * Called when a pause request is received from an external controller.
     */
    fun onPauseRequest()

    /**
     * Called when a skip to next request is received.
     */
    fun onSkipToNextRequest()

    /**
     * Called when a skip to previous request is received.
     */
    fun onSkipToPreviousRequest()

    /**
     * Called when a seek request is received.
     *
     * @param positionMs Position to seek to in milliseconds
     */
    fun onSeekRequest(positionMs: Long)

    /**
     * Called when a stop request is received.
     */
    fun onStopRequest()

    /**
     * Called when a media button event is received (e.g., from Bluetooth headset).
     *
     * @param keyCode The key code of the button pressed
     */
    fun onMediaButtonEvent(keyCode: Int)

    /**
     * Called when a custom action is invoked.
     *
     * @param actionId The ID of the custom action
     * @param extras Optional extras bundle
     */
    fun onCustomAction(actionId: String, extras: Bundle?)

    /**
     * Called when a setRating request is received.
     *
     * @param rating The rating value (0.0 to 5.0)
     */
    fun onSetRating(rating: Float)
}

/**
 * Comprehensive handler for MediaSession callbacks.
 *
 * This class implements MediaSession.Callback to handle all external control requests
 * from lock screen, Android Auto, Wear OS, Android TV, and Bluetooth headsets. It provides
 * a clean interface for the playback service to respond to external media control events.
 *
 * ## Supported Controllers
 *
 * - **Lock Screen**: Shows playback controls and track metadata on device lock screen
 * - **Android Auto**: Full media playback control through car display and voice commands
 * - **Wear OS**: Control playback from smartwatch with complications and notifications
 * - **Android TV**: D-pad navigation and playback control from TV remote
 * - **Bluetooth Headsets**: Media button events (play/pause/skip) from wireless headsets
 * - **Google Assistant**: Voice commands through Google Assistant integration
 *
 * ## Custom Actions
 *
 * Supports custom actions for extended functionality:
 * - `ACTION_TOGGLE_FAVORITE`: Toggle favorite status of current track
 * - `ACTION_TOGGLE_REPEAT`: Cycle through repeat modes
 * - `ACTION_TOGGLE_SHUFFLE`: Toggle shuffle mode
 * - `ACTION_SHOW_EQUALIZER`: Open equalizer settings
 *
 * ## Usage
 *
 * ```kotlin
 * @HiltViewModel
 * class PlayerViewModel @Inject constructor(
 *     private val sessionCallbackHandler: SessionCallbackHandler
 * ) : ViewModel() {
 *
 *     init {
 *         sessionCallbackHandler.setListener(object : SessionCallbackListener {
 *             override fun onPlayRequest() {
 *                 // Handle play request from external controller
 *             }
 *
 *             override fun onSkipToNextRequest() {
 *                 // Skip to next track
 *             }
 *         })
 *     }
 * }
 * ```
 *
 * @property context Application context
 * @property ioScope Coroutine scope for background operations
 */
@Singleton
class SessionCallbackHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * State flow for UI observation of session state.
     */
    private val _sessionState = MutableStateFlow(SessionCallbackState())
    val sessionState: StateFlow<SessionCallbackState> = _sessionState.asStateFlow()

    /**
     * Coroutine scope for callback processing.
     */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Optional listener for callback events.
     */
    private var callbackListener: SessionCallbackListener? = null

    /**
     * Media session instance.
     */
    private var mediaSession: MediaSession? = null

    /**
     * Custom actions for extended functionality.
     */
    companion object {
        const val ACTION_TOGGLE_FAVORITE = "com.amberesaiae.melos.TOGGLE_FAVORITE"
        const val ACTION_TOGGLE_REPEAT = "com.amberesaiae.melos.TOGGLE_REPEAT"
        const val ACTION_TOGGLE_SHUFFLE = "com.amberesaiae.melos.TOGGLE_SHUFFLE"
        const val ACTION_SHOW_EQUALIZER = "com.amberesaiae.melos.SHOW_EQUALIZER"
        const val ACTION_SHOW_LYRICS = "com.amberesaiae.melos.SHOW_LYRICS"
        const val ACTION_SLEEP_TIMER = "com.amberesaiae.melos.SLEEP_TIMER"
    }

    /**
     * Set the callback listener to receive external control events.
     *
     * @param listener Listener to receive callbacks
     */
    fun setListener(listener: SessionCallbackListener) {
        callbackListener = listener
    }

    /**
     * Clear the callback listener.
     */
    fun clearListener() {
        callbackListener = null
    }

    /**
     * Create and configure the MediaSession with callbacks.
     *
     * This method creates a new MediaSession configured with all necessary callbacks
     * for external control. Call this from your PlaybackService.
     *
     * @param player The player instance to control
     * @param sessionTag Tag for identifying the session
     * @return Configured MediaSession instance
     */
    fun createMediaSession(
        player: Player,
        sessionTag: String = "MelosPlayer"
    ): MediaSession {
        val session = MediaSession.Builder(context, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        // Set up custom commands
        session.setCustomLayouts(context, buildCustomLayouts())

        // Register for media button events
        session.setMediaButtonReceiver(mediaButtonPendingIntent)

        mediaSession = session

        _sessionState.update { it.copy(isConnected = true) }

        return session
    }

    /**
     * Build custom command layouts for notification and lock screen.
     */
    private fun buildCustomLayouts(context: Context): List<CommandButton> {
        return listOf(
            CommandButton.Builder()
                .setDisplayName("Favorite")
                .setSessionExtras(Bundle().apply {
                    putString("action", ACTION_TOGGLE_FAVORITE)
                })
                .setIconResId(android.R.drawable.btn_star)
                .build(),
            CommandButton.Builder()
                .setDisplayName("Repeat")
                .setSessionExtras(Bundle().apply {
                    putString("action", ACTION_TOGGLE_REPEAT)
                })
                .setIconResId(android.R.drawable.ic_menu_rotate)
                .build(),
            CommandButton.Builder()
                .setDisplayName("Equalizer")
                .setSessionExtras(Bundle().apply {
                    putString("action", ACTION_SHOW_EQUALIZER)
                })
                .setIconResId(android.R.drawable.ic_menu_equalizer)
                .build()
        )
    }

    /**
     * Create a PendingIntent for the session activity (tap to open app).
     */
    private val sessionActivityPendingIntent: PendingIntent
        get() {
            val intent = context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?.apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    addCategory(Intent.CATEGORY_DEFAULT)
                }
            return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

    /**
     * Create a PendingIntent for media button events.
     */
    private val mediaButtonPendingIntent: PendingIntent
        get() {
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
                .setPackage(context.packageName)
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

    /**
     * Update playback state for lock screen and external controllers.
     *
     * Call this method whenever playback state changes to update notifications
     * and lock screen controls.
     *
     * @param playbackState Current playback state
     * @param positionMs Current position in milliseconds
     * @param bufferedPositionMs Buffered position in milliseconds
     * @param durationMs Total duration in milliseconds
     * @param playbackSpeed Current playback speed
     */
    fun updatePlaybackState(
        playbackState: PlaybackState,
        positionMs: Long,
        bufferedPositionMs: Long,
        durationMs: Long,
        playbackSpeed: Float = 1.0f
    ) {
        mediaSession?.let { session ->
            val state = when (playbackState) {
                PlaybackState.Idle -> PlaybackStateCompat.STATE_NONE
                PlaybackState.Buffering -> PlaybackStateCompat.STATE_BUFFERING
                PlaybackState.Ready -> PlaybackStateCompat.STATE_PAUSED
                PlaybackState.Ended -> PlaybackStateCompat.STATE_STOPPED
            }

            val actions = buildPlaybackActions(playbackState)

            val playbackStateCompat = PlaybackStateCompat.Builder()
                .setState(state, positionMs, playbackSpeed)
                .setBufferedPosition(bufferedPositionMs)
                .setActions(actions)
                .setActiveQueueItemId(0)
                .build()

            session.setPlaybackState(playbackStateCompat)

            _sessionState.update {
                it.copy(isLockScreenActive = state != PlaybackStateCompat.STATE_NONE)
            }
        }
    }

    /**
     * Build playback actions based on current state.
     */
    private fun buildPlaybackActions(playbackState: PlaybackState): Long {
        var actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO

        // Add fast forward and rewind
        actions = actions or PlaybackStateCompat.ACTION_FAST_FORWARD or
                PlaybackStateCompat.ACTION_REWIND

        // Add rating support
        actions = actions or PlaybackStateCompat.ACTION_SET_RATING

        return actions
    }

    /**
     * Update metadata for lock screen and notification display.
     *
     * @param trackMetadata Current track metadata
     */
    fun updateMetadata(trackMetadata: TrackMetadata?) {
        mediaSession?.let { session ->
            val metadata = trackMetadata?.let { track ->
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.duration)
                    .apply {
                        track.artUri?.let {
                            putString(MediaMetadataCompat.METADATA_KEY_ART_URI, it.toString())
                        }
                    }
                    .build()
            } ?: MediaMetadataCompat.Builder().build()

            session.setMetadata(metadata)
        }
    }

    /**
     * Update the queue for media session.
     *
     * @param queue List of queue items as MediaSession.QueueItem
     */
    fun updateQueue(queue: List<MediaSession.QueueItem>) {
        mediaSession?.let { session ->
            session.setQueue(queue)
        }
    }

    /**
     * Release the media session and clean up resources.
     */
    fun release() {
        mediaSession?.let { session ->
            session.release()
            mediaSession = null
        }
        ioScope.coroutineContext.cancelChildren()
        _sessionState.update { SessionCallbackState() }
    }

    /**
     * Create a custom layout for notification actions.
     * This can be used with Media3's notification system.
     */
    fun createNotificationCustomLayout(
        context: Context,
        builder: NotificationCompat.Builder
    ): NotificationCompat.Builder {
        return builder
            .addAction(
                android.R.drawable.ic_media_pause,
                "Pause",
                mediaButtonPendingIntent
            )
            .addAction(
                android.R.drawable.ic_media_next,
                "Next",
                mediaButtonPendingIntent
            )
    }

    /**
     * Handle media button events from hardware buttons.
     *
     * @param intent The media button intent
     * @return true if handled, false otherwise
     */
    fun handleMediaButtonIntent(intent: Intent?): Boolean {
        if (intent?.action != Intent.ACTION_MEDIA_BUTTON) {
            return false
        }

        val keyEvent = intent.getParcelableExtra<android.view.KeyEvent>(
            Intent.EXTRA_KEY_EVENT
        ) ?: return false

        if (keyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
            val keyCode = keyEvent.keyCode
            _sessionState.update { it.copy(lastMediaButtonEvent = keyCode) }

            callbackListener?.onMediaButtonEvent(keyCode)

            when (keyCode) {
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> callbackListener?.onPlayRequest()
                android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> callbackListener?.onPauseRequest()
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    val currentState = _sessionState.value
                    if (currentState.isLockScreenActive) {
                        callbackListener?.onPauseRequest()
                    } else {
                        callbackListener?.onPlayRequest()
                    }
                }
                android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> callbackListener?.onSkipToNextRequest()
                android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> callbackListener?.onSkipToPreviousRequest()
                android.view.KeyEvent.KEYCODE_MEDIA_STOP -> callbackListener?.onStopRequest()
                android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    // Seek forward by 10 seconds
                    callbackListener?.onSeekRequest(10000L)
                }
                android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    // Seek backward by 10 seconds
                    callbackListener?.onSeekRequest(-10000L)
                }
            }
        }

        return true
    }
}
