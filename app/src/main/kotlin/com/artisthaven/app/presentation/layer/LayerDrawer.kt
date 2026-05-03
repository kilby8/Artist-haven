package com.artisthaven.app.presentation.layer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.artisthaven.app.domain.model.Layer
import kotlinx.coroutines.delay

/**
 * Layer management drawer composable.
 * Displays all layers with controls for visibility, opacity, and ordering.
 */
@Composable
fun LayerDrawer(
    layers: List<Layer>,
    activeLayerIndex: Int,
    onLayerSelected: (Int) -> Unit,
    onLayerVisibilityToggled: (String) -> Unit,
    onLayerAdded: () -> Unit,
    onLayerDeleted: (String) -> Unit,
    onLayerOpacityChanged: (String, Float) -> Unit,
    onClose: () -> Unit = {},
    autoHideSeconds: Int = 12,
    modifier: Modifier = Modifier,
) {
    var interactionTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(interactionTick) {
        delay(autoHideSeconds * 1_000L)
        onClose()
    }

    fun touched() { interactionTick++ }

    Column(
        modifier = modifier
            .width(200.dp)
            .fillMaxHeight()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
            )
            .padding(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Layers",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close layers",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = { touched(); onLayerAdded() },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add layer",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            reverseLayout = true,
        ) {
            itemsIndexed(layers) { index, layer ->
                LayerItem(
                    layer = layer,
                    isActive = index == activeLayerIndex,
                    onClick = { touched(); onLayerSelected(index) },
                    onVisibilityToggled = { touched(); onLayerVisibilityToggled(layer.id) },
                    onOpacityChanged = { opacity -> touched(); onLayerOpacityChanged(layer.id, opacity) },
                    onInteracted = { touched() },
                    onDelete = {
                        touched()
                        if (layers.size > 1) onLayerDeleted(layer.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun LayerItem(
    layer: Layer,
    isActive: Boolean,
    onClick: () -> Unit,
    onVisibilityToggled: () -> Unit,
    onOpacityChanged: (Float) -> Unit,
    onInteracted: () -> Unit,
    onDelete: () -> Unit,
) {
    var showOpacitySlider by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .border(
                width = if (isActive) 1.dp else 0.dp,
                color = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onVisibilityToggled,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = if (layer.isVisible) Icons.Default.Visibility
                                  else Icons.Default.VisibilityOff,
                    contentDescription = "Toggle visibility",
                    tint = if (layer.isVisible) MaterialTheme.colorScheme.onSurface
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp),
                )
            }

            Text(
                text = layer.name,
                style = MaterialTheme.typography.bodySmall,
                color = if (layer.isVisible) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            IconButton(
                onClick = { onInteracted(); showOpacitySlider = !showOpacitySlider },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = "Opacity",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp),
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete layer",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        if (showOpacitySlider) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${(layer.opacity * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(32.dp),
                )
                Slider(
                    value = layer.opacity,
                    onValueChange = onOpacityChanged,
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
