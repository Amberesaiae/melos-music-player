@file:Suppress("kotlin:S6290")

package com.amberesaiae.melos.feature.downloads.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val DOWNLOADS_ROUTE = "downloads"

fun NavController.navigateToDownloads() {
    navigate(DOWNLOADS_ROUTE)
}

fun NavGraphBuilder.downloadsScreen(
    onNavigateBack: () -> Unit
) {
    composable(route = DOWNLOADS_ROUTE) {
        com.amberesaiae.melos.feature.downloads.ui.ui.DownloadQueueScreen(
            onNavigateBack = onNavigateBack
        )
    }
}
