package com.plane.tracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "route_cache")
data class RouteCacheEntity(
    @PrimaryKey val callsign: String,
    val origin: String,
    val dest: String,
    val originName: String,
    val destName: String,
    val originLat: Double,
    val originLon: Double,
    val destLat: Double,
    val destLon: Double,
    val airlineName: String,
    val airlineIata: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "flight_log")
data class FlightLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val callsign: String,
    val airline: String,
    val origin: String,
    val dest: String,
    val altFt: Int,
    val timestamp: Long = System.currentTimeMillis()
)
