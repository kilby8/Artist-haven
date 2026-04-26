package com.artisthaven.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artisthaven.app.ui.theme.GlassBackground
import com.artisthaven.app.ui.theme.GlassBorder
import com.artisthaven.app.ui.theme.TextPrimary
import com.artisthaven.app.ui.theme.TextSecondary
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * HSV "Disc" colour picker popup.
 *
 * Layout (from outer to inner):
 *  1. Hue ring   — drag around the ring to select Hue (0°–360°).
 *  2. SV square  — drag inside the central square to select Saturation (x) and Value/Brightness (y).
 *  3. Colour preview circle at the bottom showing the current picked colour.
 *
 * [visible] drives the pop-in/pop-out animation.
 * Tap outside the panel (on the scrim) dismisses the picker.
 */
@Composable
fun ColorPickerDisc(
    visible: Boolean,
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    // Decompose the initial colour into HSV
    val initialHsv = remember(initialColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), hsv)
        hsv
    }

    var hue by remember(initialColor) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(initialColor) { mutableFloatStateOf(initialHsv[1]) }
    var value by remember(initialColor) { mutableFloatStateOf(initialHsv[2]) }

    val pickedColor by remember(hue, saturation, value) {
        mutableStateOf(
            Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
        )
    }

    // Scrim
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(role = Role.Button, onClickLabel = "Close colour picker") { onDismiss() }
        )
    }

    // Disc panel
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(initialScale = 0.7f) + fadeIn(),
        exit = scaleOut(targetScale = 0.7f) + fadeOut()
    ) {
        val panelShape = RoundedCornerShape(24.dp)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(panelShape)
                .background(GlassBackground)
                .border(width = 0.5.dp, color = GlassBorder, shape = panelShape)
                .padding(24.dp)
        ) {
            Text(
                text = "Colour",
                fontSize = 15.sp,
                color = TextPrimary
            )

            Spacer(Modifier.height(16.dp))

            // ── Hue + SV Disc ──────────────────────────────────────────────
            HueSaturationValueDisc(
                hue = hue,
                saturation = saturation,
                value = value,
                onHueChange = { hue = it },
                onSaturationValueChange = { s, v ->
                    saturation = s
                    value = v
                    onColorSelected(
                        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, s, v)))
                    )
                }
            )

            Spacer(Modifier.height(20.dp))

            // ── Colour preview ─────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(pickedColor)
                    .border(width = 2.dp, color = GlassBorder, shape = CircleShape)
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "#%06X".format(pickedColor.toArgb() and 0xFFFFFF),
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }
}

/**
 * Renders the two-zone colour disc:
 *  - An outer hue ring (sweep gradient).
 *  - An inner square for Saturation × Value, painted on-canvas via a
 *    two-pass gradient (white→pure-hue horizontally, then top×alpha black vertically).
 */
@Composable
private fun HueSaturationValueDisc(
    hue: Float,
    saturation: Float,
    value: Float,
    onHueChange: (Float) -> Unit,
    onSaturationValueChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val discSize = 240.dp
    val ringFraction = 0.15f   // Hue ring takes this fraction of the radius

    Canvas(
        modifier = modifier
            .size(discSize)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    handleDiscTouch(
                        position = change.position,
                        canvasSize = size,
                        ringFraction = ringFraction,
                        onHueChange = onHueChange,
                        onSaturationValueChange = onSaturationValueChange
                    )
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { position ->
                    handleDiscTouch(
                        position = position,
                        canvasSize = size,
                        ringFraction = ringFraction,
                        onHueChange = onHueChange,
                        onSaturationValueChange = onSaturationValueChange
                    )
                }
            }
    ) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val ringWidth = radius * ringFraction

        // ── Hue ring (sweep gradient) ───────────────────────────────────
        val hueColors = buildList {
            repeat(361) { deg ->
                add(Color(android.graphics.Color.HSVToColor(floatArrayOf(deg.toFloat(), 1f, 1f))))
            }
        }
        drawCircle(
            brush = Brush.sweepGradient(hueColors),
            radius = radius,
            center = center,
            style = Stroke(width = ringWidth * 2f)
        )

        // ── Inner SV square ─────────────────────────────────────────────
        val innerRadius = radius - ringWidth * 2f
        val sqHalf = innerRadius * 0.68f           // square inscribed in the inner circle
        val sqTop = center.y - sqHalf
        val sqLeft = center.x - sqHalf
        val sqSize = Size(sqHalf * 2f, sqHalf * 2f)

        val pureHue = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))

        // Horizontal gradient: White → Pure hue
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.White, pureHue),
                startX = sqLeft,
                endX = sqLeft + sqHalf * 2f
            ),
            topLeft = Offset(sqLeft, sqTop),
            size = sqSize
        )
        // Vertical gradient: Transparent → Black (shadows the bottom)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startY = sqTop,
                endY = sqTop + sqHalf * 2f
            ),
            topLeft = Offset(sqLeft, sqTop),
            size = sqSize
        )

        // ── Hue thumb on ring ────────────────────────────────────────────
        val hueAngleRad = (hue - 90f) * (PI / 180f).toFloat()
        val ringMidRadius = radius - ringWidth
        val hueThumbCenter = Offset(
            center.x + ringMidRadius * cos(hueAngleRad),
            center.y + ringMidRadius * sin(hueAngleRad)
        )
        drawCircle(color = Color.White, radius = 10.dp.toPx(), center = hueThumbCenter)
        drawCircle(
            color = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))),
            radius = 8.dp.toPx(),
            center = hueThumbCenter
        )

        // ── SV thumb inside the square ───────────────────────────────────
        val svThumbX = sqLeft + saturation * sqHalf * 2f
        val svThumbY = sqTop + (1f - value) * sqHalf * 2f
        drawCircle(color = Color.White, radius = 9.dp.toPx(), center = Offset(svThumbX, svThumbY))
        drawCircle(
            color = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))),
            radius = 7.dp.toPx(),
            center = Offset(svThumbX, svThumbY)
        )
    }
}

/** Determines whether a touch is in the hue ring or SV square and delegates accordingly. */
private fun handleDiscTouch(
    position: Offset,
    canvasSize: IntSize,
    ringFraction: Float,
    onHueChange: (Float) -> Unit,
    onSaturationValueChange: (Float, Float) -> Unit
) {
    val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
    val radius = canvasSize.width.coerceAtMost(canvasSize.height) / 2f
    val ringWidth = radius * ringFraction

    val dx = position.x - center.x
    val dy = position.y - center.y
    val dist = hypot(dx, dy)

    if (dist in (radius - ringWidth * 2f)..(radius + 4f)) {
        // Hue ring
        var angle = atan2(dy, dx) * (180f / PI.toFloat()) + 90f
        if (angle < 0f) angle += 360f
        if (angle >= 360f) angle -= 360f
        onHueChange(angle)
    } else {
        // SV square
        val innerRadius = radius - ringWidth * 2f
        val sqHalf = innerRadius * 0.68f
        val sqLeft = center.x - sqHalf
        val sqTop = center.y - sqHalf
        val sqSize = sqHalf * 2f

        val s = ((position.x - sqLeft) / sqSize).coerceIn(0f, 1f)
        val v = (1f - (position.y - sqTop) / sqSize).coerceIn(0f, 1f)
        onSaturationValueChange(s, v)
    }
}
