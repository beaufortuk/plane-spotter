package com.plane.tracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Monospace font — Solari split-flap aesthetic
val SolariMono = FontFamily.Monospace

val SolariTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = SolariMono,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        letterSpacing = 2.sp,
        color = SolariTextPrimary
    ),
    displayMedium = TextStyle(
        fontFamily = SolariMono,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        letterSpacing = 2.sp,
        color = SolariTextPrimary
    ),
    headlineLarge = TextStyle(
        fontFamily = SolariMono,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 1.5.sp,
        color = SolariTextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = SolariMono,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        letterSpacing = 1.sp,
        color = SolariTextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = SolariMono,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        letterSpacing = 1.sp,
        color = SolariGold
    ),
    titleMedium = TextStyle(
        fontFamily = SolariMono,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 1.sp,
        color = SolariTextSecondary
    ),
    bodyLarge = TextStyle(
        fontFamily = SolariMono,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        color = SolariTextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = SolariMono,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = SolariTextSecondary
    ),
    bodySmall = TextStyle(
        fontFamily = SolariMono,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        color = SolariTextSecondary
    ),
    labelSmall = TextStyle(
        fontFamily = SolariMono,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 1.5.sp,
        color = SolariTextSecondary
    )
)
