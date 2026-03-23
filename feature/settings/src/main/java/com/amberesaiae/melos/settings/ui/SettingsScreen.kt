package com.amberesaiae.melos.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.ClearCache
import androidx.compose.material.icons.filled.Eq
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amberesaiae.melos.core.data.model.AudioQuality
import com.amberesaiae.melos.core.data.model.ScanInterval
import com.amberesaiae.melos.core.data.model.ThemeMode
import com.amberesaiae.melos.settings.ui.components.CacheSizeDialog
import com.amberesaiae.melos.settings.ui.components.SettingsCategory
import com.amberesaiae.melos.settings.ui.components.SettingsItem
import com.amberesaiae.melos.settings.ui.components.SliderSettingsItem
import com.amberesaiae.melos.settings.ui.components.SwitchSettingsItem
import com.amberesaiae.melos.settings.ui.components.DropdownSettingsItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onNavigateBack) {
                        androidx.compose.material3.Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors()
            )
        }
    ) { paddingValues ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Playback Settings
                item {
                    SettingsCategory(
                        title = "Playback",
                        icon = Icons.Default.PlayArrow
                    )
                }
                item {
                    SwitchSettingsItem(
                        title = "Gapless playback",
                        subtitle = "Seamless transitions between tracks",
                        checked = state.gaplessPlayback,
                        onCheckedChange = { viewModel.onAction(SettingsAction.SetGaplessPlayback(it)) }
                    )
                }
                item {
                    SliderSettingsItem(
                        title = "Crossfade duration",
                        subtitle = if (state.crossfadeDuration == 0) "Disabled" else "${state.crossfadeDuration / 1000}s",
                        value = state.crossfadeDuration.toFloat(),
                        valueRange = 0f..10000f,
                        steps = 19,
                        onValueChange = { viewModel.onAction(SettingsAction.SetCrossfadeDuration(it.toInt())) }
                    )
                }
                item {
                    SwitchSettingsItem(
                        title = "Skip silence",
                        subtitle = "Automatically skip silent parts",
                        checked = state.skipSilence,
                        onCheckedChange = { viewModel.onAction(SettingsAction.SetSkipSilence(it)) }
                    )
                }

                // Audio Settings
                item {
                    SettingsCategory(
                        title = "Audio",
                        icon = Icons.Default.Eq
                    )
                }
                item {
                    SwitchSettingsItem(
                        title = "ReplayGain",
                        subtitle = "Normalize volume across tracks",
                        checked = state.replayGainEnabled,
                        onCheckedChange = { viewModel.onAction(SettingsAction.SetReplayGainEnabled(it)) }
                    )
                }
                item {
                    SettingsItem(
                        title = "Equalizer",
                        subtitle = "Adjust audio frequencies",
                        icon = Icons.Default.Eq,
                        onClick = { viewModel.onAction(SettingsAction.NavigateToEqualizer) }
                    )
                }
                item {
                    DropdownSettingsItem(
                        title = "Audio quality (Wi-Fi)",
                        selectedValue = state.audioQualityWifi.displayName,
                        options = AudioQuality.entries.map { it.displayName },
                        onOptionSelected = { selected ->
                            AudioQuality.entries.find { it.displayName == selected }?.let {
                                viewModel.onAction(SettingsAction.SetAudioQualityWifi(it))
                            }
                        }
                    )
                }
                item {
                    DropdownSettingsItem(
                        title = "Audio quality (Mobile)",
                        selectedValue = state.audioQualityMobile.displayName,
                        options = AudioQuality.entries.map { it.displayName },
                        onOptionSelected = { selected ->
                            AudioQuality.entries.find { it.displayName == selected }?.let {
                                viewModel.onAction(SettingsAction.SetAudioQualityMobile(it))
                            }
                        }
                    )
                }

                // Library Settings
                item {
                    SettingsCategory(
                        title = "Library",
                        icon = Icons.Default.LibraryMusic
                    )
                }
                item {
                    SwitchSettingsItem(
                        title = "Auto-scan",
                        subtitle = "Automatically scan for new music",
                        checked = state.autoScanEnabled,
                        onCheckedChange = { viewModel.onAction(SettingsAction.SetAutoScanEnabled(it)) }
                    )
                }
                item {
                    DropdownSettingsItem(
                        title = "Scan interval",
                        selectedValue = state.scanInterval.displayName,
                        options = ScanInterval.entries.map { it.displayName },
                        onOptionSelected = { selected ->
                            ScanInterval.entries.find { it.displayName == selected }?.let {
                                viewModel.onAction(SettingsAction.SetScanInterval(it))
                            }
                        }
                    )
                }
                item {
                    SettingsItem(
                        title = "Excluded folders",
                        subtitle = "Manage folders to ignore during scan",
                        icon = Icons.Default.Folder,
                        onClick = { viewModel.onAction(SettingsAction.NavigateToExcludedFolders) }
                    )
                }

                // Appearance Settings
                item {
                    SettingsCategory(
                        title = "Appearance",
                        icon = Icons.Default.Palette
                    )
                }
                item {
                    DropdownSettingsItem(
                        title = "Theme",
                        selectedValue = state.themeMode.displayName,
                        options = ThemeMode.entries.map { it.displayName },
                        onOptionSelected = { selected ->
                            ThemeMode.entries.find { it.displayName == selected }?.let {
                                viewModel.onAction(SettingsAction.SetThemeMode(it))
                            }
                        }
                    )
                }
                item {
                    SwitchSettingsItem(
                        title = "AMOLED black",
                        subtitle = "Use pure black for dark theme",
                        checked = state.amoledBlackEnabled,
                        onCheckedChange = { viewModel.onAction(SettingsAction.SetAmoledBlackEnabled(it)) }
                    )
                }
                item {
                    SwitchSettingsItem(
                        title = "Dynamic colors",
                        subtitle = "Use Material You theming",
                        checked = state.dynamicColorsEnabled,
                        onCheckedChange = { viewModel.onAction(SettingsAction.SetDynamicColorsEnabled(it)) }
                    )
                }

                // Storage Settings
                item {
                    SettingsCategory(
                        title = "Storage",
                        icon = Icons.Default.Storage
                    )
                }
                item {
                    SettingsItem(
                        title = "Cache location",
                        subtitle = state.cacheLocation,
                        icon = Icons.Default.Folder,
                        onClick = { viewModel.onAction(SettingsAction.ChangeCacheLocation) }
                    )
                }
                item {
                    SettingsItem(
                        title = "Cache size",
                        subtitle = state.cacheSizeFormatted,
                        icon = Icons.Default.ClearCache,
                        onClick = { viewModel.onAction(SettingsAction.ShowCacheDialog) }
                    )
                }

                // About Section
                item {
                    SettingsCategory(
                        title = "About",
                        icon = Icons.Default.Info
                    )
                }
                item {
                    SettingsItem(
                        title = "Version",
                        subtitle = state.appVersion,
                        icon = Icons.Default.Info,
                        onClick = { }
                    )
                }
                item {
                    SettingsItem(
                        title = "Open source licenses",
                        subtitle = "View licenses",
                        icon = Icons.AutoMirrored.Filled.Article,
                        onClick = { viewModel.onAction(SettingsAction.NavigateToLicenses) }
                    )
                }
                item {
                    SettingsItem(
                        title = "Privacy policy",
                        subtitle = "Read our privacy policy",
                        icon = Icons.Default.Shield,
                        onClick = { viewModel.onAction(SettingsAction.NavigateToPrivacyPolicy) }
                    )
                }
                
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    if (state.showCacheDialog) {
        CacheSizeDialog(
            cacheSize = state.cacheSizeFormatted,
            onDismiss = { viewModel.onAction(SettingsAction.DismissCacheDialog) },
            onConfirm = { viewModel.onAction(SettingsAction.ClearCache) },
            isClearing = state.clearingCache
        )
    }
}

@PreviewLightDark
@Composable
private fun SettingsScreenPreview() {
    Surface {
        SettingsScreen(onNavigateBack = { })
    }
}
