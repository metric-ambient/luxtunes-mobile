package io.github.metricambient.luxtunes.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** The app's colors: black background, light grey text, blue accent. */
object Lux {
    val accent = Color(0xFF0099CC)
    val background = Color.Black
    val bar = Color(0xFF1E1E1E)          // bottom bars and menus
    val panel = Color(0xFF2B2B2B)
    val text = Color.White
    val dim = Color(0xFFB0B0B0)          // second lines
    val icon = Color(0xFFBDBDBD)
    val shade = Color.Black.copy(alpha = 0.7f)
}

@Composable
fun LuxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Lux.accent,
            onPrimary = Color.White,
            secondary = Lux.accent,
            background = Lux.background,
            onBackground = Lux.text,
            surface = Lux.bar,
            onSurface = Lux.text,
            surfaceVariant = Lux.panel,
            onSurfaceVariant = Lux.dim,
            surfaceContainer = Lux.panel,
        ),
        content = content,
    )
}
