package com.raouf.grabit.data.downloader

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.raouf.grabit.data.db.DownloadDao
import com.raouf.grabit.data.db.DownloadEntity
import com.raouf.grabit.data.prefs.UserPreferences
import com.raouf.grabit.domain.model.DownloadStatus
import com.raouf.grabit.domain.model.VideoSource
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadProgress(
    val id: Long,
    val progress: Float,
    val etaSeconds: Long? = null,
    val speedBytes: Long? = null,
)

@Singleton
class GrabitDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
    private val prefs: UserPreferences,
) {
    private val _activeProgress = MutableStateFlow<Map<Long, DownloadProgress>>(emptyMap())
    val activeProgress = _activeProgress.asStateFlow()

    private val tempDir: File
        get() = File(context.cacheDir, "grabit_tmp").also { it.mkdirs() }

    suspend fun startDownload(
        id: Long,
        url: String,
        title: String,
        source: VideoSource,
        formatId: String,
        isAudioOnly: Boolean,
        onProgress: (Float) -> Unit = {},
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Ensure yt-dlp is ready
            com.raouf.grabit.GrabitApp.ytdlReady.await()
            dao.updateProgress(id, DownloadStatus.DOWNLOADING.name, 0f)

            val sanitizedTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)
            val tmpFile = File(tempDir, sanitizedTitle)

            val request = YoutubeDLRequest(url)
            request.addOption("-o", tmpFile.absolutePath + ".%(ext)s")
            request.addOption("--no-playlist")
            request.addOption("--continue")

            if (isAudioOnly) {
                request.addOption("-x")
                request.addOption("--audio-format", "mp3")
            } else {
                request.addOption("-f", formatId)
                request.addOption("--merge-output-format", "mp4")
            }

            val response = YoutubeDL.getInstance().execute(
                request
            ) { progress, eta, _ ->
                val pct = progress / 100f
                onProgress(pct)
                _activeProgress.value = _activeProgress.value + (id to DownloadProgress(id, pct, eta.toLong()))
                kotlinx.coroutines.runBlocking {
                    dao.updateProgress(id, DownloadStatus.DOWNLOADING.name, pct)
                }
            }

            // Find the output file
            val outputFiles = tempDir.listFiles { f -> f.name.startsWith(sanitizedTitle) }
            val outputFile = outputFiles?.maxByOrNull { it.lastModified() }
                ?: return@withContext Result.failure(Exception("Download completed but file not found"))

            // Move to user-selected directory
            val finalPath = moveToOutputDir(outputFile, source, isAudioOnly)

            _activeProgress.value = _activeProgress.value - id
            dao.markCompleted(id, finalPath, outputFile.length())

            Result.success(finalPath)
        } catch (e: Exception) {
            _activeProgress.value = _activeProgress.value - id
            dao.markFailed(id, e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    private suspend fun moveToOutputDir(file: File, source: VideoSource, isAudioOnly: Boolean): String {
        val dirUriStr = prefs.downloadDirUri.first()
        if (dirUriStr != null) {
            try {
                val treeUri = Uri.parse(dirUriStr)
                val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                if (rootDoc != null && rootDoc.canWrite()) {
                    val autoSub = prefs.autoSubfolder.first()
                    val subName = if (autoSub) {
                        if (isAudioOnly) "Audio" else source.folder
                    } else null

                    val targetDir = if (subName != null) {
                        rootDoc.findFile(subName) ?: rootDoc.createDirectory(subName)
                    } else rootDoc

                    val mimeType = if (file.extension == "mp3") "audio/mpeg" else "video/mp4"
                    val docFile = targetDir?.createFile(mimeType, file.nameWithoutExtension)
                    if (docFile != null) {
                        context.contentResolver.openOutputStream(docFile.uri)?.use { out ->
                            file.inputStream().use { inp -> inp.copyTo(out) }
                        }
                        file.delete()
                        return docFile.uri.toString()
                    }
                }
            } catch (_: Exception) { }
        }

        // Fallback: keep in temp
        return file.absolutePath
    }
}
