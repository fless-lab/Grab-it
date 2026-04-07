package com.raouf.grabit.data.downloader

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

    suspend fun extract(url: String): VideoInfo = withContext(Dispatchers.IO) {
        // Wait for yt-dlp to be initialized
        val ready = GrabitApp.ytdlReady.await()
        if (!ready) throw Exception("yt-dlp failed to initialize")

        val request = YoutubeDLRequest(url)
        request.addOption("--dump-json")
        request.addOption("--no-download")
        request.addOption("--no-playlist")

        val response = YoutubeDL.getInstance().execute(request)
        val json = com.google.gson.JsonParser.parseString(response.out).asJsonObject

        val title = json.get("title")?.asString ?: "Untitled"
        val thumbnail = json.get("thumbnail")?.asString
        val duration = json.get("duration")?.asLong
        val uploader = json.get("uploader")?.asString
        val source = VideoSource.fromUrl(url)

        val formats = mutableListOf<VideoFormat>()
        val formatsJson = json.getAsJsonArray("formats")
        if (formatsJson != null) {
            // Collect best video formats by resolution
            val seen = mutableSetOf<String>()
            for (f in formatsJson) {
                val obj = f.asJsonObject
                val ext = obj.get("ext")?.asString ?: continue
                val height = obj.get("height")?.asInt
                val acodec = obj.get("acodec")?.asString ?: "none"
                val vcodec = obj.get("vcodec")?.asString ?: "none"
                val filesize = obj.get("filesize")?.asLong

                val isAudioOnly = vcodec == "none" && acodec != "none"
                val quality = when {
                    isAudioOnly -> "Audio"
                    height != null -> "${height}p"
                    else -> continue
                }

                val key = "$quality-$ext"
                if (key in seen) continue
                seen.add(key)

                formats.add(
                    VideoFormat(
                        formatId = obj.get("format_id")?.asString ?: "",
                        ext = ext,
                        quality = quality,
                        filesize = filesize,
                        isAudioOnly = isAudioOnly,
                    )
                )
            }
        }

        // Always add "best" and "audio" options
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

    /** Quick check: is this a URL we can likely handle? */
    fun isSupportedUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }
}
