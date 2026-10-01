package app.snaptok.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.os.Build
import android.os.Bundle
import android.content.ContentResolver
import app.snaptok.data.DownloadJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

interface VideoPublisher {
    suspend fun publish(job: DownloadJob, video: DownloadedVideo): String
}

class GalleryPublisher(private val context: Context) : VideoPublisher {
    override suspend fun publish(job: DownloadJob, video: DownloadedVideo): String = withContext(Dispatchers.IO) {
        if (!video.file.isFile || video.file.length() < 1024) throw IOException("Downloaded file is incomplete")
        MediaMetadataRetriever().use { metadata ->
            metadata.setDataSource(video.file.absolutePath)
            if (metadata.extractMetadata(if (video.audioOnly) MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO else MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != "yes" ||
                (metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) <= 0) {
                throw IOException("Downloaded file is not a playable video")
            }
        }
        if (job.destinationTreeUri != null) return@withContext FolderStorage(context).publish(job, video)
        val resolver = context.contentResolver
        val collection = if (video.audioOnly) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val name = "SnapTok-${job.id}.${video.extension}"
        val folder = if (video.audioOnly) "Music/SnapTok/" else "Movies/SnapTok/"
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
