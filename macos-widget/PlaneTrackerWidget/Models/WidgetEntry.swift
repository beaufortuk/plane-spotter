import WidgetKit
import Foundation

struct PlaneTrackerEntry: TimelineEntry {
    let date: Date
    let flights: [Flight]
    let routes: [String: RouteInfo]   // keyed by callsign
    let weather: WeatherInfo?
    let errorMessage: String?
    let units: TemperatureUnit

    /// The nearest flight (if any)
    var nearestFlight: Flight? { flights.first }

    /// Route for a given flight (with plausibility check)
    func route(for flight: Flight) -> RouteInfo? {
        guard let r = routes[flight.callsign] else { return nil }
        return r.isPlausible(acLat: flight.lat, acLon: flight.lon) ? r : nil
    }

    /// Formatted temperature string using the configured unit
    func tempString(_ w: WeatherInfo) -> String {
        switch units {
        case .celsius:    return "\(Int(round(w.tempC)))°C"
        case .fahrenheit: return "\(w.tempF)°F"
        }
    }

    /// Vertical status string
    static func verticalStatus(_ fpm: Int) -> (text: String, symbol: String) {
        if fpm > 200 { return ("Climbing", "arrow.up") }
        if fpm < -200 { return ("Descending", "arrow.down") }
        return ("Level", "arrow.right")
    }

    // ─── Placeholder / snapshot ──────────────────────────────────────────────
    static let placeholder = PlaneTrackerEntry(
        date: .now,
        flights: [
            Flight(id: "ABCDEF", callsign: "BAW123", lat: 51.5, lon: -0.1,
                   altFt: 35000, speedKts: 450, direction: "NE",
                   vertRateFpm: 0, distanceMi: 2.3,
                   aircraftType: "A320", registration: "G-EUPT")
        ],
        routes: [
            "BAW123": RouteInfo(
                origin: "LHR", dest: "CDG",
                originName: "London", destName: "Paris",
                originLat: 51.47, originLon: -0.46,
                destLat: 49.01, destLon: 2.55,
                airlineName: "British Airways", airlineIata: "BA"
            )
        ],
        weather: WeatherInfo(tempC: 12, humidity: 65, weatherCode: 2, pressureHpa: 1013),
        errorMessage: nil,
        units: .celsius
    )

    static let empty = PlaneTrackerEntry(
        date: .now, flights: [], routes: [:], weather: nil, errorMessage: nil, units: .celsius
    )
}
