package com.tuusuario.carlauncher.services

import android.content.Context
import android.location.Location
import android.os.Environment
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// ── Modelos ──────────────────────────────────────────────────────────────────

data class SpeedRecord(
    val date: String,           // "2026-05-29"
    val maxSpeedKmH: Float,     // km/h calibrados
    val time: String,           // "HH:mm:ss" — hora exacta del pico
    val lat: Double,            // latitud en el momento del pico
    val lon: Double,            // longitud
    val locationLabel: String   // descripción aproximada (ej. "22.15°N, 100.98°W")
)

enum class RecordAlertType { DAY_RECORD, ALL_TIME_RECORD }

data class RecordAlert(
    val type: RecordAlertType,
    val speedKmH: Float,
    val previousRecord: Float   // cuánto superó
)

// ── Tracker ──────────────────────────────────────────────────────────────────

object SpeedRecordTracker {

    // Estado observable para la UI del banner
    val recordAlert = mutableStateOf<RecordAlert?>(null)

    private var recordsFile: File? = null
    private val records = mutableMapOf<String, SpeedRecord>() // date → record

    // Máximo del día en curso (en memoria, se persiste al superarse)
    private var todayDate: String = ""
    private var todayMaxKmH: Float = 0f

    // Máximo histórico (en memoria)
    private var allTimeMaxKmH: Float = 0f

    // Cooldown para no disparar el alert varias veces por el mismo pico
    private var lastAlertSpeedKmH: Float = 0f
    private var lastAlertTimeMs: Long = 0L
    private val ALERT_COOLDOWN_MS = 8_000L    // no repetir en 8s
    private val ALERT_DELTA_KMH  = 1.5f       // superar en al menos 1.5 km/h para re-alertar

    // ── Init ─────────────────────────────────────────────────────────────────

    fun init(context: Context) {
        val base = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "CarLauncher/Records"
        )
        if (!base.exists()) base.mkdirs()
        recordsFile = File(base, "records.json")

        loadAll()

        todayDate = LocalDate.now().toString()
        todayMaxKmH = records[todayDate]?.maxSpeedKmH ?: 0f
        allTimeMaxKmH = records.values.maxOfOrNull { it.maxSpeedKmH } ?: 0f
    }

    // ── Llamado en cada tick del velocímetro ─────────────────────────────────

    fun onSpeedUpdate(speedKmH: Float, location: Location?) {
        if (speedKmH < 5f) return  // ignorar velocidades irrelevantes (parado/ruido GPS)

        val today = LocalDate.now().toString()
        if (today != todayDate) {
            // Cambio de día: resetear el máximo del día
            todayDate = today
            todayMaxKmH = 0f
        }

        val isNewDayRecord   = speedKmH > todayMaxKmH
        val isNewAllTime     = speedKmH > allTimeMaxKmH

        if (isNewDayRecord) {
            val prev = todayMaxKmH
            todayMaxKmH = speedKmH

            // Persistir
            val now = LocalDateTime.now()
            val timeStr = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            val label = if (location != null)
                "%.4f°, %.4f°".format(location.latitude, location.longitude)
            else ""

            records[today] = SpeedRecord(
                date          = today,
                maxSpeedKmH   = speedKmH,
                time          = timeStr,
                lat           = location?.latitude  ?: 0.0,
                lon           = location?.longitude ?: 0.0,
                locationLabel = label
            )
            saveAll()

            // Actualizar máximo histórico si aplica
            if (isNewAllTime) {
                allTimeMaxKmH = speedKmH
            }

            // Disparar alert (con cooldown)
            val nowMs = System.currentTimeMillis()
            val speedDelta = speedKmH - lastAlertSpeedKmH
            val timeSinceLast = nowMs - lastAlertTimeMs
            if (timeSinceLast > ALERT_COOLDOWN_MS || speedDelta > ALERT_DELTA_KMH) {
                val alertType = if (isNewAllTime && prev > 0f) RecordAlertType.ALL_TIME_RECORD
                                else RecordAlertType.DAY_RECORD
                // Solo alertar si ya existía un récord previo del día (no al primer fix)
                if (prev > 0f || isNewAllTime) {
                    recordAlert.value = RecordAlert(
                        type           = alertType,
                        speedKmH       = speedKmH,
                        previousRecord = if (isNewAllTime) allTimeMaxKmH else prev
                    )
                    lastAlertSpeedKmH = speedKmH
                    lastAlertTimeMs   = nowMs
                }
            }
        }
    }

    fun dismissAlert() {
        recordAlert.value = null
    }

    // ── Consultas ─────────────────────────────────────────────────────────────

    fun getAllRecords(): List<SpeedRecord> =
        records.values.sortedByDescending { it.date }

    fun getAllTimeRecord(): SpeedRecord? =
        records.values.maxByOrNull { it.maxSpeedKmH }

    fun getTodayRecord(): SpeedRecord? =
        records[LocalDate.now().toString()]

    // ── Persistencia ──────────────────────────────────────────────────────────

    private fun loadAll() {
        records.clear()
        val file = recordsFile ?: return
        if (!file.exists()) return
        try {
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val rec = SpeedRecord(
                    date          = obj.optString("date"),
                    maxSpeedKmH   = obj.optDouble("maxSpeedKmH", 0.0).toFloat(),
                    time          = obj.optString("time"),
                    lat           = obj.optDouble("lat", 0.0),
                    lon           = obj.optDouble("lon", 0.0),
                    locationLabel = obj.optString("locationLabel")
                )
                if (rec.date.isNotBlank()) records[rec.date] = rec
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun saveAll() {
        val file = recordsFile ?: return
        try {
            val arr = JSONArray()
            records.values.sortedByDescending { it.date }.forEach { rec ->
                val obj = JSONObject()
                obj.put("date",          rec.date)
                obj.put("maxSpeedKmH",   rec.maxSpeedKmH.toDouble())
                obj.put("time",          rec.time)
                obj.put("lat",           rec.lat)
                obj.put("lon",           rec.lon)
                obj.put("locationLabel", rec.locationLabel)
                arr.put(obj)
            }
            file.writeText(arr.toString(2))
        } catch (e: Exception) { e.printStackTrace() }
    }
}
