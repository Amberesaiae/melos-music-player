package com.amberesaiae.melos

import android.app.Application
import com.jakewharton.timber.Timber
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber.DebugTree

/**
 * Main Application class for the Melos Music Player.
 * 
 * Initializes core dependencies including:
 * - Hilt for dependency injection
 * - Timber for logging
 * - Future services: player, sync worker, etc.
 */
@HiltAndroidApp
class MelosApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber for logging in debug builds
        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
            Timber.d("MelosApplication initialized in debug mode")
        }
        
        // TODO: Initialize music player service
        // TODO: Initialize sync worker for library updates
        // TODO: Initialize analytics service
        // TODO: Initialize crash reporting
    }
}