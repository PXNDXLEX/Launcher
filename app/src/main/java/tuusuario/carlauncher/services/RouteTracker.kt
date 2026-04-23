package com.tuusuario.carlauncher.services

import android.location.Location
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class RoutePoint(
    val lat: Double,
    val lon: Double,
    val timestamp: String,
    val note: String? = null
)

data class DailyRoute(
    val date: String,
    val points: MutableList<RoutePoint> = mutableListOf(),
    var isDeleted: Boolean = false,
    var deletedAt: String? = null
)

object RouteTracker {
    private const val MIN_DISTANCE_METERS = 50f
    private var lastRecordedLocation: Location? = null
    var currentDailyRoute: DailyRoute? = null

    private fun getRoutesDir(): File {
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val routesDir = File(docsDir, "CarLauncher/Rutas")
        if (!routesDir.exists()) routesDir.mkdirs()
        return routesDir
    }
    
    private fun getTrashDir(): File {
        val trashDir = File(getRoutesDir(), "Papelera")
        if (!trashDir.exists()) trashDir.mkdirs()
        return trashDir
    }

    fun cleanupOldTrash() {
        try {
            val trashDir = getTrashDir()
            val files = trashDir.listFiles() ?: return
            val now = LocalDateTime.now()
            files.forEach { file ->
                val json = JSONObject(file.readText())
                val deletedAtStr = json.optString("deletedAt", "")
                if (deletedAtStr.isNotEmpty()) {
                    val deletedAt = LocalDateTime.parse(deletedAtStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    val daysInTrash = ChronoUnit.DAYS.between(deletedAt, now)
                    if (daysInTrash >= 2) {
                        file.delete() // Permanently delete after 2 days
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun onLocationUpdate(loc: Location) {
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
            
            if (currentDailyRoute == null || currentDailyRoute?.date != dateStr) {
                currentDailyRoute = loadRoute(dateStr) ?: DailyRoute(dateStr)
            }
            
            currentDailyRoute?.points?.add(RoutePoint(loc.latitude, loc.longitude, timeStr))
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
            
            val pointsArray = JSONArray()
            route.points.forEach { pt ->
                val ptObj = JSONObject()
                ptObj.put("lat", pt.lat)
                ptObj.put("lon", pt.lon)
                ptObj.put("timestamp", pt.timestamp)
                pt.note?.let { ptObj.put("note", it) }
                pointsArray.put(ptObj)
            }
            json.put("points", pointsArray)
            
            val dir = if (route.isDeleted) getTrashDir() else getRoutesDir()
            val file = File(dir, "ruta_${route.date}.json")
            file.writeText(json.toString(2))
            
            // If it was moved to trash, delete the original
            if (route.isDeleted) {
                val originalFile = File(getRoutesDir(), "ruta_${route.date}.json")
                if (originalFile.exists()) originalFile.delete()
            } else {
                val trashFile = File(getTrashDir(), "ruta_${route.date}.json")
                if (trashFile.exists()) trashFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadRoute(dateStr: String): DailyRoute? {
        val file = File(getRoutesDir(), "ruta_${dateStr}.json")
        val trashFile = File(getTrashDir(), "ruta_${dateStr}.json")
        
        val targetFile = if (file.exists()) file else if (trashFile.exists()) trashFile else null
        if (targetFile == null) return null
        
        return try {
            val json = JSONObject(targetFile.readText())
            val route = DailyRoute(
                date = json.getString("date"),
                isDeleted = json.optBoolean("isDeleted", false),
                deletedAt = json.optString("deletedAt", null).takeIf { it != "null" && it.isNotEmpty() }
            )
            val ptsArray = json.getJSONArray("points")
            for (i in 0 until ptsArray.length()) {
                val ptObj = ptsArray.getJSONObject(i)
                route.points.add(
                    RoutePoint(
                        lat = ptObj.getDouble("lat"),
                        lon = ptObj.getDouble("lon"),
                        timestamp = ptObj.getString("timestamp"),
                        note = ptObj.optString("note", null).takeIf { it != "null" && it.isNotEmpty() }
                    )
                )
            }
            route
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getAllRoutes(): List<DailyRoute> {
        val routes = mutableListOf<DailyRoute>()
        try {
            getRoutesDir().listFiles()?.filter { it.name.endsWith(".json") }?.forEach {
                loadRoute(it.name.removePrefix("ruta_").removeSuffix(".json"))?.let { r -> routes.add(r) }
            }
            getTrashDir().listFiles()?.filter { it.name.endsWith(".json") }?.forEach {
                loadRoute(it.name.removePrefix("ruta_").removeSuffix(".json"))?.let { r -> routes.add(r) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return routes.sortedByDescending { it.date }
    }
}
