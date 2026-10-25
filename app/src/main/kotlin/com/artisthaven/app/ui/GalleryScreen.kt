package com.artisthaven.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artisthaven.app.data.ProjectStore
import com.artisthaven.app.data.ProjectSummary
import com.artisthaven.app.ui.theme.Haven
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.max
import kotlin.math.min

/** A canvas size offered when starting a new artwork. */
data class CanvasPreset(val name: String, val width: Int, val height: Int)

/** Largest canvas side we allow, to stay inside phone memory with undo history. */
const val MAX_CANVAS_SIDE = 4096

/** Your artworks, newest first, and the button to start a new one. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(store: ProjectStore, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var projects by remember { mutableStateOf<List<ProjectSummary>?>(null) }
    var thumbs by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var reload by remember { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ProjectSummary?>(null) }

    LaunchedEffect(reload) {
        val list = withContext(Dispatchers.IO) {
            store.purgeTrash()
            store.list()
        }
        projects = list
        val loaded = withContext(Dispatchers.IO) {
            list.associate { p -> p.id to store.loadThumbnail(p.id) }.filterValues { it != null }.mapValues { it.value!! }
        }
        thumbs = loaded
    }

    val screenPreset = remember {
        val dm = context.resources.displayMetrics
        CanvasPreset("Screen size", min(dm.widthPixels, dm.heightPixels), max(dm.widthPixels, dm.heightPixels))
    }

    Box(Modifier.fillMaxSize().background(Haven.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Gallery", color = Haven.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            }
            val list = projects
            if (list != null && list.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No artworks yet", color = Haven.text, fontSize = 18.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Tap New canvas to start drawing.", color = Haven.textDim)
                }
            } else if (list != null) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.testTag("gallery_grid"),
                ) {
                    items(list, key = { it.id }) { p ->
                        var menu by remember { mutableStateOf(false) }
                        Column(
                            Modifier
                                .combinedClickable(onClick = { onOpen(p.id) }, onLongClick = { menu = true })
                                .testTag("artwork_${p.name}"),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.8f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Haven.panelRaised),
                                contentAlignment = Alignment.Center,
                            ) {
                                thumbs[p.id]?.let {
                                    Image(it.asImageBitmap(), p.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(6.dp))
                                }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = p })
                                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = {
                                        menu = false
                                        scope.launch {
                                            withContext(Dispatchers.IO) { store.duplicate(p.id) }
                                            reload++
                                        }
                                    })
                                    DropdownMenuItem(text = { Text("Delete", color = Haven.danger) }, onClick = {
                                        menu = false
                                        scope.launch {
                                            val ok = withContext(Dispatchers.IO) { store.delete(p.id) }
                                            reload++
                                            if (ok) {
                                                val r = snackbar.showSnackbar("Deleted \"${p.name}\"", actionLabel = "Undo", duration = SnackbarDuration.Long)
                                                if (r == SnackbarResult.ActionPerformed) {
                                                    withContext(Dispatchers.IO) { store.undelete(p.id) }
                                                    reload++
                                                }
                                            }
                                        }
                                    })
                                }
                            }
                            Text(
                                p.name,
                                color = Haven.text,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            Text(
                                "${p.width} × ${p.height} · ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(p.modifiedAt))}",
                                color = Haven.textDim,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("New canvas") },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp).testTag("new_canvas"),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    if (creating) {
        NewCanvasDialog(
            presets = listOf(
                screenPreset,
                CanvasPreset("Square", 2048, 2048),
                CanvasPreset("A4 (150 dpi)", 1240, 1754),
                CanvasPreset("Phone wallpaper", 1440, 3200),
                CanvasPreset("4K", 3840, 2160),
            ),
            onDismiss = { creating = false },
            onCreate = { preset ->
                creating = false
                scope.launch {
                    val id = withContext(Dispatchers.IO) {
                        val n = (projects?.size ?: 0) + 1
                        store.create("Untitled $n", preset.width, preset.height, 0xFFFFFFFF.toInt())
                    }
                    onOpen(id)
                }
            },
        )
    }

    renaming?.let { p ->
        var name by remember(p.id) { mutableStateOf(p.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename artwork") },
            text = { OutlinedTextField(name, { name = it.take(60) }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.trim()
                    renaming = null
                    if (n.isNotEmpty()) scope.launch {
                        withContext(Dispatchers.IO) { store.rename(p.id, n) }
                        reload++
                    }
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NewCanvasDialog(presets: List<CanvasPreset>, onDismiss: () -> Unit, onCreate: (CanvasPreset) -> Unit) {
    var w by remember { mutableStateOf("") }
    var h by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New canvas") },
        text = {
            Column {
                presets.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onCreate(p) }
                            .padding(vertical = 10.dp, horizontal = 4.dp)
                            .testTag("preset_${p.name}"),
                    ) {
                        Text(p.name, modifier = Modifier.weight(1f))
                        Text("${p.width} × ${p.height}", color = Haven.textDim)
                    }
                }
                Text("Custom size", color = Haven.textDim, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        w, { w = it.filter(Char::isDigit).take(4) },
                        label = { Text("Width") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(110.dp),
                    )
                    Text(" × ", color = Haven.textDim)
                    OutlinedTextField(
                        h, { h = it.filter(Char::isDigit).take(4) },
                        label = { Text("Height") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(110.dp),
                    )
                }
                Text("Up to $MAX_CANVAS_SIDE px a side", color = Haven.textDim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = {
            val cw = w.toIntOrNull()
            val ch = h.toIntOrNull()
            val valid = cw != null && ch != null && cw in 64..MAX_CANVAS_SIDE && ch in 64..MAX_CANVAS_SIDE
            TextButton(onClick = { onCreate(CanvasPreset("Custom", cw!!, ch!!)) }, enabled = valid) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
