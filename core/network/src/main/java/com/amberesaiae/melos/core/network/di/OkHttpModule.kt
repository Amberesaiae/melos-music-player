package com.amberesaiae.melos.core.network

import android.content.Context
import com.amberesaiae.melos.core.network.di.NetworkModule
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OkHttpModule {

    private const val CONNECT_TIMEOUT = 30L
    private const val READ_TIMEOUT = 60L
    private const val WRITE_TIMEOUT = 60L
    private const val CACHE_SIZE = 10 * 1024 * 1024L // 10 MB

    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    @Provides
    @Singleton
    fun provideCache(@ApplicationContext context: Context): Cache {
        val cacheDir = File(context.cacheDir, "http_cache")
        return Cache(cacheDir, CACHE_SIZE)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        cache: Cache
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .cache(cache)
            .build()
    }
}
