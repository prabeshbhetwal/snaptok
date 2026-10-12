package app.snaptok.download

import android.media.MediaMetadataRetriever
import androidx.test.platform.app.InstrumentationRegistry
import app.snaptok.core.VideoLink
import app.snaptok.data.DownloadJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class LiveSourceTest {
    @Test fun optionalLiveDownload() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val raw = args.getString("mediaUrl")
        assumeTrue("Explicit live source URL required", raw != null)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "live-v04-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val job = DownloadJob(UUID.randomUUID().toString(), VideoLink.parse(raw!!).url, qualityHeight = 360, maxSizeMb = 100)
            val media = withTimeout(180000) { YtDlpEngine(context).download(job, directory) {} }
            assertTrue(media.file.length() > 1024)
            MediaMetadataRetriever().use { probe ->
                probe.setDataSource(media.file.absolutePath)
                assertEquals("yes", probe.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
                if (args.getString("requireAudio") == "true") assertEquals("yes", probe.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
            }
        } finally { directory.deleteRecursively() }
    }
}
