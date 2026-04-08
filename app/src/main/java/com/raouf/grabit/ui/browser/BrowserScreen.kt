package com.raouf.grabit.ui.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.raouf.grabit.ui.theme.SourceFacebook
import com.raouf.grabit.ui.theme.SourceInstagram
import com.raouf.grabit.ui.theme.SourceTiktok
import com.raouf.grabit.ui.theme.SourceTwitter
import com.raouf.grabit.ui.theme.SourceYoutube

private data class QuickLink(
    val name: String,
    val url: String,
    val color: Color,
    val domain: String,
) {
    val iconUrl get() = "https://www.google.com/s2/favicons?domain=$domain&sz=128"
}

private val DailymotionBlue = Color(0xFF0066DC)
private val VimeoCyan = Color(0xFF1AB7EA)
private val SoundCloudOrange = Color(0xFFFF5500)
private val RedditOrange = Color(0xFFFF4500)

private val quickLinks = listOf(
    QuickLink("YouTube", "https://m.youtube.com", SourceYoutube, "youtube.com"),
    QuickLink("Instagram", "https://www.instagram.com", SourceInstagram, "instagram.com"),
    QuickLink("TikTok", "https://www.tiktok.com", SourceTiktok, "tiktok.com"),
    QuickLink("Facebook", "https://m.facebook.com", SourceFacebook, "facebook.com"),
    QuickLink("Twitter", "https://x.com", SourceTwitter, "x.com"),
    QuickLink("Reddit", "https://www.reddit.com", RedditOrange, "reddit.com"),
    QuickLink("Dailymotion", "https://www.dailymotion.com", DailymotionBlue, "dailymotion.com"),
    QuickLink("Vimeo", "https://vimeo.com", VimeoCyan, "vimeo.com"),
    QuickLink("SoundCloud", "https://soundcloud.com", SoundCloudOrange, "soundcloud.com"),
)

private val videoUrlPatterns = listOf(
    Regex("youtube\\.com/watch\\?v="),
    Regex("youtu\\.be/[\\w-]+"),
    Regex("youtube\\.com/shorts/[\\w-]+"),
    Regex("instagram\\.com/(p|reel|tv)/"),
    Regex("tiktok\\.com/.+/video/"),
    Regex("facebook\\.com/.+/videos/"),
    Regex("fb\\.watch/"),
    Regex("twitter\\.com/.+/status/"),
    Regex("x\\.com/.+/status/"),
    Regex("linkedin\\.com/.+/(posts|pulse)/"),
    Regex("dailymotion\\.com/video/"),
    Regex("vimeo\\.com/\\d+"),
    Regex("twitch\\.tv/videos/"),
    Regex("reddit\\.com/.+/comments/"),
    Regex("soundcloud\\.com/.+/.+"),
)

private fun isVideoPage(url: String): Boolean =
    videoUrlPatterns.any { it.containsMatchIn(url) }

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    onBack: () -> Unit,
    onDownloadUrl: (String) -> Unit,
    initialUrl: String? = null,
) {
    var currentUrl by rememberSaveable { mutableStateOf(initialUrl ?: "") }
    var urlBarText by rememberSaveable { mutableStateOf(initialUrl ?: "") }
    var isLoading by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var showHomePage by rememberSaveable { mutableStateOf(initialUrl == null) }
    var isFocused by remember { mutableStateOf(false) }

    val videoDetected = remember(currentUrl) { isVideoPage(currentUrl) }
    val focusManager = LocalFocusManager.current

    var webView by remember { mutableStateOf<WebView?>(null) }
    val bgColor = MaterialTheme.colorScheme.background.toArgb()

    BackHandler(enabled = !showHomePage) {
        if (isFocused) {
            focusManager.clearFocus()
        } else if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            showHomePage = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                clearHistory()
                clearCache(true)
                CookieManager.getInstance().removeAllCookies(null)
                (parent as? ViewGroup)?.removeView(this)
                destroy()
            }
        }
    }

    fun navigateTo(url: String) {
        val finalUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            if (url.contains(".") && !url.contains(" ")) "https://$url"
            else "https://www.google.com/search?q=${java.net.URLEncoder.encode(url, "UTF-8")}"
        } else url

        urlBarText = finalUrl
        currentUrl = finalUrl
        showHomePage = false
        focusManager.clearFocus()
        webView?.loadUrl(finalUrl)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // ---- URL Bar ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Close button
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Rounded.Close, "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // URL input field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (!isFocused && !showHomePage) {
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    if (isFocused || showHomePage) {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        BasicTextField(
                            value = urlBarText,
                            onValueChange = { urlBarText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isFocused = it.isFocused },
                            textStyle = TextStyle(
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(
                                onGo = { navigateTo(urlBarText) },
                            ),
                        )
                        if (urlBarText.isEmpty()) {
                            Text(
                                "Search or enter URL",
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                ),
                            )
                        }
                    }

                    if (urlBarText.isNotEmpty() && isFocused) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) { urlBarText = "" },
                        )
                    }
                }
            }

            // Reload / Stop
            if (!showHomePage) {
                Spacer(Modifier.width(2.dp))
                IconButton(
                    onClick = {
                        if (isLoading) webView?.stopLoading() else webView?.reload()
                    },
                ) {
                    Icon(
                        if (isLoading) Icons.Rounded.Close else Icons.Rounded.Refresh,
                        contentDescription = if (isLoading) "Stop" else "Reload",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ---- Progress bar ----
        AnimatedVisibility(visible = isLoading) {
            LinearProgressIndicator(
                progress = { loadProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
            )
        }

        // ---- Content ----
        Box(modifier = Modifier.weight(1f)) {
            if (showHomePage) {
                QuickLinksPage(
                    onQuickLinkClick = { url -> navigateTo(url) },
                )
            } else {
                // WebView
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            setBackgroundColor(bgColor)

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                builtInZoomControls = true
                                displayZoomControls = false
                                setSupportMultipleWindows(false)
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                userAgentString = settings.userAgentString
                                    .replace("; wv", "")
                                mediaPlaybackRequiresUserGesture = false
                                cacheMode = WebSettings.LOAD_DEFAULT
                                databaseEnabled = true
                            }

                            val wv = this
                            CookieManager.getInstance().apply {
                                setAcceptCookie(true)
                                setAcceptThirdPartyCookies(wv, true)
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): WebResourceResponse? {
                                    val reqUrl = request?.url?.toString() ?: return null
                                    if (AdBlocker.shouldBlock(reqUrl)) {
                                        return AdBlocker.createEmptyResponse()
                                    }
                                    return null
                                }

                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: Bitmap?,
                                ) {
                                    isLoading = true
                                    url?.let {
                                        currentUrl = it
                                        urlBarText = it
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                    canGoBack = view?.canGoBack() ?: false
                                    canGoForward = view?.canGoForward() ?: false
                                    url?.let {
                                        currentUrl = it
                                        urlBarText = it
                                    }
                                    // YouTube ad skip (only on YouTube)
                                    val u = url?.lowercase() ?: ""
                                    if ("youtube.com" in u || "youtu.be" in u) {
                                        view?.evaluateJavascript(
                                            AdBlocker.getYouTubeAdSkipScript(), null,
                                        )
                                    }
                                }

                                override fun doUpdateVisitedHistory(
                                    view: WebView?,
                                    url: String?,
                                    isReload: Boolean,
                                ) {
                                    super.doUpdateVisitedHistory(view, url, isReload)
                                    url?.let {
                                        currentUrl = it
                                        urlBarText = it
                                        canGoBack = view?.canGoBack() ?: false
                                        canGoForward = view?.canGoForward() ?: false
                                    }
                                    val u = url?.lowercase() ?: ""
                                    if ("youtube.com" in u) {
                                        view?.evaluateJavascript(
                                            AdBlocker.getYouTubeAdSkipScript(), null,
                                        )
                                    }
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean {
                                    val scheme = request?.url?.scheme?.lowercase() ?: return false
                                    // Block app deep links (snssdk, intent, market, etc.)
                                    // Only allow http/https in our browser
                                    return scheme != "http" && scheme != "https"
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(
                                    view: WebView?,
                                    newProgress: Int,
                                ) {
                                    loadProgress = newProgress / 100f
                                }
                            }

                            webView = this
                            if (currentUrl.isNotBlank()) loadUrl(currentUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // ---- Download FAB ----
            if (videoDetected && !showHomePage) {
                FloatingActionButton(
                    onClick = { onDownloadUrl(currentUrl) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 72.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = "Download",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Grab",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }

        // ---- Bottom Bar ----
        if (!showHomePage) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Back
                    IconButton(
                        onClick = { webView?.goBack() },
                        enabled = canGoBack,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            "Back",
                            modifier = Modifier.size(22.dp),
                            tint = if (canGoBack) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                        )
                    }

                    // Forward
                    IconButton(
                        onClick = { webView?.goForward() },
                        enabled = canGoForward,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowForward,
                            "Forward",
                            modifier = Modifier.size(22.dp),
                            tint = if (canGoForward) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                        )
                    }

                    // Home
                    IconButton(onClick = { showHomePage = true }) {
                        Icon(
                            Icons.Rounded.Home, "Home",
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                }
            }
        }
    }
}

// ---- Home page with quick links ----

@Composable
private fun QuickLinksPage(
    onQuickLinkClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))

        Text(
            "Grab'it Browser",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Ads blocked",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )

        Spacer(Modifier.height(36.dp))

        Text(
            "Quick access",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, bottom = 12.dp),
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(quickLinks) { link ->
                QuickLinkItem(
                    name = link.name,
                    color = link.color,
                    iconUrl = link.iconUrl,
                    onClick = { onQuickLinkClick(link.url) },
                )
            }
        }
    }
}

@Composable
private fun QuickLinkItem(
    name: String,
    color: Color,
    iconUrl: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = iconUrl,
                contentDescription = name,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = name,
            style = TextStyle(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
