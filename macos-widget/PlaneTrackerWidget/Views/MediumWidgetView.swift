import SwiftUI
import WidgetKit

/// Medium widget — the money shot: airline, route, stats
struct MediumWidgetView: View {
    let entry: PlaneTrackerEntry

    var body: some View {
        if let flight = entry.nearestFlight {
            flightView(flight)
        } else {
            emptyView
        }
    }

    // ─── Flight display ──────────────────────────────────────────────────────
    @ViewBuilder
    private func flightView(_ flight: Flight) -> some View {
        let route = entry.route(for: flight)
        let vs = PlaneTrackerEntry.verticalStatus(flight.vertRateFpm)

        HStack(spacing: 0) {
            // LEFT: airline + route
            VStack(alignment: .leading, spacing: 6) {
                // Airline + callsign
                HStack(spacing: 6) {
                    if let iata = route?.airlineIata, !iata.isEmpty {
                        AsyncImage(url: URL(string: "https://pics.avs.io/40/40/\(iata).png")) { phase in
                            if let img = phase.image {
                                img.resizable().aspectRatio(contentMode: .fit)
                            } else {
                                Image(systemName: "airplane.circle.fill")
                                    .foregroundColor(PTConstants.goldAccent)
                            }
                        }
                        .frame(width: 20, height: 20)
                    }

                    VStack(alignment: .leading, spacing: 1) {
                        Text(route?.airlineName ?? "")
                            .font(.system(size: 10))
                            .foregroundColor(PTConstants.textDim)
                            .lineLimit(1)
                        FlapText(text: String(flight.callsign.prefix(8)))
                    }
                }

                // Route IATA codes
                HStack(spacing: 6) {
                    FlapText(text: route?.origin ?? "???", isLarge: true)

                    VStack(spacing: 2) {
                        Image(systemName: "airplane")
                            .font(.system(size: 7))
                            .foregroundColor(PTConstants.goldAccent)
                        Rectangle()
                            .fill(PTConstants.textDim.opacity(0.3))
                            .frame(width: 16, height: 1)
                    }

                    FlapText(text: route?.dest ?? "???", isLarge: true)
                }

                // City names
                HStack {
                    Text(route?.originName ?? "")
                        .font(.system(size: 8))
                        .foregroundColor(PTConstants.textDim)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                    Spacer(minLength: 4)
                    Text(route?.destName ?? "")
                        .font(.system(size: 8))
                        .foregroundColor(PTConstants.textDim)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            // RIGHT: stats grid
            VStack(spacing: 6) {
                statRow(label: "ALT", value: "\(flight.altFt.formatted()) ft", icon: "arrow.up.forward")
                statRow(label: "GND SPD", value: "\(flight.speedKts) kts", icon: "gauge.medium")
                statRow(label: "DIST", value: "\(String(format: "%.1f", flight.distanceMi)) mi \(flight.direction)", icon: "location")

                HStack(spacing: 4) {
                    Image(systemName: vs.symbol)
                        .font(.system(size: 8))
                    Text(vs.text)
                        .font(.system(size: 9, weight: .medium, design: .monospaced))
                }
                .foregroundColor(
                    flight.vertRateFpm > 200 ? PTConstants.climbGreen :
                    flight.vertRateFpm < -200 ? PTConstants.descentRed :
                    PTConstants.levelBlue
                )
            }
            .frame(width: 106)
        }
        .padding(10)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(PTConstants.panelBg)
    }

    private func statRow(label: String, value: String, icon: String) -> some View {
        HStack(spacing: 4) {
            Image(systemName: icon)
                .font(.system(size: 8))
                .foregroundColor(PTConstants.goldAccent)
                .frame(width: 12)
            VStack(alignment: .leading, spacing: 0) {
                Text(label)
                    .font(.system(size: 7, weight: .medium))
                    .foregroundColor(PTConstants.textDim)
                Text(value)
                    .font(.system(size: 10, weight: .bold, design: .monospaced))
                    .foregroundColor(PTConstants.textPrimary)
                    .lineLimit(1)
            }
            Spacer(minLength: 0)
        }
    }

    // ─── Empty state ─────────────────────────────────────────────────────────
    private var emptyView: some View {
        HStack(spacing: 16) {
            VStack(spacing: 6) {
                Text(entry.date, style: .time)
                    .font(.system(size: 32, weight: .bold, design: .monospaced))
                    .foregroundColor(PTConstants.goldAccent)
                Text(entry.date, style: .date)
                    .font(.system(size: 11, design: .monospaced))
                    .foregroundColor(PTConstants.textDim)
            }

            VStack(spacing: 8) {
                Image(systemName: "binoculars")
                    .font(.system(size: 24))
                    .foregroundColor(PTConstants.textDim.opacity(0.5))
                Text("NO FLIGHTS")
                    .font(.system(size: 11, weight: .bold, design: .monospaced))
                    .foregroundColor(PTConstants.textDim)

                if let w = entry.weather {
                    HStack(spacing: 4) {
                        Image(systemName: w.sfSymbol)
                            .font(.system(size: 12))
                        Text(entry.tempString(w))
                            .font(.system(size: 11, design: .monospaced))
                    }
                    .foregroundColor(PTConstants.textDim)
                }
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(PTConstants.panelBg)
    }
}
