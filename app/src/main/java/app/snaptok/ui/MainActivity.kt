package app.snaptok.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.lifecycleScope
import app.snaptok.SnapTokApplication
import app.snaptok.R
import app.snaptok.core.VideoQuality
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
        window.isNavigationBarContrastEnforced = true
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent { SnapTokTheme { SnapTokScreen() } }
        openCompletedVideo(intent)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openCompletedVideo(intent)
    }
    private fun openCompletedVideo(intent: Intent) {
        val id = intent.getStringExtra("openJobId") ?: return
        intent.removeExtra("openJobId")
        lifecycleScope.launch {
            val job = (application as SnapTokApplication).container.dao.get(id) ?: return@launch
            val uri = job.mediaUri ?: return@launch
            try { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri.toUri(), "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
            catch (_: Exception) { Toast.makeText(this@MainActivity, "Cannot open the saved video. Check your chosen folder.", Toast.LENGTH_LONG).show() }
        }
    }
}

@Composable
private fun SnapTokScreen(model: MainViewModel = viewModel()) {
    val jobs by model.jobs.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val updating by model.updating.collectAsStateWithLifecycle()
    val engine by model.engineVersion.collectAsStateWithLifecycle()
    val preferences by model.preferences.collectAsStateWithLifecycle()
    val inspecting by model.inspecting.collectAsStateWithLifecycle()
    val qualities by model.qualities.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var qualityHeight by rememberSaveable { mutableIntStateOf(preferences.options.qualityHeight) }
    var qualityMenu by remember { mutableStateOf(false) }
    LaunchedEffect(preferences.options.qualityHeight) { qualityHeight = preferences.options.qualityHeight }
    var settings by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf("All") }
    val listState = rememberLazyListState()
    BackHandler(enabled = settings) { settings = false }
    LaunchedEffect(settings) { listState.scrollToItem(0) }
    var permissionGranted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionGranted = it }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(model::chooseFolder) }
    LifecycleResumeEffect(Unit) {
        permissionGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        onPauseOrDispose { }
    }
    LaunchedEffect(model) {
        model.events.collect { event ->
            when (event) {
                is UiEvent.Accepted -> if (text == event.input) { text = ""; model.invalidateQualities(); qualityMenu = false; qualityHeight = preferences.options.qualityHeight }
                is UiEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }
    fun pickFolder() { folderPicker.launch(preferences.options.destinationTreeUri?.toUri()) }
    fun systemNotifications() {
        context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
    }
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
                settingsItems(model, preferences, updating, engine, ::pickFolder, ::systemNotifications)
            } else {
                item {
                    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary, RoundedCornerShape(28.dp)).padding(24.dp)) {
                        Text("SAVE IT. KEEP SCROLLING.", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                        Spacer(Modifier.height(14.dp))
                        Text("A little less tapping.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.height(12.dp))
                        Text("In TikTok or X, tap Share, then choose SnapTok. We’ll queue your video and return you to your feed.", color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.height(16.dp))
                        Text("You may find SnapTok under More in the share menu.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Have a link?", style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(
                            value = text, onValueChange = { text = it.take(16384); model.invalidateQualities(); qualityMenu = false; qualityHeight = preferences.options.qualityHeight },
                            modifier = Modifier.fillMaxWidth(), label = { Text("TikTok or X video link") },
                            placeholder = { Text("Paste a video-post link") }, maxLines = 3, readOnly = busy,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), shape = RoundedCornerShape(16.dp),
                            trailingIcon = { IconButton(enabled = !busy, onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val pasted = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                                if (pasted.isNullOrBlank()) model.notify("No link found on the clipboard")
                                else { text = pasted.take(16384); model.invalidateQualities(); qualityMenu = false; qualityHeight = preferences.options.qualityHeight }
                            }) { Icon(painterResource(R.drawable.ic_paste), contentDescription = "Paste link") } }
                        )
                        Box {
                            OutlinedButton(enabled = text.isNotBlank() && !busy && !inspecting, onClick = {
                                qualityMenu = true
                                if (qualities == null) model.inspect(text)
                            }) { Text(if (inspecting) "Checking available qualities…" else "Quality · ${VideoQuality.label(qualityHeight)}") }
                            DropdownMenu(expanded = qualityMenu && qualities != null, onDismissRequest = { qualityMenu = false }) {
                                DropdownMenuItem(text = { Text("Best available") }, onClick = { qualityHeight = 0; qualityMenu = false })
                                qualities?.heights?.forEach { height -> DropdownMenuItem(text = { Text(VideoQuality.label(height)) }, onClick = { qualityHeight = height; qualityMenu = false }) }
                                if (qualities?.heights?.isEmpty() == true) DropdownMenuItem(text = { Text("No alternative MP4 qualities reported") }, onClick = {}, enabled = false)
                            }
                        }
                        TextButton(onClick = ::pickFolder) { Text("Save to · ${preferences.options.destinationLabel}") }
                        Button(onClick = { model.enqueue(text, qualityHeight) }, enabled = text.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
                            Text(if (busy) "Adding to queue…" else "Download video")
                        }
                    }
                }
            }
            if (!permissionGranted && preferences.notificationsEnabled && Build.VERSION.SDK_INT >= 33) {
                item {
                    Card {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Know when a download needs you", style = MaterialTheme.typography.titleMedium)
                            Text("Enable notifications for quiet progress and download failures. You can keep using TikTok.", style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Enable notifications") }
                            TextButton(onClick = ::systemNotifications) { Text("Open Android settings") }
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
                        Text(if (jobs.isEmpty()) "Your first save starts with a share." else "Nothing here right now.", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Saved videos appear in your chosen folder.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                items(visibleJobs, key = { it.id }) { job ->
                    DownloadCard(job, onSource = {
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, job.url.toUri())) }
                        catch (_: Exception) { model.notify("No app is available to open this link") }
                    }, onRetry = { model.retry(job.id) }, onCancel = { model.cancel(job.id) }, onOpen = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(requireNotNull(job.mediaUri).toUri(), "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                        } catch (_: Exception) {
                            scope.launch { snackbar.showSnackbar("Video unavailable. Check your chosen folder.") }
                        }
                    })
                }
                item { Text("Private by design. No account. No adverts.", modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable
private fun DownloadCard(job: DownloadJob, onSource: () -> Unit, onRetry: () -> Unit, onCancel: () -> Unit, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(job.status.uppercase(), color = if (job.status == JobStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                Text(DateFormat.getDateInstance(DateFormat.SHORT).format(Date(job.createdAt)), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            Text(job.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Text(job.url, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = "Open original video", onClick = onSource).padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline, maxLines = 2)
            Text("Requested: ${VideoQuality.label(job.qualityHeight)} · ${job.destinationLabel}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (job.status == JobStatus.DOWNLOADING) {
                LinearProgressIndicator(progress = { job.progress / 100f }, modifier = Modifier.fillMaxWidth())
                Text("${job.progress}%", style = MaterialTheme.typography.labelSmall)
            }
            if (job.status == JobStatus.SAVING) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Finishing and verifying the saved file…", style = MaterialTheme.typography.bodySmall) }
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
