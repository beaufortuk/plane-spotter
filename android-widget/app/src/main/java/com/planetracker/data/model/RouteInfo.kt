package com.planetracker.data.model

import com.planetracker.util.GeoMath
import com.squareup.moshi.JsonClass

/** adsbdb callsign API response */
@JsonClass(generateAdapter = true)
data class ADSBDBCallsignResponse(
    val response: ADSBDBCallsignInner? = null
)

@JsonClass(generateAdapter = true)
data class ADSBDBCallsignInner(
    val flightroute: ADSBDBFlightRoute? = null
)

@JsonClass(generateAdapter = true)
data class ADSBDBFlightRoute(
    val origin: ADSBDBAirport? = null,
    val destination: ADSBDBAirport? = null,
    val airline: ADSBDBAirline? = null
)

@JsonClass(generateAdapter = true)
data class ADSBDBAirport(
    val iata_code: String? = null,
    val icao_code: String? = null,
    val municipality: String? = null,
    val name: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val country_iso_name: String? = null
)

@JsonClass(generateAdapter = true)
data class ADSBDBAirline(
    val name: String? = null,
    val iata: String? = null,
    val icao: String? = null
)

/** Processed route for display */
@JsonClass(generateAdapter = true)
data class RouteInfo(
    val origin: String,         // IATA code
    val dest: String,           // IATA code
    val originName: String,     // city/municipality
    val destName: String,
    val originLat: Double,
    val originLon: Double,
    val destLat: Double,
    val destLon: Double,
    val airlineName: String,
    val airlineIata: String
) {
    /** Check if this route makes sense for an aircraft at the given position */
    fun isPlausible(acLat: Double, acLon: Double): Boolean =
        GeoMath.isRoutePlausible(originLat, originLon, destLat, destLon, acLat, acLon)

    /** Flight progress 0–100 */
    fun progress(acLat: Double, acLon: Double): Double =
        GeoMath.flightProgress(originLat, originLon, destLat, destLon, acLat, acLon)

    companion object {
        fun from(fr: ADSBDBFlightRoute): RouteInfo? {
            return RouteInfo(
                origin = fr.origin?.iata_code ?: fr.origin?.icao_code ?: "???",
                dest = fr.destination?.iata_code ?: fr.destination?.icao_code ?: "???",
                originName = fr.origin?.municipality ?: fr.origin?.name ?: "",
                destName = fr.destination?.municipality ?: fr.destination?.name ?: "",
                originLat = fr.origin?.latitude ?: 0.0,
                originLon = fr.origin?.longitude ?: 0.0,
                destLat = fr.destination?.latitude ?: 0.0,
                destLon = fr.destination?.longitude ?: 0.0,
                airlineName = fr.airline?.name ?: "",
                airlineIata = fr.airline?.iata ?: ""
            )
        }
    }
}
