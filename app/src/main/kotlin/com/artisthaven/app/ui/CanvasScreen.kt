package com.artisthaven.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.HighlightAlt
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.artisthaven.app.canvas.CanvasView
import com.artisthaven.app.canvas.CanvasViewModel
import com.artisthaven.app.canvas.Panel
import com.artisthaven.app.canvas.ToolMode
import com.artisthaven.app.canvas.SelectShape
import com.artisthaven.engine.Editor
import com.artisthaven.app.canvas.TouchAction
import com.artisthaven.app.ui.theme.Haven
import com.artisthaven.engine.stroke.InputPoint
import com.artisthaven.engine.tools.Affine
import com.artisthaven.engine.tools.Resample
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** The drawing screen: canvas, top bar, sidebar sliders and the pop-over panels. */
@Composable
fun CanvasScreen(vm: CanvasViewModel, onExit: () -> Unit) {
    val state by vm.state.collectAsState()
    val host by vm.hostReady.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var canvasView by remember { mutableStateOf<CanvasView?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }

    BackHandler {
        when {
            state.panel != null -> vm.closePanel()
            state.transform != null -> vm.cancelTransform()
            else -> vm.close(onExit)
        }
    }

    Box(Modifier.fillMaxSize().background(Haven.background)) {
        if (host != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize().testTag("canvas"),
                factory = { ctx ->
                    CanvasView(ctx, object : CanvasView.Callbacks {
                        override fun touchAction(): TouchAction = vm.touchAction()
                        override fun beginStroke(first: InputPoint, isStylus: Boolean, isEraserTip: Boolean) =
                            vm.beginStroke(first, isStylus, isEraserTip)
                        override fun strokeTo(points: List<InputPoint>) { vm.host?.strokeTo(points) }
                        override fun endStroke() { vm.host?.endStroke() }
                        override fun cancelStroke() { vm.host?.cancelStroke() }
                        override fun quickShape(done: (Boolean) -> Unit) { vm.host?.quickShape(done) ?: done(false) }
                        override fun fill(x: Int, y: Int) = vm.fill(x, y)
                        override fun pickColor(x: Int, y: Int, final: Boolean) {
                            val view = canvasView
                            if (!final) view?.previousColor = vm.state.value.color
                            vm.host?.sample(x, y) { c ->
                                view?.loupeColor = c
                                if (final) {
                                    vm.setColor(c)
                                    if (vm.state.value.tool == ToolMode.EYEDROPPER) vm.selectTool(vm.state.value.lastPaintTool)
                                }
                            }
                        }
                        override fun undo() { vm.undo() }
                        override fun redo() { vm.redo() }
                        override fun brushDiameter(): Float = vm.state.value.size
                        override fun fingerPaintingAllowed(): Boolean = vm.state.value.fingerPainting
                        override fun onStylusDetected() = vm.onStylusDetected()
                        override fun transformTo(m: Affine) = vm.updateTransform(m)
                        override fun selectShape(): SelectShape = vm.state.value.selectShape
                        override fun selectOutline(xy: FloatArray) = vm.selectOutline(xy)
                        override fun selectAt(x: Int, y: Int) { vm.selectAt(x, y) }
                    }).also {
                        it.leftInset = 64f * ctx.resources.displayMetrics.density
                        canvasView = it
                    }
                },
                update = { view ->
                    if (view.host !== host) view.host = host
                    view.transform = state.transform
                },
            )
        }

        Column(Modifier.fillMaxSize()) {
            TopBar(
                state = state,
                onGallery = { vm.close(onExit) },
                onActions = { vm.togglePanel(Panel.ACTIONS) },
                onTransform = vm::toggleTransform,
                onTool = vm::selectTool,
                onLayers = { vm.togglePanel(Panel.LAYERS) },
                onColors = { vm.togglePanel(Panel.COLORS) },
            )
            Box(Modifier.fillMaxSize()) {
                Sidebar(
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp),
                    size = state.size,
                    minSize = state.activeBrush.minSize,
                    maxSize = state.activeBrush.maxSize,
                    opacity = state.opacity,
                    eyedropperActive = state.tool == ToolMode.EYEDROPPER,
                    fillActive = state.tool == ToolMode.FILL,
                    onFill = { vm.selectTool(if (state.tool == ToolMode.FILL) state.lastPaintTool else ToolMode.FILL) },
                    canUndo = state.host.canUndo,
                    canRedo = state.host.canRedo,
                    onUndo = { vm.undo() },
                    onRedo = { vm.redo() },
                    onSize = vm::setSize,
                    onOpacity = vm::setOpacity,
                    onEyedropper = {
                        if (state.tool == ToolMode.EYEDROPPER) vm.selectTool(state.lastPaintTool) else vm.selectTool(ToolMode.EYEDROPPER)
                    },
                )
                val panel = state.panel
                if (panel != null) {
                    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { vm.closePanel() } })
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 8.dp)
                            .widthIn(max = 380.dp)
                            .fillMaxWidth(0.92f)
                            .heightIn(max = 620.dp)
                            .testTag("panel_${panel.name.lowercase()}"),
                        shape = RoundedCornerShape(16.dp),
                        color = Haven.panel,
                        shadowElevation = 12.dp,
                    ) {
                        when (panel) {
                            Panel.BRUSHES -> BrushLibraryPanel(state, onSelect = vm::selectBrush)
                            Panel.COLORS -> ColorPanel(
                                state = state,
                                onColor = { c, final -> vm.setColor(c, addToRecent = final) },
                                onAddToPalette = vm::addToPalette,
                                onRemoveFromPalette = vm::removeFromPalette,
                            )
                            Panel.LAYERS -> LayersPanel(state, vm)
                            Panel.ACTIONS -> ActionsPanel(
                                state = state,
                                vm = vm,
                                onFit = { canvasView?.fitToScreen() },
                                onShare = { intent -> context.startActivity(intent) },
                            )
                        }
                    }
                }
            }
        }

        val bottom = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp)
        if (state.transform == null && state.tool == ToolMode.SELECT) {
            SelectionBar(bottom, state, vm)
        } else if (state.transform == null && state.host.hasSelection) {
            Row(bottom.clip(RoundedCornerShape(14.dp)).background(Haven.bar).padding(horizontal = 4.dp)) {
                SmallAction("Deselect", "deselect_quick", onClick = { vm.deselect() })
            }
        }
        state.transform?.let { t ->
            TransformBar(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
                quality = t.quality,
                onFlipH = { vm.flipTransform(horizontal = true) },
                onFlipV = { vm.flipTransform(horizontal = false) },
                onRotate = vm::rotateTransform90,
                onReset = vm::resetTransform,
                onQuality = vm::setTransformQuality,
                onCancel = vm::cancelTransform,
                onDone = vm::commitTransform,
            )
        }

        if (state.loading || state.busy) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        state.error?.let { err ->
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(err, color = Haven.text, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text("Back to gallery", color = Haven.accent, modifier = Modifier.clickable { onExit() }.padding(8.dp))
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

@Composable
private fun TopBar(
    state: com.artisthaven.app.canvas.CanvasUiState,
    onGallery: () -> Unit,
    onActions: () -> Unit,
    onTransform: () -> Unit,
    onTool: (ToolMode) -> Unit,
    onLayers: () -> Unit,
    onColors: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Haven.bar)
            .statusBarsPadding()
            .height(52.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarButton(Icons.Filled.GridView, "Gallery", onClick = onGallery)
        BarButton(Icons.Filled.Build, "Actions", selected = state.panel == Panel.ACTIONS, onClick = onActions)
        BarButton(Icons.Filled.HighlightAlt, "Select", selected = state.transform == null && state.tool == ToolMode.SELECT) { onTool(ToolMode.SELECT) }
        BarButton(Icons.Filled.OpenWith, "Transform", selected = state.transform != null, onClick = onTransform)
        Spacer(Modifier.weight(1f))
        val painting = state.transform == null
        BarButton(Icons.Filled.Brush, "Paint", selected = painting && state.tool == ToolMode.BRUSH) { onTool(ToolMode.BRUSH) }
        BarButton(Icons.Filled.TouchApp, "Smudge", selected = painting && state.tool == ToolMode.SMUDGE) { onTool(ToolMode.SMUDGE) }
        BarButton(Icons.Filled.AutoFixNormal, "Erase", selected = painting && state.tool == ToolMode.ERASER) { onTool(ToolMode.ERASER) }
        BarButton(Icons.Filled.Layers, "Layers", selected = state.panel == Panel.LAYERS, onClick = onLayers)
        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(state.color))
                .border(2.dp, if (state.panel == Panel.COLORS) Haven.accent else Color.White.copy(alpha = 0.8f), CircleShape)
                .clickable(onClick = onColors)
                .semantics { contentDescription = "Colours" }
                .testTag("tool_colors"),
        )
    }
}

/** Selection tool options: how to select, how to combine, and what to do with the selection. */
@Composable
private fun SelectionBar(modifier: Modifier, state: com.artisthaven.app.canvas.CanvasUiState, vm: CanvasViewModel) {
    Column(
        modifier
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Haven.bar)
            .padding(4.dp)
            .testTag("selection_bar"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            SelectShape.entries.forEach { shape ->
                SmallAction(
                    shape.label,
                    "select_${shape.name.lowercase()}",
                    onClick = { vm.setSelectShape(shape) },
                    color = if (state.selectShape == shape) Haven.accent else Haven.text,
                )
            }
            Spacer(Modifier.width(6.dp))
            listOf(Editor.SelectMode.REPLACE to "New", Editor.SelectMode.ADD to "Add", Editor.SelectMode.SUBTRACT to "Remove").forEach { (m, label) ->
                SmallAction(
                    label,
                    "select_mode_${m.name.lowercase()}",
                    onClick = { vm.setSelectMode(m) },
                    color = if (state.selectMode == m) Haven.accent else Haven.textDim,
                )
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            val has = state.host.hasSelection
            SmallAction("All", "select_all", onClick = { vm.selectAll() })
            SmallAction("Invert", "select_invert", onClick = { vm.invertSelection() }, color = if (has) Haven.text else Haven.textDim)
            SmallAction("Clear", "select_clear", onClick = { vm.clearSelected() }, color = if (has) Haven.text else Haven.textDim)
            SmallAction("Fill", "select_fill", onClick = { vm.fillSelection() }, color = if (has) Haven.text else Haven.textDim)
            SmallAction("Copy", "select_copy", onClick = { vm.selectionToNewLayer(cut = false) }, color = if (has) Haven.text else Haven.textDim)
            SmallAction("Cut", "select_cut", onClick = { vm.selectionToNewLayer(cut = true) }, color = if (has) Haven.text else Haven.textDim)
            SmallAction("Deselect", "select_none", onClick = { vm.deselect() }, color = if (has) Haven.accent else Haven.textDim)
        }
    }
}

/** Options shown while moving a layer: flips, quarter turns, resampling, and Cancel / Done. */
@Composable
private fun TransformBar(
    modifier: Modifier,
    quality: Resample,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
    onRotate: () -> Unit,
    onReset: () -> Unit,
    onQuality: (Resample) -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    Row(
        modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Haven.bar)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .testTag("transform_bar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Options scroll if the screen is narrow; Cancel and Done always stay in reach.
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            SmallAction("Flip ↔", "transform_flip_h", onFlipH)
            SmallAction("Flip ↕", "transform_flip_v", onFlipV)
            SmallAction("90°", "transform_rotate", onRotate)
            val next = Resample.entries[(quality.ordinal + 1) % Resample.entries.size]
            SmallAction(quality.displayName, "transform_quality", onClick = { onQuality(next) })
            SmallAction("Reset", "transform_reset", onReset)
        }
        SmallAction("Cancel", "transform_cancel", onCancel, color = Haven.textDim)
        SmallAction("Done", "transform_done", onDone, color = Haven.accent)
    }
}

@Composable
private fun SmallAction(label: String, tag: String, onClick: () -> Unit, color: Color = Haven.text) {
    Text(
        label,
        color = color,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp)
            .testTag(tag),
    )
}

@Composable
private fun BarButton(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp).testTag("tool_${label.lowercase()}")) {
        Icon(
            icon,
            contentDescription = label,
            tint = when {
                !enabled -> Haven.textDim.copy(alpha = 0.4f)
                selected -> Haven.accent
                else -> Haven.text
            },
        )
    }
}

@Composable
private fun Sidebar(
    modifier: Modifier,
    size: Float,
    minSize: Float,
    maxSize: Float,
    opacity: Float,
    eyedropperActive: Boolean,
    fillActive: Boolean,
    onFill: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSize: (Float) -> Unit,
    onOpacity: (Float) -> Unit,
    onEyedropper: () -> Unit,
) {
    // Size uses a squared curve so small brushes get most of the slider, like Procreate.
    val sizeT = sqrt(((size - minSize) / (maxSize - minSize)).coerceIn(0f, 1f))
    Column(
        modifier
            .background(Haven.bar, RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        VerticalSlider(
            value = sizeT,
            onChange = { t -> onSize(minSize + (maxSize - minSize) * t * t) },
            label = "Size",
            readout = { "${size.roundToInt()} px" },
            tag = "slider_size",
        )
        IconButton(onClick = onEyedropper, modifier = Modifier.size(36.dp).testTag("tool_eyedropper")) {
            Icon(Icons.Filled.Colorize, "Eyedropper", tint = if (eyedropperActive) Haven.accent else Haven.text)
        }
        IconButton(onClick = onFill, modifier = Modifier.size(36.dp).testTag("tool_fill")) {
            Icon(Icons.Filled.FormatColorFill, "Fill", tint = if (fillActive) Haven.accent else Haven.text)
        }
        VerticalSlider(
            value = opacity,
            onChange = onOpacity,
            label = "Opacity",
            readout = { "${(opacity * 100).roundToInt()}%" },
            tag = "slider_opacity",
        )
        // Undo and redo live with the sliders, under the thumb, as in Procreate.
        IconButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.size(36.dp).testTag("tool_undo")) {
            Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = if (canUndo) Haven.text else Haven.textDim.copy(alpha = 0.4f))
        }
        IconButton(onClick = onRedo, enabled = canRedo, modifier = Modifier.size(36.dp).testTag("tool_redo")) {
            Icon(Icons.AutoMirrored.Filled.Redo, "Redo", tint = if (canRedo) Haven.text else Haven.textDim.copy(alpha = 0.4f))
        }
    }
}

/** A thin vertical slider with a value bubble while dragging; drag up to increase. */
@Composable
fun VerticalSlider(value: Float, onChange: (Float) -> Unit, label: String, readout: () -> String, tag: String) {
    var dragging by remember { mutableStateOf(false) }
    BoxWithConstraints(
        Modifier
            .width(30.dp)
            .height(150.dp)
            .testTag(tag)
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    change.consume()
                    onChange((1f - change.position.y / size.height).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { pos -> onChange((1f - pos.y / size.height).coerceIn(0f, 1f)) }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        val trackH = maxHeight
        Box(
            Modifier
                .width(6.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.18f)),
        )
        val thumb = 22.dp
        val y = (trackH - thumb) * (1f - value.coerceIn(0f, 1f))
        Box(
            Modifier
                .offset(y = y)
                .size(width = 26.dp, height = thumb)
                .clip(RoundedCornerShape(7.dp))
                .background(if (dragging) Haven.accent else Color(0xFFDADADF)),
        )
        if (dragging) {
            Text(
                readout(),
                color = Haven.text,
                fontSize = 13.sp,
                modifier = Modifier
                    .offset { IntOffset(48.dp.roundToPx(), y.roundToPx()) }
                    .clip(RoundedCornerShape(8.dp))
                    .background(Haven.panelRaised)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}
