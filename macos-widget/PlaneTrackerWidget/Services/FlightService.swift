import Foundation

enum FlightService {
    /// Fetch flights near the given coordinates via the Cloudflare Worker proxy
    static func fetchFlights(lat: Double, lon: Double, radiusNM: Int = PTConstants.searchRadiusNM) async throws -> [Flight] {
        let urlStr = "\(PTConstants.adsbProxy)/lat/\(String(format: "%.4f", lat))/lon/\(String(format: "%.4f", lon))/dist/\(radiusNM)"
        guard let url = URL(string: urlStr) else { throw PTError.invalidURL }

        var request = URLRequest(url: url)
        request.timeoutInterval = 15

        let (data, response) = try await URLSession.shared.data(for: request)

        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
            throw PTError.httpError
        }

        let decoded = try JSONDecoder().decode(ADSBResponse.self, from: data)
        let flights = decoded.allAircraft
            .compactMap { Flight.from($0, userLat: lat, userLon: lon) }
            .sorted { $0.distanceMi < $1.distanceMi }

        return Array(flights.prefix(5))
    }
}

enum PTError: Error, LocalizedError {
    case invalidURL
    case httpError
    case noData

    var errorDescription: String? {
        switch self {
        case .invalidURL: return "Invalid URL"
        case .httpError: return "Server error"
        case .noData: return "No data"
        }
    }
}
