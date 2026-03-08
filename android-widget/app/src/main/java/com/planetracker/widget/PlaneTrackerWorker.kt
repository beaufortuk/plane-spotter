package com.planetracker.widget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import androidx.work.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import com.planetracker.data.service.FlightService
import com.planetracker.data.service.RouteService
import com.planetracker.data.service.WeatherService
import com.planetracker.util.Constants
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.TimeUnit

class PlaneTrackerWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val (lat, lon) = getLocation()
            val state = fetchData(lat, lon)

            // Write state to file for widget to read
            val file = PlaneTrackerStateDefinition.getLocation(applicationContext, "default")
            file.writeText(WidgetState.toJson(state))

            // Update all widget instances
            PlaneTrackerWidget().updateAll(applicationContext)

            Result.success()
        } catch (e: Exception) {
            val errorState = WidgetState(
                error = e.message ?: "Unknown error",
                lastUpdate = System.currentTimeMillis()
            )
            val file = PlaneTrackerStateDefinition.getLocation(applicationContext, "default")
            file.writeText(WidgetState.toJson(errorState))

            try {
                PlaneTrackerWidget().updateAll(applicationContext)
            } catch (_: Exception) {}

            Result.retry()
        }
    }

    private fun getLocation(): Pair<Double, Double> {
        val hasPermission = ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return Constants.DEFAULT_LAT to Constants.DEFAULT_LON
        }

        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(applicationContext)
            val location = Tasks.await(fusedClient.lastLocation, 5, TimeUnit.SECONDS)
            if (location != null) {
                location.latitude to location.longitude
            } else {
                Constants.DEFAULT_LAT to Constants.DEFAULT_LON
            }
        } catch (_: Exception) {
            Constants.DEFAULT_LAT to Constants.DEFAULT_LON
        }
    }

    private suspend fun fetchData(lat: Double, lon: Double): WidgetState = coroutineScope {
        val flights = FlightService.fetchFlights(lat, lon)

        val routesDeferred = async { RouteService.fetchRoutes(flights) }
        val weatherDeferred = async { WeatherService.fetchWeather(lat, lon) }

        val routes = routesDeferred.await()
        val weather = weatherDeferred.await()

        // Read current preferences
        val prefs = applicationContext.getSharedPreferences("plane_tracker", Context.MODE_PRIVATE)
        val useFahrenheit = prefs.getBoolean("use_fahrenheit", false)

        WidgetState(
            flights = flights,
            routes = routes,
            weather = weather,
            lastUpdate = System.currentTimeMillis(),
            error = null,
            useFahrenheit = useFahrenheit
        )
    }

    companion object {
        private const val WORK_NAME = "plane_tracker_refresh"
        private const val ONE_SHOT_WORK_NAME = "plane_tracker_immediate"

        /** Schedule periodic background updates */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicWork = PeriodicWorkRequestBuilder<PlaneTrackerWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWork
            )
        }

        /** Trigger an immediate one-shot update */
        fun refreshNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneShot = OneTimeWorkRequestBuilder<PlaneTrackerWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_SHOT_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                oneShot
            )
        }

        /** Cancel all scheduled work */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
