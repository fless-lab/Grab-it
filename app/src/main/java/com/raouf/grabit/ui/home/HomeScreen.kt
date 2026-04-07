package com.raouf.grabit.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.net.Uri
import com.raouf.grabit.domain.model.Download
import com.raouf.grabit.domain.model.DownloadStatus
import com.raouf.grabit.ui.components.DownloadCard
import com.raouf.grabit.ui.components.PlaylistGroupCard
import com.raouf.grabit.ui.components.UrlInputBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToPreview: (url: String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToBrowser: () -> Unit = {},
    onNavigateToSafeZone: () -> Unit = {},
    onDownloadClick: (Download) -> Unit,
    onNavigateToPlaylistDetail: (playlistId: String) -> Unit = {},
    clipboardUrl: String? = null,
    onDismissClipboard: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val currentTab by viewModel.tab.collectAsStateWithLifecycle()
    val allItems by viewModel.allItems.collectAsStateWithLifecycle()
    val activeItems by viewModel.activeItems.collectAsStateWithLifecycle()
    val completedItems by viewModel.completedItems.collectAsStateWithLifecycle()
    val progressMap by viewModel.progressMap.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var searchVisible by remember { mutableStateOf(false) }

    val displayList = run {
        val tabList = when (currentTab) {
            HomeTab.ALL -> allItems
            HomeTab.ACTIVE -> activeItems
            HomeTab.COMPLETED -> completedItems
        }
        if (searchQuery.isBlank()) tabList
        else {
            val q = searchQuery.lowercase()
            tabList.filter { item ->
                when (item) {
                    is DownloadItem.Single -> item.download.title.lowercase().contains(q) ||
                        item.download.source.displayName.lowercase().contains(q)
                    is DownloadItem.PlaylistGroup -> item.title.lowercase().contains(q) ||
                        item.downloads.any { it.title.lowercase().contains(q) }
                }
            }
        }
    }

    var urlInput by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    // Delete confirmation dialog state
    var downloadToDelete by remember { mutableStateOf<Download?>(null) }
    var playlistToDelete by remember { mutableStateOf<DownloadItem.PlaylistGroup?>(null) }
    var deleteFromDisk by remember { mutableStateOf(false) }

    // Quick mode events
    LaunchedEffect(Unit) {
        viewModel.quickEvent.collect { event ->
            when (event) {
                is QuickDownloadEvent.Extracting ->
                    snackbarHostState.showSnackbar("Extracting video info...")
                is QuickDownloadEvent.Started ->
                    snackbarHostState.showSnackbar("Download started: ${event.title}")
                is QuickDownloadEvent.Failed ->
                    snackbarHostState.showSnackbar("Failed: ${event.error}")
            }
        }
    }

    // Manual paste = always preview (user is in the app, has time)
    val submitUrl: (String) -> Unit = remember {
        { url: String -> onNavigateToPreview(url) }
    }

    // Clipboard auto-detect: show snackbar with "Download" action
    LaunchedEffect(clipboardUrl) {
        if (!clipboardUrl.isNullOrBlank()) {
            val result = snackbarHostState.showSnackbar(
                message = "Video link detected",
                actionLabel = "Download",
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                onNavigateToPreview(clipboardUrl)
            }
            onDismissClipboard()
        }
    }

    // Delete confirmation dialog
    if (downloadToDelete != null) {
        val dl = downloadToDelete!!
        AlertDialog(
            onDismissRequest = {
                downloadToDelete = null
                deleteFromDisk = false
            },
            title = {
                Text(
                    "Delete download?",
                    color = MaterialTheme.colorScheme.onBackground,
                )
            },
            text = {
                Column {
                    Text(
                        dl.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = deleteFromDisk,
                            onCheckedChange = { deleteFromDisk = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.error,
                                checkmarkColor = MaterialTheme.colorScheme.onError,
                            ),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Also delete file from disk",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    if (deleteFromDisk) {
                        Text(
                            "This cannot be undone.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 48.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDownload(dl.id, deleteFile = deleteFromDisk)
                        downloadToDelete = null
                        deleteFromDisk = false
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        downloadToDelete = null
                        deleteFromDisk = false
                    },
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
        )
    }

    // Playlist group delete dialog
    if (playlistToDelete != null) {
        val group = playlistToDelete!!
        AlertDialog(
            onDismissRequest = {
                playlistToDelete = null
                deleteFromDisk = false
            },
            title = {
                Text(
                    "Delete playlist?",
                    color = MaterialTheme.colorScheme.onBackground,
                )
            },
            text = {
                Column {
                    Text(
                        "${group.title} (${group.totalCount} videos)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = deleteFromDisk,
                            onCheckedChange = { deleteFromDisk = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.error,
                                checkmarkColor = MaterialTheme.colorScheme.onError,
                            ),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Also delete files from disk",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    if (deleteFromDisk) {
                        Text(
                            "This cannot be undone.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 48.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePlaylistGroup(group.downloads, deleteFiles = deleteFromDisk)
                        playlistToDelete = null
                        deleteFromDisk = false
                    },
                ) {
                    Text("Delete all", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        playlistToDelete = null
                        deleteFromDisk = false
                    },
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Top bar
        TopAppBar(
            title = {
                if (searchVisible) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search downloads...") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = "Grab'it",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            },
            actions = {
                IconButton(onClick = {
                    if (searchVisible) {
                        searchQuery = ""
                        searchVisible = false
                    } else {
                        searchVisible = true
                    }
                }) {
                    Icon(
                        if (searchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!searchVisible) {
                    IconButton(onClick = onNavigateToSafeZone) {
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = "Safe Zone",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onNavigateToBrowser) {
                        Icon(
                            Icons.Rounded.Language,
                            contentDescription = "Browser",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )

        // URL input
        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            UrlInputBar(
                url = urlInput,
                onUrlChange = { urlInput = it },
                onSubmit = {
                    if (urlInput.isNotBlank()) {
                        submitUrl(urlInput)
                        urlInput = ""
                    }
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
                    color = MaterialTheme.colorScheme.primary,
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

        val listState = androidx.compose.foundation.lazy.rememberLazyListState()

        // Scroll to top when list grows (new download added)
        val listSize = displayList.size
        LaunchedEffect(listSize) {
            if (listSize > 0) listState.animateScrollToItem(0)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                count = displayList.size,
                key = { index ->
                    when (val item = displayList[index]) {
                        is DownloadItem.Single -> "s_${item.download.id}"
                        is DownloadItem.PlaylistGroup -> "p_${item.playlistId}"
                    }
                },
            ) { index ->
                when (val item = displayList[index]) {
                    is DownloadItem.PlaylistGroup -> {
                        val plDismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    playlistToDelete = item
                                    false // don't auto-dismiss, show dialog
                                } else false
                            },
                        )
                        SwipeToDismissBox(
                            state = plDismissState,
                            backgroundContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(12.dp),
                                        )
                                        .padding(end = 20.dp),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            },
                            enableDismissFromStartToEnd = false,
                        ) {
                            PlaylistGroupCard(
                                group = item,
                                onClick = { onNavigateToPlaylistDetail(item.playlistId) },
                            )
                        }
                    }
                    is DownloadItem.Single -> {
                        val download = item.download
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    if (download.status == DownloadStatus.COMPLETED) {
                                        downloadToDelete = download
                                        false
                                    } else {
                                        viewModel.deleteDownload(download.id, deleteFile = true)
                                        true
                                    }
                                } else false
                            },
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(12.dp),
                                        )
                                        .padding(end = 20.dp),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            },
                            enableDismissFromStartToEnd = false,
                        ) {
                            val liveProgress = progressMap[download.id]
                            DownloadCard(
                                download = download,
                                etaSeconds = liveProgress?.etaSeconds,
                                speedBytes = liveProgress?.speedBytes,
                                onClick = { onDownloadClick(download) },
                                onPause = { viewModel.pauseDownload(download.id) },
                                onResume = { viewModel.resumeDownload(download) },
                                onRetry = { viewModel.retryDownload(download) },
                                onDelete = { downloadToDelete = download },
                                onHide = { viewModel.hideDownload(download.id) },
                                onShare = {
                                    download.filePath?.let { path ->
                                        val uri = Uri.parse(path)
                                        val mime = if (download.isAudioOnly) "audio/*" else "video/*"
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = mime
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share"))
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter),
    ) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shape = RoundedCornerShape(12.dp),
        )
    }
    } // Box
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
