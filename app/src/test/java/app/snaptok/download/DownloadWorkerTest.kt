package app.snaptok.download

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.workDataOf
import androidx.work.testing.TestListenableWorkerBuilder
import app.snaptok.SnapTokApplication
import app.snaptok.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = SnapTokApplication::class)
class DownloadWorkerTest {
    private lateinit var db: DownloadDatabase
    private lateinit var context: Context
    private val job = DownloadJob("test-job", "https://www.tiktok.com/@author/video/1234567890")
    @Before fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, DownloadDatabase::class.java).build()
        db.downloads().insert(job)
        Unit
    }
    @After fun teardown() { db.close() }
    private fun worker(attempt: Int = 0) = TestListenableWorkerBuilder<DownloadWorker>(context, inputData = workDataOf("jobId" to job.id), runAttemptCount = attempt).build()
    private fun engine(error: Exception? = null) = object : DownloadEngine {
        override suspend fun download(job: DownloadJob, directory: File, progress: (Int) -> Unit): DownloadedVideo {
            if (error != null) throw error
            return DownloadedVideo(File(directory, "video.mp4"), "Example video")
        }
    }
    private val publisher = object : VideoPublisher {
        override suspend fun publish(job: DownloadJob, video: DownloadedVideo) = "content://media/external/video/media/1"
    }
    private fun photoEngine() = object : DownloadEngine {
        override suspend fun download(job: DownloadJob, directory: File, progress: (Int) -> Unit) =
            DownloadedVideo(File(directory, "one.jpg"), "Album", photo = true,
                extraOutputs = listOf(DownloadedVideo(File(directory, "two.jpg"), "Photo 2", photo = true)))
    }
    @Test fun successRequiresPublisherReceipt() = runBlocking {
        worker().execute(db.downloads(), engine(), publisher, false)
        val saved = db.downloads().get(job.id)!!
        assertEquals(JobStatus.SAVED, saved.status)
        assertEquals("content://media/external/video/media/1", saved.mediaUri)
        assertEquals("Example video", saved.title)
    }
    @Test fun transientFailureRetriesThenStops() = runBlocking {
        val result = worker().execute(db.downloads(), engine(IOException("Connection reset")), publisher, false)
        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(JobStatus.RETRYING, db.downloads().get(job.id)!!.status)
        worker(2).execute(db.downloads(), engine(IOException("Connection reset")), publisher, false)
        assertEquals(JobStatus.FAILED, db.downloads().get(job.id)!!.status)
    }
    @Test fun permanentFailureDoesNotRetry() = runBlocking {
        assertEquals(ListenableWorker.Result.success(), worker().execute(db.downloads(), engine(IOException("Video is private")), publisher, false))
        assertEquals(JobStatus.FAILED, db.downloads().get(job.id)!!.status)
    }
    @Test fun cancelledJobNeverCallsExtractor() = runBlocking {
        db.downloads().cancel(job.id)
        worker().execute(db.downloads(), engine(IllegalStateException("Must not run")), publisher, false)
        assertEquals(JobStatus.CANCELLED, db.downloads().get(job.id)!!.status)
    }
    @Test fun failedPublicationNeverMarksSaved() = runBlocking {
        val failPublisher = object : VideoPublisher {
            override suspend fun publish(job: DownloadJob, video: DownloadedVideo): String { throw IOException("No space left on device") }
        }
        worker().execute(db.downloads(), engine(), failPublisher, false)
        assertEquals(JobStatus.FAILED, db.downloads().get(job.id)!!.status)
        assertNull(db.downloads().get(job.id)!!.mediaUri)
    }
    @Test fun laterAlbumFailureRollsBackDurablePublishedReceipt() = runBlocking {
        val uri = "content://media/external/images/media/1"
        val visible = mutableListOf<String>()
        val removed = mutableListOf<String>()
        val failPublisher = object : VideoPublisher {
            override suspend fun publish(job: DownloadJob, video: DownloadedVideo): String {
                if (visible.isNotEmpty()) {
                    assertEquals(listOf(uri), db.downloads().get(this@DownloadWorkerTest.job.id)!!.outputs().map { it.uri })
                    throw IOException("No space left on device")
                }
                visible += uri
                return uri
            }
            override suspend fun remove(uri: String) { visible.remove(uri); removed += uri }
        }
        worker().execute(db.downloads(), photoEngine(), failPublisher, false)
        val failed = db.downloads().get(job.id)!!
        assertEquals(JobStatus.FAILED, failed.status)
        assertNull(failed.mediaUri)
        assertTrue(failed.outputs().isEmpty())
        assertTrue(visible.isEmpty())
        assertEquals(listOf(uri), removed)
        assertEquals(1, db.downloads().removeFinished(job.id))
    }
    @Test fun cancelledPublicationFailureStillRecordsFailedAfterRollback() = runBlocking {
        val uri = "content://media/external/images/media/1"
        val visible = mutableListOf<String>()
        val removed = mutableListOf<String>()
        lateinit var execution: Job
        val failPublisher = object : VideoPublisher {
            override suspend fun publish(job: DownloadJob, video: DownloadedVideo): String {
                if (visible.isNotEmpty()) {
                    execution.cancel()
                    throw IOException("No space left on device")
                }
                visible += uri
                return uri
            }
            override suspend fun remove(uri: String) { visible.remove(uri); removed += uri }
        }
        execution = launch(start = CoroutineStart.LAZY) {
            worker().execute(db.downloads(), photoEngine(), failPublisher, false)
        }
        execution.start()
        execution.join()
        assertTrue(execution.isCancelled)
        assertEquals(listOf(uri), removed)
        assertTrue(visible.isEmpty())
        val failed = db.downloads().get(job.id)!!
        assertTrue(failed.outputs().isEmpty())
        assertNull(failed.mediaUri)
        assertEquals(JobStatus.FAILED, failed.status)
    }
    @Test fun deniedRollbackRetainsReceiptUntilExplicitCleanup() = runBlocking {
        val uri = "content://media/external/images/media/1"
        var published = false
        var allowCleanup = false
        val removed = mutableListOf<String>()
        val failPublisher = object : VideoPublisher {
            override suspend fun publish(job: DownloadJob, video: DownloadedVideo): String {
                if (published) throw IOException("No space left on device")
                published = true
                return uri
            }
            override suspend fun remove(uri: String) {
                if (!allowCleanup) throw SecurityException("Folder access revoked")
                removed += uri
            }
        }
        worker().execute(db.downloads(), photoEngine(), failPublisher, false)
        val failed = db.downloads().get(job.id)!!
        assertEquals(JobStatus.FAILED, failed.status)
        assertEquals(listOf(uri), failed.outputs().map { it.uri })
        assertTrue(failed.error!!.contains("Delete partial files"))
        assertEquals(0, db.downloads().removeFinished(job.id))
        try {
            StorageManager(context, db.downloads(), failPublisher).delete(failed)
            fail("Denied cleanup must retain the receipt")
        } catch (_: SecurityException) { }
        assertEquals(listOf(uri), db.downloads().get(job.id)!!.outputs().map { it.uri })
        allowCleanup = true
        StorageManager(context, db.downloads(), failPublisher).delete(failed)
        assertEquals(listOf(uri), removed)
        assertNull(db.downloads().get(job.id))
    }
    @Test fun retryCleansTrackedPartialOutputBeforePublishingAgain() = runBlocking {
        val partial = MediaOutput("content://media/external/images/media/old", "image/jpeg", "Old partial")
        db.downloads().outputs(job.id, encodeOutputs(listOf(partial)))
        db.downloads().failed(job.id, JobStatus.FAILED, "Cleanup needed")
        assertEquals(1, db.downloads().reset(job.id))
        assertEquals(1, db.downloads().cancel(job.id))
        assertEquals(0, db.downloads().removeFinished(job.id))
        assertEquals(1, db.downloads().reset(job.id))
        val removed = mutableListOf<String>()
        val retryPublisher = object : VideoPublisher {
            override suspend fun publish(job: DownloadJob, video: DownloadedVideo): String {
                assertEquals(listOf(partial.uri), removed)
                return "content://media/external/images/media/${job.id}"
            }
            override suspend fun remove(uri: String) { removed += uri }
        }
        worker().execute(db.downloads(), photoEngine(), retryPublisher, false)
        val saved = db.downloads().get(job.id)!!
        assertEquals(JobStatus.SAVED, saved.status)
        assertEquals(2, saved.outputs().size)
        assertFalse(saved.outputs().any { it.uri == partial.uri })
    }
    @Test fun receiptFailureNeverRollsBackReusedSavedAlias() = runBlocking {
        val fixture = File.createTempFile("saved-alias-", ".mp4", context.cacheDir).apply { writeText("fixture") }
        try {
            val uri = android.net.Uri.fromFile(fixture).toString()
            val source = "Fixture:shared"
            val prior = job.copy(id = "prior", requestKey = "prior", status = JobStatus.SAVED,
                mediaUri = uri, sourceId = source, outputsJson = encodeOutputs(listOf(MediaOutput(uri, "video/mp4", "Prior"))))
            db.downloads().insert(prior)
            val failDao = object : DownloadDao by db.downloads() {
                override suspend fun savedOutputs(id: String, title: String, uri: String, outputs: String, destination: String): Int =
                    throw IOException("No space left on device")
            }
            val aliasEngine = object : DownloadEngine {
                override suspend fun download(job: DownloadJob, directory: File, progress: (Int) -> Unit) =
                    DownloadedVideo(File(directory, "video.mp4"), "Alias", sourceId = source)
            }
            val aliasPublisher = object : VideoPublisher {
                override suspend fun publish(job: DownloadJob, video: DownloadedVideo): String = error("Saved output should be reused")
                override suspend fun remove(uri: String) { fail("Saved alias must never be rolled back") }
            }
            worker().execute(failDao, aliasEngine, aliasPublisher, false)
            assertEquals(JobStatus.FAILED, db.downloads().get(job.id)!!.status)
            assertTrue(db.downloads().get(job.id)!!.outputs().isEmpty())
            assertEquals(JobStatus.SAVED, db.downloads().get(prior.id)!!.status)
            assertTrue(fixture.exists())
        } finally { fixture.delete() }
    }
    @Test fun interruptedManualCleanupRetainsOnlyUnremovedReceipts() = runBlocking {
        val outputs = (1..3).map { MediaOutput("content://media/external/images/media/$it", "image/jpeg", "Photo $it") }
        db.downloads().outputs(job.id, encodeOutputs(outputs))
        db.downloads().failed(job.id, JobStatus.FAILED, "Cleanup needed")
        val removed = mutableListOf<String>()
        var allowCleanup = false
        val cleanupPublisher = object : VideoPublisher {
            override suspend fun publish(job: DownloadJob, video: DownloadedVideo): String = error("Only cleanup expected")
            override suspend fun remove(uri: String) {
                if (uri == outputs[1].uri && !allowCleanup) throw SecurityException("Folder access revoked")
                removed += uri
            }
        }
        val storage = StorageManager(context, db.downloads(), cleanupPublisher)
        try {
            storage.delete(db.downloads().get(job.id)!!)
            fail("Cleanup must report denied access")
        } catch (_: SecurityException) { }
        val partial = db.downloads().get(job.id)!!
        assertEquals(outputs.drop(1), partial.outputs())
        assertEquals(listOf(outputs.first().uri), removed)
        assertEquals(0, db.downloads().removeFinished(job.id))
        allowCleanup = true
        storage.delete(partial)
        assertEquals(outputs.map { it.uri }, removed)
        assertNull(db.downloads().get(job.id))
    }
    @Test fun interruptionCannotResurrectUserCancellation() = runBlocking {
        val interrupted = object : DownloadEngine {
            override suspend fun download(job: DownloadJob, directory: File, progress: (Int) -> Unit): DownloadedVideo {
                db.downloads().cancel(job.id)
                throw CancellationException("User cancelled")
            }
        }
        try { worker().execute(db.downloads(), interrupted, publisher, false); fail("Expected cancellation") } catch (_: CancellationException) { }
        assertEquals(JobStatus.CANCELLED, db.downloads().get(job.id)!!.status)
    }
    @Test fun coroutineCancellationIsNotReportedAsFailure() = runBlocking {
        try {
            worker().execute(db.downloads(), engine(CancellationException("Stopped")), publisher, false)
            fail("Cancellation was swallowed")
        } catch (_: CancellationException) { }
        assertEquals(JobStatus.QUEUED, db.downloads().get(job.id)!!.status)
    }
}
