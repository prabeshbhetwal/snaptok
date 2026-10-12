package app.snaptok.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.snaptok.download.GalleryPublisher
import app.snaptok.download.VideoPublisher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StorageSummary(val savedBytes: Long, val temporaryBytes: Long, val freeBytes: Long)
class StorageManager(private val context: Context, private val dao: DownloadDao, private val publisher: VideoPublisher = GalleryPublisher(context)) {
    suspend fun summary(): StorageSummary = withContext(Dispatchers.IO) {
        val uris = dao.all().flatMap { it.outputs().map { output -> output.uri } }.distinct()
        val bytes = uris.sumOf { value -> runCatching {
            context.contentResolver.query(Uri.parse(value), arrayOf(OpenableColumns.SIZE), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else 0L } ?: 0L
        }.getOrDefault(0L) }
        StorageSummary(bytes, java.io.File(context.cacheDir, "staging").walkTopDown().filter { it.isFile }.sumOf { it.length() }, context.cacheDir.usableSpace)
    }
    suspend fun delete(job: DownloadJob): Unit = withContext(Dispatchers.IO) {
        val current = dao.get(job.id) ?: return@withContext
        require(current.status in listOf(JobStatus.SAVED, JobStatus.FAILED, JobStatus.CANCELLED)) { "Only finished downloads can be deleted." }
        val outputs = current.outputs(); require(outputs.isNotEmpty())
        val remaining = outputs.toMutableList()
        outputs.forEach { output ->
            publisher.remove(output.uri)
            remaining.remove(output)
            if (current.status != JobStatus.SAVED) dao.outputs(current.id, encodeOutputs(remaining))
        }
        // Aliases that point to the same file must not retain a misleading Saved entry.
        val uris = outputs.map { it.uri }.toSet()
        dao.all().filter { it.outputs().any { output -> output.uri in uris } }.forEach { alias ->
            if (alias.status == JobStatus.SAVED) dao.removeFinished(alias.id)
            else if (alias.status in listOf(JobStatus.FAILED, JobStatus.CANCELLED)) {
                dao.outputs(alias.id, encodeOutputs(alias.outputs().filter { it.uri !in uris }))
                dao.removeFinished(alias.id)
            }
        }
        dao.removeFinished(current.id)
    }
}
