package com.raouf.grabit.domain.model

data class VideoInfo(
    val url: String,
    val title: String,
    val thumbnail: String?,
    val duration: Long?,
    val uploader: String?,
    val source: VideoSource,
    val formats: List<VideoFormat>,
    val subtitleLanguages: List<String> = emptyList(),
)

data class VideoFormat(
    val formatId: String,
    val ext: String,
    val quality: String,
    val filesize: Long?,
    val isAudioOnly: Boolean,
)

data class PlaylistInfo(
    val title: String,
    val uploader: String?,
    val entries: List<PlaylistEntry>,
)

data class PlaylistEntry(
    val url: String,
    val title: String,
    val thumbnail: String?,
    val duration: Long?,
)

enum class VideoSource(val displayName: String, val folder: String) {
    YOUTUBE("YouTube", "YouTube"),
    FACEBOOK("Facebook", "Facebook"),
    INSTAGRAM("Instagram", "Instagram"),
    TIKTOK("TikTok", "TikTok"),
    TWITTER("Twitter", "Twitter"),
    LINKEDIN("LinkedIn", "LinkedIn"),
    OTHER("Other", "Other");

    companion object {
        fun fromUrl(url: String): VideoSource = when {
            "youtube.com" in url || "youtu.be" in url -> YOUTUBE
            "facebook.com" in url || "fb.watch" in url -> FACEBOOK
            "instagram.com" in url -> INSTAGRAM
            "tiktok.com" in url -> TIKTOK
            "twitter.com" in url || "x.com" in url -> TWITTER
            "linkedin.com" in url -> LINKEDIN
            else -> OTHER
        }
    }
}
