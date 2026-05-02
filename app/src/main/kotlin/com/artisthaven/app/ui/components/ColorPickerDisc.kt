package com.artisthaven.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ColorPickerDisc(
    visible: Boolean,
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val swatches = listOf(
        Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFFD32F2F), Color(0xFFF57C00),
        Color(0xFFFBC02D), Color(0xFF388E3C), Color(0xFF00796B), Color(0xFF0288D1),
        Color(0xFF303F9F), Color(0xFF7B1FA2), Color(0xFFC2185B), Color(0xFF5D4037),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Pick color", style = MaterialTheme.typography.titleMedium)

                for (rowStart in swatches.indices step 4) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        swatches.subList(rowStart, minOf(rowStart + 4, swatches.size)).forEach { color ->
                            val borderColor =
                                if (color == initialColor) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(color, CircleShape)
                                    .background(
                                        borderColor.copy(alpha = if (borderColor == Color.Transparent) 0f else 0.2f),
                                        CircleShape,
                                    )
                                    .clickable {
                                        onColorSelected(color)
                                        onDismiss()
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}

