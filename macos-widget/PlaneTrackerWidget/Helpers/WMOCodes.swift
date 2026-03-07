import Foundation

enum WMOCodes {
    /// WMO weather code → SF Symbol name
    static func sfSymbol(for code: Int) -> String {
        switch code {
        case 0:           return "sun.max.fill"
        case 1:           return "sun.min.fill"
        case 2:           return "cloud.sun.fill"
        case 3:           return "cloud.fill"
        case 45, 48:      return "cloud.fog.fill"
        case 51, 53:      return "cloud.drizzle.fill"
        case 55, 61, 63:  return "cloud.rain.fill"
        case 65:          return "cloud.heavyrain.fill"
        case 71, 73, 77:  return "cloud.snow.fill"
        case 75:          return "snowflake"
        case 80, 81:      return "cloud.rain.fill"
        case 82:          return "cloud.bolt.rain.fill"
        case 85, 86:      return "cloud.snow.fill"
        case 95, 96, 99:  return "cloud.bolt.fill"
        default:          return "cloud.fill"
        }
    }

    /// WMO weather code → short description
    static func description(for code: Int) -> String {
        switch code {
        case 0:           return "Clear"
        case 1:           return "Mostly Clear"
        case 2:           return "Partly Cloudy"
        case 3:           return "Overcast"
        case 45, 48:      return "Foggy"
        case 51, 53:      return "Drizzle"
        case 55:          return "Heavy Drizzle"
        case 61:          return "Light Rain"
        case 63:          return "Rain"
        case 65:          return "Heavy Rain"
        case 71:          return "Light Snow"
        case 73:          return "Snow"
        case 75:          return "Heavy Snow"
        case 77:          return "Snow Grains"
        case 80, 81:      return "Showers"
        case 82:          return "Heavy Showers"
        case 85, 86:      return "Snow Showers"
        case 95:          return "Thunderstorm"
        case 96, 99:      return "Hailstorm"
        default:          return "Unknown"
        }
    }
}
