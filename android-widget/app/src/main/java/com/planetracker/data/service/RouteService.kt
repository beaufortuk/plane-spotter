package com.planetracker.data.service

import com.planetracker.data.model.ADSBDBCallsignResponse
import com.planetracker.data.model.Flight
import com.planetracker.data.model.RouteInfo
import com.planetracker.util.Constants
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object RouteService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val responseAdapter = moshi.adapter(ADSBDBCallsignResponse::class.java)

    /** Fetch route info for a callsign from adsbdb */
    suspend fun fetchRoute(callsign: String): RouteInfo? = withContext(Dispatchers.IO) {
        val cleaned = callsign.trim()
        if (cleaned.isEmpty()) return@withContext null

        try {
            val encoded = URLEncoder.encode(cleaned, "UTF-8")
            val url = "${Constants.ADSBDB_BASE}/callsign/$encoded"
            val request = Request.Builder().url(url).build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val parsed = responseAdapter.fromJson(body) ?: return@withContext null
            val fr = parsed.response?.flightroute ?: return@withContext null
            RouteInfo.from(fr)
        } catch (_: Exception) {
            null
        }
    }

    /** Fetch routes for multiple flights concurrently */
    suspend fun fetchRoutes(flights: List<Flight>): Map<String, RouteInfo> =
        coroutineScope {
            flights.map { flight ->
                async {
                    val route = fetchRoute(flight.callsign)
                    if (route != null) flight.callsign to route else null
                }
            }
                .awaitAll()
                .filterNotNull()
                .toMap()
        }
}
