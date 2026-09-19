package app.snaptok.download

import androidx.test.platform.app.InstrumentationRegistry
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeEngineTest {
    @Test fun bundledPythonAndExtractorCanExecute() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // A clean install must use the pinned app payload, not the wrapper's older copy.
        val bundledBytes = context.resources.openRawResource(app.snaptok.R.raw.ytdlp).use { it.readBytes() }
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bundledBytes).joinToString("") { "%02x".format(it) }
        assertEquals("1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6", digest)
        YoutubeDL.init(context)
        val request = YoutubeDLRequest(emptyList<String>()).addOption("--version")
        val response = YoutubeDL.execute(request, "native-smoke-test", null)
        if (YoutubeDL.version(context) == null) assertEquals("2026.08.19", response.out.trim())
        assertTrue(response.out.trim().matches(Regex("[0-9]{4}\\.[0-9]{2}\\.[0-9]{2}.*")))
    }
}
