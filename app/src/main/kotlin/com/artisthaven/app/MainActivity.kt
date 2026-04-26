package com.artisthaven.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.artisthaven.app.presentation.canvas.DrawingScreen
import com.artisthaven.app.ui.theme.ArtistHavenTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArtistHavenTheme {
                DrawingScreen()
            }
        }
    }
}
