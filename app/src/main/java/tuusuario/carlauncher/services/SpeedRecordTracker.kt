package com.tuusuario.carlauncher.services

import android.content.Context
import android.location.Location
import android.os.Environment
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class SpeedRecord(
    val date: String,
    val maxSpeedKmH: Float,
    val time: String,
    val lat: Double,
    val lon: Double,
    val locationLabel: String
)

enum class RecordAlertType { DAY_RECORD, ALL_TIME_RECORD }

data class RecordAlert(
    val type: RecordAlertType,
    val speedKmH: Float,
    val previousRecord: Float
)

@OptIn(ExperimentalCoroutinesApi::class)
object SpeedRecordTracker {

    val recordAlert = mutableStateOf<RecordAlert?>(null)
    val todayMaxLive = mutableStateOf(0f)
    val allTimeMaxLive = mutableStateOf(0f)

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private var saveJob: Job? = null

    private var pendingAlert: RecordAlert? = null
    private var lastShownSpeedKmH: Float = 0f

    private var recordsFile: File? = null
    private val records = java.util.concurrent.ConcurrentHashMap<String, SpeedRecord>()

    private var todayDate: String = ""
    private var todayMaxKmH: Float = 0f
    private var allTimeMaxKmH: Float = 0f

    fun init(context: Context) {
        val base = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "CarLauncher/Records"
        )
        recordsFile = File(base, "records.json")
        todayDate = LocalDate.now().toString()

        ioScope.launch {
            if (!base.exists()) base.mkdirs()
            loadAll()
            val dayMax = records[todayDate]?.maxSpeedKmH ?: 0f
            val bestMax = records.values.maxOfOrNull { it.maxSpeedKmH } ?: 0f
            withContext(Dispatchers.Main) {
                todayMaxKmH = dayMax
                allTimeMaxKmH = bestMax
                todayMaxLive.value = dayMax
                allTimeMaxLive.value = bestMax
            }
        }
    }

    fun onSpeedUpdate(speedKmH: Float, location: Location?) {
        val today = LocalDate.now().toString()
        if (today != todayDate) {
            todayDate = today
            todayMaxKmH = 0f
            todayMaxLive.value = 0f
        }

        if (speedKmH < 10f) {
            dispatchPendingAlertIfNeeded()
            return
        }

        val isNewDayRecord = speedKmH > todayMaxKmH
        val isNewAllTime = speedKmH > allTimeMaxKmH

        if (isNewDayRecord) {
            val prevDay = todayMaxKmH
            val prevAllTime = allTimeMaxKmH

            todayMaxKmH = speedKmH
            todayMaxLive.value = speedKmH

            val now = LocalDateTime.now()
            val timeStr = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            val label = if (location != null)
                "%.4f°, %.4f°".format(location.latitude, location.longitude)
            else ""

            records[today] = SpeedRecord(
                date = today,
                maxSpeedKmH = speedKmH,
                time = timeStr,
                lat = location?.latitude ?: 0.0,
                lon = location?.longitude ?: 0.0,
                locationLabel = label
            )

            if (isNewAllTime) {
                allTimeMaxKmH = speedKmH
                allTimeMaxLive.value = speedKmH
            }

            // Escritura asíncrona debounced (evita bloquear el hilo principal durante aceleración)
            scheduleSave()

            val alertType = if (isNewAllTime && prevAllTime > 0f)
                RecordAlertType.ALL_TIME_RECORD
            else
                RecordAlertType.DAY_RECORD

            val prevRecord = when {
                isNewAllTime && prevAllTime > 0f -> prevAllTime
                prevDay > 0f -> prevDay
                else -> 0f
            }

            pendingAlert = RecordAlert(
                type = alertType,
                speedKmH = speedKmH,
                previousRecord = prevRecord
            )
        }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        val snapshot = records.values.sortedByDescending { it.date }
        saveJob = ioScope.launch {
            delay(1200)
            saveSnapshot(snapshot)
        }
    }

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

    fun triggerTestAlert(isAllTime: Boolean = false) {
        val fakeSpeed = if (isAllTime) 142f else 97f
        val fakePrev = if (isAllTime) 130f else 85f
        recordAlert.value = RecordAlert(
            type = if (isAllTime) RecordAlertType.ALL_TIME_RECORD else RecordAlertType.DAY_RECORD,
            speedKmH = fakeSpeed,
            previousRecord = fakePrev
        )
    }

    fun getAllRecords(): List<SpeedRecord> = records.values.sortedByDescending { it.date }

    fun getAllTimeRecord(): SpeedRecord? = records.values.maxByOrNull { it.maxSpeedKmH }

    fun getTodayRecord(): SpeedRecord? = records[LocalDate.now().toString()]

    private fun loadAll() {
        records.clear()
        val file = recordsFile ?: return
        if (!file.exists()) return
        try {
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val rec = SpeedRecord(
                    date = obj.optString("date"),
                    maxSpeedKmH = obj.optDouble("maxSpeedKmH", 0.0).toFloat(),
                    time = obj.optString("time"),
                    lat = obj.optDouble("lat", 0.0),
                    lon = obj.optDouble("lon", 0.0),
                    locationLabel = obj.optString("locationLabel")
                )
                if (rec.date.isNotBlank()) records[rec.date] = rec
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveSnapshot(snapshot: List<SpeedRecord>) {
        val file = recordsFile ?: return
        try {
            file.parentFile?.let { if (!it.exists()) it.mkdirs() }
            val arr = JSONArray()
            snapshot.forEach { rec ->
                val obj = JSONObject()
                obj.put("date", rec.date)
                obj.put("maxSpeedKmH", rec.maxSpeedKmH.toDouble())
                obj.put("time", rec.time)
                obj.put("lat", rec.lat)
                obj.put("lon", rec.lon)
                obj.put("locationLabel", rec.locationLabel)
                arr.put(obj)
            }
            file.writeText(arr.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
