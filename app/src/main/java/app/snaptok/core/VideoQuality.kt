package app.snaptok.core

/** Use the shorter edge so portrait 1080×1920 videos are labelled 1080p. */
object VideoQuality {
    val defaults = listOf(0, 2160, 1440, 1080, 720, 480, 360, 240)
    fun label(height: Int) = if (height == 0) "Best available" else "${height}p"
    fun selector(height: Int): String {
        require(height == 0 || height in 1..4320) { "Unsupported quality preference" }
        return "best[ext=mp4]/bestvideo[ext=mp4]"
    }
    fun sort(height: Int): String? = if (height == 0) null else "res:$height"
    fun format(extension: String, height: Int, width: Int, videoCodec: String?, audioCodec: String?) =
        VideoFormat(extension, height, videoCodec != "none" && (height > 0 || width > 0 || videoCodec != null), audioCodec != "none", width)
    fun heights(formats: List<VideoFormat>): List<Int> {
        val videos = formats.filter { it.extension == "mp4" && it.hasVideo && it.resolution in 1..4320 }
        val withAudio = videos.filter { it.hasAudio }
        return (withAudio.ifEmpty { videos }).map { it.resolution }.distinct().sortedDescending()
    }
}
data class VideoFormat(val extension: String, val height: Int, val hasVideo: Boolean, val hasAudio: Boolean, val width: Int = height) {
    val resolution: Int get() = if (width > 0) minOf(width, height) else height
}
data class AvailableQualities(val url: String, val title: String, val heights: List<Int>)
