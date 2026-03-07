import Foundation

enum GeoMath {
    static let earthRadiusMi = 3958.8
    static let nmToMi = 1.15078

    /// Haversine distance in statute miles
    static func haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double) -> Double {
        let dLat = (lat2 - lat1) * .pi / 180
        let dLon = (lon2 - lon1) * .pi / 180
        let a = sin(dLat / 2) * sin(dLat / 2)
              + cos(lat1 * .pi / 180) * cos(lat2 * .pi / 180)
              * sin(dLon / 2) * sin(dLon / 2)
        return earthRadiusMi * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /// Bearing from point 1 to point 2 (degrees, 0=N clockwise)
    static func bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double) -> Double {
        let dLon = (lon2 - lon1) * .pi / 180
        let y = sin(dLon) * cos(lat2 * .pi / 180)
        let x = cos(lat1 * .pi / 180) * sin(lat2 * .pi / 180)
              - sin(lat1 * .pi / 180) * cos(lat2 * .pi / 180) * cos(dLon)
        return (atan2(y, x) * 180 / .pi + 360).truncatingRemainder(dividingBy: 360)
    }

    /// Cardinal direction string
    static func toCardinal(_ deg: Double) -> String {
        let dirs = ["N", "NE", "E", "SE", "S", "SW", "W", "NW"]
        return dirs[Int(round(deg / 45)) % 8]
    }

    /// Route plausibility check — triangle inequality
    static func isRoutePlausible(
        originLat: Double, originLon: Double,
        destLat: Double, destLon: Double,
        acLat: Double, acLon: Double
    ) -> Bool {
        let routeDist = haversine(lat1: originLat, lon1: originLon, lat2: destLat, lon2: destLon)
        guard routeDist > 50 else { return true }
        let toOrig = haversine(lat1: acLat, lon1: acLon, lat2: originLat, lon2: originLon)
        let toDest = haversine(lat1: acLat, lon1: acLon, lat2: destLat, lon2: destLon)
        let detour = (toOrig + toDest) / routeDist
        return detour < 1.5
    }

    /// Flight progress percentage (0–100) based on position between origin and dest
    static func flightProgress(
        originLat: Double, originLon: Double,
        destLat: Double, destLon: Double,
        acLat: Double, acLon: Double
    ) -> Double {
        let total = haversine(lat1: originLat, lon1: originLon, lat2: destLat, lon2: destLon)
        guard total > 0 else { return 50 }
        let flown = haversine(lat1: originLat, lon1: originLon, lat2: acLat, lon2: acLon)
        return min(max((flown / total) * 100, 2), 98)
    }

    static func mpsToKnots(_ mps: Double) -> Int { Int(round(mps * 1.94384)) }
    static func mToFt(_ m: Double) -> Int { Int(round(m * 3.28084)) }
}
