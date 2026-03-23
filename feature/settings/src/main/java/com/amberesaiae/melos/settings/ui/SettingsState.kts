package com.amberesaiae.melos.settings.ui

import com.amberesaiae.melos.core.data.model.AudioQuality
import com.amberesaiae.melos.core.data.model.ScanInterval
import com.amberesaiae.melos.core.data.model.ThemeMode

/**
 * UI state for the Settings screen.
 */
data class SettingsState(
    // Playback settings
    val gaplessPlayback: Boolean = true,
    val crossfadeDuration: Int = 0,
    val skipSilence: Boolean = false,
    
    // Audio settings
    val replayGainEnabled: Boolean = false,
    val equalizerEnabled: Boolean = false,
    val audioQualityWifi: AudioQuality = AudioQuality.HIGH,
    val audioQualityMobile: AudioQuality = AudioQuality.MEDIUM,
    
    // Library settings
    val autoScanEnabled: Boolean = true,
    val scanInterval: ScanInterval = ScanInterval.DAILY,
    val excludedFolders: List<String> = emptyList(),
    
    // Appearance settings
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val amoledBlackEnabled: Boolean = false,
    val dynamicColorsEnabled: Boolean = false,
    
    // Storage settings
    val cacheLocation: String = "Internal storage",
    val cacheSizeBytes: Long = 0L,
    val cacheSizeFormatted: String = "0 MB",
    
    // App info
    val appVersion: String = "1.0.0",
    
    // UI state
    val isLoading: Boolean = false,
    val showCacheDialog: Boolean = false,
    val clearingCache: Boolean = false,
    val error: String? = null,
)

/**
 * Actions that can be performed on the Settings screen.
 */
sealed class SettingsAction {
    data class SetGaplessPlayback(val enabled: Boolean) : SettingsAction()
    data class SetCrossfadeDuration(val durationMs: Int) : SettingsAction()
    data class SetSkipSilence(val enabled: Boolean) : SettingsAction()
    
    data class SetReplayGainEnabled(val enabled: Boolean) : SettingsAction()
    data class SetEqualizerEnabled(val enabled: Boolean) : SettingsAction()
    data class SetAudioQualityWifi(val quality: AudioQuality) : SettingsAction()
    data class SetAudioQualityMobile(val quality: AudioQuality) : SettingsAction()
    
    data class SetAutoScanEnabled(val enabled: Boolean) : SettingsAction()
    data class SetScanInterval(val interval: ScanInterval) : SettingsAction()
    
    data class SetThemeMode(val mode: ThemeMode) : SettingsAction()
    data class SetAmoledBlackEnabled(val enabled: Boolean) : SettingsAction()
    data class SetDynamicColorsEnabled(val enabled: Boolean) : SettingsAction()
    
    object ShowCacheDialog : SettingsAction()
    object DismissCacheDialog : SettingsAction()
    object ClearCache : SettingsAction()
    object ChangeCacheLocation : SettingsAction()
    
    object NavigateToEqualizer : SettingsAction()
    object NavigateToExcludedFolders : SettingsAction()
    object NavigateToLicenses : SettingsAction()
    object NavigateToPrivacyPolicy : SettingsAction()
}
