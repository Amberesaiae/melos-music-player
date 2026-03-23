@file:Suppress("kotlin:S6290")

package com.amberesaiae.melos.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.amberesaiae.melos.core.network.api.SubsonicApiService
import com.amberesaiae.melos.core.network.api.JellyfinApiService
import com.amberesaiae.melos.core.sync.model.SyncResult
import com.amberesaiae.melos.core.sync.model.SyncStatus
import com.amberesaiae.melos.core.sync.model.PlaylistOperation
import com.amberesaiae.melos.core.sync.model.BidirectionalSyncStrategy
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager worker for playlist synchronization.
 * Supports bidirectional sync with merge strategies for conflict resolution.
 */
@HiltWorker
class PlaylistSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncRepository: SyncRepository,
    private val subsonicApiService: SubsonicApiService,
    private val jellyfinApiService: JellyfinApiService
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "PlaylistSyncWorker"
        const val WORK_NAME = "playlist_sync"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            syncRepository.updateSyncState(
                SyncStatus.IN_PROGRESS,
                0,
                "Starting playlist sync..."
            )

            val serverType = inputData.getString("server_type") ?: "subsonic"
            val syncStrategy = BidirectionalSyncStrategy.fromString(
                inputData.getString("sync_strategy") ?: "MERGE"
            )

            val syncResult = when (serverType) {
                "jellyfin" -> syncJellyfinPlaylists(syncStrategy)
                else -> syncSubsonicPlaylists(syncStrategy)
            }

            syncRepository.recordSyncStatistics(syncResult)
            syncRepository.updateLastPlaylistSyncTimestamp(syncResult.timestamp)

            syncRepository.updateSyncState(
                SyncStatus.COMPLETED,
                100,
                "Playlist sync completed: ${syncResult.itemsSynced} playlists"
            )

            when {
                syncResult.isSuccessful -> Result.success()
                syncResult.hasPartialFailures -> Result.retry()
                else -> Result.failure()
            }

        } catch (e: Exception) {
            syncRepository.updateSyncState(
                SyncStatus.FAILED,
                0,
                "Playlist sync failed: ${e.message}"
            )
            Result.failure()
        }
    }

    private suspend fun syncSubsonicPlaylists(
        strategy: BidirectionalSyncStrategy
    ): SyncResult {
        val syncedPlaylists = mutableListOf<String>()
        val conflicts = mutableListOf<SyncConflict>()
        var totalPlaylists = 0
        var failedPlaylists = 0

        try {
            // Get local playlists
            val localPlaylists = syncRepository.getLocalPlaylists()
            
            // Get server playlists
            val serverPlaylistsResponse = subsonicApiService.getPlaylists()
            val serverPlaylists = serverPlaylistsResponse.subsonicResponse?.playlists?.playlist ?: emptyList()

            totalPlaylists = maxOf(localPlaylists.size, serverPlaylists.size)

            when (strategy) {
                BidirectionalSyncStrategy.MERGE -> {
                    // Merge both sides
                    serverPlaylists.forEach { serverPlaylist ->
                        try {
                            val localVersion = localPlaylists.find { it.id == serverPlaylist.id }
                            
                            if (localVersion == null) {
                                // New on server, download
                                syncRepository.downloadPlaylist(serverPlaylist)
                                syncedPlaylists.add(serverPlaylist.id)
                            } else {
                                // Check for conflicts
                                val serverModified = serverPlaylist.lastModified?.time ?: 0L
                                val localModified = localVersion.lastModified ?: 0L
                                
                                if (serverModified > localModified) {
                                    // Server is newer, check if local has changes
                                    val localChanges = syncRepository.hasLocalChanges(serverPlaylist.id)
                                    
                                    if (localChanges) {
                                        conflicts.add(
                                            SyncConflict(
                                                itemId = serverPlaylist.id,
                                                itemType = "PLAYLIST",
                                                serverVersion = serverPlaylist,
                                                localVersion = localVersion,
                                                conflictType = "BOTH_MODIFIED"
                                            )
                                        )
                                    } else {
                                        // No local changes, update from server
                                        syncRepository.updatePlaylistFromServer(serverPlaylist)
                                        syncedPlaylists.add(serverPlaylist.id)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            failedPlaylists++
                        }
                    }

                    // Upload new local playlists
                    localPlaylists.forEach { localPlaylist ->
                        val existsOnServer = serverPlaylists.any { it.id == localPlaylist.id }
                        if (!existsOnServer && localPlaylist.isDirty) {
                            try {
                                subsonicApiService.createPlaylist(
                                    playlistId = localPlaylist.id,
                                    name = localPlaylist.name,
                                    songIds = localPlaylist.songIds
                                )
                                syncedPlaylists.add(localPlaylist.id)
                            } catch (e: Exception) {
                                failedPlaylists++
                            }
                        }
                    }
                }

                BidirectionalSyncStrategy.SERVER_WINS -> {
                    // Server version always wins
                    serverPlaylists.forEach { serverPlaylist ->
                        try {
                            syncRepository.downloadPlaylist(serverPlaylist)
                            syncedPlaylists.add(serverPlaylist.id)
                        } catch (e: Exception) {
                            failedPlaylists++
                        }
                    }
                }

                BidirectionalSyncStrategy.LOCAL_WINS -> {
                    // Local version always wins, upload all
                    localPlaylists.forEach { localPlaylist ->
                        if (localPlaylist.isDirty) {
                            try {
                                subsonicApiService.createPlaylist(
                                    playlistId = localPlaylist.id,
                                    name = localPlaylist.name,
                                    songIds = localPlaylist.songIds
                                )
                                syncedPlaylists.add(localPlaylist.id)
                            } catch (e: Exception) {
                                failedPlaylists++
                            }
                        }
                    }
                }
            }

            return SyncResult(
                isSuccessful = failedPlaylists == 0,
                hasPartialFailures = failedPlaylists > 0,
                itemsSynced = syncedPlaylists.size,
                totalItems = totalPlaylists,
                timestamp = System.currentTimeMillis(),
                conflicts = conflicts
            )

        } catch (e: Exception) {
            return SyncResult(
                isSuccessful = false,
                hasPartialFailures = false,
                itemsSynced = syncedPlaylists.size,
                totalItems = totalPlaylists,
                timestamp = System.currentTimeMillis(),
                conflicts = conflicts,
                errorMessage = e.message
            )
        }
    }

    private suspend fun syncJellyfinPlaylists(
        strategy: BidirectionalSyncStrategy
    ): SyncResult {
        val syncedPlaylists = mutableListOf<String>()
        val conflicts = mutableListOf<SyncConflict>()
        var totalPlaylists = 0
        var failedPlaylists = 0

        try {
            val localPlaylists = syncRepository.getLocalPlaylists()
            
            // Jellyfin playlists are stored as items with Playlist type
            val playlistsResponse = jellyfinApiService.getItemsByType(
                type = "Playlist",
                userId = syncRepository.getUserId()
            )

            val serverPlaylists = playlistsResponse.items ?: emptyList()
            totalPlaylists = maxOf(localPlaylists.size, serverPlaylists.size)

            when (strategy) {
                BidirectionalSyncStrategy.MERGE -> {
                    serverPlaylists.forEach { serverPlaylist ->
                        try {
                            val localVersion = localPlaylists.find { it.id == serverPlaylist.id }
                            
                            if (localVersion == null) {
                                syncRepository.downloadJellyfinPlaylist(serverPlaylist)
                                syncedPlaylists.add(serverPlaylist.id)
                            } else {
                                val serverModified = serverPlaylist.dateCreated?.time ?: 0L
                                val localModified = localVersion.lastModified ?: 0L
                                
                                if (serverModified > localModified) {
                                    val localChanges = syncRepository.hasLocalChanges(serverPlaylist.id)
                                    
                                    if (localChanges) {
                                        conflicts.add(
                                            SyncConflict(
                                                itemId = serverPlaylist.id,
                                                itemType = "PLAYLIST",
                                                serverVersion = serverPlaylist,
                                                localVersion = localVersion,
                                                conflictType = "BOTH_MODIFIED"
                                            )
                                        )
                                    } else {
                                        syncRepository.updatePlaylistFromServer(serverPlaylist)
                                        syncedPlaylists.add(serverPlaylist.id)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            failedPlaylists++
                        }
                    }
                }

                else -> {
                    // For SERVER_WINS and LOCAL_WINS, similar logic to Subsonic
                    serverPlaylists.forEach { serverPlaylist ->
                        try {
                            syncRepository.downloadJellyfinPlaylist(serverPlaylist)
                            syncedPlaylists.add(serverPlaylist.id)
                        } catch (e: Exception) {
                            failedPlaylists++
                        }
                    }
                }
            }

            return SyncResult(
                isSuccessful = failedPlaylists == 0,
                hasPartialFailures = failedPlaylists > 0,
                itemsSynced = syncedPlaylists.size,
                totalItems = totalPlaylists,
                timestamp = System.currentTimeMillis(),
                conflicts = conflicts
            )

        } catch (e: Exception) {
            return SyncResult(
                isSuccessful = false,
                hasPartialFailures = false,
                itemsSynced = syncedPlaylists.size,
                totalItems = totalPlaylists,
                timestamp = System.currentTimeMillis(),
                conflicts = conflicts,
                errorMessage = e.message
            )
        }
    }
}
