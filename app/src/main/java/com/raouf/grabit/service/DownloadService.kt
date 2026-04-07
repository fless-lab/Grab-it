package com.raouf.grabit.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.raouf.grabit.MainActivity
import com.raouf.grabit.R
import com.raouf.grabit.data.DownloadRepository
import com.raouf.grabit.data.downloader.GrabitDownloadManager
import com.raouf.grabit.domain.model.VideoSource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService : Service() {

    @Inject lateinit var downloadManager: GrabitDownloadManager
    @Inject lateinit var repository: DownloadRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeDownloads = mutableSetOf<Long>()

    companion object {
        const val CHANNEL_ID = "grabit_downloads"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.raouf.grabit.START_DOWNLOAD"
        const val ACTION_CANCEL = "com.raouf.grabit.CANCEL_DOWNLOAD"

        const val EXTRA_ID = "download_id"
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_FORMAT = "format_id"
        const val EXTRA_AUDIO_ONLY = "audio_only"

        fun startDownload(
            context: Context,
            id: Long,
            url: String,
            title: String,
            source: VideoSource,
            formatId: String,
            isAudioOnly: Boolean,
        ) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ID, id)
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SOURCE, source.name)
                putExtra(EXTRA_FORMAT, formatId)
                putExtra(EXTRA_AUDIO_ONLY, isAudioOnly)
            }
            context.startForegroundService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Preparing download..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val id = intent.getLongExtra(EXTRA_ID, -1)
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Video"
                val sourceName = intent.getStringExtra(EXTRA_SOURCE) ?: "OTHER"
                val source = try { VideoSource.valueOf(sourceName) } catch (_: Exception) { VideoSource.OTHER }
                val formatId = intent.getStringExtra(EXTRA_FORMAT) ?: "best"
                val isAudioOnly = intent.getBooleanExtra(EXTRA_AUDIO_ONLY, false)

                if (id > 0 && id !in activeDownloads) {
                    activeDownloads.add(id)
                    scope.launch {
                        updateNotification("Downloading: $title")
                        downloadManager.startDownload(
                            id = id,
                            url = url,
                            title = title,
                            source = source,
                            formatId = formatId,
                            isAudioOnly = isAudioOnly,
                        ) { progress ->
                            updateNotification("${(progress * 100).toInt()}% - $title", progress)
                        }
                        activeDownloads.remove(id)
                        if (activeDownloads.isEmpty()) {
                            updateNotification("All downloads complete")
                            stopSelf()
                        }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Downloads",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Video download progress"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String, progress: Float? = null): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Grab'it")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .apply {
                if (progress != null) {
                    setProgress(100, (progress * 100).toInt(), false)
                }
            }
            .build()
    }

    private fun updateNotification(text: String, progress: Float? = null) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text, progress))
    }
}
