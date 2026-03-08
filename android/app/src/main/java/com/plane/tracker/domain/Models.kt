package com.plane.tracker.domain

/** Processed flight ready for display */
data class Flight(
    val icao24: String,
    val callsign: String,
    val lat: Double,
    val lon: Double,
    val altFt: Int,
    val speedKts: Int,
    val heading: Float,
    val vrateFpm: Int,
    val type: String,
    val reg: String,
    val distMi: Double,
    val direction: String,
    val navAlt: Int?,
    val navModes: List<String>
)

/** Resolved route information */
data class Route(
    val origin: String,
    val dest: String,
    val originName: String,
    val destName: String,
    val originLat: Double,
    val originLon: Double,
    val destLat: Double,
    val destLon: Double,
    val airlineName: String,
    val airlineIata: String
)

/** Flight classification tag */
data class Classification(
    val tag: String,
    val tier: Tier
)

enum class Tier { RARE, UNCOMMON }

/** Flight phase for arc rendering */
enum class FlightPhase { CLIMB, CRUISE, DESCENT }

/** Combined state for a tracked flight */
data class TrackedFlight(
    val flight: Flight,
    val route: Route?,
    val classification: Classification?,
    val progress: Float  // 0..1
)
