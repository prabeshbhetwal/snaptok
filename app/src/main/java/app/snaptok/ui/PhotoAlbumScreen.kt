package app.snaptok.ui

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import app.snaptok.data.DownloadJob
import app.snaptok.data.outputs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PhotoAlbumScreen(job: DownloadJob, close: () -> Unit) {
    val context = LocalContext.current
    val images = remember(job.outputsJson) { job.outputs().filter { it.mimeType.startsWith("image/") } }
    val video = remember(job.outputsJson) { job.outputs().firstOrNull { it.mimeType.startsWith("video/") } }
    val pager = rememberPagerState { images.size }
    val scope = rememberCoroutineScope()
    var play by remember { mutableStateOf(false) }
    if (play && video != null) {
        PlayerScreen(job.copy(mediaUri = video.uri, outputsJson = "[]")) { play = false }
        return
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row { Text("${pager.currentPage + 1} of ${images.size}", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); TextButton(onClick = close) { Text("Done") } }
                HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { index ->
                    val bitmap by produceState<android.graphics.Bitmap?>(null, images[index].uri) {
                        value = withContext(Dispatchers.IO) {
                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            val uri = images[index].uri.toUri()
                            runCatching {
                                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                                var sample = 1
                                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
                                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                            }.getOrNull()
                        }
                    }
                    bitmap?.let { Image(it.asImageBitmap(), "Photo ${index + 1} of ${images.size}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                        ?: Text("Image unavailable. Check its saved folder.")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(enabled = pager.currentPage > 0, onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) { Text("Previous") }
                    TextButton(enabled = pager.currentPage < images.lastIndex, onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) { Text("Next") }
                }
                if (video != null) FilledTonalButton(onClick = { play = true }, modifier = Modifier.fillMaxWidth()) { Text("Play slideshow") }
                Row {
                    TextButton(onClick = { shareSaved(context, job) }) { Text("Share all") }
                    TextButton(onClick = { val item = images[pager.currentPage]; runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(item.uri.toUri(), item.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) } }) { Text("Open image") }
                }
            }
        }
    }
}
