package com.raouf.grabit

import android.app.Application
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class GrabitApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        /** Await this before using YoutubeDL */
        val ytdlReady = CompletableDeferred<Boolean>()
    }

    override fun onCreate() {
        super.onCreate()

        appScope.launch {
            try {
                YoutubeDL.getInstance().init(this@GrabitApp)
                ytdlReady.complete(true)
            } catch (e: YoutubeDLException) {
                e.printStackTrace()
                ytdlReady.complete(false)
            }

            // Try to update extractors (non-blocking)
            try {
                YoutubeDL.getInstance().updateYoutubeDL(this@GrabitApp)
            } catch (_: Exception) { }
        }
    }
}
