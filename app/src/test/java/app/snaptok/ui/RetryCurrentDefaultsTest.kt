package app.snaptok.ui

import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.snaptok.SnapTokApplication
import app.snaptok.data.DownloadJob
import app.snaptok.data.DownloadOptions
import app.snaptok.data.JobStatus
import app.snaptok.data.downloadRequestKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = SnapTokApplication::class)
class RetryCurrentDefaultsTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var application: SnapTokApplication
    private lateinit var model: MainViewModel

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        application = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(application,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        model = MainViewModel(application)
    }
    @After fun teardown() {
        model.viewModelScope.cancel()
        application.container.scope.cancel()
        dispatcher.scheduler.runCurrent()
        WorkManagerTestInitHelper.closeWorkDatabase()
        application.container.database.close()
        Dispatchers.resetMain()
    }

    @Test fun retryKeepsSelectedItemAndAudioWhileUsingCurrentDefaults() = runTest {
        val container = application.container
        container.preferences.update { it.copy(options = DownloadOptions(720, "content://test/tree/current", "Current folder",
            unmetered = true, charging = true, maxSizeMb = 250, photoMode = "both", slideSeconds = 7, slideMusic = false)) }
        val failed = DownloadJob("failed-audio", URL, status = JobStatus.FAILED, qualityHeight = 480,
            destinationTreeUri = "content://test/tree/old", destinationLabel = "Old folder", itemIndex = 3, audioOnly = true,
            requestKey = downloadRequestKey(URL, DownloadOptions(480, "content://test/tree/old", "Old folder", itemIndex = 3, audioOnly = true)))
        container.dao.insert(failed)

        model.retryInCurrentFolder(failed).join()

        val queued = container.dao.incomplete().single()
        assertEquals(3, queued.itemIndex)
        assertTrue(queued.audioOnly)
        assertEquals(720, queued.qualityHeight)
        assertEquals("content://test/tree/current", queued.destinationTreeUri)
        assertEquals("Current folder", queued.destinationLabel)
        assertTrue(queued.unmetered); assertTrue(queued.charging)
        assertEquals(250, queued.maxSizeMb)
        assertEquals("both", queued.photoMode)
        assertEquals(7, queued.slideSeconds); assertFalse(queued.slideMusic)
        assertEquals(JobStatus.FAILED, container.dao.get(failed.id)!!.status)
    }

    @Test fun retryUsesCurrentPlatformQualityAndFolder() = runTest {
        val container = application.container
        container.preferences.update { it.copy(options = DownloadOptions(1080, "content://test/tree/platform", "TikTok folder", charging = true)) }
        container.preferences.savePlatform("TikTok", true)
        container.preferences.update { it.copy(options = DownloadOptions(720, "content://test/tree/global", "Global folder",
            unmetered = true, charging = true, maxSizeMb = 600, photoMode = "slideshow", slideSeconds = 4, slideMusic = false)) }
        val failed = DownloadJob("failed-platform", URL, status = JobStatus.FAILED, qualityHeight = 480, itemIndex = 2,
            requestKey = downloadRequestKey(URL, DownloadOptions(qualityHeight = 480, itemIndex = 2)))
        container.dao.insert(failed)

        model.retryInCurrentFolder(failed).join()

        val queued = container.dao.incomplete().single()
        assertEquals(1080, queued.qualityHeight)
        assertEquals("content://test/tree/platform", queued.destinationTreeUri)
        assertEquals("TikTok folder", queued.destinationLabel)
        assertEquals(2, queued.itemIndex); assertFalse(queued.audioOnly)
        assertTrue(queued.unmetered); assertTrue(queued.charging)
        assertEquals(600, queued.maxSizeMb)
        assertEquals("slideshow", queued.photoMode)
        assertEquals(4, queued.slideSeconds); assertFalse(queued.slideMusic)
    }

    @Test fun exportCleanupNeverQueuesOriginalSource() = runTest {
        val container = application.container
        val exported = DownloadJob("exported", URL, status = JobStatus.FAILED,
            mediaUri = "content://media/external/video/media/2", requestKey = "export:exported")
        container.dao.insert(exported)

        model.retryInCurrentFolder(exported).join()

        assertTrue(container.dao.incomplete().isEmpty())
        assertEquals(exported, container.dao.get(exported.id))
    }

    companion object { private const val URL = "https://www.tiktok.com/@author/video/1234567890" }
}
