package app.snaptok.download

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import app.snaptok.core.VideoQuality

class MediaPipelineTest {
    @Test fun nativeMergerCombinesSeparateAudioAndVideo() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = instrument.targetContext
        val root = File(context.cacheDir, "merge-fixture").apply { mkdirs() }
        try {
            for (name in listOf("video-only.mp4", "audio-only.m4a")) instrument.context.assets.open(name).use { input -> File(root,name).outputStream().use { input.copyTo(it) } }
            YoutubeDL.init(context); FFmpeg.init(context)
            val info = JSONObject().put("id", "fixture").put("title", "Merge fixture").put("extractor", "generic").put("extractor_key", "Generic")
                .put("formats", JSONArray().put(JSONObject().put("format_id", "video").put("url", File(root,"video-only.mp4").toURI().toString()).put("ext", "mp4").put("vcodec", "avc1").put("acodec", "none").put("width",320).put("height",240))
                    .put(JSONObject().put("format_id", "audio").put("url",File(root,"audio-only.m4a").toURI().toString()).put("ext","m4a").put("vcodec","none").put("acodec","aac")))
            val metadata = File(root, "info.json").apply { writeText(info.toString()) }
            val output = File(root,"merged.mp4")
            val request = YoutubeDLRequest(emptyList<String>()).addOption("--load-info-json",metadata.absolutePath).addOption("--enable-file-urls")
                .addOption("-f",VideoQuality.selector(0)).addOption("--merge-output-format","mp4").addOption("-o",output.absolutePath)
            YoutubeDL.execute(request, "merge-fixture", null)
            MediaMetadataRetriever().use { media -> media.setDataSource(output.absolutePath)
                assertEquals("yes",media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
                assertEquals("yes",media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
            }
        } finally { root.deleteRecursively() }
    }
    @Test fun exportAudioAndTrimPreserveOriginal() = runBlocking {
        val instrument = InstrumentationRegistry.getInstrumentation(); val context = instrument.targetContext
        val input = File(context.cacheDir,"edit-source.mp4")
        instrument.context.assets.open("sample-av.mp4").use { stream -> input.outputStream().use { stream.copyTo(it) } }
        val original = input.readBytes()
        val clipped = File(context.cacheDir,"clipped-test.mp4")
        try {
            MediaTools.export(context, Uri.fromFile(input), clipped, 0, 1000, false)
            MediaMetadataRetriever().use { media -> media.setDataSource(clipped.absolutePath)
                val duration = media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                assertTrue(duration in 1..1500)
            }
            val audio = File(context.cacheDir,"audio-export-test.m4a")
            try {
                MediaTools.export(context, Uri.fromFile(input), audio, 0, null, true)
                MediaMetadataRetriever().use { media -> media.setDataSource(audio.absolutePath)
                    assertEquals("yes",media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
                    assertNotEquals("yes",media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
                }
            } finally { audio.delete() }
            assertArrayEquals(original, input.readBytes())
        } finally { input.delete(); clipped.delete() }
    }
}
