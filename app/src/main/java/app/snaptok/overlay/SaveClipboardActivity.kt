package app.snaptok.overlay

import android.content.ClipboardManager
import android.os.Bundle
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

/** Reads once, after focus, only in response to tapping our overlay. */
class SaveClipboardActivity : ComponentActivity() {
    private var handled = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) { finish(); return }
        setContent { SnapTokTheme((application as app.snaptok.SnapTokApplication).container.preferences.snapshot().theme) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Card { Text("Saving copied link…", Modifier.padding(20.dp)) }
            }
        } }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || handled || isFinishing) return
        handled = true
        // Text only: do not dereference arbitrary clipboard content URIs.
        val text = runCatching { getSystemService(ClipboardManager::class.java).primaryClip?.let {
            if (it.itemCount > 0) it.getItemAt(0).text?.toString() else null
        } }.getOrNull()
        if (text.isNullOrBlank()) {
            Toast.makeText(this, "Copy a supported video link first.", Toast.LENGTH_LONG).show()
            finish(); return
        }
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
}
