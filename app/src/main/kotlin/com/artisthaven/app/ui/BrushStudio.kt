package com.artisthaven.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artisthaven.app.canvas.BrushPreviews
import com.artisthaven.app.ui.theme.Haven
import com.artisthaven.engine.brush.BrushLibrary
import com.artisthaven.engine.brush.BrushPreset
import com.artisthaven.engine.brush.GrainTexture
import com.artisthaven.engine.brush.RenderMode
import com.artisthaven.engine.brush.TipAngle
import com.artisthaven.engine.brush.TipShape
import com.artisthaven.engine.document.BlendMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * The Brush Studio: every setting the engine uses, grouped like Procreate's, with a live stroke
 * preview painted by the real engine as you drag.
 */
@Composable
fun BrushStudio(
    brush: BrushPreset,
    color: Int,
    onChange: (BrushPreset) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(brush, color) {
        delay(60)
        preview = withContext(Dispatchers.Default) { BrushPreviews.render(brush) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Haven.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("brush_studio"),
    ) {
        Row(
            Modifier.fillMaxWidth().background(Haven.bar).padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text("Cancel", color = Haven.textDim) }
            OutlinedTextField(
                value = brush.name,
                onValueChange = { onChange(brush.copy(name = it.take(40))) },
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = Haven.text),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f).testTag("studio_name"),
            )
            TextButton(onClick = onSave, modifier = Modifier.testTag("studio_save")) {
                Text("Save", color = Haven.accent, fontWeight = FontWeight.SemiBold)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .height(120.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Haven.panel),
            contentAlignment = Alignment.Center,
        ) {
            preview?.let {
                Image(it.asImageBitmap(), contentDescription = "Preview", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Group("Shape")
            Choice("Tip", TipShape.entries, brush.tip, { it.displayName }) { onChange(brush.copy(tip = it)) }
            Value("Hardness", brush.hardness, 0f..1f, pct) { onChange(brush.copy(hardness = it)) }
            Value("Roundness", brush.roundness, 0.05f..1f, pct) { onChange(brush.copy(roundness = it)) }
            Choice("Tip faces", TipAngle.entries, brush.tipAngle, { angleName(it) }) { onChange(brush.copy(tipAngle = it)) }
            Value("Angle", brush.angleDegrees, 0f..180f, deg) { onChange(brush.copy(angleDegrees = it)) }
            Value("Rotation jitter", brush.rotationJitter, 0f..180f, deg) { onChange(brush.copy(rotationJitter = it)) }

            Group("Stroke path")
            Value("Spacing", brush.spacing, 0.01f..1f, pct) { onChange(brush.copy(spacing = it)) }
            Value("Scatter", brush.lateralJitter, 0f..1f, pct) { onChange(brush.copy(lateralJitter = it)) }
            Value("Size jitter", brush.sizeJitter, 0f..1f, pct) { onChange(brush.copy(sizeJitter = it)) }
            Value("Opacity jitter", brush.opacityJitter, 0f..1f, pct) { onChange(brush.copy(opacityJitter = it)) }
            Value("StreamLine", brush.streamline, 0f..1f, pct) { onChange(brush.copy(streamline = it)) }

            Group("Taper")
            Value("Start", brush.taperStart, 0f..12f, num) { onChange(brush.copy(taperStart = it)) }
            Value("End", brush.taperEnd, 0f..12f, num) { onChange(brush.copy(taperEnd = it)) }
            Value("Thinnest", brush.taperMinSize, 0f..1f, pct) { onChange(brush.copy(taperMinSize = it)) }

            Group("Grain")
            Choice("Texture", GrainTexture.entries, brush.grain, { it.displayName }) { onChange(brush.copy(grain = it)) }
            Value("Depth", brush.grainDepth, 0f..1f, pct) { onChange(brush.copy(grainDepth = it)) }
            Value("Scale", brush.grainScale, 0.1f..4f, num) { onChange(brush.copy(grainScale = it)) }
            Toggle("Moves with the stroke", brush.grainMoving) { onChange(brush.copy(grainMoving = it)) }

            Group("Rendering")
            Choice("Paint", RenderMode.entries, brush.renderMode, { if (it == RenderMode.WET) "Wet (mixes)" else "Glaze (even)" }) {
                onChange(brush.copy(renderMode = it))
            }
            Value("Flow", brush.flow, 0.02f..1f, pct) { onChange(brush.copy(flow = it)) }
            Choice("Blend", BlendMode.entries, brush.blendMode, { it.displayName }) { onChange(brush.copy(blendMode = it)) }
            if (brush.renderMode == RenderMode.WET) {
                Value("Dilution", brush.dilution, 0f..1f, pct) { onChange(brush.copy(dilution = it)) }
                Value("Charge", brush.charge, 0f..1f, pct) { onChange(brush.copy(charge = it)) }
                Value("Pull", brush.pull, 0f..1f, pct) { onChange(brush.copy(pull = it)) }
            }

            Group("Pressure and tilt")
            Value("Pressure → size", brush.pressureSize, 0f..1f, pct) { onChange(brush.copy(pressureSize = it)) }
            Value("Pressure → opacity", brush.pressureOpacity, 0f..1f, pct) { onChange(brush.copy(pressureOpacity = it)) }
            Value("Pressure → flow", brush.pressureFlow, 0f..1f, pct) { onChange(brush.copy(pressureFlow = it)) }
            Value("Pressure curve", brush.pressureGamma, 0.3f..3f, num) { onChange(brush.copy(pressureGamma = it)) }
            Value("Tilt → size", brush.tiltSize, 0f..3f, num) { onChange(brush.copy(tiltSize = it)) }
            Value("Tilt → opacity", brush.tiltOpacity, 0f..1f, pct) { onChange(brush.copy(tiltOpacity = it)) }
            Value("Speed → size", brush.speedSize, 0f..1f, pct) { onChange(brush.copy(speedSize = it)) }
            Value("Speed → opacity", brush.speedOpacity, 0f..1f, pct) { onChange(brush.copy(speedOpacity = it)) }

            Group("Properties")
            Value("Smallest size", brush.minSize, 0.5f..50f, px) { onChange(brush.copy(minSize = it, defaultSize = brush.defaultSize.coerceAtLeast(it))) }
            Value("Largest size", brush.maxSize, 10f..1000f, px) { onChange(brush.copy(maxSize = it, defaultSize = brush.defaultSize.coerceAtMost(it))) }
            Value("Smudge strength", brush.smudgeStrength, 0f..1f, pct) { onChange(brush.copy(smudgeStrength = it)) }

            if (onDelete != null) {
                TextButton(onClick = onDelete, modifier = Modifier.padding(12.dp).testTag("studio_delete")) {
                    Text("Delete brush", color = Haven.danger)
                }
            }
        }
    }
}

private val pct: (Float) -> String = { "${(it * 100).roundToInt()}%" }
private val deg: (Float) -> String = { "${it.roundToInt()}°" }
private val px: (Float) -> String = { "${if (it < 10f) "%.1f".format(it) else it.roundToInt().toString()} px" }
private val num: (Float) -> String = { "%.2f".format(it) }

private fun angleName(a: TipAngle) = when (a) {
    TipAngle.FIXED -> "Fixed"
    TipAngle.FOLLOW_STROKE -> "Stroke direction"
    TipAngle.STYLUS -> "Pen angle"
}

@Composable
private fun Group(title: String) {
    Text(
        title,
        color = Haven.accent,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 4.dp),
    )
}

@Composable
private fun Value(label: String, value: Float, range: ClosedFloatingPointRange<Float>, format: (Float) -> String, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Haven.text, fontSize = 13.sp, modifier = Modifier.width(132.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f).testTag("studio_${label.lowercase().replace(' ', '_')}"),
        )
        Text(format(value), color = Haven.textDim, fontSize = 12.sp, modifier = Modifier.width(56.dp).padding(start = 6.dp))
    }
}

@Composable
private fun <T> Choice(label: String, options: List<T>, selected: T, name: (T) -> String, onPick: (T) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
        Text(label, color = Haven.text, fontSize = 13.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { o ->
                FilterChip(selected = o == selected, onClick = { onPick(o) }, label = { Text(name(o), fontSize = 12.sp) })
            }
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Haven.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** True if [brush] is one of the user's own (so it can be deleted). */
fun isOwnBrush(brush: BrushPreset): Boolean = BrushLibrary.isCustom(brush.id)
