package com.raouf.grabit.ui.browser

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * Ad blocker with domain blocking + YouTube/platform ad injection scripts.
 */
object AdBlocker {

    private val blockedDomains = setOf(
        // Google Ads
        "pagead2.googlesyndication.com",
        "googleads.g.doubleclick.net",
        "tpc.googlesyndication.com",
        "ad.doubleclick.net",
        "ads.google.com",
        "adservice.google.com",
        "adservice.google.fr",
        "www.googleadservices.com",
        "partner.googleadservices.com",
        "google-analytics.com",
        "www.google-analytics.com",
        "ssl.google-analytics.com",
        "googletagmanager.com",
        "www.googletagmanager.com",
        "googletagservices.com",
        "ade.googlesyndication.com",
        "pagead2.googlesyndication.com",
        "static.doubleclick.net",
        "m.doubleclick.net",
        "mediavisor.doubleclick.net",

        // YouTube ad-specific
        "pubads.g.doubleclick.net",
        "securepubads.g.doubleclick.net",
        "www.youtube.com/api/stats/ads",
        "www.youtube.com/pagead",
        "www.youtube.com/ptracking",

        // Facebook tracking
        "pixel.facebook.com",
        "an.facebook.com",
        "www.facebook.com/tr",
        "connect.facebook.net",

        // Common ad networks
        "ads.yahoo.com",
        "ad.turn.com",
        "ads.pubmatic.com",
        "bidder.criteo.com",
        "static.criteo.net",
        "cas.criteo.com",
        "ssp.criteo.com",
        "ads.rubiconproject.com",
        "pixel.rubiconproject.com",
        "ib.adnxs.com",
        "acdn.adnxs.com",
        "prebid.adnxs.com",
        "secure-assets.rubiconproject.com",
        "ads.linkedin.com",
        "ad.atdmt.com",
        "bat.bing.com",
        "adsymptotic.com",
        "adtago.s3.amazonaws.com",
        "analyticsengine.s3.amazonaws.com",
        "advice-ads.s3.amazonaws.com",

        // Popups & overlays
        "popads.net",
        "www.popads.net",
        "popcash.net",
        "www.popcash.net",
        "propellerads.com",
        "ad.propellerads.com",
        "serve.popads.net",
        "juicyads.com",
        "www.juicyads.com",
        "exoclick.com",
        "main.exoclick.com",
        "static.exoclick.com",
        "syndication.exoclick.com",
        "ads.exoclick.com",

        // Tracking & analytics
        "cdn.mxpnl.com",
        "api.mixpanel.com",
        "api.segment.io",
        "cdn.segment.com",
        "t.co",
        "stats.wp.com",
        "pixel.wp.com",
        "mc.yandex.ru",
        "hotjar.com",
        "static.hotjar.com",
        "script.hotjar.com",
        "vars.hotjar.com",

        // Anime/streaming site ads
        "betterads.org",
        "a.magsrv.com",
        "s.magsrv.com",
        "adserverplus.com",
        "go.oclasrv.com",
        "onclickmax.com",
        "onclicksuper.com",
        "pushance.com",
        "pushnest.com",
        "dolohen.com",
        "acscdn.com",
        "notifpush.com",
        "push-notification.com",
        "pushcrew.com",
        "richpush.co",
        "onesignal.com",
        "cdn.onesignal.com",
        "trafficjunky.com",
        "ads.trafficjunky.net",
    )

    private val blockedPathKeywords = listOf(
        "/ad/", "/ads/", "/adserver", "/adclick",
        "/pagead/", "/doubleclick/", "/sponsor",
        "/popup", "/popunder", "/interstitial",
        "prebid", "vast.xml", "vpaid",
    )

    /** YouTube ad-serving URL patterns (video ads served from googlevideo.com) */
    private val youtubeAdUrlPatterns = listOf(
        Regex("""googlevideo\.com/videoplayback.*ctier=L"""),
        Regex("""googlevideo\.com/videoplayback.*oad="""),
        Regex("""googlevideo\.com/videoplayback.*owc=yes"""),
        Regex("""/get_midroll_"""),
        Regex("""/api/stats/ads"""),
        Regex("""/pagead/"""),
        Regex("""/ptracking"""),
        Regex("""/api/stats/atr"""),
        Regex("""/youtubei/v1/player/ad_break"""),
    )

    fun shouldBlock(url: String): Boolean {
        val lower = url.lowercase()

        // YouTube video ad URLs
        if (youtubeAdUrlPatterns.any { it.containsMatchIn(lower) }) return true

        val host = extractHost(url) ?: return false

        // Domain match
        if (blockedDomains.any { host == it || host.endsWith(".$it") }) return true

        // Path keywords
        val path = lower.substringAfter(host)
        if (blockedPathKeywords.any { it in path }) return true

        return false
    }

    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            ByteArrayInputStream(ByteArray(0)),
        )
    }

    /**
     * JavaScript to inject into YouTube pages to skip/remove ads.
     * Runs every 500ms to catch dynamically loaded ads.
     */
    fun getYouTubeAdSkipScript(): String = """
        (function() {
            if (window.__grabit_adblock) return;
            window.__grabit_adblock = true;

            function skipAds() {
                // Click "Skip Ad" button (all known variants)
                var skipBtns = document.querySelectorAll(
                    '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, ' +
                    '.ytp-skip-ad-button, button[id^="skip-button"], ' +
                    '.videoAdUiSkipButton, .ytp-ad-skip-button-slot'
                );
                skipBtns.forEach(function(btn) {
                    if (btn.offsetParent !== null) btn.click();
                });

                // Close overlay ads
                var closeBtns = document.querySelectorAll(
                    '.ytp-ad-overlay-close-button, .ytp-ad-overlay-close-container, ' +
                    '.close-button, .ytp-ad-timed-pie-countdown-container'
                );
                closeBtns.forEach(function(btn) {
                    if (btn.offsetParent !== null) btn.click();
                });

                // Force skip video ads: if ad is playing, seek to end
                var video = document.querySelector('video');
                var adShowing = document.querySelector('.ad-showing, .ytp-ad-player-overlay-instream-info');
                if (video && adShowing) {
                    video.currentTime = video.duration || 999;
                    video.playbackRate = 16;
                }

                // Remove ad containers from DOM
                var adElements = document.querySelectorAll(
                    '.ytp-ad-module, .ytp-ad-image-overlay, ' +
                    '.ytp-ad-text-overlay, .ytd-ad-slot-renderer, ' +
                    'ytd-promoted-sparkles-web-renderer, ' +
                    'ytd-display-ad-renderer, ytd-companion-slot-renderer, ' +
                    'ytd-action-companion-ad-renderer, ' +
                    'ytd-in-feed-ad-layout-renderer, ' +
                    'ytd-banner-promo-renderer, ' +
                    '#player-ads, .ytd-merch-shelf-renderer, ' +
                    'ytd-promoted-video-renderer, ' +
                    '.ytm-promoted-sparkles-web-renderer, ' +
                    '.ytm-companion-ad-renderer'
                );
                adElements.forEach(function(el) { el.remove(); });

                // Remove "Ad" badge and info bar
                var adBadge = document.querySelectorAll(
                    '.ytp-ad-badge, .ytp-ad-visit-advertiser-button, ' +
                    '.ytp-ad-button, .ytp-ad-preview-container'
                );
                adBadge.forEach(function(el) { el.remove(); });
            }

            // Run immediately and every 500ms
            skipAds();
            setInterval(skipAds, 500);

            // Also observe DOM mutations for dynamically added ads
            var observer = new MutationObserver(function() { skipAds(); });
            observer.observe(document.body || document.documentElement, {
                childList: true, subtree: true
            });
        })();
    """.trimIndent()

    /**
     * Generic ad cleanup script for non-YouTube sites.
     * Hides common ad selectors and blocks popups.
     */
    fun getGenericAdCleanScript(): String = """
        (function() {
            if (window.__grabit_generic_adblock) return;
            window.__grabit_generic_adblock = true;

            var style = document.createElement('style');
            style.textContent = '' +
                '[class*="ad-banner"], [class*="ad_banner"], [class*="adsbygoogle"], ' +
                '[id*="ad-container"], [id*="ad_container"], ' +
                '[class*="popup"], [class*="overlay-ad"], ' +
                'iframe[src*="doubleclick"], iframe[src*="googlesyndication"], ' +
                'iframe[src*="adserver"], ' +
                '[class*="sticky-ad"], [class*="floating-ad"] ' +
                '{ display: none !important; }';
            (document.head || document.documentElement).appendChild(style);

            // Block window.open popups
            window.open = function() { return null; };
        })();
    """.trimIndent()

    private fun extractHost(url: String): String? {
        return try {
            val withoutScheme = url
                .removePrefix("https://")
                .removePrefix("http://")
            withoutScheme.substringBefore("/").substringBefore(":").lowercase()
        } catch (_: Exception) { null }
    }
}
