package com.raouf.grabit

import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.raouf.grabit.data.prefs.UserPreferences
import com.raouf.grabit.data.security.BiometricHelper
import com.raouf.grabit.ui.navigation.GrabitNavGraph
import com.raouf.grabit.ui.navigation.Routes
import com.raouf.grabit.ui.theme.GrabitTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var prefs: UserPreferences

    private var sharedUrl by mutableStateOf<String?>(null)
    private var clipboardUrl by mutableStateOf<String?>(null)
    private var isDarkTheme by mutableStateOf(true)
    private var onboardingDone by mutableStateOf(true)
    private var isUnlocked by mutableStateOf(false)
    private var lastClipboardText: String? = null

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

        // Handle share intent
        handleIntent(intent)

        setContent {
            GrabitTheme(darkTheme = isDarkTheme) {
                if (!isUnlocked) {
                    // Show blank screen while waiting for biometric
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxSize()
                            .background(androidx.compose.material3.MaterialTheme.colorScheme.background),
                    )
                    return@GrabitTheme
                }

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
            }
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
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (text != null) {
                        val urlPattern = Regex("https?://\\S+")
                        val match = urlPattern.find(text)
                        sharedUrl = match?.value ?: text
                    }
                }
            }
            Intent.ACTION_VIEW -> {
                // Deep link: URL is in intent.data
                val url = intent.data?.toString()
                if (!url.isNullOrBlank()) {
                    sharedUrl = url
                }
            }
        }
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

            // Check if it looks like a video URL
            val videoHosts = listOf("youtube.com", "youtu.be", "facebook.com", "fb.watch",
                "instagram.com", "tiktok.com", "twitter.com", "x.com", "linkedin.com")
            if (videoHosts.none { it in url.lowercase() }) return@launch

            lastClipboardText = text
            clipboardUrl = url
        }
    }
}
