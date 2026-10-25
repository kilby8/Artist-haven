package com.artisthaven.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.artisthaven.app.canvas.CanvasUiState
import com.artisthaven.app.canvas.CanvasViewModel
import com.artisthaven.app.data.ExportFormat
import com.artisthaven.app.ui.theme.Haven
import com.artisthaven.engine.stroke.Symmetry
import kotlin.math.roundToInt

/** Export and share, canvas options, drawing guides and preferences. */
@Composable
fun ActionsPanel(state: CanvasUiState, vm: CanvasViewModel, onFit: () -> Unit, onShare: (Intent) -> Unit) {
    val context = LocalContext.current
    var renaming by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<ExportFormat?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val f = pendingSave
        pendingSave = null
        if (granted && f != null) vm.export(context, f, share = false, onShare = onShare)
        else if (!granted) vm.showMessage("Storage permission is needed to save to your gallery")
    }

    fun save(format: ExportFormat) {
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingSave = format
            permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            vm.export(context, format, share = false, onShare = onShare)
        }
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
        PanelTitle("Actions")

        Section("Save and share")
        ExportFormat.entries.forEach { f ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(f.label, color = Haven.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { save(f) }, enabled = !state.busy, modifier = Modifier.testTag("save_${f.name.lowercase()}")) {
                    Text("Save")
                }
                TextButton(
                    onClick = { vm.export(context, f, share = true, onShare = onShare) },
                    enabled = !state.busy,
                    modifier = Modifier.testTag("share_${f.name.lowercase()}"),
                ) { Text("Share") }
            }
        }

        Section("Canvas")
        Row(Modifier.padding(horizontal = 8.dp)) {
            TextButton(onClick = { renaming = true }) { Text("Rename") }
            TextButton(onClick = onFit) { Text("Fit to screen") }
            TextButton(onClick = { vm.saveNow(); vm.showMessage("Saved") }) { Text("Save now") }
        }
        Text(
            "${state.docWidth} × ${state.docHeight} px · ${state.host.layers.size} layers",
            color = Haven.textDim,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Section("Symmetry")
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Symmetry.entries.forEach { s ->
                FilterChip(
                    selected = state.symmetry == s,
                    onClick = { vm.setSymmetry(s) },
                    label = { Text(s.displayName) },
                    modifier = Modifier.testTag("symmetry_${s.name.lowercase()}"),
                )
            }
        }

        Section("Drawing")
        SwitchRow("Paint with fingers", "Off: fingers only zoom and pan once a pen is used", state.fingerPainting, vm::setFingerPainting)
        SliderRow("Extra smoothing", state.extraStreamline, { vm.setExtraStreamline(it) }, "${(state.extraStreamline * 100).roundToInt()}%")

        Section("Fill")
        SliderRow("Threshold", state.fillTolerance / 0.6f, { vm.setFillTolerance(it * 0.6f) }, "${(state.fillTolerance * 100).roundToInt()}%")
        SwitchRow("Use all layers", "Fill up to lines on other layers", state.fillAllLayers, vm::setFillAllLayers)

        Section("Gestures")
        Text(
            "Two-finger tap: undo · Three-finger tap: redo\n" +
                "Pinch: zoom and rotate · Two fingers drag: move\n" +
                "Touch and hold: pick a colour\n" +
                "Hold at the end of a line: straight line or shape",
            color = Haven.textDim,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }

    if (renaming) {
        var name by remember { mutableStateOf(state.projectName) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename artwork") },
            text = { OutlinedTextField(name, { name = it.take(60) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.rename(name); renaming = false }) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(text: String) {
    Text(text, color = Haven.textDim, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp))
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Haven.text, fontSize = 14.sp)
            Text(subtitle, color = Haven.textDim, fontSize = 11.sp)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(title: String, value: Float, onChange: (Float) -> Unit, readout: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Haven.text, fontSize = 14.sp, modifier = Modifier.width(110.dp))
        Slider(value = value, onValueChange = onChange, modifier = Modifier.weight(1f))
        Text(readout, color = Haven.textDim, fontSize = 12.sp, modifier = Modifier.width(44.dp).padding(start = 6.dp))
    }
}
