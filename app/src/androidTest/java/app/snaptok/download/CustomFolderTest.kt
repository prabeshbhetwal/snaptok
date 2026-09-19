package app.snaptok.download

import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.platform.app.InstrumentationRegistry
import app.snaptok.SnapTokApplication
import app.snaptok.data.DownloadJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import java.io.File
import java.util.UUID

class CustomFolderTest {
    @Test fun chosenFolderPublishesOneVerifiedFileAndRecoversPartial() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val treeArgument = InstrumentationRegistry.getArguments().getString("folderTree")
        assumeNotNull(treeArgument)
        val context = instrumentation.targetContext
        val tree = Uri.parse(treeArgument!!)
        // The test requires a real user-selected, persistently granted folder.
        assertTrue(context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission })
        val file = File(context.cacheDir, "custom-folder-test.mp4")
        instrumentation.context.assets.open("sample.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
        val job = DownloadJob(UUID.randomUUID().toString(), "https://www.tiktok.com/@test/video/1234567890", destinationTreeUri = tree.toString(), destinationLabel = "Test folder")
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val partial = DocumentsContract.createDocument(context.contentResolver, parent, "video/mp4", ".SnapTok-${job.id}.partial.mp4")!!
        context.contentResolver.openOutputStream(partial)!!.use { it.write(byteArrayOf(1, 2, 3)) }
        var saved: Uri? = null
        try {
            val publisher = GalleryPublisher(context)
            saved = Uri.parse(publisher.publish(job, DownloadedVideo(file, "Test")))
            assertEquals(saved.toString(), publisher.publish(job, DownloadedVideo(file, "Test")))
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val names = mutableListOf<String>()
            context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)!!.use {
                while (it.moveToNext()) names.add(it.getString(0))
            }
            assertEquals(1, names.count { it == "SnapTok-${job.id}.mp4" })
            assertFalse(names.contains(".SnapTok-${job.id}.partial.mp4"))
            context.contentResolver.openInputStream(saved)!!.use { assertArrayEquals(file.readBytes(), it.readBytes()) }
        } finally {
            saved?.let { runCatching { DocumentsContract.deleteDocument(context.contentResolver, it) } }
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, partial) }
            file.delete()
        }
    }
}
