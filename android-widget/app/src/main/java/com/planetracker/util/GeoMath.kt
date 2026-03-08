package com.planetracker.util

import kotlin.math.*

object GeoMath {
    private const val EARTH_RADIUS_MI = 3958.8
    const val NM_TO_MI = 1.15078

    /** Haversine distance in statute miles */
    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return EARTH_RADIUS_MI * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Bearing from point 1 to point 2 (degrees, 0=N clockwise) */
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(Math.toRadians(lat2))
        val x = cos(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) -
                sin(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    /** Cardinal direction string */
    fun toCardinal(deg: Double): String {
        val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return dirs[(Math.round(deg / 45).toInt()) % 8]
    }

    /** Route plausibility check — triangle inequality */
    fun isRoutePlausible(
        originLat: Double, originLon: Double,
        destLat: Double, destLon: Double,
        acLat: Double, acLon: Double
    ): Boolean {
        val routeDist = haversine(originLat, originLon, destLat, destLon)
        if (routeDist <= 50) return true
        val toOrig = haversine(acLat, acLon, originLat, originLon)
        val toDest = haversine(acLat, acLon, destLat, destLon)
        val detour = (toOrig + toDest) / routeDist
        return detour < 1.5
    }

    /** Flight progress percentage (0–100) based on position between origin and dest */
    fun flightProgress(
        originLat: Double, originLon: Double,
        destLat: Double, destLon: Double,
        acLat: Double, acLon: Double
    ): Double {
        val total = haversine(originLat, originLon, destLat, destLon)
        if (total <= 0) return 50.0
        val flown = haversine(originLat, originLon, acLat, acLon)
        return (flown / total * 100).coerceIn(2.0, 98.0)
    }
}
