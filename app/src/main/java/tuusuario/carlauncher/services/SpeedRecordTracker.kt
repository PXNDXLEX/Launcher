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
    val previousRecord: Float   // récord anterior que se superó
)

// ── Tracker ──────────────────────────────────────────────────────────────────

object SpeedRecordTracker {

    // Estado observable para la UI del banner.
    // Solo se pone en valor cuando la velocidad baja a < 10 km/h.
    val recordAlert = mutableStateOf<RecordAlert?>(null)

    // Alert pendiente: ya se rompió el récord mientras se iba rápido.
    // Se actualiza cada vez que se rompe un récord nuevo (siempre el más alto).
    // Se despacha a recordAlert cuando la velocidad baja a < 10 km/h.
    private var pendingAlert: RecordAlert? = null

    // Para evitar re-mostrar el mismo aviso si se sube y baja de 10 km/h
    // varias veces sin haber roto un nuevo récord.
    private var lastShownSpeedKmH: Float = 0f

    private var recordsFile: File? = null
    private val records = mutableMapOf<String, SpeedRecord>() // date → record

    // Máximo del día en curso (en memoria, se persiste al superarse)
    private var todayDate: String = ""
    private var todayMaxKmH: Float = 0f

    // Máximo histórico (en memoria)
    private var allTimeMaxKmH: Float = 0f

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
        val today = LocalDate.now().toString()

        // Cambio de día: resetear máximo del día
        if (today != todayDate) {
            todayDate = today
            todayMaxKmH = 0f
        }

        // ── Zona de "detenido": velocidad muy baja o cero ──
        if (speedKmH < 2f) {
            dispatchPendingAlertIfNeeded()
            return
        }

        // ── Zona de "lento" (< 10 km/h): mostrar alerta pendiente ──
        if (speedKmH < 10f) {
            dispatchPendingAlertIfNeeded()
            // No seguimos procesando records a esta velocidad (ruido GPS / parado)
            return
        }

        // ── Zona de conducción activa (>= 10 km/h) ──
        // Ignorar velocidades muy bajas como ruido GPS
        if (speedKmH < 5f) return

        val isNewDayRecord = speedKmH > todayMaxKmH
        val isNewAllTime   = speedKmH > allTimeMaxKmH

        if (isNewDayRecord) {
            val prevDay     = todayMaxKmH
            val prevAllTime = allTimeMaxKmH

            todayMaxKmH = speedKmH

            // Persistir en archivo
            val now     = LocalDateTime.now()
            val timeStr = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            val label   = if (location != null)
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

            // ── Siempre actualizar pendingAlert con el nuevo récord ──
            // (sin cooldown — queremos guardar el más reciente siempre)
            val alertType = if (isNewAllTime && prevAllTime > 0f)
                RecordAlertType.ALL_TIME_RECORD
            else
                RecordAlertType.DAY_RECORD

            val prevRecord = when {
                isNewAllTime && prevAllTime > 0f -> prevAllTime
                prevDay > 0f                    -> prevDay
                else                            -> 0f
            }

            pendingAlert = RecordAlert(
                type           = alertType,
                speedKmH       = speedKmH,
                previousRecord = prevRecord
            )
        }
    }

    /**
     * Muestra el pendingAlert en la UI solo si:
     * 1. Hay un alert pendiente.
     * 2. La velocidad del alert es distinta a la del último alert mostrado
     *    (para no repetir el mismo popup si oscilamos alrededor de 10 km/h).
     */
    private fun dispatchPendingAlertIfNeeded() {
        val pending = pendingAlert ?: return
        if (pending.speedKmH != lastShownSpeedKmH) {
            recordAlert.value = pending
            lastShownSpeedKmH = pending.speedKmH
            pendingAlert = null
        }
    }

    fun dismissAlert() {
        recordAlert.value = null
        pendingAlert = null
        lastShownSpeedKmH = 0f
    }

    /**
     * Muestra una alerta de prueba SIN guardar datos.
     * Solo para verificar que el logro de velocidad se ve y suena correctamente.
     */
    fun triggerTestAlert(isAllTime: Boolean = false) {
        val fakeSpeed = if (isAllTime) 142f else 97f
        val fakePrev  = if (isAllTime) 130f else 85f
        recordAlert.value = RecordAlert(
            type           = if (isAllTime) RecordAlertType.ALL_TIME_RECORD else RecordAlertType.DAY_RECORD,
            speedKmH       = fakeSpeed,
            previousRecord = fakePrev
        )
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
