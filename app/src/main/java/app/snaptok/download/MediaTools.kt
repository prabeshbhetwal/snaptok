package app.snaptok.download

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.*
import kotlinx.coroutines.*
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@androidx.annotation.OptIn(UnstableApi::class)
object MediaTools {
    suspend fun export(context: Context, uri: Uri, output: File, startMs: Long, endMs: Long?, audio: Boolean) = withContext(Dispatchers.Main) {
        require(startMs >= 0 && (endMs == null || endMs > startMs)) { "Choose an end time after the start." }
        val item = MediaItem.Builder().setUri(uri).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startMs).apply { endMs?.let { setEndPositionMs(it) } }.build()).build()
        suspendCancellableCoroutine<Unit> { continuation ->
            val transformer = Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, result: ExportResult) { if (continuation.isActive) continuation.resume(Unit) }
                    override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                        if (continuation.isActive) continuation.resumeWithException(exception)
                    }
                }).build()
            continuation.invokeOnCancellation { android.os.Handler(android.os.Looper.getMainLooper()).post { transformer.cancel() } }
            transformer.start(EditedMediaItem.Builder(item).setRemoveVideo(audio).build(), output.absolutePath)
        }
    }
}
