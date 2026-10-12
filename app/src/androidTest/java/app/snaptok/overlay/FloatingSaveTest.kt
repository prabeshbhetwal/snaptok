package app.snaptok.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import app.snaptok.SnapTokApplication
import app.snaptok.data.DownloadJob
import app.snaptok.data.JobStatus
import app.snaptok.ui.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.Stage

class FloatingSaveTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private val selector = By.descStartsWith("Save copied video link")
    @Before fun prepare() {
        context.stopService(Intent(context, FloatingSaveService::class.java))
        device.wait(Until.gone(selector), 5000)
        device.executeShellCommand("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        assertTrue(device.wait(Until.hasObject(By.pkg(context.packageName).text("Save")), 10_000))
        context.getSharedPreferences(FloatingSaveService.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        instrumentation.runOnMainSync { ContextCompat.startForegroundService(context, Intent(context, FloatingSaveService::class.java)) }
        assertNotNull(device.wait(Until.findObject(selector), 10_000))
    }
    @After fun stop() { context.stopService(Intent(context, FloatingSaveService::class.java)); device.wait(Until.gone(selector), 5000) }
    private fun assertHome() {
        val deadline = android.os.SystemClock.elapsedRealtime() + 8000
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            if (device.executeShellCommand("dumpsys activity activities").lineSequence()
                .any { it.contains("topResumedActivity") && it.contains("com.android.launcher3") }) return
            android.os.SystemClock.sleep(100)
        }
        fail("Launcher should remain the resumed activity")
    }
    private fun tapAndWaitForReturn() {
        val finished = CountDownLatch(1)
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is SaveClipboardActivity && stage == Stage.DESTROYED) finished.countDown()
        }
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        instrumentation.runOnMainSync { monitor.addLifecycleCallback(callback) }
        try {
            device.findObject(selector).click()
            assertTrue("Clipboard screen should finish after handling the tap", finished.await(10, TimeUnit.SECONDS))
        } finally { instrumentation.runOnMainSync { monitor.removeLifecycleCallback(callback) } }
    }
    private fun copy(value: String) {
        instrumentation.runOnMainSync { context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Test", value)) }
    }
    @Test fun savesCopiedLinkFromHomeAndReturnsWithoutOpeningLibrary() = runBlocking {
        val container = (context.applicationContext as SnapTokApplication).container
        val options = container.preferences.snapshot().options
        val job = DownloadJob(UUID.randomUUID().toString(), "https://www.tiktok.com/@floatingtest/video/999999999999999998", status = JobStatus.CANCELLED,
            qualityHeight = options.qualityHeight, destinationTreeUri = options.destinationTreeUri, destinationLabel = options.destinationLabel)
        container.dao.insert(job)
        try {
            copy(job.url)
            device.pressHome()
            assertHome()
            tapAndWaitForReturn()
            assertNotEquals("Copied URL should requeue the cancelled job", JobStatus.CANCELLED, container.dao.get(job.id)!!.status)
            assertHome()
            assertFalse(device.hasObject(By.desc("Paste link")))
            assertNotNull(device.findObject(selector))
        } finally { container.queue.cancel(job.id); container.dao.removeFinished(job.id) }
    }
    @Test fun invalidClipboardReturnsToPreviousScreenWithoutAJob() = runBlocking {
        val dao = (context.applicationContext as SnapTokApplication).container.dao
        val before = dao.incomplete().size
        copy("not a video link")
        device.pressHome()
        assertHome()
        tapAndWaitForReturn()
        assertHome()
        assertEquals(before, dao.incomplete().size)
    }
    @Test fun sizeDragAndLongPressHideWork() {
        val prefs = context.getSharedPreferences(FloatingSaveService.PREFS, Context.MODE_PRIVATE)
        val before = device.findObject(selector).visibleBounds.width()
        instrumentation.runOnMainSync { prefs.edit().putInt("size", 80).putFloat("opacity", .25f).commit() }
        device.waitForIdle()
        val enlarged = device.findObject(selector)
        assertTrue(enlarged.visibleBounds.width() > before)
        val bounds = enlarged.visibleBounds
        device.swipe(bounds.centerX(), bounds.centerY(), 60, 450, 30)
        device.waitForIdle()
        assertTrue(device.findObject(selector).visibleBounds.left < device.displayWidth / 2)
        assertFalse(prefs.getBoolean("right", true))
        device.findObject(selector).longClick()
        assertTrue(device.wait(Until.gone(selector), 5000))
    }
}
