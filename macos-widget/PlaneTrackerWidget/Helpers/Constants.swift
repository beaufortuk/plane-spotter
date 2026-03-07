import SwiftUI

enum PTConstants {
    // ─── Location (London default) ───────────────────────────────────────────
    static let defaultLat = 51.5074
    static let defaultLon = -0.1278
    static let searchRadiusNM = 5 // nautical miles

    // ─── API URLs ────────────────────────────────────────────────────────────
    static let adsbProxy = "https://plane-tracker-proxy.plane-tracker-proxy.workers.dev"
    static let adsbdbBase = "https://api.adsbdb.com/v0"
    static let openMeteoBase = "https://api.open-meteo.com/v1/forecast"

    // ─── Solari colors ───────────────────────────────────────────────────────
    static let panelBg     = Color(red: 0.08, green: 0.08, blue: 0.09)
    static let flapBg      = Color(red: 0.12, green: 0.12, blue: 0.13)
    static let flapBorder  = Color(red: 0.18, green: 0.18, blue: 0.20)
    static let goldAccent  = Color(red: 0.83, green: 0.66, blue: 0.28)
    static let textPrimary = Color(red: 0.95, green: 0.92, blue: 0.85)
    static let textDim     = Color(red: 0.55, green: 0.53, blue: 0.48)
    static let statusOk    = Color(red: 0.30, green: 0.75, blue: 0.40)
    static let statusErr   = Color(red: 0.85, green: 0.30, blue: 0.30)
    static let climbGreen  = Color(red: 0.30, green: 0.80, blue: 0.45)
    static let descentRed  = Color(red: 0.90, green: 0.35, blue: 0.30)
    static let levelBlue   = Color(red: 0.40, green: 0.65, blue: 0.90)
}
