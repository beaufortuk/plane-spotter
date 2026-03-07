import Foundation

enum WeatherService {
    /// Fetch current weather from Open-Meteo
    static func fetchWeather(lat: Double, lon: Double) async -> WeatherInfo? {
        let urlStr = "\(PTConstants.openMeteoBase)"
            + "?latitude=\(String(format: "%.4f", lat))"
            + "&longitude=\(String(format: "%.4f", lon))"
            + "&current=temperature_2m,relative_humidity_2m,weather_code,surface_pressure"
            + "&timezone=auto"

        guard let url = URL(string: urlStr) else { return nil }

        do {
            var request = URLRequest(url: url)
            request.timeoutInterval = 10
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else { return nil }
            let decoded = try JSONDecoder().decode(OpenMeteoResponse.self, from: data)
            guard let cur = decoded.current else { return nil }

            return WeatherInfo(
                tempC: cur.temperature_2m ?? 0,
                humidity: Int(cur.relative_humidity_2m ?? 0),
                weatherCode: cur.weather_code ?? 0,
                pressureHpa: cur.surface_pressure ?? 1013
            )
        } catch {
            return nil
        }
    }
}
