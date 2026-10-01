package app.snaptok.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FeatureOptionsTest {
    @Test fun appearanceAndDownloadPreferencesSurviveRecreation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val prefs = DownloadPreferences(context)
        prefs.update { it.copy(theme="light",grid=true,reduceMotion=true,options=it.options.copy(unmetered=true,charging=true,maxSizeMb=250)) }
        val restored = DownloadPreferences(context).snapshot()
        assertEquals("light",restored.theme); assertTrue(restored.grid); assertTrue(restored.reduceMotion)
        assertTrue(restored.options.unmetered); assertTrue(restored.options.charging); assertEquals(250,restored.options.maxSizeMb)
    }
    @Test fun itemAndAudioDistinguishFilesButNetworkDoesNotDuplicateContent() {
        val base = DownloadOptions()
        assertEquals("url|0|",downloadRequestKey("url",base))
        assertNotEquals(downloadRequestKey("url",base),downloadRequestKey("url",base.copy(itemIndex=2)))
        assertNotEquals(downloadRequestKey("url",base),downloadRequestKey("url",base.copy(audioOnly=true)))
        assertEquals(downloadRequestKey("url",base),downloadRequestKey("url",base.copy(unmetered=true,charging=true)))
    }
}
