package com.raouf.grabit.ui.preview

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.raouf.grabit.domain.model.VideoFormat
import com.raouf.grabit.ui.player.PlayerActivity

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PreviewScreen(
    onBack: () -> Unit,
    onDownloadStarted: () -> Unit = onBack,
    viewModel: PreviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopAppBar(
            title = { Text("Preview", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                }
            },
            actions = {
                if (state.info != null && !state.isLoading) {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )

        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Extracting video info...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            state.error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.Error,
                            null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            state.error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = { viewModel.retry() }) {
                            Text("Retry", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            state.downloadStarted -> {
                // Auto-navigate to home when download starts
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(600)
                    onDownloadStarted()
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Download started!",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }

            state.info != null -> {
                val info = state.info!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    // Thumbnail
                    if (info.thumbnail != null) {
                        AsyncImage(
                            model = info.thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        Spacer(Modifier.height(16.dp))
                    }

                    // Title
                    Text(
                        text = info.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // Meta line
                    Spacer(Modifier.height(4.dp))
                    val meta = buildString {
                        append(info.source.displayName)
                        if (info.uploader != null) append(" \u00B7 ${info.uploader}")
                        if (info.duration != null) {
                            val m = info.duration / 60
                            val s = info.duration % 60
                            append(" \u00B7 ${m}:${s.toString().padStart(2, '0')}")
                        }
                    }
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(24.dp))

                    // Video formats
                    val videoFormats = info.formats.filter { !it.isAudioOnly }
                    val audioFormats = info.formats.filter { it.isAudioOnly }

                    if (videoFormats.isNotEmpty()) {
                        Text(
                            text = "VIDEO",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            videoFormats.forEach { format ->
                                FormatChip(
                                    format = format,
                                    selected = state.selectedFormat == format,
                                    onClick = { viewModel.selectFormat(format) },
                                )
                            }
                        }
                    }

                    if (audioFormats.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "AUDIO",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            audioFormats.forEach { format ->
                                FormatChip(
                                    format = format,
                                    selected = state.selectedFormat == format,
                                    onClick = { viewModel.selectFormat(format) },
                                )
                            }
                        }
                    }

                    // Subtitles section
                    if (info.subtitleLanguages.isNotEmpty() && state.selectedFormat?.isAudioOnly == false) {
                        Spacer(Modifier.height(16.dp))
                        // Toggle row
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (state.subtitlesEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .clickable { viewModel.toggleSubtitles(!state.subtitlesEnabled) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            androidx.compose.foundation.layout.Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.ClosedCaption,
                                    contentDescription = null,
                                    tint = if (state.subtitlesEnabled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.padding(start = 8.dp))
                                Text(
                                    text = "Download subtitles",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (state.subtitlesEnabled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${info.subtitleLanguages.size} languages",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        // Language chips (shown when enabled)
                        if (state.subtitlesEnabled) {
                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                info.subtitleLanguages.forEach { lang ->
                                    val selected = lang in state.selectedSubLangs
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .then(
                                                if (selected) Modifier
                                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                                                else Modifier
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                            )
                                            .clickable { viewModel.toggleSubLang(lang) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                    ) {
                                        Text(
                                            text = lang.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // Play + Download buttons
                    val context = LocalContext.current
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Play (stream without downloading)
                        OutlinedButton(
                            onClick = {
                                PlayerActivity.launch(
                                    context, "", info.title,
                                    streaming = true, videoUrl = info.url,
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.padding(start = 4.dp))
                            Text("Play", style = MaterialTheme.typography.titleSmall)
                        }

                        // Download
                        Button(
                            onClick = { viewModel.startDownload() },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.background,
                            ),
                        ) {
                            Icon(
                                Icons.Rounded.Download,
                                null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.padding(start = 4.dp))
                            Text("Download", style = MaterialTheme.typography.titleSmall)
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun FormatChip(
    format: VideoFormat,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val label = buildString {
        append(format.quality)
        if (format.filesize != null && format.filesize > 0) {
            append(" \u00B7 ")
            append(formatFileSize(format.filesize))
        }
    }
    Box(
        modifier = Modifier
            .clip(shape)
            .then(
                if (selected) Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(1.dp, MaterialTheme.colorScheme.primary, shape)
                else Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .animateContentSize(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.0f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024 -> "%.0f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}
