package app.snaptok.ui

import android.content.Intent
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import app.snaptok.SnapTokApplication
import app.snaptok.data.*
import app.snaptok.download.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class LibraryFeatureTest {
    @Test fun favouriteCollectionPlayerAndDeletionWorkTogether() = runBlocking {
        val instrument = InstrumentationRegistry.getInstrumentation(); val context = instrument.targetContext
        val container = (context.applicationContext as SnapTokApplication).container
        val device = UiDevice.getInstance(instrument)
        container.preferences.update { it.copy(grid=false) }
        val file=File(context.cacheDir,"library-fixture.mp4")
        instrument.context.assets.open("sample-av.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
        val job=DownloadJob(UUID.randomUUID().toString(),"https://www.tiktok.com/@librarytest/video/999999999999999997",title="Library fixture",status=JobStatus.SAVED)
        val uri=GalleryPublisher(context).publish(job,DownloadedVideo(file,job.title))
        container.dao.insert(job.copy(mediaUri=uri))
        fun settle(description: String) {
            var previous: android.graphics.Rect? = null
            var stable = 0
            repeat(40) {
                val current = device.findObject(By.desc(description))?.visibleBounds
                stable = if (current != null && current == previous) stable + 1 else 0
                if (stable >= 3) return
                previous = current
                android.os.SystemClock.sleep(100)
            }
        }
        fun reveal(description: String) {
            repeat(5) {
                val target = device.findObject(By.desc(description))
                if (target != null && target.visibleBounds.height() > 30 && target.visibleBounds.bottom < device.displayHeight - 350) { settle(description); return }
                device.swipe(550,1450,550,900,80)
                device.waitForIdle()
            }
        }
        fun tap(text:String) { (device.wait(Until.findObject(By.text(text)),8000) ?: run {
            device.takeScreenshot(File(context.getExternalFilesDir(null),"library-failure.png"))
            device.dumpWindowHierarchy(File(context.getExternalFilesDir(null),"library-failure.xml"))
            error("Missing $text")
        }).click() }
        var phase = "launch"
        try {
            context.startActivity(Intent(context,MainActivity::class.java).putExtra("showLibrary",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            assertTrue(device.wait(Until.hasObject(By.text("Library fixture")),10000))
            device.findObject(By.clazz("android.widget.EditText")).click()
            device.executeShellCommand("input text Library%sfixture")
            device.pressBack(); device.waitForIdle()
            device.findObject(By.desc("Favourite · Library fixture")).click()
            device.waitForIdle(); assertTrue(container.dao.get(job.id)!!.favourite)
            phase = "collection menu"
            reveal("Video actions · Library fixture"); device.findObject(By.desc("Video actions · Library fixture")).click(); tap("Collection…")
            device.findObject(By.desc("Collection name")).click()
            device.executeShellCommand("input text Learning")
            assertTrue(device.wait(Until.hasObject(By.text("Learning")),5000))
            phase = "save collection"
            tap("Save collection")
            kotlinx.coroutines.withTimeout(5000) { while (container.dao.get(job.id)!!.collection != "Learning") kotlinx.coroutines.delay(50) }
            phase = "player"
            settle("Play Library fixture")
            device.findObject(By.desc("Play Library fixture")).click(); assertTrue(device.wait(Until.hasObject(By.text("Now playing")),10000)); tap("Done")
            phase = "grid"
            repeat(3) { device.swipe(550,600,550,1450,80) }
            settle("Use grid view")
            device.findObject(By.desc("Use grid view")).click()
            assertTrue(device.wait(Until.hasObject(By.desc("Use list view")),5000)); assertTrue(container.preferences.snapshot().grid)
            phase = "delete"
            reveal("Video actions · Library fixture"); device.findObject(By.desc("Video actions · Library fixture")).click(); tap("Delete file…"); tap("Delete file")
            kotlinx.coroutines.withTimeout(5000) { while (container.dao.get(job.id) != null) kotlinx.coroutines.delay(50) }
        } catch (failure: Throwable) {
            println("Failed phase: $phase; fixture row: ${container.dao.get(job.id)}")
            device.takeScreenshot(File(context.getExternalFilesDir(null),"library-failure.png"))
            device.dumpWindowHierarchy(File(context.getExternalFilesDir(null),"library-failure.xml"))
            throw failure
        } finally {
            container.dao.removeFinished(job.id); runCatching { context.contentResolver.delete(Uri.parse(uri),null,null) }; file.delete()
            container.preferences.update { it.copy(grid=false) }
        }
    }
}
