package com.artisthaven.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.artisthaven.app.ui.ArtistHavenScreen
import com.artisthaven.app.ui.theme.ArtistHavenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArtistHavenTheme {
                ArtistHavenScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
