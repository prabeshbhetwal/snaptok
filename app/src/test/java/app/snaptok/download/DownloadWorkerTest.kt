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
    @Test fun coroutineCancellationIsNotReportedAsFailure() = runBlocking {
        try {
            worker().execute(db.downloads(), engine(CancellationException("Stopped")), publisher, false)
            fail("Cancellation was swallowed")
        } catch (_: CancellationException) { }
        assertNotEquals(JobStatus.FAILED, db.downloads().get(job.id)!!.status)
    }
}
