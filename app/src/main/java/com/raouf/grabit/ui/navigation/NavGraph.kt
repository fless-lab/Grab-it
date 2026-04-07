package com.raouf.grabit.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.raouf.grabit.ui.home.HomeScreen
import com.raouf.grabit.ui.preview.PreviewScreen
import com.raouf.grabit.ui.settings.SettingsScreen
import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
    const val HOME = "home"
    const val PREVIEW = "preview/{url}"
    const val SETTINGS = "settings"

    fun preview(url: String): String =
        "preview/${URLEncoder.encode(url, "UTF-8")}"
}

@Composable
fun GrabitNavGraph(
    navController: NavHostController,
    sharedUrl: String?,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToPreview = { url ->
                    navController.navigate(Routes.preview(url))
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onDownloadClick = { /* TODO: detail view */ },
                urlFromIntent = sharedUrl,
            )
        }

        composable(
            route = Routes.PREVIEW,
            arguments = listOf(navArgument("url") { type = NavType.StringType }),
        ) { backStackEntry ->
            val encodedUrl = backStackEntry.arguments?.getString("url") ?: ""
            // URL is decoded automatically by Navigation, but double-check
            PreviewScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
