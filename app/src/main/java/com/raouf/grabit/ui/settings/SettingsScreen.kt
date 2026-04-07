package com.raouf.grabit.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Folder
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
import com.raouf.grabit.ui.theme.MintAccent

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
                    tint = MintAccent,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Download folder", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (downloadDir != null) {
                            Uri.parse(downloadDir).lastPathSegment ?: "Selected"
                        } else "Not set",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { dirPicker.launch(null) }) {
                    Text(if (downloadDir != null) "Change" else "Select", color = MintAccent)
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

            // About
            SectionLabel("ABOUT")
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Grab'it v1.0.0",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Powered by yt-dlp",
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
            Text(title, style = MaterialTheme.typography.titleMedium)
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
                checkedTrackColor = MintAccent,
                checkedThumbColor = MaterialTheme.colorScheme.background,
            ),
        )
    }
}
