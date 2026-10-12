package app.snaptok.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.snaptok.data.DownloadSettings

@Composable
fun SettingsScreen(model: MainViewModel, settings: DownloadSettings, folder: () -> Unit, notifications: () -> Unit) {
    val updating by model.updating.collectAsStateWithLifecycle()
    val engine by model.engineVersion.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        settingsItems(model, settings, updating, engine, folder, notifications)
    }
}

fun LazyListScope.advancedSettingsItems(model: MainViewModel, settings: DownloadSettings) {
    item { SettingCard("Photo posts") { PhotoOptions(settings.options) { value -> model.changeSettings { it.copy(options = value) } } } }
    item { SettingCard("Appearance") {
        ChoiceRow("Theme", listOf("system", "light", "dark"), settings.theme) { value -> model.changeSettings { it.copy(theme = value) } }
        SettingsToggle("Reduce motion", "Use simple page changes and static progress updates.", settings.reduceMotion) { enabled -> model.changeSettings { it.copy(reduceMotion = enabled) } }
        SettingsToggle("Grid library", "Display saved videos in two columns.", settings.grid) { enabled -> model.changeSettings { it.copy(grid = enabled) } }
    } }
    item { SettingCard("Download preferences") {
        SettingsToggle("Unmetered connections only", "Wait for an unmetered connection, usually Wi-Fi. Metered Wi-Fi is excluded.", settings.options.unmetered) { enabled -> model.changeSettings { it.copy(options = it.options.copy(unmetered = enabled)) } }
        SettingsToggle("Only while charging", "Queue now and wait until the phone is charging.", settings.options.charging) { enabled -> model.changeSettings { it.copy(options = it.options.copy(charging = enabled)) } }
        var expanded by remember { mutableStateOf(false) }
        Text("Maximum file size", style = MaterialTheme.typography.titleSmall)
        Box { OutlinedButton(onClick = { expanded = true }) { Text("${settings.options.maxSizeMb} MB") }
            DropdownMenu(expanded, { expanded = false }) { listOf(50, 100, 250, 500, 1024, 2048, 4096).forEach { mb -> DropdownMenuItem(text = { Text("$mb MB") }, onClick = { model.changeSettings { it.copy(options = it.options.copy(maxSizeMb = mb)) }; expanded = false }) } }
        }
        Text("Changes apply to new requests. The size limit is checked before transfer where reported, and against the finished file. Download components can use additional temporary space.", style = MaterialTheme.typography.bodySmall)
    } }
    item { StorageSettings(model) }
    item { SettingCard("Defaults by platform") {
        Text("First choose the quality and folder below, then save that combination for a platform. Share and floating-button downloads use it automatically.", style = MaterialTheme.typography.bodySmall)
        listOf("TikTok", "X", "Instagram", "YouTube", "Facebook", "Reddit").forEach { platform ->
            var enabled by remember(platform) { mutableStateOf(model.platformOverride(platform)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(platform, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { model.savePlatform(platform, true); enabled = true }) { Text(if (enabled) "Replace" else "Save current") }
                if (enabled) TextButton(onClick = { model.savePlatform(platform, false); enabled = false }) { Text("Reset") }
            }
        }
    } }
}

@Composable
private fun StorageSettings(model: MainViewModel) {
    val value by model.storage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { model.refreshStorage() }
    if (confirm) ConfirmDialog("Clear temporary files?", "Removes partial files belonging to finished or cancelled jobs. Active downloads and saved media are kept. A future retry may download those parts again.", "Clear temporary files", { confirm = false }) { confirm = false; model.cleanCache() }
    SettingCard("Storage") {
        value?.let {
            Text("Saved media · ${Formatter.formatFileSize(context, it.savedBytes)}")
            Text("Temporary files · ${Formatter.formatFileSize(context, it.temporaryBytes)}")
            Text("Phone storage available · ${Formatter.formatFileSize(context, it.freeBytes)}", style = MaterialTheme.typography.bodySmall)
        } ?: LinearProgressIndicator(Modifier.fillMaxWidth())
        Row { OutlinedButton(onClick = { confirm = true }) { Text("Clean up") }; TextButton(onClick = { model.refreshStorage() }) { Text("Refresh") } }
        Text("Saved size includes accessible files still in your history. Use each video's menu to delete a saved file.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun SettingsToggle(title: String, detail: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked, change, modifier = Modifier.semanticsLabel(title))
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceRow(title: String, choices: List<String>, selected: String, change: (String) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { choices.forEach { value -> FilterChip(selected == value, { change(value) }, label = { Text(value.replaceFirstChar { it.uppercase() }) }) } }
}
