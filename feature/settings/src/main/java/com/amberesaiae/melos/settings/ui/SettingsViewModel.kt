@file:Suppress("ktlint:standard:max-line-length")

package com.amberesaiae.melos.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amberesaiae.melos.settings.data.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServerUiState(
    val servers: List<ServerConfig> = emptyList(),
    val activeServerId: String? = null,
    val connectionStates: Map<String, ConnectionState> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val serverRepository: ServerRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ServerUiState())
    val serverUiState: StateFlow<ServerUiState> = _uiState.asStateFlow()
    
    private val _snackbarFlow = MutableStateFlow<String?>(null)
    val snackbarFlow = _snackbarFlow.asStateFlow()
    
    init {
        loadServers()
    }
    
    private fun loadServers() {
        viewModelScope.launch {
            combine(
                serverRepository.serversFlow,
                serverRepository.activeServerIdFlow
            ) { servers, activeId ->
                servers to activeId
            }.collect { (servers, activeId) ->
                _uiState.update { currentState ->
                    currentState.copy(
                        servers = servers,
                        activeServerId = activeId
                    )
                }
            }
        }
    }
    
    fun addServer(server: ServerConfig) {
        viewModelScope.launch {
            try {
                serverRepository.addServer(server)
                _snackbarFlow.value = "Server \"${server.name}\" added successfully"
                
                if (_uiState.value.servers.isEmpty()) {
                    serverRepository.activateServer(server.id)
                }
            } catch (e: Exception) {
                _snackbarFlow.value = "Failed to add server: ${e.message}"
            }
        }
    }
    
    fun updateServer(serverId: String, server: ServerConfig) {
        viewModelScope.launch {
            try {
                serverRepository.updateServer(server)
                _snackbarFlow.value = "Server \"${server.name}\" updated successfully"
            } catch (e: Exception) {
                _snackbarFlow.value = "Failed to update server: ${e.message}"
            }
        }
    }
    
    fun deleteServer(serverId: String) {
        viewModelScope.launch {
            try {
                val serverName = _uiState.value.servers.find { it.id == serverId }?.name
                serverRepository.deleteServer(serverId)
                
                if (_uiState.value.activeServerId == serverId) {
                    val remainingServers = _uiState.value.servers.filter { it.id != serverId }
                    if (remainingServers.isNotEmpty()) {
                        serverRepository.activateServer(remainingServers.first().id)
                    }
                }
                
                _snackbarFlow.value = "Server \"${serverName ?: "Unknown"}\" deleted"
            } catch (e: Exception) {
                _snackbarFlow.value = "Failed to delete server: ${e.message}"
            }
        }
    }
    
    fun activateServer(serverId: String) {
        viewModelScope.launch {
            try {
                serverRepository.activateServer(serverId)
                val server = _uiState.value.servers.find { it.id == serverId }
                _snackbarFlow.value = "Switched to \"${server?.name}\""
            } catch (e: Exception) {
                _snackbarFlow.value = "Failed to activate server: ${e.message}"
            }
        }
    }
    
    fun moveServerUp(index: Int) {
        viewModelScope.launch {
            val servers = _uiState.value.servers.toMutableList()
            if (index > 0) {
                val temp = servers[index]
                servers[index] = servers[index - 1]
                servers[index - 1] = temp
                
                servers.forEachIndexed { i, server ->
                    serverRepository.updateServerPriority(server.id, i)
                }
            }
        }
    }
    
    fun moveServerDown(index: Int) {
        viewModelScope.launch {
            val servers = _uiState.value.servers.toMutableList()
            if (index < servers.size - 1) {
                val temp = servers[index]
                servers[index] = servers[index + 1]
                servers[index + 1] = temp
                
                servers.forEachIndexed { i, server ->
                    serverRepository.updateServerPriority(server.id, i)
                }
            }
        }
    }
    
    fun testServerConnection(serverId: String) {
        viewModelScope.launch {
            val server = _uiState.value.servers.find { it.id == serverId } ?: return@launch
            
            _uiState.update { 
                it.copy(
                    connectionStates = it.connectionStates + (serverId to ConnectionState.CONNECTING)
                )
            }
            
            try {
                val isConnected = testConnection(server.baseUrl)
                
                _uiState.update { 
                    it.copy(
                        connectionStates = it.connectionStates + 
                            (serverId to if (isConnected) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED)
                    )
                }
                
                _snackbarFlow.value = if (isConnected) 
                    "Connected to \"${server.name}\"" 
                    else "Failed to connect to \"${server.name}\""
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        connectionStates = it.connectionStates + (serverId to ConnectionState.DISCONNECTED)
                    )
                }
                _snackbarFlow.value = "Connection test failed: ${e.message}"
            }
        }
    }
    
    fun testAllConnections() {
        viewModelScope.launch {
            _uiState.value.servers.forEach { server ->
                testServerConnection(server.id)
            }
        }
    }
    
    private suspend fun testConnection(url: String): Boolean {
        return try {
            withTimeoutOrNull(5000) {
                val parsedUrl = java.net.URL(url)
                val host = parsedUrl.host
                java.net.InetAddress.getByName(host)
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    private suspend inline fun <T> withTimeoutOrNull(timeMillis: Long, crossinline block: suspend () -> T): T? {
        return try {
            kotlinx.coroutines.withTimeout(timeMillis) { block() }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            null
        }
    }
    
    fun validateServerUrl(url: String): Boolean {
        return serverRepository.validateServerUrl(url)
    }
    
    fun sanitizeServerUrl(url: String): String {
        return serverRepository.sanitizeServerUrl(url)
    }
    
    fun clearSnackbar() {
        _snackbarFlow.value = null
    }
}
