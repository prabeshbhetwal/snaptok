package app.snaptok.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.activity.compose.LocalActivity
import androidx.core.view.WindowCompat
import androidx.compose.foundation.isSystemInDarkTheme
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
private val lightColours = lightColorScheme(
    primary = Color(0xFF226344), onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEED7), onPrimaryContainer = Color(0xFF10291B),
    secondaryContainer = Color(0xFFE5EDCB), onSecondaryContainer = Color(0xFF23301B),
    background = Color(0xFFF7F8F3), onBackground = Color(0xFF19221D),
    surface = Color(0xFFF7F8F3), onSurface = Color(0xFF19221D),
    surfaceContainer = Color(0xFFEEF1E9), surfaceContainerLow = Color(0xFFF2F5EF),
    surfaceContainerHigh = Color(0xFFE6ECE2), surfaceContainerHighest = Color(0xFFDDE5D9),
    onSurfaceVariant = Color(0xFF49584B), outline = Color(0xFF6C7D6C)
)
@Composable fun SnapTokTheme(mode: String = "system", content: @Composable () -> Unit) {
    val dark = mode == "dark" || (mode == "system" && isSystemInDarkTheme())
    val activity = LocalActivity.current
    SideEffect { activity?.window?.let { window -> WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
    } } }
    MaterialTheme(colorScheme = if (dark) colours else lightColours, content = content)
}
