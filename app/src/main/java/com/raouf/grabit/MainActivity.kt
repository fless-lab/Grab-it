package com.raouf.grabit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.raouf.grabit.data.prefs.UserPreferences
import com.raouf.grabit.ui.navigation.GrabitNavGraph
import com.raouf.grabit.ui.navigation.Routes
import com.raouf.grabit.ui.theme.GrabitTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefs: UserPreferences

    private var sharedUrl by mutableStateOf<String?>(null)
    private var isDarkTheme by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Read theme preference
        lifecycleScope.launch {
            prefs.darkTheme.collect { isDarkTheme = it }
        }

        // Handle share intent
        handleIntent(intent)

        setContent {
            GrabitTheme(darkTheme = isDarkTheme) {
                val navController = rememberNavController()

                GrabitNavGraph(
                    navController = navController,
                    sharedUrl = sharedUrl,
                )

                // If we received a URL via share, navigate to preview
                if (sharedUrl != null) {
                    val url = sharedUrl!!
                    sharedUrl = null
                    navController.navigate(Routes.preview(url)) {
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (text != null) {
                // Extract URL from shared text (some apps include extra text around the URL)
                val urlPattern = Regex("https?://\\S+")
                val match = urlPattern.find(text)
                sharedUrl = match?.value ?: text
            }
        }
    }
}
