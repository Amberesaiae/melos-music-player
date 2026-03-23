package com.amberesaiae.melos.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

/**
 * Sealed class defining all navigation routes in the Melos Music Player app.
 * Each route represents a distinct screen or destination.
 */
sealed class MelosRoute(val route: String) {
    data object Library : MelosRoute("library")
    data object NowPlaying : MelosRoute("now_playing")
    data object Playlists : MelosRoute("playlists")
    data object PlaylistDetail : MelosRoute("playlist_detail/{playlistId}") {
        fun createRoute(playlistId: String) = "playlist_detail/$playlistId"
    }
    data object Search : MelosRoute("search/{query?}") {
        fun createRoute(query: String? = null) = if (query != null) "search/$query" else "search"
    }
    data object ServerSetup : MelosRoute("server_setup")
    data object Settings : MelosRoute("settings")
    data object AndroidAuto : MelosRoute("android_auto")
}

/**
 * Bottom navigation items configuration.
 * Defines the main tabs shown in the bottom navigation bar.
 */
val bottomNavItems = listOf(
    BottomNavItem(
        route = MelosRoute.Library.route,
        label = "Library",
        selectedIcon = Icons.Filled.LibraryMusic,
        unselectedIcon = Icons.Outlined.LibraryMusic
    ),
    BottomNavItem(
        route = MelosRoute.Search.route,
        label = "Search",
        selectedIcon = Icons.Filled.Search,
        unselectedIcon = Icons.Outlined.Search
    ),
    BottomNavItem(
        route = MelosRoute.Playlists.route,
        label = "Playlists",
        selectedIcon = Icons.Filled.PlaylistPlay,
        unselectedIcon = Icons.Outlined.PlaylistPlay
    ),
    BottomNavItem(
        route = MelosRoute.NowPlaying.route,
        label = "Now Playing",
        selectedIcon = Icons.Filled.PlaylistPlay,
        unselectedIcon = Icons.Outlined.PlaylistPlay
    ),
    BottomNavItem(
        route = MelosRoute.Settings.route,
        label = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
)

/**
 * Data class representing a bottom navigation item.
 */
data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector
)

/**
 * Main navigation graph for the Melos Music Player app.
 * 
 * @param navController The NavHostController managing navigation
 * @param startDestination The initial destination to display
 * @param modifier Optional modifier for the NavHost
 */
@Composable
fun MelosNavGraph(
    navController: NavHostController,
    startDestination: String = MelosRoute.Library.route,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { EnterTransition.Default },
        exitTransition = { ExitTransition.Default }
    ) {
        // Library - Home screen showing music library
        composable(route = MelosRoute.Library.route) {
            LibraryScreenPlaceholder()
        }
        
        // Now Playing - Full screen player
        composable(route = MelosRoute.NowPlaying.route) {
            NowPlayingScreenPlaceholder()
        }
        
        // Playlists - List of all playlists
        composable(route = MelosRoute.Playlists.route) {
            PlaylistsScreenPlaceholder()
        }
        
        // Playlist Detail - Single playlist with ID parameter
        composable(
            route = MelosRoute.PlaylistDetail.route,
            arguments = listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId")
            PlaylistDetailScreenPlaceholder(playlistId = playlistId)
        }
        
        // Search - Search screen with optional query parameter
        composable(
            route = MelosRoute.Search.route,
            arguments = listOf(
                navArgument("query") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val query = backStackEntry.arguments?.getString("query")
            SearchScreenPlaceholder(query = query)
        }
        
        // Server Setup - Subsonic server configuration
        composable(route = MelosRoute.ServerSetup.route) {
            ServerSetupScreenPlaceholder()
        }
        
        // Settings - App settings
        composable(route = MelosRoute.Settings.route) {
            SettingsScreenPlaceholder()
        }
        
        // Android Auto - Car app interface
        composable(route = MelosRoute.AndroidAuto.route) {
            AndroidAutoScreenPlaceholder()
        }
    }
}

/**
 * Bottom navigation component for the Melos Music Player.
 * 
 * @param navController The NavHostController for navigation
 * @param modifier Optional modifier for the NavigationBar
 */
@Composable
fun MelosBottomNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    // Hide bottom navigation on certain screens
    val shouldShowBottomNav = currentRoute in bottomNavItems.map { it.route }
    
    if (shouldShowBottomNav) {
        NavigationBar(modifier = modifier) {
            bottomNavItems.forEach { item ->
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = if (currentRoute == item.route) {
                                item.selectedIcon
                            } else {
                                item.unselectedIcon
                            },
                            contentDescription = item.label
                        )
                    },
                    label = { Text(text = item.label) },
                    selected = currentRoute == item.route,
                    onClick = {
                        navController.navigate(item.route) {
                            // Pop up to the start destination of the graph to
                            // avoid building up a large stack of destinations
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            // Avoid multiple copies of the same destination when
                            // reselecting the same item
                            launchSingleTop = true
                            // Restore state when reselecting a previously selected item
                            restoreState = true
                        }
                    }
                )
            }
        }
    }
}

/**
 * Main app scaffold with navigation and bottom bar integration.
 * 
 * @param navController The NavHostController for navigation
 * @param modifier Optional modifier for the Scaffold
 */
@Composable
fun MelosAppScaffold(
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        bottomBar = {
            MelosBottomNavigation(navController = navController)
        }
    ) { innerPadding ->
        MelosNavGraph(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

// ============================================================================
// Placeholder Composables - To be implemented in Phase 1-2
// ============================================================================

/**
 * Placeholder for Library screen - displays the music library.
 * To be implemented with actual library browsing functionality.
 */
@Composable
fun LibraryScreenPlaceholder() {
    // TODO: Implement library screen with album/artist/song browsing
    androidx.compose.material3.Text("Library Screen - To be implemented")
}

/**
 * Placeholder for Now Playing screen - full screen player.
 * To be implemented with playback controls and media display.
 */
@Composable
fun NowPlayingScreenPlaceholder() {
    // TODO: Implement now playing screen with player controls
    androidx.compose.material3.Text("Now Playing Screen - To be implemented")
}

/**
 * Placeholder for Playlists screen - list of all playlists.
 * To be implemented with playlist management features.
 */
@Composable
fun PlaylistsScreenPlaceholder() {
    // TODO: Implement playlists screen with playlist list
    androidx.compose.material3.Text("Playlists Screen - To be implemented")
}

/**
 * Placeholder for Playlist Detail screen - single playlist view.
 * 
 * @param playlistId The ID of the playlist to display
 */
@Composable
fun PlaylistDetailScreenPlaceholder(playlistId: String?) {
    // TODO: Implement playlist detail screen with songs list
    androidx.compose.material3.Text("Playlist Detail Screen - Playlist ID: $playlistId")
}

/**
 * Placeholder for Search screen - music search functionality.
 * 
 * @param query Optional search query parameter
 */
@Composable
fun SearchScreenPlaceholder(query: String?) {
    // TODO: Implement search screen with search bar and results
    androidx.compose.material3.Text("Search Screen - Query: $query")
}

/**
 * Placeholder for Server Setup screen - Subsonic server configuration.
 * To be implemented with server connection settings.
 */
@Composable
fun ServerSetupScreenPlaceholder() {
    // TODO: Implement server setup screen with Subsonic configuration
    androidx.compose.material3.Text("Server Setup Screen - To be implemented")
}

/**
 * Placeholder for Settings screen - app settings and preferences.
 * To be implemented with settings options.
 */
@Composable
fun SettingsScreenPlaceholder() {
    // TODO: Implement settings screen with app preferences
    androidx.compose.material3.Text("Settings Screen - To be implemented")
}

/**
 * Placeholder for Android Auto screen - car app interface.
 * To be implemented with Android Auto specific UI.
 */
@Composable
fun AndroidAutoScreenPlaceholder() {
    // TODO: Implement Android Auto screen
    androidx.compose.material3.Text("Android Auto Screen - To be implemented")
}
