package com.raouf.grabit.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val darkTheme by viewModel.darkTheme.collectAsStateWithLifecycle()
    val quickMode by viewModel.quickMode.collectAsStateWithLifecycle()
    val autoSubfolder by viewModel.autoSubfolder.collectAsStateWithLifecycle()
    val clipboardMonitor by viewModel.clipboardMonitor.collectAsStateWithLifecycle()
    val downloadDir by viewModel.downloadDir.collectAsStateWithLifecycle()
    val appLock by viewModel.appLock.collectAsStateWithLifecycle()
    val hideFromGallery by viewModel.hideFromGallery.collectAsStateWithLifecycle()
    val autoUpdate by viewModel.autoUpdate.collectAsStateWithLifecycle()

    val context = LocalContext.current

    val dirPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        uri?.let {
            // Persist permission
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.setDownloadDir(it.toString())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopAppBar(
            title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // Download folder
            SectionLabel("STORAGE")
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Folder,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Download folder", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        text = if (downloadDir != null) {
                            Uri.parse(downloadDir).lastPathSegment ?: "Selected"
                        } else "Not set",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { dirPicker.launch(null) }) {
                    Text(if (downloadDir != null) "Change" else "Select", color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(24.dp))

            // Behavior
            SectionLabel("BEHAVIOR")
            Spacer(Modifier.height(8.dp))

            SettingToggle(
                title = "Quick mode",
                subtitle = "Download immediately in best quality, skip preview",
                checked = quickMode,
                onCheckedChange = viewModel::setQuickMode,
            )

            SettingToggle(
                title = "Auto subfolders",
                subtitle = "Organize downloads by source (YouTube, Facebook...)",
                checked = autoSubfolder,
                onCheckedChange = viewModel::setAutoSubfolder,
            )

            SettingToggle(
                title = "Clipboard monitor",
                subtitle = "Detect video links when you copy them",
                checked = clipboardMonitor,
                onCheckedChange = viewModel::setClipboardMonitor,
            )

            SettingToggle(
                title = "Auto-update",
                subtitle = "Automatically download and install new versions",
                checked = autoUpdate,
                onCheckedChange = viewModel::setAutoUpdate,
            )

            Spacer(Modifier.height(24.dp))

            // Appearance
            SectionLabel("APPEARANCE")
            Spacer(Modifier.height(8.dp))

            SettingToggle(
                title = "Dark mode",
                subtitle = "Use dark theme",
                checked = darkTheme,
                onCheckedChange = viewModel::setDarkTheme,
            )

            Spacer(Modifier.height(24.dp))

            // Security
            SectionLabel("SECURITY")
            Spacer(Modifier.height(8.dp))

            SettingToggle(
                title = "App lock",
                subtitle = "Require biometric or PIN to open the app",
                checked = appLock,
                onCheckedChange = viewModel::setAppLock,
            )

            SettingToggle(
                title = "Hide from gallery",
                subtitle = "Prevent downloaded files from appearing in gallery apps",
                checked = hideFromGallery,
                onCheckedChange = viewModel::setHideFromGallery,
            )

            Spacer(Modifier.height(24.dp))

            // Support
            SectionLabel("SUPPORT")
            Spacer(Modifier.height(8.dp))

            SettingLink(
                icon = Icons.Rounded.Coffee,
                title = "Buy me a coffee",
                subtitle = "Support development on Ko-fi",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/raoufatm"))
                    context.startActivity(intent)
                },
            )

            SettingLink(
                icon = Icons.Rounded.Star,
                title = "Star on GitHub",
                subtitle = "Help others discover Grab'it",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/fless-lab/Grab-it"))
                    context.startActivity(intent)
                },
            )

            Spacer(Modifier.height(24.dp))

            // About
            SectionLabel("ABOUT")
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Grab'it v${com.raouf.grabit.BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Powered by yt-dlp (auto-updates every 12h)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun SettingLink(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = MaterialTheme.colorScheme.outline,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}
