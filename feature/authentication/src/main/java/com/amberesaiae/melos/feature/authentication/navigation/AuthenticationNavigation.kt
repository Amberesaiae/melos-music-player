@file:Suppress("kotlin:S6290")

package com.amberesaiae.melos.feature.authentication.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.amberesaiae.melos.feature.authentication.domain.ServerType
import com.amberesaiae.melos.feature.authentication.ui.LoginScreen

/**
 * Navigation route for authentication screen.
 */
const val AUTHENTICATION_ROUTE = "authentication"

/**
 * Add authentication screen to navigation graph.
 */
fun NavGraphBuilder.authenticationScreen(
    onLoginSuccess: (username: String, serverType: ServerType) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    composable(route = AUTHENTICATION_ROUTE) {
        LoginScreen(
            onLoginSuccess = onLoginSuccess,
            onNavigateToSettings = onNavigateToSettings
        )
    }
}

/**
 * Navigate to authentication screen.
 */
fun NavController.navigateToAuthentication(navOptions: NavOptions? = null) {
    navigate(AUTHENTICATION_ROUTE, navOptions)
}

/**
 * Pop authentication screen from back stack.
 */
fun NavController.popAuthentication() {
    popBackStack(AUTHENTICATION_ROUTE, inclusive = true)
}
