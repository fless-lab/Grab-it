package com.raouf.grabit.ui.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.raouf.grabit.GrabitApp
import com.raouf.grabit.R
import com.raouf.grabit.ui.theme.GrabitTheme
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PlayerActivity : ComponentActivity() {

    companion object {
        private const val TAG = "PlayerActivity"
        private const val ACTION_PIP_PLAY = "com.raouf.grabit.PIP_PLAY"
        private const val ACTION_PIP_PAUSE = "com.raouf.grabit.PIP_PAUSE"
        private const val ACTION_PIP_FORWARD = "com.raouf.grabit.PIP_FORWARD"
        private const val ACTION_PIP_REWIND = "com.raouf.grabit.PIP_REWIND"
        private const val SKIP_MS = 10_000L

        /**
         * Launch player for a completed/local file.
         */
        fun launch(context: Context, filePath: String, title: String, streaming: Boolean = false, videoUrl: String? = null) {
            context.startActivity(Intent(context, PlayerActivity::class.java).apply {
                putExtra("filePath", filePath)
                putExtra("title", title)
                putExtra("streaming", streaming)
                if (videoUrl != null) putExtra("videoUrl", videoUrl)
            })
        }
    }

    private var exoPlayer: ExoPlayer? = null
    private var isInPip by mutableStateOf(false)
    private var playbackError by mutableStateOf<String?>(null)
    private var isLoading by mutableStateOf(false)
    private var videoAspectRatio = Rational(16, 9)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PIP_PLAY -> exoPlayer?.play()
                ACTION_PIP_PAUSE -> exoPlayer?.pause()
                ACTION_PIP_FORWARD -> exoPlayer?.let {
                    it.seekTo((it.currentPosition + SKIP_MS).coerceAtMost(it.duration))
                }
                ACTION_PIP_REWIND -> exoPlayer?.let {
                    it.seekTo((it.currentPosition - SKIP_MS).coerceAtLeast(0))
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                updatePipParams()
            }
        }
    }

    @kotlin.OptIn(ExperimentalMaterial3Api::class)
    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val filePath = intent.getStringExtra("filePath") ?: run { finish(); return }
        val title = intent.getStringExtra("title") ?: "Video"
        val streaming = intent.getBooleanExtra("streaming", false)
        val videoUrl = intent.getStringExtra("videoUrl")

        // Register PiP action receiver
        val filter = IntentFilter().apply {
            addAction(ACTION_PIP_PLAY)
            addAction(ACTION_PIP_PAUSE)
            addAction(ACTION_PIP_FORWARD)
            addAction(ACTION_PIP_REWIND)
        }
        registerReceiver(pipReceiver, filter, RECEIVER_NOT_EXPORTED)

        // Track if this is a fallback attempt (local file after CDN failed)
        var isFallbackAttempt = false

        // Create player
        val player = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    if (isFallbackAttempt) {
                        // Local file also failed: show toast and close
                        Toast.makeText(
                            this@PlayerActivity,
                            "Video not playable yet. Try again later.",
                            Toast.LENGTH_SHORT,
                        ).show()
                        finish()
                    } else {
                        playbackError = "Playback error: ${error.localizedMessage}"
                    }
                }
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        videoAspectRatio = Rational(videoSize.width, videoSize.height)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            updatePipParams()
                        }
                    }
                }
            })
        }
        exoPlayer = player

        if (streaming && videoUrl != null) {
            // Streaming mode: extract direct CDN URL, then stream
            isLoading = true
            scope.launch {
                try {
                    val streamUrl = extractStreamUrl(videoUrl)
                    player.setMediaItem(MediaItem.fromUri(Uri.parse(streamUrl)))
                    player.prepare()
                    player.playWhenReady = true
                    isLoading = false
                } catch (e: Exception) {
                    Log.e(TAG, "Stream URL extraction failed: ${e.message}")
                    // Fallback: try local file (works for direct downloads, e.g. 87% of a non-DASH video)
                    if (filePath.isNotBlank()) {
                        isFallbackAttempt = true
                        val uri = buildFileUri(filePath)
                        player.setMediaItem(MediaItem.fromUri(uri))
                        player.prepare()
                        player.playWhenReady = true
                        isLoading = false
                    } else {
                        isLoading = false
                        Toast.makeText(
                            this@PlayerActivity,
                            "No connection. Video not available offline yet.",
                            Toast.LENGTH_SHORT,
                        ).show()
                        finish()
                    }
                }
            }
        } else {
            // Normal mode: play from local file
            val uri = buildFileUri(filePath)
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.playWhenReady = true
        }

        // Auto-enter PiP on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setPictureInPictureParams(
                PictureInPictureParams.Builder()
                    .setAspectRatio(videoAspectRatio)
                    .setAutoEnterEnabled(true)
                    .setActions(buildPipActions())
                    .build()
            )
        }

        setContent {
            GrabitTheme(darkTheme = true) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .then(if (!isInPip) Modifier.statusBarsPadding() else Modifier),
                ) {
                    // TopAppBar: hidden in PiP
                    if (!isInPip) {
                        TopAppBar(
                            title = {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    androidx.compose.material3.Icon(
                                        Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White,
                                    )
                                }
                            },
                            actions = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    IconButton(onClick = { enterPip() }) {
                                        androidx.compose.material3.Icon(
                                            Icons.Rounded.PictureInPictureAlt,
                                            contentDescription = "Picture in Picture",
                                            tint = Color.White,
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Black,
                            ),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            isLoading -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(32.dp),
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        "Loading stream...",
                                        color = Color.White.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                            playbackError != null && !isInPip -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(32.dp),
                                ) {
                                    androidx.compose.material3.Icon(
                                        Icons.Rounded.ErrorOutline,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(48.dp),
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        text = playbackError ?: "",
                                        color = Color.White.copy(alpha = 0.8f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        TextButton(
                                            onClick = {
                                                playbackError = null
                                                player.prepare()
                                                player.playWhenReady = true
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                        ) {
                                            Text("Retry", color = Color.White)
                                        }
                                        TextButton(
                                            onClick = { finish() },
                                            shape = RoundedCornerShape(8.dp),
                                        ) {
                                            Text("Go back", color = Color.White.copy(alpha = 0.6f))
                                        }
                                    }
                                }
                            }
                            else -> {
                                AndroidView(
                                    factory = { ctx ->
                                        PlayerView(ctx).apply {
                                            this.player = player
                                            useController = !isInPip
                                            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                            setBackgroundColor(Color.Black.toArgb())
                                            setShutterBackgroundColor(Color.Black.toArgb())
                                        }
                                    },
                                    update = { view ->
                                        view.useController = !isInPip
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun buildFileUri(filePath: String): Uri {
        return if (filePath.startsWith("/")) {
            Uri.fromFile(File(filePath))
        } else {
            Uri.parse(filePath)
        }
    }

    /**
     * Extract a direct streaming URL from yt-dlp for live playback.
     * Uses a muxed format (video+audio in one stream) so ExoPlayer plays it directly.
     */
    private suspend fun extractStreamUrl(videoUrl: String): String = withContext(Dispatchers.IO) {
        GrabitApp.ytdlReady.await()
        Log.d(TAG, "Extracting stream URL for: $videoUrl")

        val request = YoutubeDLRequest(videoUrl)
        request.addOption("--get-url")
        request.addOption("-f", "best[ext=mp4]/best")
        request.addOption("--no-playlist")
        request.addOption("--no-check-certificates")

        val response = YoutubeDL.getInstance().execute(request)
        val url = response.out?.trim()?.lines()?.firstOrNull()

        if (url.isNullOrBlank()) {
            throw Exception("No stream URL found")
        }

        Log.d(TAG, "Stream URL extracted (${url.length} chars)")
        url
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun enterPip() {
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(videoAspectRatio)
            .setActions(buildPipActions())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setAutoEnterEnabled(true)
        }
        enterPictureInPictureMode(params.build())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updatePipParams() {
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(videoAspectRatio)
            .setActions(buildPipActions())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setAutoEnterEnabled(true)
        }
        setPictureInPictureParams(params.build())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipActions(): List<RemoteAction> {
        val isPlaying = exoPlayer?.isPlaying == true

        val rewindAction = RemoteAction(
            Icon.createWithResource(this, R.drawable.ic_pip_rewind),
            "Rewind", "Rewind 10 seconds",
            PendingIntent.getBroadcast(this, 0, Intent(ACTION_PIP_REWIND), PendingIntent.FLAG_IMMUTABLE),
        )

        val playPauseAction = if (isPlaying) {
            RemoteAction(
                Icon.createWithResource(this, R.drawable.ic_pip_pause),
                "Pause", "Pause playback",
                PendingIntent.getBroadcast(this, 1, Intent(ACTION_PIP_PAUSE), PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            RemoteAction(
                Icon.createWithResource(this, R.drawable.ic_pip_play),
                "Play", "Resume playback",
                PendingIntent.getBroadcast(this, 2, Intent(ACTION_PIP_PLAY), PendingIntent.FLAG_IMMUTABLE),
            )
        }

        val forwardAction = RemoteAction(
            Icon.createWithResource(this, R.drawable.ic_pip_forward),
            "Forward", "Forward 10 seconds",
            PendingIntent.getBroadcast(this, 3, Intent(ACTION_PIP_FORWARD), PendingIntent.FLAG_IMMUTABLE),
        )

        return listOf(rewindAction, playPauseAction, forwardAction)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.S) {
            if (exoPlayer?.isPlaying == true) {
                enterPip()
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPip = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        if (isInPip) {
            exoPlayer?.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
        scope.cancel()
        try { unregisterReceiver(pipReceiver) } catch (_: Exception) { }
    }
}
