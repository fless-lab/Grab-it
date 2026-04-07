package com.raouf.grabit

import android.app.Application
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class GrabitApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Initialize yt-dlp on a background thread
        appScope.launch {
            try {
                YoutubeDL.getInstance().init(this@GrabitApp)
            } catch (e: YoutubeDLException) {
                e.printStackTrace()
            }

            // Try to update yt-dlp extractors
            try {
                YoutubeDL.getInstance().updateYoutubeDL(this@GrabitApp)
            } catch (_: Exception) { }
        }
    }
}
