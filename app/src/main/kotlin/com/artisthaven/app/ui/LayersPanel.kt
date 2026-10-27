package com.artisthaven.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artisthaven.app.canvas.CanvasUiState
import com.artisthaven.app.canvas.CanvasViewModel
import com.artisthaven.app.canvas.LayerInfo
import com.artisthaven.app.ui.theme.Haven
import com.artisthaven.engine.document.BlendMode
import com.artisthaven.engine.document.LayerProps
import kotlin.math.roundToInt

/** Layer stack, top layer first. Tap a layer to select it; tap the selected layer for its options. */
@Composable
fun LayersPanel(state: CanvasUiState, vm: CanvasViewModel) {
    var expanded by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<LayerInfo?>(null) }
    val host = state.host

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PanelTitle("Layers")
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.addLayer() }, modifier = Modifier.testTag("layer_add")) {
                Icon(Icons.Filled.Add, "New layer", tint = Haven.text)
            }
        }
        LazyColumn(Modifier.heightIn(max = 470.dp).padding(horizontal = 8.dp)) {
            items(host.layers, key = { it.id }) { layer ->
                val active = layer.id == host.activeLayerId
                LayerRow(
                    layer = layer,
                    active = active,
                    editingMask = active && host.editingMask,
                    onMask = {
                        if (!active) vm.selectLayer(layer.id)
                        vm.setEditingMask(!(active && host.editingMask))
                    },
                    onClick = {
                        if (active) expanded = if (expanded == layer.id) null else layer.id else vm.selectLayer(layer.id)
                    },
                    onToggleVisible = { vm.setLayerProps(layer.id) { it.copy(visible = !it.visible) } },
                )
                if (active && expanded == layer.id) {
                    LayerOptions(
                        layer = layer,
                        isBottom = host.layers.lastOrNull()?.id == layer.id,
                        isTop = host.layers.firstOrNull()?.id == layer.id,
                        vm = vm,
                        editingMask = host.editingMask,
                        onRename = { renaming = layer },
                    )
                }
            }
            item {
                PaperRow(state, vm)
            }
        }
    }

    renaming?.let { layer ->
        var name by remember(layer.id) { mutableStateOf(layer.props.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename layer") },
            text = { OutlinedTextField(name, { name = it.take(40) }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.trim()
                    if (n.isNotEmpty()) vm.setLayerProps(layer.id) { it.copy(name = n) }
                    renaming = null
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LayerRow(
    layer: LayerInfo,
    active: Boolean,
    editingMask: Boolean,
    onMask: () -> Unit,
    onClick: () -> Unit,
    onToggleVisible: () -> Unit,
) {
    val p = layer.props
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) Haven.accentSoft else Haven.panelRaised)
            .clickable(onClick = onClick)
            .padding(6.dp)
            .testTag("layer_${layer.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (p.clipping) {
            Text("↳", color = Haven.textDim, modifier = Modifier.padding(end = 4.dp))
        }
        val layerBorder = if (active && !editingMask && layer.maskThumbnail != null) Haven.accent else Haven.divider
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)).border(1.5.dp, layerBorder, RoundedCornerShape(6.dp))) {
            Checkerboard()
            layer.thumbnail?.let {
                Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
        // The mask sits beside the layer; tap it to paint on the mask, tap again to go back.
        layer.maskThumbnail?.let { mask ->
            Box(
                Modifier
                    .padding(start = 4.dp)
                    .size(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.5.dp, if (editingMask) Haven.accent else Haven.divider, RoundedCornerShape(6.dp))
                    .clickable(onClick = onMask)
                    .testTag("mask_${layer.id}"),
            ) {
                Image(mask.asImageBitmap(), "Layer mask", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(p.name, color = Haven.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val details = buildList {
                if (p.blendMode != BlendMode.NORMAL) add(p.blendMode.displayName)
                if (p.opacity < 0.999f) add("${(p.opacity * 100).roundToInt()}%")
                if (p.alphaLock) add("Alpha lock")
                if (p.clipping) add("Clipped")
            }
            if (details.isNotEmpty()) Text(details.joinToString(" · "), color = Haven.textDim, fontSize = 11.sp)
        }
        IconButton(onClick = onToggleVisible, modifier = Modifier.size(36.dp)) {
            Icon(
                if (p.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                if (p.visible) "Hide layer" else "Show layer",
                tint = if (p.visible) Haven.text else Haven.textDim,
            )
        }
    }
}

@Composable
private fun LayerOptions(
    layer: LayerInfo,
    isBottom: Boolean,
    isTop: Boolean,
    vm: CanvasViewModel,
    editingMask: Boolean,
    onRename: () -> Unit,
) {
    var before by remember(layer.id) { mutableStateOf<LayerProps?>(null) }
    var opacity by remember(layer.id, layer.props.opacity) { mutableStateOf(layer.props.opacity) }
    var blendOpen by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Haven.panelRaised.copy(alpha = 0.6f))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Opacity", color = Haven.textDim, fontSize = 12.sp, modifier = Modifier.width(56.dp))
            Slider(
                value = opacity,
                onValueChange = { v ->
                    if (before == null) before = layer.props
                    opacity = v
                    vm.previewLayerProps(layer.id) { it.copy(opacity = v) }
                },
                onValueChangeFinished = {
                    before?.let { vm.commitLayerProps(layer.id, it) }
                    before = null
                },
                modifier = Modifier.weight(1f).testTag("layer_opacity"),
            )
            Text("${(opacity * 100).roundToInt()}%", color = Haven.text, fontSize = 12.sp, modifier = Modifier.width(40.dp))
        }
        Box {
            Text(
                "Blend: ${layer.props.blendMode.displayName}",
                color = Haven.text,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Haven.panelRaised)
                    .clickable { blendOpen = true }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
                    .testTag("layer_blend"),
            )
            DropdownMenu(expanded = blendOpen, onDismissRequest = { blendOpen = false }) {
                BlendMode.entries.forEach { m ->
                    DropdownMenuItem(
                        text = { Text(m.displayName, fontWeight = if (m == layer.props.blendMode) FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            blendOpen = false
                            vm.setLayerProps(layer.id) { it.copy(blendMode = m) }
                        },
                    )
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = layer.props.alphaLock,
                onClick = { vm.setLayerProps(layer.id) { it.copy(alphaLock = !it.alphaLock) } },
                label = { Text("Alpha lock") },
            )
            FilterChip(
                selected = layer.props.clipping,
                enabled = !isBottom,
                onClick = { vm.setLayerProps(layer.id) { it.copy(clipping = !it.clipping) } },
                label = { Text("Clipping mask") },
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (layer.maskThumbnail == null) {
                TextButton(onClick = { vm.addMask(layer.id) }, modifier = Modifier.testTag("mask_add")) {
                    Text("Add mask", color = Haven.accent, fontSize = 13.sp)
                }
            } else {
                SmallAction(if (editingMask) "Edit layer" else "Edit mask") { vm.setEditingMask(!editingMask) }
                SmallAction("Apply mask") { vm.applyMask(layer.id) }
                SmallAction("Delete mask", color = Haven.danger) { vm.deleteMask(layer.id) }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            SmallAction("Rename", onClick = onRename)
            SmallAction("Duplicate") { vm.duplicateLayer(layer.id) }
            SmallAction("Merge down", enabled = !isBottom) { vm.mergeDown(layer.id) }
            SmallAction("Clear") { vm.clearLayer(layer.id) }
            SmallAction("Delete", color = Haven.danger) { vm.deleteLayer(layer.id) }
        }
        Row {
            IconButton(onClick = { vm.moveLayer(layer.id, up = true) }, enabled = !isTop) {
                Icon(Icons.Filled.KeyboardArrowUp, "Move up", tint = if (isTop) Haven.textDim else Haven.text)
            }
            IconButton(onClick = { vm.moveLayer(layer.id, up = false) }, enabled = !isBottom) {
                Icon(Icons.Filled.KeyboardArrowDown, "Move down", tint = if (isBottom) Haven.textDim else Haven.text)
            }
        }
    }
}

@Composable
private fun SmallAction(label: String, enabled: Boolean = true, color: Color = Haven.accent, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(label, color = if (enabled) color else Haven.textDim, fontSize = 13.sp)
    }
}

@Composable
private fun PaperRow(state: CanvasUiState, vm: CanvasViewModel) {
    val host = state.host
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Haven.panelRaised)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)).background(Color(host.paperColor)))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text("Background colour", color = Haven.text, fontSize = 14.sp)
            Text(
                "Use current colour",
                color = Haven.accent,
                fontSize = 12.sp,
                modifier = Modifier.clickable { vm.setPaper(state.color, true) }.padding(vertical = 4.dp),
            )
        }
        IconButton(onClick = { vm.setPaper(host.paperColor, !host.paperVisible) }, modifier = Modifier.size(36.dp)) {
            Icon(
                if (host.paperVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                if (host.paperVisible) "Hide background" else "Show background",
                tint = if (host.paperVisible) Haven.text else Haven.textDim,
            )
        }
    }
}

@Composable
fun Checkerboard(modifier: Modifier = Modifier.fillMaxSize()) {
    Canvas(modifier) {
        val cell = 8.dp.toPx()
        drawRect(Color(0xFFBDBDBD))
        var y = 0f
        var row = 0
        while (y < size.height) {
            var x = if (row % 2 == 0) 0f else cell
            while (x < size.width) {
                drawRect(Color(0xFFEDEDED), Offset(x, y), Size(cell, cell))
                x += cell * 2
            }
            y += cell
            row++
        }
    }
}
