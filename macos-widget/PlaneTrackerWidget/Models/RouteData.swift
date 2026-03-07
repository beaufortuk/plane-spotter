import Foundation

/// adsbdb callsign API response
struct ADSBDBCallsignResponse: Codable {
    let response: ADSBDBCallsignInner?
}

struct ADSBDBCallsignInner: Codable {
    let flightroute: ADSBDBFlightRoute?
}

struct ADSBDBFlightRoute: Codable {
    let origin: ADSBDBAirport?
    let destination: ADSBDBAirport?
    let airline: ADSBDBAirline?
}

struct ADSBDBAirport: Codable {
    let iata_code: String?
    let icao_code: String?
    let municipality: String?
    let name: String?
    let latitude: Double?
    let longitude: Double?
    let country_iso_name: String?
}

struct ADSBDBAirline: Codable {
    let name: String?
    let iata: String?
    let icao: String?
}

/// Processed route for display
struct RouteInfo {
    let origin: String       // IATA code
    let dest: String         // IATA code
    let originName: String   // city/municipality
    let destName: String
    let originLat: Double
    let originLon: Double
    let destLat: Double
    let destLon: Double
    let airlineName: String
    let airlineIata: String
}

extension RouteInfo {
    static func from(_ fr: ADSBDBFlightRoute) -> RouteInfo? {
        return RouteInfo(
            origin: fr.origin?.iata_code ?? fr.origin?.icao_code ?? "???",
            dest: fr.destination?.iata_code ?? fr.destination?.icao_code ?? "???",
            originName: fr.origin?.municipality ?? fr.origin?.name ?? "",
            destName: fr.destination?.municipality ?? fr.destination?.name ?? "",
            originLat: fr.origin?.latitude ?? 0,
            originLon: fr.origin?.longitude ?? 0,
            destLat: fr.destination?.latitude ?? 0,
            destLon: fr.destination?.longitude ?? 0,
            airlineName: fr.airline?.name ?? "",
            airlineIata: fr.airline?.iata ?? ""
        )
    }

    /// Check if this route makes sense for an aircraft at the given position
    func isPlausible(acLat: Double, acLon: Double) -> Bool {
        GeoMath.isRoutePlausible(
            originLat: originLat, originLon: originLon,
            destLat: destLat, destLon: destLon,
            acLat: acLat, acLon: acLon
        )
    }

    /// Flight progress 0–100
    func progress(acLat: Double, acLon: Double) -> Double {
        GeoMath.flightProgress(
            originLat: originLat, originLon: originLon,
            destLat: destLat, destLon: destLon,
            acLat: acLat, acLon: acLon
        )
    }
}
