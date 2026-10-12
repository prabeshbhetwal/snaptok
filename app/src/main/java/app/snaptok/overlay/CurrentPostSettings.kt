package app.snaptok.overlay

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun CurrentPostSettings() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(FloatingSaveService.PREFS, Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(CurrentPostRules.MODE, false)) }
    var disclosure by remember { mutableStateOf(false) }
    val connected by CurrentPostAccessibilityService.connected.collectAsStateWithLifecycle()
    fun openSettings() {
        if (runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }.isFailure) {
            Toast.makeText(context, "Open Android Settings → Accessibility → Installed apps → SnapTok — Save current post.", Toast.LENGTH_LONG).show()
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Save current post · TikTok", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Switch(checked = enabled, onCheckedChange = { value ->
            if (value) disclosure = true
            else {
                enabled = false
                prefs.edit().putBoolean(CurrentPostRules.MODE, false).apply()
                if (CurrentPostCapture.busy.value) CurrentPostAccessibilityService.stopCapture()
            }
        }, modifier = Modifier.semantics { contentDescription = "Save current TikTok post" })
    }
    Text(if (enabled) "Experimental · English TikTok. Tap the floating button while viewing a post and keep it on screen until capture finishes. Share may appear briefly. Other apps use Share → SnapTok."
        else "Copy a post link, then tap the floating button. Turn on Save current post to automate TikTok’s Share and Copy link controls.", style = MaterialTheme.typography.bodySmall)
    if (enabled) {
        Text(if (connected) "Accessibility service connected" else "Setup required: enable SnapTok — Save current post in Android Accessibility settings.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = ::openSettings) { Text("Android Accessibility settings") }
        Text("If Android shows Restricted settings, review the permission in SnapTok’s App info. You can keep using Save copied link without enabling this service.", style = MaterialTheme.typography.bodySmall)
    }
    if (disclosure) AlertDialog(
        onDismissRequest = { disclosure = false },
        title = { Text("Allow TikTok capture?") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("After you tap the floating button, SnapTok uses Android Accessibility to read TikTok’s visible interface controls and activate Share and Copy link on your behalf.")
            Text("It then briefly opens to read the newly copied link and download that post using your saved preferences. TikTok’s sharing panel may appear during capture.")
            Text("Interface data is processed on your phone and is not stored or sent elsewhere. Only the captured post link enters the existing downloader, which contacts TikTok and its media hosts. No screenshots, message collection or continuous clipboard monitoring. You can switch this mode off here and revoke the service in Android settings.")
        } },
        confirmButton = { TextButton(onClick = {
            prefs.edit().putBoolean(CurrentPostRules.CONSENT, true).putBoolean(CurrentPostRules.MODE, true).apply()
            enabled = true; disclosure = false; openSettings()
        }) { Text("Agree and open settings") } },
        dismissButton = { TextButton(onClick = { disclosure = false }) { Text("Not now") } }
    )
}
