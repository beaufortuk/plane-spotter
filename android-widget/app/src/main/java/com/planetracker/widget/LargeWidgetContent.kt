package com.planetracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.planetracker.data.model.Flight
import com.planetracker.util.Constants
import com.planetracker.util.WMOCodes
import com.planetracker.widget.components.FlapText
import com.planetracker.widget.components.StatCard
import com.planetracker.widget.components.RouteArcBitmap
import com.planetracker.widget.components.VerticalSpeedCard
import java.text.SimpleDateFormat
import java.util.*

/** Large widget — full Solari board with route arc, stats, weather (mirrors LargeWidgetView.swift) */
@Composable
fun LargeWidgetContent(state: WidgetState) {
    val flight = state.nearestFlight

    if (flight != null) {
        LargeFlightView(state, flight)
    } else {
        LargeEmptyView(state)
    }
}

@Composable
private fun LargeFlightView(state: WidgetState, flight: Flight) {
    val context = LocalContext.current
    val route = state.routeFor(flight)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Constants.PanelBg))
    ) {
        // ── Header: airline + callsign ──────────────────────────────────
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Airline name + callsign
            Column(modifier = GlanceModifier.defaultWeight()) {
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
                FlapText(text = flight.callsign.take(8))
            }

            // Aircraft type
            if (flight.aircraftType.isNotEmpty()) {
                Text(
                    text = flight.aircraftType,
                    style = TextStyle(
                        color = ColorProvider(Constants.TextDim),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )
                Spacer(GlanceModifier.width(8.dp))
            }

            // Flight counter badge
            if (state.flights.size > 1) {
                Box(
                    modifier = GlanceModifier
                        .background(ColorProvider(Constants.FlapBg))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "1/${state.flights.size}",
                        style = TextStyle(
                            color = ColorProvider(Constants.TextDim),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }
        }

        // ── Route: IATA codes + arc ─────────────────────────────────────
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Origin
            Column(
                modifier = GlanceModifier.width(60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FlapText(text = route?.origin ?: "???", isLarge = true)
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = route?.originName ?: "",
                    style = TextStyle(
                        color = ColorProvider(Constants.TextDim),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    maxLines = 1
                )
            }

            // Arc bitmap
            Spacer(GlanceModifier.defaultWeight())

            // Destination
            Column(
                modifier = GlanceModifier.width(60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FlapText(text = route?.dest ?: "???", isLarge = true)
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = route?.destName ?: "",
                    style = TextStyle(
                        color = ColorProvider(Constants.TextDim),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    maxLines = 1
                )
            }
        }

        // Route arc bitmap
        val arcBitmap = RouteArcBitmap.render(
            width = 600,
            height = 120,
            flight = flight,
            route = route,
            userLat = Constants.DEFAULT_LAT,
            userLon = Constants.DEFAULT_LON
        )
        Image(
            provider = ImageProvider(arcBitmap),
            contentDescription = "Flight route arc",
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(horizontal = 8.dp)
        )

        Spacer(GlanceModifier.height(6.dp))

        // ── Stats grid (2×2) ─────────────────────────────────────────────
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                StatCard(
                    label = "ALTITUDE",
                    value = "%,d".format(flight.altFt),
                    unit = "ft",
                    modifier = GlanceModifier.fillMaxWidth()
                )
                Spacer(GlanceModifier.height(4.dp))
                StatCard(
                    label = "DISTANCE",
                    value = "%.1f".format(flight.distanceMi),
                    unit = "mi ${flight.direction}",
                    modifier = GlanceModifier.fillMaxWidth()
                )
            }
            Spacer(GlanceModifier.width(4.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                StatCard(
                    label = "GND SPEED",
                    value = "${flight.speedKts}",
                    unit = "kts",
                    modifier = GlanceModifier.fillMaxWidth()
                )
                Spacer(GlanceModifier.height(4.dp))
                VerticalSpeedCard(
                    vertRateFpm = flight.vertRateFpm,
                    modifier = GlanceModifier.fillMaxWidth()
                )
            }
        }

        Spacer(GlanceModifier.defaultWeight())

        // ── Footer: weather + update time ────────────────────────────────
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val weather = state.weather
            if (weather != null) {
                Text(
                    text = "${weather.emoji} ${state.tempString(weather)} · ${weather.humidity}% RH",
                    style = TextStyle(
                        color = ColorProvider(Constants.TextDim),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )
            }

            Spacer(GlanceModifier.defaultWeight())

            // Status dot + time
            val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
            val updateTime = if (state.lastUpdate > 0) {
                timeFormat.format(Date(state.lastUpdate))
            } else "..."

            Text(
                text = "● $updateTime",
                style = TextStyle(
                    color = ColorProvider(
                        if (state.error == null) Constants.StatusOk else Constants.StatusErr
                    ),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            )
        }
    }
}

@Composable
private fun LargeEmptyView(state: WidgetState) {
    val now = Date()
    val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
    val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.US)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Constants.PanelBg))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = timeFormat.format(now),
            style = TextStyle(
                color = ColorProvider(Constants.GoldAccent),
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        )

        Text(
            text = dateFormat.format(now),
            style = TextStyle(
                color = ColorProvider(Constants.TextDim),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        )

        Spacer(GlanceModifier.height(20.dp))

        Text(
            text = "👀",
            style = TextStyle(fontSize = 32.sp)
        )

        Spacer(GlanceModifier.height(8.dp))

        Text(
            text = "NO FLIGHTS OVERHEAD",
            style = TextStyle(
                color = ColorProvider(Constants.TextDim),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        )

        Spacer(GlanceModifier.height(20.dp))

        // Weather
        val weather = state.weather
        if (weather != null) {
            Text(
                text = "${weather.emoji} ${state.tempString(weather)} · ${weather.description}",
                style = TextStyle(
                    color = ColorProvider(Constants.TextDim),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            )

            // 3-day forecast
            Spacer(GlanceModifier.height(12.dp))
            Text(
                text = "3-DAY FORECAST",
                style = TextStyle(
                    color = ColorProvider(Constants.TextDim),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            )
            Spacer(GlanceModifier.height(6.dp))
            ForecastRow(state)
        }
    }
}

@Composable
private fun ForecastRow(state: WidgetState) {
    val weather = state.weather ?: return
    val days = weather.dailyTimes.take(3)

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        days.forEachIndexed { i, dateStr ->
            if (i > 0) Spacer(GlanceModifier.width(16.dp))

            val dayName = try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val date = sdf.parse(dateStr)
                SimpleDateFormat("EEE", Locale.US).format(date!!).uppercase()
            } catch (_: Exception) {
                "???"
            }

            val emoji = weather.dailyCodes.getOrNull(i)?.let { WMOCodes.emoji(it) } ?: "🌡️"
            val maxTemp = weather.dailyMaxTemps.getOrNull(i)?.let {
                if (state.useFahrenheit) "${Math.round(it * 9.0 / 5.0 + 32)}°"
                else "${Math.round(it)}°"
            } ?: "--"
            val minTemp = weather.dailyMinTemps.getOrNull(i)?.let {
                if (state.useFahrenheit) "${Math.round(it * 9.0 / 5.0 + 32)}°"
                else "${Math.round(it)}°"
            } ?: "--"

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = dayName,
                    style = TextStyle(
                        color = ColorProvider(Constants.GoldAccent),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )
                Text(text = emoji, style = TextStyle(fontSize = 16.sp))
                Row {
                    Text(
                        text = maxTemp,
                        style = TextStyle(
                            color = ColorProvider(Constants.TextPrimary),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                    Text(
                        text = " $minTemp",
                        style = TextStyle(
                            color = ColorProvider(Constants.TextDim),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }
        }
    }
}
