package com.artisthaven.app.presentation.splash

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// Smooth ease-out for the fade
private val EaseOut = Easing { t -> 1f - (1f - t) * (1f - t) }

/**
 * Splash screen that fades in the "For Grace" dedication and Psalm 20:4 over 2 seconds,
 * holds for 4 seconds total, then calls [onFinished].
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var startFade by remember { mutableStateOf(false) }

    // Opacity of the main content
    val alpha by animateFloatAsState(
        targetValue = if (startFade) 1f else 0f,
        animationSpec = tween(durationMillis = 2000, easing = EaseOut),
        label = "splash_fade",
    )

    // Subtle upward drift as content fades in
    val offsetY by animateFloatAsState(
        targetValue = if (startFade) 0f else 24f,
        animationSpec = tween(durationMillis = 2200, easing = EaseOut),
        label = "splash_drift",
    )

    LaunchedEffect(Unit) {
        startFade = true            // trigger fade animation
        delay(4_000)                // hold screen for 4 s
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212)),
        contentAlignment = Alignment.Center,
    ) {
        // ── Centred text block ──────────────────────────────────────────
        Column(
            modifier = Modifier
                .padding(horizontal = 40.dp)
                .graphicsLayer {
                    this.alpha = alpha
                    this.translationY = offsetY
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "For Grace",
                fontSize = 46.sp,
                fontWeight = FontWeight.Light,
                fontStyle = FontStyle.Italic,
                color = Color.White,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "\u201cMay he give you the desire of your heart\n" +
                       "and make all your plans succeed.\u201d",
                fontSize = 14.sp,
                fontWeight = FontWeight.Light,
                color = Color(0xFFA8A8A8),
                letterSpacing = 1.5.sp,
                lineHeight = 24.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Psalm 20:4",
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF666666),
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
            )
        }

        // ── Loading indicator at bottom ─────────────────────────────────
        CircularProgressIndicator(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .size(20.dp),
            color = Color(0xFF444444),
            strokeWidth = 2.dp,
            strokeCap = StrokeCap.Round,
        )
    }
}


