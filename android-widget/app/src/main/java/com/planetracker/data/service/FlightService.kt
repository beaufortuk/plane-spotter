package com.planetracker.data.service

import com.planetracker.data.model.ADSBResponse
import com.planetracker.data.model.Flight
import com.planetracker.util.Constants
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object FlightService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val responseAdapter = moshi.adapter(ADSBResponse::class.java)

    /** Fetch flights near the given coordinates via the Cloudflare Worker proxy */
    suspend fun fetchFlights(
        lat: Double,
        lon: Double,
        radiusNM: Int = Constants.SEARCH_RADIUS_NM
    ): List<Flight> = withContext(Dispatchers.IO) {
        val url = "${Constants.ADSB_PROXY}/lat/${"%.4f".format(lat)}/lon/${"%.4f".format(lon)}/dist/$radiusNM"
        val request = Request.Builder().url(url).build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("API ${response.code}")

        val body = response.body?.string() ?: throw Exception("Empty response")
        val parsed = responseAdapter.fromJson(body) ?: throw Exception("Parse error")

        parsed.allAircraft
            .mapNotNull { Flight.from(it, lat, lon) }
            .sortedBy { it.distanceMi }
            .take(Constants.MAX_FLIGHTS)
    }
}
