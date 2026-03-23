@file:Suppress("kotlin:S6290")

package com.amberesaiae.melos.core.sync.di

import android.content.Context
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Hilt module for WorkManager worker factory.
 * Enables dependency injection in WorkManager workers.
 */
@Module
@InstallIn(SingletonComponent::class)
object SyncWorkerModule {
    
    @Singleton
    @Provides
    fun provideWorkerFactory(
        workerFactories: @JvmSuppressWildcards Map<String, Provider<SyncWorkerFactory.Factory>>
    ): SyncWorkerFactory {
        return SyncWorkerFactory(workerFactories)
    }
    
    @Singleton
    @Provides
    fun provideWorkManagerConfiguration(
        @ApplicationContext context: Context,
        workerFactory: SyncWorkerFactory
    ): Configuration {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
    }
}

/**
 * Worker factory for Hilt-enabled workers.
 * Automatically discovers and provides worker instances with dependencies.
 */
class SyncWorkerFactory @Inject constructor(
    private val workerFactories: Map<String, Provider<SyncWorkerFactory.Factory>>
) : WorkerFactory() {

    /**
     * Factory interface for creating worker instances.
     */
    interface Factory {
        fun createWorker(
            context: Context,
            workerClassName: String,
            workerParameters: WorkerParameters
        ): ListenableWorker
    }

    override fun createWorker(
        context: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        // Extract worker type from class name
        val workerType = workerClassName.substringAfterLast('.')
        
        // Find matching factory provider
        val factoryProvider = workerFactories[workerType]
            ?: return null
        
        val factory = factoryProvider.get()
        return factory.createWorker(context, workerClassName, workerParameters)
    }
}
