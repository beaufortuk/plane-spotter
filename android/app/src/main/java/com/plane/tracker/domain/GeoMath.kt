package com.plane.tracker.domain

import kotlin.math.*

object GeoMath {
    private const val R_MI = 3958.8  // Earth radius in statute miles

    /** Haversine distance in statute miles */
    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        return R_MI * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Bearing from (lat1,lon1) to (lat2,lon2) in degrees */
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val y = sin(dLon) * cos(rLat2)
        val x = cos(rLat1) * sin(rLat2) - sin(rLat1) * cos(rLat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    /** Convert bearing degrees to 8-point cardinal direction */
    fun toCardinal(deg: Double): String {
        val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return dirs[((deg + 22.5) / 45).toInt() % 8]
    }

    /** Flight progress along route: 0.0 to 1.0 */
    fun flightProgress(
        flightLat: Double, flightLon: Double,
        originLat: Double, originLon: Double,
        destLat: Double, destLon: Double
    ): Float {
        val total = haversine(originLat, originLon, destLat, destLon)
        if (total < 1.0) return 0.5f
        val fromOrigin = haversine(originLat, originLon, flightLat, flightLon)
        return (fromOrigin / total).coerceIn(0.0, 1.0).toFloat()
    }

    /** Convert m/s to knots */
    fun mpsToKnots(mps: Double): Int = (mps * 1.94384).roundToInt()

    /** Nautical miles to statute miles */
    fun nmToMi(nm: Double): Double = nm * 1.15078
}
