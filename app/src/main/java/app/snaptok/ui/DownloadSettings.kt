package app.snaptok.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import app.snaptok.core.VideoQuality
import app.snaptok.data.DownloadSettings

fun LazyListScope.settingsItems(model: MainViewModel, settings: DownloadSettings, updating: Boolean, engine: String, chooseFolder: () -> Unit, systemNotifications: () -> Unit) {
    item {
        Text("Make it yours.", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("Your defaults keep sharing quick.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    item {
        SettingCard("Save location") {
            Text(settings.options.destinationLabel)
            Text("Applies to new downloads. Existing jobs keep their chosen folder.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = chooseFolder) { Text("Choose folder") }
            if (settings.options.destinationTreeUri != null) TextButton(onClick = { model.resetFolder() }) { Text("Use default gallery folder") }
            Text("Choose a subfolder in Movies or Downloads. Other providers may show files only in Files, not Gallery.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    item {
        SettingCard("Default video quality") {
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { expanded = true }) { Text(VideoQuality.label(settings.options.qualityHeight)) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    VideoQuality.defaults.forEach { height -> DropdownMenuItem(text = { Text(VideoQuality.label(height)) }, onClick = { model.setDefaultQuality(height); expanded = false }) }
                }
            }
            Text("Shared links use this preference automatically. We choose the best available quality at or below it, or the lowest available if none fits. Videos are never upscaled.", style = MaterialTheme.typography.bodySmall)
        }
    }
    item {
        SettingCard("Download notifications") {
            PreferenceSwitch("Notify me", "Completion and failure alerts, including brief status messages.", settings.notificationsEnabled) { enabled -> model.changeSettings { it.copy(notificationsEnabled = enabled) } }
            if (settings.notificationsEnabled) {
                PreferenceSwitch("Progress bar", "Show transfer percentage while downloading.", settings.showProgress) { enabled -> model.changeSettings { it.copy(showProgress = enabled) } }
                PreferenceSwitch("Download completed", "Tap the notification to play the saved video.", settings.showCompletion) { enabled -> model.changeSettings { it.copy(showCompletion = enabled) } }
                PreferenceSwitch("Download failed", "Tell me when retries are exhausted or a video cannot be saved.", settings.showErrors) { enabled -> model.changeSettings { it.copy(showErrors = enabled) } }
            }
            Text("Android can still display a minimal ongoing-service indicator while a download runs. System notification permissions also apply.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = systemNotifications) { Text("Android notification settings") }
        }
    }
    item {
        SettingCard("Download engine") {
            Text(engine, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Videos download directly on your phone using yt-dlp. SnapTik's website is not involved. Update if a source changes and downloads stop working.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { model.updateEngine() }, enabled = !updating) { Text(if (updating) "Waiting / updating…" else "Update engine") }
            if (updating) Text("Keep SnapTok open. Any current download finishes first.", style = MaterialTheme.typography.bodySmall)
        }
    }
    item { SettingCard("Privacy & supported links") {
        Text("Public TikTok and X/Twitter video posts. No account, adverts, analytics or automatic clipboard reading. Paste reads the clipboard only when you tap it. Videos download from their source; engine updates contact GitHub.", style = MaterialTheme.typography.bodyMedium)
        Text("Private posts, login-required content, slideshows and live streams are not supported. For X posts with multiple videos, share the specific /video/2 link or the first video is saved.", style = MaterialTheme.typography.bodySmall)
    } }
    item { Text("SnapTok 0.2.0 · Personal preview", style = MaterialTheme.typography.labelMedium) }
}

@Composable
fun SettingCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun PreferenceSwitch(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.semantics { contentDescription = title })
    }
}
