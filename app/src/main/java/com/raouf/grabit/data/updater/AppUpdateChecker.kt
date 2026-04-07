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
    @SerializedName("version_code") val versionCode: Int,
    @SerializedName("version_name") val versionName: String,
    @SerializedName("apk_url") val apkUrl: String,
    val changelog: String,
)

@Singleton
class AppUpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: UserPreferences,
) {
    companion object {
        private const val TAG = "AppUpdateChecker"
        private const val VERSION_URL =
            "https://raw.githubusercontent.com/fless-lab/Grab-it/main/version.json"
    }

    /**
     * Checks for a new app version. Returns AppUpdate if a newer version is available,
     * null if already up to date or if user skipped this version.
     */
    suspend fun check(): AppUpdate? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(VERSION_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.requestMethod = "GET"

            if (conn.responseCode != 200) {
                Log.w(TAG, "Version check HTTP ${conn.responseCode}")
                return@withContext null
            }

            val json = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val update = Gson().fromJson(json, AppUpdate::class.java)

            if (update.versionCode <= BuildConfig.VERSION_CODE) {
                Log.d(TAG, "App is up to date (${BuildConfig.VERSION_CODE})")
                return@withContext null
            }

            // Check if user skipped this version
            val skipped = prefs.skippedVersion.first()
            if (skipped == update.versionCode) {
                Log.d(TAG, "User skipped version ${update.versionCode}")
                return@withContext null
            }

            Log.d(TAG, "New version available: ${update.versionName} (${update.versionCode})")
            update
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed: ${e.message}")
            null
        }
    }
}
