package app.snaptok.overlay

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.snaptok.R
import app.snaptok.ui.SettingCard
import kotlin.math.roundToInt

@Composable
fun FloatingSaveSettings() {
    val context = LocalContext.current
    val serviceIntent = remember { Intent(context, FloatingSaveService::class.java) }
    val prefs = remember { context.getSharedPreferences(FloatingSaveService.PREFS, Context.MODE_PRIVATE) }
    val active by FloatingSaveService.active.collectAsStateWithLifecycle()
    var opacity by remember { mutableFloatStateOf(FloatingSaveService.opacity(prefs.getFloat("opacity", .45f))) }
    var size by remember { mutableFloatStateOf(FloatingSaveService.buttonSize(prefs.getInt("size", 56)).toFloat()) }
    fun start() {
        try { ContextCompat.startForegroundService(context, Intent(context, FloatingSaveService::class.java)) }
        catch (_: Exception) { Toast.makeText(context, "Could not start the floating button. Please try again.", Toast.LENGTH_LONG).show() }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(context)) start()
        else Toast.makeText(context, "Allow Appear on top to use the floating Save button.", Toast.LENGTH_LONG).show()
    }
    SettingCard("Floating Save button") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (active) "Button is on" else "Button is off", Modifier.weight(1f))
            Switch(modifier = Modifier.semantics { contentDescription = "Floating Save button" }, checked = active, onCheckedChange = { enabled ->
                if (!enabled) context.stopService(serviceIntent)
                else if (Settings.canDrawOverlays(context)) start()
                else try { permission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())) }
                catch (_: Exception) { Toast.makeText(context, "Open Android Settings and allow SnapTok to appear on top.", Toast.LENGTH_LONG).show() }
            })
        }
        Text("Copy a video link, then tap the floating button. SnapTok briefly opens to read the link and returns you to your previous app.", style = MaterialTheme.typography.bodySmall)
        Box(Modifier.fillMaxWidth().height(88.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(size.dp).alpha(opacity).background(Color(0xFF21302B), CircleShape).border(1.dp, MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_download), "Floating Save preview", Modifier.size((size / 2).dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Text("Button size · ${size.roundToInt()} dp", style = MaterialTheme.typography.labelLarge)
        Slider(modifier = Modifier.semantics { contentDescription = "Floating button size" }, value = size, onValueChange = { size = it }, valueRange = 48f..80f, steps = 7,
            onValueChangeFinished = { prefs.edit().putInt("size", size.roundToInt()).apply() })
        Text("Visibility · ${(opacity * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
        Slider(modifier = Modifier.semantics { contentDescription = "Floating button visibility" }, value = opacity, onValueChange = { opacity = it }, valueRange = .25f..1f,
            onValueChangeFinished = { prefs.edit().putFloat("opacity", opacity).apply() })
        Text("Drag to move it; release to dock at the nearest edge. Hold the button to hide it. You can also hide it from its notification or this switch.", style = MaterialTheme.typography.bodySmall)
        listOf("fade" to "Fade when idle", "haptic" to "Vibrate on tap", "holdMenu" to "Hold to show an action menu", "restoreNotice" to "Show a restore notification when hidden").forEach { (key, label) ->
            var enabled by remember(key) { mutableStateOf(prefs.getBoolean(key, key == "fade" || key == "restoreNotice")) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Switch(enabled, { enabled = it; prefs.edit().putBoolean(key, it).apply() }, modifier = Modifier.semantics { contentDescription = label })
            }
        }
        Text("The ring shows progress for one active download; the badge counts all active requests. A check mark means saved; an exclamation mark means attention is needed.", style = MaterialTheme.typography.bodySmall)
        Text("Requires Appear on top permission. Android keeps an ongoing service notification while it is on. Hidden on the lock screen. If Android stops the service or the phone restarts, enable it here again.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
