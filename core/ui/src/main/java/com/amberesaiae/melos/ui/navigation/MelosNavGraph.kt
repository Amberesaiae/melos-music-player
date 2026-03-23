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
    data object Authentication : MelosRoute("authentication")
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
 * Main navigation host for the Melos Music Player app.
 * Sets up the nav graph with all destinations and handles navigation between screens.
 */
@Composable
fun MelosNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = MelosRoute.Authentication.route,
    onNavigateToAuthentication: () -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None }
    ) {
        // Authentication screen
        composable(route = MelosRoute.Authentication.route) {
            AuthenticationScreenWrapper(
                onLoginSuccess = { username, serverType ->
                    navController.navigate(MelosRoute.Library.route) {
                        popUpTo(MelosRoute.Authentication.route) { inclusive = true }
                    }
                },
                onNavigateToSettings = {
                    navController.navigate(MelosRoute.Settings.route)
                }
            )
        }

        // Library screen
        composable(route = MelosRoute.Library.route) {
            LibraryScreenPlaceholder()
        }

        // Search screen
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

        // Playlists screen
        composable(route = MelosRoute.Playlists.route) {
            PlaylistsScreenPlaceholder()
        }

        // Playlist detail screen
        composable(
            route = MelosRoute.PlaylistDetail.route,
            arguments = listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId")
            PlaylistDetailScreenPlaceholder(playlistId = playlistId ?: "")
        }

        // Now Playing screen
        composable(route = MelosRoute.NowPlaying.route) {
            NowPlayingScreenPlaceholder()
        }

        // Server Setup screen
        composable(route = MelosRoute.ServerSetup.route) {
            ServerSetupScreenPlaceholder()
        }

        // Settings screen
        composable(route = MelosRoute.Settings.route) {
            SettingsScreenPlaceholder()
        }

        // Android Auto screen
        composable(route = MelosRoute.AndroidAuto.route) {
            AndroidAutoScreenPlaceholder()
        }
    }
}

/**
 * Wrapper composable for authentication screen.
 * Integrates with feature:authentication module's LoginScreen.
 * 
 * To use actual implementation, uncomment imports and replace body with:
 * import com.amberesaiae.melos.feature.authentication.ui.LoginScreen
 * 
 * LoginScreen(
 *     onLoginSuccess = onLoginSuccess,
 *     onNavigateToSettings = onNavigateToSettings
 * )
 */
@Composable
private fun AuthenticationScreenWrapper(
    onLoginSuccess: (String, com.amberesaiae.melos.feature.authentication.domain.ServerType) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    // Placeholder - replace with actual LoginScreen from feature:authentication
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Authentication Screen",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "From feature:authentication module",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { 
            onLoginSuccess("test_user", com.amberesaiae.melos.feature.authentication.domain.ServerType.SUBSONIC)
        }) {
            Text("Test Login (navigate to Library)")
        }
    }
}

@Composable
private fun LibraryScreenPlaceholder() {
    Text("Library Screen")
}

@Composable
private fun SearchScreenPlaceholder(query: String?) {
    Text("Search Screen - Query: $query")
}

@Composable
private fun PlaylistsScreenPlaceholder() {
    Text("Playlists Screen")
}

@Composable
private fun PlaylistDetailScreenPlaceholder(playlistId: String) {
    Text("Playlist Detail Screen - ID: $playlistId")
}

@Composable
private fun NowPlayingScreenPlaceholder() {
    Text("Now Playing Screen")
}

@Composable
private fun ServerSetupScreenPlaceholder() {
    Text("Server Setup Screen")
}

@Composable
private fun SettingsScreenPlaceholder() {
    Text("Settings Screen")
}

@Composable
private fun AndroidAutoScreenPlaceholder() {
    Text("Android Auto Screen")
}

/**
 * Main app scaffold with bottom navigation bar.
 * Shows bottom navigation for authenticated users.
 */
@Composable
fun MelosAppScaffold(
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    if (currentRoute == item.route) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) },
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        MelosNavHost(
            modifier = Modifier.padding(innerPadding),
            navController = navController
        )
    }
}
