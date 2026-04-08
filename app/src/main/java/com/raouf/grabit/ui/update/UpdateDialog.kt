package com.raouf.grabit.ui.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun UpdateBanner(
    visible: Boolean,
    downloading: Boolean,
    ready: Boolean,
    versionName: String,
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            if (downloading) {
                Text(
                    "Downloading v$versionName...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.width(60.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                )
            } else if (ready) {
                Text(
                    "v$versionName ready. Tap to install.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    "v$versionName available. Tap to update.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

fun startApkDownload(context: Context, apkUrl: String, versionName: String): Long {
    // Clean up old APKs
    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
    dir?.listFiles()?.filter { it.name.startsWith("grabit-") && it.name.endsWith(".apk") }
        ?.forEach { it.delete() }

    val request = DownloadManager.Request(Uri.parse(apkUrl))
        .setTitle("Grab'it v$versionName")
        .setDescription("Downloading update...")
        .setDestinationInExternalFilesDir(
            context,
            Environment.DIRECTORY_DOWNLOADS,
            "grabit-$versionName.apk",
        )
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)

    val dm = context.getSystemService(DownloadManager::class.java)
    return dm.enqueue(request)
}

fun installApk(context: Context, downloadId: Long) {
    val dm = context.getSystemService(DownloadManager::class.java)
    val uri = dm.getUriForDownloadedFile(downloadId) ?: return

    val install = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
    context.startActivity(install)
}

fun registerDownloadReceiver(
    context: Context,
    downloadId: Long,
    onComplete: () -> Unit,
): BroadcastReceiver {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == downloadId) onComplete()
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.registerReceiver(
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_EXPORTED,
        )
    } else {
        @Suppress("UnspecifiedRegisterReceiverFlag")
        context.registerReceiver(
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
        )
    }

    return receiver
}
