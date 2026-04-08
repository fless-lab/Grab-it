package com.raouf.grabit

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.raouf.grabit.data.prefs.UserPreferences
import com.raouf.grabit.data.security.BiometricHelper
import com.raouf.grabit.data.updater.AppUpdate
import com.raouf.grabit.data.updater.AppUpdateChecker
import com.raouf.grabit.ui.navigation.GrabitNavGraph
import com.raouf.grabit.ui.navigation.Routes
import com.raouf.grabit.ui.theme.GrabitTheme
import com.raouf.grabit.ui.update.UpdateBanner
import com.raouf.grabit.ui.update.installApk
import com.raouf.grabit.ui.update.registerDownloadReceiver
import com.raouf.grabit.ui.update.startApkDownload
import android.widget.Toast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var prefs: UserPreferences
    @Inject lateinit var updateChecker: AppUpdateChecker

    private var sharedUrl by mutableStateOf<String?>(null)
    private var clipboardUrl by mutableStateOf<String?>(null)
    private var isDarkTheme by mutableStateOf(true)
    private var onboardingDone by mutableStateOf(true)
    private var isUnlocked by mutableStateOf(false)
    private var lastClipboardText: String? = null

    // Auto-update state
    private var pendingUpdate by mutableStateOf<AppUpdate?>(null)
    private var updateDownloading by mutableStateOf(false)
    private var updateReady by mutableStateOf(false)
    private var updateProgress by mutableFloatStateOf(0f)
    private var updateDownloadId by mutableLongStateOf(-1L)
    private var downloadReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // App lock check
        val appLockEnabled = runBlocking { prefs.appLock.first() }
        if (appLockEnabled && BiometricHelper.isAvailable(this)) {
            isUnlocked = false
            BiometricHelper.authenticate(
                activity = this,
                title = "Unlock Grab'it",
                subtitle = "Verify to access your downloads",
                onSuccess = { isUnlocked = true },
                onError = { finish() },
            )
        } else {
            isUnlocked = true
        }

        // Read initial state synchronously to avoid UI flash
        runBlocking {
            onboardingDone = prefs.onboardingDone.first()
            isDarkTheme = prefs.darkTheme.first()
        }

        // Keep collecting theme changes
        lifecycleScope.launch {
            prefs.darkTheme.collect { isDarkTheme = it }
        }

        // Update check
        lifecycleScope.launch(Dispatchers.IO) {
            // 1. Check for new version from server
            val latestUpdate = updateChecker.check()

            // 2. Check if a previously downloaded APK is still valid
            val savedDownloadId = prefs.pendingUpdateDownloadId.first()
            val savedVersion = prefs.pendingUpdateVersion.first()
            var savedApkValid = false

            if (savedDownloadId > 0 && savedVersion.isNotBlank()) {
                // If a newer version exists, discard old APK
                if (latestUpdate != null && latestUpdate.versionName != savedVersion) {
                    val dm = getSystemService(DownloadManager::class.java)
                    dm.remove(savedDownloadId)
                    prefs.clearPendingUpdate()
                } else {
                    // Check if APK file still exists
                    val dm = getSystemService(DownloadManager::class.java)
                    val query = DownloadManager.Query().setFilterById(savedDownloadId)
                    val cursor = dm.query(query)
                    if (cursor != null && cursor.moveToFirst()) {
                        val status = cursor.getInt(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS),
                        )
                        val localUri = cursor.getString(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI),
                        )
                        cursor.close()
                        if (status == DownloadManager.STATUS_SUCCESSFUL && localUri != null) {
                            // Verify file actually exists on disk
                            val file = try { java.io.File(Uri.parse(localUri).path!!) } catch (_: Exception) { null }
                            if (file?.exists() == true) {
                                pendingUpdate = AppUpdate(savedVersion, "", "")
                                updateDownloadId = savedDownloadId
                                updateReady = true
                                savedApkValid = true
                            } else {
                                // File was deleted manually
                                dm.remove(savedDownloadId)
                                prefs.clearPendingUpdate()
                            }
                        } else {
                            cursor.close()
                            prefs.clearPendingUpdate()
                        }
                    } else {
                        cursor?.close()
                        prefs.clearPendingUpdate()
                    }
                }
            }

            // 3. If saved APK is still valid, we're done
            if (savedApkValid) return@launch

            // 4. No valid saved APK, use latest from server
            val update = latestUpdate ?: return@launch
            pendingUpdate = update

            val autoUpdateEnabled = prefs.autoUpdate.first()
            if (!autoUpdateEnabled) return@launch // show banner only

            // 5. Auto-download
            updateDownloading = true
            updateDownloadId = startApkDownload(
                this@MainActivity, update.apkUrl, update.versionName,
            )
            downloadReceiver = registerDownloadReceiver(
                this@MainActivity, updateDownloadId,
            ) {
                updateDownloading = false
                updateReady = true
                lifecycleScope.launch(Dispatchers.IO) {
                    prefs.setPendingUpdate(updateDownloadId, update.versionName)
                }
            }
            // Poll progress
            val dm = getSystemService(DownloadManager::class.java)
            while (updateDownloading) {
                val query = DownloadManager.Query().setFilterById(updateDownloadId)
                val cursor = dm.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val downloaded = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                    )
                    val total = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
                    )
                    if (total > 0) updateProgress = downloaded.toFloat() / total
                    cursor.close()
                }
                delay(500)
            }
        }

        // Handle share intent
        handleIntent(intent)

        setContent {
            GrabitTheme(darkTheme = isDarkTheme) {
                if (!isUnlocked) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    )
                    return@GrabitTheme
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val startDest = if (onboardingDone) Routes.HOME else Routes.ONBOARDING

                    GrabitNavGraph(
                        navController = navController,
                        sharedUrl = sharedUrl,
                        onSharedUrlConsumed = { sharedUrl = null },
                        clipboardUrl = clipboardUrl,
                        onDismissClipboard = { clipboardUrl = null },
                        startDestination = startDest,
                        onOnboardingComplete = { folderUri ->
                            lifecycleScope.launch {
                                if (folderUri != null) {
                                    prefs.setDownloadDir(folderUri)
                                }
                                prefs.setOnboardingDone()
                                onboardingDone = true
                            }
                        },
                    )

                    // Update banner at bottom
                    val update = pendingUpdate
                    if (update != null) {
                        UpdateBanner(
                            visible = true,
                            downloading = updateDownloading,
                            ready = updateReady,
                            versionName = update.versionName,
                            progress = updateProgress,
                            onClick = {
                                if (updateReady) {
                                    installApk(this@MainActivity, updateDownloadId)
                                    // Don't clear pending update here:
                                    // if install fails (signature mismatch), user can retry next launch
                                } else if (!updateDownloading && update.apkUrl.isNotBlank()) {
                                    // Manual download (auto-update off)
                                    updateDownloading = true
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        updateDownloadId = startApkDownload(
                                            this@MainActivity, update.apkUrl, update.versionName,
                                        )
                                        downloadReceiver = registerDownloadReceiver(
                                            this@MainActivity, updateDownloadId,
                                        ) {
                                            updateDownloading = false
                                            updateReady = true
                                            lifecycleScope.launch(Dispatchers.IO) {
                                                prefs.setPendingUpdate(updateDownloadId, update.versionName)
                                            }
                                        }
                                        val dm = getSystemService(DownloadManager::class.java)
                                        while (updateDownloading) {
                                            val query = DownloadManager.Query().setFilterById(updateDownloadId)
                                            val cursor = dm.query(query)
                                            if (cursor != null && cursor.moveToFirst()) {
                                                val downloaded = cursor.getLong(
                                                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                                                )
                                                val total = cursor.getLong(
                                                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
                                                )
                                                if (total > 0) updateProgress = downloaded.toFloat() / total
                                                cursor.close()
                                            }
                                            delay(500)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .navigationBarsPadding(),
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) checkClipboard()
    }

    private fun handleIntent(intent: Intent?) {
        val url: String? = when (intent?.action) {
            // ACTION_SEND is routed through ShareReceiverActivity (handles quick mode there)
            // but arrives here when quick mode is OFF (forwarded by ShareReceiverActivity)
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (text != null) {
                        val urlPattern = Regex("https?://\\S+")
                        urlPattern.find(text)?.value ?: text
                    } else null
                } else null
            }
            // Deep links (ACTION_VIEW) come directly to MainActivity
            Intent.ACTION_VIEW -> {
                val deepUrl = intent.data?.toString()
                if (!deepUrl.isNullOrBlank()) {
                    val quickMode = runBlocking { prefs.quickMode.first() }
                    if (quickMode) {
                        Toast.makeText(this, "Downloading...", Toast.LENGTH_SHORT).show()
                        com.raouf.grabit.service.DownloadService.quickDownload(this, deepUrl)
                        return
                    }
                }
                deepUrl
            }
            else -> null
        }

        if (url.isNullOrBlank()) return
        sharedUrl = url
    }

    private fun checkClipboard() {
        lifecycleScope.launch {
            val enabled = prefs.clipboardMonitor.first()
            if (!enabled) return@launch

            val cm = getSystemService(ClipboardManager::class.java)
            val clip = cm.primaryClip ?: return@launch
            if (clip.itemCount == 0) return@launch

            val text = clip.getItemAt(0).text?.toString() ?: return@launch
            if (text == lastClipboardText) return@launch

            val urlPattern = Regex("https?://\\S+")
            val match = urlPattern.find(text) ?: return@launch
            val url = match.value

            val videoHosts = listOf("youtube.com", "youtu.be", "facebook.com", "fb.watch",
                "instagram.com", "tiktok.com", "twitter.com", "x.com", "linkedin.com")
            if (videoHosts.none { it in url.lowercase() }) return@launch

            lastClipboardText = text
            clipboardUrl = url
        }
    }
}
