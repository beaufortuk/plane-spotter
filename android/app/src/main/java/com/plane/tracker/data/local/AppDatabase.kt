package com.plane.tracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [RouteCacheEntity::class, FlightLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun routeCacheDao(): RouteCacheDao
    abstract fun flightLogDao(): FlightLogDao
}
