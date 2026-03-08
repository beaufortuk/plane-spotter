package com.plane.tracker.data.remote

import retrofit2.http.GET
import retrofit2.http.Path

interface ADSBApi {
    @GET("/lat/{lat}/lon/{lon}/dist/{dist}")
    suspend fun getNearbyFlights(
        @Path("lat") lat: Double,
        @Path("lon") lon: Double,
        @Path("dist") dist: Int = 5
    ): ADSBResponse

    companion object {
        const val BASE_URL = "https://plane-tracker-proxy.plane-tracker-proxy.workers.dev"
    }
}
