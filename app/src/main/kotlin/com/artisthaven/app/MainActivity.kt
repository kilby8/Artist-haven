package com.artisthaven.app

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artisthaven.app.canvas.CanvasViewModel
import com.artisthaven.app.data.ProjectStore
import com.artisthaven.app.ui.CanvasScreen
import com.artisthaven.app.ui.GalleryScreen
import com.artisthaven.app.ui.theme.ArtistHavenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArtistHavenTheme { App() }
        }
    }
}

@Composable
fun App() {
    val context = LocalContext.current
    val store = remember { ProjectStore(context.applicationContext) }
    var openProject by rememberSaveable { mutableStateOf<String?>(null) }

    val id = openProject
    if (id == null) {
        GalleryScreen(store, onOpen = { openProject = it })
    } else {
        // Each open artwork gets its own ViewModel store, cleared when you go back to the gallery,
        // so reopening an artwork always starts a fresh engine.
        val owner = remember(id) {
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        }
        DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
        val vm: CanvasViewModel = viewModel(
            viewModelStoreOwner = owner,
            key = id,
            factory = CanvasViewModel.Factory(context.applicationContext as Application, id),
        )
        CanvasScreen(vm, onExit = { openProject = null })
    }
}
