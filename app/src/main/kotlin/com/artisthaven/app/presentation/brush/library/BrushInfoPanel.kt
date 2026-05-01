package com.artisthaven.app.presentation.brush.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artisthaven.app.domain.model.BrushDefinition

/**
 * Detailed information panel for the currently selected brush.
 * Shows all technical parameters and characteristics.
 */
@Composable
fun BrushInfoPanel(
    brush: BrushDefinition,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // Brush name and description
        Text(
            text = brush.displayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = brush.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Core Characteristics
        SectionHeader("Characteristics")
        Text(
            text = brush.coreCharacteristics,
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Technical Parameters Section
        SectionHeader("Parameters")

        ParameterRow("Size", "${brush.defaultSize.toInt()}px", "1-100px")
        ParameterRow("Opacity", "${(brush.defaultOpacity * 100).toInt()}%", "0-100%")
        ParameterRow("Hardness", "${(brush.defaultHardness * 100).toInt()}%", "0-100%")
        ParameterRow("Flow", "${(brush.defaultFlow * 100).toInt()}%", "0-100%")
        ParameterRow("Spacing", "${(brush.spacing * 100).toInt()}%", "0-100%")
        ParameterRow("Scatter", "${brush.scatter.toInt()}", "0+")
        ParameterRow("Jitter", "${brush.jitter.toInt()}°", "0+")

        Spacer(modifier = Modifier.height(12.dp))

        // Blend Mode
        SectionHeader("Rendering")
        ParameterRow("Blend Mode", brush.blendMode.toString(), "")

        Spacer(modifier = Modifier.height(4.dp))

        // Pressure Sensitivity
        ParameterRow("Pressure", brush.pressureSensitivity.toString(), "")

        Spacer(modifier = Modifier.height(4.dp))

        // Shader Info
        if (brush.usesShader && brush.shaderId != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.filled.Palette,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "GPU Shader Effect",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = brush.shaderId!!,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Section header for grouping related parameters.
 */
@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
    Spacer(modifier = Modifier.height(4.dp))
}

/**
 * Row showing a parameter with label, value, and range.
 */
@Composable
private fun ParameterRow(
    label: String,
    value: String,
    range: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
            )
        }
        if (range.isNotEmpty()) {
            Text(
                text = range,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Compact brush info to show in sidebar.
 */
@Composable
fun CompactBrushInfo(
    brush: BrushDefinition,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = brush.displayName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AssistChip(
                onClick = {},
                label = { Text("Size: ${brush.defaultSize.toInt()}") },
                modifier = Modifier.height(24.dp),
            )
            AssistChip(
                onClick = {},
                label = { Text("${(brush.defaultOpacity * 100).toInt()}%") },
                modifier = Modifier.height(24.dp),
            )
        }

        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            shape = MaterialTheme.shapes.extraSmall,
        ) {
            Text(
                text = brush.coreCharacteristics,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.sp,
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}
