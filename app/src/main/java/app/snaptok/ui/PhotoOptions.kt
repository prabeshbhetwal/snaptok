package app.snaptok.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.snaptok.data.DownloadOptions
import kotlin.math.roundToInt

@Composable
fun PhotoOptions(options: DownloadOptions, change: (DownloadOptions) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceRow("TikTok photo posts", listOf("images", "slideshow", "both"), options.photoMode) { change(options.copy(photoMode = it)) }
        Text("Saved as your default for shared links and the floating button. Regular videos are unchanged.", style = MaterialTheme.typography.bodySmall)
        if (options.photoMode != "images") {
            Text("${options.slideSeconds} seconds per image", style = MaterialTheme.typography.labelLarge)
            Slider(options.slideSeconds.toFloat(), { change(options.copy(slideSeconds = it.roundToInt().coerceIn(2, 10))) }, valueRange = 2f..10f, steps = 7,
                modifier = Modifier.semanticsLabel("Seconds per image"))
            SettingsToggle("Include music", "Uses the post’s available sound and repeats it to fit the slideshow. If unavailable, we ask you to retry without music.", options.slideMusic) { change(options.copy(slideMusic = it)) }
            Text("Images keep their original order and fit without cropping. The slideshow uses your chosen timing; swiping has no fixed original timeline.", style = MaterialTheme.typography.bodySmall)
        } else Text("Original images are saved individually. Image files do not include music.", style = MaterialTheme.typography.bodySmall)
    }
}
