package com.plane.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.plane.tracker.ui.screens.TrackerScreen
import com.plane.tracker.ui.theme.PlaneTrackerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlaneTrackerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TrackerScreen()
                }
            }
        }
    }
}
