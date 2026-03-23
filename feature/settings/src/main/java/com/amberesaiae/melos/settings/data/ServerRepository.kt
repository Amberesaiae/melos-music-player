@file:Suppress("ktlint:standard:max-line-length")

package com.amberesaiae.melos.settings.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.amberesaiae.melos.settings.ui.ServerConfig
import com.amberesaiae.melos.settings.ui.ServerType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "server_settings")

@Singleton
class ServerRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val SERVER_IDS_KEY = stringPreferencesKey("server_ids")
        private fun serverNameKey(id: String) = stringPreferencesKey("server_$id")
        private fun serverUrlKey(id: String) = stringPreferencesKey("server_url_$id")
        private fun serverTypeKey(id: String) = stringPreferencesKey("server_type_$id")
        private fun serverUsernameKey(id: String) = stringPreferencesKey("server_username_$id")
        private fun serverPriorityKey(id: String) = intPreferencesKey("server_priority_$id")
        private fun serverActiveKey(id: String) = booleanPreferencesKey("server_active_$id")
        
        // Credential storage (in production, use EncryptedDataStore)
        private fun serverPasswordKey(id: String) = stringPreferencesKey("server_password_$id")
        private val ACTIVE_SERVER_ID_KEY = stringPreferencesKey("active_server_id")
    }
    
    val serversFlow: Flow<List<ServerConfig>> = context.dataStore.data.map { preferences ->
        val serverIds = preferences[SERVER_IDS_KEY]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        serverIds.mapNotNull { id -> getServerFromPreferences(preferences, id) }
            .sortedBy { it.priority }
    }
    
    val activeServerIdFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[ACTIVE_SERVER_ID_KEY]
    }
    
    suspend fun addServer(server: ServerConfig) {
        context.dataStore.edit { preferences ->
            val currentIds = preferences[SERVER_IDS_KEY]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            preferences[SERVER_IDS_KEY] = (currentIds + server.id).joinToString(",")
            saveServerToPreferences(preferences, server)
        }
    }
    
    suspend fun updateServer(server: ServerConfig) {
        context.dataStore.edit { preferences ->
            saveServerToPreferences(preferences, server)
        }
    }
    
    suspend fun deleteServer(serverId: String) {
        context.dataStore.edit { preferences ->
            val currentIds = preferences[SERVER_IDS_KEY]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            val newIds = currentIds - serverId
            preferences[SERVER_IDS_KEY] = newIds.joinToString(",")
            
            preferences.remove(serverNameKey(serverId))
            preferences.remove(serverUrlKey(serverId))
            preferences.remove(serverTypeKey(serverId))
            preferences.remove(serverUsernameKey(serverId))
            preferences.remove(serverPriorityKey(serverId))
            preferences.remove(serverActiveKey(serverId))
            preferences.remove(serverPasswordKey(serverId))
        }
    }
    
    suspend fun activateServer(serverId: String) {
        context.dataStore.edit { preferences ->
            val serverIds = preferences[SERVER_IDS_KEY]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            
            serverIds.forEach { id ->
                preferences[serverActiveKey(id)] = (id == serverId)
            }
            
            preferences[ACTIVE_SERVER_ID_KEY] = serverId
        }
    }
    
    suspend fun updateServerPriority(serverId: String, newPriority: Int) {
        context.dataStore.edit { preferences ->
            preferences[serverPriorityKey(serverId)] = newPriority
        }
    }
    
    suspend fun saveCredentials(serverId: String, password: String) {
        context.dataStore.edit { preferences ->
            preferences[serverPasswordKey(serverId)] = password
        }
    }
    
    suspend fun getCredentials(serverId: String): Pair<String, String?> {
        return context.dataStore.data.map { preferences ->
            val username = preferences[serverUsernameKey(serverId)] ?: ""
            val password = preferences[serverPasswordKey(serverId)]
            username to password
        }.first()
    }
    
    private fun getServerFromPreferences(preferences: Preferences, id: String): ServerConfig? {
        val name = preferences[serverNameKey(id)] ?: return null
        val baseUrl = preferences[serverUrlKey(id)] ?: return null
        val typeStr = preferences[serverTypeKey(id)] ?: return null
        val username = preferences[serverUsernameKey(id)] ?: ""
        val priority = preferences[serverPriorityKey(id)] ?: 0
        val isActive = preferences[serverActiveKey(id)] ?: false
        
        val serverType = when (typeStr) {
            "SUBSONIC" -> ServerType.SUBSONIC
            "JELLYFIN" -> ServerType.JELLYFIN
            else -> return null
        }
        
        return when (serverType) {
            ServerType.SUBSONIC -> ServerConfig.Subsonic(
                id = id,
                name = name,
                baseUrl = baseUrl,
                username = username,
                priority = priority,
                isActive = isActive
            )
            ServerType.JELLYFIN -> ServerConfig.Jellyfin(
                id = id,
                name = name,
                baseUrl = baseUrl,
                username = username,
                priority = priority,
                isActive = isActive
            )
        }
    }
    
    private fun saveServerToPreferences(preferences: Preferences, server: ServerConfig) {
        preferences[serverNameKey(server.id)] = server.name
        preferences[serverUrlKey(server.id)] = server.baseUrl
        preferences[serverTypeKey(server.id)] = server.serverType.name
        preferences[serverUsernameKey(server.id)] = server.username
        preferences[serverPriorityKey(server.id)] = server.priority
        preferences[serverActiveKey(server.id)] = server.isActive
    }
    
    fun validateServerUrl(url: String): Boolean {
        return try {
            java.net.URL(url).let {
                it.protocol in listOf("http", "https") && it.host.isNotBlank()
            }
        } catch (e: Exception) {
            false
        }
    }
    
    fun sanitizeServerUrl(url: String): String {
        var sanitized = url.trim()
        if (!sanitized.startsWith("http://") && !sanitized.startsWith("https://")) {
            sanitized = "http://$sanitized"
        }
        sanitized = sanitized.removeSuffix("/")
        return sanitized
    }
}
