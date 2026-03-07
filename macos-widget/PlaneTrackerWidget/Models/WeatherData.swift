import Foundation

/// Open-Meteo API response
struct OpenMeteoResponse: Codable {
    let current: OpenMeteoCurrent?
}

struct OpenMeteoCurrent: Codable {
    let temperature_2m: Double?
    let relative_humidity_2m: Double?
    let weather_code: Int?
    let surface_pressure: Double?
}

/// Processed weather for display
struct WeatherInfo {
    let tempC: Double
    let humidity: Int
    let weatherCode: Int
    let pressureHpa: Double

    var tempF: Int { Int(round(tempC * 9.0 / 5.0 + 32)) }
    var sfSymbol: String { WMOCodes.sfSymbol(for: weatherCode) }
    var description: String { WMOCodes.description(for: weatherCode) }
}
