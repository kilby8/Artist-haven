package com.artisthaven.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artisthaven.app.canvas.BrushPreviews
import com.artisthaven.app.canvas.CanvasUiState
import com.artisthaven.app.canvas.ToolMode
import com.artisthaven.app.ui.theme.Haven
import com.artisthaven.engine.brush.BrushCategory
import com.artisthaven.engine.brush.BrushLibrary
import com.artisthaven.engine.brush.BrushPreset

/**
 * Brush library: categories on the left, live stroke previews on the right. Long-press a brush (or
 * tap "Make my own") to open it in the Brush Studio.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrushLibraryPanel(
    state: CanvasUiState,
    onSelect: (BrushPreset) -> Unit,
    onEdit: (BrushPreset) -> Unit,
    onImport: (Uri) -> Unit,
) {
    val current = state.activeBrush
    var category by remember { mutableStateOf(current.category) }
    // Photoshop brushes (.abr) and shared brush files have no standard type, so allow any file.
    val pickBrushes = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            category = BrushCategory.MINE
            onImport(uri)
        }
    }
    val previews by BrushPreviews.previews.collectAsState()

    Column {
        val title = when (state.paintingTool) {
            ToolMode.SMUDGE -> "Smudge with"
            ToolMode.ERASER -> "Erase with"
            else -> "Brush library"
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { PanelTitle(title) }
            TextButton(onClick = { pickBrushes.launch(arrayOf("*/*")) }, enabled = !state.busy, modifier = Modifier.testTag("brush_import")) {
                Text("Import", color = Haven.accent, fontSize = 13.sp)
            }
            // Opens the Brush Studio on the current brush; built-in brushes are copied first.
            TextButton(onClick = { onEdit(current) }, modifier = Modifier.padding(end = 6.dp).testTag("brush_edit")) {
                Text(if (BrushLibrary.isCustom(current.id)) "Edit brush" else "Make my own", color = Haven.accent, fontSize = 13.sp)
            }
        }
        Row(Modifier.height(440.dp)) {
            LazyColumn(Modifier.width(118.dp).padding(start = 8.dp)) {
                items(BrushCategory.entries) { c ->
                    val sel = c == category
                    Text(
                        c.displayName,
                        color = if (sel) Haven.text else Haven.textDim,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (sel) Haven.accentSoft else Haven.panel.copy(alpha = 0f))
                            .clickable { category = c }
                            .padding(horizontal = 10.dp, vertical = 9.dp)
                            .testTag("category_${c.name.lowercase()}"),
                    )
                }
            }
            val brushes = remember(category, state.brushesVersion) { BrushLibrary.inCategory(category) }
            if (brushes.isEmpty()) {
                Text(
                    "Brushes you make appear here. Pick any brush and tap \"Make my own\" to start from it, " +
                        "or tap Import to add Photoshop brushes (.abr).",
                    color = Haven.textDim,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(12.dp),
                )
            }
            LazyColumn(Modifier.padding(horizontal = 8.dp)) {
                items(brushes, key = { it.id }) { brush ->
                    val sel = brush.id == current.id
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (sel) Haven.accentSoft else Haven.panelRaised)
                            .combinedClickable(onClick = { onSelect(brush) }, onLongClick = { onEdit(brush) })
                            .padding(8.dp)
                            .testTag("brush_${brush.id}"),
                    ) {
                        Text(brush.name, color = Haven.text, fontSize = 13.sp)
                        brush.importedFrom?.let { Text("from $it", color = Haven.textDim, fontSize = 11.sp) }
                        Box(Modifier.fillMaxWidth().height(48.dp)) {
                            previews[brush.id]?.let { bmp ->
                                Image(
                                    bmp.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(6.dp)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PanelTitle(text: String) {
    Text(
        text,
        color = Haven.text,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp),
    )
}
