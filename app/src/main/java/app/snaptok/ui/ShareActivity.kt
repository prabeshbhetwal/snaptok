package app.snaptok.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.snaptok.SnapTokApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val error = mutableStateOf<String?>(null)
        setContent {
            SnapTokTheme {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Card {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("SnapTok", style = MaterialTheme.typography.titleLarge)
                            Text(error.value ?: "Adding to your download queue…")
                            if (error.value != null) TextButton(onClick = { finish() }) { Text("Back to TikTok") }
                            else LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        lifecycleScope.launch {
            try {
                require(intent.action == Intent.ACTION_SEND && intent.type == "text/plain") { "Share a TikTok video link to SnapTok." }
                val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
                val message = (application as SnapTokApplication).container.queue.enqueue(text)
                Toast.makeText(this@ShareActivity, message, Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                error.value = if (e is IllegalArgumentException) e.message else "Could not schedule this download. Open SnapTok to recover the queue."
            }
        }
    }
}
