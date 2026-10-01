package app.snaptok.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class SavedMediaDetails(val width: Int, val height: Int, val durationMs: Long, val bytes: Long?, val hasAudio: Boolean, val thumbnail: Bitmap?)

internal fun readSavedMedia(context: Context, uri: Uri): SavedMediaDetails {
    val bytes = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
        if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
    }
    return MediaMetadataRetriever().use { reader ->
        reader.setDataSource(context, uri)
        fun number(key: Int) = reader.extractMetadata(key)?.toLongOrNull() ?: 0L
        val rotation = number(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
        val width = number(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toInt()
        val height = number(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toInt()
        SavedMediaDetails(if (rotation % 180 == 0L) width else height, if (rotation % 180 == 0L) height else width,
            number(MediaMetadataRetriever.METADATA_KEY_DURATION), bytes, reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes",
            runCatching { reader.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 240, 240) }.getOrNull())
    }
}

@Composable
internal fun SavedVideoDetails(uri: String) {
    val context = LocalContext.current
    val state by produceState<Pair<Boolean, SavedMediaDetails?>>(false to null, uri) {
        value = true to withContext(Dispatchers.IO) { runCatching { readSavedMedia(context, Uri.parse(uri)) }.getOrNull() }
    }
    val reservedHeight = (72 * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp
    val media = state.second
    Row(Modifier.fillMaxWidth().heightIn(min = reservedHeight), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            media?.thumbnail?.let { Image(it.asImageBitmap(), "Saved media thumbnail", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                ?: Icon(if (media != null && media.width == 0) SnapIcons.MusicNote else SnapIcons.VideoFile, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (media == null) Text(if (state.first) "File details unavailable" else "Reading file details…", style = MaterialTheme.typography.bodySmall)
        else Column {
            Text(if (media.width > 0) "Saved · ${media.width} × ${media.height}" else "Saved audio", style = MaterialTheme.typography.labelLarge)
            val seconds = media.durationMs / 1000
            if (!media.hasAudio) Text("No audio track", style = MaterialTheme.typography.labelSmall)
            Text("${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}" + (media.bytes?.let { " · ${Formatter.formatFileSize(context, it)}" } ?: ""), style = MaterialTheme.typography.bodySmall)
        }
    }
}
