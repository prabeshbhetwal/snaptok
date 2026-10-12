package app.snaptok.ui

import android.content.Intent
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.work.WorkManager
import androidx.work.WorkInfo
import app.snaptok.SnapTokApplication
import app.snaptok.data.DownloadJob
import app.snaptok.data.DownloadOptions
import app.snaptok.data.JobStatus
import app.snaptok.data.downloadRequestKey
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ShareActivityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val container get() = (context.applicationContext as SnapTokApplication).container
    private val device get() = UiDevice.getInstance(instrumentation)

    private fun shareAndReturn(text: String, rejected: Boolean = false) {
        context.startActivity(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        assertTrue(device.wait(Until.hasObject(By.pkg(context.packageName).text("Save")), 10_000))
        val created = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val shared = AtomicReference<ShareActivity>()
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is ShareActivity) {
                if (stage == Stage.CREATED) { shared.set(activity); created.countDown() }
                if (stage == Stage.DESTROYED) finished.countDown()
            }
        }
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        instrumentation.runOnMainSync { monitor.addLifecycleCallback(callback) }
        try {
            val intent = Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(context.packageName)
                .putExtra(Intent.EXTRA_TEXT, text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            assertEquals(ShareActivity::class.java.name, intent.resolveActivity(context.packageManager)?.className)
            context.startActivity(intent)
            assertTrue("ShareActivity did not receive the share", created.await(10, TimeUnit.SECONDS))
            assertEquals(Intent.ACTION_SEND, shared.get().intent.action)
            assertEquals("text/plain", shared.get().intent.type)
            assertEquals(text, shared.get().intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString())
            if (rejected) {
                assertTrue(device.wait(Until.hasObject(By.textContains("Use a supported video-post link")), 10_000))
                (device.wait(Until.findObject(By.text("Back")), 5000) ?: error("Share rejection Back button missing")).click()
            }
            assertTrue("ShareActivity did not finish", finished.await(10, TimeUnit.SECONDS))
            assertTrue("Sharing did not return to the caller", device.wait(Until.hasObject(By.pkg(context.packageName).text("Save")), 10_000))
        } finally {
            instrumentation.runOnMainSync {
                monitor.removeLifecycleCallback(callback)
                shared.get()?.takeUnless { it.isDestroyed }?.finish()
            }
        }
    }

    @Test fun sharedSavedLinkReturnsWithoutSchedulingOrChangingOriginal() = runBlocking {
        val id = UUID.randomUUID().toString()
        val post = kotlin.random.Random.nextLong(100_000_000_000_000_000L, 999_999_999_999_999_999L)
        val url = "https://www.tiktok.com/@snaptoksharetest/video/$post"
        val options = container.preferences.optionsFor(url)
        val fixture = File(context.cacheDir, "share-$id.mp4")
        val job = DownloadJob(id, url, title = "Share fixture", status = JobStatus.SAVED,
            mediaUri = Uri.fromFile(fixture).toString(), qualityHeight = options.qualityHeight,
            destinationTreeUri = options.destinationTreeUri, destinationLabel = options.destinationLabel,
            unmetered = options.unmetered, charging = options.charging, maxSizeMb = options.maxSizeMb,
            itemIndex = options.itemIndex, audioOnly = options.audioOnly, photoMode = options.photoMode,
            slideSeconds = options.slideSeconds, slideMusic = options.slideMusic,
            requestKey = downloadRequestKey(url, options))
        val work = WorkManager.getInstance(context)
        try {
            instrumentation.context.assets.open("sample.mp4").use { input -> fixture.outputStream().use { input.copyTo(it) } }
            val original = fixture.readBytes()
            assertTrue(container.dao.insert(job) != -1L)
            val before = container.dao.all().map { it.id }.toSet()
            assertTrue(work.getWorkInfosForUniqueWork("download-$id").get().isEmpty())
            shareAndReturn(url)
            assertEquals(before, container.dao.all().map { it.id }.toSet())
            assertEquals(job, container.dao.get(id))
            assertTrue(work.getWorkInfosForUniqueWork("download-$id").get().isEmpty())
            assertArrayEquals(original, fixture.readBytes())
        } finally {
            container.queue.cancel(id)
            container.database.openHelper.writableDatabase.execSQL("DELETE FROM downloads WHERE id = ?", arrayOf(id))
            fixture.delete()
        }
    }

    @Test fun invalidSharedTextShowsRejectionAndReturnsWithoutAddingJob() = runBlocking {
        val before = container.dao.all().map { it.id }.toSet()
        shareAndReturn("not a video link", rejected = true)
        assertEquals(before, container.dao.all().map { it.id }.toSet())
    }

    @Test fun freshSharedLinkQueuesDurablyWithChargingPreference() = runBlocking {
        val savedSettings = container.preferences.snapshot()
        val post = kotlin.random.Random.nextLong(100_000_000_000_000_000L, 999_999_999_999_999_999L)
        val url = "https://www.tiktok.com/@snaptoksharetest/video/$post"
        var ownRequest: String? = null
        try {
            device.executeShellCommand("dumpsys battery unplug")
            device.executeShellCommand("dumpsys battery set status 3")
            container.preferences.update { it.copy(options = it.options.copy(charging = true)) }
            val preferred = container.preferences.optionsFor(url)
            val options = if (preferred.audioOnly && preferred.destinationTreeUri == null)
                preferred.copy(destinationLabel = "Music · Music/SnapTok") else preferred
            val key = downloadRequestKey(url, options)
            assertNull(container.dao.findRequest(key))
            ownRequest = key
            shareAndReturn(url)
            delay(2000)
            val queued = container.dao.findRequest(key) ?: error("Shared link was not persisted")
            assertEquals(url, queued.url)
            assertEquals(JobStatus.QUEUED, queued.status)
            assertTrue(queued.charging)
            assertEquals(options, DownloadOptions(qualityHeight = queued.qualityHeight,
                destinationTreeUri = queued.destinationTreeUri, destinationLabel = queued.destinationLabel,
                unmetered = queued.unmetered, charging = queued.charging, maxSizeMb = queued.maxSizeMb,
                itemIndex = queued.itemIndex, audioOnly = queued.audioOnly, photoMode = queued.photoMode,
                slideSeconds = queued.slideSeconds, slideMusic = queued.slideMusic))
            val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork("download-${queued.id}").get().single()
            assertEquals(WorkInfo.State.ENQUEUED, work.state)
        } finally {
            try {
                ownRequest?.let { container.dao.findRequest(it) }?.let { own ->
                    container.queue.cancel(own.id)
                    container.dao.removeFinished(own.id)
                }
            } finally {
                try { container.preferences.update { savedSettings } }
                finally { device.executeShellCommand("dumpsys battery reset") }
            }
        }
    }
}
