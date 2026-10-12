package app.snaptok.download

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.work.ForegroundInfo
import app.snaptok.R
import app.snaptok.data.DownloadPreferences
import app.snaptok.ui.MainActivity

class DownloadNotifications(private val context: Context, private val preferences: DownloadPreferences) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    init {
        manager.createNotificationChannel(NotificationChannel("downloads", "Download progress", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel("completed", "Completed downloads", NotificationManager.IMPORTANCE_DEFAULT))
        manager.createNotificationChannel(NotificationChannel("failures", "Downloads needing attention", NotificationManager.IMPORTANCE_DEFAULT))
    }
    private fun id(jobId: String) = (jobId.hashCode() and 0x3fffffff) + 1
    private fun openApp(): PendingIntent = PendingIntent.getActivity(context, 0,
        Intent(context, MainActivity::class.java).putExtra("showLibrary", true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun progressNotification(jobId: String, percent: Int, saving: Boolean = false): Notification {
        val settings = preferences.snapshot()
        val detailed = settings.notificationsEnabled && settings.showProgress
        val builder = NotificationCompat.Builder(context, "downloads")
            .setSmallIcon(R.drawable.ic_download).setContentTitle("SnapTok")
            .setContentText(when {
                !detailed -> "Download running in the background"
                saving -> "Finishing and verifying your saved file…"
                percent < 0 -> "Preparing your download…"
                percent >= 100 -> "Finishing media…"
                else -> "Downloading · $percent%"
            })
            .setContentIntent(openApp()).setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .addExtras(android.os.Bundle().apply { putString("jobId", jobId); putBoolean("saving", saving) })
        if (detailed) builder.setProgress(100, percent.coerceIn(0, 100), percent < 0 || saving)
        return builder.build()
    }
    fun foreground(jobId: String): ForegroundInfo = ForegroundInfo(id(jobId), progressNotification(jobId, -1), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    fun progress(jobId: String, percent: Int) {
        if (allowed()) manager.notify(id(jobId), progressNotification(jobId, percent))
    }
    fun saving(jobId: String) {
        if (allowed()) manager.notify(id(jobId), progressNotification(jobId, 100, saving = true))
    }
    fun completed(jobId: String, title: String, uri: String, destination: String) {
        val settings = preferences.snapshot()
        if (!allowed() || manager.getNotificationChannel("completed").importance == NotificationManager.IMPORTANCE_NONE || !settings.notificationsEnabled || !settings.showCompletion) return
        val play = PendingIntent.getActivity(context, id(jobId),
            Intent(context, MainActivity::class.java).putExtra("openJobId", jobId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify("completed-$jobId", 0, NotificationCompat.Builder(context, "completed")
            .setSmallIcon(R.drawable.ic_download).setContentTitle("Download complete")
            .setContentText(title).setStyle(NotificationCompat.BigTextStyle().bigText("$title\nSaved to $destination"))
            .setContentIntent(play).addAction(0, "Open", play).setAutoCancel(true).build())
        briefMessage("Download complete")
    }
    fun failed(jobId: String, message: String) {
        val settings = preferences.snapshot()
        if (!allowed() || manager.getNotificationChannel("failures").importance == NotificationManager.IMPORTANCE_NONE || !settings.notificationsEnabled || !settings.showErrors) return
        manager.notify("failed-$jobId", 0, NotificationCompat.Builder(context, "failures")
            .setSmallIcon(R.drawable.ic_download).setContentTitle("Download failed")
            .setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openApp()).setAutoCancel(true).build())
        briefMessage("Download failed. Open SnapTok for details.")
    }
    fun clearFailure(jobId: String) {
        manager.cancel("failed-$jobId", 0)
        manager.cancel(id(jobId) + 0x40000000) // Also clear v0.1 failure notices.
    }
    fun refreshPreferences() {
        val settings = preferences.snapshot()
        manager.activeNotifications.forEach { active ->
            when {
                active.tag?.startsWith("completed-") == true && (!settings.notificationsEnabled || !settings.showCompletion) -> manager.cancel(active.tag, active.id)
                active.tag?.startsWith("failed-") == true && (!settings.notificationsEnabled || !settings.showErrors) -> manager.cancel(active.tag, active.id)
                active.tag == null && active.notification.extras.getString("jobId") != null && allowed() -> {
                    val jobId = active.notification.extras.getString("jobId")!!
                    manager.notify(active.id, progressNotification(jobId, active.notification.extras.getInt(Notification.EXTRA_PROGRESS, -1), active.notification.extras.getBoolean("saving")))
                }
            }
        }
    }
    private fun briefMessage(message: String) { Handler(Looper.getMainLooper()).post { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() } }
    private fun allowed() = manager.areNotificationsEnabled() && (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
}
