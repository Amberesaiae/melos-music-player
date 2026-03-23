package com.amberesaiae.melos.core.network

import com.amberesaiae.melos.core.network.api.JellyfinApiService
import com.amberesaiae.melos.core.network.api.SubsonicApiService
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Converter
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RetrofitModule {

    private const val SUBSONIC_BASE_URL = "http://192.168.1.100:4040/"
    private const val JELLYFIN_BASE_URL = "http://192.168.1.100:8096/"

    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
            isLenient = true
            allowStructuredMapKeys = true
            prettyPrint = false
        }
    }

    @Provides
    @Singleton
    fun provideConverterFactory(json: Json): Converter.Factory {
        return json.asConverterFactory("application/json".toMediaType())
    }

    @Provides
    @Singleton
    fun provideSubsonicRetrofit(
        okHttpClient: OkHttpClient,
        converterFactory: Converter.Factory
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(SUBSONIC_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(converterFactory)
            .build()
    }

    @Provides
    @Singleton
    fun provideJellyfinRetrofit(
        okHttpClient: OkHttpClient,
        converterFactory: Converter.Factory
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(JELLYFIN_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(converterFactory)
            .build()
    }

    @Provides
    @Singleton
    fun provideSubsonicApiService(retrofit: Retrofit): SubsonicApiService {
        return retrofit.create(SubsonicApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideJellyfinApiService(retrofit: Retrofit): JellyfinApiService {
        return retrofit.create(JellyfinApiService::class.java)
    }
}
