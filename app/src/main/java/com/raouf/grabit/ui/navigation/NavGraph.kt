package com.raouf.grabit.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.compose.ui.platform.LocalContext
import com.raouf.grabit.domain.model.Download
import com.raouf.grabit.domain.model.DownloadStatus
import com.raouf.grabit.ui.browser.BrowserScreen
import com.raouf.grabit.ui.home.HomeScreen
import com.raouf.grabit.ui.onboarding.OnboardingScreen
import com.raouf.grabit.ui.player.PlayerActivity
import com.raouf.grabit.ui.playlist.PlaylistDetailScreen
import com.raouf.grabit.ui.playlist.PlaylistScreen
import com.raouf.grabit.ui.preview.PreviewScreen
import com.raouf.grabit.ui.settings.SettingsScreen
import java.net.URLEncoder

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val PREVIEW = "preview/{url}"
    const val PLAYLIST = "playlist/{url}"
    const val PLAYLIST_DETAIL = "playlist_detail/{playlistId}"
    const val BROWSER = "browser"
    const val SETTINGS = "settings"
    const val SAFE_ZONE = "safe_zone"

    fun preview(url: String): String =
        "preview/${URLEncoder.encode(url, "UTF-8")}"

    fun playlist(url: String): String =
        "playlist/${URLEncoder.encode(url, "UTF-8")}"

    fun playlistDetail(playlistId: String): String =
        "playlist_detail/${URLEncoder.encode(playlistId, "UTF-8")}"

    /** Route to the right screen based on URL type */
    fun forUrl(url: String): String {
        val lower = url.lowercase()
        return if ("playlist?list=" in lower || "&list=" in lower || "/sets/" in lower) {
            playlist(url)
        } else {
            preview(url)
        }
    }
}

@Composable
fun GrabitNavGraph(
    navController: NavHostController,
    sharedUrl: String?,
    onSharedUrlConsumed: () -> Unit = {},
    clipboardUrl: String? = null,
    onDismissClipboard: () -> Unit = {},
    startDestination: String,
    onOnboardingComplete: (folderUri: String?) -> Unit,
) {
    // Handle shared URL at graph level (works from any screen)
    LaunchedEffect(sharedUrl) {
        if (!sharedUrl.isNullOrBlank()) {
            navController.navigate(Routes.forUrl(sharedUrl))
            onSharedUrlConsumed()
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFolderSelected = { uri ->
                    onOnboardingComplete(uri)
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
                onSkip = {
                    onOnboardingComplete(null)
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            val context = LocalContext.current
            HomeScreen(
                onNavigateToPreview = { url ->
                    navController.navigate(Routes.forUrl(url))
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onNavigateToBrowser = {
                    navController.navigate(Routes.BROWSER)
                },
                onNavigateToSafeZone = {
                    val activity = context as? androidx.fragment.app.FragmentActivity
                    if (activity != null && com.raouf.grabit.data.security.BiometricHelper.isAvailable(activity)) {
                        com.raouf.grabit.data.security.BiometricHelper.authenticate(
                            activity = activity,
                            title = "Safe Zone",
                            subtitle = "Verify to access hidden downloads",
                            onSuccess = { navController.navigate(Routes.SAFE_ZONE) },
                        )
                    } else {
                        navController.navigate(Routes.SAFE_ZONE)
                    }
                },
                onDownloadClick = { download ->
                    if (!download.isAudioOnly) {
                        val isIncomplete = download.status in listOf(
                            DownloadStatus.DOWNLOADING, DownloadStatus.PAUSED,
                            DownloadStatus.WAITING_NETWORK, DownloadStatus.QUEUED,
                            DownloadStatus.FAILED,
                        )
                        if (isIncomplete) {
                            // Stream from CDN (local file may lack audio track)
                            PlayerActivity.launch(
                                context, download.filePath ?: "", download.title,
                                streaming = true, videoUrl = download.url,
                            )
                        } else if (download.filePath != null) {
                            // Play from local file (completed, has both tracks)
                            PlayerActivity.launch(context, download.filePath, download.title)
                        }
                    }
                },
                onNavigateToPlaylistDetail = { playlistId ->
                    navController.navigate(Routes.playlistDetail(playlistId))
                },
                clipboardUrl = clipboardUrl,
                onDismissClipboard = onDismissClipboard,
            )
        }

        composable(
            route = Routes.PREVIEW,
            arguments = listOf(navArgument("url") { type = NavType.StringType }),
        ) {
            PreviewScreen(
                onBack = { navController.popBackStack() },
                onDownloadStarted = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Routes.PLAYLIST,
            arguments = listOf(navArgument("url") { type = NavType.StringType }),
        ) {
            PlaylistScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.PLAYLIST_DETAIL,
            arguments = listOf(navArgument("playlistId") { type = NavType.StringType }),
        ) {
            val context = LocalContext.current
            PlaylistDetailScreen(
                onBack = { navController.popBackStack() },
                onDownloadClick = { download ->
                    if (!download.isAudioOnly) {
                        val isIncomplete = download.status in listOf(
                            DownloadStatus.DOWNLOADING, DownloadStatus.PAUSED,
                            DownloadStatus.WAITING_NETWORK, DownloadStatus.QUEUED,
                            DownloadStatus.FAILED,
                        )
                        if (isIncomplete) {
                            // Stream from CDN (local file may lack audio track)
                            PlayerActivity.launch(
                                context, download.filePath ?: "", download.title,
                                streaming = true, videoUrl = download.url,
                            )
                        } else if (download.filePath != null) {
                            // Play from local file (completed/failed with partial file)
                            PlayerActivity.launch(context, download.filePath, download.title)
                        }
                    }
                },
            )
        }

        composable(Routes.BROWSER) {
            BrowserScreen(
                onBack = { navController.popBackStack() },
                onDownloadUrl = { url ->
                    navController.navigate(Routes.forUrl(url))
                },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SAFE_ZONE) {
            val context = LocalContext.current
            com.raouf.grabit.ui.safezone.SafeZoneScreen(
                onBack = { navController.popBackStack() },
                onDownloadClick = { download ->
                    if (!download.isAudioOnly && download.filePath != null) {
                        PlayerActivity.launch(context, download.filePath, download.title)
                    }
                },
            )
        }
    }
}
