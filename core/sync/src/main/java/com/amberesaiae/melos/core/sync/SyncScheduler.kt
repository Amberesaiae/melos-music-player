@file:Suppress("kotlin:S6290")

package com.amberesaiae.melos.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scheduler for background sync operations using WorkManager.
 * Manages periodic sync requests with appropriate constraints.
 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val LIBRARY_SYNC_TAG = "library_sync"
        private const val PLAYLIST_SYNC_TAG = "playlist_sync"
        
        // Sync intervals
        private const val LIBRARY_SYNC_INTERVAL_HOURS = 6L
        private const val PLAYLIST_SYNC_INTERVAL_HOURS = 12L
        
        // Backoff settings
        private const val BACKOFF_DELAY_MINUTES = 5L
        private const val BACKOFF_MAX_DELAY_HOURS = 2L
    }

    private val workManager = WorkManager.getInstance(context)

    /**
     * Schedule periodic library synchronization.
     * @param syncIntervalHours Interval between syncs in hours
     * @param requireWifi Only sync on WiFi
     * @param requireCharging Only sync while charging
     */
    fun scheduleLibrarySync(
        syncIntervalHours: Long = LIBRARY_SYNC_INTERVAL_HOURS,
        requireWifi: Boolean = true,
        requireCharging: Boolean = false,
        serverType: String = "subsonic",
        conflictResolution: String = "SERVER_WINS"
    ) {
        val constraints = buildConstraints(
            requireWifi = requireWifi,
            requireCharging = requireCharging
        )

        val workRequest = PeriodicWorkRequestBuilder<LibrarySyncWorker>(
            syncIntervalHours, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_DELAY_MINUTES,
                TimeUnit.MINUTES
            )
            .setInputData(
                LibrarySyncWorker.buildInputData(
                    serverType = serverType,
                    conflictResolution = conflictResolution
                )
            )
            .addTag(LIBRARY_SYNC_TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            LIBRARY_SYNC_TAG,
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
    }

    /**
     * Schedule periodic playlist synchronization.
     */
    fun schedulePlaylistSync(
        syncIntervalHours: Long = PLAYLIST_SYNC_INTERVAL_HOURS,
        requireWifi: Boolean = true,
        requireCharging: Boolean = false,
        serverType: String = "subsonic",
        syncStrategy: String = "MERGE"
    ) {
        val constraints = buildConstraints(
            requireWifi = requireWifi,
            requireCharging = requireCharging
        )

        val workRequest = PeriodicWorkRequestBuilder<PlaylistSyncWorker>(
            syncIntervalHours, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_DELAY_MINUTES,
                TimeUnit.MINUTES
            )
            .setInputData(
                PlaylistSyncWorker.buildInputData(
                    serverType = serverType,
                    syncStrategy = syncStrategy
                )
            )
            .addTag(PLAYLIST_SYNC_TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PLAYLIST_SYNC_TAG,
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
    }

    /**
     * Trigger manual library sync immediately.
     */
    fun triggerManualLibrarySync(
        serverType: String = "subsonic",
        conflictResolution: String = "SERVER_WINS"
    ): UUID {
        val constraints = buildConstraints(
            requireWifi = false,
            requireCharging = false
        )

        val workRequest = OneTimeWorkRequestBuilder<LibrarySyncWorker>()
            .setConstraints(constraints)
            .setInputData(
                LibrarySyncWorker.buildInputData(
                    serverType = serverType,
                    conflictResolution = conflictResolution
                )
            )
            .build()

        workManager.enqueueUniqueWork(
            "manual_library_sync",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        return workRequest.id
    }

    /**
     * Trigger manual playlist sync immediately.
     */
    fun triggerManualPlaylistSync(
        serverType: String = "subsonic",
        syncStrategy: String = "MERGE"
    ): UUID {
        val constraints = buildConstraints(
            requireWifi = false,
            requireCharging = false
        )

        val workRequest = OneTimeWorkRequestBuilder<PlaylistSyncWorker>()
            .setConstraints(constraints)
            .setInputData(
                PlaylistSyncWorker.buildInputData(
                    serverType = serverType,
                    syncStrategy = syncStrategy
                )
            )
            .build()

        workManager.enqueueUniqueWork(
            "manual_playlist_sync",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        return workRequest.id
    }

    /**
     * Cancel all scheduled sync operations.
     */
    fun cancelAllSync() {
        workManager.cancelUniqueWork(LIBRARY_SYNC_TAG)
        workManager.cancelUniqueWork(PLAYLIST_SYNC_TAG)
        workManager.cancelAllWorkByTag(LIBRARY_SYNC_TAG)
        workManager.cancelAllWorkByTag(PLAYLIST_SYNC_TAG)
    }

    /**
     * Cancel library sync only.
     */
    fun cancelLibrarySync() {
        workManager.cancelUniqueWork(LIBRARY_SYNC_TAG)
        workManager.cancelAllWorkByTag(LIBRARY_SYNC_TAG)
    }

    /**
     * Cancel playlist sync only.
     */
    fun cancelPlaylistSync() {
        workManager.cancelUniqueWork(PLAYLIST_SYNC_TAG)
        workManager.cancelAllWorkByTag(PLAYLIST_SYNC_TAG)
    }

    /**
     * Get sync status as Flow.
     */
    fun getSyncStatus(workId: UUID): Flow<SyncStatusInfo> = flow {
        workManager.getWorkInfoByIdFlow(workId).collect { workInfo ->
            emit(
                SyncStatusInfo(
                    state = workInfo.state.name,
                    progress = workInfo.progress.getInt("progress", 0),
                    statusMessage = workInfo.outputData.getString("status_message") ?: "",
                    isFinished = workInfo.state.isFinished
                )
            )
        }
    }

    /**
     * Check if library sync is scheduled.
     */
    fun isLibrarySyncScheduled(): Flow<Boolean> = flow {
        val workInfos = workManager.getWorkInfosForUniqueWorkFlow(LIBRARY_SYNC_TAG)
        workInfos.collect { list ->
            val isScheduled = list.any { it.state == WorkInfo.State.ENQUEUED }
            emit(isScheduled)
        }
    }

    /**
     * Check if playlist sync is scheduled.
     */
    fun isPlaylistSyncScheduled(): Flow<Boolean> = flow {
        val workInfos = workManager.getWorkInfosForUniqueWorkFlow(PLAYLIST_SYNC_TAG)
        workInfos.collect { list ->
            val isScheduled = list.any { it.state == WorkInfo.State.ENQUEUED }
            emit(isScheduled)
        }
    }

    private fun buildConstraints(
        requireWifi: Boolean,
        requireCharging: Boolean
    ): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(
                if (requireWifi) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .setRequiresCharging(requireCharging)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
    }
}

/**
 * Sync status information.
 */
data class SyncStatusInfo(
    val state: String,
    val progress: Int,
    val statusMessage: String,
    val isFinished: Boolean
)

/**
 * Build input data for LibrarySyncWorker.
 */
fun LibrarySyncWorker.Companion.buildInputData(
    serverType: String,
    conflictResolution: String
): androidx.work.Data {
    return androidx.work.Data.Builder()
        .putString("server_type", serverType)
        .putString("conflict_resolution", conflictResolution)
        .build()
}

/**
 * Build input data for PlaylistSyncWorker.
 */
fun PlaylistSyncWorker.Companion.buildInputData(
    serverType: String,
    syncStrategy: String
): androidx.work.Data {
    return androidx.work.Data.Builder()
        .putString("server_type", serverType)
        .putString("sync_strategy", syncStrategy)
        .build()
}
