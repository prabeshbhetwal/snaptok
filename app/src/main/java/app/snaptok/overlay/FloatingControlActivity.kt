package app.snaptok.overlay

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.snaptok.ui.MainActivity
import app.snaptok.ui.SnapTokTheme

class FloatingControlActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hideIntent = Intent(this, FloatingSaveService::class.java)
        setContent { SnapTokTheme((application as app.snaptok.SnapTokApplication).container.preferences.snapshot().theme) { Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Card { Column(Modifier.padding(24.dp)) {
            Text("SnapTok", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { startActivity(Intent(this@FloatingControlActivity, SaveClipboardActivity::class.java)); finish() }) { Text("Save copied link") }
            TextButton(onClick = { startActivity(Intent(this@FloatingControlActivity, MainActivity::class.java).putExtra("showLibrary", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)); finish() }) { Text("Open library") }
            TextButton(onClick = { stopService(hideIntent); finish() }) { Text("Hide floating button") }
            TextButton(onClick = { finish() }) { Text("Back") }
        } } } } }
    }
}
