package app.snaptok.download

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.snaptok.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ExportPublicationTest {
    private lateinit var db: DownloadDatabase
    private lateinit var video: DownloadedVideo
    private val originalUri = "content://media/external/video/media/1"
    private val exportedUri = "content://media/external/video/media/2"
    private val original = DownloadJob("original", "https://www.tiktok.com/@author/video/1234567890",
        status = JobStatus.SAVED, mediaUri = originalUri,
        outputsJson = encodeOutputs(listOf(MediaOutput(originalUri, "video/mp4", "Original"))))
    private val exported = original.exportCopy("exported", false)
    private val visible = mutableListOf<String>()
    private val removed = mutableListOf<String>()
    private var denyCleanup = false
    private val publisher = object : VideoPublisher {
        override suspend fun publish(job: DownloadJob, video: DownloadedVideo): String {
            visible += exportedUri
            return exportedUri
        }
        override suspend fun remove(uri: String) {
            if (denyCleanup) throw SecurityException("Folder access revoked")
            removed += uri; visible.remove(uri)
        }
    }
    @Before fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DownloadDatabase::class.java).build()
        db.downloads().insert(original)
        video = DownloadedVideo(File(context.cacheDir, "export-fixture.mp4"), "Clip")
        Unit
    }
    @After fun teardown() { db.close() }
    private fun failingInsertDao(ignore: Boolean = false, alwaysFail: Boolean = false) = object : DownloadDao by db.downloads() {
        override suspend fun insert(job: DownloadJob): Long {
            if (job.status == JobStatus.SAVED || alwaysFail) {
                if (ignore) return -1L
                throw IOException("Receipt storage unavailable")
            }
            return db.downloads().insert(job)
        }
    }
    private suspend fun assertOriginalKept() { assertEquals(original, db.downloads().get(original.id)) }

    @Test fun successCommitsOnlyExportedOutput() = runBlocking {
        assertEquals(exportedUri, publisher.publishExport(db.downloads(), exported, video))
        val saved = db.downloads().get(exported.id)!!
        assertEquals(JobStatus.SAVED, saved.status)
        assertEquals(listOf(exportedUri), saved.outputs().map { it.uri })
        assertTrue(removed.isEmpty()); assertOriginalKept()
    }
    @Test fun insertionFailureRollsBackNewOutput() = runBlocking {
        try { publisher.publishExport(failingInsertDao(), exported, video); fail("Receipt failure must be reported") }
        catch (_: IOException) { }
        assertEquals(listOf(exportedUri), removed); assertTrue(visible.isEmpty())
        assertNull(db.downloads().get(exported.id)); assertOriginalKept()
    }
    @Test fun ignoredInsertionRollsBackNewOutput() = runBlocking {
        try { publisher.publishExport(failingInsertDao(ignore = true), exported, video); fail("Ignored receipt must be reported") }
        catch (_: IOException) { }
        assertEquals(listOf(exportedUri), removed); assertTrue(visible.isEmpty())
        assertNull(db.downloads().get(exported.id)); assertOriginalKept()
    }
    @Test fun deniedRollbackRetainsFailedExportForCleanup() = runBlocking {
        denyCleanup = true
        try { publisher.publishExport(failingInsertDao(), exported, video); fail("Denied cleanup must be reported") }
        catch (error: ExportCleanupException) { assertTrue(error.message!!.contains("Delete partial files")) }
        val failed = db.downloads().get(exported.id)!!
        assertEquals(JobStatus.FAILED, failed.status)
        assertEquals(listOf(exportedUri), failed.outputs().map { it.uri })
        assertEquals("[]", failed.outputsJson)
        assertTrue(failed.error!!.contains("Delete partial files"))
        assertTrue(db.downloads().incomplete().isEmpty())
        assertEquals(listOf(exportedUri), visible); assertOriginalKept()
    }
    @Test fun unavailableReceiptStorageReportsFilesFallback() = runBlocking {
        denyCleanup = true
        try { publisher.publishExport(failingInsertDao(alwaysFail = true), exported, video); fail("Unrecorded output must be reported") }
        catch (error: ExportCleanupException) { assertTrue(error.message!!.contains("delete the exported file in Files")) }
        assertNull(db.downloads().get(exported.id))
        assertEquals(listOf(exportedUri), visible); assertOriginalKept()
    }
}
