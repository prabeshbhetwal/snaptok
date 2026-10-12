package app.snaptok.download

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import androidx.test.platform.app.InstrumentationRegistry
import app.snaptok.data.DownloadJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class PhotoPipelineTest {
    @Test fun slideshowPreservesImagesAndLoopsSoundtrack() = runBlocking {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = instrument.targetContext
        val root = File(context.cacheDir, "photo-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val images = listOf(Color.RED, Color.GREEN, Color.BLUE).mapIndexed { index, colour ->
                val file = File(root, "photo-$index.jpg")
                val bitmap = Bitmap.createBitmap(if (index == 1) 300 else 480, if (index == 1) 480 else 300, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(colour); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }; bitmap.recycle(); file
            }
            val originals = images.map { it.readBytes() }
            val audio = File(root, "sound.m4a")
            instrument.context.assets.open("audio-only.m4a").use { input -> audio.outputStream().use { input.copyTo(it) } }
            val output = File(root, "slideshow.mp4")
            PhotoSlideshow.render(context, images, audio, output, 2, 720) {}
            MediaMetadataRetriever().use { media ->
                media.setDataSource(output.absolutePath)
                assertEquals("yes", media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
                assertEquals("yes", media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
                assertTrue(media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() in 5700..6500)
            }
            images.forEachIndexed { index, file -> assertArrayEquals(originals[index], file.readBytes()) }
        } finally { root.deleteRecursively() }
    }
    @Test fun suppliedLivePhotoPost() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val url = args.getString("photoUrl") ?: return@runBlocking
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "photo-live-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val result = PhotoDownloader(context).downloadIfPhoto(DownloadJob(UUID.randomUUID().toString(), url, photoMode = "both", qualityHeight = 480), root) {}!!
            val outputs = listOf(result) + result.extraOutputs
            assertEquals(10, outputs.count { it.photo })
            assertEquals(1, outputs.count { !it.photo })
            MediaMetadataRetriever().use { media -> media.setDataSource(outputs.first { !it.photo }.file.absolutePath)
                assertEquals("yes", media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
                assertTrue(media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() in 29700..30700)
            }
        } finally { root.deleteRecursively() }
    }
}
