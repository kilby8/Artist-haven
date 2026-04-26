package com.artisthaven.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DeepDarkColorScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = OLEDBlack,
    primaryContainer = AccentBlueVariant,
    onPrimaryContainer = TextPrimary,
    secondary = AccentAmber,
    onSecondary = OLEDBlack,
    background = OLEDBlack,
    onBackground = TextPrimary,
    surface = SurfaceBlack,
    onSurface = TextPrimary,
    surfaceVariant = CardBlack,
    onSurfaceVariant = TextSecondary,
    outline = BorderGray,
    outlineVariant = DividerGray,
    error = AccentRed,
    onError = OLEDBlack,
    scrim = Color(0xCC000000)
)

@Composable
fun ArtistHavenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DeepDarkColorScheme,
        typography = ArtistHavenTypography,
        content = content
    )
}
