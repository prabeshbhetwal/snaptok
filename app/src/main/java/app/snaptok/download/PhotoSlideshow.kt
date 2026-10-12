package app.snaptok.download

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.*
import kotlinx.coroutines.*
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@androidx.annotation.OptIn(UnstableApi::class)
object PhotoSlideshow {
    suspend fun render(context: Context, images: List<File>, music: File?, output: File, seconds: Int, quality: Int, progress: (Int) -> Unit) = withContext(Dispatchers.Main) {
        require(images.isNotEmpty() && seconds in 2..10)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(images.first().absolutePath, bounds)
        val scale = minOf(1.0, (if (quality == 0) 1080 else quality).toDouble() / maxOf(bounds.outWidth, bounds.outHeight))
        val width = maxOf(2, (bounds.outWidth * scale).toInt() / 2 * 2)
        val height = maxOf(2, (bounds.outHeight * scale).toInt() / 2 * 2)
        val frames = images.map { file ->
            EditedMediaItem.Builder(MediaItem.Builder().setUri(android.net.Uri.fromFile(file)).setImageDurationMs(seconds * 1000L).build())
                .setDurationUs(seconds * 1_000_000L).setFrameRate(24)
                .setEffects(Effects(emptyList(), listOf(Presentation.createForWidthAndHeight(width, height, Presentation.LAYOUT_SCALE_TO_FIT)))).build()
        }
        val sequences = mutableListOf(EditedMediaItemSequence.Builder(frames).build())
        music?.let { sequences += EditedMediaItemSequence.Builder(EditedMediaItem.Builder(MediaItem.fromUri(android.net.Uri.fromFile(it))).setRemoveVideo(true).build()).setIsLooping(true).build() }
        val composition = Composition.Builder(sequences).build()
        suspendCancellableCoroutine<Unit> { continuation ->
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            var ticker: Runnable? = null
            val transformer = Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, result: ExportResult) {
                        ticker?.let(handler::removeCallbacks)
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                    override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                        ticker?.let(handler::removeCallbacks)
                        if (continuation.isActive) continuation.resumeWithException(exception)
                    }
                }).build()
            ticker = Runnable {
                val holder = ProgressHolder()
                if (continuation.isActive) {
                    if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) progress(70 + holder.progress * 29 / 100)
                    ticker?.let { handler.postDelayed(it, 600) }
                }
            }
            continuation.invokeOnCancellation { handler.post { ticker?.let(handler::removeCallbacks); transformer.cancel() } }
            transformer.start(composition, output.absolutePath)
            handler.post(ticker!!)
        }
        if (music != null) MediaMetadataRetriever().use { reader ->
            reader.setDataSource(output.absolutePath)
            check(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes") { "The slideshow could not retain the soundtrack. Try again or turn off Include music." }
        }
    }
}
