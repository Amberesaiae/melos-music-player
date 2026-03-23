@file:Suppress("ktlint:standard:max-line-length")

package com.amberesaiae.melos.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.amberesaiae.melos.settings.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToServerSettings: () -> Unit,
    onNavigateToAudioSettings: () -> Unit,
    onNavigateToCacheSettings: () -> Unit,
    onNavigateToAppearanceSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSection(
                title = stringResource(R.string.servers),
                items = listOf(
                    SettingsItem(
                        title = stringResource(R.string.server_settings),
                        subtitle = stringResource(R.string.manage_music_servers),
                        icon = Icons.Default.Cloud,
                        onClick = onNavigateToServerSettings
                    )
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            SettingsSection(
                title = stringResource(R.string.playback),
                items = listOf(
                    SettingsItem(
                        title = stringResource(R.string.audio_settings),
                        subtitle = stringResource(R.string.equalizer_audio_effects),
                        icon = Icons.Default.MusicNote,
                        onClick = onNavigateToAudioSettings
                    )
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            SettingsSection(
                title = stringResource(R.string.storage),
                items = listOf(
                    SettingsItem(
                        title = stringResource(R.string.cache_settings),
                        subtitle = stringResource(R.string.offline_cache_management),
                        icon = Icons.Default.Storage,
                        onClick = onNavigateToCacheSettings
                    )
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            SettingsSection(
                title = stringResource(R.string.appearance),
                items = listOf(
                    SettingsItem(
                        title = stringResource(R.string.appearance_settings),
                        subtitle = stringResource(R.string.themes_colors_display),
                        icon = Icons.Default.Palette,
                        onClick = onNavigateToAppearanceSettings
                    )
                )
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.height(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    items: List<SettingsItem>
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Column {
                items.forEachIndexed { index, item ->
                    ListItem(
                        leadingContent = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        headlineContent = { Text(item.title) },
                        supportingContent = { Text(item.subtitle) },
                        onClick = item.onClick,
                        colors = ListItemDefaults.colors(
                            containerColor = if (index == 0 && items.size > 1) 
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.0f)
                        )
                    )
                    
                    if (index < items.size - 1) {
                        androidx.compose.material3.Divider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                        )
                    }
                }
            }
        }
    }
}

data class SettingsItem(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: () -> Unit
)
