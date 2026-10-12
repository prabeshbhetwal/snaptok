package app.snaptok.ui

import androidx.test.platform.app.InstrumentationRegistry
import android.net.Uri
import app.snaptok.SnapTokApplication
import app.snaptok.data.DownloadJob
import app.snaptok.data.JobStatus
import app.snaptok.download.DownloadedVideo
import app.snaptok.download.GalleryPublisher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class SavedMediaTest {
    @Test fun readsPublishedMetadataAndRemovingHistoryPreservesFile() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val dao = (context.applicationContext as SnapTokApplication).container.dao
        val file = File(context.cacheDir, "details-test.mp4")
        instrumentation.context.assets.open("sample.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
        val job = DownloadJob(UUID.randomUUID().toString(), "https://www.instagram.com/p/SnapTokTest/", status = JobStatus.SAVED)
        var uri: Uri? = null
        try {
            uri = Uri.parse(GalleryPublisher(context).publish(job, DownloadedVideo(file, "Details test")))
            dao.insert(job.copy(mediaUri = uri.toString()))
            val details = readSavedMedia(context, uri)
            assertTrue(details.width > 0)
            assertTrue(details.height > 0)
            assertTrue(details.durationMs > 0)
            assertEquals(file.length(), details.bytes)
            assertNotNull(details.thumbnail)
            assertEquals(1, dao.removeFinished(job.id))
            assertNull(dao.get(job.id))
            context.contentResolver.openInputStream(uri)!!.use { assertEquals(file.length(), it.readBytes().size.toLong()) }
        } finally {
            dao.removeFinished(job.id)
            uri?.let { context.contentResolver.delete(it, null, null) }
            file.delete()
        }
    }
}
