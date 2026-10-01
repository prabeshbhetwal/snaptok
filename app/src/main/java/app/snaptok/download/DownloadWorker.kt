package app.snaptok.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.snaptok.SnapTokApplication
import app.snaptok.core.FailurePolicy
import app.snaptok.data.DownloadDao
import app.snaptok.data.JobStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException

class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val container get() = (applicationContext as SnapTokApplication).container
    private val jobId get() = inputData.getString("jobId").orEmpty()
    override suspend fun getForegroundInfo() = container.notifications.foreground(jobId)

    override suspend fun doWork(): Result = execute(container.dao, container.engine, container.publisher, foreground = true)

    // The same lifecycle is tested with fake extraction/publication; no TikTok network in unit tests.
    internal suspend fun execute(dao: DownloadDao, engine: DownloadEngine, publisher: VideoPublisher, foreground: Boolean): Result {
        val job = dao.get(jobId) ?: return Result.failure()
        if (job.status in listOf(JobStatus.SAVED, JobStatus.CANCELLED, JobStatus.FAILED)) return Result.success()
        if (foreground) {
            val battery = applicationContext.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val plugged = battery?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            val status = battery?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = plugged != 0 && status in listOf(android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_STATUS_FULL)
            val metered = applicationContext.getSystemService(android.net.ConnectivityManager::class.java).isActiveNetworkMetered
            if ((job.charging && !charging) || (job.unmetered && metered)) { dao.interrupted(jobId); return Result.retry() }
        }
        val directory = File(applicationContext.cacheDir, "staging/$jobId")
        return try {
            if (foreground) setForeground(getForegroundInfo())
            container.notifications.clearFailure(jobId)
            if (dao.start(jobId, runAttemptCount + 1) == 0) return Result.success()
            var lastProgress = -1
            var lastUpdate = 0L
            val video = try {
                engine.download(job, directory) { progress ->
                        val now = System.currentTimeMillis()
                        if (progress != lastProgress && now - lastUpdate >= 1000) {
                            lastProgress = progress
                            lastUpdate = now
                            runBlocking { dao.progress(jobId, progress) }
                            if (foreground) container.notifications.progress(jobId, progress)
                        }
                    }
            } catch (error: TimeoutCancellationException) {
                throw IOException("Download timed out", error)
            }
            currentCoroutineContext().ensureActive()
            if (dao.saving(jobId) == 0) return Result.success()
            if (foreground) container.notifications.saving(jobId)
            // Finish atomic gallery publication and its database receipt together,
            // even if WorkManager requests cancellation during the copy.
            withContext(NonCancellable) { container.publication.withLock {
                dao.metadata(jobId, video.creator, video.caption, video.sourceId)
                val duplicate = if (video.sourceId.isBlank()) null else dao.all().firstOrNull {
                    it.id != jobId && it.status == JobStatus.SAVED && it.sourceId == video.sourceId &&
                    it.qualityHeight == job.qualityHeight && it.audioOnly == job.audioOnly && it.destinationTreeUri == job.destinationTreeUri &&
                    it.mediaUri?.let { uri -> runCatching { applicationContext.contentResolver.openFileDescriptor(android.net.Uri.parse(uri), "r")?.use { true } ?: false }.getOrDefault(false) } == true
                }
                val uri = duplicate?.mediaUri ?: publisher.publish(job, video)
                dao.saved(jobId, video.title, uri)
                if (foreground) runCatching { container.notifications.completed(jobId, video.title, uri, job.destinationLabel) }
            } }
            withContext(Dispatchers.IO) { directory.deleteRecursively() }
            Result.success()
        } catch (error: CancellationException) {
            withContext(NonCancellable) { dao.interrupted(jobId) }
            throw error
        } catch (error: Exception) {
            val failure = FailurePolicy.classify(error)
            val retry = FailurePolicy.shouldRetry(failure, runAttemptCount)
            dao.failed(jobId, if (retry) JobStatus.RETRYING else JobStatus.FAILED, failure.message)
            if (retry) Result.retry() else {
                if (foreground) runCatching { container.notifications.failed(jobId, failure.message) }
                // A terminal job failure is recorded in Room. Do not poison other queued work.
                Result.success()
            }
        }
    }
}
