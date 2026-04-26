package com.artisthaven.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artisthaven.app.ui.theme.AccentBlue
import com.artisthaven.app.ui.theme.AccentAmber
import com.artisthaven.app.ui.theme.GlassBackground
import com.artisthaven.app.ui.theme.GlassBorder
import com.artisthaven.app.ui.theme.TextSecondary

/**
 * Left floating sidebar with vertical sliders for Brush Size and Opacity.
 *
 * Uses a dual-layer glassmorphism approach:
 *  - A semi-transparent OLED-black background Box that doesn't blur content.
 *  - The actual content (sliders, labels) rendered on top, fully sharp.
 *
 * True backdrop-blur (CSS backdrop-filter equivalent) can be added via a
 * third-party BlurView or a custom RenderEffect layer without affecting
 * readability of the controls.
 */
@Composable
fun BrushSidebar(
    brushSize: Float,
    opacity: Float,
    onBrushSizeChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)

    // Outer Box provides the glass panel (background + border) without blurring content
    Box(
        modifier = modifier
            .width(52.dp)
            .clip(shape)
            .background(GlassBackground)
            .border(width = 0.5.dp, color = GlassBorder, shape = shape)
    ) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(52.dp)
            .padding(horizontal = 6.dp, vertical = 16.dp)
    ) {
        // ── Brush Size slider ────────────────────────────────────────────────
        Text(
            text = "B",
            fontSize = 10.sp,
            color = TextSecondary,
            lineHeight = 10.sp
        )
        Spacer(Modifier.height(4.dp))
        VerticalSlider(
            value = brushSize,
            onValueChange = onBrushSizeChange,
            trackColor = AccentBlue,
            height = 140.dp,
            width = 32.dp
        )

        Spacer(Modifier.height(24.dp))

        // ── Opacity slider ───────────────────────────────────────────────────
        Text(
            text = "O",
            fontSize = 10.sp,
            color = TextSecondary,
            lineHeight = 10.sp
        )
        Spacer(Modifier.height(4.dp))
        VerticalSlider(
            value = opacity,
            onValueChange = onOpacityChange,
            trackColor = AccentAmber,
            height = 140.dp,
            width = 32.dp
        )
    }
    }
}
