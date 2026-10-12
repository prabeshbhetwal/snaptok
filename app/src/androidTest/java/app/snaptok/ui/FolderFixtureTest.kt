package app.snaptok.ui

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.snaptok.SnapTokApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.regex.Pattern

/** Selects a real SAF grant on a disposable emulator. */
class FolderFixtureTest {
    @Test fun chooseIsolatedFolderThroughSystemPicker(): Unit = runBlocking {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = instrument.targetContext
        val device = UiDevice.getInstance(instrument)
        assertEquals("1", device.executeShellCommand("getprop ro.kernel.qemu").trim())
        val container = (context.applicationContext as SnapTokApplication).container
        device.executeShellCommand("mkdir -p /sdcard/Documents/SnapTokCI")
        context.startActivity(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        fun tap(text: String) {
            (device.wait(Until.findObject(By.text(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE))), 10_000)
                ?: error("Missing folder-picker control: $text")).click()
        }
        tap("Settings")
        assertTrue(device.wait(Until.hasObject(By.text("Make it yours.")), 10_000))
        val settings = device.findObject(By.pkg(context.packageName).scrollable(true))
            ?: error("Settings list is missing")
        settings.setGestureMargin(80)
        for (attempt in 1..20) {
            if (device.hasObject(By.text("Choose folder"))) break
            settings.scroll(Direction.DOWN, .8f)
        }
        tap("Choose folder")
        assertTrue(device.wait(Until.hasObject(By.pkg("com.android.documentsui")), 10_000))
        if (device.hasObject(By.text("Documents"))) {
            tap("Documents")
            assertTrue(device.wait(Until.hasObject(By.res("com.android.documentsui:id/breadcrumb_text").text("Documents")), 10_000))
            tap("SnapTokCI")
        } else {
            assertTrue("Picker must already be in the isolated folder", device.hasObject(By.text("SnapTokCI")))
        }
        assertTrue("Picker should finish entering the isolated folder before granting it",
            device.wait(Until.hasObject(By.res("com.android.documentsui:id/breadcrumb_text").text("SnapTokCI")), 10_000))
        tap("Use this folder")
        tap("Allow")
        val tree = withTimeout(10_000) {
            while (container.preferences.snapshot().options.destinationTreeUri == null) delay(100)
            Uri.parse(container.preferences.snapshot().options.destinationTreeUri)
        }
        assertEquals("primary:Documents/SnapTokCI", DocumentsContract.getTreeDocumentId(tree))
        assertTrue(context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission })
        device.pressBack()
    }
}
