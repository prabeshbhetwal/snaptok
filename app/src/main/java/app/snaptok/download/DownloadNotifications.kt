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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import app.snaptok.R
import app.snaptok.ui.MainActivity

class DownloadNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    init {
        manager.createNotificationChannel(NotificationChannel("downloads", "Download progress", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel("failures", "Downloads needing attention", NotificationManager.IMPORTANCE_DEFAULT))
    }
    private fun id(jobId: String) = (jobId.hashCode() and 0x3fffffff) + 1
    private fun openApp(): PendingIntent = PendingIntent.getActivity(context, 0,
        Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun progressNotification(percent: Int): Notification = NotificationCompat.Builder(context, "downloads")
        .setSmallIcon(R.drawable.ic_download).setContentTitle("SnapTok")
        .setContentText(if (percent < 0) "Preparing your download" else "Saving your video · $percent%")
        .setProgress(100, percent.coerceAtLeast(0), percent < 0)
        .setContentIntent(openApp()).setOngoing(true).setOnlyAlertOnce(true).setSilent(true).build()

    fun foreground(jobId: String): ForegroundInfo = ForegroundInfo(id(jobId), progressNotification(-1), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    fun progress(jobId: String, percent: Int) {
        if (allowed()) manager.notify(id(jobId), progressNotification(percent))
    }
    fun failed(jobId: String, message: String) {
        if (allowed()) manager.notify(id(jobId) + 0x40000000, NotificationCompat.Builder(context, "failures")
            .setSmallIcon(R.drawable.ic_download).setContentTitle("A video needs attention")
            .setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openApp()).setAutoCancel(true).build())
    }
    fun clearFailure(jobId: String) { manager.cancel(id(jobId) + 0x40000000) }
    private fun allowed() = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
