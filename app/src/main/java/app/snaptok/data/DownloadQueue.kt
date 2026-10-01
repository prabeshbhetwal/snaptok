package app.snaptok.data

import android.content.Context
import androidx.work.*
import app.snaptok.core.VideoLink
import app.snaptok.download.DownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

class DownloadQueue(private val dao: DownloadDao, private val scheduler: DownloadScheduler, private val defaultOptions: () -> DownloadOptions = { DownloadOptions() },
    private val fileExists: (String) -> Boolean = { true },
    private val platformOptions: ((String) -> DownloadOptions)? = null) {
    private val lock = Mutex()
    val jobs = dao.observeAll()

    suspend fun enqueue(text: String, requested: DownloadOptions? = null): String = lock.withLock {
        val link = VideoLink.parse(text)
        val base = requested ?: platformOptions?.invoke(link.url) ?: defaultOptions()
        val options = if (base.audioOnly && base.destinationTreeUri == null) base.copy(destinationLabel = "Music · Music/SnapTok") else base
        require(options.itemIndex in 1..50 && options.maxSizeMb in 10..4096) { "Invalid download options" }
        val existing = dao.findRequest(downloadRequestKey(link.url, options))
        if (existing != null) {
            if (existing.status == JobStatus.SAVED) {
                val available = kotlinx.coroutines.withContext(Dispatchers.IO) { existing.mediaUri?.let(fileExists) == true }
                if (available) return@withLock "Already saved"
                dao.requestOptions(existing.id, options.unmetered, options.charging, options.maxSizeMb)
                dao.missing(existing.id); schedule(existing.id, replace = true)
                return@withLock "Saved file missing — downloading again"
            }
            if (existing.status in listOf(JobStatus.FAILED, JobStatus.CANCELLED)) {
                dao.requestOptions(existing.id, options.unmetered, options.charging, options.maxSizeMb)
                dao.reset(existing.id)
                schedule(existing.id, replace = true)
                return@withLock "Queued again"
            }
            schedule(existing.id)
            return@withLock "Already queued"
        }
        val job = DownloadJob(UUID.randomUUID().toString(), link.url,
            qualityHeight = options.qualityHeight, destinationTreeUri = options.destinationTreeUri,
            destinationLabel = options.destinationLabel, unmetered = options.unmetered, charging = options.charging,
            maxSizeMb = options.maxSizeMb, itemIndex = options.itemIndex, audioOnly = options.audioOnly,
            requestKey = downloadRequestKey(link.url, options))
        dao.insert(job)
        schedule(job.id)
        "Queued — you can keep scrolling"
    }

    suspend fun retry(id: String) = lock.withLock {
        if (dao.reset(id) > 0) schedule(id, replace = true)
    }

    suspend fun cancel(id: String) = lock.withLock {
        if (dao.cancel(id) > 0) scheduler.cancel(id)
    }

    suspend fun cleanTemporaryFiles(root: java.io.File): Long = lock.withLock {
        val active = dao.incomplete().map { it.id }.toSet()
        withContext(Dispatchers.IO) {
            var bytes = 0L
            root.listFiles().orEmpty().filter { it.isDirectory && it.name !in active }.forEach { folder ->
                val size = folder.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                if (folder.deleteRecursively()) bytes += size
            }
            bytes
        }
    }
    suspend fun reconcile() = lock.withLock {
        dao.incomplete().forEach { schedule(it.id) }
    }

    private suspend fun schedule(id: String, replace: Boolean = false) = scheduler.schedule(id, replace)
}

interface DownloadScheduler {
    suspend fun schedule(id: String, replace: Boolean)
    suspend fun cancel(id: String)
}

class AndroidDownloadScheduler(context: Context, private val dao: DownloadDao) : DownloadScheduler {
    private val appContext = context.applicationContext
    private val work get() = WorkManager.getInstance(appContext)
    override suspend fun cancel(id: String) { withContext(Dispatchers.IO) { work.cancelUniqueWork(name(id)).result.get() } }
    override suspend fun schedule(id: String, replace: Boolean) {
        val job = dao.get(id) ?: return
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf("jobId" to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (job.unmetered) NetworkType.UNMETERED else NetworkType.CONNECTED).setRequiresCharging(job.charging).setRequiresStorageNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .apply { if (!job.charging) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) }
            .addTag("downloads")
            .build()
        withContext(Dispatchers.IO) {
            work.enqueueUniqueWork(name(id), if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request).result.get()
        }
    }
    private fun name(id: String) = "download-$id"
}
