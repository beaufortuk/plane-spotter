import Foundation

enum RouteService {
    /// Fetch route info for a callsign from adsbdb
    static func fetchRoute(callsign: String) async -> RouteInfo? {
        let cleaned = callsign.trimmingCharacters(in: .whitespaces)
        guard !cleaned.isEmpty,
              let encoded = cleaned.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed),
              let url = URL(string: "\(PTConstants.adsbdbBase)/callsign/\(encoded)")
        else { return nil }

        do {
            var request = URLRequest(url: url)
            request.timeoutInterval = 10
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else { return nil }
            let decoded = try JSONDecoder().decode(ADSBDBCallsignResponse.self, from: data)
            guard let fr = decoded.response?.flightroute else { return nil }
            return RouteInfo.from(fr)
        } catch {
            return nil
        }
    }

    /// Fetch routes for multiple callsigns concurrently
    static func fetchRoutes(for flights: [Flight]) async -> [String: RouteInfo] {
        var result: [String: RouteInfo] = [:]

        await withTaskGroup(of: (String, RouteInfo?).self) { group in
            for flight in flights {
                group.addTask {
                    let route = await fetchRoute(callsign: flight.callsign)
                    return (flight.callsign, route)
                }
            }
            for await (callsign, route) in group {
                if let route { result[callsign] = route }
            }
        }

        return result
    }
}
