package com.tuusuario.carlauncher.services

import android.location.Location
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object DashcamRouteTracker {
    private const val MIN_DISTANCE_METERS = 10f
    
    private var lastRecordedLocation: Location? = null
    private var currentVideoId: String? = null
    private val currentPoints = mutableListOf<RoutePoint>()

    private fun getMetadataDir(): File {
        val dir = File(DashcamManager.getVideosDir(), "Metadata")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun startSession(videoId: String) {
        currentVideoId = videoId
        currentPoints.clear()
        lastRecordedLocation = null
    }

    fun stopSession() {
        if (currentVideoId != null && currentPoints.isNotEmpty()) {
            saveSession()
        }
        currentVideoId = null
        currentPoints.clear()
        lastRecordedLocation = null
    }

    fun onLocationUpdate(loc: Location) {
        if (currentVideoId == null) return
        
        val lastLoc = lastRecordedLocation
        if (lastLoc == null || lastLoc.distanceTo(loc) >= MIN_DISTANCE_METERS) {
            val timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            currentPoints.add(RoutePoint(loc.latitude, loc.longitude, timeStr))
            lastRecordedLocation = loc
            
            // Save on each update so we don't lose data if app crashes
            saveSession()
        }
    }

    private fun saveSession() {
        val videoId = currentVideoId ?: return
        try {
            val json = JSONObject()
            json.put("videoId", videoId)
            
            val pointsArray = JSONArray()
            currentPoints.forEach { pt ->
                val ptObj = JSONObject()
                ptObj.put("lat", pt.lat)
                ptObj.put("lon", pt.lon)
                ptObj.put("timestamp", pt.timestamp)
                pointsArray.put(ptObj)
            }
            json.put("points", pointsArray)
            
            val file = File(getMetadataDir(), "VID_${videoId}.json")
            file.writeText(json.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
