package com.plane.tracker.data.repository

import com.plane.tracker.data.local.AppDatabase
import com.plane.tracker.data.local.FlightLogEntity
import com.plane.tracker.data.local.RouteCacheEntity
import com.plane.tracker.data.remote.ADSBApi
import com.plane.tracker.data.remote.ADSBDBApi
import com.plane.tracker.data.remote.HexDBApi
import com.plane.tracker.domain.*
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FlightRepository @Inject constructor(
    private val adsbApi: ADSBApi,
    private val adsbdbApi: ADSBDBApi,
    private val hexdbApi: HexDBApi,
    private val db: AppDatabase
) {
    companion object {
        private const val ROUTE_CACHE_TTL = 24 * 60 * 60 * 1000L   // 24 hours
        private const val MAX_ROUTE_CACHE = 500
        private const val MAX_FLIGHT_LOG = 200
        private const val DEDUP_WINDOW = 60 * 60 * 1000L            // 1 hour
        private const val MIN_ALT_FT = 1000
        private const val HEXDB_TIMEOUT = 5000L                     // 5 seconds
    }

    /** Fetch nearby flights from adsb.fi via Cloudflare Worker */
    suspend fun fetchFlights(lat: Double, lon: Double, radiusNm: Int = 5): List<Flight> {
        return try {
            val response = adsbApi.getNearbyFlights(lat, lon, radiusNm)
            val flights = response.aircraft
                ?.mapNotNull { ac ->
                    val alt = ac.altitudeBaroFt() ?: return@mapNotNull null
                    if (alt < MIN_ALT_FT) return@mapNotNull null
                    val acLat = ac.lat ?: return@mapNotNull null
                    val acLon = ac.lon ?: return@mapNotNull null
                    val dist = if (ac.distance != null) GeoMath.nmToMi(ac.distance) else
                        GeoMath.haversine(lat, lon, acLat, acLon)

                    Flight(
                        icao24 = ac.hex,
                        callsign = ac.flight?.trim() ?: "",
                        lat = acLat,
                        lon = acLon,
                        altFt = alt,
                        speedKts = ac.gs?.toInt() ?: 0,
                        heading = ac.track?.toFloat() ?: 0f,
                        vrateFpm = ac.baroRate ?: 0,
                        type = ac.type ?: "",
                        reg = ac.registration ?: "",
                        distMi = dist,
                        direction = GeoMath.toCardinal(GeoMath.bearing(lat, lon, acLat, acLon)),
                        navAlt = ac.navAltitude,
                        navModes = ac.navModes ?: emptyList()
                    )
                }
                ?.sortedBy { it.distMi }
                ?.take(5)
                ?: emptyList()
            flights
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Resolve route for a callsign — 3-tier fallback */
    suspend fun resolveRoute(callsign: String): Route? {
        if (callsign.isBlank()) return null

        // Check cache first
        val cached = db.routeCacheDao().get(
            callsign, System.currentTimeMillis() - ROUTE_CACHE_TTL
        )
        if (cached != null) return cached.toRoute()

        // Tier 1: adsbdb
        var route = tryAdsbdb(callsign)

        // Tier 2: hexdb
        if (route == null) route = tryHexdb(callsign)

        // Tier 3: airline-only from callsign prefix
        if (route == null) {
            val info = ICAOData.airlineFromCallsign(callsign)
            if (info != null) {
                route = Route(
                    origin = "", dest = "",
                    originName = "", destName = "",
                    originLat = 0.0, originLon = 0.0,
                    destLat = 0.0, destLon = 0.0,
                    airlineName = info.name, airlineIata = info.iata
                )
            }
        }

        // Cache and return
        if (route != null) {
            cacheRoute(callsign, route)
        }
        return route
    }

    private suspend fun tryAdsbdb(callsign: String): Route? = try {
        val resp = adsbdbApi.getRoute(callsign)
        val fr = resp.response?.flightRoute ?: return null
        val origin = fr.origin
        val dest = fr.destination

        if (origin != null && dest != null) {
            val (oCode, oCity) = normaliseAirport(
                origin.iataCode ?: origin.icaoCode ?: "", origin.municipality
            )
            val (dCode, dCity) = normaliseAirport(
                dest.iataCode ?: dest.icaoCode ?: "", dest.municipality
            )
            Route(
                origin = oCode, dest = dCode,
                originName = oCity, destName = dCity,
                originLat = origin.latitude ?: 0.0, originLon = origin.longitude ?: 0.0,
                destLat = dest.latitude ?: 0.0, destLon = dest.longitude ?: 0.0,
                airlineName = fr.airline?.name ?: "",
                airlineIata = fr.airline?.iata ?: ""
            )
        } else {
            // Airline-only from adsbdb
            val info = fr.airline
            if (info != null) Route(
                origin = "", dest = "",
                originName = "", destName = "",
                originLat = 0.0, originLon = 0.0,
                destLat = 0.0, destLon = 0.0,
                airlineName = info.name ?: "", airlineIata = info.iata ?: ""
            ) else null
        }
    } catch (e: Exception) { null }

    private suspend fun tryHexdb(callsign: String): Route? {
        return try {
            val resp = withTimeoutOrNull(HEXDB_TIMEOUT) {
                hexdbApi.getRoute(callsign)
            } ?: return null

            val routeStr = resp.route ?: return null
            val parts = routeStr.split("-")
            if (parts.size != 2) return null

            val (oCode, oCity) = normaliseAirport(parts[0].trim(), null)
            val (dCode, dCity) = normaliseAirport(parts[1].trim(), null)
            val airline = ICAOData.airlineFromCallsign(callsign)

            Route(
                origin = oCode, dest = dCode,
                originName = oCity, destName = dCity,
                originLat = 0.0, originLon = 0.0,
                destLat = 0.0, destLon = 0.0,
                airlineName = airline?.name ?: "", airlineIata = airline?.iata ?: ""
            )
        } catch (e: Exception) { null }
    }

    private fun normaliseAirport(code: String, cityHint: String?): Pair<String, String> {
        return ICAOData.normaliseAirport(code, cityHint)
    }

    private suspend fun cacheRoute(callsign: String, route: Route) {
        db.routeCacheDao().insertAndEvict(
            RouteCacheEntity(
                callsign = callsign,
                origin = route.origin, dest = route.dest,
                originName = route.originName, destName = route.destName,
                originLat = route.originLat, originLon = route.originLon,
                destLat = route.destLat, destLon = route.destLon,
                airlineName = route.airlineName, airlineIata = route.airlineIata
            ),
            MAX_ROUTE_CACHE
        )
    }

    /** Log a flight sighting (for first-sighting classification) */
    suspend fun logFlight(flight: Flight, route: Route?) {
        if (flight.callsign.isBlank()) return
        // Dedup: skip if same callsign logged within 1 hour
        val recent = db.flightLogDao().recentEntry(
            flight.callsign, System.currentTimeMillis() - DEDUP_WINDOW
        )
        if (recent != null) return

        db.flightLogDao().insertAndEvict(
            FlightLogEntity(
                callsign = flight.callsign,
                airline = route?.airlineName ?: "",
                origin = route?.origin ?: "",
                dest = route?.dest ?: "",
                altFt = flight.altFt
            ),
            MAX_FLIGHT_LOG
        )
    }

    /** Get all previously seen airline names (for first-sighting check) */
    suspend fun seenAirlines(): Set<String> {
        return db.flightLogDao().allSeenAirlines().toSet()
    }

    private fun RouteCacheEntity.toRoute() = Route(
        origin = origin, dest = dest,
        originName = originName, destName = destName,
        originLat = originLat, originLon = originLon,
        destLat = destLat, destLon = destLon,
        airlineName = airlineName, airlineIata = airlineIata
    )
}
