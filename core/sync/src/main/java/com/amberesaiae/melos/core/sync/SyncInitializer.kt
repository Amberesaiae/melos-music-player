@file:Suppress("kotlin:S6290")

package com.amberesaiae.melos.core.sync

import android.app.Application
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.amberesaiae.melos.core.sync.di.SyncWorkerFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Extension function to initialize WorkManager with Hilt.
 * Call this from your Application class onCreate() method.
 *
 * Usage in Application class:
 * ```
 * @HiltAndroidApp
 * class MelosApplication : Application(), Configuration.Provider {
 *     @Inject lateinit var workerFactory: SyncWorkerFactory
 *
 *     override val workManagerConfiguration: Configuration
 *         get() = Configuration.Builder()
 *             .setWorkerFactory(workerFactory)
 *             .setMinimumLoggingLevel(Log.INFO)
 *             .build()
 *
 *     override fun onCreate() {
 *         super.onCreate()
 *         initializeSync()
 *     }
 * }
 * ```
 */
fun Application.initializeSync(
    enableLibrarySync: Boolean = true,
    enablePlaylistSync: Boolean = true,
    librarySyncIntervalHours: Long = 6L,
    playlistSyncIntervalHours: Long = 12L,
    requireWifi: Boolean = true,
    requireCharging: Boolean = false
) {
    if (enableLibrarySync) {
        scheduleLibrarySync(
            intervalHours = librarySyncIntervalHours,
            requireWifi = requireWifi,
            requireCharging = requireCharging
        )
    }

    if (enablePlaylistSync) {
        schedulePlaylistSync(
            intervalHours = playlistSyncIntervalHours,
            requireWifi = requireWifi,
            requireCharging = requireCharging
        )
    }
}

private fun Application.scheduleLibrarySync(
    intervalHours: Long,
    requireWifi: Boolean,
    requireCharging: Boolean
) {
    val constraints = androidx.work.Constraints.Builder()
        .setRequiredNetworkType(
            if (requireWifi) androidx.work.NetworkType.UNMETERED
            else androidx.work.NetworkType.CONNECTED
        )
        .setRequiresCharging(requireCharging)
        .setRequiresBatteryNotLow(true)
        .build()

    val workRequest = PeriodicWorkRequestBuilder<LibrarySyncWorker>(
        intervalHours, TimeUnit.HOURS
    )
        .setConstraints(constraints)
        .setBackoffCriteria(
            androidx.work.BackoffPolicy.EXPONENTIAL,
            5L, TimeUnit.MINUTES
        )
        .addTag(LibrarySyncWorker.WORK_NAME)
        .build()

    WorkManager.getInstance(this).enqueueUniquePeriodicWork(
        LibrarySyncWorker.WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        workRequest
    )
}

private fun Application.schedulePlaylistSync(
    intervalHours: Long,
    requireWifi: Boolean,
    requireCharging: Boolean
) {
    val constraints = androidx.work.Constraints.Builder()
        .setRequiredNetworkType(
            if (requireWifi) androidx.work.NetworkType.UNMETERED
            else androidx.work.NetworkType.CONNECTED
        )
        .setRequiresCharging(requireCharging)
        .setRequiresBatteryNotLow(true)
        .build()

    val workRequest = PeriodicWorkRequestBuilder<PlaylistSyncWorker>(
        intervalHours, TimeUnit.HOURS
    )
        .setConstraints(constraints)
        .setBackoffCriteria(
            androidx.work.BackoffPolicy.EXPONENTIAL,
            5L, TimeUnit.MINUTES
        )
        .addTag(PlaylistSyncWorker.WORK_NAME)
        .build()

    WorkManager.getInstance(this).enqueueUniquePeriodicWork(
        PlaylistSyncWorker.WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        workRequest
    )
}
