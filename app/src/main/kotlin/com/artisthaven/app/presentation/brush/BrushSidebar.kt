package com.artisthaven.app.presentation.brush

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.artisthaven.app.domain.model.BrushDefinition
import com.artisthaven.app.domain.model.BrushType
import com.artisthaven.app.ui.components.ColorPickerDisc
import kotlinx.coroutines.delay

/**
 * Sidebar for brush selection and configuration.
 * Displays available brush types and allows adjustment of brush properties.
 * Includes button to open full brush library.
 */
@Composable
fun BrushSidebar(
    activeBrush: Brush,
    selectedColor: Color,
    recentBrushes: List<BrushDefinition> = emptyList(),
    selectedBrushDefinition: BrushDefinition? = null,
    selectedBrushDefinitionId: String? = null,
    savedColors: List<Color> = emptyList(),
    onBrushTypeSelected: (BrushType) -> Unit,
    onRecentBrushSelected: (BrushDefinition) -> Unit = {},
    onBrushSizeChanged: (Float) -> Unit,
    onBrushOpacityChanged: (Float) -> Unit,
    onBrushHardnessChanged: (Float) -> Unit,
    onColorSelected: (Color) -> Unit,
    onSaveColor: (Color) -> Unit = {},
    onRemoveColor: (Color) -> Unit = {},
    onOpenBrushLibrary: () -> Unit = {},
    onClose: () -> Unit = {},
    autoHideSeconds: Int = 12,
    modifier: Modifier = Modifier,
) {
    var showColorPicker by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // Auto-hide: reset the countdown whenever `interactionTick` increments
    var interactionTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(interactionTick) {
        delay(autoHideSeconds * 1_000L)
        onClose()
    }

    // Helper to bump the inactivity timer on any sidebar tap
    fun touched() { interactionTick++ }

    Column(
        modifier = modifier
            .width(72.dp)
            .fillMaxHeight()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
            )
            .padding(vertical = 8.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // ── Close / hide button ───────────────────────────────────────
        IconButton(
            onClick = { touched(); onClose() },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "Hide brush sidebar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }

        // Color picker circle
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(selectedColor)
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable { touched(); showColorPicker = true },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Brush library button
        IconButton(
            onClick = { touched(); onOpenBrushLibrary() },
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

        Text(
            text = selectedBrushDefinition?.displayName ?: activeBrush.type.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        if (selectedBrushDefinition != null) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Default brush types
        BrushType.entries.forEach { brushType ->
            BrushTypeButton(
                brushType = brushType,
                isSelected = activeBrush.type == brushType,
                onClick = { touched(); onBrushTypeSelected(brushType) },
            )
        }

        if (recentBrushes.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = "Recent",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 9.sp,
            )

            recentBrushes.take(4).forEach { brush ->
                RecentBrushButton(
                    brush = brush,
                    isSelected = selectedBrushDefinitionId == brush.id,
                    onClick = { touched(); onRecentBrushSelected(brush) },
                )
            }
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

        Text(
            text = "Hard",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
        )
        VerticalSlider(
            value = activeBrush.hardness,
            onValueChange = onBrushHardnessChanged,
            valueRange = 0f..1f,
            modifier = Modifier.height(80.dp),
        )
    }

    if (showColorPicker) {
        ColorPickerDisc(
            visible = showColorPicker,
            initialColor = selectedColor,
            savedColors = savedColors,
            onColorSelected = { color ->
                onColorSelected(color)
                showColorPicker = false
            },
            onSaveColor = onSaveColor,
            onRemoveColor = onRemoveColor,
            onDismiss = { showColorPicker = false },
        )
    }
}

@Composable
private fun RecentBrushButton(
    brush: BrushDefinition,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val label = brush.displayName.split(' ').joinToString("") { it.take(1) }.take(3).uppercase()

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.sp,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
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
