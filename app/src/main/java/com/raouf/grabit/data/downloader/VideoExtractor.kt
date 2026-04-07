package com.raouf.grabit.data.downloader

import android.util.Log
import com.raouf.grabit.GrabitApp
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
    }

    suspend fun extract(url: String): VideoInfo = withContext(Dispatchers.IO) {
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
            throw Exception("Download failed: ${e.message}")
        }

        val stdout = response.out
        val stderr = response.err

        Log.d(TAG, "stdout length: ${stdout?.length ?: 0}")
        Log.d(TAG, "stderr: ${stderr?.take(500)}")

        if (stdout.isNullOrBlank()) {
            val errorMsg = stderr?.takeIf { it.isNotBlank() } ?: "No response from yt-dlp"
            Log.e(TAG, "Empty stdout. stderr: $errorMsg")
            throw Exception(errorMsg)
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

        val standardFormats = mutableListOf(
            VideoFormat("best", "mp4", "Best", null, false),
            VideoFormat("best[height<=720]", "mp4", "720p", null, false),
            VideoFormat("best[height<=480]", "mp4", "480p", null, false),
            VideoFormat("bestaudio", "mp3", "Audio MP3", null, true),
        )

        VideoInfo(
            url = url,
            title = title,
            thumbnail = thumbnail,
            duration = duration,
            uploader = uploader,
            source = source,
            formats = standardFormats,
        )
    }

    fun isSupportedUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }
}
