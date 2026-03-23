package com.amberesaiae.melos.settings.ui

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amberesaiae.melos.core.data.model.AudioQuality
import com.amberesaiae.melos.core.data.model.ScanInterval
import com.amberesaiae.melos.core.data.model.ThemeMode
import com.amberesaiae.melos.core.datastore.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState(isLoading = true))
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadSettings()
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.SetGaplessPlayback -> setGaplessPlayback(action.enabled)
            is SettingsAction.SetCrossfadeDuration -> setCrossfadeDuration(action.durationMs)
            is SettingsAction.SetSkipSilence -> setSkipSilence(action.enabled)
            is SettingsAction.SetReplayGainEnabled -> setReplayGainEnabled(action.enabled)
            is SettingsAction.SetEqualizerEnabled -> setEqualizerEnabled(action.enabled)
            is SettingsAction.SetAudioQualityWifi -> setAudioQualityWifi(action.quality)
            is SettingsAction.SetAudioQualityMobile -> setAudioQualityMobile(action.quality)
            is SettingsAction.SetAutoScanEnabled -> setAutoScanEnabled(action.enabled)
            is SettingsAction.SetScanInterval -> setScanInterval(action.interval)
            is SettingsAction.SetThemeMode -> setThemeMode(action.mode)
            is SettingsAction.SetAmoledBlackEnabled -> setAmoledBlackEnabled(action.enabled)
            is SettingsAction.SetDynamicColorsEnabled -> setDynamicColorsEnabled(action.enabled)
            is SettingsAction.ShowCacheDialog -> showCacheDialog()
            is SettingsAction.DismissCacheDialog -> dismissCacheDialog()
            is SettingsAction.ClearCache -> clearCache()
            is SettingsAction.ChangeCacheLocation -> changeCacheLocation()
            is SettingsAction.NavigateToEqualizer -> navigateToEqualizer()
            is SettingsAction.NavigateToExcludedFolders -> navigateToExcludedFolders()
            is SettingsAction.NavigateToLicenses -> navigateToLicenses()
            is SettingsAction.NavigateToPrivacyPolicy -> navigateToPrivacyPolicy()
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            settingsDataStore.settingsFlow
                .catch { e ->
                    _state.update { it.copy(isLoading = false, error = "Failed to load settings") }
                }
                .collect { preferences ->
                    val cacheSize = calculateCacheSize()
                    _state.update {
                        SettingsState(
                            isLoading = false,
                            gaplessPlayback = preferences[PreferencesKeys.GAPLESS_PLAYBACK] ?: true,
                            crossfadeDuration = preferences[PreferencesKeys.CROSSFADE_DURATION] ?: 0,
                            skipSilence = preferences[PreferencesKeys.SKIP_SILENCE] ?: false,
                            replayGainEnabled = preferences[PreferencesKeys.REPLAY_GAIN] ?: false,
                            equalizerEnabled = preferences[PreferencesKeys.EQUALIZER_ENABLED] ?: false,
                            audioQualityWifi = AudioQuality.valueOf(
                                preferences[PreferencesKeys.AUDIO_QUALITY_WIFI] ?: AudioQuality.HIGH.name
                            ),
                            audioQualityMobile = AudioQuality.valueOf(
                                preferences[PreferencesKeys.AUDIO_QUALITY_MOBILE] ?: AudioQuality.MEDIUM.name
                            ),
                            autoScanEnabled = preferences[PreferencesKeys.AUTO_SCAN] ?: true,
                            scanInterval = ScanInterval.valueOf(
                                preferences[PreferencesKeys.SCAN_INTERVAL] ?: ScanInterval.DAILY.name
                            ),
                            themeMode = ThemeMode.valueOf(
                                preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name
                            ),
                            amoledBlackEnabled = preferences[PreferencesKeys.AMOLED_BLACK] ?: false,
                            dynamicColorsEnabled = preferences[PreferencesKeys.DYNAMIC_COLORS] ?: false,
                            cacheSizeBytes = cacheSize,
                            cacheSizeFormatted = formatFileSize(cacheSize),
                        )
                    }
                }
        }
    }

    private fun calculateCacheSize(): Long {
        return try {
            calculateDirSize(context.cacheDir)
        } catch (e: Exception) {
            0L
        }
    }

    private fun calculateDirSize(dir: File): Long {
        var size = 0L
        try {
            dir.listFiles()?.forEach { file ->
                size += if (file.isDirectory) calculateDirSize(file) else file.length()
            }
        } catch (e: Exception) { }
        return size
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    private fun setGaplessPlayback(enabled: Boolean) = saveSetting(PreferencesKeys.GAPLESS_PLAYBACK, enabled)
    private fun setCrossfadeDuration(durationMs: Int) = saveSetting(PreferencesKeys.CROSSFADE_DURATION, durationMs)
    private fun setSkipSilence(enabled: Boolean) = saveSetting(PreferencesKeys.SKIP_SILENCE, enabled)
    private fun setReplayGainEnabled(enabled: Boolean) = saveSetting(PreferencesKeys.REPLAY_GAIN, enabled)
    private fun setEqualizerEnabled(enabled: Boolean) = saveSetting(PreferencesKeys.EQUALIZER_ENABLED, enabled)
    private fun setAudioQualityWifi(quality: AudioQuality) = saveSetting(PreferencesKeys.AUDIO_QUALITY_WIFI, quality.name)
    private fun setAudioQualityMobile(quality: AudioQuality) = saveSetting(PreferencesKeys.AUDIO_QUALITY_MOBILE, quality.name)
    private fun setAutoScanEnabled(enabled: Boolean) = saveSetting(PreferencesKeys.AUTO_SCAN, enabled)
    private fun setScanInterval(interval: ScanInterval) = saveSetting(PreferencesKeys.SCAN_INTERVAL, interval.name)
    private fun setThemeMode(mode: ThemeMode) = saveSetting(PreferencesKeys.THEME_MODE, mode.name)
    private fun setAmoledBlackEnabled(enabled: Boolean) = saveSetting(PreferencesKeys.AMOLED_BLACK, enabled)
    private fun setDynamicColorsEnabled(enabled: Boolean) = saveSetting(PreferencesKeys.DYNAMIC_COLORS, enabled)

    private fun saveSetting(key: Preferences.Key<Boolean>, value: Boolean) {
        viewModelScope.launch {
            settingsDataStore.dataStore.edit { it[key] = value }
        }
    }

    private fun saveSetting(key: Preferences.Key<Int>, value: Int) {
        viewModelScope.launch {
            settingsDataStore.dataStore.edit { it[key] = value }
        }
    }

    private fun saveSetting(key: Preferences.Key<String>, value: String) {
        viewModelScope.launch {
            settingsDataStore.dataStore.edit { it[key] = value }
        }
    }

    private fun showCacheDialog() { _state.update { it.copy(showCacheDialog = true) } }
    private fun dismissCacheDialog() { _state.update { it.copy(showCacheDialog = false) } }

    private fun clearCache() {
        _state.update { it.copy(showCacheDialog = false, clearingCache = true) }
        viewModelScope.launch {
            try {
                context.cacheDir.deleteRecursively()
                val newSize = calculateCacheSize()
                _state.update { it.copy(clearingCache = false, cacheSizeBytes = newSize, cacheSizeFormatted = formatFileSize(newSize)) }
            } catch (e: Exception) {
                _state.update { it.copy(clearingCache = false, error = "Failed to clear cache") }
            }
        }
    }

    private fun changeCacheLocation() { /* TODO: Implement cache location picker */ }
    private fun navigateToEqualizer() { /* TODO: Navigate to equalizer screen */ }
    private fun navigateToExcludedFolders() { /* TODO: Navigate to excluded folders screen */ }
    private fun navigateToLicenses() { /* TODO: Navigate to licenses screen */ }
    private fun navigateToPrivacyPolicy() { /* TODO: Navigate to privacy policy screen */ }
}

object PreferencesKeys {
    val GAPLESS_PLAYBACK = booleanPreferencesKey("gapless_playback")
    val CROSSFADE_DURATION = intPreferencesKey("crossfade_duration")
    val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
    val REPLAY_GAIN = booleanPreferencesKey("replay_gain")
    val EQUALIZER_ENABLED = booleanPreferencesKey("equalizer_enabled")
    val AUDIO_QUALITY_WIFI = stringPreferencesKey("audio_quality_wifi")
    val AUDIO_QUALITY_MOBILE = stringPreferencesKey("audio_quality_mobile")
    val AUTO_SCAN = booleanPreferencesKey("auto_scan")
    val SCAN_INTERVAL = stringPreferencesKey("scan_interval")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val AMOLED_BLACK = booleanPreferencesKey("amoled_black")
    val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
}
