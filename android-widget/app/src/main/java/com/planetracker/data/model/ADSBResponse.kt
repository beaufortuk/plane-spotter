package com.planetracker.data.model

import com.squareup.moshi.*

/** Raw adsb.fi API response */
@JsonClass(generateAdapter = true)
data class ADSBResponse(
    val ac: List<ADSBAircraft>? = null,
    val aircraft: List<ADSBAircraft>? = null,
    val msg: String? = null,
    val total: Int? = null
) {
    val allAircraft: List<ADSBAircraft> get() = ac ?: aircraft ?: emptyList()
}

@JsonClass(generateAdapter = true)
data class ADSBAircraft(
    val hex: String? = null,
    val flight: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    @Json(name = "alt_baro") val altBaro: Any? = null,  // can be Double or "ground"
    @Json(name = "alt_geom") val altGeom: Double? = null,
    val gs: Double? = null,       // ground speed (knots)
    val track: Double? = null,
    @Json(name = "baro_rate") val baroRate: Double? = null,  // ft/min
    val t: String? = null,        // aircraft type
    val r: String? = null,        // registration
    val dst: Double? = null       // distance in nautical miles
) {
    /** Get barometric altitude as a number, or null if "ground" or missing */
    val altBaroFt: Double?
        get() = when (altBaro) {
            is Double -> altBaro
            is Number -> altBaro.toDouble()
            is String -> if (altBaro == "ground") null else altBaro.toDoubleOrNull()
            else -> null
        }

    val isGround: Boolean
        get() = altBaro == "ground" || (altBaro is String && altBaro == "ground")
}
