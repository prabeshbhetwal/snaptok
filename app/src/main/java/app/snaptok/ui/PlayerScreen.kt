package app.snaptok.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import app.snaptok.data.DownloadJob

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(job: DownloadJob, close: () -> Unit) {
    val context = LocalContext.current
    val resume = remember { context.getSharedPreferences("playback", Context.MODE_PRIVATE) }
    var error by remember { mutableStateOf<String?>(null) }
    val player = remember(job.mediaUri) { ExoPlayer.Builder(context).build().apply {
        setMediaItem(MediaItem.fromUri(requireNotNull(job.mediaUri)))
        seekTo(resume.getLong(job.id, 0L)); prepare(); playWhenReady = true
        addListener(object : Player.Listener { override fun onPlayerError(value: PlaybackException) { error = "This file cannot be played. Check that it still exists in its folder." } })
    } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) {
            resume.edit().putLong(job.id, player.currentPosition).apply(); player.pause()
        } }
        lifecycle.addObserver(observer)
        onDispose { resume.edit().putLong(job.id, if (player.playbackState == Player.STATE_ENDED) 0 else player.currentPosition).apply(); lifecycle.removeObserver(observer); player.release() }
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Now playing", color = Color.White, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = close) { Text("Done") }
            }
            AndroidView(factory = { PlayerView(it).apply { this.player = player; setShowNextButton(false); setShowPreviousButton(false); setShowFastForwardButton(true); setShowRewindButton(true) } }, modifier = Modifier.fillMaxWidth().weight(1f))
            error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
            Text(job.title, Modifier.padding(horizontal = 20.dp), color = Color.White, maxLines = 2)
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(.5f, 1f, 1.5f, 2f).forEach { speed -> TextButton(onClick = { player.setPlaybackSpeed(speed) }) { Text("${speed}×") } }
            }
        }
    }
}
