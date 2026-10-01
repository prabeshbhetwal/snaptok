package app.snaptok.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StorageSummary(val savedBytes: Long, val temporaryBytes: Long, val freeBytes: Long)
class StorageManager(private val context: Context, private val dao: DownloadDao) {
    suspend fun summary(): StorageSummary = withContext(Dispatchers.IO) {
        val uris = dao.all().mapNotNull { it.mediaUri }.distinct()
        val bytes = uris.sumOf { value -> runCatching {
            context.contentResolver.query(Uri.parse(value), arrayOf(OpenableColumns.SIZE), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else 0L } ?: 0L
        }.getOrDefault(0L) }
        StorageSummary(bytes, java.io.File(context.cacheDir, "staging").walkTopDown().filter { it.isFile }.sumOf { it.length() }, context.cacheDir.usableSpace)
    }
    suspend fun delete(job: DownloadJob) = withContext(Dispatchers.IO) {
        require(job.status == JobStatus.SAVED) { "Only saved files can be deleted." }
        val uri = Uri.parse(requireNotNull(job.mediaUri))
        val removed = if (DocumentsContract.isDocumentUri(context, uri)) DocumentsContract.deleteDocument(context.contentResolver, uri)
            else context.contentResolver.delete(uri, null, null) > 0
        check(removed) { "The file could not be deleted. Check folder access." }
        // Aliases that point to the same file must not retain a misleading Saved entry.
        dao.all().filter { it.mediaUri == job.mediaUri }.forEach { dao.removeFinished(it.id) }
    }
}
