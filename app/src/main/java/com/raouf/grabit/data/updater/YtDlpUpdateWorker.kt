package com.raouf.grabit.data.updater

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yausername.youtubedl_android.YoutubeDL
import java.util.concurrent.TimeUnit

class YtDlpUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "YtDlpUpdate"
        private const val WORK_NAME = "ytdlp_auto_update"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<YtDlpUpdateWorker>(
                12, TimeUnit.HOURS,
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
            Log.d(TAG, "Scheduled periodic yt-dlp update (12h)")
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val status = YoutubeDL.getInstance().updateYoutubeDL(applicationContext)
            Log.d(TAG, "yt-dlp update result: $status")
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "yt-dlp update failed: ${e.message}")
            Result.retry()
        }
    }
}
