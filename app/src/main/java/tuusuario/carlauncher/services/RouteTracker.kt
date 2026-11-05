package com.tuusuario.carlauncher.services

import android.content.Context
import android.location.Location
import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class RoutePoint(
    val lat: Double,
    val lon: Double,
    val timestamp: String,
    val note: String? = null
)

data class RouteSegment(
    val startTime: String,   // "HH:mm"
    val endTime: String,     // "HH:mm"
    val points: MutableList<RoutePoint> = mutableListOf()
)

data class DailyRoute(
    val date: String,
    val segments: MutableList<RouteSegment> = mutableListOf(),
    var isDeleted: Boolean = false,
    var deletedAt: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
object RouteTracker {
    private const val MIN_DISTANCE_METERS = 10f
    private const val SEGMENT_DURATION_MINUTES = 30

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private var debouncedSaveJob: Job? = null
    private val lock = Any()

    private var lastRecordedLocation: Location? = null
    private var currentDailyRoute: DailyRoute? = null
    private var currentSegment: RouteSegment? = null
    private var routesDir: File? = null
    private var trashDir: File? = null

    fun init(context: Context) {
        val base = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "CarLauncher/Rutas"
        )
        routesDir = base
        trashDir = File(base, "Papelera")

        val appCtx = context.applicationContext
        ioScope.launch {
            try {
                if (!base.exists()) base.mkdirs()
                val trash = trashDir!!
                if (!trash.exists()) trash.mkdirs()
                migrateFromInternalStorage(appCtx)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getRoutesDir(): File = routesDir ?: throw IllegalStateException("RouteTracker not initialized. Call init(context) first.")
    private fun getTrashDir(): File = trashDir ?: throw IllegalStateException("RouteTracker not initialized. Call init(context) first.")

    private fun migrateFromInternalStorage(context: Context) {
        try {
            val oldBase = File(context.filesDir, "CarLauncher/Rutas")
            if (!oldBase.exists()) return
            val externalBase = getRoutesDir()
            val externalTrash = getTrashDir()

            oldBase.listFiles()?.filter { it.name.startsWith("ruta_") && it.name.endsWith(".json") }?.forEach { oldFile ->
                val newFile = File(externalBase, oldFile.name)
                if (!newFile.exists()) oldFile.copyTo(newFile, overwrite = false)
            }

            val oldTrash = File(oldBase, "Papelera")
            if (oldTrash.exists()) {
                oldTrash.listFiles()?.filter { it.name.startsWith("ruta_") && it.name.endsWith(".json") }?.forEach { oldFile ->
                    val newFile = File(externalTrash, oldFile.name)
                    if (!newFile.exists()) oldFile.copyTo(newFile, overwrite = false)
                }
            }

            oldBase.deleteRecursively()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cleanupOldTrash() {
        ioScope.launch {
            try {
                val files = trashDir?.listFiles() ?: return@launch
                val now = LocalDateTime.now()
                files.forEach { file ->
                    try {
                        val json = JSONObject(file.readText())
                        val deletedAtStr = json.optString("deletedAt", "")
                        if (deletedAtStr.isNotEmpty()) {
                            val deletedAt = LocalDateTime.parse(deletedAtStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                            val daysInTrash = ChronoUnit.DAYS.between(deletedAt, now)
                            if (daysInTrash >= 2) {
                                file.delete()
                            }
                        }
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onLocationUpdate(loc: Location) {
        if (routesDir == null) return

        val lastLoc = lastRecordedLocation
        if (lastLoc == null || lastLoc.distanceTo(loc) >= MIN_DISTANCE_METERS) {
            lastRecordedLocation = Location(loc)
            val lat = loc.latitude
            val lon = loc.longitude
            val now = LocalDateTime.now()
            ioScope.launch {
                recordPointInternal(lat, lon, now)
            }
        }
    }

    private fun recordPointInternal(lat: Double, lon: Double, now: LocalDateTime) {
        val snapshotToSave: DailyRoute = synchronized(lock) {
            val dateStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val timeStr = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            val timeHHmm = timeStr.substring(0, 5)

            if (currentDailyRoute == null || currentDailyRoute?.date != dateStr) {
                currentDailyRoute = loadRoute(dateStr) ?: DailyRoute(dateStr)
                currentSegment = currentDailyRoute?.segments?.lastOrNull()
            }

            val needNewSegment = if (currentSegment == null) {
                true
            } else {
                try {
                    val segStart = LocalTime.parse(currentSegment!!.startTime, DateTimeFormatter.ofPattern("HH:mm"))
                    val currentTime = LocalTime.parse(timeHHmm, DateTimeFormatter.ofPattern("HH:mm"))
                    val diff = ChronoUnit.MINUTES.between(segStart, currentTime)
                    diff >= SEGMENT_DURATION_MINUTES || diff < 0
                } catch (_: Exception) {
                    true
                }
            }

            if (needNewSegment) {
                currentSegment = RouteSegment(startTime = timeHHmm, endTime = timeHHmm)
                currentDailyRoute!!.segments.add(currentSegment!!)
            }

            currentSegment!!.points.add(RoutePoint(lat, lon, timeStr))
            currentSegment = currentSegment!!.copy(endTime = timeHHmm).also { updated ->
                val idx = currentDailyRoute!!.segments.size - 1
                currentDailyRoute!!.segments[idx] = updated
                currentSegment = currentDailyRoute!!.segments[idx]
            }
            deepCopyRoute(currentDailyRoute!!)
        }

        // Escritura diferida para no saturar flash storage mientras conduces
        debouncedSaveJob?.cancel()
        debouncedSaveJob = ioScope.launch {
            delay(2500)
            writeRouteToDisk(snapshotToSave)
        }
    }

    private fun deepCopyRoute(route: DailyRoute): DailyRoute {
        return DailyRoute(
            date = route.date,
            isDeleted = route.isDeleted,
            deletedAt = route.deletedAt,
            segments = route.segments.map { seg ->
                RouteSegment(
                    startTime = seg.startTime,
                    endTime = seg.endTime,
                    points = seg.points.toMutableList()
                )
            }.toMutableList()
        )
    }

    fun saveRoute(route: DailyRoute) {
        synchronized(lock) {
            if (currentDailyRoute?.date == route.date) {
                currentDailyRoute = deepCopyRoute(route)
                currentSegment = currentDailyRoute?.segments?.lastOrNull()
            }
        }
        writeRouteToDisk(route)
    }

    private fun writeRouteToDisk(route: DailyRoute) {
        try {
            val json = JSONObject()
            json.put("date", route.date)
            json.put("isDeleted", route.isDeleted)
            route.deletedAt?.let { json.put("deletedAt", it) }

            val segmentsArray = JSONArray()
            route.segments.forEach { seg ->
                val segObj = JSONObject()
                segObj.put("startTime", seg.startTime)
                segObj.put("endTime", seg.endTime)

                val pointsArray = JSONArray()
                seg.points.forEach { pt ->
                    val ptObj = JSONObject()
                    ptObj.put("lat", pt.lat)
                    ptObj.put("lon", pt.lon)
                    ptObj.put("timestamp", pt.timestamp)
                    pt.note?.let { ptObj.put("note", it) }
                    pointsArray.put(ptObj)
                }
                segObj.put("points", pointsArray)
                segmentsArray.put(segObj)
            }
            json.put("segments", segmentsArray)

            val dir = if (route.isDeleted) getTrashDir() else getRoutesDir()
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "ruta_${route.date}.json")
            file.writeText(json.toString())

            if (route.isDeleted) {
                File(getRoutesDir(), "ruta_${route.date}.json").let { if (it.exists()) it.delete() }
            } else {
                File(getTrashDir(), "ruta_${route.date}.json").let { if (it.exists()) it.delete() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadRoute(dateStr: String): DailyRoute? {
        synchronized(lock) {
            if (currentDailyRoute?.date == dateStr) {
                return deepCopyRoute(currentDailyRoute!!)
            }
        }
        val file = File(getRoutesDir(), "ruta_${dateStr}.json")
        val trashFile = File(getTrashDir(), "ruta_${dateStr}.json")
        val targetFile = when {
            file.exists() -> file
            trashFile.exists() -> trashFile
            else -> return null
        }

        return try {
            val json = JSONObject(targetFile.readText())
            val route = DailyRoute(
                date = json.optString("date", dateStr),
                isDeleted = json.optBoolean("isDeleted", false),
                deletedAt = json.optString("deletedAt", null).takeIf { it != "null" && !it.isNullOrEmpty() }
            )

            if (json.has("segments")) {
                val segArray = json.getJSONArray("segments")
                for (s in 0 until segArray.length()) {
                    val segObj = segArray.getJSONObject(s)
                    val segment = RouteSegment(
                        startTime = segObj.optString("startTime", "00:00"),
                        endTime = segObj.optString("endTime", "00:00")
                    )
                    val ptsArray = segObj.optJSONArray("points") ?: JSONArray()
                    for (i in 0 until ptsArray.length()) {
                        val ptObj = ptsArray.getJSONObject(i)
                        segment.points.add(
                            RoutePoint(
                                lat = ptObj.optDouble("lat", 0.0),
                                lon = ptObj.optDouble("lon", 0.0),
                                timestamp = ptObj.optString("timestamp", "00:00:00"),
                                note = ptObj.optString("note", null).takeIf { it != "null" && !it.isNullOrEmpty() }
                            )
                        )
                    }
                    route.segments.add(segment)
                }
            } else if (json.has("points")) {
                val ptsArray = json.optJSONArray("points") ?: JSONArray()
                if (ptsArray.length() > 0) {
                    val points = mutableListOf<RoutePoint>()
                    for (i in 0 until ptsArray.length()) {
                        val ptObj = ptsArray.getJSONObject(i)
                        points.add(
                            RoutePoint(
                                lat = ptObj.optDouble("lat", 0.0),
                                lon = ptObj.optDouble("lon", 0.0),
                                timestamp = ptObj.optString("timestamp", "00:00:00"),
                                note = ptObj.optString("note", null).takeIf { it != "null" && !it.isNullOrEmpty() }
                            )
                        )
                    }
                    val firstTs = points.first().timestamp
                    val lastTs = points.last().timestamp
                    route.segments.add(
                        RouteSegment(
                            startTime = if (firstTs.length >= 5) firstTs.substring(0, 5) else "00:00",
                            endTime = if (lastTs.length >= 5) lastTs.substring(0, 5) else "00:00",
                            points = points
                        )
                    )
                }
            }
            route
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getAllRoutes(): List<DailyRoute> {
        if (routesDir == null) return emptyList()
        val routes = mutableListOf<DailyRoute>()
        try {
            val seenDates = mutableSetOf<String>()
            synchronized(lock) {
                currentDailyRoute?.let {
                    routes.add(deepCopyRoute(it))
                    seenDates.add(it.date)
                }
            }
            getRoutesDir().listFiles()?.filter { it.name.startsWith("ruta_") && it.name.endsWith(".json") }?.forEach {
                val dateStr = it.name.removePrefix("ruta_").removeSuffix(".json")
                if (seenDates.add(dateStr)) {
                    loadRoute(dateStr)?.let { r -> routes.add(r) }
                }
            }
            getTrashDir().listFiles()?.filter { it.name.startsWith("ruta_") && it.name.endsWith(".json") }?.forEach {
                val dateStr = it.name.removePrefix("ruta_").removeSuffix(".json")
                if (seenDates.add(dateStr)) {
                    loadRoute(dateStr)?.let { r -> routes.add(r) }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return routes.sortedByDescending { it.date }
    }

    fun getAvailableDates(): List<String> {
        if (routesDir == null) return emptyList()
        val dates = mutableSetOf<String>()
        try {
            getRoutesDir().listFiles()?.filter { it.name.startsWith("ruta_") && it.name.endsWith(".json") }?.forEach {
                dates.add(it.name.removePrefix("ruta_").removeSuffix(".json"))
            }
            synchronized(lock) {
                currentDailyRoute?.takeIf { !it.isDeleted }?.let { dates.add(it.date) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return dates.sorted()
    }

    fun getAllPoints(route: DailyRoute): List<RoutePoint> = route.segments.flatMap { it.points }

    fun extractWindowSegment(route: DailyRoute, targetTimeStr: String, windowSeconds: Int = 30): RouteSegment? {
        return try {
            val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
            val targetTime = LocalTime.parse(targetTimeStr, formatter)
            val halfWindow = windowSeconds / 2L

            val windowPoints = getAllPoints(route).filter { pt ->
                try {
                    val ptTime = LocalTime.parse(pt.timestamp, formatter)
                    val diff = java.time.Duration.between(targetTime, ptTime).seconds
                    kotlin.math.abs(diff) <= halfWindow
                } catch (_: Exception) {
                    false
                }
            }
            if (windowPoints.isEmpty()) return null
            RouteSegment(
                startTime = windowPoints.first().timestamp.take(5),
                endTime = windowPoints.last().timestamp.take(5),
                points = windowPoints.toMutableList()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
