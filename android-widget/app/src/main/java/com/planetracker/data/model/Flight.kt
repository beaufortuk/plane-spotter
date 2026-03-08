package com.planetracker.data.model

import com.planetracker.util.Constants
import com.planetracker.util.GeoMath
import com.squareup.moshi.JsonClass

/** Processed flight for display */
@JsonClass(generateAdapter = true)
data class Flight(
    val id: String,              // icao24 hex
    val callsign: String,
    val lat: Double,
    val lon: Double,
    val altFt: Int,
    val speedKts: Int,
    val direction: String,       // cardinal from user to aircraft
    val vertRateFpm: Int,
    val distanceMi: Double,
    val aircraftType: String,
    val registration: String
) {
    companion object {
        /** Create from raw adsb.fi aircraft data */
        fun from(ac: ADSBAircraft, userLat: Double, userLon: Double): Flight? {
            val lat = ac.lat ?: return null
            val lon = ac.lon ?: return null
            val altFt = ac.altBaroFt ?: return null
            if (ac.isGround) return null
            if (altFt < Constants.MIN_ALT_FT) return null

            val distMi = if (ac.dst != null) {
                ac.dst * GeoMath.NM_TO_MI
            } else {
                GeoMath.haversine(userLat, userLon, lat, lon)
            }

            val dir = GeoMath.toCardinal(
                GeoMath.bearing(userLat, userLon, lat, lon)
            )

            val callsign = ac.flight?.trim()?.takeIf { it.isNotEmpty() }
                ?: ac.hex?.uppercase() ?: "???"

            return Flight(
                id = ac.hex ?: "unknown",
                callsign = callsign,
                lat = lat,
                lon = lon,
                altFt = altFt.toInt(),
                speedKts = Math.round(ac.gs ?: 0.0).toInt(),
                direction = dir,
                vertRateFpm = Math.round(ac.baroRate ?: 0.0).toInt(),
                distanceMi = distMi,
                aircraftType = ac.t ?: "",
                registration = ac.r ?: ""
            )
        }
    }
}
