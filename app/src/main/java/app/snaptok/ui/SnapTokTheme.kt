package app.snaptok.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val colours = darkColorScheme(
    primary = Color(0xFFB9F6CA), onPrimary = Color(0xFF10291B),
    secondaryContainer = Color(0xFF234333), onSecondaryContainer = Color(0xFFD5FADF),
    primaryContainer = Color(0xFF234333), onPrimaryContainer = Color(0xFFD5FADF),
    background = Color(0xFF101A18), onBackground = Color(0xFFF1F3E9),
    surface = Color(0xFF101A18), onSurface = Color(0xFFF1F3E9),
    surfaceVariant = Color(0xFF21302B), onSurfaceVariant = Color(0xFFBAC9BF),
    surfaceContainer = Color(0xFF1B2822), surfaceContainerHigh = Color(0xFF21302B),
    surfaceContainerHighest = Color(0xFF293A32), surfaceContainerLow = Color(0xFF17221E),
    outline = Color(0xFF6F8377), error = Color(0xFFFFB4A9)
)
@Composable fun SnapTokTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colours, content = content)
}
