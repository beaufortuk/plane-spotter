import Foundation

/// Raw adsb.fi API response
struct ADSBResponse: Codable {
    let ac: [ADSBAircraft]?
    let aircraft: [ADSBAircraft]?
    let msg: String?
    let total: Int?

    var allAircraft: [ADSBAircraft] { ac ?? aircraft ?? [] }
}

struct ADSBAircraft: Codable {
    let hex: String?
    let flight: String?
    let lat: Double?
    let lon: Double?
    let alt_baro: AltValue?
    let alt_geom: Double?
    let gs: Double?        // ground speed (knots)
    let track: Double?
    let baro_rate: Double? // ft/min
    let t: String?         // aircraft type
    let r: String?         // registration
    let dst: Double?       // distance in nautical miles

    enum AltValue: Codable {
        case number(Double)
        case string(String)

        init(from decoder: Decoder) throws {
            let container = try decoder.singleValueContainer()
            if let d = try? container.decode(Double.self) {
                self = .number(d)
            } else if let s = try? container.decode(String.self) {
                self = .string(s)
            } else {
                self = .string("ground")
            }
        }

        func encode(to encoder: Encoder) throws {
            var container = encoder.singleValueContainer()
            switch self {
            case .number(let d): try container.encode(d)
            case .string(let s): try container.encode(s)
            }
        }

        var numericValue: Double? {
            if case .number(let d) = self { return d }
            return nil
        }

        var isGround: Bool {
            if case .string(let s) = self { return s == "ground" }
            return false
        }
    }
}

/// Processed flight for display
struct Flight: Identifiable {
    let id: String       // icao24 hex
    let callsign: String
    let lat: Double
    let lon: Double
    let altFt: Int
    let speedKts: Int
    let direction: String  // cardinal from user to aircraft
    let vertRateFpm: Int
    let distanceMi: Double
    let aircraftType: String
    let registration: String
}

extension Flight {
    /// Create from raw adsb.fi aircraft data
    static func from(_ ac: ADSBAircraft, userLat: Double, userLon: Double) -> Flight? {
        guard let lat = ac.lat, let lon = ac.lon,
              let altVal = ac.alt_baro, !altVal.isGround,
              let altFt = altVal.numericValue,
              altFt >= 1000
        else { return nil }

        let distMi: Double
        if let dstNm = ac.dst {
            distMi = dstNm * GeoMath.nmToMi
        } else {
            distMi = GeoMath.haversine(lat1: userLat, lon1: userLon, lat2: lat, lon2: lon)
        }

        let dir = GeoMath.toCardinal(
            GeoMath.bearing(lat1: userLat, lon1: userLon, lat2: lat, lon2: lon)
        )

        return Flight(
            id: ac.hex ?? UUID().uuidString,
            callsign: (ac.flight?.trimmingCharacters(in: .whitespaces)).flatMap { $0.isEmpty ? nil : $0 }
                      ?? (ac.hex?.uppercased() ?? "???"),
            lat: lat,
            lon: lon,
            altFt: Int(altFt),
            speedKts: Int(round(ac.gs ?? 0)),
            direction: dir,
            vertRateFpm: Int(round(ac.baro_rate ?? 0)),
            distanceMi: distMi,
            aircraftType: ac.t ?? "",
            registration: ac.r ?? ""
        )
    }
}
