package com.mantismoonlabs.fujinetgoadam.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FujiOrange = Color(0xFFE8743B)
private val FujiDark = Color(0xFF101418)
private val FujiPanel = Color(0xFF1B2026)

private val DarkColors = darkColorScheme(
    primary = FujiOrange,
    onPrimary = Color.Black,
    background = FujiDark,
    surface = FujiPanel,
    onSurface = Color(0xFFE6E6E6),
)

private val LightColors = lightColorScheme(
    primary = FujiOrange,
    onPrimary = Color.White,
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
