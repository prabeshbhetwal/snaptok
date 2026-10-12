package app.snaptok.download

import android.content.Context
import android.graphics.BitmapFactory
import app.snaptok.data.DownloadJob
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class PhotoPostException(message: String) : IOException(message)
data class PhotoPost(val id: String, val title: String, val creator: String, val caption: String, val images: List<List<String>>, val music: String?)

object PhotoMetadata {
    private val script = Regex("<script[^>]+id=[\"']__UNIVERSAL_DATA_FOR_REHYDRATION__[\"'][^>]*>(.*?)</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    fun parse(html: String, expectedId: String): PhotoPost {
        val raw = script.find(html)?.groupValues?.get(1) ?: throw PhotoPostException("TikTok did not return this photo post. It may be unavailable or temporarily restricted. Try again later.")
        val item = JSONObject(raw).optJSONObject("__DEFAULT_SCOPE__")?.optJSONObject("webapp.video-detail")?.optJSONObject("itemInfo")?.optJSONObject("itemStruct")
            ?: throw PhotoPostException("TikTok did not return this photo post. Open the original link to check availability.")
        if (item.optString("id") != expectedId) throw PhotoPostException("The source returned a different post. Nothing was saved.")
        val images = item.optJSONObject("imagePost")?.optJSONArray("images") ?: throw PhotoPostException("The post did not contain downloadable images.")
        if (images.length() !in 1..50) throw PhotoPostException("Photo posts must contain between 1 and 50 images.")
        val urls = (0 until images.length()).map { index ->
            val list = images.getJSONObject(index).getJSONObject("imageURL").getJSONArray("urlList")
            (0 until list.length()).map { list.getString(it) }.filter(::mediaURL).take(4).also {
                if (it.isEmpty()) throw PhotoPostException("Image ${index + 1} is unavailable. The album was not saved.")
            }
        }
        val caption = item.optString("desc").take(4000)
        return PhotoPost(expectedId, caption.take(100).ifBlank { "TikTok photos" }, item.optJSONObject("author")?.optString("uniqueId").orEmpty(), caption,
            urls, item.optJSONObject("music")?.optString("playUrl")?.takeIf(::mediaURL))
    }
    fun parseEmbed(html: String, expectedId: String): PhotoPost {
        val script = Regex("<script[^>]+id=[\"']__FRONTITY_CONNECT_STATE__[\"'][^>]*>(.*?)</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val raw = script.find(html)?.groupValues?.get(1) ?: throw PhotoPostException("TikTok did not return this photo post. Open the original link and try again later.")
        val data = JSONObject(raw).optJSONObject("source")?.optJSONObject("data")?.optJSONObject("/embed/v2/$expectedId")?.optJSONObject("videoData")
            ?: throw PhotoPostException("The photo post is not publicly available right now.")
        val item = data.optJSONObject("itemInfos")
        if (item?.optString("id") != expectedId) throw PhotoPostException("The source returned a different post. Nothing was saved.")
        val images = data.optJSONObject("imagePostInfo")?.optJSONArray("displayImages") ?: throw PhotoPostException("No public images were returned.")
        if (images.length() !in 1..50) throw PhotoPostException("Photo posts must contain between 1 and 50 images.")
        val urls = (0 until images.length()).map { index ->
            val list = images.getJSONObject(index).getJSONArray("urlList")
            (0 until list.length()).map { list.getString(it) }.filter(::mediaURL).take(4).also { if (it.isEmpty()) throw PhotoPostException("An image in this post is unavailable.") }
        }
        val sound = data.optJSONObject("musicInfos")?.opt("playUrl")
        val music = when (sound) { is String -> sound; is org.json.JSONArray -> sound.optString(0); else -> null }?.takeIf(::mediaURL)
        val caption = item.optString("text").take(4000)
        return PhotoPost(expectedId, caption.take(100).ifBlank { "TikTok photos" }, data.optJSONObject("authorInfos")?.optString("uniqueId").orEmpty(), caption, urls, music)
    }
    fun mediaURL(value: String): Boolean = runCatching {
        val uri = URI(value); val host = uri.host?.lowercase().orEmpty()
        uri.scheme == "https" && uri.userInfo == null && uri.port == -1 &&
            listOf("tiktokcdn.com", "tiktokcdn-us.com", "tiktokv.com", "byteoversea.com", "ibytedtos.com", "byteimg.com", "musical.ly").any { host == it || host.endsWith(".$it") }
    }.getOrDefault(false)
}

/** Public post metadata only. No account, cookies, third-party API or profile scraping. */
class PhotoDownloader(private val context: Context) {
    suspend fun downloadIfPhoto(job: DownloadJob, directory: File, progress: (Int) -> Unit): DownloadedVideo? {
        val host = URI(job.url).host.orEmpty()
        if (host != "tiktok.com" && !host.endsWith(".tiktok.com")) return null
        var canonical = job.url
        if (host in listOf("vt.tiktok.com", "vm.tiktok.com") || URI(job.url).path.startsWith("/t/")) {
            canonical = runInterruptible(Dispatchers.IO) { connection(job.url, page = true).let { connection -> try { connection.url.toString() } finally { connection.disconnect() } } }
        }
        val photoPath = Regex("/@[^/]+/photo/([0-9]{6,})/?").matchEntire(URI(canonical).path) ?: return null
        if (job.audioOnly) throw PhotoPostException("This is a photo post. Turn off Audio only and choose Images or Slideshow.")
        directory.mkdirs()
        val id = photoPath.groupValues[1]
        val page = "https://www.tiktok.com" + URI(canonical).path.replace("/photo/", "/video/")
        val post = runInterruptible(Dispatchers.IO) {
            fun readPage(address: String): String {
            val c = connection(address, page = true)
            try {
                val bytes = c.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                    while (out.size() <= 2 * 1024 * 1024) { val count = input.read(buffer); if (count < 0) break; out.write(buffer, 0, count) }
                    out.toByteArray()
                }
                if (bytes.size > 2 * 1024 * 1024) throw PhotoPostException("The post response was too large.")
                return bytes.toString(Charsets.UTF_8)
            } finally { c.disconnect() }
            }
            try { PhotoMetadata.parse(readPage(page), id) }
            catch (error: java.io.InterruptedIOException) { throw error }
            catch (_: Exception) { PhotoMetadata.parseEmbed(readPage("https://www.tiktok.com/embed/v2/$id"), id) }
        }
        val maximum = job.maxSizeMb.toLong() * 1024 * 1024
        var used = 0L
        val images = post.images.mapIndexed { index, candidates ->
            currentCoroutineContext().ensureActive()
            val part = File(directory, "photo-${(index + 1).toString().padStart(3, '0')}.image")
            runInterruptible(Dispatchers.IO) { fetch(candidates, part, minOf(40L * 1024 * 1024, maximum - used)) }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(part.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong() * bounds.outHeight > 80_000_000)
                throw PhotoPostException("Image ${index + 1} did not pass validation. The album was not saved.")
            val extension = when (bounds.outMimeType) { "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/webp" -> "webp"; else -> throw PhotoPostException("Image ${index + 1} uses an unsupported format.") }
            val image = File(directory, "photo-${(index + 1).toString().padStart(3, '0')}.$extension")
            part.copyTo(image, overwrite = true); used += image.length()
            progress(5 + ((index + 1) * 65 / post.images.size))
            image
        }
        var slideshow: File? = null
        if (job.photoMode != "images") {
            val music = if (job.slideMusic) {
                val url = post.music ?: throw PhotoPostException("This post has no accessible music. Choose Images, or turn off Include music, then use Retry with current defaults.")
                File(directory, "soundtrack.audio").also { file ->
                    try { runInterruptible(Dispatchers.IO) { fetch(listOf(url), file, maximum - used) }; used += file.length() }
                    catch (error: CancellationException) { throw error }
                    catch (_: Exception) { throw PhotoPostException("The photos are available, but the music could not be downloaded. Choose Images or turn off Include music, then use Retry with current defaults.") }
                }
            } else null
            slideshow = File(directory, "slideshow.mp4")
            PhotoSlideshow.render(context, images, music, slideshow, job.slideSeconds, job.qualityHeight, progress)
            if (used + slideshow.length() > maximum) throw PhotoPostException("The photo post and slideshow exceed the chosen size limit.")
        }
        val outputs = mutableListOf<DownloadedVideo>()
        slideshow?.let { outputs += DownloadedVideo(it, "Slideshow · ${post.title}", post.creator, post.caption, "TikTok:$id") }
        if (job.photoMode != "slideshow") images.forEachIndexed { index, file ->
            outputs += DownloadedVideo(file, "${index + 1}/${images.size} · ${post.title}", post.creator, post.caption, "TikTok:$id", photo = true)
        }
        progress(100)
        return outputs.first().copy(title = "${images.size} photos${if (slideshow != null) " · Slideshow" else ""} · ${post.title}", extraOutputs = outputs.drop(1))
    }

    private fun fetch(urls: List<String>, file: File, limit: Long) {
        if (limit <= 0) throw PhotoPostException("The photo post exceeds the chosen size limit.")
        var failure: Exception? = null
        for (url in urls) {
            try {
                val c = connection(url, page = false)
                try {
                    if (c.contentLengthLong > limit) throw PhotoPostException("The photo post exceeds the chosen size limit.")
                    c.inputStream.use { input -> file.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024); var total = 0L
                        while (true) {
                            if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
                            val count = input.read(buffer); if (count < 0) break
                            total += count; if (total > limit) throw PhotoPostException("The photo post exceeds the chosen size limit.")
                            out.write(buffer, 0, count)
                        }
                        if (total == 0L) throw IOException("Empty image response")
                        if (c.contentLengthLong >= 0 && total != c.contentLengthLong) throw IOException("Incomplete image transfer")
                    } }
                } finally { c.disconnect() }
                return
            } catch (e: Exception) { failure = e }
        }
        throw failure ?: PhotoPostException("The image could not be downloaded.")
    }
    private fun connection(start: String, page: Boolean): HttpURLConnection {
        var value = start
        repeat(6) {
            val uri = URI(value); val host = uri.host?.lowercase().orEmpty()
            val valid = if (page) uri.scheme == "https" && uri.port == -1 && uri.userInfo == null && (host == "tiktok.com" || host.endsWith(".tiktok.com")) else PhotoMetadata.mediaURL(value)
            if (!valid) throw PhotoPostException("The source redirected outside its supported media hosts.")
            val c = URL(value).openConnection() as HttpURLConnection
            c.connectTimeout = 15000; c.readTimeout = 20000; c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36")
            if (!page) c.setRequestProperty("Referer", "https://www.tiktok.com/")
            val code = c.responseCode
            if (code in 300..399) {
                val location = c.getHeaderField("Location"); c.disconnect()
                value = uri.resolve(location ?: throw IOException("Missing redirect")).toString()
            } else if (code == 200) return c
            else { c.disconnect(); throw PhotoPostException("TikTok could not deliver this post ($code). Try again later.") }
        }
        throw PhotoPostException("The post redirected too many times.")
    }
}
