package com.plane.tracker.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ── adsb.fi response (via Cloudflare Worker) ──

@JsonClass(generateAdapter = true)
data class ADSBResponse(
    @Json(name = "ac") val aircraft: List<ADSBAircraft>? = null,
    @Json(name = "msg") val msg: String? = null,
    @Json(name = "total") val total: Int? = null,
    @Json(name = "now") val now: Long? = null
)

@JsonClass(generateAdapter = true)
data class ADSBAircraft(
    @Json(name = "hex") val hex: String = "",
    @Json(name = "flight") val flight: String? = null,
    @Json(name = "lat") val lat: Double? = null,
    @Json(name = "lon") val lon: Double? = null,
    @Json(name = "alt_baro") val altBaro: Any? = null,       // can be Int or "ground"
    @Json(name = "alt_geom") val altGeom: Int? = null,
    @Json(name = "gs") val gs: Double? = null,                // ground speed knots
    @Json(name = "track") val track: Double? = null,
    @Json(name = "baro_rate") val baroRate: Int? = null,      // ft/min
    @Json(name = "t") val type: String? = null,               // ICAO type code
    @Json(name = "r") val registration: String? = null,
    @Json(name = "dst") val distance: Double? = null,         // nautical miles
    @Json(name = "nav_altitude_mcp") val navAltitude: Int? = null,
    @Json(name = "nav_modes") val navModes: List<String>? = null
) {
    /** Parse alt_baro which can be a number or "ground" */
    fun altitudeBaroFt(): Int? = when (altBaro) {
        is Number -> altBaro.toInt()
        else -> null  // "ground" or null
    }
}

// ── adsbdb.com route response ──

@JsonClass(generateAdapter = true)
data class ADSBDBResponse(
    @Json(name = "response") val response: ADSBDBInner? = null
)

@JsonClass(generateAdapter = true)
data class ADSBDBInner(
    @Json(name = "flightroute") val flightRoute: ADSBDBFlightRoute? = null
)

@JsonClass(generateAdapter = true)
data class ADSBDBFlightRoute(
    @Json(name = "origin") val origin: ADSBDBAirport? = null,
    @Json(name = "destination") val destination: ADSBDBAirport? = null,
    @Json(name = "airline") val airline: ADSBDBAirline? = null
)

@JsonClass(generateAdapter = true)
data class ADSBDBAirport(
    @Json(name = "iata_code") val iataCode: String? = null,
    @Json(name = "icao_code") val icaoCode: String? = null,
    @Json(name = "municipality") val municipality: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "latitude") val latitude: Double? = null,
    @Json(name = "longitude") val longitude: Double? = null,
    @Json(name = "country_iso_name") val country: String? = null
)

@JsonClass(generateAdapter = true)
data class ADSBDBAirline(
    @Json(name = "name") val name: String? = null,
    @Json(name = "iata") val iata: String? = null,
    @Json(name = "icao") val icao: String? = null
)

// ── hexdb.io route response ──

@JsonClass(generateAdapter = true)
data class HexDBResponse(
    @Json(name = "route") val route: String? = null  // e.g. "EGLL-KIAD"
)
