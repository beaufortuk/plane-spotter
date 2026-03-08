import AppIntents
import WidgetKit

enum TemperatureUnit: String, AppEnum {
    case celsius    = "celsius"
    case fahrenheit = "fahrenheit"

    static var typeDisplayRepresentation = TypeDisplayRepresentation(name: "Temperature Unit")

    static var caseDisplayRepresentations: [TemperatureUnit: DisplayRepresentation] = [
        .celsius:    DisplayRepresentation(title: "°C"),
        .fahrenheit: DisplayRepresentation(title: "°F"),
    ]
}

struct PlaneTrackerIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Plane Tracker"
    static var description: IntentDescription = "Configure your plane tracker widget."

    @Parameter(title: "Temperature", default: .celsius)
    var units: TemperatureUnit

    init() {
        self.units = .celsius
    }

    init(units: TemperatureUnit) {
        self.units = units
    }
}
