package app.snaptok

import android.app.Application
import android.content.Context
import androidx.room.Room
import app.snaptok.data.DownloadDatabase
import app.snaptok.data.DownloadQueue
import app.snaptok.data.AndroidDownloadScheduler
import app.snaptok.data.DownloadPreferences
import app.snaptok.data.MIGRATION_1_2
import app.snaptok.download.DownloadNotifications
import app.snaptok.download.GalleryPublisher
import app.snaptok.download.YtDlpEngine
import kotlinx.coroutines.*

class SnapTokApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
    override fun onCreate() {
        super.onCreate()
        container.scope.launch {
            // Database-first enqueue is recoverable if the process stopped before scheduling.
            runCatching { container.queue.reconcile() }
        }
    }
}
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database = Room.databaseBuilder(context, DownloadDatabase::class.java, "downloads.db").addMigrations(MIGRATION_1_2).build()
    val dao = database.downloads()
    val preferences = DownloadPreferences(context)
    val queue = DownloadQueue(dao, AndroidDownloadScheduler(context)) { preferences.snapshot().options }
    val engine = YtDlpEngine(context)
    val publisher = GalleryPublisher(context)
    val notifications = DownloadNotifications(context, preferences)
}
