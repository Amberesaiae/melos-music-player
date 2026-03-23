@file:Suppress("kotlin:S6290")

package com.amberesaiae.melos.core.cache.di

import android.content.Context
import androidx.room.Room
import com.amberesaiae.melos.core.cache.CacheDao
import com.amberesaiae.melos.core.cache.CacheDatabase
import com.amberesaiae.melos.core.cache.CacheManager
import com.amberesaiae.melos.core.cache.CacheRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for cache-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CacheModule {

    @Binds
    @Singleton
    abstract fun bindCacheRepository(
        cacheRepository: CacheRepository
    ): CacheRepository

    @Binds
    @Singleton
    abstract fun bindCacheManager(
        cacheManager: CacheManager
    ): CacheManager

    companion object {

        @Provides
        @Singleton
        fun provideCacheDatabase(
            @ApplicationContext context: Context
        ): CacheDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                CacheDatabase::class.java,
                "melos_cache_db"
            )
                .fallbackToDestructiveMigration()
                .build()
        }

        @Provides
        @Singleton
        fun provideCacheDao(
            database: CacheDatabase
        ): CacheDao {
            return database.cacheDao()
        }
    }
}
