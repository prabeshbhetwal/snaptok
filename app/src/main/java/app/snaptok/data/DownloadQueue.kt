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

class DownloadQueue(private val dao: DownloadDao, private val scheduler: DownloadScheduler, private val defaultOptions: () -> DownloadOptions = { DownloadOptions() }) {
    private val lock = Mutex()
    val jobs = dao.observeAll()

    suspend fun enqueue(text: String, options: DownloadOptions = defaultOptions()): String = lock.withLock {
        val link = VideoLink.parse(text)
        val existing = dao.findRequest(downloadRequestKey(link.url, options))
        if (existing != null) {
            if (existing.status == JobStatus.SAVED) return@withLock "Already saved"
            if (existing.status in listOf(JobStatus.FAILED, JobStatus.CANCELLED)) {
                dao.reset(existing.id)
                schedule(existing.id, replace = true)
                return@withLock "Queued again"
            }
            schedule(existing.id)
            return@withLock "Already queued"
        }
        val job = DownloadJob(UUID.randomUUID().toString(), link.url,
            qualityHeight = options.qualityHeight, destinationTreeUri = options.destinationTreeUri,
            destinationLabel = options.destinationLabel)
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

    suspend fun reconcile() = lock.withLock {
        dao.incomplete().forEach { schedule(it.id) }
    }

    private suspend fun schedule(id: String, replace: Boolean = false) = scheduler.schedule(id, replace)
}

interface DownloadScheduler {
    suspend fun schedule(id: String, replace: Boolean)
    suspend fun cancel(id: String)
}

class AndroidDownloadScheduler(context: Context) : DownloadScheduler {
    private val appContext = context.applicationContext
    private val work get() = WorkManager.getInstance(appContext)
    override suspend fun cancel(id: String) { withContext(Dispatchers.IO) { work.cancelUniqueWork(name(id)).result.get() } }
    override suspend fun schedule(id: String, replace: Boolean) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf("jobId" to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag("downloads")
            .build()
        withContext(Dispatchers.IO) {
            work.enqueueUniqueWork(name(id), if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request).result.get()
        }
    }
    private fun name(id: String) = "download-$id"
}
