@file:Suppress("kotlin:S6290")

package com.amberesaiae.melos.core.player

/**
 * Network quality levels for adaptive streaming.
 */
enum class NetworkQuality {
    WIFI,
    CELLULAR_GOOD,
    CELLULAR_POOR,
    OFFLINE
}

/**
 * Represents the current network state for playback decisions.
 */
data class NetworkState(
    val isOnline: Boolean,
    val isWifi: Boolean,
    val networkQuality: NetworkQuality,
    val isMetered: Boolean,
    val bandwidthEstimate: Long // bits per second
) {
    companion object {
        val UNKNOWN = NetworkState(
            isOnline = false,
            isWifi = false,
            networkQuality = NetworkQuality.OFFLINE,
            isMetered = true,
            bandwidthEstimate = 0
        )
    }
}

/**
 * Streaming quality presets.
 */
enum class StreamingQuality(val bitrate: Int, val description: String) {
    LOW(96, "Low (96 kbps)"),
    MEDIUM(128, "Medium (128 kbps)"),
    HIGH(192, "High (192 kbps)"),
    ORIGINAL(-1, "Original");

    companion object {
        fun forNetworkQuality(quality: NetworkQuality): StreamingQuality {
            return when (quality) {
                NetworkQuality.WIFI -> ORIGINAL
                NetworkQuality.CELLULAR_GOOD -> HIGH
                NetworkQuality.CELLULAR_POOR -> MEDIUM
                NetworkQuality.OFFLINE -> LOW
            }
        }
    }
}
