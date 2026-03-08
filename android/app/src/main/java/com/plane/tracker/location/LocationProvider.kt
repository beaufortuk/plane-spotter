package com.plane.tracker.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationClient: FusedLocationProviderClient
) {
    companion object {
        // Default: London
        const val DEFAULT_LAT = 51.5074
        const val DEFAULT_LON = -0.1278

        private val KEY_LAT = doublePreferencesKey("lat")
        private val KEY_LON = doublePreferencesKey("lon")
        private val KEY_USE_GPS = booleanPreferencesKey("use_gps")
        private val KEY_UNITS = stringPreferencesKey("units")
    }

    data class Location(val lat: Double, val lon: Double)

    /** Get current location — GPS if available + permitted, else saved/default */
    suspend fun getLocation(): Location {
        val useGps = context.dataStore.data.first()[KEY_USE_GPS] ?: true

        if (useGps && hasLocationPermission()) {
            val cts = CancellationTokenSource()
            try {
                val loc = locationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cts.token
                ).await()
                if (loc != null) {
                    saveLocation(loc.latitude, loc.longitude)
                    return Location(loc.latitude, loc.longitude)
                }
            } catch (_: SecurityException) { }
            catch (_: Exception) { }
            finally { cts.cancel() }
        }

        // Fall back to saved or default
        val prefs = context.dataStore.data.first()
        return Location(
            lat = prefs[KEY_LAT] ?: DEFAULT_LAT,
            lon = prefs[KEY_LON] ?: DEFAULT_LON
        )
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun saveLocation(lat: Double, lon: Double) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAT] = lat
            prefs[KEY_LON] = lon
        }
    }

    suspend fun setUseGps(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USE_GPS] = enabled
        }
    }

    val useGpsFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_USE_GPS] ?: true }

    // Units preference
    suspend fun setUnits(units: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_UNITS] = units
        }
    }

    val unitsFlow: Flow<String> = context.dataStore.data.map { it[KEY_UNITS] ?: "imperial" }
}
