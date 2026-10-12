package app.snaptok.download

import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.platform.app.InstrumentationRegistry
import app.snaptok.SnapTokApplication
import app.snaptok.data.DownloadJob
import app.snaptok.ui.FolderFixtureTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class CustomFolderTest {
    @Test fun chosenFolderPublishesOneVerifiedFileAndRecoversPartial() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = (context.applicationContext as SnapTokApplication).container.preferences
        val previous = preferences.snapshot()
        var file: File? = null
        var partial: Uri? = null
        var saved: Uri? = null
        try {
            if (InstrumentationRegistry.getArguments().getString("folderTree") == null &&
                preferences.snapshot().options.destinationTreeUri == null) {
                FolderFixtureTest().chooseIsolatedFolderThroughSystemPicker()
            }
            val treeArgument = InstrumentationRegistry.getArguments().getString("folderTree")
                ?: preferences.snapshot().options.destinationTreeUri
            requireNotNull(treeArgument) { "Choose an isolated, persistently granted folder before the SAF suite." }
            val tree = Uri.parse(treeArgument)
            // The test requires a real user-selected, persistently granted folder.
            assertTrue(context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission })
            val fixture = File(context.cacheDir, "custom-folder-test.mp4").also { file = it }
            instrumentation.context.assets.open("sample.mp4").use { input -> fixture.outputStream().use { input.copyTo(it) } }
            val job = DownloadJob(UUID.randomUUID().toString(), "https://www.tiktok.com/@test/video/1234567890", destinationTreeUri = tree.toString(), destinationLabel = "Test folder")
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val pending = DocumentsContract.createDocument(context.contentResolver, parent, "video/mp4", ".SnapTok-${job.id}.partial.mp4")!!
            partial = pending
            context.contentResolver.openOutputStream(pending)!!.use { it.write(byteArrayOf(1, 2, 3)) }
            val publisher = GalleryPublisher(context)
            val complete = Uri.parse(publisher.publish(job, DownloadedVideo(fixture, "Test")))
            saved = complete
            assertEquals(complete.toString(), publisher.publish(job, DownloadedVideo(fixture, "Test")))
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val names = mutableListOf<String>()
            context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)!!.use {
                while (it.moveToNext()) names.add(it.getString(0))
            }
            assertEquals(1, names.count { it == "SnapTok-${job.id}.mp4" })
            assertFalse(names.contains(".SnapTok-${job.id}.partial.mp4"))
            context.contentResolver.openInputStream(complete)!!.use { assertArrayEquals(fixture.readBytes(), it.readBytes()) }
        } finally {
            try {
                saved?.let { runCatching { DocumentsContract.deleteDocument(context.contentResolver, it) } }
                partial?.let { runCatching { DocumentsContract.deleteDocument(context.contentResolver, it) } }
                file?.delete()
            } finally {
                withContext(NonCancellable) { preferences.update { previous } }
            }
        }
    }
}
