package com.tuusuario.carlauncher.services

import android.content.Context
import android.location.Location
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

data class WeatherSnapshot(
    val currentTempC: Int,
    val currentLabel: String,
    val currentCode: Int,
    val isDay: Boolean,
    val nextHourTime: String,
    val nextTempC: Int,
    val nextLabel: String,
    val nextCode: Int,
    val isLoaded: Boolean = true
)

/**
 * Servicio ligero de clima GPS en vivo (Open-Meteo, sin API key) con caché instantáneo.
 * Alimenta el widget de clima doble ("Ahora" | "Siguiente") de la pantalla principal.
 */
object WeatherService {

    val weather = mutableStateOf(
        WeatherSnapshot(
            currentTempC = 28,
            currentLabel = "Parcial nublado",
            currentCode = 2,
            isDay = true,
            nextHourTime = "--:00",
            nextTempC = 28,
            nextLabel = "Parcial nublado",
            nextCode = 2,
            isLoaded = false
        )
    )

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastFetchTimeMs = 0L
    private var lastLat = Double.NaN
    private var lastLon = Double.NaN
    private var isFetching = false
    private var initializedCache = false

    fun init(context: Context) {
        if (initializedCache) return
        initializedCache = true
        val prefs = context.getSharedPreferences("WeatherCachePrefs", Context.MODE_PRIVATE)
        if (prefs.contains("currentTempC")) {
            weather.value = WeatherSnapshot(
                currentTempC = prefs.getInt("currentTempC", 28),
                currentLabel = prefs.getString("currentLabel", "Nublado") ?: "Nublado",
                currentCode = prefs.getInt("currentCode", 2),
                isDay = prefs.getBoolean("isDay", true),
                nextHourTime = prefs.getString("nextHourTime", "12:00") ?: "12:00",
                nextTempC = prefs.getInt("nextTempC", 28),
                nextLabel = prefs.getString("nextLabel", "Nublado") ?: "Nublado",
                nextCode = prefs.getInt("nextCode", 2),
                isLoaded = true
            )
        }
    }

    fun onLocationAvailable(context: Context, lat: Double, lon: Double, force: Boolean = false) {
        if (lat == 0.0 && lon == 0.0) return
        val now = System.currentTimeMillis()
        val movedMeters = if (!lastLat.isNaN() && !lastLon.isNaN()) {
            val out = FloatArray(1)
            Location.distanceBetween(lastLat, lastLon, lat, lon, out)
            out[0]
        } else Float.MAX_VALUE

        val fifteenMinutes = 15 * 60 * 1000L
        if (!force && isFetching) return
        if (!force && lastFetchTimeMs > 0L && (now - lastFetchTimeMs) < fifteenMinutes && movedMeters < 5000f) {
            return
        }

        isFetching = true
        val appCtx = context.applicationContext
        ioScope.launch {
            try {
                val urlStr = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,is_day&hourly=temperature_2m,weather_code&timezone=auto&forecast_days=2"
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 7000
                    readTimeout = 7000
                    setRequestProperty("User-Agent", "CarLauncherRemake/2.0")
                }
                val text = try {
                    if (conn.responseCode in 200..299) {
                        conn.inputStream.bufferedReader().readText()
                    } else null
                } finally {
                    conn.disconnect()
                } ?: return@launch

                val root = JSONObject(text)
                val current = root.getJSONObject("current")
                val currTemp = current.getDouble("temperature_2m").roundToInt()
                val currCode = current.getInt("weather_code")
                val isDay = current.optInt("is_day", 1) == 1

                val hourly = root.getJSONObject("hourly")
                val times = hourly.getJSONArray("time")
                val temps = hourly.getJSONArray("temperature_2m")
                val codes = hourly.getJSONArray("weather_code")

                val nextHourTarget = LocalDateTime.now().plusHours(1)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:00"))
                var nextIdx = 0
                for (i in 0 until times.length()) {
                    if (times.getString(i) >= nextHourTarget) {
                        nextIdx = i
                        break
                    }
                }

                val nextTimeRaw = times.optString(nextIdx, "")
                val nextHourDisplay = if (nextTimeRaw.contains("T")) nextTimeRaw.substringAfter("T").take(5) else "12:00"
                val nextTemp = temps.optDouble(nextIdx, currTemp.toDouble()).roundToInt()
                val nextCode = codes.optInt(nextIdx, currCode)

                val snap = WeatherSnapshot(
                    currentTempC = currTemp,
                    currentLabel = weatherCodeToSpanish(currCode),
                    currentCode = currCode,
                    isDay = isDay,
                    nextHourTime = nextHourDisplay,
                    nextTempC = nextTemp,
                    nextLabel = weatherCodeToSpanish(nextCode),
                    nextCode = nextCode,
                    isLoaded = true
                )

                withContext(Dispatchers.Main) {
                    weather.value = snap
                }
                lastFetchTimeMs = System.currentTimeMillis()
                lastLat = lat
                lastLon = lon

                appCtx.getSharedPreferences("WeatherCachePrefs", Context.MODE_PRIVATE).edit()
                    .putInt("currentTempC", snap.currentTempC)
                    .putString("currentLabel", snap.currentLabel)
                    .putInt("currentCode", snap.currentCode)
                    .putBoolean("isDay", snap.isDay)
                    .putString("nextHourTime", snap.nextHourTime)
                    .putInt("nextTempC", snap.nextTempC)
                    .putString("nextLabel", snap.nextLabel)
                    .putInt("nextCode", snap.nextCode)
                    .apply()
            } catch (_: Exception) {
            } finally {
                isFetching = false
            }
        }
    }

    private fun weatherCodeToSpanish(code: Int): String = when (code) {
        0 -> "Despejado"
        1 -> "Mayormente claro"
        2 -> "Parcial nublado"
        3 -> "Nublado"
        45, 48 -> "Neblina"
        51, 53, 55, 56, 57 -> "Llovizna"
        61, 63, 65, 66, 67, 80, 81, 82 -> "Lluvia"
        71, 73, 75, 77, 85, 86 -> "Nieve"
        95, 96, 99 -> "Tormenta"
        else -> "Nublado"
    }
}
