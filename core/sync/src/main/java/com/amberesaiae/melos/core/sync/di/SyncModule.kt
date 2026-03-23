@file:Suppress("kotlin:S6290")

package com.amberesaiae.melos.core.sync.di

import android.content.Context
import com.amberesaiae.melos.core.network.api.SubsonicApiService
import com.amberesaiae.melos.core.network.api.JellyfinApiService
import com.amberesaiae.melos.core.sync.SyncRepository
import com.amberesaiae.melos.core.sync.SyncScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing sync-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    fun provideSyncRepository(
        @ApplicationContext context: Context
    ): SyncRepository {
        return SyncRepository(context)
    }

    @Provides
    @Singleton
    fun provideSyncScheduler(
        @ApplicationContext context: Context
    ): SyncScheduler {
        return SyncScheduler(context)
    }

    @Provides
    @Singleton
    fun provideSubsonicApiService(): SubsonicApiService {
        // In production, this should be provided by the network module
        // This is a placeholder - actual implementation uses RetrofitModule
        throw IllegalStateException(
            "SubsonicApiService should be provided by NetworkModule. " +
            "Ensure :core:network dependency is included."
        )
    }

    @Provides
    @Singleton
    fun provideJellyfinApiService(): JellyfinApiService {
        // In production, this should be provided by the network module
        throw IllegalStateException(
            "JellyfinApiService should be provided by NetworkModule. " +
            "Ensure :core:network dependency is included."
        )
    }
}
