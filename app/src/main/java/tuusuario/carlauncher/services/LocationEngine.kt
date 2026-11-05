package com.tuusuario.carlauncher.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.os.SystemClock
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Motor GPS unificado en tiempo real (cero batching + interpolación cinemática continua).
 * Alimenta simultáneamente el Mapa 3D (con cadencia medida y proyección dead-reckoning para
 * movimiento lineal sin pausas de 1 segundo) y los 18 Velocímetros (con flujo continuo a ~30 FPS).
 */
object LocationEngine {

    fun interface Listener {
        fun onLocation(
            location: Location,
            projectedLat: Double,
            projectedLon: Double,
            bearingDeg: Double,
            calibratedSpeedKmH: Float,
            animDurationMs: Long,
            isMock: Boolean
        )
    }

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val listeners = CopyOnWriteArrayList<Listener>()

    private var appContext: Context? = null
    private var fusedClient: FusedLocationProviderClient? = null
    private var isRunning = false
    private var currentBatterySaverState: Boolean? = null
    private var simulationJob: Job? = null
    private var speedInterpolatorJob: Job? = null

    // Estado cinemático para eliminar saltos de 1 Hz
    private var lastFixElapsedMs: Long = 0L
    private var prevAcceptedLoc: Location? = null
    private var lastValidBearingDeg: Double = 0.0

    // Estimador continuo de velocidad en tiempo real para el velocímetro
    private var targetGpsSpeedKmH: Float = 0f
    private var smoothedSpeedKmH: Float = 0f
    private var gpsAccelKmHPerSec: Float = 0f
    private var lastSpeedFixElapsedMs: Long = 0L

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val locations = result.locations
            val loc = if (locations.isNotEmpty()) locations.last() else (result.lastLocation ?: return)
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
        ensureSpeedInterpolatorRunning()
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
        val interval = if (batterySaver) 2500L else 50L
        val minInterval = if (batterySaver) 1500L else 16L
        val maxDelay = if (batterySaver) 3000L else 0L

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval)
            .setMinUpdateIntervalMillis(minInterval)
            .setMaxUpdateDelayMillis(maxDelay)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .build()

        client.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        isRunning = true
        currentBatterySaverState = batterySaver
        ensureSpeedInterpolatorRunning()
    }

    fun stop() {
        fusedClient?.removeLocationUpdates(locationCallback)
        isRunning = false
        currentBatterySaverState = null
        if (!AppSettings.isGpsSimulationMode.value) {
            speedInterpolatorJob?.cancel()
            speedInterpolatorJob = null
        }
    }

    private fun ensureSpeedInterpolatorRunning() {
        if (speedInterpolatorJob?.isActive == true) return
        speedInterpolatorJob = mainScope.launch {
            while (isActive) {
                delay(32L) // ~31 FPS de flujo continuo hacia el velocímetro (animado luego a 60/120 FPS)
                val nowElapsed = SystemClock.elapsedRealtime()
                val dtSinceFixSec = if (lastSpeedFixElapsedMs > 0L) {
                    ((nowElapsed - lastSpeedFixElapsedMs) / 1000f).coerceAtLeast(0f)
                } else 0f

                // Si pasan > 1.8s sin nuevo fix real (ej. túnel o detenido sin señal nueva), amortiguar aceleración
                val effectiveTarget = if (!AppSettings.isGpsSimulationMode.value && dtSinceFixSec > 2.2f && targetGpsSpeedKmH < 4f) {
                    0f
                } else if (targetGpsSpeedKmH <= 0.2f) {
                    0f
                } else {
                    val predWindow = dtSinceFixSec.coerceAtMost(0.85f)
                    (targetGpsSpeedKmH + gpsAccelKmHPerSec * predWindow * 0.42f).coerceIn(0f, 260f)
                }

                val diff = effectiveTarget - smoothedSpeedKmH
                if (abs(diff) > 0.02f) {
                    // Persecución suave pero inmediata: sin saltos bruscos de 1 segundo
                    val alpha = if (effectiveTarget == 0f && smoothedSpeedKmH < 2.2f) 0.28f else 0.20f
                    smoothedSpeedKmH += diff * alpha
                    if (effectiveTarget == 0f && smoothedSpeedKmH < 0.30f) {
                        smoothedSpeedKmH = 0f
                    }
                    if (abs(NavigationState.currentSpeedKmH.value - smoothedSpeedKmH) >= 0.04f ||
                        (smoothedSpeedKmH == 0f && NavigationState.currentSpeedKmH.value != 0f)
                    ) {
                        NavigationState.currentSpeedKmH.value = smoothedSpeedKmH
                    }
                }
            }
        }
    }

    fun updateSimulationMode(enabled: Boolean) {
        simulationJob?.cancel()
        simulationJob = null
        lastFixElapsedMs = 0L
        if (!enabled) return

        ensureSpeedInterpolatorRunning()

        // Simulación de alta frecuencia (10 Hz / cada 100ms) con aceleración, crucero y frenado reales
        simulationJob = mainScope.launch {
            var angle = 0.0
            val centerLat = NavigationState.currentLocation.value?.latitude?.takeIf { it != 0.0 } ?: 10.998
            val centerLon = NavigationState.currentLocation.value?.longitude?.takeIf { it != 0.0 } ?: -63.998
            val radiusDegrees = 0.0022
            var simSpeedKmH = 0f
            var tick = 0

            while (isActive && AppSettings.isGpsSimulationMode.value) {
                val cyclePos = tick % 220 // Ciclo de 22 segundos a 10 Hz
                val desiredKmH = when {
                    cyclePos < 45 -> 68f * (cyclePos / 45f)               // Aceleración fluida 0 -> 68 km/h
                    cyclePos < 130 -> 68f + 10f * sin((cyclePos - 45) * 0.08f) // Crucero dinámico 58..78 km/h
                    cyclePos < 175 -> 68f * (1f - (cyclePos - 130) / 45f) // Frenado progresivo -> 0 km/h
                    else -> 0f                                            // Detenido en semáforo (luces de freno)
                }.coerceAtLeast(0f)

                simSpeedKmH += (desiredKmH - simSpeedKmH) * 0.25f
                if (desiredKmH == 0f && simSpeedKmH < 0.5f) simSpeedKmH = 0f

                val speedMps = simSpeedKmH / 3.6f
                // Avanzar ángulo proporcionalmente a los metros recorridos en 0.1s
                val arcStep = (speedMps * 0.10) / (radiusDegrees * 111320.0)
                val prevLat = centerLat + radiusDegrees * sin(angle)
                val prevLon = centerLon + radiusDegrees * cos(angle)
                angle += arcStep
                val newLat = centerLat + radiusDegrees * sin(angle)
                val newLon = centerLon + radiusDegrees * cos(angle)

                val tangentLat = centerLat + radiusDegrees * sin(angle + 0.01)
                val tangentLon = centerLon + radiusDegrees * cos(angle + 0.01)
                val bearing = Location("").apply {
                    latitude = if (arcStep > 0.00001) prevLat else newLat
                    longitude = if (arcStep > 0.00001) prevLon else newLon
                }.bearingTo(Location("").apply {
                    latitude = if (arcStep > 0.00001) newLat else tangentLat
                    longitude = if (arcStep > 0.00001) newLon else tangentLon
                }).let { ((it % 360f) + 360f) % 360f }

                val mockLoc = Location("mock").apply {
                    latitude = newLat
                    longitude = newLon
                    speed = speedMps
                    this.bearing = bearing
                    accuracy = 3.5f
                    time = System.currentTimeMillis()
                }

                processLocation(mockLoc, isMock = true)
                tick++
                delay(100L)
            }
        }
    }

    private fun processLocation(loc: Location, isMock: Boolean) {
        val nowElapsed = SystemClock.elapsedRealtime()
        val fixIntervalMs = if (lastFixElapsedMs > 0L) {
            (nowElapsed - lastFixElapsedMs).coerceIn(60L, 1200L)
        } else {
            if (isMock) 110L else 850L
        }
        lastFixElapsedMs = nowElapsed

        NavigationState.currentLocation.value = loc
        if (!NavigationState.isGpsReady.value) {
            NavigationState.isGpsReady.value = true
        }

        // Velocidad base: usa Doppler del chip GNSS, o respaldo por distancia si el chip omite speed
        val fallbackSpeedMps = prevAcceptedLoc?.let { prev ->
            val dtSec = fixIntervalMs / 1000f
            val dist = prev.distanceTo(loc)
            if (dtSec >= 0.25f && dist in 0.8f..90f) dist / dtSec else 0f
        } ?: 0f

        val rawMps = when {
            loc.hasSpeed() -> loc.speed.coerceAtLeast(0f)
            fallbackSpeedMps > 0f -> fallbackSpeedMps
            else -> 0f
        }
        val rawSpeedKmH = rawMps * 3.6f

        val calibratedSpeedKmH = when {
            rawSpeedKmH < 1.2f -> 0f
            rawSpeedKmH < 10f  -> rawSpeedKmH
            rawSpeedKmH < 30f  -> rawSpeedKmH * 1.04f
            rawSpeedKmH < 70f  -> rawSpeedKmH * 1.07f
            else               -> rawSpeedKmH * 1.10f
        }

        // Actualizar estimador continuo de aceleración y velocidad
        if (lastSpeedFixElapsedMs > 0L) {
            val dtSpeedSec = ((nowElapsed - lastSpeedFixElapsedMs) / 1000f).coerceIn(0.08f, 2.5f)
            val instantAccel = ((calibratedSpeedKmH - targetGpsSpeedKmH) / dtSpeedSec).coerceIn(-38f, 32f)
            gpsAccelKmHPerSec = if (calibratedSpeedKmH == 0f) 0f else (gpsAccelKmHPerSec * 0.45f + instantAccel * 0.55f)
        } else {
            smoothedSpeedKmH = calibratedSpeedKmH
            NavigationState.currentSpeedKmH.value = calibratedSpeedKmH
        }
        targetGpsSpeedKmH = calibratedSpeedKmH
        lastSpeedFixElapsedMs = nowElapsed

        // Rumbo preciso continuo (evita congelar rotación a baja velocidad urbana)
        val bearingDeg = when {
            loc.hasBearing() && (isMock || loc.speed >= 0.75f) -> {
                ((loc.bearing.toDouble() % 360.0) + 360.0) % 360.0
            }
            prevAcceptedLoc != null && prevAcceptedLoc!!.distanceTo(loc) >= 0.85f -> {
                ((prevAcceptedLoc!!.bearingTo(loc).toDouble() % 360.0) + 360.0) % 360.0
            }
            else -> lastValidBearingDeg
        }
        lastValidBearingDeg = bearingDeg
        prevAcceptedLoc = Location(loc)

        // Proyección dead-reckoning ligera (0.32s, máx 7.5m) para compensar latencia NMEA
        // y lograr que la interpolación lineal de Mapbox mantenga el auto en tiempo real
        val calibratedMps = calibratedSpeedKmH / 3.6
        val lookaheadMeters = if (!isMock && calibratedSpeedKmH >= 3.5f) {
            (calibratedMps * 0.32).coerceAtMost(7.5)
        } else 0.0

        val radBearing = Math.toRadians(bearingDeg)
        val dLat = (lookaheadMeters * cos(radBearing)) / 111320.0
        val cosLat = cos(Math.toRadians(loc.latitude)).coerceAtLeast(0.15)
        val dLon = (lookaheadMeters * sin(radBearing)) / (111320.0 * cosLat)
        val projectedLat = loc.latitude + dLat
        val projectedLon = loc.longitude + dLon

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

        // Alimentar servicios en segundo plano (con ubicación real exacta para rutas y récords)
        RouteTracker.onLocationUpdate(loc)
        DashcamRouteTracker.onLocationUpdate(loc)
        SpeedRecordTracker.onSpeedUpdate(calibratedSpeedKmH, loc)
        appContext?.let { WeatherService.onLocationAvailable(it, loc.latitude, loc.longitude) }

        // Duración de interpolación lineal ligeramente superior (+4%) al intervalo medido
        // para que el movimiento nunca se detenga antes de que llegue el siguiente paquete GPS
        val smoothAnimMs = (fixIntervalMs * 1.04).toLong().coerceIn(60L, 1250L)

        for (listener in listeners) {
            listener.onLocation(
                location = loc,
                projectedLat = projectedLat,
                projectedLon = projectedLon,
                bearingDeg = bearingDeg,
                calibratedSpeedKmH = calibratedSpeedKmH,
                animDurationMs = smoothAnimMs,
                isMock = isMock
            )
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
