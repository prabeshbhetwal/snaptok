package app.snaptok.download
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.work.WorkManager
import androidx.work.WorkInfo
import app.snaptok.SnapTokApplication
import app.snaptok.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
class ConstraintTest {
    @Test fun chargingOnlyRequestRemainsQueuedWhenUnplugged() = runBlocking {
        val instrument=InstrumentationRegistry.getInstrumentation();val context=instrument.targetContext
        val device=UiDevice.getInstance(instrument);val container=(context.applicationContext as SnapTokApplication).container
        val url="https://www.tiktok.com/@constrainttest/video/999999999999999996"
        var id:String?=null
        try {
            device.executeShellCommand("dumpsys battery unplug");device.executeShellCommand("dumpsys battery set status 3")
            container.queue.enqueue(url,DownloadOptions(charging=true))
            id=container.dao.findUrl(url)!!.id
            kotlinx.coroutines.delay(2000)
            assertEquals(JobStatus.QUEUED,container.dao.get(id)!!.status)
            val work=WorkManager.getInstance(context).getWorkInfosForUniqueWork("download-$id").get().single()
            assertEquals(WorkInfo.State.ENQUEUED,work.state)
        } finally {
            id?.let { container.queue.cancel(it);container.dao.removeFinished(it) }
            device.executeShellCommand("dumpsys battery reset")
        }
    }
}
