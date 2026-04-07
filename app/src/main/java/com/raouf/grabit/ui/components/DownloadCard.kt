package com.raouf.grabit.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.raouf.grabit.domain.model.Download
import com.raouf.grabit.domain.model.DownloadStatus
import com.raouf.grabit.ui.theme.StatusError
import com.raouf.grabit.ui.theme.StatusWarning

@Composable
fun DownloadCard(
    download: Download,
    etaSeconds: Long? = null,
    speedBytes: Long? = null,
    onClick: () -> Unit,
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onRetry: () -> Unit = {},
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = download.progress,
        animationSpec = tween(300),
        label = "progress",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (download.thumbnail != null) {
                AsyncImage(
                    model = download.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Icon(
                    imageVector = if (download.isAudioOnly) Icons.Rounded.AudioFile else Icons.Rounded.VideoFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
            // Playable overlay (stream from CDN for active downloads, play file for completed)
            val isPlayable = !download.isAudioOnly &&
                download.status in listOf(DownloadStatus.DOWNLOADING, DownloadStatus.PAUSED)
            if (isPlayable) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = "Stream",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = download.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = download.source.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (download.quality.isNotBlank()) {
                    Text(
                        text = " \u00B7 ${download.quality}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Queued indicator
            if (download.status == DownloadStatus.QUEUED && download.progress == 0f) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "In queue",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Progress bar for active downloads
            if (download.status == DownloadStatus.DOWNLOADING || (download.status == DownloadStatus.QUEUED && download.progress > 0f)) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${(animatedProgress.coerceAtLeast(0f) * 100).toInt()}%" +
                                (speedBytes?.let { " \u00B7 ${formatSpeed(it)}" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (etaSeconds != null && etaSeconds > 0) {
                        Text(
                            text = formatEta(etaSeconds),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Paused indicator
            if (download.status == DownloadStatus.PAUSED) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Paused${if (download.progress > 0f) " \u00B7 ${(download.progress * 100).toInt()}%" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Waiting for network indicator
            if (download.status == DownloadStatus.WAITING_NETWORK) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Waiting for network \u00B7 ${(download.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusWarning,
                )
            }

            // Error message
            if (download.status == DownloadStatus.FAILED && download.errorMessage != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = download.errorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusError,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.width(4.dp))

        // Action buttons based on status
        when (download.status) {
            DownloadStatus.DOWNLOADING -> {
                IconButton(onClick = onPause, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Rounded.Pause,
                        contentDescription = "Pause",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            DownloadStatus.PAUSED -> {
                IconButton(onClick = onResume, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = "Resume",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            DownloadStatus.WAITING_NETWORK -> {
                IconButton(onClick = onResume, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = "Waiting for network",
                        tint = StatusWarning,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            DownloadStatus.COMPLETED -> {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = "Completed",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            DownloadStatus.FAILED -> {
                IconButton(onClick = onRetry, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = "Retry",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Remove",
                        tint = StatusError,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            else -> {}
        }
    }
}

private fun formatSpeed(bytesPerSec: Long): String = when {
    bytesPerSec >= 1_048_576 -> "%.1f MB/s".format(bytesPerSec / 1_048_576.0)
    bytesPerSec >= 1_024 -> "%.0f KB/s".format(bytesPerSec / 1_024.0)
    else -> "$bytesPerSec B/s"
}

private fun formatEta(seconds: Long): String = when {
    seconds >= 3600 -> "%d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
    seconds >= 60 -> "%d:%02d".format(seconds / 60, seconds % 60)
    else -> "${seconds}s"
}
