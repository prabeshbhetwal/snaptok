package app.snaptok.download

import android.content.Context
import app.snaptok.data.DownloadJob
import app.snaptok.core.VideoQuality
import app.snaptok.core.AvailableQualities
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
data class DownloadedVideo(val file: File, val title: String)

class YtDlpEngine(private val context: Context) : DownloadEngine {
    private val mutex = Mutex()
    override suspend fun download(job: DownloadJob, directory: File, progress: (Int) -> Unit): DownloadedVideo = mutex.withLock {
        withTimeout(8 * 60 * 1000L) { runInterruptible(Dispatchers.IO) {
            YoutubeDL.init(context)
            check(directory.exists() || directory.mkdirs()) { "Could not create download folder" }
            val request = YoutubeDLRequest(job.url).apply {
                addOption("--no-playlist")
                addOption("--playlist-items", "1")
                addOption("--no-mtime")
                addOption("--newline")
                addOption("--socket-timeout", 20)
                addOption("--retries", 2)
                addOption("--fragment-retries", 2)
                addOption("--max-filesize", "1G")
                addOption("--write-info-json")
                addOption("--no-write-playlist-metafiles")
                // Select one already muxed MP4; no FFmpeg or server is required.
                addOption("-f", VideoQuality.selector(job.qualityHeight))
                VideoQuality.sort(job.qualityHeight)?.let { addOption("-S", it) }
                addOption("-o", File(directory, "video.%(ext)s").absolutePath)
            }
            YoutubeDL.execute(request, job.id) { value, _, _ -> progress(value.toInt().coerceIn(0, 100)) }
            val file = File(directory, "video.mp4")
            val info = File(directory, "video.info.json")
            val title = runCatching { JSONObject(info.readText()).optString("title").take(160) }
                .getOrDefault("").ifBlank { "Video" }
            DownloadedVideo(file, title)
        } }
    }

    suspend fun inspect(url: String): AvailableQualities = mutex.withLock {
        withTimeout(90_000L) { runInterruptible(Dispatchers.IO) {
            YoutubeDL.init(context)
            val request = YoutubeDLRequest(url).apply {
                addOption("--dump-single-json")
                addOption("--skip-download")
                addOption("--no-playlist")
                addOption("--playlist-items", "1")
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
            AvailableQualities(url, video.optString("title", "Video"), VideoQuality.heights(available))
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
