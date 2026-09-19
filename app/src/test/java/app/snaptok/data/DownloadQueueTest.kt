package app.snaptok.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.app.Application

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DownloadQueueTest {
    private lateinit var db: DownloadDatabase
    private lateinit var dao: DownloadDao
    private lateinit var queue: DownloadQueue
    private lateinit var scheduler: FakeScheduler
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DownloadDatabase::class.java).build()
        dao = db.downloads()
        scheduler = FakeScheduler()
        queue = DownloadQueue(dao, scheduler)
    }
    @After fun teardown() { db.close() }
    @Test fun duplicateSharesHaveOneDurableJob() = runBlocking {
        queue.enqueue(URL)
        assertEquals("Already queued", queue.enqueue("$URL?tracking=123"))
        assertEquals(1, dao.incomplete().size)
        assertEquals(1, scheduler.jobs.distinct().size)
    }
    @Test fun schedulingFailureLeavesRecoverableRequest() = runBlocking {
        scheduler.fail = true
        try { queue.enqueue(URL); fail("Expected scheduling failure") } catch (_: IllegalStateException) { }
        val id = dao.incomplete().single().id
        scheduler.fail = false
        queue.reconcile()
        assertEquals(id, scheduler.jobs.single())
    }
    @Test fun savedJobIsNotScheduledAgain() = runBlocking {
        queue.enqueue(URL)
        val id = dao.incomplete().single().id
        dao.saved(id, "Video", "content://media/1")
        assertEquals("Already saved", queue.enqueue(URL))
        assertEquals(1, scheduler.jobs.size)
    }
    @Test fun cancelledJobCanBeSharedAgain() = runBlocking {
        queue.enqueue(URL)
        val id = dao.incomplete().single().id
        queue.cancel(id)
        assertEquals(JobStatus.CANCELLED, dao.get(id)!!.status)
        assertEquals(listOf(id), scheduler.cancelled)
        assertEquals("Queued again", queue.enqueue(URL))
        assertEquals(JobStatus.QUEUED, dao.get(id)!!.status)
        assertTrue(scheduler.replaced)
    }
    @Test fun cancellationCannotOverwritePublication() = runBlocking {
        queue.enqueue(URL)
        val id = dao.incomplete().single().id
        dao.start(id, 1)
        dao.saving(id)
        queue.cancel(id)
        assertEquals(JobStatus.SAVING, dao.get(id)!!.status)
        assertTrue(scheduler.cancelled.isEmpty())
    }
    @Test fun databaseSurvivesReopening() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("durability-test")
        val first = Room.databaseBuilder(context, DownloadDatabase::class.java, "durability-test").build()
        first.downloads().insert(DownloadJob("persisted", URL))
        first.close()
        val second = Room.databaseBuilder(context, DownloadDatabase::class.java, "durability-test").build()
        assertEquals(URL, second.downloads().get("persisted")!!.url)
        second.close()
        context.deleteDatabase("durability-test")
        Unit
    }
    private class FakeScheduler : DownloadScheduler {
        val jobs = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        var fail = false
        var replaced = false
        override suspend fun schedule(id: String, replace: Boolean) {
            check(!fail)
            jobs.add(id)
            replaced = replace
        }
        override suspend fun cancel(id: String) { cancelled.add(id) }
    }
    companion object { const val URL = "https://www.tiktok.com/@author/video/1234567890" }
}
