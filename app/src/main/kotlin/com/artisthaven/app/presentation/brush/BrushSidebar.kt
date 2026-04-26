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

/**
 * Sidebar for brush selection and configuration.
 * Displays available brush types and allows adjustment of brush properties.
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
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(selectedColor)
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable { showColorPicker = true },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        BrushType.entries.forEach { brushType ->
            BrushTypeButton(
                brushType = brushType,
                isSelected = activeBrush.type == brushType,
                onClick = { onBrushTypeSelected(brushType) },
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

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
        ColorPickerDialog(
            currentColor = selectedColor,
            onColorSelected = { color ->
                onColorSelected(color)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false },
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
private fun ColorPickerDialog(
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    val presetColors = listOf(
        Color.Black, Color.White, Color.Red, Color.Green, Color.Blue,
        Color.Yellow, Color(0xFFFF6600), Color(0xFF9900FF), Color(0xFF00CCFF),
        Color(0xFF8B4513), Color(0xFF228B22), Color(0xFF191970),
        Color.Gray, Color.LightGray, Color(0xFFFFB6C1), Color(0xFFADD8E6),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Color") },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(currentColor)
                )
                Spacer(Modifier.height(12.dp))

                LazyColumn {
                    items(presetColors.chunked(4)) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (color == currentColor) 3.dp else 1.dp,
                                            color = if (color == currentColor)
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline,
                                            shape = CircleShape,
                                        )
                                        .clickable { onColorSelected(color) }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

private fun brushTypeIcon(brushType: BrushType): ImageVector = when (brushType) {
    BrushType.PENCIL -> Icons.Default.Edit
    BrushType.PEN -> Icons.Default.Create
    BrushType.MARKER -> Icons.Default.BorderColor
    BrushType.WATERCOLOR -> Icons.Default.Water
    BrushType.CHARCOAL -> Icons.Default.Brush
    BrushType.ERASER -> Icons.Default.AutoFixNormal
}
