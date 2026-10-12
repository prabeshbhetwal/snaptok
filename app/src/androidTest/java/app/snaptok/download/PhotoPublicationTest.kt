package app.snaptok.download

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.snaptok.SnapTokApplication
import app.snaptok.core.VideoLink
import app.snaptok.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in source test. Keeps the requested public post in the isolated emulator for UI inspection. */
class PhotoPublicationTest {
    @Test fun sharedPhotoPostPublishesAllOutputsAndOpensAlbum() = runBlocking {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val source = InstrumentationRegistry.getArguments().getString("photoUrl")
        assumeTrue("Pass photoUrl to exercise the live gallery path", source != null)
        val context = instrument.targetContext
        val device = UiDevice.getInstance(instrument)
        val app = context.applicationContext as SnapTokApplication
        val options = DownloadOptions(qualityHeight = 480, photoMode = "both")
        val link = VideoLink.parse(requireNotNull(source))
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        device.executeShellCommand("am start -W -n app.snaptok/.ui.MainActivity")
        app.container.queue.enqueue(link.url, options)
        val id = app.container.dao.findRequest(downloadRequestKey(link.url, options))!!.id
        val deadline = System.currentTimeMillis() + 180_000
        var job = app.container.dao.get(id)!!
        while (job.status !in listOf(JobStatus.SAVED, JobStatus.FAILED, JobStatus.CANCELLED) && System.currentTimeMillis() < deadline) {
            delay(400); job = app.container.dao.get(id)!!
        }
        assertEquals(job.error, JobStatus.SAVED, job.status)
        val outputs = job.outputs()
        assertEquals(10, outputs.count { it.mimeType.startsWith("image/") })
        assertEquals(1, outputs.count { it.mimeType.startsWith("video/") })
        outputs.forEach { asset ->
            context.contentResolver.openFileDescriptor(android.net.Uri.parse(asset.uri), "r")!!.use { assertTrue(it.statSize > 0) }
        }
        assertTrue(job.destinationLabel.contains("Pictures/SnapTok"))
        device.executeShellCommand("am start -W -n app.snaptok/.ui.MainActivity --activity-single-top --es openJobId $id")
        assertTrue("Photo viewer did not open", device.wait(Until.hasObject(By.text("1 of 10")), 15000))
        device.findObject(By.text("Next")).click()
        assertTrue(device.wait(Until.hasObject(By.text("2 of 10")), 4000))
        assertNotNull(device.findObject(By.text("Play slideshow")))
    }
}
