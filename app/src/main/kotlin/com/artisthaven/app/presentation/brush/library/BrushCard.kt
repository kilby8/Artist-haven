package com.artisthaven.app.presentation.brush.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artisthaven.app.domain.model.BrushDefinition
import com.artisthaven.app.domain.model.PressureSensitivityMode

/**
 * Visual card displaying a brush with preview and characteristics.
 */
@Composable
fun BrushCard(
    brush: BrushDefinition,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                color = if (isSelected)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Brush preview
        BrushPreview(
            brush = brush,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.background)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Brush name
        Text(
            text = brush.name,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = if (isSelected)
                MaterialTheme.colorScheme.onPrimaryContainer
            else
                MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Characteristics chip
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            shape = RoundedCornerShape(4.dp),
        ) {
            Text(
                text = brush.coreCharacteristics.take(30) + if (brush.coreCharacteristics.length > 30) "..." else "",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 7.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(2.dp),
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Pressure sensitivity indicator
        PressureSensitivityChip(brush.pressureSensitivity)
    }
}

/**
 * Simple preview canvas showing brush stroke characteristics.
 */
@Composable
fun BrushPreview(
    brush: BrushDefinition,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        // Simple visual representation of brush properties
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            // Draw circle scaled by hardness
            val hardnessRadius = brush.defaultHardness * 20f + 10f
            drawCircle(
                color = androidx.compose.ui.graphics.Color.Black.copy(
                    alpha = brush.defaultOpacity.coerceIn(0f, 1f)
                ),
                radius = hardnessRadius
            )

            // Add texture dots for scattered effect
            if (brush.scatter > 0) {
                repeat((brush.scatter / 2).toInt().coerceAtLeast(1)) {
                    drawCircle(
                        color = androidx.compose.ui.graphics.Color.Black.copy(
                            alpha = 0.3f
                        ),
                        radius = 2f,
                        center = androidx.compose.ui.graphics.center.copy(
                            x = androidx.compose.ui.graphics.center.x + (kotlin.math.random(-20f, 20f)),
                            y = androidx.compose.ui.graphics.center.y + (kotlin.math.random(-20f, 20f))
                        )
                    )
                }
            }
        }
    }
}

/**
 * Small chip showing pressure sensitivity mode.
 */
@Composable
fun PressureSensitivityChip(
    mode: PressureSensitivityMode,
    modifier: Modifier = Modifier,
) {
    val label = when (mode) {
        PressureSensitivityMode.PRESSURE_TO_SIZE -> "P→Size"
        PressureSensitivityMode.PRESSURE_TO_OPACITY -> "P→Opacity"
        PressureSensitivityMode.PRESSURE_TO_FLOW -> "P→Flow"
        PressureSensitivityMode.PRESSURE_TO_SIZE_AND_OPACITY -> "P→S+O"
        PressureSensitivityMode.PRESSURE_TO_HARDNESS -> "P→Hard"
        PressureSensitivityMode.NONE -> "No Pressure"
    }

    val backgroundColor = when (mode) {
        PressureSensitivityMode.NONE -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 7.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

// Helper function for random value in range
private fun kotlin.math.random(min: Float, max: Float): Float {
    return kotlin.math.Random.nextFloat() * (max - min) + min
}
