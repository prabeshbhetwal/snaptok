package app.snaptok.download

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import app.snaptok.data.DownloadJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

class DestinationUnavailableException(message: String) : IOException(message)

/** SAF access is confined to the folder the user explicitly selected. */
class FolderStorage(private val context: Context) {
    private val resolver get() = context.contentResolver
    private data class Child(val uri: Uri, val name: String, val flags: Int)

    suspend fun labelAndValidate(tree: Uri): String = withContext(Dispatchers.IO) {
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        resolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use {
            if (!it.moveToFirst() || it.getInt(1) and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE == 0) unavailable()
            if (tree.authority == "com.android.externalstorage.documents")
                "Folder · " + DocumentsContract.getTreeDocumentId(tree).substringAfter(':')
            else "Folder · " + it.getString(0)
        } ?: unavailable()
    }

    suspend fun publish(job: DownloadJob, video: DownloadedVideo): String = withContext(Dispatchers.IO) {
        val tree = Uri.parse(requireNotNull(job.destinationTreeUri))
        try {
            labelAndValidate(tree)
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val finalName = "SnapTok-${job.id}.${video.extension}"
            val partialName = ".SnapTok-${job.id}.partial.${video.extension}"
            val expectedHash = video.file.inputStream().use(::hash)
            val existing = children(tree)
            existing.firstOrNull { it.name == finalName }?.let {
                val actual = resolver.openInputStream(it.uri)?.use(::hash)
                if (actual != null && actual.contentEquals(expectedHash)) return@withContext it.uri.toString()
                throw DestinationUnavailableException("A conflicting file already exists in the selected folder. Choose another folder and download again.")
            }
            existing.filter { it.name == partialName }.forEach { DocumentsContract.deleteDocument(resolver, it.uri) }
            val partial = DocumentsContract.createDocument(resolver, parent, video.mimeType, partialName) ?: unavailable()
            try {
                val flags = resolver.query(partial, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use {
                    if (it.moveToFirst()) it.getInt(0) else 0
                } ?: 0
                if (flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME == 0)
                    throw DestinationUnavailableException("This folder provider cannot finish files safely. Choose a local folder in Movies or Downloads.")
                resolver.openOutputStream(partial, "w")?.use { output -> video.file.inputStream().use { it.copyTo(output) } } ?: unavailable()
                val writtenHash = resolver.openInputStream(partial)?.use(::hash)
                if (writtenHash == null || !writtenHash.contentEquals(expectedHash)) throw IOException("Saved file did not pass verification")
                val complete = DocumentsContract.renameDocument(resolver, partial, finalName) ?: unavailable()
                complete.toString()
            } catch (error: Exception) {
                runCatching { DocumentsContract.deleteDocument(resolver, partial) }
                throw error
            }
        } catch (error: SecurityException) {
            throw DestinationUnavailableException("Folder access was revoked. Choose the folder again in Settings, then retry.")
        } catch (error: IllegalArgumentException) {
            throw DestinationUnavailableException("The selected folder is unavailable. Choose it again in Settings.")
        }
    }

    private fun children(tree: Uri): List<Child> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val rows = mutableListOf<Child>()
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use { cursor: Cursor ->
            while (cursor.moveToNext()) rows.add(Child(DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)), cursor.getString(1), cursor.getInt(2)))
        } ?: unavailable()
        return rows
    }
    private fun hash(input: InputStream): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) { val count = input.read(buffer); if (count == -1) break; digest.update(buffer, 0, count) }
        return digest.digest()
    }
    private fun unavailable(): Nothing = throw DestinationUnavailableException("Cannot write to the selected folder. Choose it again in Settings.")
}
