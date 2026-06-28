package online.fujinet.go.adam.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Accent derived from the ADAM launcher icon's light periwinkle background
// (#8D84E5, matching the original Play Store icon), over dark indigo surfaces.
// The accent is light, so content drawn on it (onPrimary) is black.
private val FujiPeriwinkle = Color(0xFF8D84E5)
private val FujiDark = Color(0xFF0D0B1A)
private val FujiPanel = Color(0xFF1A1730)

private val DarkColors = darkColorScheme(
    primary = FujiPeriwinkle,
    onPrimary = Color.Black,
    background = FujiDark,
    surface = FujiPanel,
    onSurface = Color(0xFFE8E6F2),
)

private val LightColors = lightColorScheme(
    primary = FujiPeriwinkle,
    onPrimary = Color.Black,
)

@Composable
fun FujiNetGoAdamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
