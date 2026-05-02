package com.artisthaven.app.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// ─── HSV ↔ Color helpers ───────────────────────────────────────────────────────

private fun Color.toHsvArray(): FloatArray {
    val hsv = FloatArray(3)
    AndroidColor.RGBToHSV(
        (red * 255).toInt().coerceIn(0, 255),
        (green * 255).toInt().coerceIn(0, 255),
        (blue * 255).toInt().coerceIn(0, 255),
        hsv,
    )
    return hsv
}

private fun hsvToColor(hue: Float, sat: Float, value: Float, alpha: Float = 1f): Color =
    Color(AndroidColor.HSVToColor((alpha * 255f).toInt().coerceIn(0, 255), floatArrayOf(hue, sat, value)))

private fun Color.toHexString(): String = "%06X".format(toArgb() and 0x00FFFFFF)

// ─── Preset art palettes ───────────────────────────────────────────────────────

private val PRESET_PALETTES: List<Pair<String, List<Color>>> = listOf(
    "Neutrals" to listOf(
        0xFF000000, 0xFF111111, 0xFF222222, 0xFF333333, 0xFF555555,
        0xFF777777, 0xFF999999, 0xFFBBBBBB, 0xFFCCCCCC, 0xFFDDDDDD,
        0xFFEEEEEE, 0xFFF5F5F5, 0xFFFFFFFF,
    ),
    "Reds" to listOf(
        0xFF8B0000, 0xFFB71C1C, 0xFFD32F2F, 0xFFF44336, 0xFFE57373,
        0xFFFFCDD2, 0xFFC62828, 0xFFFF6B6B, 0xFFFF8A80, 0xFFFFE4E1,
    ),
    "Oranges & Yellows" to listOf(
        0xFFBF360C, 0xFFE64A19, 0xFFFF5722, 0xFFFF8A65, 0xFFFFCCBC,
        0xFFF57F17, 0xFFF9A825, 0xFFFDD835, 0xFFFFF176, 0xFFFFF9C4,
    ),
    "Greens" to listOf(
        0xFF1B5E20, 0xFF2E7D32, 0xFF388E3C, 0xFF66BB6A, 0xFFC8E6C9,
        0xFF004D40, 0xFF00695C, 0xFF00897B, 0xFF80CBC4, 0xFFE0F2F1,
    ),
    "Blues" to listOf(
        0xFF0D47A1, 0xFF1565C0, 0xFF1976D2, 0xFF64B5F6, 0xFFBBDEFB,
        0xFF006064, 0xFF00838F, 0xFF00ACC1, 0xFF80DEEA, 0xFFE0F7FA,
    ),
    "Purples & Pinks" to listOf(
        0xFF4A148C, 0xFF6A1B9A, 0xFF7B1FA2, 0xFFCE93D8, 0xFFF3E5F5,
        0xFF880E4F, 0xFFC2185B, 0xFFE91E63, 0xFFF48FB1, 0xFFFCE4EC,
    ),
    "Skin Tones" to listOf(
        0xFFFFDFC4, 0xFFF0C27F, 0xFFD4956A, 0xFFAD7049, 0xFF7D4B2A,
        0xFFFFD5B8, 0xFFEBB98A, 0xFFCD8E5F, 0xFF9B6342, 0xFF5E3422,
        0xFFFDE3C8, 0xFFF5CBA7, 0xFF3B1F08,
    ),
    "Earth & Brown" to listOf(
        0xFF3E2723, 0xFF4E342E, 0xFF6D4C41, 0xFF8D6E63, 0xFFBCAAA4,
        0xFF795548, 0xFFA1887F, 0xFFD7CCC8, 0xFF5D4037, 0xFF4E342E,
    ),
    "Warm Shadows" to listOf(
        0xFF2C1810, 0xFF4A2813, 0xFF6B3A20, 0xFF8B5532, 0xFFAA7744,
        0xFF3D2B1F, 0xFF5C3D2E, 0xFF7A5444, 0xFF9B7B6B, 0xFFBCA9A2,
    ),
    "Cool Shadows" to listOf(
        0xFF0A1628, 0xFF152642, 0xFF1E3A5F, 0xFF2B5282, 0xFF3D70B5,
        0xFF172B3A, 0xFF213D52, 0xFF2E5770, 0xFF4A7A9B, 0xFF7AB3CE,
    ),
).map { (name, longs) -> name to longs.map { Color(it.toInt() or 0xFF000000.toInt()) } }

// ─── Main composable ───────────────────────────────────────────────────────────

/**
 * Professional-grade color picker:
 * • Draggable 2D saturation/value square
 * • Draggable hue bar
 * • Draggable alpha/opacity bar with checkerboard
 * • Live hex input
 * • R G B H numeric readout
 * • Before / after preview swatch
 * • 100+ curated artist palette swatches across 10 families
 */
@Composable
fun ColorPickerDisc(
    visible: Boolean,
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val initHsv = remember(initialColor) { initialColor.toHsvArray() }
    var hue   by remember(initialColor) { mutableStateOf(initHsv[0]) }
    var sat   by remember(initialColor) { mutableStateOf(initHsv[1]) }
    var value by remember(initialColor) { mutableStateOf(initHsv[2]) }
    var alpha by remember(initialColor) { mutableStateOf(initialColor.alpha) }

    val currentColor by remember { derivedStateOf { hsvToColor(hue, sat, value, alpha) } }

    var hexText by remember { mutableStateOf(initialColor.toHexString()) }
    var hexBusy by remember { mutableStateOf(false) }
    LaunchedEffect(hue, sat, value) {
        if (!hexBusy) hexText = hsvToColor(hue, sat, value).toHexString()
    }

    var tab by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── Header: title + before/after ─────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Color Picker",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(initialColor).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                        Text("→", fontSize = 14.sp)
                        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(currentColor).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape))
                    }
                }

                // ── Tabs ─────────────────────────────────────────────────
                @OptIn(ExperimentalMaterial3Api::class)
                PrimaryTabRow(selectedTabIndex = tab, modifier = Modifier.fillMaxWidth()) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Custom") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Palettes") })
                }

                if (tab == 0) {
                    // ── Saturation/Value square ───────────────────────────
                    SatValuePicker(
                        hue = hue, saturation = sat, value = value,
                        onSVChange = { s, v -> sat = s; value = v },
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    )

                    // ── Hue bar ───────────────────────────────────────────
                    Text("Hue", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HueBar(hue = hue, onHueChange = { hue = it }, modifier = Modifier.fillMaxWidth().height(28.dp))

                    // ── Alpha bar ─────────────────────────────────────────
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Opacity", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${(alpha * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                    }
                    AlphaBar(alpha = alpha, color = currentColor, onAlphaChange = { alpha = it }, modifier = Modifier.fillMaxWidth().height(28.dp))

                    // ── Hex input ─────────────────────────────────────────
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = "#$hexText",
                            onValueChange = { raw ->
                                hexBusy = true
                                val clean = raw.removePrefix("#").filter { it.isLetterOrDigit() }.take(6).uppercase()
                                hexText = clean
                                if (clean.length == 6) {
                                    runCatching {
                                        val rgb = clean.toLong(16).toInt()
                                        val parsed = Color(0xFF000000.toInt() or rgb)
                                        val hsv = parsed.toHsvArray()
                                        hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                                    }
                                }
                                hexBusy = false
                            },
                            label = { Text("Hex") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        )
                        // Quick-apply swatch
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(currentColor)
                                .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                                .clickable { onColorSelected(currentColor); onDismiss() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "✓",
                                color = if (value < 0.5f) Color.White else Color.Black,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    // ── RGBH readout ──────────────────────────────────────
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        RgbChip("R", (currentColor.red * 255).toInt(), Color(1f, 0.3f, 0.3f, 0.2f))
                        RgbChip("G", (currentColor.green * 255).toInt(), Color(0.2f, 0.8f, 0.2f, 0.2f))
                        RgbChip("B", (currentColor.blue * 255).toInt(), Color(0.2f, 0.5f, 1f, 0.2f))
                        RgbChip("H°", hue.toInt(), Color(1f, 0.8f, 0.2f, 0.2f))
                    }

                } else {
                    // ── Preset palettes ────────────────────────────────────
                    PRESET_PALETTES.forEach { (name, colors) ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        colors.chunked(10).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
                                row.forEach { sw ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(sw)
                                            .border(
                                                width = if (currentColor == sw) 2.dp else 0.5.dp,
                                                color = if (currentColor == sw) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                                shape = RoundedCornerShape(4.dp),
                                            )
                                            .clickable {
                                                val hsv = sw.toHsvArray()
                                                hue = hsv[0]; sat = hsv[1]; value = hsv[2]; alpha = sw.alpha
                                            },
                                    )
                                }
                                repeat(10 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }

                // ── Buttons ───────────────────────────────────────────────
                HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onColorSelected(currentColor); onDismiss() }) { Text("Apply") }
                }
            }
        }
    }
}

// ─── 2D Saturation/Value picker ───────────────────────────────────────────────

@Composable
private fun SatValuePicker(
    hue: Float,
    saturation: Float,
    value: Float,
    onSVChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hueColor = remember(hue) { hsvToColor(hue, 1f, 1f) }
    var sz by remember { mutableStateOf(IntSize.Zero) }

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .onSizeChanged { sz = it }
            .pointerInput(sz) {
                detectDragGestures(
                    onDragStart = { o ->
                        if (sz.width > 0) onSVChange((o.x / sz.width).coerceIn(0f, 1f), 1f - (o.y / sz.height).coerceIn(0f, 1f))
                    },
                    onDrag = { c, _ ->
                        if (sz.width > 0) onSVChange((c.position.x / sz.width).coerceIn(0f, 1f), 1f - (c.position.y / sz.height).coerceIn(0f, 1f))
                    },
                )
            }
            .pointerInput(sz) {
                detectTapGestures { o ->
                    if (sz.width > 0) onSVChange((o.x / sz.width).coerceIn(0f, 1f), 1f - (o.y / sz.height).coerceIn(0f, 1f))
                }
            },
    ) {
        drawRect(brush = Brush.horizontalGradient(listOf(Color.White, hueColor)))
        drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val ix = saturation * size.width
        val iy = (1f - value) * size.height
        drawCircle(Color.White, radius = 13f, center = Offset(ix, iy), style = Stroke(3f))
        drawCircle(Color.Black, radius = 13f, center = Offset(ix, iy), style = Stroke(1f))
        drawCircle(hsvToColor(hue, saturation, value), radius = 10f, center = Offset(ix, iy))
    }
}

// ─── Hue bar ──────────────────────────────────────────────────────────────────

@Composable
private fun HueBar(hue: Float, onHueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val gradient = remember { Brush.horizontalGradient((0..12).map { hsvToColor(it * 30f, 1f, 1f) }) }
    var bw by remember { mutableStateOf(1) }

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .onSizeChanged { bw = it.width }
            .pointerInput(bw) {
                detectDragGestures(
                    onDragStart = { o -> onHueChange((o.x / bw * 360f).coerceIn(0f, 359.9f)) },
                    onDrag = { c, _ -> onHueChange((c.position.x / bw * 360f).coerceIn(0f, 359.9f)) },
                )
            }
            .pointerInput(bw) { detectTapGestures { o -> onHueChange((o.x / bw * 360f).coerceIn(0f, 359.9f)) } },
    ) {
        drawRect(brush = gradient)
        val cy = size.height / 2f; val r = cy - 2f
        val x = (hue / 360f * size.width).coerceIn(r, size.width - r)
        drawCircle(Color.White, radius = r + 2f, center = Offset(x, cy), style = Stroke(3f))
        drawCircle(Color.Black, radius = r + 2f, center = Offset(x, cy), style = Stroke(1f))
        drawCircle(hsvToColor(hue, 1f, 1f), radius = r - 1f, center = Offset(x, cy))
    }
}

// ─── Alpha bar ────────────────────────────────────────────────────────────────

@Composable
private fun AlphaBar(alpha: Float, color: Color, onAlphaChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    var bw by remember { mutableStateOf(1) }

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .onSizeChanged { bw = it.width }
            .pointerInput(bw) {
                detectDragGestures(
                    onDragStart = { o -> onAlphaChange((o.x / bw).coerceIn(0f, 1f)) },
                    onDrag = { c, _ -> onAlphaChange((c.position.x / bw).coerceIn(0f, 1f)) },
                )
            }
            .pointerInput(bw) { detectTapGestures { o -> onAlphaChange((o.x / bw).coerceIn(0f, 1f)) } },
    ) {
        // Checkerboard
        val tile = size.height
        var col = 0; var cx = 0f
        while (cx < size.width) {
            drawRect(color = if (col % 2 == 0) Color(0xFFCCCCCC) else Color.White, topLeft = Offset(cx, 0f), size = Size(tile, tile))
            cx += tile; col++
        }
        drawRect(brush = Brush.horizontalGradient(listOf(color.copy(alpha = 0f), color.copy(alpha = 1f))))
        val cy = size.height / 2f; val r = cy - 2f
        val x = (alpha * size.width).coerceIn(r, size.width - r)
        drawCircle(Color.White, radius = r + 2f, center = Offset(x, cy), style = Stroke(3f))
        drawCircle(Color.Black, radius = r + 2f, center = Offset(x, cy), style = Stroke(1f))
        drawCircle(color.copy(alpha = alpha), radius = r - 1f, center = Offset(x, cy))
    }
}

// ─── RGB chip ─────────────────────────────────────────────────────────────────

@Composable
private fun RgbChip(label: String, channelValue: Int, bgColor: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = bgColor) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            Text("$channelValue", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
    }
}
