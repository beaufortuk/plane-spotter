package com.planetracker.data.service

import com.planetracker.data.model.OpenMeteoResponse
import com.planetracker.data.model.WeatherInfo
import com.planetracker.util.Constants
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object WeatherService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val responseAdapter = moshi.adapter(OpenMeteoResponse::class.java)

    /** Fetch current weather + 3-day forecast from Open-Meteo */
    suspend fun fetchWeather(lat: Double, lon: Double): WeatherInfo? =
        withContext(Dispatchers.IO) {
            try {
                val url = "${Constants.OPEN_METEO_BASE}" +
                    "?latitude=${"%.4f".format(lat)}" +
                    "&longitude=${"%.4f".format(lon)}" +
                    "&current=temperature_2m,relative_humidity_2m,weather_code,surface_pressure" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                    "&forecast_days=3" +
                    "&timezone=auto"

                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null

                val body = response.body?.string() ?: return@withContext null
                val parsed = responseAdapter.fromJson(body) ?: return@withContext null
                val cur = parsed.current ?: return@withContext null

                WeatherInfo(
                    tempC = cur.temperature_2m ?: 0.0,
                    humidity = (cur.relative_humidity_2m ?: 0.0).toInt(),
                    weatherCode = cur.weather_code ?: 0,
                    pressureHpa = cur.surface_pressure ?: 1013.0,
                    dailyTimes = parsed.daily?.time ?: emptyList(),
                    dailyCodes = parsed.daily?.weather_code ?: emptyList(),
                    dailyMaxTemps = parsed.daily?.temperature_2m_max ?: emptyList(),
                    dailyMinTemps = parsed.daily?.temperature_2m_min ?: emptyList()
                )
            } catch (_: Exception) {
                null
            }
        }
}
