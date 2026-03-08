package com.plane.tracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SolariDarkScheme = darkColorScheme(
    background = SolariBg,
    surface = SolariSurface,
    surfaceVariant = SolariSurface,
    onBackground = SolariTextPrimary,
    onSurface = SolariTextPrimary,
    onSurfaceVariant = SolariTextSecondary,
    primary = SolariGold,
    onPrimary = SolariBg,
    secondary = SolariTextSecondary,
    onSecondary = SolariBg,
    tertiary = SolariGreen,
    onTertiary = SolariBg,
    error = SolariRed,
    onError = SolariTextPrimary
)

@Composable
fun PlaneTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SolariDarkScheme,
        typography = SolariTypography,
        content = content
    )
}
