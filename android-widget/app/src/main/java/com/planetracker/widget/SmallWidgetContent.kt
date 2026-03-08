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

/** Small widget — clock + flight count summary (mirrors SmallWidgetView.swift) */
@Composable
fun SmallWidgetContent(state: WidgetState) {
    val now = Date()
    val timeFormat = SimpleDateFormat("h:mm a", Locale.US)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Constants.PanelBg))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Clock
        Text(
            text = timeFormat.format(now),
            style = TextStyle(
                color = ColorProvider(Constants.GoldAccent),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        )

        Spacer(GlanceModifier.height(6.dp))

        val flight = state.nearestFlight
        if (flight != null) {
            // Flight info
            Row(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "✈ ",
                    style = TextStyle(
                        color = ColorProvider(Constants.GoldAccent),
                        fontSize = 10.sp
                    )
                )
                FlapText(text = flight.callsign.take(7).uppercase())
            }

            Spacer(GlanceModifier.height(4.dp))

            // Route IATA codes
            val route = state.routeFor(flight)
            if (route != null) {
                Row(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = route.origin,
                        style = TextStyle(
                            color = ColorProvider(Constants.TextPrimary),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                    Text(
                        text = " → ",
                        style = TextStyle(
                            color = ColorProvider(Constants.GoldAccent),
                            fontSize = 10.sp
                        )
                    )
                    Text(
                        text = route.dest,
                        style = TextStyle(
                            color = ColorProvider(Constants.TextPrimary),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }

            // Distance + direction
            Text(
                text = "${"%.1f".format(flight.distanceMi)} mi · ${flight.direction}",
                style = TextStyle(
                    color = ColorProvider(Constants.TextDim),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            )

            // More flights indicator
            if (state.flights.size > 1) {
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = "+${state.flights.size - 1} more",
                    style = TextStyle(
                        color = ColorProvider(Constants.TextDim),
                        fontSize = 9.sp
                    )
                )
            }
        } else {
            // No flights
            Text(
                text = "👀",
                style = TextStyle(fontSize = 20.sp)
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = "Clear skies",
                style = TextStyle(
                    color = ColorProvider(Constants.TextDim),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            )
        }

        Spacer(GlanceModifier.height(6.dp))

        // Weather
        val weather = state.weather
        if (weather != null) {
            Text(
                text = "${weather.emoji} ${state.tempString(weather)}",
                style = TextStyle(
                    color = ColorProvider(Constants.TextDim),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            )
        }
    }
}
