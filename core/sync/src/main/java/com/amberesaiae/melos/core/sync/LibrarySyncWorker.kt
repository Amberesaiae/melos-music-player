@file:Suppress("kotlin:S6290")

package com.amberesaiae.melos.core.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.amberesaiae.melos.core.network.api.SubsonicApiService
import com.amberesaiae.melos.core.network.api.JellyfinApiService
import com.amberesaiae.melos.core.sync.model.SyncResult
import com.amberesaiae.melos.core.sync.model.SyncStatus
import com.amberesaiae.melos.core.sync.model.ConflictResolution
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * WorkManager worker for library synchronization.
 * Periodically syncs the music library with the server, performing incremental updates.
 */
@HiltWorker
class LibrarySyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncRepository: SyncRepository,
    private val subsonicApiService: SubsonicApiService,
    private val jellyfinApiService: JellyfinApiService
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "LibrarySyncWorker"
        const val WORK_NAME = "library_sync"
        private const val NOTIFICATION_CHANNEL_ID = "sync_notifications"
        private const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Create notification channel
            createNotificationChannel()

            // Update sync status
            syncRepository.updateSyncState(
                SyncStatus.IN_PROGRESS,
                0,
                "Starting library sync..."
            )

            // Get server type from input
            val serverType = inputData.getString("server_type") ?: "subsonic"
            val lastSyncTimestamp = syncRepository.getLastLibrarySyncTimestamp().first()

            // Perform sync based on server type
            val syncResult = when (serverType) {
                "jellyfin" -> syncJellyfinLibrary(lastSyncTimestamp)
                else -> syncSubsonicLibrary(lastSyncTimestamp)
            }

            // Update sync statistics
            syncRepository.recordSyncStatistics(syncResult)

            // Update last sync timestamp
            syncRepository.updateLastLibrarySyncTimestamp(syncResult.timestamp)

            // Handle conflicts based on resolution strategy
            val conflictResolution = ConflictResolution.fromString(
                inputData.getString("conflict_resolution") ?: "SERVER_WINS"
            )

            if (syncResult.conflicts.isNotEmpty()) {
                resolveConflicts(syncResult.conflicts, conflictResolution)
            }

            // Update final status
            syncRepository.updateSyncState(
                SyncStatus.COMPLETED,
                100,
                "Library sync completed: ${syncResult.itemsSynced} items"
            )

            // Return result
            when {
                syncResult.isSuccessful -> Result.success()
                syncResult.hasPartialFailures -> Result.retry()
                else -> Result.failure()
            }

        } catch (e: Exception) {
            syncRepository.updateSyncState(
                SyncStatus.FAILED,
                0,
                "Sync failed: ${e.message}"
            )
            Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            NOTIFICATION_ID,
            createNotification("Syncing library...", 0)
        )
    }

    private suspend fun syncSubsonicLibrary(lastSyncTimestamp: Long): SyncResult {
        val itemsSynced = mutableListOf<String>()
        val conflicts = mutableListOf<SyncConflict>()
        var totalItems = 0
        var failedItems = 0

        try {
            // Fetch indexes from server
            val indexesResponse = subsonicApiService.getIndexes()
            
            indexesResponse.subsonicResponse?.indexes?.index?.forEach { index -> {
                index.child?.forEach { songInfo ->
                    totalItems++
                    
                    try {
                        // Check if song needs sync (incremental)
                        val songLastModified = songInfo.lastModified?.time ?: 0L
                        
                        if (songLastModified > lastSyncTimestamp) {
                            // Update local cache
                            syncRepository.cacheSongFromServer(songInfo)
                            itemsSynced.add(songInfo.id)
                        }
                        
                        updateProgress(
                            (itemsSynced.size.toFloat() / totalItems * 100).toInt(),
                            "Syncing: ${songInfo.title}"
                        )
                        
                    } catch (e: Exception) {
                        failedItems++
                        // Log but continue sync
                    }
                }
            }}

            return SyncResult(
                isSuccessful = failedItems == 0,
                hasPartialFailures = failedItems > 0,
                itemsSynced = itemsSynced.size,
                totalItems = totalItems,
                timestamp = System.currentTimeMillis(),
                conflicts = conflicts
            )

        } catch (e: Exception) {
            return SyncResult(
                isSuccessful = false,
                hasPartialFailures = false,
                itemsSynced = itemsSynced.size,
                totalItems = totalItems,
                timestamp = System.currentTimeMillis(),
                conflicts = conflicts,
                errorMessage = e.message
            )
        }
    }

    private suspend fun syncJellyfinLibrary(lastSyncTimestamp: Long): SyncResult {
        val itemsSynced = mutableListOf<String>()
        val conflicts = mutableListOf<SyncConflict>()
        var totalItems = 0
        var failedItems = 0

        try {
            // Fetch all items from Jellyfin
            val itemsResponse = jellyfinApiService.getSongs(
                includeItemTypes = "Audio",
                startIndex = 0,
                limit = 100
            )

            itemsResponse.items?.forEach { item ->
                totalItems++
                
                try {
                    val itemLastModified = item.dateCreated?.time ?: 0L
                    
                    if (itemLastModified > lastSyncTimestamp) {
                        syncRepository.cacheSongFromJellyfinItem(item)
                        itemsSynced.add(item.id)
                    }
                    
                    updateProgress(
                        (itemsSynced.size.toFloat() / totalItems * 100).toInt(),
                        "Syncing: ${item.name}"
                    )
                    
                } catch (e: Exception) {
                    failedItems++
                }
            }

            return SyncResult(
                isSuccessful = failedItems == 0,
                hasPartialFailures = failedItems > 0,
                itemsSynced = itemsSynced.size,
                totalItems = totalItems,
                timestamp = System.currentTimeMillis(),
                conflicts = conflicts
            )

        } catch (e: Exception) {
            return SyncResult(
                isSuccessful = false,
                hasPartialFailures = false,
                itemsSynced = itemsSynced.size,
                totalItems = totalItems,
                timestamp = System.currentTimeMillis(),
                conflicts = conflicts,
                errorMessage = e.message
            )
        }
    }

    private suspend fun resolveConflicts(
        conflicts: List<SyncConflict>,
        resolution: ConflictResolution
    ) {
        conflicts.forEach { conflict ->
            when (resolution) {
                ConflictResolution.SERVER_WINS -> {
                    // Always use server version
                    syncRepository.applyServerVersion(conflict)
                }
                ConflictResolution.LOCAL_WINS -> {
                    // Keep local version
                    syncRepository.keepLocalVersion(conflict)
                }
                ConflictResolution.MANUAL -> {
                    // Mark for manual resolution
                    syncRepository.markForManualResolution(conflict)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Sync Notifications",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progress notifications for library sync"
                setShowBadge(false)
            }
            
            val notificationManager = applicationContext.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(message: String, progress: Int): android.app.Notification {
        val builder = androidx.core.app.NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Library Sync")
            .setContentText(message)
            .setPriority(NotificationManagerCompat.IMPORTANCE_LOW)
            .setOngoing(true)
        
        if (progress > 0) {
            builder.setProgress(100, progress, false)
        }
        
        return builder.build()
    }
}
