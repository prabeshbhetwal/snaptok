package app.snaptok.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.snaptok.SnapTokApplication
import app.snaptok.data.DownloadJob
import app.snaptok.data.JobStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ManualInputTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private fun launchAndPaste(text: String) {
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        val paste = device.wait(Until.findObject(By.desc("Paste link")), 10_000) ?: error("Paste button missing")
        instrumentation.runOnMainSync {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Test video link", text))
        }
        paste.click()
        assertTrue(device.wait(Until.hasObject(By.text(text)), 5_000))
    }
    @Test fun explicitPasteAndSuccessfulSubmissionClearTheField() = runBlocking {
        val container = (context.applicationContext as SnapTokApplication).container
        val options = container.preferences.snapshot().options
        val link = "https://www.tiktok.com/@snaptoktest/video/999999999999999999"
        val job = DownloadJob(UUID.randomUUID().toString(), link, status = JobStatus.SAVED,
            qualityHeight = options.qualityHeight, destinationTreeUri = options.destinationTreeUri, destinationLabel = options.destinationLabel)
        container.dao.insert(job)
        try {
            launchAndPaste(link)
            device.findObject(By.text("Download video")).click()
            assertTrue("Accepted input should clear", device.wait(Until.gone(By.text(link)), 5_000))
        } finally {
            container.database.openHelper.writableDatabase.execSQL("DELETE FROM downloads WHERE id = ?", arrayOf(job.id))
        }
    }
    @Test fun rejectedInputStaysAvailableForCorrection() {
        launchAndPaste("not a video link")
        device.findObject(By.text("Download video")).click()
        assertTrue(device.wait(Until.hasObject(By.textContains("Use a TikTok or X")), 5_000))
        assertNotNull(device.findObject(By.text("not a video link")))
    }
}
