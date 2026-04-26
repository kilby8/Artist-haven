package com.artisthaven.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artisthaven.app.model.Layer
import com.artisthaven.app.ui.theme.AccentBlue
import com.artisthaven.app.ui.theme.AccentRed
import com.artisthaven.app.ui.theme.BorderGray
import com.artisthaven.app.ui.theme.CardBlack
import com.artisthaven.app.ui.theme.DividerGray
import com.artisthaven.app.ui.theme.GlassBackground
import com.artisthaven.app.ui.theme.GlassBorder
import com.artisthaven.app.ui.theme.GlassTint
import com.artisthaven.app.ui.theme.OLEDBlack
import com.artisthaven.app.ui.theme.TextPrimary
import com.artisthaven.app.ui.theme.TextSecondary

/**
 * Right-aligned sliding layer panel.
 *
 * Opens from the right edge of the screen with a slide+fade animation.
 * Contains a [LazyColumn] of layer rows with:
 *  - Thumbnail preview
 *  - Layer name
 *  - Visibility toggle
 *  - Delete button
 * Plus an "Add Layer" button at the bottom.
 */
@Composable
fun LayerDrawer(
    visible: Boolean,
    layers: List<Layer>,
    activeLayerIndex: Int,
    onDismiss: () -> Unit,
    onAddLayer: () -> Unit,
    onDeleteLayer: (Int) -> Unit,
    onToggleVisibility: (Int) -> Unit,
    onSelectLayer: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Scrim — tap outside to dismiss
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(role = Role.Button, onClickLabel = "Close layer panel") { onDismiss() }
        )
    }

    // Sliding panel from the right
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        modifier = modifier
    ) {
        val panelShape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(320.dp)
                .clip(panelShape)
                .background(GlassBackground)
                .border(width = 0.5.dp, color = GlassBorder, shape = panelShape)
                .systemBarsPadding()
                .padding(top = 8.dp, bottom = 16.dp)
        ) {
            // ── Header ───────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column {
                    Text(
                        text = "Layers",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${layers.size}",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(GlassTint)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // ── Divider ───────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(DividerGray)
            )

            // ── Layer list ────────────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(
                    items = layers,
                    key = { _, layer -> layer.id }
                ) { index, layer ->
                    LayerRow(
                        layer = layer,
                        isActive = index == activeLayerIndex,
                        onSelect = { onSelectLayer(index) },
                        onToggleVisibility = { onToggleVisibility(index) },
                        onDelete = { onDeleteLayer(index) },
                        canDelete = layers.size > 1
                    )
                }
            }

            // ── Add Layer button ──────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(DividerGray)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = "Add layer") { onAddLayer() }
                    .padding(vertical = 14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Add Layer",
                    fontSize = 14.sp,
                    color = AccentBlue,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun LayerRow(
    layer: Layer,
    isActive: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onToggleVisibility: () -> Unit,
    onDelete: () -> Unit
) {
    val rowBg = if (isActive) AccentBlue.copy(alpha = 0.12f) else Color.Transparent
    val rowBorder = if (isActive) AccentBlue.copy(alpha = 0.4f) else Color.Transparent
    val rowShape = RoundedCornerShape(10.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(rowShape)
            .background(rowBg)
            .border(width = 0.5.dp, color = rowBorder, shape = rowShape)
            .clickable(role = Role.Button, onClickLabel = "Select ${layer.name}") { onSelect() }
            .padding(8.dp)
    ) {
        // ── Thumbnail ─────────────────────────────────────────────────────
        LayerThumbnail(
            thumbnail = layer.thumbnail,
            isVisible = layer.isVisible,
            modifier = Modifier.size(width = 44.dp, height = 34.dp)
        )

        Spacer(Modifier.width(10.dp))

        // ── Name ──────────────────────────────────────────────────────────
        Text(
            text = layer.name,
            fontSize = 13.sp,
            color = if (layer.isVisible) TextPrimary else TextSecondary,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )

        // ── Visibility toggle ─────────────────────────────────────────────
        IconButton(
            onClick = onToggleVisibility,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = if (layer.isVisible) "Hide layer" else "Show layer",
                tint = if (layer.isVisible) AccentBlue else TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }

        // ── Delete button ─────────────────────────────────────────────────
        if (canDelete) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete layer",
                    tint = AccentRed.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun LayerThumbnail(
    thumbnail: ImageBitmap?,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (isVisible) CardBlack else OLEDBlack)
            .border(width = 0.5.dp, color = BorderGray, shape = shape),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (!isVisible) Modifier.alpha(0.35f) else Modifier)
            )
        } else {
            // Placeholder checkerboard pattern hint
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CardBlack)
            )
        }
    }
}
