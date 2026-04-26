package com.artisthaven.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artisthaven.app.model.DrawingTool
import com.artisthaven.app.ui.theme.BrushToolColor
import com.artisthaven.app.ui.theme.EraseToolColor
import com.artisthaven.app.ui.theme.GlassBackground
import com.artisthaven.app.ui.theme.GlassBorder
import com.artisthaven.app.ui.theme.GlassTint
import com.artisthaven.app.ui.theme.OLEDBlack
import com.artisthaven.app.ui.theme.SmudgeToolColor
import com.artisthaven.app.ui.theme.TextSecondary

/**
 * Minimal auto-hiding top toolbar containing:
 *  - Gallery button (left)
 *  - Tool selector — Brush / Smudge / Erase (centre)
 *  - Layer manager shortcut + circular Color Preview (right)
 *
 * Visibility is driven by [visible]; transitions are smooth slide + fade animations.
 */
@Composable
fun TopToolbar(
    visible: Boolean,
    currentTool: DrawingTool,
    currentColor: Color,
    onGalleryClick: () -> Unit,
    onToolSelected: (DrawingTool) -> Unit,
    onColorPreviewClick: () -> Unit,
    onLayersClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        val shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(GlassBackground)
                .border(width = 0.5.dp, color = GlassBorder, shape = shape)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // ── Gallery button ───────────────────────────────────────────────
            IconButton(
                onClick = onGalleryClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(GlassTint)
            ) {
                Icon(
                    imageVector = Icons.Default.CollectionsBookmark,
                    contentDescription = "Gallery",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // ── Tool selector ────────────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(OLEDBlack.copy(alpha = 0.4f))
                    .padding(4.dp)
            ) {
                DrawingTool.entries.forEach { tool ->
                    ToolChip(
                        label = tool.label,
                        selected = tool == currentTool,
                        color = toolColor(tool),
                        onClick = { onToolSelected(tool) }
                    )
                }
            }

            // ── Right side: Layers + Color preview ───────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onLayersClick,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(GlassTint)
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Layers",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Circular color preview — tap opens the color picker
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(currentColor)
                        .border(width = 2.dp, color = GlassBorder, shape = CircleShape)
                        .clickable(role = Role.Button, onClickLabel = "Pick colour") {
                            onColorPreviewClick()
                        }
                )
            }
        }
    }
}

@Composable
private fun ToolChip(
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    val bg = if (selected) color.copy(alpha = 0.25f) else Color.Transparent
    val textColor = if (selected) color else TextSecondary
    val borderColor = if (selected) color.copy(alpha = 0.6f) else Color.Transparent

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClickLabel = label) { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = textColor
        )
    }
}

private fun toolColor(tool: DrawingTool): Color = when (tool) {
    DrawingTool.BRUSH -> BrushToolColor
    DrawingTool.SMUDGE -> SmudgeToolColor
    DrawingTool.ERASE -> EraseToolColor
}
