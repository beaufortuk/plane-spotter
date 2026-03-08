package com.planetracker.util

object WMOCodes {
    /** WMO weather code → emoji */
    fun emoji(code: Int): String = when (code) {
        0 -> "\u2600\uFE0F"          // ☀️
        1 -> "\uD83C\uDF24\uFE0F"    // 🌤️
        2 -> "\u26C5"                 // ⛅
        3 -> "\u2601\uFE0F"          // ☁️
        45, 48 -> "\uD83C\uDF2B\uFE0F" // 🌫️
        51, 53 -> "\uD83C\uDF26\uFE0F" // 🌦️
        55, 61, 63 -> "\uD83C\uDF27\uFE0F" // 🌧️
        65 -> "\uD83C\uDF27\uFE0F"   // 🌧️
        71, 73, 77 -> "\uD83C\uDF28\uFE0F" // 🌨️
        75 -> "\u2744\uFE0F"          // ❄️
        80, 81 -> "\uD83C\uDF27\uFE0F" // 🌧️
        82 -> "\u26C8\uFE0F"          // ⛈️
        85, 86 -> "\uD83C\uDF28\uFE0F" // 🌨️
        95, 96, 99 -> "\u26C8\uFE0F"  // ⛈️
        else -> "\uD83C\uDF21\uFE0F"  // 🌡️
    }

    /** WMO weather code → short description */
    fun description(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly Clear"
        2 -> "Partly Cloudy"
        3 -> "Overcast"
        45, 48 -> "Foggy"
        51, 53 -> "Drizzle"
        55 -> "Heavy Drizzle"
        61 -> "Light Rain"
        63 -> "Rain"
        65 -> "Heavy Rain"
        71 -> "Light Snow"
        73 -> "Snow"
        75 -> "Heavy Snow"
        77 -> "Snow Grains"
        80, 81 -> "Showers"
        82 -> "Heavy Showers"
        85, 86 -> "Snow Showers"
        95 -> "Thunderstorm"
        96, 99 -> "Hailstorm"
        else -> "Unknown"
    }
}
