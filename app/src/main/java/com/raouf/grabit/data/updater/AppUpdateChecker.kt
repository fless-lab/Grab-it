package com.raouf.grabit.data.updater

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.raouf.grabit.BuildConfig
import com.raouf.grabit.data.prefs.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class AppUpdate(
    val versionName: String,
    val apkUrl: String,
    val changelog: String,
)

/** GitHub Releases API response (only fields we need) */
private data class GithubRelease(
    @SerializedName("tag_name") val tagName: String,
    val name: String?,
    val body: String?,
    val assets: List<GithubAsset>,
)

private data class GithubAsset(
    val name: String,
    @SerializedName("browser_download_url") val downloadUrl: String,
)

@Singleton
class AppUpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: UserPreferences,
) {
    companion object {
        private const val TAG = "AppUpdateChecker"
        private const val RELEASES_URL =
            "https://api.github.com/repos/fless-lab/Grab-it/releases/latest"
    }

    /**
     * Checks the latest GitHub release. Returns AppUpdate if a newer version is available.
     * Compares the release tag (e.g. "v1.1.0") with BuildConfig.VERSION_NAME.
     * APK is auto-detected from release assets (first .apk file).
     */
    suspend fun check(): AppUpdate? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(RELEASES_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github+json")

            if (conn.responseCode != 200) {
                Log.w(TAG, "Release check HTTP ${conn.responseCode}")
                return@withContext null
            }

            val json = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val release = Gson().fromJson(json, GithubRelease::class.java)

            // Extract version from tag (strip leading "v" if present)
            val remoteVersion = release.tagName.removePrefix("v")
            val localVersion = BuildConfig.VERSION_NAME

            if (!isNewer(remoteVersion, localVersion)) {
                Log.d(TAG, "App is up to date ($localVersion)")
                return@withContext null
            }

            // Find APK asset
            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
            if (apkAsset == null) {
                Log.w(TAG, "No APK found in release assets")
                return@withContext null
            }

            // Check if user skipped this version
            val skippedTag = prefs.skippedVersionTag.first()
            if (skippedTag == remoteVersion) {
                Log.d(TAG, "User skipped version $remoteVersion")
                return@withContext null
            }

            Log.d(TAG, "New version available: $remoteVersion (current: $localVersion)")
            AppUpdate(
                versionName = remoteVersion,
                apkUrl = apkAsset.downloadUrl,
                changelog = release.body ?: release.name ?: "New version available",
            )
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed: ${e.message}")
            null
        }
    }

    /** Compare semver strings: "1.2.0" > "1.1.0" */
    private fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split(".").mapNotNull { it.toIntOrNull() }
        val l = local.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv > lv) return true
            if (rv < lv) return false
        }
        return false
    }
}
