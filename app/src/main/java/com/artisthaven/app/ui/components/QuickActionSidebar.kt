package com.artisthaven.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.artisthaven.app.ui.theme.AccentBlue
import com.artisthaven.app.ui.theme.GlassBackground
import com.artisthaven.app.ui.theme.GlassBorder
import com.artisthaven.app.ui.theme.GlassTint
import com.artisthaven.app.ui.theme.TextDisabled
import com.artisthaven.app.ui.theme.TextSecondary

/**
 * Right floating "Quick Action" bar containing Undo, Redo, and Eye-Dropper buttons.
 *
 * Uses a dual-layer glassmorphism approach: the panel Box carries the glass background/border,
 * and the inner Column content stays fully sharp (no blur on controls or icons).
 */
@Composable
fun QuickActionSidebar(
    canUndo: Boolean,
    canRedo: Boolean,
    isEyeDropperActive: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onEyeDropper: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)

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
            .padding(horizontal = 6.dp, vertical = 12.dp)
    ) {
        QuickActionButton(
            icon = Icons.AutoMirrored.Filled.Undo,
            contentDescription = "Undo",
            enabled = canUndo,
            onClick = onUndo
        )

        Spacer(Modifier.height(8.dp))

        QuickActionButton(
            icon = Icons.AutoMirrored.Filled.Redo,
            contentDescription = "Redo",
            enabled = canRedo,
            onClick = onRedo
        )

        Spacer(Modifier.height(16.dp))

        // Eye-dropper — highlighted when active
        val eyeDropperBg = if (isEyeDropperActive) AccentBlue.copy(alpha = 0.25f) else GlassTint
        val eyeDropperTint = if (isEyeDropperActive) AccentBlue else TextSecondary

        IconButton(
            onClick = onEyeDropper,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(eyeDropperBg)
                .border(
                    width = if (isEyeDropperActive) 1.5.dp else 0.5.dp,
                    color = if (isEyeDropperActive) AccentBlue else GlassBorder,
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.Colorize,
                contentDescription = "Eye Dropper",
                tint = eyeDropperTint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (enabled) GlassTint else GlassBackground)
            .border(width = 0.5.dp, color = GlassBorder, shape = CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) TextSecondary else TextDisabled,
            modifier = Modifier.size(20.dp)
        )
    }
}
