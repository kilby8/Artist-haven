package com.artisthaven.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Quiet dark chrome so the artwork is the brightest thing on screen. */
object Haven {
    val background = Color(0xFF1E1E22)
    val bar = Color(0xF0141417)
    val panel = Color(0xF7232328)
    val panelRaised = Color(0xFF2E2E35)
    val divider = Color(0x22FFFFFF)
    val text = Color(0xFFEDEDF0)
    val textDim = Color(0xFF9A9AA3)
    val accent = Color(0xFF6FA8FF)
    val accentSoft = Color(0x336FA8FF)
    val danger = Color(0xFFFF6B6B)
}

private val scheme = darkColorScheme(
    primary = Haven.accent,
    onPrimary = Color(0xFF0B1B33),
    secondary = Color(0xFFB9A7F0),
    background = Haven.background,
    onBackground = Haven.text,
    surface = Color(0xFF232328),
    onSurface = Haven.text,
    surfaceVariant = Haven.panelRaised,
    onSurfaceVariant = Haven.textDim,
    surfaceContainer = Color(0xFF26262C),
    surfaceContainerHigh = Color(0xFF2C2C33),
    surfaceContainerHighest = Color(0xFF33333B),
    outline = Color(0xFF55555F),
    error = Haven.danger,
)

@Composable
fun ArtistHavenTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
