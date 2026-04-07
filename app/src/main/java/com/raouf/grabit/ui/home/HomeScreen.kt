package com.raouf.grabit.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raouf.grabit.domain.model.Download
import com.raouf.grabit.ui.components.DownloadCard
import com.raouf.grabit.ui.components.UrlInputBar
import com.raouf.grabit.ui.theme.MintAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToPreview: (url: String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onDownloadClick: (Download) -> Unit,
    urlFromIntent: String?,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val currentTab by viewModel.tab.collectAsStateWithLifecycle()
    val allDownloads by viewModel.allDownloads.collectAsStateWithLifecycle()
    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val completedDownloads by viewModel.completedDownloads.collectAsStateWithLifecycle()

    val displayList = when (currentTab) {
        HomeTab.ALL -> allDownloads
        HomeTab.ACTIVE -> activeDownloads
        HomeTab.COMPLETED -> completedDownloads
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Top bar
        TopAppBar(
            title = {
                Text(
                    text = "Grab'it",
                    style = MaterialTheme.typography.headlineMedium,
                )
            },
            actions = {
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )

        // URL input
        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            UrlInputBar(
                url = urlFromIntent ?: "",
                onUrlChange = { /* handled by state hoisting in parent */ },
                onSubmit = {
                    val u = urlFromIntent ?: ""
                    if (u.isNotBlank()) onNavigateToPreview(u)
                },
            )
        }

        Spacer(Modifier.height(12.dp))

        // Tabs
        val tabs = listOf("All", "Active", "Done")
        TabRow(
            selectedTabIndex = currentTab.ordinal,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[currentTab.ordinal]),
                    color = MintAccent,
                    height = 2.dp,
                )
            },
            divider = {},
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = currentTab.ordinal == index,
                    onClick = { viewModel.setTab(HomeTab.entries[index]) },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    },
                    selectedContentColor = MaterialTheme.colorScheme.onBackground,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Download list
        AnimatedVisibility(
            visible = displayList.isEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            EmptyState()
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(displayList, key = { it.id }) { download ->
                DownloadCard(
                    download = download,
                    onClick = { onDownloadClick(download) },
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.CloudDownload,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "No downloads yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Share a video link or paste one above",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}
