package com.planetracker.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.state.GlanceStateDefinition
import com.planetracker.data.model.Flight
import com.planetracker.data.model.RouteInfo
import com.planetracker.data.model.WeatherInfo
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File

/** Serializable widget state */
@JsonClass(generateAdapter = true)
data class WidgetState(
    val flights: List<Flight> = emptyList(),
    val routes: Map<String, RouteInfo> = emptyMap(),
    val weather: WeatherInfo? = null,
    val lastUpdate: Long = 0L,
    val error: String? = null,
    val useFahrenheit: Boolean = false
) {
    val nearestFlight: Flight? get() = flights.firstOrNull()

    /** Route for a given flight (with plausibility check) */
    fun routeFor(flight: Flight): RouteInfo? {
        val route = routes[flight.callsign] ?: return null
        return if (route.isPlausible(flight.lat, flight.lon)) route else null
    }

    /** Temperature string using the configured unit */
    fun tempString(weather: WeatherInfo): String =
        if (useFahrenheit) "${weather.tempF}°F" else "${Math.round(weather.tempC)}°C"

    /** Vertical status info */
    data class VerticalStatus(val text: String, val symbol: String)

    companion object {
        val EMPTY = WidgetState()

        fun verticalStatus(fpm: Int): VerticalStatus = when {
            fpm > 200 -> VerticalStatus("Climbing", "↑")
            fpm < -200 -> VerticalStatus("Descending", "↓")
            else -> VerticalStatus("Level", "→")
        }

        private val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        private val adapter = moshi.adapter(WidgetState::class.java)

        fun toJson(state: WidgetState): String = adapter.toJson(state)
        fun fromJson(json: String): WidgetState? = try {
            adapter.fromJson(json)
        } catch (_: Exception) {
            null
        }
    }
}

/** File-based Glance state definition for WidgetState */
object PlaneTrackerStateDefinition : GlanceStateDefinition<WidgetState> {
    private const val FILE_NAME = "plane_tracker_widget_state"

    override suspend fun getDataStore(context: Context, fileKey: String) =
        PlaneTrackerDataStore(context, fileKey)

    override fun getLocation(context: Context, fileKey: String): File =
        File(context.filesDir, "$FILE_NAME-$fileKey.json")
}

/** Simple file-backed DataStore for WidgetState */
class PlaneTrackerDataStore(
    private val context: Context,
    private val fileKey: String
) : androidx.datastore.core.DataStore<WidgetState> {
    private val file get() = PlaneTrackerStateDefinition.getLocation(context, fileKey)

    override val data: kotlinx.coroutines.flow.Flow<WidgetState>
        get() = kotlinx.coroutines.flow.flow {
            emit(readState())
        }

    override suspend fun updateData(transform: suspend (WidgetState) -> WidgetState): WidgetState {
        val current = readState()
        val updated = transform(current)
        file.writeText(WidgetState.toJson(updated))
        return updated
    }

    private fun readState(): WidgetState {
        return try {
            if (file.exists()) {
                WidgetState.fromJson(file.readText()) ?: WidgetState.EMPTY
            } else {
                WidgetState.EMPTY
            }
        } catch (_: Exception) {
            WidgetState.EMPTY
        }
    }
}
