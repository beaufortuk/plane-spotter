import WidgetKit
import SwiftUI
import AppIntents

struct PlaneTrackerProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> PlaneTrackerEntry {
        .placeholder
    }

    func snapshot(for configuration: PlaneTrackerIntent, in context: Context) async -> PlaneTrackerEntry {
        if context.isPreview { return .placeholder }
        return await fetchEntry(units: configuration.units)
    }

    func timeline(for configuration: PlaneTrackerIntent, in context: Context) async -> Timeline<PlaneTrackerEntry> {
        let entry = await fetchEntry(units: configuration.units)

        // Single entry with fresh data; reload ASAP so widget stays current.
        // WidgetKit throttles reloads to ~5 min minimum regardless of policy.
        return Timeline(entries: [entry], policy: .atEnd)
    }

    private func fetchEntry(units: TemperatureUnit) async -> PlaneTrackerEntry {
        let lat = PTConstants.defaultLat
        let lon = PTConstants.defaultLon

        do {
            let flights = try await FlightService.fetchFlights(lat: lat, lon: lon)

            async let routesFetch = RouteService.fetchRoutes(for: flights)
            async let weatherFetch = WeatherService.fetchWeather(lat: lat, lon: lon)

            let routes = await routesFetch
            let weather = await weatherFetch

            return PlaneTrackerEntry(
                date: .now, flights: flights, routes: routes,
                weather: weather, errorMessage: nil, units: units
            )
        } catch {
            return PlaneTrackerEntry(
                date: .now, flights: [], routes: [:],
                weather: await WeatherService.fetchWeather(lat: lat, lon: lon),
                errorMessage: error.localizedDescription, units: units
            )
        }
    }
}

struct PlaneTrackerWidgetView: View {
    @Environment(\.widgetFamily) var family
    let entry: PlaneTrackerEntry

    var body: some View {
        switch family {
        case .systemSmall:
            SmallWidgetView(entry: entry)
        case .systemMedium:
            MediumWidgetView(entry: entry)
        case .systemLarge:
            LargeWidgetView(entry: entry)
        default:
            MediumWidgetView(entry: entry)
        }
    }
}

struct PlaneTrackerWidget: Widget {
    let kind: String = "PlaneTrackerWidget"

    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: kind, intent: PlaneTrackerIntent.self, provider: PlaneTrackerProvider()) { entry in
            PlaneTrackerWidgetView(entry: entry)
                .containerBackground(PTConstants.panelBg, for: .widget)
        }
        .configurationDisplayName("Plane Spotter")
        .description("Spot flights overhead with a Solari split-flap display.")
        .contentMarginsDisabled()
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

#Preview(as: .systemMedium) {
    PlaneTrackerWidget()
} timeline: {
    PlaneTrackerEntry.placeholder
    PlaneTrackerEntry.empty
}
