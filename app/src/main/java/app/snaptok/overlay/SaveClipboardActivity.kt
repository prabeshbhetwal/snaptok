package app.snaptok.overlay

import android.content.ClipboardManager
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.snaptok.SnapTokApplication
import app.snaptok.ui.SnapTokTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.lifecycle.lifecycleScope

/** User-triggered foreground clipboard read; automatic capture additionally requires a fresh ticket. */
class SaveClipboardActivity : ComponentActivity() {
    private var handled = false
    private var ticket: CaptureTicketStore.Ticket? = null
    private var captureRequested = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) { finish(); return }
        captureRequested = intent.hasExtra(CurrentPostCapture.EXTRA_TOKEN)
        if (captureRequested) {
            ticket = CurrentPostCapture.tickets.take(intent.getStringExtra(CurrentPostCapture.EXTRA_TOKEN), SystemClock.elapsedRealtime())
            if (ticket == null) {
                Toast.makeText(this, "Capture expired. Tap the floating button again.", Toast.LENGTH_LONG).show()
                finish(); return
            }
        }
        setContent { SnapTokTheme((application as app.snaptok.SnapTokApplication).container.preferences.snapshot().theme) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Card { Text(if (captureRequested) "Capturing this post…" else "Saving copied link…", Modifier.padding(20.dp)) }
            }
        } }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || handled || isFinishing) return
        handled = true
        if (captureRequested) {
            lifecycleScope.launch {
                val capture = ticket ?: return@launch
                val deadline = minOf(capture.expiresAt, SystemClock.elapsedRealtime() + 1800)
                while (SystemClock.elapsedRealtime() < deadline) {
                    val prefs = getSharedPreferences(FloatingSaveService.PREFS, MODE_PRIVATE)
                    if (!hasWindowFocus() || getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked || !CurrentPostCapture.busy.value || !prefs.getBoolean(CurrentPostRules.MODE, false) || !prefs.getBoolean(CurrentPostRules.CONSENT, false)) break
                    val clip = runCatching { getSystemService(ClipboardManager::class.java).primaryClip }.getOrNull()
                    val text = if (clip?.itemCount == 1) clip.getItemAt(0).text?.toString() else null
                    val link = CurrentPostRules.freshLink(text, clip?.description?.timestamp ?: 0L, capture.clickedAt, System.currentTimeMillis())
                    if (link != null) { enqueue(link); return@launch }
                    delay(120)
                }
                val message = "No fresh TikTok link was captured. Use Share → SnapTok."
                FloatingFeedback.latest.value = SaveFeedback(message)
                Toast.makeText(this@SaveClipboardActivity, message, Toast.LENGTH_LONG).show()
                finish()
            }
            return
        }
        // Text only: do not dereference arbitrary clipboard content URIs.
        val text = runCatching { getSystemService(ClipboardManager::class.java).primaryClip?.let {
            if (it.itemCount > 0) it.getItemAt(0).text?.toString() else null
        } }.getOrNull()
        if (text.isNullOrBlank()) {
            Toast.makeText(this, "Copy a supported video link first.", Toast.LENGTH_LONG).show()
            finish(); return
        }
        enqueue(text)
    }
    private fun enqueue(text: String) {
        val app = application as SnapTokApplication
        // Durable enqueue must finish even if this brief activity loses focus or is dismissed.
        app.container.scope.launch {
            val result = try { app.container.queue.enqueue(text) }
            catch (e: Exception) { if (e is IllegalArgumentException) e.message.orEmpty() else "Could not queue the link. Open SnapTok and try again." }
            runOnUiThread {
                FloatingFeedback.latest.value = SaveFeedback(result)
                Toast.makeText(app, result, Toast.LENGTH_LONG).show()
                if (!isDestroyed) finish()
            }
        }
    }
    override fun onDestroy() {
        if (ticket != null) CurrentPostAccessibilityService.finishCapture()
        super.onDestroy()
    }
}
