package app.snaptok.download

import android.content.Context
import app.snaptok.data.DownloadJob
import app.snaptok.core.VideoQuality
import app.snaptok.core.AvailableQualities
import app.snaptok.core.MediaChoice
import com.yausername.ffmpeg.FFmpeg
import java.util.UUID
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File

/** Native extraction stays behind this boundary so the provider can be replaced. */
interface DownloadEngine {
    suspend fun download(job: DownloadJob, directory: File, progress: (Int) -> Unit): DownloadedVideo
}
data class DownloadedVideo(val file: File, val title: String, val creator: String = "", val caption: String = "", val sourceId: String = "", val audioOnly: Boolean = false) {
    val mimeType: String get() = if (audioOnly) "audio/mp4" else "video/mp4"
    val extension: String get() = if (audioOnly) "m4a" else "mp4"
}

class YtDlpEngine(private val context: Context) : DownloadEngine {
    private val mutex = Mutex()
    override suspend fun download(job: DownloadJob, directory: File, progress: (Int) -> Unit): DownloadedVideo = mutex.withLock {
        withTimeout(60 * 60 * 1000L) { runInterruptible(Dispatchers.IO) {
            YoutubeDL.init(context)
            FFmpeg.init(context)
            check(context.cacheDir.usableSpace > 16 * 1024 * 1024) { "No space left for staging" }
            check(directory.exists() || directory.mkdirs()) { "Could not create download folder" }
            val request = YoutubeDLRequest(job.url).apply {
                addOption("--no-playlist")
                addOption("--match-filter", "!is_live")
                addOption("--playlist-items", job.itemIndex.toString())
                addOption("--no-mtime")
                addOption("--newline")
                addOption("--socket-timeout", 20)
                addOption("--retries", 2)
                addOption("--fragment-retries", 2)
                addOption("--max-filesize", "${job.maxSizeMb}M")
                addOption("--write-info-json")
                addOption("--no-write-playlist-metafiles")
                if (job.audioOnly) {
                    addOption("-f", "bestaudio/best")
                    addOption("--extract-audio")
                    addOption("--audio-format", "m4a")
                } else {
                    addOption("-f", VideoQuality.selector(job.qualityHeight))
                    addOption("--merge-output-format", "mp4")
                }
                val quickjs = File(context.applicationInfo.nativeLibraryDir, "libqjs.so")
                if (quickjs.isFile) addOption("--js-runtimes", "quickjs:${quickjs.absolutePath}")
                VideoQuality.sort(job.qualityHeight)?.let { addOption("-S", it) }
                addOption("-o", File(directory, "video.%(ext)s").absolutePath)
            }
            YoutubeDL.execute(request, job.id) { value, _, _ -> progress(value.toInt().coerceIn(0, 100)) }
            val file = File(directory, if (job.audioOnly) "video.m4a" else "video.mp4")
            check(file.isFile) { "No downloadable media returned" }
            check(file.length() <= job.maxSizeMb.toLong() * 1024 * 1024) { "Video exceeds the selected file size limit" }
            val info = File(directory, "video.info.json")
            val metadata = runCatching { JSONObject(info.readText()) }.getOrNull()
            val title = metadata?.optString("title")?.takeUnless { it == "null" }?.take(160).orEmpty().ifBlank { "Video" }
            val formats = metadata?.optJSONArray("formats")
            val sourceReportsAudio = (0 until (formats?.length() ?: 0)).any { index ->
                formats!!.getJSONObject(index).optString("acodec").let { it.isNotBlank() && it != "none" && it != "null" }
            }
            if (sourceReportsAudio || job.audioOnly) android.media.MediaMetadataRetriever().use { reader ->
                reader.setDataSource(file.absolutePath)
                if (reader.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != "yes")
                    throw IllegalStateException("Audio verification failed after merging")
            }
            DownloadedVideo(file, title, metadata?.optString("uploader")?.takeUnless { it == "null" }.orEmpty().take(200), metadata?.optString("description")?.takeUnless { it == "null" }.orEmpty().take(4000),
                metadata?.let { info ->
                    val provider = info.optString("extractor_key").takeUnless { it.isBlank() || it == "null" }
                    val identity = info.optString("id").takeUnless { it.isBlank() || it == "null" }
                    if (provider != null && identity != null) "$provider:$identity" else ""
                }.orEmpty(), job.audioOnly)
        } }
    }

    suspend fun inspect(url: String): AvailableQualities = mutex.withLock {
        withTimeout(90_000L) { runInterruptible(Dispatchers.IO) {
            YoutubeDL.init(context)
            val request = YoutubeDLRequest(url).apply {
                addOption("--dump-single-json")
                addOption("--skip-download")
                addOption("--no-playlist")
                addOption("--playlist-end", "50")
                addOption("--socket-timeout", 20)
                addOption("--retries", 1)
            }
            val info = JSONObject(YoutubeDL.execute(request, "inspect-${UUID.randomUUID()}", null).out)
            val video = info.optJSONArray("entries")?.optJSONObject(0) ?: info
            val formats = video.optJSONArray("formats")
            val available = (0 until (formats?.length() ?: 0)).map { index ->
                val format = formats!!.getJSONObject(index)
                VideoQuality.format(format.optString("ext"), format.optInt("height"), format.optInt("width"),
                    format.optString("vcodec").takeIf { it.isNotBlank() && it != "null" },
                    format.optString("acodec").takeIf { it.isNotBlank() && it != "null" })
            }
            val entries = info.optJSONArray("entries")
            val choices = if (entries == null) listOf(MediaChoice(1, video.optString("title", "Video"))) else
                (0 until entries.length()).mapNotNull { i -> entries.optJSONObject(i)?.let { MediaChoice(i + 1, it.optString("title", "Video ${i + 1}")) } }
            AvailableQualities(url, video.optString("title", "Video"), VideoQuality.heights(available), choices)
        } }
    }

    suspend fun update(): String = mutex.withLock {
        runInterruptible(Dispatchers.IO) {
            YoutubeDL.init(context)
            YoutubeDL.updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
            YoutubeDL.versionName(context) ?: "Latest stable engine"
        }
    }
    fun version(): String = YoutubeDL.versionName(context) ?: "Bundled · 2026.08.19"
}
