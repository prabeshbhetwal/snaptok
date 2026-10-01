package app.snaptok.ui

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.snaptok.core.matchesLibrarySearch
import app.snaptok.core.videoPlatform
import app.snaptok.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(model: MainViewModel, jobs: List<DownloadJob>, settings: DownloadSettings) {
    var search by rememberSaveable { mutableStateOf("") }
    var filtersVisible by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf("All") }
    var collection by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("Newest") }
    var collectionsMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf<DownloadJob?>(null) }
    var delete by remember { mutableStateOf<DownloadJob?>(null) }
    var organise by remember { mutableStateOf<DownloadJob?>(null) }
    var edit by remember { mutableStateOf<DownloadJob?>(null) }
    var details by remember { mutableStateOf<DownloadJob?>(null) }
    val exporting by model.exporting.collectAsStateWithLifecycle()
    val filtered = jobs.filter { job ->
        (collection.isEmpty() || job.collection == collection) && matchesLibrarySearch("${job.title} ${job.creator} ${job.caption}", job.url, search) && when (filter) {
            "Saved" -> job.status == JobStatus.SAVED
            "Active" -> job.status in listOf(JobStatus.QUEUED, JobStatus.DOWNLOADING, JobStatus.SAVING, JobStatus.RETRYING)
            "Favourites" -> job.favourite
            "Failed" -> job.status == JobStatus.FAILED
            else -> true
        }
    }.let { list -> when (sort) { "Oldest" -> list.sortedBy { it.createdAt }; "Title" -> list.sortedBy { it.title.lowercase() }; else -> list } }
    remove?.let { job -> ConfirmDialog("Remove from history?", "The saved file stays in its folder.", "Remove entry", { remove = null }) { model.removeHistory(job.id); remove = null } }
    delete?.let { job -> ConfirmDialog("Delete saved file?", "This deletes the file from its folder and removes history entries linked to that file. It cannot be undone here.", "Delete file", { delete = null }) { model.deleteFile(job); delete = null } }
    organise?.let { job ->
        var name by remember(job.id) { mutableStateOf(job.collection) }
        AlertDialog(onDismissRequest = { organise = null }, title = { Text("Add to collection") }, text = {
            Column { OutlinedTextField(name, { name = it.take(60) }, modifier = Modifier.semanticsLabel("Collection name"), label = { Text("Collection name") }); Text("Leave blank to remove from a collection.", style = MaterialTheme.typography.bodySmall)
                jobs.map { it.collection }.filter { it.isNotBlank() }.distinct().take(8).forEach { TextButton(onClick = { name = it }) { Text(it) } }
            }
        }, confirmButton = { TextButton(onClick = { model.collection(job, name); organise = null }) { Text("Save collection") } }, dismissButton = { TextButton(onClick = { organise = null }) { Text("Cancel") } })
    }
    edit?.let { job -> ExportDialog(job, { edit = null }) { start, end, audio -> model.exportMedia(job, start, end, audio); edit = null } }
    details?.let { job -> AlertDialog(onDismissRequest = { details = null }, title = { Text(job.creator.ifBlank { videoPlatform(job.url) }) }, text = {
        LazyColumn { item { Text(job.caption.ifBlank { "No caption was provided by the source." }) } }
    }, confirmButton = { TextButton(onClick = { details = null }) { Text("Done") } }) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeading("YOUR LIBRARY", "Keep the good ones.", "${jobs.count { it.status == JobStatus.SAVED }} saved · ${jobs.count { it.favourite }} favourites") }
        item { OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("Search downloads") }, leadingIcon = { Icon(SnapIcons.Search, null) }, trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(SnapIcons.Close, "Clear search") } }, singleLine = true, shape = RoundedCornerShape(18.dp)) }
        item {
            Column {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box { TextButton(onClick = { collectionsMenu = true }) { Text(collection.ifBlank { "All collections" }); Icon(SnapIcons.ArrowDropDown, null) }
                        DropdownMenu(collectionsMenu, { collectionsMenu = false }) { (listOf("") + jobs.map { it.collection }.filter { it.isNotBlank() }.distinct()).forEach { value -> DropdownMenuItem(text = { Text(value.ifBlank { "All collections" }) }, onClick = { collection = value; collectionsMenu = false }) } }
                    }
                    Box { TextButton(onClick = { sortMenu = true }) { Text(sort); Icon(SnapIcons.ArrowDropDown, null) }; DropdownMenu(sortMenu, { sortMenu = false }) { listOf("Newest", "Oldest", "Title").forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { sort = value; sortMenu = false }) } } }
                    IconButton(onClick = { filtersVisible = !filtersVisible }) { Icon(SnapIcons.Tune, if (filtersVisible) "Hide library filters" else "Show library filters", tint = if (filter != "All") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = { model.changeSettings { it.copy(grid = !it.grid) } }) { Icon(if (settings.grid) SnapIcons.ViewList else SnapIcons.GridView, if (settings.grid) "Use list view" else "Use grid view") }
                }
                // Two rows keep controls usable with large text and narrow screens.
                if (filtersVisible) listOf(listOf("All", "Saved", "Active"), listOf("Favourites", "Failed")).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { value -> FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(value) }) } } }
            }
        }
        if (exporting) item { Card { Column(Modifier.padding(16.dp)) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Exporting a new file. Keep SnapTok open.", Modifier.padding(top = 8.dp)) } } }
        if (filtered.isEmpty()) item { Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(SnapIcons.VideoLibrary, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline); Spacer(Modifier.height(16.dp)); Text("Nothing here yet", style = MaterialTheme.typography.titleMedium); Text("Try a different filter or save your first video.", style = MaterialTheme.typography.bodySmall) } }
        items(filtered.chunked(if (settings.grid) 2 else 1), key = { row -> row.joinToString { it.id } }) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { job ->
                    LibraryCard(job, settings, Modifier.weight(1f), { model.openJob(job.id) }, { model.favourite(job) }, { organise = job }, { remove = job }, { delete = job }, { edit = job }, { details = job }, { model.retry(job.id) }, { model.cancel(job.id) }, { model.retryInCurrentFolder(job) })
                }
                if (settings.grid && row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LibraryCard(job: DownloadJob, settings: DownloadSettings, modifier: Modifier, play: () -> Unit, favourite: () -> Unit, organise: () -> Unit, remove: () -> Unit, delete: () -> Unit, edit: () -> Unit, details: () -> Unit, retry: () -> Unit, cancel: () -> Unit, retryCurrent: () -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(job.progress / 100f, tween(if (settings.reduceMotion) 0 else 350), label = "Download progress")
    Card(modifier.then(if (settings.reduceMotion) Modifier else Modifier.animateContentSize()), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(if (settings.grid) 12.dp else 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(videoPlatform(job.url), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                IconButton(onClick = favourite, modifier = Modifier.size(48.dp)) { Icon(if (job.favourite) SnapIcons.Favorite else SnapIcons.FavoriteBorder, if (job.favourite) "Remove favourite · ${job.title}" else "Favourite · ${job.title}", tint = if (job.favourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (job.status == JobStatus.SAVED) job.mediaUri?.let { uri ->
                if (settings.grid) MediaTile(uri, job.audioOnly) else SavedVideoDetails(uri)
            }
            Text(job.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (job.creator.isNotBlank()) Text(job.creator, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            if (job.collection.isNotBlank()) Text(job.collection, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(when {
                job.status == JobStatus.QUEUED && job.unmetered -> "Waiting for an unmetered connection / download slot"
                job.status == JobStatus.QUEUED && job.charging -> "Waiting for charging / download slot"
                job.status == JobStatus.DOWNLOADING && job.progress == 0 -> "Checking source / waiting for a download slot"
                job.status == JobStatus.DOWNLOADING && job.progress >= 100 -> "Combining and checking media…"
                else -> job.status
            }, style = MaterialTheme.typography.labelMedium, color = if (job.status == JobStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            if (job.status == JobStatus.DOWNLOADING) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            if (job.status == JobStatus.SAVING) LinearProgressIndicator(Modifier.fillMaxWidth())
            job.error?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (job.status == JobStatus.SAVED) FilledTonalButton(onClick = play, modifier = Modifier.weight(1f).semanticsLabel("Play ${job.title}")) { Icon(SnapIcons.PlayArrow, null); if (!settings.grid) Text("Play") }
                else if (job.status in listOf(JobStatus.FAILED, JobStatus.CANCELLED)) TextButton(onClick = retry, modifier = Modifier.weight(1f)) { Text("Retry") }
                else TextButton(onClick = cancel, enabled = job.status != JobStatus.SAVING, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(SnapIcons.MoreVert, "Video actions · ${job.title}") }
                    DropdownMenu(menu, { menu = false }) {
                        fun close(action: () -> Unit) { menu = false; action() }
                        DropdownMenuItem(text = { Text("Open original post") }, onClick = { close { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, job.url.toUri())) } } })
                        DropdownMenuItem(text = { Text("Collection…") }, onClick = { close(organise) })
                        DropdownMenuItem(text = { Text("Caption & creator") }, onClick = { close(details) })
                        if (job.status in listOf(JobStatus.FAILED, JobStatus.CANCELLED)) DropdownMenuItem(text = { Text("Retry with current defaults") }, onClick = { close(retryCurrent) })
                        if (job.status == JobStatus.SAVED) {
                            DropdownMenuItem(text = { Text("Share saved file") }, onClick = { close { shareSaved(context, job) } })
                            DropdownMenuItem(text = { Text("Trim / export audio") }, onClick = { close(edit) })
                            DropdownMenuItem(text = { Text("Delete file…") }, onClick = { close(delete) })
                        }
                        if (job.status in listOf(JobStatus.SAVED, JobStatus.FAILED, JobStatus.CANCELLED)) DropdownMenuItem(text = { Text("Remove history…") }, onClick = { close(remove) })
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaTile(uri: String, audio: Boolean) {
    val context = LocalContext.current
    val media by produceState<SavedMediaDetails?>(null, uri) { value = withContext(Dispatchers.IO) { runCatching { readSavedMedia(context, uri.toUri()) }.getOrNull() } }
    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        media?.thumbnail?.let { Image(it.asImageBitmap(), "Saved media thumbnail", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: Icon(if (audio) SnapIcons.MusicNote else SnapIcons.VideoFile, null, Modifier.size(40.dp))
    }
}

@Composable
fun ConfirmDialog(title: String, body: String, action: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(body) }, confirmButton = { TextButton(onClick = confirm) { Text(action) } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
private fun ExportDialog(job: DownloadJob, dismiss: () -> Unit, export: (Long, Long?, Boolean) -> Unit) {
    var start by remember { mutableStateOf("0") }; var end by remember { mutableStateOf("") }; var audio by remember { mutableStateOf(job.audioOnly) }
    val valid = start.toLongOrNull()?.let { it >= 0 && it <= 86400 && (end.isBlank() || end.toLongOrNull()?.let { finish -> finish > it && finish <= 86400 } == true) } == true
    AlertDialog(onDismissRequest = dismiss, title = { Text("Create a new copy") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("The original stays unchanged. Times are in seconds. Leave End blank to use the rest of the file.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(start, { start = it }, label = { Text("Start (seconds)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(end, { end = it }, label = { Text("End (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Audio only", Modifier.weight(1f)); Switch(audio, { audio = it }, enabled = !job.audioOnly) }
    } }, confirmButton = { TextButton(enabled = valid, onClick = { export(start.toLong(), end.toLongOrNull(), audio) }) { Text("Export copy") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
