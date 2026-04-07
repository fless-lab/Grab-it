package com.raouf.grabit.data.downloader

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.raouf.grabit.data.db.DownloadDao
import com.raouf.grabit.data.prefs.UserPreferences
import com.raouf.grabit.domain.model.DownloadStatus
import com.raouf.grabit.domain.model.VideoSource
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    companion object {
        private const val TAG = "DownloadManager"
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 5_000L
    }

    private val _activeProgress = MutableStateFlow<Map<Long, DownloadProgress>>(emptyMap())
    val activeProgress = _activeProgress.asStateFlow()

    private val tempDir: File
        get() = File(context.cacheDir, "grabit_tmp").also { it.mkdirs() }

    private fun processId(id: Long) = "grabit_dl_$id"

    suspend fun startDownload(
        id: Long,
        url: String,
        title: String,
        source: VideoSource,
        formatId: String,
        isAudioOnly: Boolean,
        subLangs: String? = null,
        onProgress: (Float) -> Unit = {},
    ): Result<String> = withContext(Dispatchers.IO) {
        // WiFi-only check
        val wifiOnly = prefs.wifiOnly.first()
        if (wifiOnly && !isOnWifi()) {
            dao.updateProgress(id, DownloadStatus.WAITING_NETWORK.name, 0f)
            return@withContext Result.failure(Exception("Waiting for WiFi"))
        }

        com.raouf.grabit.GrabitApp.ytdlReady.await()
        // Keep current progress on resume, only set 0 for fresh downloads
        val existing = dao.getById(id)
        val startProgress = existing?.progress?.takeIf { it > 0f } ?: 0f
        dao.updateProgress(id, DownloadStatus.DOWNLOADING.name, startProgress)

        val sanitizedTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)

        var lastError: Exception? = null
        var lastProgress = startProgress

        for (attempt in 1..MAX_RETRIES) {
            try {
                val tmpFile = File(tempDir, sanitizedTitle)
                val request = YoutubeDLRequest(url)
                request.addOption("-o", tmpFile.absolutePath + ".%(ext)s")
                request.addOption("--no-playlist")
                request.addOption("--continue")
                request.addOption("--no-check-certificates")
                request.addOption("--no-mtime")

                if (isAudioOnly) {
                    request.addOption("-x")
                    request.addOption("--audio-format", "mp3")
                } else {
                    request.addOption("-f", formatId)
                    request.addOption("--merge-output-format", "mp4")
                }

                // Subtitles: write + embed into video
                if (!subLangs.isNullOrBlank()) {
                    request.addOption("--write-subs")
                    request.addOption("--embed-subs")
                    request.addOption("--sub-langs", subLangs)
                    request.addOption("--convert-subs", "srt")
                }

                val pid = processId(id)
                Log.d(TAG, "Starting download $id attempt $attempt/$MAX_RETRIES (pid=$pid): $title")

                var tempPathSaved = false
                val response = YoutubeDL.getInstance().execute(
                    request, pid
                ) { progress, eta, line ->
                    val rawPct = (progress / 100f).coerceIn(0f, 1f)
                    val pct = maxOf(rawPct, lastProgress)
                    lastProgress = pct
                    val speed = parseSpeed(line)
                    onProgress(pct)
                    _activeProgress.value = _activeProgress.value + (id to DownloadProgress(id, pct, eta.toLong(), speed))
                    kotlinx.coroutines.runBlocking {
                        dao.updateProgress(id, DownloadStatus.DOWNLOADING.name, pct)
                        // Save temp file path on first progress so user can stream while downloading
                        if (!tempPathSaved && pct > 0f) {
                            val tempFiles = tempDir.listFiles { f -> f.name.startsWith(sanitizedTitle) }
                            // Prefer non-.part files, fallback to .part
                            val tempFile = tempFiles
                                ?.sortedWith(compareBy<File> { it.name.endsWith(".part") }.thenByDescending { it.length() })
                                ?.firstOrNull { it.length() > 0 }
                            if (tempFile != null) {
                                dao.updateFilePath(id, tempFile.absolutePath)
                                tempPathSaved = true
                                Log.d(TAG, "Saved temp path for streaming: ${tempFile.name}")
                            }
                        }
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
                Log.d(TAG, "Download $id completed: $finalPath")

                return@withContext Result.success(finalPath)
            } catch (e: Exception) {
                lastError = e
                // Check if paused (user action, not a real error)
                val current = dao.getById(id)
                if (current?.status == DownloadStatus.PAUSED.name) {
                    _activeProgress.value = _activeProgress.value - id
                    Log.d(TAG, "Download $id paused by user")
                    return@withContext Result.failure(e)
                }

                val isNetworkError = isNetworkError(e)
                _activeProgress.value = _activeProgress.value - id

                if (isNetworkError) {
                    // Network error: fail fast, go straight to WAITING_NETWORK
                    // No retries — the network callback will auto-resume when connectivity returns
                    dao.updateProgress(id, DownloadStatus.WAITING_NETWORK.name, lastProgress)
                    Log.w(TAG, "Download $id no network, waiting at ${(lastProgress * 100).toInt()}%")
                    return@withContext Result.failure(e)
                }

                // Non-network error (server issue, format unavailable, etc.): retry
                if (attempt < MAX_RETRIES) {
                    Log.w(TAG, "Download $id error, retrying in ${RETRY_DELAY_MS / 1000}s (attempt $attempt/$MAX_RETRIES)")
                    _activeProgress.value = _activeProgress.value + (id to DownloadProgress(id, lastProgress))
                    delay(RETRY_DELAY_MS)
                    continue
                }

                dao.markFailed(id, ErrorParser.friendlyMessage(e.message))
                Log.e(TAG, "Download $id failed: ${e.message}")
                return@withContext Result.failure(e)
            }
        }

        // Should not reach here, but just in case
        _activeProgress.value = _activeProgress.value - id
        Result.failure(lastError ?: Exception("Download failed"))
    }

    private fun parseSpeed(line: String): Long? {
        // yt-dlp output: "[download]  45.2% of 50.00MiB at 2.50MiB/s ETA 00:12"
        val regex = Regex("""at\s+([\d.]+)\s*(KiB|MiB|GiB|B)/s""")
        val match = regex.find(line) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = match.groupValues[2]
        return when (unit) {
            "B" -> value.toLong()
            "KiB" -> (value * 1024).toLong()
            "MiB" -> (value * 1024 * 1024).toLong()
            "GiB" -> (value * 1024 * 1024 * 1024).toLong()
            else -> null
        }
    }

    private fun isNetworkError(e: Exception): Boolean {
        val msg = e.message?.lowercase() ?: return false
        return "no address associated" in msg
                || "network" in msg
                || "timeout" in msg
                || "connection" in msg
                || "errno 7" in msg
                || "unreachable" in msg
    }

    fun pauseDownload(id: Long) {
        val pid = processId(id)
        Log.d(TAG, "Pausing download $id (pid=$pid)")
        try {
            YoutubeDL.getInstance().destroyProcessById(pid)
        } catch (e: Exception) {
            Log.w(TAG, "Pause error: ${e.message}")
        }
        kotlinx.coroutines.runBlocking {
            dao.updateStatus(id, DownloadStatus.PAUSED.name)
        }
        _activeProgress.value = _activeProgress.value - id
    }

    private suspend fun moveToOutputDir(file: File, source: VideoSource, isAudioOnly: Boolean): String {
        val dirUriStr = prefs.downloadDirUri.first()
        if (dirUriStr != null) {
            try {
                val treeUri = Uri.parse(dirUriStr)
                val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                if (rootDoc != null && rootDoc.canWrite()) {
                    // Always use Grabit parent folder
                    val grabitDir = rootDoc.findFile("Grabit") ?: rootDoc.createDirectory("Grabit")

                    val autoSub = prefs.autoSubfolder.first()
                    val subName = if (autoSub) {
                        if (isAudioOnly) "Audio" else source.folder
                    } else null

                    val targetDir = if (subName != null && grabitDir != null) {
                        grabitDir.findFile(subName) ?: grabitDir.createDirectory(subName)
                    } else grabitDir

                    // .nomedia to hide from gallery
                    ensureNomedia(targetDir)

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

        // Fallback: save to public Downloads/Grabit/ via MediaStore
        return saveToPublicDownloads(file, source, isAudioOnly)
    }

    private fun saveToPublicDownloads(file: File, source: VideoSource, isAudioOnly: Boolean): String {
        val autoSub = kotlinx.coroutines.runBlocking { prefs.autoSubfolder.first() }
        val subFolder = if (autoSub) {
            if (isAudioOnly) "Audio" else source.folder
        } else null
        val relativePath = if (subFolder != null) "Download/Grabit/$subFolder" else "Download/Grabit"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+: use MediaStore.Downloads (allows Download/ directory)
            val mimeType = if (file.extension == "mp3") "audio/mpeg" else "video/mp4"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = context.contentResolver.insert(collection, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { inp -> inp.copyTo(out) }
                }
                file.delete()
                Log.d(TAG, "Saved to public Downloads: $relativePath/${file.name}")
                return uri.toString()
            }
        } else {
            // Android 9 and below: direct file access
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Grabit")
            val targetDir = if (subFolder != null) File(dir, subFolder) else dir
            targetDir.mkdirs()
            val dest = File(targetDir, file.name)
            file.copyTo(dest, overwrite = true)
            file.delete()
            return dest.absolutePath
        }

        // Last resort: keep in temp
        return file.absolutePath
    }

    private suspend fun ensureNomedia(dir: DocumentFile?) {
        if (dir == null) return
        val hideFromGallery = prefs.hideFromGallery.first()
        if (!hideFromGallery) return
        if (dir.findFile(".nomedia") == null) {
            dir.createFile("application/octet-stream", ".nomedia")
        }
    }

    private fun isOnWifi(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
