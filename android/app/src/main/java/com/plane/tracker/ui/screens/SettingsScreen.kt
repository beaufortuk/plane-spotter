package com.plane.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plane.tracker.ui.theme.*

/**
 * Minimal settings screen — units toggle, location mode, about.
 * Placeholder for future expansion; not yet wired into navigation.
 */
@Composable
fun SettingsScreen(
    useGps: Boolean,
    units: String,
    onToggleGps: (Boolean) -> Unit,
    onToggleUnits: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SolariBg)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "SETTINGS",
            fontFamily = SolariMono,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            letterSpacing = 2.sp,
            color = SolariGold
        )

        // GPS toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("LOCATION", fontFamily = SolariMono, fontWeight = FontWeight.Bold,
                    fontSize = 12.sp, color = SolariTextPrimary, letterSpacing = 1.sp)
                Text(
                    if (useGps) "Using GPS" else "London (default)",
                    fontFamily = SolariMono, fontSize = 11.sp, color = SolariTextSecondary
                )
            }
            Switch(
                checked = useGps,
                onCheckedChange = onToggleGps,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = SolariGold,
                    checkedTrackColor = SolariGold.copy(alpha = 0.3f),
                    uncheckedThumbColor = SolariTextSecondary,
                    uncheckedTrackColor = SolariSurface
                )
            )
        }

        // Units toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("UNITS", fontFamily = SolariMono, fontWeight = FontWeight.Bold,
                    fontSize = 12.sp, color = SolariTextPrimary, letterSpacing = 1.sp)
                Text(
                    if (units == "metric") "Metric (km, m)" else "Imperial (mi, ft)",
                    fontFamily = SolariMono, fontSize = 11.sp, color = SolariTextSecondary
                )
            }
            Switch(
                checked = units == "metric",
                onCheckedChange = { onToggleUnits(if (it) "metric" else "imperial") },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = SolariGold,
                    checkedTrackColor = SolariGold.copy(alpha = 0.3f),
                    uncheckedThumbColor = SolariTextSecondary,
                    uncheckedTrackColor = SolariSurface
                )
            )
        }

        Spacer(Modifier.weight(1f))

        // About
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("PLANE TRACKER RGB", fontFamily = SolariMono, fontSize = 11.sp,
                color = SolariTextSecondary, letterSpacing = 1.5.sp)
            Text("v1.0.0", fontFamily = SolariMono, fontSize = 10.sp,
                color = SolariTextSecondary.copy(alpha = 0.5f))
        }
    }
}
