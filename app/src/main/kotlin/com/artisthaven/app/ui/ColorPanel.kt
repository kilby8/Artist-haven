package com.artisthaven.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artisthaven.app.canvas.CanvasUiState
import com.artisthaven.app.ui.theme.Haven
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Colour picker: hue ring around a saturation/brightness square, hex entry, recent colours and a
 * saved palette (tap to use, long-press to remove).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ColorPanel(
    state: CanvasUiState,
    onColor: (color: Int, final: Boolean) -> Unit,
    onAddToPalette: (Int) -> Unit,
    onRemoveFromPalette: (Int) -> Unit,
) {
    // Keep HSV locally so the hue survives when saturation or brightness reach zero.
    var hsv by remember { mutableStateOf(toHsv(state.color)) }
    if (fromHsv(hsv) != (state.color or 0xFF000000.toInt())) hsv = toHsv(state.color)

    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 14.dp)) {
        PanelTitle("Colours")
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            HueRingPicker(
                hsv = hsv,
                onChange = { h, final ->
                    hsv = h
                    onColor(fromHsv(h), final)
                },
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Color(state.color)))
            Spacer(Modifier.width(12.dp))
            var text by remember(state.color) { mutableStateOf(toHex(state.color)) }
            OutlinedTextField(
                value = text,
                onValueChange = { v ->
                    text = v.take(7)
                    parseHex(text)?.let { onColor(it, false) }
                },
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = Haven.text),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { parseHex(text)?.let { onColor(it, true) } }),
                modifier = Modifier.width(130.dp).testTag("hex_field"),
                label = { Text("Hex") },
            )
        }
        if (state.recentColors.isNotEmpty()) {
            SectionLabel("Recent")
            SwatchRow(state.recentColors, onPick = { onColor(it, true) })
        }
        SectionLabel("Palette")
        FlowRow(
            Modifier.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            state.palette.forEach { c ->
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(c))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(7.dp))
                        .combinedClickable(onClick = { onColor(c, true) }, onLongClick = { onRemoveFromPalette(c) }),
                )
            }
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .border(1.dp, Haven.textDim, RoundedCornerShape(7.dp))
                    .combinedClickable(onClick = { onAddToPalette(state.color) })
                    .testTag("palette_add"),
                contentAlignment = Alignment.Center,
            ) { Text("+", color = Haven.text, fontSize = 20.sp) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Haven.textDim, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 6.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwatchRow(colors: List<Int>, onPick: (Int) -> Unit) {
    Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        colors.take(10).forEach { c ->
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    .combinedClickable(onClick = { onPick(c) }),
            )
        }
    }
}

private enum class Target { NONE, RING, SQUARE }

@Composable
private fun HueRingPicker(hsv: FloatArray, onChange: (FloatArray, Boolean) -> Unit) {
    val hueColors = remember { (0..6).map { Color.hsv((it * 60f) % 360f, 1f, 1f) } }
    var current by remember { mutableStateOf(hsv) }
    current = hsv
    Canvas(
        Modifier
            .size(250.dp)
            .testTag("color_wheel")
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val g = Geometry(size.width.toFloat(), size.height.toFloat())
                    val target = g.targetAt(down.position)
                    if (target == Target.NONE) return@awaitEachGesture
                    fun update(p: Offset, final: Boolean) {
                        val h = current.copyOf()
                        if (target == Target.RING) {
                            val a = Math.toDegrees(atan2((p.y - g.cy).toDouble(), (p.x - g.cx).toDouble())).toFloat()
                            h[0] = (a + 360f) % 360f
                        } else {
                            h[1] = ((p.x - g.sqLeft) / g.sqSize).coerceIn(0f, 1f)
                            h[2] = (1f - (p.y - g.sqTop) / g.sqSize).coerceIn(0f, 1f)
                        }
                        current = h
                        onChange(h, final)
                    }
                    down.consume()
                    update(down.position, false)
                    var last = down.position
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        last = change.position
                        change.consume()
                        if (!change.pressed) break
                        update(last, false)
                    }
                    update(last, true)
                }
            },
    ) {
        val g = Geometry(size.width, size.height)
        drawCircle(
            brush = Brush.sweepGradient(hueColors, center = Offset(g.cx, g.cy)),
            radius = (g.outer + g.inner) / 2f,
            center = Offset(g.cx, g.cy),
            style = Stroke(width = g.outer - g.inner),
        )
        val hueColor = Color.hsv(hsv[0], 1f, 1f)
        val tl = Offset(g.sqLeft, g.sqTop)
        val sz = Size(g.sqSize, g.sqSize)
        drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor), startX = g.sqLeft, endX = g.sqLeft + g.sqSize), tl, sz)
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = g.sqTop, endY = g.sqTop + g.sqSize), tl, sz)

        val ringR = (g.outer + g.inner) / 2f
        val ang = Math.toRadians(hsv[0].toDouble())
        val hp = Offset(g.cx + (cos(ang) * ringR).toFloat(), g.cy + (sin(ang) * ringR).toFloat())
        drawCircle(Color.White, radius = (g.outer - g.inner) / 2f, center = hp, style = Stroke(3.dp.toPx()))
        val sp = Offset(g.sqLeft + hsv[1] * g.sqSize, g.sqTop + (1f - hsv[2]) * g.sqSize)
        drawCircle(Color.Black.copy(alpha = 0.5f), radius = 9.dp.toPx(), center = sp, style = Stroke(4.dp.toPx()))
        drawCircle(Color.White, radius = 9.dp.toPx(), center = sp, style = Stroke(2.dp.toPx()))
    }
}

private class Geometry(w: Float, h: Float) {
    val cx = w / 2f
    val cy = h / 2f
    val outer = min(w, h) / 2f
    val inner = outer * 0.80f
    val sqSize = (inner - 6f) * sqrt(2f)
    val sqLeft = cx - sqSize / 2f
    val sqTop = cy - sqSize / 2f

    fun targetAt(p: Offset): Target {
        val d = hypot(p.x - cx, p.y - cy)
        return when {
            d in (inner - 4f)..(outer + 12f) -> Target.RING
            p.x in sqLeft - 12f..sqLeft + sqSize + 12f && p.y in sqTop - 12f..sqTop + sqSize + 12f -> Target.SQUARE
            else -> Target.NONE
        }
    }
}

fun toHsv(color: Int): FloatArray = FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) }
fun fromHsv(hsv: FloatArray): Int = android.graphics.Color.HSVToColor(hsv)
fun toHex(color: Int): String = "#%06X".format(color and 0xFFFFFF)
fun parseHex(text: String): Int? {
    val t = text.trim().removePrefix("#")
    if (t.length != 6) return null
    return t.toLongOrNull(16)?.let { (0xFF000000L or it).toInt() }
}
