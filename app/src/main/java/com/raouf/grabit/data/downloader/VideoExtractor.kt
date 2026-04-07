package com.raouf.grabit.data.downloader

import android.util.Log
import com.google.gson.JsonObject
import com.raouf.grabit.GrabitApp
import com.raouf.grabit.domain.model.PlaylistEntry
import com.raouf.grabit.domain.model.PlaylistInfo
import com.raouf.grabit.domain.model.VideoFormat
import com.raouf.grabit.domain.model.VideoInfo
import com.raouf.grabit.domain.model.VideoSource
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoExtractor @Inject constructor() {

    companion object {
        private const val TAG = "VideoExtractor"
        private const val CACHE_TTL_MS = 60 * 60 * 1000L // 1 hour
        private const val CACHE_MAX_SIZE = 20
    }

    private data class CacheEntry(val info: VideoInfo, val timestamp: Long)

    private val cache = object : LinkedHashMap<String, CacheEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean {
            return size > CACHE_MAX_SIZE
        }
    }

    suspend fun extract(url: String, forceRefresh: Boolean = false): VideoInfo = withContext(Dispatchers.IO) {
        // Check cache first
        if (!forceRefresh) {
            synchronized(cache) { cache[url] }?.let { entry ->
                if (System.currentTimeMillis() - entry.timestamp < CACHE_TTL_MS) {
                    Log.d(TAG, "Cache hit for: $url")
                    return@withContext entry.info
                } else {
                    synchronized(cache) { cache.remove(url) }
                }
            }
        }

        val ready = GrabitApp.ytdlReady.await()
        if (!ready) throw Exception("yt-dlp failed to initialize")

        Log.d(TAG, "Extracting info for: $url")

        val request = YoutubeDLRequest(url)
        request.addOption("--dump-json")
        request.addOption("--no-download")
        request.addOption("--no-playlist")
        request.addOption("--no-check-certificates")

        val response = try {
            YoutubeDL.getInstance().execute(request)
        } catch (e: Exception) {
            Log.e(TAG, "yt-dlp execute failed: ${e.message}", e)
            throw Exception(ErrorParser.friendlyMessage(e.message))
        }

        val stdout = response.out
        val stderr = response.err

        Log.d(TAG, "stdout length: ${stdout?.length ?: 0}")
        Log.d(TAG, "stderr: ${stderr?.take(500)}")

        if (stdout.isNullOrBlank()) {
            val errorMsg = stderr?.takeIf { it.isNotBlank() } ?: "No response from yt-dlp"
            Log.e(TAG, "Empty stdout. stderr: $errorMsg")
            throw Exception(ErrorParser.friendlyMessage(errorMsg))
        }

        val json = try {
            com.google.gson.JsonParser.parseString(stdout).asJsonObject
        } catch (e: Exception) {
            Log.e(TAG, "JSON parse failed. stdout: ${stdout.take(200)}", e)
            throw Exception("Failed to parse video info")
        }

        val title = json.get("title")?.asString ?: "Untitled"
        val thumbnail = json.get("thumbnail")?.asString
        val duration = json.get("duration")?.asLong
        val uploader = json.get("uploader")?.asString
        val source = VideoSource.fromUrl(url)

        Log.d(TAG, "Extracted: $title (${source.displayName})")

        val formats = parseFormats(json, duration)
        val subtitleLangs = parseSubtitleLanguages(json)

        val info = VideoInfo(
            url = url,
            title = title,
            thumbnail = thumbnail,
            duration = duration,
            uploader = uploader,
            source = source,
            formats = formats,
            subtitleLanguages = subtitleLangs,
        )

        // Cache the result
        synchronized(cache) { cache[url] = CacheEntry(info, System.currentTimeMillis()) }
        Log.d(TAG, "Cached extraction for: $url")

        info
    }

    private fun parseSubtitleLanguages(json: JsonObject): List<String> {
        val langs = mutableSetOf<String>()
        // Manual subtitles (higher quality)
        json.getAsJsonObject("subtitles")?.keySet()?.let { langs.addAll(it) }
        // Auto-generated captions
        json.getAsJsonObject("automatic_captions")?.keySet()?.let { langs.addAll(it) }
        return langs.sorted()
    }

    private fun parseFormats(json: JsonObject, duration: Long?): List<VideoFormat> {
        val formatsArray = json.getAsJsonArray("formats")
        if (formatsArray == null || formatsArray.size() == 0) {
            Log.w(TAG, "No formats array, using defaults")
            return defaultFormats()
        }

        // Parse raw format entries
        data class RawFormat(
            val height: Int,
            val filesize: Long?,
            val vcodec: String?,
            val acodec: String?,
            val tbr: Double?,
        )

        val rawFormats = formatsArray.mapNotNull { element ->
            try {
                val f = element.asJsonObject
                val vcodec = f.get("vcodec")?.asString
                val acodec = f.get("acodec")?.asString
                val height = f.get("height")?.takeIf { !it.isJsonNull }?.asNumber?.toInt()
                val filesize = f.get("filesize")?.takeIf { !it.isJsonNull }?.asNumber?.toLong()
                    ?: f.get("filesize_approx")?.takeIf { !it.isJsonNull }?.asNumber?.toLong()
                val tbr = f.get("tbr")?.takeIf { !it.isJsonNull }?.asNumber?.toDouble()

                RawFormat(
                    height = height ?: 0,
                    filesize = filesize,
                    vcodec = vcodec,
                    acodec = acodec,
                    tbr = tbr,
                )
            } catch (_: Exception) { null }
        }

        // Video formats: has video codec and height
        val videoFormats = rawFormats.filter {
            it.vcodec != null && it.vcodec != "none" && it.height > 0
        }

        // Audio-only formats
        val audioFormats = rawFormats.filter {
            (it.vcodec == null || it.vcodec == "none") && it.acodec != null && it.acodec != "none"
        }

        // Best audio size for combined estimation
        val bestAudio = audioFormats.maxByOrNull { it.filesize ?: it.tbr?.toLong() ?: 0 }
        val bestAudioSize = bestAudio?.filesize ?: estimateSize(bestAudio?.tbr, duration)

        // Find available resolutions
        val availableHeights = videoFormats.map { it.height }.distinct().sortedDescending()
        Log.d(TAG, "Available heights: $availableHeights")

        val result = mutableListOf<VideoFormat>()

        // Smart "Best" format: pick size from any format at max resolution
        val maxHeight = videoFormats.maxOfOrNull { it.height } ?: 0
        val bestTierFormats = videoFormats.filter { it.height >= maxHeight - 80 }
        val bestVideoSize = bestTierFormats.firstNotNullOfOrNull { it.filesize }
            ?: bestTierFormats.mapNotNull { estimateSize(it.tbr, duration) }.maxOrNull()
        val bestTotalSize = sumSizes(bestVideoSize, bestAudioSize)

        result.add(VideoFormat(
            formatId = "bestvideo+bestaudio/best",
            ext = "mp4",
            quality = "Best",
            filesize = bestTotalSize,
            isAudioOnly = false,
        ))

        // Per-resolution smart formats
        val tiers = listOf(2160 to "4K", 1440 to "1440p", 1080 to "1080p", 720 to "720p", 480 to "480p", 360 to "360p")
        val addedLabels = mutableSetOf<String>()

        for ((targetHeight, label) in tiers) {
            // Find all formats at or near this height
            val matching = videoFormats.filter { it.height in (targetHeight - 80)..targetHeight }
            if (matching.isEmpty()) continue

            if (label in addedLabels) continue
            addedLabels.add(label)

            // Pick size from the format with best data: prefer filesize, then tbr estimation
            val videoSize = matching.firstNotNullOfOrNull { it.filesize }
                ?: matching.mapNotNull { estimateSize(it.tbr, duration) }.maxOrNull()

            val totalSize = sumSizes(videoSize, bestAudioSize)

            result.add(VideoFormat(
                formatId = "bestvideo[height<=$targetHeight]+bestaudio/best[height<=$targetHeight]",
                ext = "mp4",
                quality = label,
                filesize = totalSize,
                isAudioOnly = false,
            ))
        }

        // Audio formats
        val bestAudioFilesize = bestAudioSize ?: audioFormats.firstOrNull()?.filesize
        result.add(VideoFormat(
            formatId = "bestaudio",
            ext = "mp3",
            quality = "Audio MP3",
            filesize = bestAudioFilesize,
            isAudioOnly = true,
        ))

        // If we only got Best + Audio (no resolution tiers), add fallback mid-quality
        if (result.size <= 2) {
            result.add(1, VideoFormat(
                formatId = "bestvideo[height<=720]+bestaudio/best[height<=720]",
                ext = "mp4",
                quality = "720p",
                filesize = null,
                isAudioOnly = false,
            ))
            result.add(2, VideoFormat(
                formatId = "bestvideo[height<=480]+bestaudio/best[height<=480]",
                ext = "mp4",
                quality = "480p",
                filesize = null,
                isAudioOnly = false,
            ))
        }

        Log.d(TAG, "Parsed ${result.size} format options")
        return result
    }

    private fun estimateSize(tbrKbps: Double?, durationSec: Long?): Long? {
        if (tbrKbps == null || durationSec == null || durationSec <= 0) return null
        return (tbrKbps * 1000 / 8 * durationSec).toLong()
    }

    private fun sumSizes(a: Long?, b: Long?): Long? {
        if (a == null && b == null) return null
        return (a ?: 0) + (b ?: 0)
    }

    private fun defaultFormats(): List<VideoFormat> = listOf(
        VideoFormat("bestvideo+bestaudio/best", "mp4", "Best", null, false),
        VideoFormat("bestvideo[height<=720]+bestaudio/best[height<=720]", "mp4", "720p", null, false),
        VideoFormat("bestvideo[height<=480]+bestaudio/best[height<=480]", "mp4", "480p", null, false),
        VideoFormat("bestaudio", "mp3", "Audio MP3", null, true),
    )

    fun isPlaylistUrl(url: String): Boolean {
        val lower = url.lowercase()
        return "playlist?list=" in lower || "/sets/" in lower || "&list=" in lower
    }

    suspend fun extractPlaylist(url: String): PlaylistInfo = withContext(Dispatchers.IO) {
        val ready = GrabitApp.ytdlReady.await()
        if (!ready) throw Exception("yt-dlp failed to initialize")

        Log.d(TAG, "Extracting playlist: $url")

        val request = YoutubeDLRequest(url)
        request.addOption("--flat-playlist")
        request.addOption("--dump-json")
        request.addOption("--no-download")
        request.addOption("--no-check-certificates")

        val response = YoutubeDL.getInstance().execute(request)
        val stdout = response.out

        if (stdout.isNullOrBlank()) {
            throw Exception(response.err?.take(200) ?: "No response from yt-dlp")
        }

        // Each line is a JSON object for one video
        val entries = stdout.trim().lines().mapNotNull { line ->
            try {
                val json = com.google.gson.JsonParser.parseString(line).asJsonObject
                val videoUrl = json.get("url")?.asString
                    ?: json.get("webpage_url")?.asString
                    ?: return@mapNotNull null
                PlaylistEntry(
                    url = videoUrl,
                    title = json.get("title")?.asString ?: "Untitled",
                    thumbnail = json.get("thumbnail")?.takeIf { !it.isJsonNull }?.asString
                        ?: json.get("thumbnails")?.takeIf { it.isJsonArray && it.asJsonArray.size() > 0 }
                            ?.asJsonArray?.last()?.asJsonObject?.get("url")?.asString,
                    duration = json.get("duration")?.takeIf { !it.isJsonNull }?.asNumber?.toLong(),
                )
            } catch (_: Exception) { null }
        }

        if (entries.isEmpty()) throw Exception("No videos found in playlist")

        // Try to get playlist title from first entry
        val firstJson = try {
            com.google.gson.JsonParser.parseString(stdout.trim().lines().first()).asJsonObject
        } catch (_: Exception) { null }

        PlaylistInfo(
            title = firstJson?.get("playlist_title")?.takeIf { !it.isJsonNull }?.asString ?: "Playlist",
            uploader = firstJson?.get("playlist_uploader")?.takeIf { !it.isJsonNull }?.asString,
            entries = entries,
        )
    }

    /**
     * Extract a direct streaming URL for live playback via ExoPlayer.
     * Uses a muxed format (video+audio combined) so ExoPlayer can play it directly.
     * Works for YouTube DASH, Instagram, TikTok, etc.
     */
    suspend fun getStreamUrl(url: String): String = withContext(Dispatchers.IO) {
        GrabitApp.ytdlReady.await()
        Log.d(TAG, "Extracting stream URL for: $url")

        val request = YoutubeDLRequest(url)
        request.addOption("--get-url")
        // Prefer a single muxed stream (video+audio) for direct playback
        request.addOption("-f", "best[ext=mp4]/best")
        request.addOption("--no-playlist")
        request.addOption("--no-check-certificates")

        val response = YoutubeDL.getInstance().execute(request)
        val streamUrl = response.out?.trim()?.lines()?.firstOrNull()

        if (streamUrl.isNullOrBlank()) {
            throw Exception("Could not extract stream URL")
        }

        Log.d(TAG, "Stream URL extracted (${streamUrl.length} chars)")
        streamUrl
    }

    fun isSupportedUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }
}
