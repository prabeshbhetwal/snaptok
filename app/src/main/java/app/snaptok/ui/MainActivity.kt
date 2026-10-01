package app.snaptok.ui

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.snaptok.data.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance

class MainActivity : ComponentActivity() {
    private val model by viewModels<MainViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent {
            val settings by model.preferences.collectAsStateWithLifecycle()
            SnapTokTheme(settings.theme) { SnapTokScreen(model) }
        }
        intent.getStringExtra("openJobId")?.let(model::openJob)
        if (intent.getBooleanExtra("showLibrary", false)) model.page.value = 1
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); intent.getStringExtra("openJobId")?.let(model::openJob); if (intent.getBooleanExtra("showLibrary", false)) model.page.value = 1 }
    override fun onPostResume() {
        super.onPostResume()
        if (intent.getBooleanExtra("restoreFloating", false)) {
            intent.removeExtra("restoreFloating")
            if (android.provider.Settings.canDrawOverlays(this)) runCatching { ContextCompat.startForegroundService(this, Intent(this, app.snaptok.overlay.FloatingSaveService::class.java)) }
        }
    }
}

@Composable
private fun SnapTokScreen(model: MainViewModel) {
    val jobs by model.jobs.collectAsStateWithLifecycle()
    val preferences by model.preferences.collectAsStateWithLifecycle()
    val player by model.player.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tab by model.page.collectAsStateWithLifecycle()
    val pageState = rememberSaveableStateHolder()
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(model::chooseFolder) }
    fun folder() { picker.launch(preferences.options.destinationTreeUri?.toUri()) }
    LaunchedEffect(model) { model.events.filterIsInstance<UiEvent.Message>().collectLatest { event ->
        val result = snackbar.showSnackbar(event.text, actionLabel = if (event.openJobId != null) "Open" else null)
        if (result == SnackbarResult.ActionPerformed) event.openJobId?.let(model::openJob)
    } }
    BackHandler(enabled = tab != 0 && player == null) { model.page.value = 0 }
    player?.let { PlayerScreen(it, model::closePlayer) }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { NavigationBar {
            listOf("Save" to SnapIcons.AddCircleOutline, "Library" to SnapIcons.VideoLibrary, "Settings" to SnapIcons.Tune).forEachIndexed { index, item ->
                NavigationBarItem(selected = tab == index, onClick = { model.page.value = index }, icon = { Icon(item.second, null) }, label = { Text(item.first) })
            }
        } }
    ) { padding ->
        Crossfade(targetState = tab, animationSpec = tween(if (preferences.reduceMotion) 0 else 180), label = "Page transition", modifier = Modifier.padding(padding)) { page ->
            pageState.SaveableStateProvider(page) {
            when (page) {
                0 -> SaveScreen(model, jobs, preferences, { model.page.value = 2 }, { model.page.value = 1 })
                1 -> LibraryScreen(model, jobs, preferences)
                else -> SettingsScreen(model, preferences, ::folder) {
                    context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            }
            }
        }
    }
}

fun shareSaved(context: Context, job: DownloadJob) {
    try {
        val uri = requireNotNull(job.mediaUri).toUri()
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(if (job.audioOnly) "audio/mp4" else "video/mp4")
            .putExtra(Intent.EXTRA_STREAM, uri).apply { clipData = ClipData.newRawUri("Saved media", uri) }
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share saved media"))
    } catch (_: Exception) { Toast.makeText(context, "Saved file unavailable. Check your folder.", Toast.LENGTH_LONG).show() }
}
