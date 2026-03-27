/*
 * Copyright (c) 2024 Amberesaiae. All rights reserved.
 * SPDX-License-Identifier: MIT
 */
package com.amberesaiae.melos.equalizer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FlatRoute
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.amberesaiae.melos.equalizer.ui.components.FrequencyBand
import com.amberesaiae.melos.equalizer.ui.components.PresetSelector

/**
 * Main Equalizer screen composable.
 * 
 * Displays 10 vertical sliders for frequency bands with preset controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    viewModel: EqualizerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Show error snackbar when error message appears
    state.errorMessage?.let { error ->
        LaunchedEffect(error) {
            snackbarHostState.showSnackbar(error)
            viewModel.onAction(EqualizerAction.DismissError)
        }
    }
    
    Scaffold(
        topBar = {
            EqualizerTopBar(
                isEnabled = state.isEnabled,
                onToggleEnabled = { viewModel.onAction(EqualizerAction.ToggleEnabled) },
                onNavigateBack = onNavigateBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Loading state
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Loading equalizer...")
                    }
                }
                return@Column
            }
            
            // Unsupported state
            if (!state.isSupported) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.width(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Equalizer not supported",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "This device doesn't support hardware equalizer",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                return@Column
            }
            
            // Preset selector row
            EqualizerControlsRow(
                currentPreset = state.currentPreset,
                availablePresets = state.availablePresets,
                onLoadPreset = { viewModel.onAction(EqualizerAction.LoadPreset(it)) },
                onResetToFlat = { viewModel.onAction(EqualizerAction.ResetToFlat) },
                onSavePreset = { viewModel.onAction(EqualizerAction.ShowSavePresetDialog) }
            )
            
            // Frequency bands
            if (state.bandLevels.isNotEmpty()) {
                FrequencyBandsRow(
                    bandLevels = state.bandLevels,
                    onBandLevelChanged = { index, level ->
                        viewModel.onAction(EqualizerAction.SetBandLevel(index, level))
                    }
                )
            }
        }
    }
    
    // Save preset dialog
    if (state.showSavePresetDialog) {
        SavePresetDialog(
            onDismissRequest = { viewModel.onAction(EqualizerAction.HideSavePresetDialog) },
            onSave = { name ->
                viewModel.onAction(EqualizerAction.SaveCustomPreset(name))
            }
        )
    }
}

/**
 * Top app bar with title and enable/disable toggle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EqualizerTopBar(
    isEnabled: Boolean,
    onToggleEnabled: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val animatedColor by animateColorAsState(
        targetValue = if (isEnabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        label = "topBarColor"
    )
    
    TopAppBar(
        title = { Text("Equalizer") },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Outlined.Equalizer,
                    contentDescription = "Back"
                )
            }
        },
        actions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isEnabled) "On" else "Off",
                    style = MaterialTheme.typography.bodySmall
                )
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { onToggleEnabled() }
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            titleContentColor = animatedColor
        )
    )
}

/**
 * Controls row with preset selector and action buttons.
 */
@Composable
private fun EqualizerControlsRow(
    currentPreset: String,
    availablePresets: List<String>,
    onLoadPreset: (String) -> Unit,
    onResetToFlat: () -> Unit,
    onSavePreset: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Preset selector
        PresetSelector(
            currentPreset = currentPreset,
            availablePresets = availablePresets,
            onPresetSelected = onLoadPreset,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChipButton(
                icon = Icons.Default.SettingsBackupRestore,
                label = "Reset",
                onClick = onResetToFlat,
                modifier = Modifier.weight(1f)
            )
            
            FilterChipButton(
                icon = Icons.Default.Save,
                label = "Save",
                onClick = onSavePreset,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Chip button with icon and label.
 */
@Composable
private fun FilterChipButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = false,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.width(18.dp)
            )
        },
        modifier = modifier.height(40.dp)
    )
}

/**
 * Row of frequency band sliders.
 */
@Composable
private fun FrequencyBandsRow(
    bandLevels: List<Int>,
    onBandLevelChanged: (Int, Int) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(bandLevels.size) { index ->
            FrequencyBand(
                frequency = FrequencyBandUi.FREQUENCIES[index],
                level = bandLevels[index],
                onLevelChanged = { newLevel ->
                    onBandLevelChanged(index, newLevel)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Dialog for saving a custom preset.
 */
@Composable
private fun SavePresetDialog(
    onDismissRequest: () -> Unit,
    onSave: (String) -> Unit
) {
    var presetName by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                imageVector = Icons.Default.Save,
                contentDescription = null
            )
        },
        title = { Text("Save Preset") },
        text = {
            Column {
                Text("Enter a name for your custom preset:")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text("Preset name") },
                    placeholder = { Text("My Preset") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(presetName.trim()) },
                enabled = presetName.trim().isNotEmpty()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismissRequest,
                colors = ButtonDefaults.outlinedButtonColors()
            ) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Preview composable.
 */
@Preview
@Composable
private fun EqualizerScreenPreview() {
    Surface {
        EqualizerScreen()
    }
}
