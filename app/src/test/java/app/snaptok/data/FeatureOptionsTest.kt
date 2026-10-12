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
    @Test fun legacyExportsResolveOnlyTheirOwnFile() {
        val originalUri = "content://media/external/video/media/1"
        val inherited = encodeOutputs(listOf(MediaOutput(originalUri, "video/mp4", "Original")))
        for (audio in listOf(false, true)) {
            val exportedUri = "content://media/external/${if (audio) "audio" else "video"}/media/2"
            val legacy = DownloadJob("legacy", "url", status = JobStatus.SAVED, mediaUri = exportedUri,
                audioOnly = audio, requestKey = "export:legacy", outputsJson = inherited)
            assertEquals(listOf(exportedUri), legacy.outputs().map { it.uri })
            assertEquals(if (audio) "audio/mp4" else "video/mp4", legacy.outputs().single().mimeType)
            assertTrue(legacy.copy(mediaUri = null).outputs().isEmpty())
            assertEquals(listOf(originalUri), legacy.copy(requestKey = "url|0|").outputs().map { it.uri })
        }
    }
    @Test fun exportedMediaNeverRetainsOriginalSharingOrDeletionReceipts() {
        val originalUri = "content://media/external/video/media/1"
        val original = DownloadJob("original", "url", title = "Original", status = JobStatus.SAVED,
            mediaUri = originalUri, sourceId = "TikTok:123456",
            outputsJson = encodeOutputs(listOf(MediaOutput(originalUri, "video/mp4", "Original"))))
        for (audio in listOf(false, true)) {
            val exportedUri = "content://media/external/${if (audio) "audio" else "video"}/media/2"
            val exported = original.exportCopy("exported", audio).copy(mediaUri = exportedUri)
            assertEquals(listOf(exportedUri), exported.outputs().map { it.uri })
            assertEquals(if (audio) "audio/mp4" else "video/mp4", exported.outputs().single().mimeType)
            assertEquals("", exported.sourceId)
            assertEquals("export:exported", exported.requestKey)
            assertEquals(listOf(originalUri), original.outputs().map { it.uri })
        }
    }
}
