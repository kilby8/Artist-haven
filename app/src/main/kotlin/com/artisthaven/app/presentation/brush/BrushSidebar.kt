package com.artisthaven.app.presentation.brush

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artisthaven.app.domain.model.Brush
import com.artisthaven.app.domain.model.BrushType
import com.artisthaven.app.ui.components.ColorPickerDisc

/**
 * Sidebar for brush selection and configuration.
 * Displays available brush types and allows adjustment of brush properties.
 * Includes button to open full brush library.
 */
@Composable
fun BrushSidebar(
    activeBrush: Brush,
    selectedColor: Color,
    onBrushTypeSelected: (BrushType) -> Unit,
    onBrushSizeChanged: (Float) -> Unit,
    onBrushOpacityChanged: (Float) -> Unit,
    onBrushHardnessChanged: (Float) -> Unit,
    onColorSelected: (Color) -> Unit,
    onOpenBrushLibrary: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showColorPicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .width(72.dp)
            .fillMaxHeight()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Color picker circle
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(selectedColor)
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable { showColorPicker = true },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Brush library button
        IconButton(
            onClick = onOpenBrushLibrary,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
        ) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = "Open Brush Library",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Default brush types
        BrushType.entries.forEach { brushType ->
            BrushTypeButton(
                brushType = brushType,
                isSelected = activeBrush.type == brushType,
                onClick = { onBrushTypeSelected(brushType) },
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Size slider
        Text(
            text = "Size",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
        )
        VerticalSlider(
            value = activeBrush.size,
            onValueChange = onBrushSizeChanged,
            valueRange = 1f..100f,
            modifier = Modifier.height(80.dp),
        )

        // Opacity slider
        Text(
            text = "Opacity",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
        )
        VerticalSlider(
            value = activeBrush.opacity,
            onValueChange = onBrushOpacityChanged,
            valueRange = 0f..1f,
            modifier = Modifier.height(80.dp),
        )
    }

    if (showColorPicker) {
        ColorPickerDisc(
            visible = showColorPicker,
            initialColor = selectedColor,
            onColorSelected = { color ->
                onColorSelected(color)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }
}

@Composable
private fun BrushTypeButton(
    brushType: BrushType,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val icon = brushTypeIcon(brushType)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = brushType.displayName,
            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                   else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = brushType.displayName.take(5),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.sp,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier
                .width(80.dp)
                .graphicsLayer { rotationZ = -90f },
        )
    }
}

@Composable
private fun brushTypeIcon(brushType: BrushType): ImageVector = when (brushType) {
    BrushType.PENCIL -> Icons.Default.Edit
    BrushType.PEN -> Icons.Default.Create
    BrushType.MARKER -> Icons.Default.BorderColor
    BrushType.WATERCOLOR -> Icons.Default.Water
    BrushType.CHARCOAL -> Icons.Default.Brush
    BrushType.ERASER -> Icons.Default.AutoFixNormal
}
