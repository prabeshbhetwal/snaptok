package app.snaptok.download

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import app.snaptok.data.DownloadPreferences
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DownloadNotificationsTest {
    private lateinit var app: Application
    private lateinit var settings: DownloadPreferences
    private lateinit var notifications: DownloadNotifications
    private lateinit var manager: NotificationManager
    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("download-settings", 0).edit().clear().commit()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        settings = DownloadPreferences(app)
        notifications = DownloadNotifications(app, settings)
        manager = app.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
    }
    @Test fun percentageProgressAndSavingHaveDistinctStates() {
        notifications.progress("job", 42)
        val progress = manager.activeNotifications.single().notification
        assertEquals(42, progress.extras.getInt(Notification.EXTRA_PROGRESS))
        assertEquals(100, progress.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        notifications.saving("job")
        assertTrue(manager.activeNotifications.single().notification.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
    }
    @Test fun completionAndFailureArePostedAndCanBeDisabled() = runBlocking {
        notifications.completed("saved", "Video", "content://media/1", "Gallery")
        notifications.failed("failed", "Unavailable")
        assertEquals(2, manager.activeNotifications.size)
        settings.update { it.copy(notificationsEnabled = false) }
        notifications.refreshPreferences()
        assertEquals(0, manager.activeNotifications.size)
        notifications.completed("new", "Video", "content://media/2", "Gallery")
        notifications.failed("new-failed", "Unavailable")
        assertEquals(0, manager.activeNotifications.size)
    }
    @Test fun optionalProgressOffStillBuildsRequiredServiceIndicator() = runBlocking {
        settings.update { it.copy(showProgress = false, showCompletion = false) }
        val notification = notifications.foreground("job").notification
        assertEquals(0, notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        assertEquals("Download running in the background", notification.extras.getString(Notification.EXTRA_TEXT))
        notifications.completed("saved", "Video", "content://media/1", "Gallery")
        assertEquals(0, manager.activeNotifications.size)
    }
    @Test fun preferencesPersistAcrossInstances() = runBlocking {
        settings.update { it.copy(showErrors = false, options = it.options.copy(qualityHeight = 480, destinationTreeUri = "content://folder/tree/test", destinationLabel = "My folder")) }
        val restored = DownloadPreferences(app).snapshot()
        assertEquals(480, restored.options.qualityHeight)
        assertEquals("My folder", restored.options.destinationLabel)
        assertFalse(restored.showErrors)
    }
}
