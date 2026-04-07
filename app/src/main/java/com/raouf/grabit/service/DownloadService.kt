package com.raouf.grabit.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.raouf.grabit.MainActivity
import com.raouf.grabit.R
import com.raouf.grabit.data.DownloadRepository
import com.raouf.grabit.data.downloader.GrabitDownloadManager
import com.raouf.grabit.data.downloader.VideoExtractor
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
    @Inject lateinit var extractor: VideoExtractor

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeDownloads = mutableSetOf<Long>()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    companion object {
        private const val TAG = "DownloadService"
        const val CHANNEL_ID = "grabit_downloads"
        const val CHANNEL_ERRORS = "grabit_errors"
        const val NOTIFICATION_ID = 1001
        const val NOTIFICATION_ERROR_ID = 1002
        const val MAX_CONCURRENT = 3

        const val ACTION_START = "com.raouf.grabit.START_DOWNLOAD"
        const val ACTION_QUICK = "com.raouf.grabit.QUICK_DOWNLOAD"
        const val ACTION_PAUSE = "com.raouf.grabit.PAUSE_DOWNLOAD"
        const val ACTION_PAUSE_ALL = "com.raouf.grabit.PAUSE_ALL"
        const val ACTION_CANCEL = "com.raouf.grabit.CANCEL_DOWNLOAD"

        const val EXTRA_ID = "download_id"
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_FORMAT = "format_id"
        const val EXTRA_AUDIO_ONLY = "audio_only"
        const val EXTRA_SUB_LANGS = "sub_langs"

        fun startDownload(
            context: Context,
            id: Long,
            url: String,
            title: String,
            source: VideoSource,
            formatId: String,
            isAudioOnly: Boolean,
            subLangs: String = "",
        ) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ID, id)
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SOURCE, source.name)
                putExtra(EXTRA_FORMAT, formatId)
                putExtra(EXTRA_AUDIO_ONLY, isAudioOnly)
                putExtra(EXTRA_SUB_LANGS, subLangs)
            }
            context.startForegroundService(intent)
        }

        fun quickDownload(context: Context, url: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_QUICK
                putExtra(EXTRA_URL, url)
            }
            context.startForegroundService(intent)
        }

        fun pauseDownload(context: Context, id: Long) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_ID, id)
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Preparing download..."))
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_QUICK -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                scope.launch {
                    updateNotification("Downloading...")

                    // Try extraction up to 3 times silently
                    var lastError: Exception? = null
                    var success = false
                    for (attempt in 1..3) {
                        try {
                            val info = extractor.extract(url, forceRefresh = attempt > 1)
                            repository.createDownload(
                                url = info.url,
                                title = info.title,
                                thumbnail = info.thumbnail,
                                source = info.source,
                                isAudioOnly = false,
                                quality = "Best",
                                formatId = "bestvideo+bestaudio/best",
                            )
                            success = true
                            break
                        } catch (e: Exception) {
                            lastError = e
                            if (attempt < 3) kotlinx.coroutines.delay(3000)
                        }
                    }

                    if (success) {
                        processQueue()
                    } else {
                        Log.e(TAG, "Quick download failed: ${lastError?.message}", lastError)
                        showErrorNotification(
                            "Download failed: ${com.raouf.grabit.data.downloader.ErrorParser.friendlyMessage(lastError?.message)}"
                        )
                        if (activeDownloads.isEmpty()) stopSelf()
                    }
                }
            }

            ACTION_START -> {
                val id = intent.getLongExtra(EXTRA_ID, -1)
                if (id > 0 && id !in activeDownloads) {
                    scope.launch { processQueue() }
                }
            }

            ACTION_PAUSE -> {
                val id = intent.getLongExtra(EXTRA_ID, -1)
                if (id > 0) {
                    downloadManager.pauseDownload(id)
                    activeDownloads.remove(id)
                    if (activeDownloads.isEmpty()) {
                        updateNotification("Downloads paused")
                        stopSelf()
                    } else {
                        scope.launch { processQueue() }
                    }
                }
            }

            ACTION_PAUSE_ALL -> {
                val ids = activeDownloads.toList()
                for (id in ids) {
                    downloadManager.pauseDownload(id)
                    activeDownloads.remove(id)
                }
                updateNotification("All downloads paused")
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterNetworkCallback()
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Core queue logic: start queued downloads up to MAX_CONCURRENT.
     */
    private suspend fun processQueue() {
        val queued = repository.getQueued()
        for (dl in queued) {
            if (activeDownloads.size >= MAX_CONCURRENT) {
                Log.d(TAG, "Max concurrent reached (${activeDownloads.size}), ${dl.title} stays queued")
                break
            }
            if (dl.id in activeDownloads) continue
            launchDownload(dl.id, dl.url, dl.title, dl.source, dl.formatId, dl.isAudioOnly, dl.subLangs)
        }
        if (activeDownloads.isEmpty() && queued.isEmpty()) {
            updateNotification("All downloads complete")
            stopSelf()
        }
    }

    private fun launchDownload(
        id: Long,
        url: String,
        title: String,
        source: VideoSource,
        formatId: String,
        isAudioOnly: Boolean,
        subLangs: String? = null,
    ) {
        // Fast-fail if no network
        if (!isNetworkAvailable()) {
            scope.launch {
                repository.setWaitingNetwork(id)
                updateNotification("No connection - waiting for network")
            }
            return
        }

        activeDownloads.add(id)
        scope.launch {
            updateNotification("Downloading: $title")
            val result = downloadManager.startDownload(
                id = id,
                url = url,
                title = title,
                source = source,
                formatId = formatId,
                isAudioOnly = isAudioOnly,
                subLangs = subLangs,
            ) { progress ->
                updateNotification("${(progress * 100).toInt()}% - $title", progress)
            }
            activeDownloads.remove(id)

            if (result.isFailure) {
                // Don't notify for paused (user action) or waiting_network (auto-resumes)
                val current = repository.getById(id)
                val isPaused = current?.status == com.raouf.grabit.domain.model.DownloadStatus.PAUSED
                val isWaiting = current?.status == com.raouf.grabit.domain.model.DownloadStatus.WAITING_NETWORK
                if (!isPaused && !isWaiting) {
                    val msg = com.raouf.grabit.data.downloader.ErrorParser.friendlyMessage(
                        result.exceptionOrNull()?.message
                    )
                    showErrorNotification("$title: $msg")
                }
            }

            // When one finishes, start next in queue
            processQueue()
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun registerNetworkCallback() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available, checking paused downloads")
                scope.launch { resumeWaitingNetwork() }
            }
        }
        cm.registerNetworkCallback(request, networkCallback!!)
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let {
            val cm = getSystemService(ConnectivityManager::class.java)
            try { cm.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
        networkCallback = null
    }

    private suspend fun resumeWaitingNetwork() {
        val waiting = repository.getWaitingNetwork()
        for (dl in waiting) {
            if (dl.id !in activeDownloads) {
                Log.d(TAG, "Auto-resuming download ${dl.id}: ${dl.title}")
                // Reset to QUEUED so processQueue picks it up respecting the limit
                repository.resetForRetry(dl.id)
            }
        }
        processQueue()
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Video download progress"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ERRORS, "Download errors", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Download failure alerts"
            }
        )
    }

    private fun buildNotification(text: String, progress: Float? = null, showActions: Boolean = false): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Grab'it")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)

        if (progress != null) {
            builder.setProgress(100, (progress * 100).toInt(), false)
        }

        if (showActions && activeDownloads.isNotEmpty()) {
            val pauseIntent = PendingIntent.getService(
                this, 1,
                Intent(this, DownloadService::class.java).apply { action = ACTION_PAUSE_ALL },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(R.drawable.ic_download, "Pause All", pauseIntent)
        }

        return builder.build()
    }

    private fun updateNotification(text: String, progress: Float? = null) {
        val showActions = progress != null && activeDownloads.isNotEmpty()
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text, progress, showActions))
    }

    /** Separate persistent notification for errors (survives service stop, pops up visibly) */
    private fun showErrorNotification(text: String) {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ERRORS)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Grab'it")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ERROR_ID, notification)
    }
}
