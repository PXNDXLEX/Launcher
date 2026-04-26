package com.tuusuario.carlauncher.services

import android.content.Context
import android.location.Location
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
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

object RouteTracker {
    private const val MIN_DISTANCE_METERS = 10f
    private const val SEGMENT_DURATION_MINUTES = 30

    private var lastRecordedLocation: Location? = null
    private var currentDailyRoute: DailyRoute? = null
    private var currentSegment: RouteSegment? = null
    private var routesDir: File? = null
    private var trashDir: File? = null

    /**
     * Must be called once with the Application context before any recording happens.
     * Uses external public Documents storage so data persists across app reinstalls.
     */
    fun init(context: Context) {
        val base = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "CarLauncher/Rutas"
        )
        if (!base.exists()) base.mkdirs()
        routesDir = base

        val trash = File(base, "Papelera")
        if (!trash.exists()) trash.mkdirs()
        trashDir = trash

        // Migrate files from old internal storage if they exist
        migrateFromInternalStorage(context)
    }

    private fun getRoutesDir(): File = routesDir ?: throw IllegalStateException("RouteTracker not initialized. Call init(context) first.")
    private fun getTrashDir(): File = trashDir ?: throw IllegalStateException("RouteTracker not initialized. Call init(context) first.")

    /**
     * One-time migration: if routes exist in old internal filesDir, copy them to external storage.
     */
    private fun migrateFromInternalStorage(context: Context) {
        try {
            val oldBase = File(context.filesDir, "CarLauncher/Rutas")
            if (!oldBase.exists()) return
            val externalBase = getRoutesDir()
            val externalTrash = getTrashDir()

            // Copy main route files
            oldBase.listFiles()?.filter { it.name.startsWith("ruta_") && it.name.endsWith(".json") }?.forEach { oldFile ->
                val newFile = File(externalBase, oldFile.name)
                if (!newFile.exists()) oldFile.copyTo(newFile, overwrite = false)
            }

            // Copy trash files
            val oldTrash = File(oldBase, "Papelera")
            if (oldTrash.exists()) {
                oldTrash.listFiles()?.filter { it.name.startsWith("ruta_") && it.name.endsWith(".json") }?.forEach { oldFile ->
                    val newFile = File(externalTrash, oldFile.name)
                    if (!newFile.exists()) oldFile.copyTo(newFile, overwrite = false)
                }
            }

            // Remove old internal directory after migration
            oldBase.deleteRecursively()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cleanupOldTrash() {
        try {
            val files = getTrashDir().listFiles() ?: return
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
                } catch (e: Exception) { /* skip corrupt files */ }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Called on every GPS update. Records the point if it is >= 10m from the last one.
     * Groups points into 30-minute segments automatically.
     */
    fun onLocationUpdate(loc: Location) {
        if (routesDir == null) return // Not initialized

        val lastLoc = lastRecordedLocation
        if (lastLoc == null || lastLoc.distanceTo(loc) >= MIN_DISTANCE_METERS) {
            recordPoint(loc)
            lastRecordedLocation = loc
        }
    }

    private fun recordPoint(loc: Location) {
        try {
            val now = LocalDateTime.now()
            val dateStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val timeStr = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            val timeHHmm = timeStr.substring(0, 5)

            // Load or create the daily route
            if (currentDailyRoute == null || currentDailyRoute?.date != dateStr) {
                currentDailyRoute = loadRoute(dateStr) ?: DailyRoute(dateStr)
                currentSegment = currentDailyRoute?.segments?.lastOrNull()
            }

            // Check if we need a new segment (every 30 minutes)
            val needNewSegment = if (currentSegment == null) {
                true
            } else {
                try {
                    val segStart = LocalTime.parse(currentSegment!!.startTime, DateTimeFormatter.ofPattern("HH:mm"))
                    val currentTime = LocalTime.parse(timeHHmm, DateTimeFormatter.ofPattern("HH:mm"))
                    ChronoUnit.MINUTES.between(segStart, currentTime) >= SEGMENT_DURATION_MINUTES
                } catch (e: Exception) {
                    true
                }
            }

            if (needNewSegment) {
                // Start a new segment
                currentSegment = RouteSegment(
                    startTime = timeHHmm,
                    endTime = timeHHmm
                )
                currentDailyRoute!!.segments.add(currentSegment!!)
            }

            // Add the point
            currentSegment!!.points.add(RoutePoint(loc.latitude, loc.longitude, timeStr))
            currentSegment = currentSegment!!.copy(endTime = timeHHmm).also { updated ->
                // Replace the last segment in the list with the updated one
                val idx = currentDailyRoute!!.segments.size - 1
                currentDailyRoute!!.segments[idx] = updated
                currentSegment = currentDailyRoute!!.segments[idx]
            }

            // Save to disk
            saveRoute(currentDailyRoute!!)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveRoute(route: DailyRoute) {
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
            file.writeText(json.toString(2))

            // If moved to trash, delete the original; and vice versa
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
                    val ptsArray = segObj.optJSONArray("points") ?: org.json.JSONArray()
                    for (i in 0 until ptsArray.length()) {
                        val ptObj = ptsArray.getJSONObject(i)
                        segment.points.add(RoutePoint(
                            lat = ptObj.optDouble("lat", 0.0),
                            lon = ptObj.optDouble("lon", 0.0),
                            timestamp = ptObj.optString("timestamp", "00:00:00"),
                            note = ptObj.optString("note", null).takeIf { it != "null" && !it.isNullOrEmpty() }
                        ))
                    }
                    route.segments.add(segment)
                }
            } else if (json.has("points")) {
                // Legacy format compatibility: convert flat points to a single segment
                val ptsArray = json.optJSONArray("points") ?: org.json.JSONArray()
                if (ptsArray.length() > 0) {
                    val points = mutableListOf<RoutePoint>()
                    for (i in 0 until ptsArray.length()) {
                        val ptObj = ptsArray.getJSONObject(i)
                        points.add(RoutePoint(
                            lat = ptObj.optDouble("lat", 0.0),
                            lon = ptObj.optDouble("lon", 0.0),
                            timestamp = ptObj.optString("timestamp", "00:00:00"),
                            note = ptObj.optString("note", null).takeIf { it != "null" && !it.isNullOrEmpty() }
                        ))
                    }
                    val firstTs = points.first().timestamp
                    val lastTs = points.last().timestamp
                    val seg = RouteSegment(
                        startTime = if (firstTs.length >= 5) firstTs.substring(0, 5) else "00:00",
                        endTime = if (lastTs.length >= 5) lastTs.substring(0, 5) else "00:00",
                        points = points
                    )
                    route.segments.add(seg)
                }
            }

            route
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get all route dates that have data, sorted descending.
     */
    fun getAllRoutes(): List<DailyRoute> {
        if (routesDir == null) return emptyList()
        val routes = mutableListOf<DailyRoute>()
        try {
            getRoutesDir().listFiles()?.filter { it.name.startsWith("ruta_") && it.name.endsWith(".json") }?.forEach {
                val dateStr = it.name.removePrefix("ruta_").removeSuffix(".json")
                loadRoute(dateStr)?.let { r -> routes.add(r) }
            }
            getTrashDir().listFiles()?.filter { it.name.startsWith("ruta_") && it.name.endsWith(".json") }?.forEach {
                val dateStr = it.name.removePrefix("ruta_").removeSuffix(".json")
                loadRoute(dateStr)?.let { r -> if (routes.none { existing -> existing.date == r.date }) routes.add(r) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return routes.sortedByDescending { it.date }
    }

    /**
     * Get all dates that have route data (for day navigation).
     */
    fun getAvailableDates(): List<String> {
        if (routesDir == null) return emptyList()
        val dates = mutableSetOf<String>()
        try {
            getRoutesDir().listFiles()?.filter { it.name.startsWith("ruta_") && it.name.endsWith(".json") }?.forEach {
                dates.add(it.name.removePrefix("ruta_").removeSuffix(".json"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return dates.sorted()
    }

    /**
     * Get all points from a route flattened across all segments (for backward compat).
     */
    fun getAllPoints(route: DailyRoute): List<RoutePoint> {
        return route.segments.flatMap { it.points }
    }
}
