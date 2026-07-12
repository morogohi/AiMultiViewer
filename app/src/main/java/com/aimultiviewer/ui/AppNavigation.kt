package com.aimultiviewer.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aimultiviewer.ui.home.HomeScreen
import com.aimultiviewer.ui.settings.SettingsScreen
import com.aimultiviewer.ui.viewer.ViewerScreen

object Routes {
    const val HOME = "home"
    const val VIEWER = "viewer"
    const val SETTINGS = "settings"
    const val ARG_DOC_ID = "docId"
}

@Composable
fun AppNavigation() {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenDocument = { id -> nav.navigate("${Routes.VIEWER}/$id") },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) }
            )
        }
        composable(
            route = "${Routes.VIEWER}/{${Routes.ARG_DOC_ID}}",
            arguments = listOf(navArgument(Routes.ARG_DOC_ID) { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString(Routes.ARG_DOC_ID).orEmpty()
            ViewerScreen(documentId = id, onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
