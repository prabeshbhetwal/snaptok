package app.snaptok.ui

import android.content.ClipboardManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.snaptok.core.VideoQuality
import app.snaptok.data.*

@Composable
fun PageHeading(eyebrow: String, title: String, description: String) {
    Text(eyebrow, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
    Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SaveScreen(model: MainViewModel, jobs: List<DownloadJob>, settings: DownloadSettings, folder: () -> Unit, library: () -> Unit) {
    val text by model.input.collectAsStateWithLifecycle()
    var quality by rememberSaveable { mutableIntStateOf(settings.options.qualityHeight) }
    var batch by rememberSaveable { mutableStateOf(false) }
    var audio by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf(1)) }
    var help by remember { mutableStateOf(false) }
    val busy by model.busy.collectAsStateWithLifecycle()
    val inspecting by model.inspecting.collectAsStateWithLifecycle()
    val qualities by model.qualities.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val destination = model.defaultsFor(text)
    val folderLabel = if (batch) "Platform folders from Settings" else if (audio && destination.destinationTreeUri == null) "Music · Music/SnapTok" else destination.destinationLabel
    LaunchedEffect(model) { model.events.collect { if (it is UiEvent.Accepted) { selected = setOf(1); model.invalidateQualities() } } }
    fun change(value: String) { model.input.value = value.take(if (batch) 100000 else 16384); quality = model.defaultsFor(value).qualityHeight; model.invalidateQualities(); selected = setOf(1) }
    Column(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { PageHeading("SNAPTOK", if (largeText) "Save a video" else "Save something good.", "Your favourites, ready when you are.") }
        if (!largeText) item {
            Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer)), RoundedCornerShape(24.dp)).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(SnapIcons.CloudDownload, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text("Copy. Tap. Keep scrolling.", style = MaterialTheme.typography.titleMedium)
                    Text("Use the floating Save button or share a link to SnapTok.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { NotificationPermissionCard(settings.notificationsEnabled) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !batch, onClick = { batch = false; change(text) }, label = { Text("One link") })
                    FilterChip(selected = batch, onClick = { batch = true; model.invalidateQualities() }, label = { Text("Batch links") })
                }
                OutlinedTextField(value = text, onValueChange = ::change, modifier = Modifier.fillMaxWidth(), readOnly = busy,
                    label = { Text(if (batch) "Paste up to 50 video links" else "Video link") }, minLines = if (batch) 3 else 1, maxLines = 5,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), shape = RoundedCornerShape(18.dp),
                    trailingIcon = { IconButton(enabled = !busy, onClick = {
                        val pasted = context.getSystemService(ClipboardManager::class.java).primaryClip?.let { if (it.itemCount > 0) it.getItemAt(0).text?.toString() else null }
                        if (pasted.isNullOrBlank()) model.notify("Copy a video link first") else change(pasted)
                    }) { Icon(SnapIcons.ContentPaste, "Paste link") } })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Audio only", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = audio, onCheckedChange = { audio = it }, modifier = Modifier.semanticsLabel("Audio only"))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        OutlinedButton(onClick = { menu = true }, enabled = !audio) { Icon(SnapIcons.HighQuality, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(VideoQuality.label(quality)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            (qualities?.heights?.let { listOf(0) + it } ?: VideoQuality.defaults).forEach { value -> DropdownMenuItem(text = { Text(VideoQuality.label(value)) }, onClick = { quality = value; menu = false }) }
                        }
                    }
                    OutlinedButton(onClick = { model.inspect(text) }, enabled = !batch && text.isNotBlank() && !busy && !inspecting) {
                        Text(if (inspecting) "Checking…" else "Check post")
                    }
                }
                if (qualities != null && !batch) {
                    Text(qualities!!.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                    if (qualities!!.items.size > 1) {
                        Text("Choose videos · ${selected.size} selected", style = MaterialTheme.typography.labelLarge)
                        Row { TextButton(onClick = { selected = qualities!!.items.map { it.index }.toSet() }) { Text("Select all") }; TextButton(onClick = { selected = emptySet() }) { Text("Clear") } }
                        qualities!!.items.forEach { item -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = item.index in selected, onCheckedChange = { selected = if (it) selected + item.index else selected - item.index })
                            Text("${item.index}. ${item.title}", maxLines = 2, style = MaterialTheme.typography.bodySmall)
                        } }
                    }
                }
                Text("TikTok · X · Instagram · YouTube · Facebook · Reddit\nAdditional platforms are beta. Public, accessible posts only.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            val active = jobs.count { it.status in listOf(JobStatus.QUEUED, JobStatus.DOWNLOADING, JobStatus.SAVING, JobStatus.RETRYING) }
            Card(onClick = library, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Your library", style = MaterialTheme.typography.titleMedium); Text("${jobs.count { it.status == JobStatus.SAVED }} saved · $active active", style = MaterialTheme.typography.bodySmall) }
                    Icon(SnapIcons.ArrowForward, "Open library")
                }
            }
        }
        item {
            TextButton(onClick = { help = !help }) { Text(if (help) "Hide guide" else "How sharing works") }
            if (settings.reduceMotion) { if (help) Text("Share a link to SnapTok, or enable the floating Save button in Settings. Saved defaults keep sharing quick.") }
            else AnimatedVisibility(help) { Text("Share a link to SnapTok, or enable the floating Save button in Settings. Saved defaults keep sharing quick.", style = MaterialTheme.typography.bodyMedium) }
        }
    }
    Surface(tonalElevation = 2.dp) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 6.dp)) {
            TextButton(onClick = folder, modifier = Modifier.fillMaxWidth()) { Icon(SnapIcons.FolderOpen, null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text(folderLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) }
                Button(onClick = { model.enqueueSelection(text, quality, selected, audio, batch) }, enabled = text.isNotBlank() && !busy && selected.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(18.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(SnapIcons.Download, null)
                    Spacer(Modifier.width(10.dp)); Text(if (busy) "Adding to queue…" else if (audio) "Download audio" else "Download video")
                }
        }
    }
    }

}
