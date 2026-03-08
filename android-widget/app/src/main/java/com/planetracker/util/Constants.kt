package com.planetracker.util

import androidx.compose.ui.graphics.Color

object Constants {
    // ── Location (London default) ──────────────────────────────────────────
    const val DEFAULT_LAT = 51.5074
    const val DEFAULT_LON = -0.1278
    const val SEARCH_RADIUS_NM = 5

    // ── API URLs ───────────────────────────────────────────────────────────
    const val ADSB_PROXY = "https://plane-tracker-proxy.plane-tracker-proxy.workers.dev"
    const val ADSBDB_BASE = "https://api.adsbdb.com/v0"
    const val OPEN_METEO_BASE = "https://api.open-meteo.com/v1/forecast"

    // ── Thresholds ─────────────────────────────────────────────────────────
    const val MIN_ALT_FT = 1000
    const val MAX_FLIGHTS = 5
    const val NM_TO_MI = 1.15078

    // ── Solari colors ──────────────────────────────────────────────────────
    val PanelBg = Color(0xFF141416)
    val FlapBg = Color(0xFF1E1E21)
    val FlapBorder = Color(0xFF2E2E33)
    val GoldAccent = Color(0xFFD4A847)
    val TextPrimary = Color(0xFFF2EBD9)
    val TextDim = Color(0xFF8C877A)
    val StatusOk = Color(0xFF4DBF66)
    val StatusErr = Color(0xFFD94D4D)
    val ClimbGreen = Color(0xFF4DCC73)
    val DescentRed = Color(0xFFE6594D)
    val LevelBlue = Color(0xFF66A6E6)

    // ── Android color ints (for Canvas/Bitmap rendering) ───────────────────
    const val PANEL_BG_INT = 0xFF141416.toInt()
    const val FLAP_BG_INT = 0xFF1E1E21.toInt()
    const val GOLD_ACCENT_INT = 0xFFD4A847.toInt()
    const val TEXT_PRIMARY_INT = 0xFFF2EBD9.toInt()
    const val TEXT_DIM_INT = 0xFF8C877A.toInt()
}
