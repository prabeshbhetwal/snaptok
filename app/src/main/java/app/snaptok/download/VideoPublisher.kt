package app.snaptok.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.os.Build
import android.os.Bundle
import android.content.ContentResolver
import android.provider.DocumentsContract
import app.snaptok.data.DownloadJob
import app.snaptok.data.DownloadDao
import app.snaptok.data.JobStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.FileNotFoundException

interface VideoPublisher {
    suspend fun publish(job: DownloadJob, video: DownloadedVideo): String
    suspend fun remove(uri: String): Unit = throw IOException("This publisher cannot remove published files")
}

internal class ExportCleanupException(message: String, cause: Exception) : IOException(message, cause)

// shortcut: process death between publication and receipt can orphan exports, add durable export reconciliation before claiming crash recovery.
internal suspend fun VideoPublisher.publishExport(dao: DownloadDao, job: DownloadJob, video: DownloadedVideo): String {
    val uri = publish(job, video)
    try {
        if (dao.insert(job.copy(mediaUri = uri)) == -1L) throw IOException("The exported file could not be recorded.")
        return uri
    } catch (error: Exception) {
        try { remove(uri) }
        catch (cleanup: Exception) {
            if (cleanup !== error) error.addSuppressed(cleanup)
            val message = "The export failed and its file could not be removed. Restore folder access, then choose Delete partial files."
            val retained = try {
                dao.insert(job.copy(status = JobStatus.FAILED, mediaUri = uri, outputsJson = "[]", error = message)) != -1L
            } catch (receipt: Exception) {
                if (receipt !== error) error.addSuppressed(receipt)
                false
            }
            throw ExportCleanupException(if (retained) message else "The export failed and its file could not be removed. Restore folder access and delete the exported file in Files.", error)
        }
        throw error
    }
}

class GalleryPublisher(private val context: Context) : VideoPublisher {
    override suspend fun remove(uri: String): Unit = withContext(Dispatchers.IO) {
        val value = Uri.parse(uri)
        val resolver = context.contentResolver
        try {
            val removed = if (DocumentsContract.isDocumentUri(context, value)) DocumentsContract.deleteDocument(resolver, value)
                else resolver.delete(value, null, null) > 0
            if (!removed) {
                resolver.openFileDescriptor(value, "r")?.use { }
                throw IOException("Some files could not be deleted. Check folder access and retry.")
            }
        } catch (_: FileNotFoundException) {
            // A missing asset already satisfies cleanup; denied access must still fail.
        }
    }

    override suspend fun publish(job: DownloadJob, video: DownloadedVideo): String = withContext(Dispatchers.IO) {
        if (!video.file.isFile || video.file.length() <= 0 || (!video.photo && video.file.length() < 1024)) throw IOException("Downloaded file is incomplete")
        if (video.photo) {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(video.file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Downloaded image is invalid")
        } else MediaMetadataRetriever().use { metadata ->
            metadata.setDataSource(video.file.absolutePath)
            if (metadata.extractMetadata(if (video.audioOnly) MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO else MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != "yes" ||
                (metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) <= 0) {
                throw IOException("Downloaded file is not a playable video")
            }
        }
        if (job.destinationTreeUri != null) return@withContext FolderStorage(context).publish(job, video)
        val resolver = context.contentResolver
        val collection = if (video.photo) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else if (video.audioOnly) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val name = "SnapTok-${job.id}.${video.extension}"
        val folder = if (video.photo) "Pictures/SnapTok/" else if (video.audioOnly) "Music/SnapTok/" else "Movies/SnapTok/"
        // Stable name recovers publication after process death, including the window
        // between making the file visible and recording its URI in Room.
        val query = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.Video.Media.DISPLAY_NAME} = ? AND ${MediaStore.Video.Media.RELATIVE_PATH} = ?")
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(name, folder))
            if (Build.VERSION.SDK_INT >= 30) putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
        }
        @Suppress("DEPRECATION")
        val queryUri = if (Build.VERSION.SDK_INT == 29) MediaStore.setIncludePending(collection) else collection
        resolver.query(queryUri, arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.IS_PENDING), query, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val uri = Uri.withAppendedPath(collection, cursor.getLong(0).toString())
                if (cursor.getInt(1) == 0) return@withContext uri.toString()
                resolver.delete(uri, null, null)
            }
        }
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.TITLE, video.title)
            put(MediaStore.Video.Media.MIME_TYPE, video.mimeType)
            put(MediaStore.Video.Media.RELATIVE_PATH, folder)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("Could not create gallery entry")
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                video.file.inputStream().use { it.copyTo(output) }
            } ?: throw IOException("Could not open gallery file")
            check(resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null) == 1)
            uri.toString()
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }
}
