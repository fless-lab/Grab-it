package com.raouf.grabit.data.downloader

/**
 * Translates raw yt-dlp / network error messages into user-friendly text.
 */
object ErrorParser {

    fun friendlyMessage(raw: String?): String {
        if (raw.isNullOrBlank()) return "Something went wrong"
        val lower = raw.lowercase()

        // Network / DNS errors
        if ("no address associated" in lower || "name or service not known" in lower ||
            "nodename nor servname" in lower || "dns" in lower
        ) {
            return "No internet connection. Check your network and retry."
        }
        if ("timeout" in lower || "timed out" in lower) {
            return "Connection timed out. Try again or switch to a better network."
        }
        if ("connection refused" in lower || "connection reset" in lower ||
            "errno 7" in lower || "unreachable" in lower
        ) {
            return "Cannot reach the server. Check your connection."
        }
        if ("unable to download webpage" in lower || "urlopen error" in lower) {
            return "No internet access. Connect to a network and retry."
        }
        if ("ssl" in lower || "certificate" in lower) {
            return "Secure connection failed. Try a different network."
        }

        // yt-dlp content errors
        if ("unsupported url" in lower || "no suitable extractor" in lower) {
            return "This link is not supported."
        }
        if ("video unavailable" in lower || "removed" in lower || "private video" in lower) {
            return "This video is unavailable or has been removed."
        }
        if ("sign in" in lower || "login" in lower || "age" in lower) {
            return "This video requires sign-in or age verification."
        }
        if ("geo" in lower || "country" in lower || "not available in your" in lower) {
            return "This content is blocked in your region."
        }
        if ("copyright" in lower || "dmca" in lower) {
            return "This content was removed for copyright reasons."
        }
        if ("live" in lower && "not supported" in lower) {
            return "Live streams cannot be downloaded."
        }
        if ("format" in lower && "not available" in lower) {
            return "Selected quality is not available. Try a different one."
        }

        // yt-dlp init
        if ("yt-dlp failed to initialize" in lower) {
            return "Download engine is still loading. Wait a moment and retry."
        }

        // Generic but short: strip yt-dlp prefix noise
        val cleaned = raw
            .replace(Regex("^ERROR:\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[\\w+]\\s*"), "")
            .trim()

        // Cap length for UI
        return if (cleaned.length > 120) cleaned.take(117) + "..." else cleaned
    }
}
