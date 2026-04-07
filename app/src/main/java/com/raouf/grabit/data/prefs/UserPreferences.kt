package com.raouf.grabit.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "grabit_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val DOWNLOAD_DIR_URI = stringPreferencesKey("download_dir_uri")
        val QUICK_MODE = booleanPreferencesKey("quick_mode")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val AUTO_SUBFOLDER = booleanPreferencesKey("auto_subfolder")
        val CLIPBOARD_MONITOR = booleanPreferencesKey("clipboard_monitor")
        val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val HIDE_FROM_GALLERY = booleanPreferencesKey("hide_from_gallery")
        val SKIPPED_VERSION_TAG = stringPreferencesKey("skipped_version_tag")
        val AUTO_UPDATE = booleanPreferencesKey("auto_update")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
    }

    val downloadDirUri: Flow<String?> = context.dataStore.data.map { it[Keys.DOWNLOAD_DIR_URI] }
    val quickMode: Flow<Boolean> = context.dataStore.data.map { it[Keys.QUICK_MODE] ?: false }
    val darkTheme: Flow<Boolean> = context.dataStore.data.map { it[Keys.DARK_THEME] ?: true }
    val autoSubfolder: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_SUBFOLDER] ?: true }
    val clipboardMonitor: Flow<Boolean> = context.dataStore.data.map { it[Keys.CLIPBOARD_MONITOR] ?: true }
    val defaultQuality: Flow<String> = context.dataStore.data.map { it[Keys.DEFAULT_QUALITY] ?: "best" }
    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }
    val appLock: Flow<Boolean> = context.dataStore.data.map { it[Keys.APP_LOCK] ?: false }
    val hideFromGallery: Flow<Boolean> = context.dataStore.data.map { it[Keys.HIDE_FROM_GALLERY] ?: true }
    val skippedVersionTag: Flow<String> = context.dataStore.data.map { it[Keys.SKIPPED_VERSION_TAG] ?: "" }
    val autoUpdate: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_UPDATE] ?: true }
    val wifiOnly: Flow<Boolean> = context.dataStore.data.map { it[Keys.WIFI_ONLY] ?: false }

    suspend fun setDownloadDir(uri: String) {
        context.dataStore.edit { it[Keys.DOWNLOAD_DIR_URI] = uri }
    }

    suspend fun setQuickMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.QUICK_MODE] = enabled }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setAutoSubfolder(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SUBFOLDER] = enabled }
    }

    suspend fun setClipboardMonitor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CLIPBOARD_MONITOR] = enabled }
    }

    suspend fun setDefaultQuality(quality: String) {
        context.dataStore.edit { it[Keys.DEFAULT_QUALITY] = quality }
    }

    suspend fun setOnboardingDone() {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = true }
    }

    suspend fun setAppLock(enabled: Boolean) {
        context.dataStore.edit { it[Keys.APP_LOCK] = enabled }
    }

    suspend fun setHideFromGallery(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HIDE_FROM_GALLERY] = enabled }
    }

    suspend fun setSkippedVersionTag(tag: String) {
        context.dataStore.edit { it[Keys.SKIPPED_VERSION_TAG] = tag }
    }

    suspend fun setAutoUpdate(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_UPDATE] = enabled }
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WIFI_ONLY] = enabled }
    }
}
