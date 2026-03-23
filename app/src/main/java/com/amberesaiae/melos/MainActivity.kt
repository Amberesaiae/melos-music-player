package com.amberesaiae.melos

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.amberesaiae.melos.ui.navigation.MelosNavGraph
import com.amberesaiae.melos.ui.theme.MelosTheme
import com.amberesaiae.melos.domain.model.MediaConsts
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main entry point for the Melos Music Player application.
 * 
 * This Activity serves as the single-activity host for the entire application,
 * providing:
 * - Edge-to-edge display with styled system bars
 * - Jetpack Compose UI container with MelosTheme
 * - Navigation host with MelosNavGraph
 * - Intent handling for media playback actions
 * - Back press handling via NavController
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display with customized system bar styling
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                scrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.dark(
                scrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT
            )
        )

        // Make content appear behind system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            MelosTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    // Handle system bar appearance changes
                    UpdateSystemBars()

                    // Set up navigation host with Melos navigation graph
                    NavHost(
                        navController = navController,
                        startDestination = MelosNavGraph.START_DESTINATION
                    ) {
                        MelosNavGraph(navController = navController)
                    }

                    // Handle back press with NavController
                    SetupBackPressHandler(navController)

                    // Handle incoming intents for media playback
                    HandleMediaIntents(intent, navController)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Update the activity's intent to handle new media playback requests
        setIntent(intent)
        handleMediaIntent(intent, null)
    }

    /**
     * Handles incoming intents for media playback actions.
     * Supports ACTION_VIEW and ACTION_PLAY_FROM_SEARCH.
     */
    private fun HandleMediaIntents(intent: Intent?, navController: androidx.navigation.NavHostController) {
        intent?.let { handleMediaIntent(it, navController) }
    }

    private fun handleMediaIntent(intent: Intent, navController: androidx.navigation.NavHostController?) {
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                // Handle media URI viewing
                intent.data?.let { uri ->
                    // Navigate to player screen with the media URI
                    navController?.navigate("player?uri=${uri.toString()}")
                }
            }
            MediaConsts.ACTION_PLAY_FROM_SEARCH -> {
                // Handle voice search playback
                val query = intent.getStringExtra("android.intent.extra.TEXT")
                query?.let {
                    // Navigate to search results with the query
                    navController?.navigate("search?query=${it}")
                }
            }
        }
    }

    /**
     * Sets up back press handling using NavController.
     * Pops the back stack instead of exiting the app.
     */
    @androidx.compose.runtime.Composable
    private fun SetupBackPressHandler(navController: androidx.navigation.NavHostController) {
        androidx.activity.compose.BackHandler {
            if (!navController.popBackStack()) {
                // If we can't pop back, finish the activity
                finish()
            }
        }
    }

    /**
     * Updates system bar appearance based on current theme state.
     */
    @androidx.compose.runtime.Composable
    private fun UpdateSystemBars() {
        val isDarkTheme = MaterialTheme.colorScheme.brightness < 0.5f
        
        DisposableEffect(isDarkTheme) {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !isDarkTheme
                isAppearanceLightNavigationBars = !isDarkTheme
            }
            onDispose { }
        }

        // React to configuration changes (e.g., dark mode toggle)
        LaunchedEffect(isDarkTheme) {
            // Additional system bar updates if needed
        }
    }
}