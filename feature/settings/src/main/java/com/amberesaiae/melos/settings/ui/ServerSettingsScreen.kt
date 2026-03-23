@file:Suppress("ktlint:standard:max-line-length")

package com.amberesaiae.melos.settings.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Server
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amberesaiae.melos.settings.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.serverUiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()
    
    var showAddServerDialog by remember { mutableStateOf(false) }
    var editingServer by remember { mutableStateOf<ServerConfig?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        viewModel.snackbarFlow.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.server_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddServerDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_server)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                scope.launch {
                    viewModel.testAllConnections()
                    isRefreshing = false
                }
            },
            state = pullToRefreshState,
            modifier = Modifier.padding(paddingValues)
        ) {
            if (uiState.servers.isEmpty()) {
                EmptyServerListContent(
                    onAddServer = { showAddServerDialog = true }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = paddingValues = paddingValues + 16.dp
                ) {
                    itemsIndexed(
                        items = uiState.servers,
                        key = { _, server -> server.id }
                    ) { index, server ->
                        ServerItemCard(
                            server = server,
                            isActive = server.id == uiState.activeServerId,
                            connectionState = uiState.connectionStates[server.id] ?: ConnectionState.UNKNOWN,
                            priority = index,
                            onSelectServer = { viewModel.activateServer(server.id) },
                            onEditServer = { editingServer = server },
                            onDeleteServer = { viewModel.deleteServer(server.id) },
                            onTestConnection = { viewModel.testServerConnection(server.id) },
                            onMoveUp = { viewModel.moveServerUp(index) },
                            onMoveDown = { viewModel.moveServerDown(index) },
                            canMoveUp = index > 0,
                            canMoveDown = index < uiState.servers.size - 1
                        )
                    }
                }
            }
        }
    }
    
    if (showAddServerDialog) {
        AddServerDialog(
            onDismiss = { showAddServerDialog = false },
            onSave = { config ->
                viewModel.addServer(config)
                showAddServerDialog = false
            }
        )
    }
    
    if (editingServer != null) {
        AddServerDialog(
            initialServer = editingServer,
            onDismiss = { editingServer = null },
            onSave = { config ->
                viewModel.updateServer(editingServer!!.id, config)
                editingServer = null
            }
        )
    }
}

@Composable
private fun EmptyServerListContent(
    onAddServer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Server,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.no_servers_configured),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.add_server_to_get_started),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAddServer) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.add_server))
        }
    }
}

@Composable
private fun ServerItemCard(
    server: ServerConfig,
    isActive: Boolean,
    connectionState: ConnectionState,
    priority: Int,
    onSelectServer: () -> Unit,
    onEditServer: () -> Unit,
    onDeleteServer: () -> Unit,
    onTestConnection: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean
) {
    Card(
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isActive) 4.dp else 1.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) 
                else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = stringResource(R.string.move_up),
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = server.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isActive) 
                                MaterialTheme.colorScheme.onPrimaryContainer 
                                else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isActive) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.active_server),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = server.baseUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = server.serverType.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        ConnectionStatusIndicator(connectionState)
                    }
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Row {
                    IconButton(onClick = onTestConnection) {
                        Icon(
                            imageVector = if (connectionState == ConnectionState.CONNECTED) 
                                Icons.Default.CloudDone else Icons.Default.CloudOff,
                            contentDescription = stringResource(R.string.test_connection),
                            tint = when (connectionState) {
                                ConnectionState.CONNECTED -> Color(0xFF4CAF50)
                                ConnectionState.DISCONNECTED -> Color(0xFFF44336)
                                ConnectionState.CONNECTING -> Color(0xFFFF9800)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    
                    IconButton(onClick = onEditServer) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.edit_server)
                        )
                    }
                    
                    IconButton(onClick = onDeleteServer) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete_server),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            if (canMoveDown) {
                Divider(modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = onSelectServer,
                        enabled = !isActive
                    ) {
                        Text(
                            if (isActive) 
                                stringResource(R.string.current_server) 
                                else stringResource(R.string.select_server)
                        )
                    }
                    
                    IconButton(
                        onClick = onMoveDown,
                        enabled = canMoveDown
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = stringResource(R.string.move_down),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusIndicator(connectionState: ConnectionState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when (connectionState) {
            ConnectionState.CONNECTED -> {
                Text(
                    text = stringResource(R.string.connected),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4CAF50)
                )
            }
            ConnectionState.DISCONNECTED -> {
                Text(
                    text = stringResource(R.string.disconnected),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFF44336)
                )
            }
            ConnectionState.CONNECTING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFFFF9800)
                )
                Text(
                    text = stringResource(R.string.testing),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFF9800)
                )
            }
            ConnectionState.UNKNOWN -> {
                Text(
                    text = stringResource(R.string.not_tested),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

sealed class ServerConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val serverType: ServerType,
    val username: String,
    val priority: Int,
    val isActive: Boolean
) {
    data class Subsonic(
        override val id: String,
        override val name: String,
        override val baseUrl: String,
        override val username: String,
        override val priority: Int = 0,
        override val isActive: Boolean = false
    ) : ServerConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        serverType = ServerType.SUBSONIC,
        username = username,
        priority = priority,
        isActive = isActive
    )
    
    data class Jellyfin(
        override val id: String,
        override val name: String,
        override val baseUrl: String,
        override val username: String,
        override val priority: Int = 0,
        override val isActive: Boolean = false
    ) : ServerConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        serverType = ServerType.JELLYFIN,
        username = username,
        priority = priority,
        isActive = isActive
    )
}

enum class ServerType(@StringRes val displayNameRes: Int) {
    SUBSONIC(R.string.subsonic),
    JELLYFIN(R.string.jellyfin);
    
    val displayName: String
        @Composable get() = stringResource(displayNameRes)
}

enum class ConnectionState {
    CONNECTED,
    DISCONNECTED,
    CONNECTING,
    UNKNOWN
}
