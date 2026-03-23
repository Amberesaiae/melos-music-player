@file:Suppress("kotlin:S6290", "kotlin:S1192")

package com.amberesaiae.melos.core.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.amberesaiae.melos.core.network.model.SubsonicSong
import com.amberesaiae.melos.core.network.model.JellyfinItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sync_preferences")

/**
 * Repository for managing sync state, statistics, and error handling.
 * Uses DataStore for persistent storage of sync metadata.
 */
@Singleton
class SyncRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // Preference keys
        private val LAST_LIBRARY_SYNC_KEY = longPreferencesKey("last_library_sync")
        private val LAST_PLAYLIST_SYNC_KEY = longPreferencesKey("last_playlist_sync")
        private val SYNC_ERROR_COUNT_KEY = longPreferencesKey("sync_error_count")
        private val LAST_SYNC_ERROR_KEY = stringPreferencesKey("last_sync_error")
        private val TOTAL_ITEMS_SYNCED_KEY = longPreferencesKey("total_items_synced")
        private val TOTAL_SYNC_OPERATIONS_KEY = longPreferencesKey("total_sync_operations")
        private val FAILED_SYNC_OPERATIONS_KEY = longPreferencesKey("failed_sync_operations")
        private val SYNC_ENABLED_KEY = stringPreferencesKey("sync_enabled")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
    }

    /**
     * Get last library sync timestamp.
     */
    suspend fun getLastLibrarySyncTimestamp(): Flow<Long> {
        return context.dataStore.data.map { preferences ->
            preferences[LAST_LIBRARY_SYNC_KEY] ?: 0L
        }
    }

    /**
     * Get last playlist sync timestamp.
     */
    suspend fun getLastPlaylistSyncTimestamp(): Flow<Long> {
        return context.dataStore.data.map { preferences ->
            preferences[LAST_PLAYLIST_SYNC_KEY] ?: 0L
        }
    }

    /**
     * Update last library sync timestamp.
     */
    suspend fun updateLastLibrarySyncTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_LIBRARY_SYNC_KEY] = timestamp
        }
    }

    /**
     * Update last playlist sync timestamp.
     */
    suspend fun updateLastPlaylistSyncTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_PLAYLIST_SYNC_KEY] = timestamp
        }
    }

    /**
     * Record sync statistics.
     */
    suspend fun recordSyncStatistics(result: SyncResult) {
        context.dataStore.edit { preferences ->
            val currentTotal = preferences[TOTAL_ITEMS_SYNCED_KEY] ?: 0L
            preferences[TOTAL_ITEMS_SYNCED_KEY] = currentTotal + result.itemsSynced

            val currentOperations = preferences[TOTAL_SYNC_OPERATIONS_KEY] ?: 0L
            preferences[TOTAL_SYNC_OPERATIONS_KEY] = currentOperations + 1

            if (!result.isSuccessful) {
                val failedOps = preferences[FAILED_SYNC_OPERATIONS_KEY] ?: 0L
                preferences[FAILED_SYNC_OPERATIONS_KEY] = failedOps + 1

                val errorCount = preferences[SYNC_ERROR_COUNT_KEY] ?: 0L
                preferences[SYNC_ERROR_COUNT_KEY] = errorCount + 1

                result.errorMessage?.let { error ->
                    preferences[LAST_SYNC_ERROR_KEY] = error
                }
            }
        }
    }

    /**
     * Get sync statistics.
     */
    fun getSyncStatistics(): Flow<SyncStatistics> {
        return context.dataStore.data.map { preferences ->
            SyncStatistics(
                totalItemsSynced = preferences[TOTAL_ITEMS_SYNCED_KEY] ?: 0L,
                totalSyncOperations = preferences[TOTAL_SYNC_OPERATIONS_KEY] ?: 0L,
                failedSyncOperations = preferences[FAILED_SYNC_OPERATIONS_KEY] ?: 0L,
                errorCount = preferences[SYNC_ERROR_COUNT_KEY] ?: 0L,
                lastError = preferences[LAST_SYNC_ERROR_KEY]
            )
        }
    }

    /**
     * Reset error count.
     */
    suspend fun resetErrorCount() {
        context.dataStore.edit { preferences ->
            preferences[SYNC_ERROR_COUNT_KEY] = 0L
        }
    }

    /**
     * Clear last error.
     */
    suspend fun clearLastError() {
        context.dataStore.edit { preferences ->
            preferences.remove(LAST_SYNC_ERROR_KEY)
        }
    }

    /**
     * Update sync state with progress.
     */
    suspend fun updateSyncState(
        status: SyncStatus,
        progress: Int,
        message: String
    ) {
        // This would typically be exposed via a Flow or callback
        // For now, we'll log it (in production, use a state holder)
        android.util.Log.d("SyncRepository", "Sync state: $status, progress: $progress%, message: $message")
    }

    /**
     * Cache song from Subsonic server response.
     */
    suspend fun cacheSongFromServer(songInfo: SubsonicSong) {
        // Delegate to cache module
        // In production, inject CacheRepository and call cacheSong()
        android.util.Log.d("SyncRepository", "Caching song: ${songInfo.id} - ${songInfo.title}")
    }

    /**
     * Cache song from Jellyfin item.
     */
    suspend fun cacheSongFromJellyfinItem(item: JellyfinItem) {
        android.util.Log.d("SyncRepository", "Caching Jellyfin song: ${item.id} - ${item.name}")
    }

    /**
     * Get local playlists.
     */
    suspend fun getLocalPlaylists(): List<LocalPlaylist> {
        // In production, query Room database for local playlists
        return emptyList()
    }

    /**
     * Download playlist from server.
     */
    suspend fun downloadPlaylist(playlist: com.amberesaiae.melos.core.network.model.SubsonicPlaylist) {
        android.util.Log.d("SyncRepository", "Downloading playlist: ${playlist.id}")
    }

    /**
     * Download Jellyfin playlist.
     */
    suspend fun downloadJellyfinPlaylist(item: JellyfinItem) {
        android.util.Log.d("SyncRepository", "Downloading Jellyfin playlist: ${item.id}")
    }

    /**
     * Update playlist from server.
     */
    suspend fun updatePlaylistFromServer(serverPlaylist: Any) {
        android.util.Log.d("SyncRepository", "Updating playlist from server: ${getPlaylistId(serverPlaylist)}")
    }

    /**
     * Check if local playlist has changes.
     */
    suspend fun hasLocalChanges(playlistId: String): Boolean {
        // In production, check dirty flag in database
        return false
    }

    /**
     * Apply server version for conflict resolution.
     */
    suspend fun applyServerVersion(conflict: SyncConflict) {
        android.util.Log.d("SyncRepository", "Applying server version for conflict: ${conflict.itemId}")
    }

    /**
     * Keep local version for conflict resolution.
     */
    suspend fun keepLocalVersion(conflict: SyncConflict) {
        android.util.Log.d("SyncRepository", "Keeping local version for conflict: ${conflict.itemId}")
    }

    /**
     * Mark conflict for manual resolution.
     */
    suspend fun markForManualResolution(conflict: SyncConflict) {
        android.util.Log.d("SyncRepository", "Marking conflict for manual resolution: ${conflict.itemId}")
    }

    /**
     * Get user ID for Jellyfin API calls.
     */
    suspend fun getUserId(): String {
        return context.dataStore.data.first()[USER_ID_KEY] ?: ""
    }

    /**
     * Set user ID.
     */
    suspend fun setUserId(userId: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_ID_KEY] = userId
        }
    }

    /**
     * Check if sync is enabled.
     */
    suspend fun isSyncEnabled(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[SYNC_ENABLED_KEY]?.toBoolean() ?: true
        }
    }

    /**
     * Set sync enabled state.
     */
    suspend fun setSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SYNC_ENABLED_KEY] = enabled.toString()
        }
    }

    /**
     * Get number of retries for a failed sync.
     */
    private suspend fun getRetryCount(tag: String): Int {
        val key = stringPreferencesKey("retry_count_$tag")
        return context.dataStore.data.first()[key]?.toInt() ?: 0
    }

    /**
     * Increment retry count.
     */
    private suspend fun incrementRetryCount(tag: String) {
        val key = stringPreferencesKey("retry_count_$tag")
        context.dataStore.edit { preferences ->
            val current = preferences[key]?.toInt() ?: 0
            preferences[key] = (current + 1).toString()
        }
    }

    /**
     * Reset retry count.
     */
    private suspend fun resetRetryCount(tag: String) {
        val key = stringPreferencesKey("retry_count_$tag")
        context.dataStore.edit { preferences ->
            preferences.remove(key)
        }
    }

    private fun getPlaylistId(playlist: Any): String {
        return when (playlist) {
            is com.amberesaiae.melos.core.network.model.SubsonicPlaylist -> playlist.id
            is JellyfinItem -> playlist.id
            else -> playlist.toString()
        }
    }
}

/**
 * Sync statistics data class.
 */
data class SyncStatistics(
    val totalItemsSynced: Long,
    val totalSyncOperations: Long,
    val failedSyncOperations: Long,
    val errorCount: Long,
    val lastError: String?
) {
    val successRate: Float
        get() = if (totalSyncOperations > 0) {
            ((totalSyncOperations - failedSyncOperations).toFloat() / totalSyncOperations) * 100
        } else {
            100f
        }
}

/**
 * Local playlist representation.
 */
data class LocalPlaylist(
    val id: String,
    val name: String,
    val songIds: List<String>,
    val lastModified: Long,
    val isDirty: Boolean
)
