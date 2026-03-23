@file:Suppress("kotlin:S6290")

package com.amberesaiae.melos.feature.downloads.ui.di

import android.content.Context
import com.amberesaiae.melos.feature.downloads.ui.data.DownloadRepository
import com.amberesaiae.melos.feature.downloads.ui.data.DownloadRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadsHiltModule {

    companion object {
        @Provides
        @Singleton
        fun provideDownloadRepository(
            @ApplicationContext context: Context
        ): DownloadRepository {
            return DownloadRepositoryImpl(context)
        }
    }

    @Binds
    @Singleton
    abstract fun bindDownloadRepository(
        impl: DownloadRepositoryImpl
    ): DownloadRepository
}
