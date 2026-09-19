package app.snaptok.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.snaptok.data.DownloadJob
import app.snaptok.data.JobStatus
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(16, 26, 24))
        )
        window.isNavigationBarContrastEnforced = false
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent { SnapTokTheme { SnapTokScreen() } }
    }
}

@Composable
private fun SnapTokScreen(model: MainViewModel = viewModel()) {
    val jobs by model.jobs.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val updating by model.updating.collectAsStateWithLifecycle()
    val engine by model.engineVersion.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var settings by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf("All") }
    val listState = rememberLazyListState()
    BackHandler(enabled = settings) { settings = false }
    LaunchedEffect(settings) { listState.scrollToItem(0) }
    var permissionGranted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionGranted = it }
    LaunchedEffect(model) { model.events.collect { snackbar.showSnackbar(it) } }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), state = listState, contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("SNAPTOK", fontWeight = FontWeight.Bold, letterSpacing = 3.sp, fontSize = 19.sp)
                        Text("Your videos. On your phone.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = { settings = !settings }) { Text(if (settings) "Library" else "Settings") }
                }
            }
            if (settings) {
                item {
                    Text("Make it yours.", style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("Simple defaults. Everything stays on your phone.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item { SettingCard("Save location", "Gallery · Movies/SnapTok\nVideos are saved automatically. No broad storage access is needed.") }
                item { SettingCard("Video quality", "Best available MP4 with sound. A watermarked version is saved when that is what the source provides.") }
                item {
                    Card {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Download engine", style = MaterialTheme.typography.titleMedium)
                            Text(engine, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("TikTok changes can affect downloads. Updating the engine may restore support. Updates come from the extractor project's release service.", style = MaterialTheme.typography.bodyMedium)
                            OutlinedButton(onClick = model::updateEngine, enabled = !updating) { Text(if (updating) "Waiting / updating…" else "Update engine") }
                            if (updating) Text("Keep SnapTok open. Any current download finishes first.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item { SettingCard("Privacy & permissions", "No account, adverts or analytics. Downloads contact TikTok and its media hosts directly. Engine updates contact GitHub. Only save content you are allowed to download.") }
                item { SettingCard("SnapTok 0.1.0 · Personal preview", "Uses yt-dlp through the GPL-3.0 youtubedl-android library. Supports ordinary video posts; photo slideshows and live streams are not supported yet. Download availability depends on TikTok.") }
            } else {
                item {
                    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary, RoundedCornerShape(28.dp)).padding(24.dp)) {
                        Text("SAVE IT. KEEP SCROLLING.", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                        Spacer(Modifier.height(14.dp))
                        Text("A little less tapping.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.height(12.dp))
                        Text("In TikTok, tap Share, then choose SnapTok. We’ll queue your video and return you to your feed.", color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.height(16.dp))
                        Text("You may find SnapTok under More in the share menu.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Have a link?", style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(value = text, onValueChange = { text = it.take(16384) }, modifier = Modifier.fillMaxWidth(), label = { Text("TikTok video link") }, placeholder = { Text("https://www.tiktok.com/…") }, maxLines = 3, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), shape = RoundedCornerShape(16.dp))
                        Button(onClick = { model.enqueue(text) }, enabled = text.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
                            Text(if (busy) "Adding to queue…" else "Download video")
                        }
                    }
                }
            }
            if (!permissionGranted && Build.VERSION.SDK_INT >= 33) {
                item {
                    Card {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Know when a download needs you", style = MaterialTheme.typography.titleMedium)
                            Text("Enable notifications for quiet progress and download failures. You can keep using TikTok.", style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Enable notifications") }
                        }
                    }
                }
            }
            if (!settings) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Your downloads", style = MaterialTheme.typography.titleLarge)
                        Text("${jobs.count { it.status == JobStatus.SAVED }} saved", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("All", "Active", "Saved").forEach { label -> FilterChip(selected = filter == label, onClick = { filter = label }, label = { Text(label) }) }
                    }
                }
                val visibleJobs = jobs.filter { when (filter) {
                    "Saved" -> it.status == JobStatus.SAVED
                    "Active" -> it.status in listOf(JobStatus.QUEUED, JobStatus.DOWNLOADING, JobStatus.RETRYING, JobStatus.SAVING)
                    else -> true
                } }
                if (visibleJobs.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (jobs.isEmpty()) "Your first save starts in TikTok." else "Nothing here right now.", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Saved videos appear in your gallery.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                items(visibleJobs, key = { it.id }) { job ->
                    DownloadCard(job, onRetry = { model.retry(job.id) }, onCancel = { model.cancel(job.id) }, onOpen = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(requireNotNull(job.mediaUri).toUri(), "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                        } catch (_: Exception) {
                            scope.launch { snackbar.showSnackbar("Video unavailable. Check Movies/SnapTok in your gallery.") }
                        }
                    })
                }
                item { Text("Private by design. No account. No adverts.", modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable
private fun SettingCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DownloadCard(job: DownloadJob, onRetry: () -> Unit, onCancel: () -> Unit, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(job.status.uppercase(), color = if (job.status == JobStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                Text(DateFormat.getDateInstance(DateFormat.SHORT).format(Date(job.createdAt)), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            Text(job.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Text(job.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            if (job.status == JobStatus.DOWNLOADING) {
                LinearProgressIndicator(progress = { job.progress / 100f }, modifier = Modifier.fillMaxWidth())
                Text("${job.progress}%", style = MaterialTheme.typography.labelSmall)
            }
            if (job.status == JobStatus.QUEUED) Text("Waiting for a connection and an available download slot.", style = MaterialTheme.typography.bodySmall)
            if (job.status == JobStatus.RETRYING) Text("Automatic retry scheduled · attempt ${job.attempts} of 3", style = MaterialTheme.typography.bodySmall)
            job.error?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (job.status == JobStatus.SAVED) TextButton(onClick = onOpen) { Text("Play video") }
                if (job.status in listOf(JobStatus.FAILED, JobStatus.CANCELLED)) TextButton(onClick = onRetry) { Text("Retry download") }
                if (job.status in listOf(JobStatus.QUEUED, JobStatus.DOWNLOADING, JobStatus.RETRYING)) TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}
