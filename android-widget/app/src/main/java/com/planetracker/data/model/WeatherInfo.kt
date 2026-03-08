package com.planetracker.data.model

import com.planetracker.util.WMOCodes
import com.squareup.moshi.JsonClass

/** Open-Meteo API response */
@JsonClass(generateAdapter = true)
data class OpenMeteoResponse(
    val current: OpenMeteoCurrent? = null,
    val daily: OpenMeteoDaily? = null
)

@JsonClass(generateAdapter = true)
data class OpenMeteoCurrent(
    val temperature_2m: Double? = null,
    val relative_humidity_2m: Double? = null,
    val weather_code: Int? = null,
    val surface_pressure: Double? = null
)

@JsonClass(generateAdapter = true)
data class OpenMeteoDaily(
    val time: List<String>? = null,
    val weather_code: List<Int>? = null,
    val temperature_2m_max: List<Double>? = null,
    val temperature_2m_min: List<Double>? = null
)

/** Processed weather for display */
@JsonClass(generateAdapter = true)
data class WeatherInfo(
    val tempC: Double,
    val humidity: Int,
    val weatherCode: Int,
    val pressureHpa: Double,
    val dailyTimes: List<String> = emptyList(),
    val dailyCodes: List<Int> = emptyList(),
    val dailyMaxTemps: List<Double> = emptyList(),
    val dailyMinTemps: List<Double> = emptyList()
) {
    val tempF: Int get() = Math.round(tempC * 9.0 / 5.0 + 32).toInt()
    val emoji: String get() = WMOCodes.emoji(weatherCode)
    val description: String get() = WMOCodes.description(weatherCode)
    val pressureInHg: String get() = "%.2f inHg".format(pressureHpa * 0.02953)
}
