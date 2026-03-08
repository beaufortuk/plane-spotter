package com.plane.tracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface RouteCacheDao {
    @Query("SELECT * FROM route_cache WHERE callsign = :callsign AND timestamp > :minTimestamp LIMIT 1")
    suspend fun get(callsign: String, minTimestamp: Long): RouteCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RouteCacheEntity)

    @Query("DELETE FROM route_cache WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("SELECT COUNT(*) FROM route_cache")
    suspend fun count(): Int

    @Query("DELETE FROM route_cache WHERE callsign IN (SELECT callsign FROM route_cache ORDER BY timestamp ASC LIMIT :n)")
    suspend fun deleteOldest(n: Int)

    /** Insert and evict atomically */
    @Transaction
    suspend fun insertAndEvict(entity: RouteCacheEntity, maxEntries: Int) {
        insert(entity)
        val c = count()
        if (c > maxEntries) deleteOldest(c - maxEntries)
    }
}

@Dao
interface FlightLogDao {
    @Query("SELECT DISTINCT airline FROM flight_log WHERE airline != ''")
    suspend fun allSeenAirlines(): List<String>

    @Query("SELECT * FROM flight_log WHERE callsign = :callsign AND timestamp > :since LIMIT 1")
    suspend fun recentEntry(callsign: String, since: Long): FlightLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FlightLogEntity)

    @Query("SELECT COUNT(*) FROM flight_log")
    suspend fun count(): Int

    @Query("DELETE FROM flight_log WHERE id IN (SELECT id FROM flight_log ORDER BY timestamp ASC LIMIT :n)")
    suspend fun deleteOldest(n: Int)

    /** Insert and evict atomically */
    @Transaction
    suspend fun insertAndEvict(entity: FlightLogEntity, maxEntries: Int) {
        insert(entity)
        val c = count()
        if (c > maxEntries) deleteOldest(c - maxEntries)
    }
}
