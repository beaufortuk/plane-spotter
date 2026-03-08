package com.planetracker.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.planetracker.util.Constants
import com.planetracker.widget.components.FlapText
import java.text.SimpleDateFormat
import java.util.*

/** Medium widget — airline, route, stats (mirrors MediumWidgetView.swift) */
@Composable
fun MediumWidgetContent(state: WidgetState) {
    val flight = state.nearestFlight

    if (flight != null) {
        MediumFlightView(state, flight)
    } else {
        MediumEmptyView(state)
    }
}

@Composable
private fun MediumFlightView(state: WidgetState, flight: com.planetracker.data.model.Flight) {
    val route = state.routeFor(flight)
    val vs = WidgetState.verticalStatus(flight.vertRateFpm)

    val statusColor = when {
        flight.vertRateFpm > 200 -> Constants.ClimbGreen
        flight.vertRateFpm < -200 -> Constants.DescentRed
        else -> Constants.LevelBlue
    }

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Constants.PanelBg))
            .padding(12.dp)
    ) {
        // LEFT: airline + route
        Column(
            modifier = GlanceModifier.defaultWeight(),
            verticalAlignment = Alignment.Top
        ) {
            // Airline name
            if (route?.airlineName?.isNotEmpty() == true) {
                Text(
                    text = route.airlineName,
                    style = TextStyle(
                        color = ColorProvider(Constants.TextDim),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    maxLines = 1
                )
                Spacer(GlanceModifier.height(1.dp))
            }

            // Callsign
            FlapText(text = flight.callsign.take(8))

            Spacer(GlanceModifier.height(8.dp))

            // Route IATA codes
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlapText(
                    text = route?.origin ?: "???",
                    isLarge = true
                )
                Column(
                    modifier = GlanceModifier.padding(horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "✈",
                        style = TextStyle(
                            color = ColorProvider(Constants.GoldAccent),
                            fontSize = 8.sp
                        )
                    )
                }
                FlapText(
                    text = route?.dest ?: "???",
                    isLarge = true
                )
            }

            Spacer(GlanceModifier.height(2.dp))

            // City names
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    text = route?.originName ?: "",
                    style = TextStyle(
                        color = ColorProvider(Constants.TextDim),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    maxLines = 1
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    text = route?.destName ?: "",
                    style = TextStyle(
                        color = ColorProvider(Constants.TextDim),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    maxLines = 1
                )
            }
        }

        // RIGHT: stats
        Column(
            modifier = GlanceModifier.width(120.dp),
            verticalAlignment = Alignment.Top
        ) {
            StatRow(icon = "↗", label = "ALT", value = "%,d ft".format(flight.altFt))
            Spacer(GlanceModifier.height(4.dp))
            StatRow(icon = "⏲", label = "GND SPD", value = "${flight.speedKts} kts")
            Spacer(GlanceModifier.height(4.dp))
            StatRow(icon = "📍", label = "DIST", value = "${"%.1f".format(flight.distanceMi)} mi ${flight.direction}")
            Spacer(GlanceModifier.height(4.dp))

            // Vertical status
            Text(
                text = "${vs.symbol} ${vs.text}",
                style = TextStyle(
                    color = ColorProvider(statusColor),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace
                )
            )
        }
    }
}

@Composable
private fun StatRow(icon: String, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = icon,
            style = TextStyle(
                color = ColorProvider(Constants.GoldAccent),
                fontSize = 8.sp
            )
        )
        Spacer(GlanceModifier.width(4.dp))
        Column {
            Text(
                text = label,
                style = TextStyle(
                    color = ColorProvider(Constants.TextDim),
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace
                )
            )
            Text(
                text = value,
                style = TextStyle(
                    color = ColorProvider(Constants.TextPrimary),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MediumEmptyView(state: WidgetState) {
    val now = Date()
    val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
    val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.US)

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Constants.PanelBg))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: clock
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = timeFormat.format(now),
                style = TextStyle(
                    color = ColorProvider(Constants.GoldAccent),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            )
            Text(
                text = dateFormat.format(now),
                style = TextStyle(
                    color = ColorProvider(Constants.TextDim),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            )
        }

        Spacer(GlanceModifier.width(16.dp))

        // Right: no flights + weather
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "👀",
                style = TextStyle(fontSize = 24.sp)
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = "NO FLIGHTS",
                style = TextStyle(
                    color = ColorProvider(Constants.TextDim),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            )

            val weather = state.weather
            if (weather != null) {
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text = "${weather.emoji} ${state.tempString(weather)}",
                    style = TextStyle(
                        color = ColorProvider(Constants.TextDim),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )
            }
        }
    }
}
