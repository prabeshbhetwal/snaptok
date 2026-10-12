package app.snaptok.ui

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.snaptok.SnapTokApplication
import app.snaptok.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class RecoveryFlowTest {
    @Test fun staleCompletionOfMissingSaveReturnsToLibraryWhileChargingIsBlocked(): Unit = runBlocking {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = instrument.targetContext
        val device = UiDevice.getInstance(instrument)
        assertEquals("1", device.executeShellCommand("getprop ro.kernel.qemu").trim())
        val container = (context.applicationContext as SnapTokApplication).container
        val manager = context.getSystemService(NotificationManager::class.java)
        val previous = container.preferences.snapshot()
        val id = UUID.randomUUID().toString()
        val url = "https://www.tiktok.com/@missingtest/video/999999999999999995"
        val file = File(context.cacheDir, "missing-$id.mp4")
        try {
            device.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
            container.preferences.update { it.copy(notificationsEnabled = true, showCompletion = true) }
            val options = container.preferences.optionsFor(url)
            instrument.context.assets.open("sample.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
            val uri = Uri.fromFile(file).toString()
            val saved = DownloadJob(id, url, title = "Missing saved fixture", status = JobStatus.SAVED,
                mediaUri = uri, qualityHeight = options.qualityHeight, destinationTreeUri = options.destinationTreeUri,
                destinationLabel = options.destinationLabel, photoMode = options.photoMode,
                slideSeconds = options.slideSeconds, slideMusic = options.slideMusic,
                requestKey = downloadRequestKey(url, options))
            container.dao.insert(saved)
            container.notifications.completed(id, saved.title, uri, saved.destinationLabel)
            val completion = withTimeout(10_000) {
                while (manager.activeNotifications.none { it.tag == "completed-$id" }) delay(50)
                manager.activeNotifications.single { it.tag == "completed-$id" }.notification.contentIntent
            }
            assertNotNull(completion)
            assertTrue(file.delete())
            device.executeShellCommand("dumpsys battery unplug")
            device.executeShellCommand("dumpsys battery set status 3")
            container.preferences.update { it.copy(options = it.options.copy(charging = true)) }
            assertEquals("Saved file missing — downloading again", container.queue.enqueue(url))
            val queued = container.dao.get(id)!!
            assertEquals(JobStatus.QUEUED, queued.status)
            assertNull(queued.mediaUri)
            assertTrue(queued.charging)
            context.startActivity(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            assertTrue(device.wait(Until.hasObject(By.pkg(context.packageName).text("Save")), 10_000))
            completion!!.send()
            assertTrue(device.wait(Until.hasObject(By.text("Saved file unavailable. Check this download in Library.")), 10_000))
            assertTrue(device.wait(Until.hasObject(By.text("Keep the good ones.")), 10_000))
            assertFalse(device.hasObject(By.text("Now playing")))
            assertEquals(JobStatus.QUEUED, container.dao.get(id)!!.status)
            assertEquals(WorkInfo.State.ENQUEUED,
                WorkManager.getInstance(context).getWorkInfosForUniqueWork("download-$id").get().single().state)
        } finally {
            try {
                container.queue.cancel(id)
                container.dao.removeFinished(id)
                manager.cancel("completed-$id", 0)
                file.delete()
            } finally {
                container.preferences.update { previous }
                device.executeShellCommand("dumpsys battery reset")
                // AGP uninstalls this disposable fixture app; revoking here kills instrumentation.
            }
        }
    }
}
