package com.artisthaven.app.presentation.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artisthaven.app.domain.model.CanvasType

/**
 * Composable UI components for canvas type selection and configuration.
 * Integrates with CanvasRenderingManager for seamless canvas switching.
 */

/**
 * Horizontal selector for different canvas types.
 * Shows previews and descriptions for each option.
 */
@Composable
fun CanvasTypeSelector(
    selectedCanvasType: CanvasType = CanvasType.COLD_PRESS_PAPER,
    onCanvasTypeSelected: (CanvasType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            text = "Canvas Type",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        LazyRow {
            items(CanvasType.values()) { canvasType ->
                CanvasTypeCard(
                    canvasType = canvasType,
                    isSelected = selectedCanvasType == canvasType,
                    onClick = { onCanvasTypeSelected(canvasType) },
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
        }
    }
}

/**
 * Individual canvas type card with preview color and selection indicator.
 */
@Composable
fun CanvasTypeCard(
    canvasType: CanvasType,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .size(width = 140.dp, height = 100.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 8.dp else 2.dp,
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFE8E4E0) else Color(0xFFFAFAFA),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Preview square showing canvas base color
            CanvasPreviewSquare(
                color = canvasType.baseColor,
                modifier = Modifier
                    .size(60.dp)
                    .padding(bottom = 8.dp),
            )

            // Canvas type name
            Text(
                text = canvasType.displayName,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp),
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )

            // Selection indicator
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * Preview square showing the base color of a canvas type.
 * In a real implementation, this could show a texture preview bitmap.
 */
@Composable
fun CanvasPreviewSquare(
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (color == Color.Transparent) {
        // Draw checkerboard for transparent canvas
        ComposableCheckerboard(modifier = modifier)
    } else {
        Row(
            modifier = modifier
                .background(
                    color = color,
                    shape = RoundedCornerShape(4.dp),
                ),
        ) {}
    }
}

/**
 * Checkerboard pattern for transparent canvas preview.
 */
@Composable
fun ComposableCheckerboard(modifier: Modifier = Modifier) {
    val lightGray = Color(0xFFD2D2D2)
    val darkGray = Color(0xFF808080)
    val squareSize = 6.dp

    Row(modifier = modifier) {
        // Simple 2x2 checkerboard for UI preview
        repeat(2) { row ->
            repeat(2) { col ->
                Row(
                    modifier = Modifier
                        .size(squareSize * 2)
                        .background(
                            color = if ((row + col) % 2 == 0) lightGray else darkGray,
                        ),
                ) {}
            }
        }
    }
}

/**
 * Detailed canvas information panel showing description and recommended mediums.
 */
@Composable
fun CanvasInfoPanel(
    canvasType: CanvasType,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF5F5F5),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = canvasType.displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Text(
                text = canvasType.description,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 12.dp),
                color = Color(0xFF666666),
            )

            Text(
                text = "Recommended for:",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 6.dp),
            )

            Column {
                canvasType.recommendedMediums.forEach { medium ->
                    Text(
                        text = "• $medium",
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                        color = Color(0xFF555555),
                    )
                }
            }

            // Technical specs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Roughness",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "%.0f%%".format(canvasType.roughness * 100),
                        fontSize = 12.sp,
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Scale",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "%.1fx".format(canvasType.scale),
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

/**
 * Canvas feature toggles for tooth interaction, lighting, etc.
 */
@Composable
fun CanvasFeatureToggles(
    enableToothInteraction: Boolean = true,
    enableLighting: Boolean = false,
    onToothInteractionChange: (Boolean) -> Unit = {},
    onLightingChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            text = "Canvas Features",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        // Tooth Interaction Toggle
        FeatureToggleRow(
            title = "Dry Brush Effect (Tooth)",
            description = "Light pressure follows paper texture peaks",
            isEnabled = enableToothInteraction,
            onToggle = onToothInteractionChange,
        )

        // Lighting Toggle
        FeatureToggleRow(
            title = "3D Lighting Simulation",
            description = "Adds depth to canvas texture",
            isEnabled = enableLighting,
            onToggle = onLightingChange,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/**
 * Individual feature toggle row.
 */
@Composable
fun FeatureToggleRow(
    title: String,
    description: String = "",
    isEnabled: Boolean = true,
    onToggle: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggle(!isEnabled) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = Color(0xFF888888),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        // Checkbox indicator
        if (isEnabled) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Enabled",
                tint = Color(0xFF4CAF50),
                modifier = Modifier
                    .size(24.dp)
                    .padding(start = 8.dp),
            )
        }
    }
}

/**
 * Canvas comparison table showing all canvas types side-by-side.
 */
@Composable
fun CanvasComparisonTable(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Canvas Type Comparison",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            // Table header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .background(Color(0xFFEEEEEE), shape = RoundedCornerShape(4.dp))
                    .padding(8.dp),
            ) {
                Text("Canvas", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                Text("Best For", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
                Text("Tech", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
            }

            // Table rows
            CanvasType.values().forEach { canvasType ->
                CanvasComparisonRow(canvasType)
            }
        }
    }
}

/**
 * Single row in the canvas comparison table.
 */
@Composable
fun CanvasComparisonRow(canvasType: CanvasType) {
    val technicalSecret = when (canvasType) {
        CanvasType.COLD_PRESS_PAPER -> "High Contrast Alpha Mask"
        CanvasType.FINE_GRAIN_LINEN -> "Cross-hatch BitmapShader"
        CanvasType.DARK_SLATE -> "Subtle Noise Grain"
        CanvasType.TRANSPARENT_GRID -> "PNG Checkerboard"
        CanvasType.VELLUM -> "Smooth Surface"
        CanvasType.PRIMED_CANVAS -> "Canvas Weave"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = canvasType.displayName,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1.5f),
        )
        Text(
            text = canvasType.recommendedMediums.take(2).joinToString(", "),
            fontSize = 10.sp,
            modifier = Modifier.weight(2f),
            color = Color(0xFF666666),
        )
        Text(
            text = technicalSecret,
            fontSize = 10.sp,
            modifier = Modifier.weight(2f),
            color = Color(0xFF888888),
        )
    }
}

