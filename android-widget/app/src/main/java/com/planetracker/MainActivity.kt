package com.planetracker

import android.Manifest
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.planetracker.widget.PlaneTrackerWorker

class MainActivity : ComponentActivity() {

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("plane_tracker", MODE_PRIVATE)
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.any { it.value }
        if (granted) {
            PlaneTrackerWorker.refreshNow(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request location permissions
        requestLocationPermissions()

        setContent {
            PlaneTrackerTheme {
                MainScreen()
            }
        }
    }

    private fun requestLocationPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        locationPermissionRequest.launch(permissions.toTypedArray())
    }

    @Composable
    private fun MainScreen() {
        val panelBg = Color(0xFF141416)
        val goldAccent = Color(0xFFD4A847)
        val textPrimary = Color(0xFFF2EBD9)
        val textDim = Color(0xFF8C877A)
        val flapBg = Color(0xFF1E1E21)

        var useFahrenheit by remember {
            mutableStateOf(prefs.getBoolean("use_fahrenheit", false))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(panelBg)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "✈ PLANE TRACKER",
                color = goldAccent,
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Instructions card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(flapBg, RoundedCornerShape(8.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Add Widget to Home Screen",
                    color = textPrimary,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Long-press your home screen, tap Widgets, then find Plane Tracker to add it.",
                    color = textDim,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Available in Small, Medium, and Large sizes.",
                    color = textDim,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Temperature unit toggle
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(flapBg, RoundedCornerShape(8.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Temperature Unit",
                    color = textDim,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UnitButton(
                        label = "°C",
                        selected = !useFahrenheit,
                        goldAccent = goldAccent,
                        textPrimary = textPrimary,
                        flapBg = flapBg,
                        onClick = {
                            useFahrenheit = false
                            prefs.edit().putBoolean("use_fahrenheit", false).apply()
                            PlaneTrackerWorker.refreshNow(this@MainActivity)
                        }
                    )
                    UnitButton(
                        label = "°F",
                        selected = useFahrenheit,
                        goldAccent = goldAccent,
                        textPrimary = textPrimary,
                        flapBg = flapBg,
                        onClick = {
                            useFahrenheit = true
                            prefs.edit().putBoolean("use_fahrenheit", true).apply()
                            PlaneTrackerWorker.refreshNow(this@MainActivity)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Refresh button
            Button(
                onClick = { PlaneTrackerWorker.refreshNow(this@MainActivity) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = goldAccent,
                    contentColor = panelBg
                ),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "REFRESH NOW",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
        }
    }

    @Composable
    private fun UnitButton(
        label: String,
        selected: Boolean,
        goldAccent: Color,
        textPrimary: Color,
        flapBg: Color,
        onClick: () -> Unit
    ) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selected) goldAccent else Color(0xFF2E2E33),
                contentColor = if (selected) Color(0xFF141416) else textPrimary
            ),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.width(80.dp)
        ) {
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun PlaneTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFD4A847),
            background = Color(0xFF141416),
            surface = Color(0xFF1E1E21)
        ),
        content = content
    )
}
