import SwiftUI
import WidgetKit

/// Large widget — full Solari board with route arc, stats, weather
struct LargeWidgetView: View {
    let entry: PlaneTrackerEntry

    var body: some View {
        if let flight = entry.nearestFlight {
            flightView(flight)
        } else {
            emptyView
        }
    }

    @ViewBuilder
    private func flightView(_ flight: Flight) -> some View {
        let route = entry.route(for: flight)
        let vs = PlaneTrackerEntry.verticalStatus(flight.vertRateFpm)
        let progress = route.map { $0.progress(acLat: flight.lat, acLon: flight.lon) } ?? 50

        VStack(spacing: 0) {
            // ── Header: airline + callsign ────────────────────────────────────
            HStack {
                if let iata = route?.airlineIata, !iata.isEmpty {
                    AsyncImage(url: URL(string: "https://pics.avs.io/40/40/\(iata).png")) { phase in
                        if let img = phase.image {
                            img.resizable().aspectRatio(contentMode: .fit)
                        } else {
                            Image(systemName: "airplane.circle.fill")
                                .foregroundColor(PTConstants.goldAccent)
                        }
                    }
                    .frame(width: 22, height: 22)
                }

                VStack(alignment: .leading, spacing: 1) {
                    Text(route?.airlineName ?? "")
                        .font(.system(size: 10))
                        .foregroundColor(PTConstants.textDim)
                        .lineLimit(1)
                    FlapText(text: String(flight.callsign.prefix(8)))
                }

                Spacer()

                if !flight.aircraftType.isEmpty {
                    Text(flight.aircraftType)
                        .font(.system(size: 10, design: .monospaced))
                        .foregroundColor(PTConstants.textDim)
                }

                if entry.flights.count > 1 {
                    Text("1/\(entry.flights.count)")
                        .font(.system(size: 9, design: .monospaced))
                        .foregroundColor(PTConstants.textDim)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(PTConstants.flapBg)
                        .cornerRadius(3)
                }
            }
            .padding(.horizontal, 14)
            .padding(.top, 12)
            .padding(.bottom, 6)

            // ── Route: IATA codes + arc ───────────────────────────────────────
            HStack(spacing: 0) {
                VStack(spacing: 2) {
                    FlapText(text: route?.origin ?? "???", isLarge: true)
                    Text(route?.originName ?? "")
                        .font(.system(size: 8))
                        .foregroundColor(PTConstants.textDim)
                        .lineLimit(1)
                }
                .frame(width: 60)

                // Arc
                RouteArc(
                    progress: progress,
                    originCode: route?.origin ?? "???",
                    destCode: route?.dest ?? "???"
                )
                .frame(height: 50)

                VStack(spacing: 2) {
                    FlapText(text: route?.dest ?? "???", isLarge: true)
                    Text(route?.destName ?? "")
                        .font(.system(size: 8))
                        .foregroundColor(PTConstants.textDim)
                        .lineLimit(1)
                }
                .frame(width: 60)
            }
            .padding(.horizontal, 8)

            // ── Stats grid (2×2) ─────────────────────────────────────────────
            LazyVGrid(columns: [
                GridItem(.flexible()),
                GridItem(.flexible()),
            ], spacing: 6) {
                statCard(label: "ALTITUDE", value: "\(flight.altFt.formatted())", unit: "ft", icon: "arrow.up.forward")
                statCard(label: "GND SPEED", value: "\(flight.speedKts)", unit: "kts", icon: "gauge.medium")
                statCard(label: "DISTANCE", value: String(format: "%.1f", flight.distanceMi), unit: "mi \(flight.direction)", icon: "location")

                // Vertical speed card
                VStack(spacing: 2) {
                    HStack(spacing: 3) {
                        Image(systemName: vs.symbol)
                            .font(.system(size: 8))
                        Text(vs.text.uppercased())
                            .font(.system(size: 7, weight: .medium))
                    }
                    .foregroundColor(PTConstants.textDim)

                    Text(flight.vertRateFpm > 200 ? "+\(flight.vertRateFpm)" :
                         flight.vertRateFpm < -200 ? "\(flight.vertRateFpm)" : "0")
                        .font(.system(size: 16, weight: .bold, design: .monospaced))
                        .foregroundColor(
                            flight.vertRateFpm > 200 ? PTConstants.climbGreen :
                            flight.vertRateFpm < -200 ? PTConstants.descentRed :
                            PTConstants.levelBlue
                        )

                    if abs(flight.vertRateFpm) > 200 {
                        Text("fpm")
                            .font(.system(size: 7))
                            .foregroundColor(PTConstants.textDim)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 6)
                .background(PTConstants.flapBg)
                .cornerRadius(6)
            }
            .padding(.horizontal, 14)
            .padding(.top, 6)

            Spacer(minLength: 4)

            // ── Footer: weather + update time ────────────────────────────────
            HStack {
                if let w = entry.weather {
                    HStack(spacing: 4) {
                        Image(systemName: w.sfSymbol)
                            .font(.system(size: 10))
                        Text(entry.tempString(w))
                            .font(.system(size: 10, design: .monospaced))
                        Text("·")
                        Text("\(w.humidity)% RH")
                            .font(.system(size: 10, design: .monospaced))
                    }
                    .foregroundColor(PTConstants.textDim)
                }

                Spacer()

                HStack(spacing: 3) {
                    Circle()
                        .fill(PTConstants.statusOk)
                        .frame(width: 5, height: 5)
                    Text(entry.date, style: .time)
                        .font(.system(size: 9, design: .monospaced))
                        .foregroundColor(PTConstants.textDim)
                }
            }
            .padding(.horizontal, 14)
            .padding(.bottom, 10)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(PTConstants.panelBg)
    }

    private func statCard(label: String, value: String, unit: String, icon: String) -> some View {
        VStack(spacing: 2) {
            HStack(spacing: 3) {
                Image(systemName: icon)
                    .font(.system(size: 8))
                Text(label)
                    .font(.system(size: 7, weight: .medium))
            }
            .foregroundColor(PTConstants.textDim)

            HStack(alignment: .lastTextBaseline, spacing: 2) {
                Text(value)
                    .font(.system(size: 16, weight: .bold, design: .monospaced))
                    .foregroundColor(PTConstants.textPrimary)
                Text(unit)
                    .font(.system(size: 8))
                    .foregroundColor(PTConstants.textDim)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 6)
        .background(PTConstants.flapBg)
        .cornerRadius(6)
    }

    // ─── Empty state ─────────────────────────────────────────────────────────
    private var emptyView: some View {
        VStack(spacing: 12) {
            Text(entry.date, style: .time)
                .font(.system(size: 48, weight: .bold, design: .monospaced))
                .foregroundColor(PTConstants.goldAccent)

            Text(entry.date, style: .date)
                .font(.system(size: 13, design: .monospaced))
                .foregroundColor(PTConstants.textDim)

            Spacer()

            Image(systemName: "binoculars")
                .font(.system(size: 32))
                .foregroundColor(PTConstants.textDim.opacity(0.4))

            Text("NO FLIGHTS OVERHEAD")
                .font(.system(size: 12, weight: .bold, design: .monospaced))
                .foregroundColor(PTConstants.textDim)

            Spacer()

            if let w = entry.weather {
                HStack(spacing: 8) {
                    Image(systemName: w.sfSymbol)
                        .font(.system(size: 16))
                    Text("\(entry.tempString(w)) · \(w.description)")
                        .font(.system(size: 12, design: .monospaced))
                }
                .foregroundColor(PTConstants.textDim)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(PTConstants.panelBg)
    }
}
