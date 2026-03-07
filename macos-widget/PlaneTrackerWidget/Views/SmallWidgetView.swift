import SwiftUI
import WidgetKit

/// Small widget — clock + flight count summary
struct SmallWidgetView: View {
    let entry: PlaneTrackerEntry

    var body: some View {
        VStack(spacing: 6) {
            // Clock
            Text(entry.date, style: .time)
                .font(.system(size: 28, weight: .bold, design: .monospaced))
                .foregroundColor(PTConstants.goldAccent)

            Spacer(minLength: 2)

            if let flight = entry.nearestFlight {
                // Flight info
                HStack(spacing: 4) {
                    Image(systemName: "airplane")
                        .font(.system(size: 10))
                        .foregroundColor(PTConstants.goldAccent)
                    FlapText(text: flight.callsign.prefix(7).uppercased())
                }

                if let route = entry.route(for: flight) {
                    HStack(spacing: 4) {
                        Text(route.origin)
                            .font(.system(size: 12, weight: .bold, design: .monospaced))
                        Image(systemName: "arrow.right")
                            .font(.system(size: 8))
                        Text(route.dest)
                            .font(.system(size: 12, weight: .bold, design: .monospaced))
                    }
                    .foregroundColor(PTConstants.textPrimary)
                }

                Text("\(String(format: "%.1f", flight.distanceMi)) mi · \(flight.direction)")
                    .font(.system(size: 10, design: .monospaced))
                    .foregroundColor(PTConstants.textDim)

                if entry.flights.count > 1 {
                    Text("+\(entry.flights.count - 1) more")
                        .font(.system(size: 9))
                        .foregroundColor(PTConstants.textDim)
                }
            } else {
                // No flights
                Image(systemName: "binoculars")
                    .font(.system(size: 20))
                    .foregroundColor(PTConstants.textDim)
                Text("Clear skies")
                    .font(.system(size: 11, design: .monospaced))
                    .foregroundColor(PTConstants.textDim)
            }

            Spacer(minLength: 2)

            // Weather
            if let w = entry.weather {
                HStack(spacing: 4) {
                    Image(systemName: w.sfSymbol)
                        .font(.system(size: 10))
                    Text(entry.tempString(w))
                        .font(.system(size: 10, design: .monospaced))
                }
                .foregroundColor(PTConstants.textDim)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(PTConstants.panelBg)
    }
}
