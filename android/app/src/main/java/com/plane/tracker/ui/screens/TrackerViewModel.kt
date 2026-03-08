package com.plane.tracker.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plane.tracker.data.repository.FlightRepository
import com.plane.tracker.domain.*
import com.plane.tracker.location.LocationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class TrackerViewModel @Inject constructor(
    private val repository: FlightRepository,
    private val locationProvider: LocationProvider
) : ViewModel() {

    companion object {
        private const val REFRESH_MS = 15_000L
        private const val CYCLE_MS = 12_000L
        private const val BACKOFF_MS = 30_000L
    }

    data class UiState(
        val flights: List<TrackedFlight> = emptyList(),
        val currentIndex: Int = 0,
        val isLoading: Boolean = true,
        val error: String? = null,
        val locationAvailable: Boolean = false
    ) {
        val currentFlight: TrackedFlight? get() = flights.getOrNull(currentIndex)
        val flightCount: Int get() = flights.size
        val displayIndex: Int get() = if (flights.isEmpty()) 0 else currentIndex + 1
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var refreshJob: Job? = null
    private var cycleJob: Job? = null
    private val fetchMutex = kotlinx.coroutines.sync.Mutex()

    init {
        startTracking()
    }

    fun startTracking() {
        refreshJob?.cancel()
        cycleJob?.cancel()

        // Periodic flight refresh
        refreshJob = viewModelScope.launch {
            while (isActive) {
                fetchFlights()
                delay(REFRESH_MS)
            }
        }

        // Cycle through flights
        cycleJob = viewModelScope.launch {
            while (isActive) {
                delay(CYCLE_MS)
                val s = _state.value
                if (s.flights.size > 1) {
                    _state.update { it.copy(currentIndex = (it.currentIndex + 1) % it.flights.size) }
                }
            }
        }
    }

    /** Manual refresh — serialised with periodic job via mutex */
    fun refresh() {
        viewModelScope.launch { fetchFlights() }
    }

    private suspend fun fetchFlights() = fetchMutex.withLock {
        try {
            val loc = locationProvider.getLocation()
            _state.update { it.copy(locationAvailable = locationProvider.hasLocationPermission()) }

            val flights = repository.fetchFlights(loc.lat, loc.lon)

            if (flights.isEmpty()) {
                _state.update { it.copy(flights = emptyList(), isLoading = false, error = null) }
                return
            }

            val seenAirlines = repository.seenAirlines().toMutableSet()
            val tracked = flights.map { flight ->
                val route = if (flight.callsign.isNotBlank()) {
                    repository.resolveRoute(flight.callsign)
                } else null

                // Log the flight
                repository.logFlight(flight, route)

                // Calculate progress
                val progress = if (route != null && route.originLat != 0.0 && route.destLat != 0.0) {
                    GeoMath.flightProgress(
                        flight.lat, flight.lon,
                        route.originLat, route.originLon,
                        route.destLat, route.destLon
                    )
                } else 0.5f

                // Classify (update running set to avoid duplicate first-sightings)
                val classification = ClassifyFlight.classify(
                    callsign = flight.callsign,
                    type = flight.type,
                    altFt = flight.altFt,
                    airlineName = route?.airlineName,
                    seenAirlines = seenAirlines
                )

                // Track this airline so next flight in batch won't duplicate FIRST SIGHTING
                route?.airlineName?.takeIf { it.isNotBlank() }?.let { seenAirlines.add(it) }

                TrackedFlight(flight, route, classification, progress)
            }

            _state.update { current ->
                val newIndex = if (current.currentIndex >= tracked.size) 0 else current.currentIndex
                current.copy(flights = tracked, currentIndex = newIndex, isLoading = false, error = null)
            }
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, error = e.message) }
            delay(BACKOFF_MS)
        }
    }

    /** Detect flight phase from telemetry */
    fun detectPhase(flight: Flight, progress: Float): FlightPhase {
        return when {
            flight.vrateFpm > 200 -> FlightPhase.CLIMB
            flight.vrateFpm < -200 -> FlightPhase.DESCENT
            flight.navModes.contains("approach") -> FlightPhase.DESCENT
            flight.navAlt != null && flight.navAlt < flight.altFt - 3000 -> FlightPhase.DESCENT
            flight.altFt > 10_000 -> FlightPhase.CRUISE
            progress < 0.5f -> FlightPhase.CLIMB
            else -> FlightPhase.DESCENT
        }
    }

    override fun onCleared() {
        refreshJob?.cancel()
        cycleJob?.cancel()
        super.onCleared()
    }
}
