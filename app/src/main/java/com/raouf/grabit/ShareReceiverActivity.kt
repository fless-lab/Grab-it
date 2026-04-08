package com.raouf.grabit

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.raouf.grabit.data.prefs.UserPreferences
import com.raouf.grabit.service.DownloadService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    @Inject lateinit var prefs: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = extractUrl(intent)
        if (url == null) {
            finish()
            return
        }

        Log.d("ShareReceiver", "URL received: $url")

        val quickMode = runBlocking { prefs.quickMode.first() }
        Log.d("ShareReceiver", "Quick mode: $quickMode")

        if (quickMode) {
            Toast.makeText(this, "Grab'it: downloading...", Toast.LENGTH_SHORT).show()
            DownloadService.quickDownload(this, url)
            // Delay finish so the service has time to call startForeground (Android 12+ requirement)
            window.decorView.postDelayed({ finish() }, 800)
        } else {
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(mainIntent)
            finish()
        }
    }

    private fun extractUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        val urlPattern = Regex("https?://\\S+")
        val match = urlPattern.find(text)
        return match?.value ?: text
    }
}
