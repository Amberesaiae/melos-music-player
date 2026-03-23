@file:Suppress("kotlin:S6290")

package com.amberesaiae.melos.core.player

import android.content.Context
import android.media.AudioAttributes
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_BUFFERING
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Player.STATE_READY
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import com.amberesaiae.melos.core.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MelosPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectivityManager: ConnectivityManager,
    private val remoteMediaSource: RemoteMediaSource
) {
    companion object {
        private const val TAG = "MelosPlayer"
        private const val PREBUFFER_DELAY_MS = 3000L
        private const val QUALITY_CHECK_INTERVAL_MS = 10000L
    }

    private val player: ExoPlayer
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    private val _playbackState = MutableStateFlow(PlayerState())
    val playbackState: StateFlow<PlayerState> = _playbackState.asStateFlow()

    private val _networkState = MutableStateFlow(NetworkState.UNKNOWN)
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    private val _currentQuality = MutableStateFlow(StreamingQuality.ORIGINAL)
    val currentQuality: StateFlow<StreamingQuality> = _currentQuality.asStateFlow()

    private var nextSongToPrebuffer: Song? = null
    private var prebufferJob: Job? = null
    private var networkQualityJob: Job? = null

    init {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(5000, 60000, 15000, 15000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        player = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setAudioAttributes(getAudioAttributes(), true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        setupPlayerListener()
        observeNetworkState()
    }

    private fun getAudioAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
    }

    private fun setupPlayerListener() {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                _playbackState.value = _playbackState.value.copy(
                    playerState = state,
                    isBuffering = state == STATE_BUFFERING,
                    isLoading = state == STATE_BUFFERING || state == STATE_IDLE
                )

                when (state) {
                    STATE_BUFFERING -> handleBuffering()
                    STATE_READY -> {}
                    STATE_ENDED -> handlePlaybackEnded()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
            }

            override fun onIsLoadingChanged(isLoading: Boolean) {
                _playbackState.value = _playbackState.value.copy(isLoading = isLoading)
            }

            override fun onPlayerError(error: PlaybackException) {
                when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                        handleNetworkError()
                    }
                    else -> handlePlaybackError(error)
                }
            }
        })
    }

    private fun observeNetworkState() {
        networkQualityJob = scope.launch {
            updateNetworkState()
        }
    }

    private fun updateNetworkState() {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

        val isOnline = capabilities != null &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isMetered = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false
        val bandwidthEstimate = capabilities?.linkDownstreamBandwidthKbps?.toLong() ?: 0

        val networkQuality = when {
            !isOnline -> NetworkQuality.OFFLINE
            isWifi -> NetworkQuality.WIFI
            bandwidthEstimate > 5000 -> NetworkQuality.CELLULAR_GOOD
            else -> NetworkQuality.CELLULAR_POOR
        }

        val newState = NetworkState(
            isOnline = isOnline,
            isWifi = isWifi,
            networkQuality = networkQuality,
            isMetered = isMetered,
            bandwidthEstimate = bandwidthEstimate * 1000
        )

        if (newState != _networkState.value) {
            _networkState.value = newState
            onNetworkQualityChanged(networkQuality)
        }
    }

    private fun onNetworkQualityChanged(quality: NetworkQuality) {
        val newStreamingQuality = StreamingQuality.forNetworkQuality(quality)
        if (newStreamingQuality != _currentQuality.value) {
            _currentQuality.value = newStreamingQuality
        }
    }

    suspend fun playRemoteSong(song: Song, serverType: String, accessToken: String? = null) {
        try {
            val mediaItem = when (serverType) {
                "subsonic" -> remoteMediaSource.createSubsonicMediaItem(song.id, song)
                "jellyfin" -> remoteMediaSource.createJellyfinMediaItem(song.id, song, accessToken ?: "")
                else -> throw IllegalArgumentException("Unknown server type: $serverType")
            }

            mediaItem?.let {
                player.setMediaItem(it)
                player.prepare()
                player.play()
            }
        } catch (e: Exception) {
            handlePlaybackError(PlaybackException(e.message ?: "Unknown error", e))
        }
    }

    fun setNextTrack(song: Song, serverType: String, accessToken: String? = null) {
        nextSongToPrebuffer = song
        schedulePrebuffer(serverType, accessToken)
    }

    private fun schedulePrebuffer(serverType: String, accessToken: String?) {
        prebufferJob?.cancel()
        prebufferJob = scope.launch {
            delay(PREBUFFER_DELAY_MS)
            nextSongToPrebuffer?.let { song ->
                try {
                    val mediaItem = when (serverType) {
                        "subsonic" -> remoteMediaSource.createSubsonicMediaItem(song.id, song)
                        "jellyfin" -> remoteMediaSource.createJellyfinMediaItem(song.id, song, accessToken ?: "")
                        else -> null
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun handleBuffering() {
        if (player.bufferedPercentage < 10) {
            handler.postDelayed({
                if (player.bufferedPercentage < 10 && player.isPlaying) {
                    _playbackState.value = _playbackState.value.copy(
                        bufferingPercentage = player.bufferedPercentage.toLong(),
                        isStalled = true
                    )
                }
            }, 3000)
        }
    }

    private fun handlePlaybackEnded() {
        prebufferJob?.cancel()
    }

    private fun handleNetworkError() {
        scope.launch {
            delay(2000)
            if (!_networkState.value.isOnline) {
                player.pause()
                _playbackState.value = _playbackState.value.copy(
                    error = PlaybackException("Network unavailable", PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
                )
            } else {
                player.prepare()
            }
        }
    }

    private fun handlePlaybackError(error: PlaybackException) {
        _playbackState.value = _playbackState.value.copy(error = error)
    }

    fun getCurrentLoadControl(): LoadControl {
        val networkQuality = _networkState.value.networkQuality
        val bufferConfig = remoteMediaSource.getNetworkAwareBufferConfig(networkQuality)

        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                bufferConfig.minBufferMs.toInt(),
                bufferConfig.maxBufferMs.toInt(),
                bufferConfig.streamBufferMs.toInt(),
                bufferConfig.prefetchBufferMs.toInt()
            )
            .build()
    }

    fun play() = player.play()
    fun pause() = player.pause()
    fun seekTo(position: Long) = player.seekTo(position)
    fun stop() = player.stop()
    fun release() {
        scope.cancel()
        prebufferJob?.cancel()
        networkQualityJob?.cancel()
        handler.removeCallbacksAndMessages(null)
        player.release()
    }

    val isPlaying: Boolean get() = player.isPlaying
    val currentPosition: Long get() = player.currentPosition
    val duration: Long get() = player.duration
    val bufferedPercentage: Int get() = player.bufferedPercentage
}

data class PlayerState(
    val playerState: Int = STATE_IDLE,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isLoading: Boolean = false,
    val isStalled: Boolean = false,
    val bufferingPercentage: Long = 0,
    val error: PlaybackException? = null
)
