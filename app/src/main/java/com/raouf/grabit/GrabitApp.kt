package com.raouf.grabit

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
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
        private const val TAG = "GrabitApp"
        val ytdlReady = CompletableDeferred<Boolean>()
    }

    override fun onCreate() {
        super.onCreate()

        appScope.launch {
            try {
                Log.d(TAG, "Initializing yt-dlp...")
                YoutubeDL.getInstance().init(this@GrabitApp)
                Log.d(TAG, "yt-dlp initialized successfully")

                Log.d(TAG, "Initializing FFmpeg...")
                FFmpeg.getInstance().init(this@GrabitApp)
                Log.d(TAG, "FFmpeg initialized successfully")
                ytdlReady.complete(true)
            } catch (e: Exception) {
                Log.e(TAG, "yt-dlp init failed: ${e.message}", e)
                ytdlReady.complete(false)
            }

            // Try to update extractors (non-blocking)
            try {
                YoutubeDL.getInstance().updateYoutubeDL(this@GrabitApp)
                Log.d(TAG, "yt-dlp updated")
            } catch (e: Exception) {
                Log.w(TAG, "yt-dlp update skipped: ${e.message}")
            }

            // Schedule periodic updates
            com.raouf.grabit.data.updater.YtDlpUpdateWorker.schedule(this@GrabitApp)
        }
    }
}
