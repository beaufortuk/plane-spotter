package com.plane.tracker.data.remote

import retrofit2.http.GET
import retrofit2.http.Path

interface ADSBDBApi {
    @GET("/api/v0/callsign/{callsign}")
    suspend fun getRoute(
        @Path("callsign") callsign: String
    ): ADSBDBResponse

    companion object {
        const val BASE_URL = "https://api.adsbdb.com"
    }
}

interface HexDBApi {
    @GET("/api/v1/route/icao/{callsign}")
    suspend fun getRoute(
        @Path("callsign") callsign: String
    ): HexDBResponse

    companion object {
        const val BASE_URL = "https://hexdb.io"
    }
}
