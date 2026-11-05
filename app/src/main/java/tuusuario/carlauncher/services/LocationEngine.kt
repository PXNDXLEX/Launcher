package com.tuusuario.carlauncher.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.tuusuario.carlauncher.ui.AppSettings
import com.tuusuario.carlauncher.ui.NavigationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.cos
import kotlin.math.sin

/**
 * Motor GPS unificado para toda la aplicación.
 * Reemplaza los múltiples listeners duplicados (Mapa + Velocímetro) por una sola tubería
 * de alta precisión y cero sobrecarga, conservando la compensación Kalman y el modo simulación.
 */
object LocationEngine {

    fun interface Listener {
        fun onLocation(location: Location, calibratedSpeedKmH: Float, isMock: Boolean)
    }

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val listeners = CopyOnWriteArrayList<Listener>()

    private var appContext: Context? = null
    private var fusedClient: FusedLocationProviderClient? = null
    private var isRunning = false
    private var currentBatterySaverState: Boolean? = null
    private var simulationJob: Job? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            val isMock = loc.provider == "mock"
            if (AppSettings.isGpsSimulationMode.value && !isMock) return
            if (loc.hasAccuracy() && loc.accuracy > 45f && !isMock) return

            processLocation(loc, isMock)
        }
    }

    fun start(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        if (fusedClient == null) {
            fusedClient = LocationServices.getFusedLocationProviderClient(ctx)
        }
        refreshHardwareRequest()
    }

    fun refreshHardwareRequest() {
        val ctx = appContext ?: return
        val client = fusedClient ?: return
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val batterySaver = AppSettings.batterySaverMode.value
        if (isRunning && currentBatterySaverState == batterySaver) return

        client.removeLocationUpdates(locationCallback)
        val interval = if (batterySaver) 3000L else 100L
        val minInterval = if (batterySaver) 2000L else 50L

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval)
            .setMinUpdateIntervalMillis(minInterval)
            .setMaxUpdateDelayMillis(if (batterySaver) 4000L else 200L)
            .build()

        client.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        isRunning = true
        currentBatterySaverState = batterySaver
    }

    fun stop() {
        fusedClient?.removeLocationUpdates(locationCallback)
        isRunning = false
        currentBatterySaverState = null
    }

    fun updateSimulationMode(enabled: Boolean) {
        simulationJob?.cancel()
        simulationJob = null
        if (!enabled) return

        simulationJob = mainScope.launch {
            var angle = 0.0
            val centerLat = NavigationState.currentLocation.value?.latitude ?: 10.998
            val centerLon = NavigationState.currentLocation.value?.longitude ?: -63.998
            val radiusDegrees = 0.002
            val speedMps = 42f / 3.6f // 42 km/h para pruebas dinámicas de velocímetro y mapa
            var iteration = 0

            while (isActive && AppSettings.isGpsSimulationMode.value) {
                val newLat = centerLat + radiusDegrees * sin(angle)
                val newLon = centerLon + radiusDegrees * cos(angle)
                val prevLat = centerLat + radiusDegrees * sin(angle - 0.05)
                val prevLon = centerLon + radiusDegrees * cos(angle - 0.05)

                val bearing = Location("").apply {
                    latitude = prevLat; longitude = prevLon
                }.bearingTo(Location("").apply {
                    latitude = newLat; longitude = newLon
                })

                val isSimBraking = (iteration % 24 < 5)
                val currentSimSpeed = if (isSimBraking) 0f else speedMps

                val mockLoc = Location("mock").apply {
                    latitude = newLat
                    longitude = newLon
                    speed = currentSimSpeed
                    this.bearing = bearing
                    accuracy = 4f
                    time = System.currentTimeMillis()
                }

                processLocation(mockLoc, isMock = true)
                if (!isSimBraking) angle += 0.035
                iteration++
                delay(1000)
            }
        }
    }

    private fun processLocation(loc: Location, isMock: Boolean) {
        NavigationState.currentLocation.value = loc
        if (!NavigationState.isGpsReady.value) {
            NavigationState.isGpsReady.value = true
        }

        val rawSpeedKmH = if (loc.hasSpeed()) (loc.speed * 3.6f).coerceAtLeast(0f) else NavigationState.currentSpeedKmH.value
        val calibratedSpeedKmH = when {
            rawSpeedKmH < 1.2f -> 0f
            rawSpeedKmH < 10f  -> rawSpeedKmH
            rawSpeedKmH < 30f  -> rawSpeedKmH * 1.04f
            rawSpeedKmH < 70f  -> rawSpeedKmH * 1.07f
            else               -> rawSpeedKmH * 1.10f
        }

        NavigationState.currentSpeedKmH.value = calibratedSpeedKmH

        if (calibratedSpeedKmH < 1f) {
            if (NavigationState.lastZeroSpeedTime == 0L) {
                NavigationState.lastZeroSpeedTime = System.currentTimeMillis()
            } else if (System.currentTimeMillis() - NavigationState.lastZeroSpeedTime > 2000L) {
                NavigationState.isBraking.value = true
            }
        } else {
            NavigationState.lastZeroSpeedTime = 0L
            NavigationState.isBraking.value = false
        }

        // Alimentar servicios en segundo plano (sin bloquear UI)
        RouteTracker.onLocationUpdate(loc)
        DashcamRouteTracker.onLocationUpdate(loc)
        SpeedRecordTracker.onSpeedUpdate(calibratedSpeedKmH, loc)
        appContext?.let { WeatherService.onLocationAvailable(it, loc.latitude, loc.longitude) }

        for (listener in listeners) {
            listener.onLocation(loc, calibratedSpeedKmH, isMock)
        }
    }

    fun addListener(listener: Listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }
}
