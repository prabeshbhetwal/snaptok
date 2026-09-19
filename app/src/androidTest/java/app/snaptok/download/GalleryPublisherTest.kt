package app.snaptok.download

import android.content.Context
import android.content.ContentValues
import android.content.ContentResolver
import android.os.Bundle
import android.os.Build
import android.net.Uri
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import app.snaptok.data.DownloadJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class GalleryPublisherTest {
    @Test fun publishesPlayableVideoAndRecoversSameReceipt() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context: Context = instrumentation.targetContext
        val file = File(context.cacheDir, "publication-test.mp4")
        instrumentation.context.assets.open("sample.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
        val job = DownloadJob(UUID.randomUUID().toString(), "https://www.tiktok.com/@test/video/1234567890")
        val publisher = GalleryPublisher(context)
        var uri: Uri? = null
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val pending = context.contentResolver.insert(collection, ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "SnapTok-${job.id}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SnapTok/")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        })!!
        try {
            val video = DownloadedVideo(file, "SnapTok publication test")
            uri = Uri.parse(publisher.publish(job, video))
            assertEquals(uri.toString(), publisher.publish(job, video))
            val query = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.Video.Media.DISPLAY_NAME} = ?")
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf("SnapTok-${job.id}.mp4"))
                if (Build.VERSION.SDK_INT >= 30) putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
            }
            @Suppress("DEPRECATION")
            val queryUri = if (Build.VERSION.SDK_INT == 29) MediaStore.setIncludePending(collection) else collection
            context.contentResolver.query(queryUri, arrayOf(MediaStore.Video.Media._ID), query, null)!!.use {
                assertEquals("No orphaned pending row or duplicate publication", 1, it.count)
            }
            context.contentResolver.query(uri, arrayOf(MediaStore.Video.Media.IS_PENDING, MediaStore.Video.Media.SIZE), null, null, null)!!.use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
                assertEquals(file.length(), it.getLong(1))
            }
            context.contentResolver.openInputStream(uri)!!.use { assertEquals(file.length(), it.readBytes().size.toLong()) }
        } finally {
            uri?.let { context.contentResolver.delete(it, null, null) }
            runCatching { context.contentResolver.delete(pending, null, null) }
            file.delete()
        }
    }

    @Test fun invalidFileNeverPublishes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "invalid-test.mp4").apply { writeText("not a video") }
        try {
            GalleryPublisher(context).publish(DownloadJob(UUID.randomUUID().toString(), "unused"), DownloadedVideo(file, "Invalid"))
            fail("Invalid file was accepted")
        } catch (_: java.io.IOException) { }
        finally { file.delete() }
    }
}
