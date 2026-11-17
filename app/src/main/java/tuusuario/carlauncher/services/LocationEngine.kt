package com.tuusuario.carlauncher.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
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
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Motor Cinemático Inercial Sincronizado por VSYNC (60/120 FPS nativos sin ValueAnimator intermedio).
 *
 * Por qué elimina al 100% los tirones ("frenazos" y acelerones):
 *  1. Sincronización directa con `Choreographer` (VSYNC de pantalla a 60/120 Hz) enviando `animDurationMs = 0L`:
 *     En Mapbox v11, pasar `duration > 0` cancela y reinicia un `ValueAnimator` en cada actualización,
 *     congelando 1 cuadro (`fraction = 0`) en cada reinicio. Con `Choreographer` + `duration = 0L`,
 *     la posición del auto 3D y de la cámara se calculan y aplican directamente cuadro por cuadro en
 *     el pulso exacto de refresco de pantalla (cero reinicios de `ValueAnimator`).
 *  2. Filtro de Inercia Física (Slew-Rate Limiter):
 *     Un auto real tiene masa y no puede cambiar su velocidad un 40% en 50ms cuando llega un paquete GPS
 *     con ruido. La velocidad de avance en el mapa (`currentSimSpeedMps`) usa la velocidad física real
 *     sobre el suelo y está limitada por aceleración/desaceleración física máxima, manteniendo una
 *     velocidad 100% constante y fluida en rectas.
 *  3. Detección de Giros por Frenado + Trayectoria:
 *     Cuando el velocímetro detecta que la velocidad desciende y las ubicaciones registradas cambian
 *     de rumbo, el vehículo reduce suavemente su velocidad de entrada y aplica velocidad angular
 *     continua para doblar la esquina sin pasarse de largo.
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

    private data class GpsBreadcrumb(
        val lat: Double,
        val lon: Double,
        val speedMps: Float,
        val courseDeg: Double,
        val elapsedMs: Long
    )

    private const val METERS_PER_DEG_LAT = 111320.0

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val listeners = CopyOnWriteArrayList<Listener>()

    private var appContext: Context? = null
    private var fusedClient: FusedLocationProviderClient? = null
    private var isRunning = false
    private var currentBatterySaverState: Boolean? = null
    private var simulationJob: Job? = null

    // Loop VSYNC de hardware (Choreographer a 60/120 FPS exactos)
    private var isVsyncLoopActive = false
    private var lastFrameTimeNanos: Long = 0L
    private var lastSpeedUiPushElapsedMs: Long = 0L

    // Historial de ubicaciones registradas para análisis de trayectoria
    private val breadcrumbs = ArrayDeque<GpsBreadcrumb>(8)
    private var lastFixElapsedMs: Long = 0L
    private var prevAcceptedLoc: Location? = null
    private var lastValidBearingDeg: Double = 0.0
    private var lastFixIsMock: Boolean = false

    // Velocidad para el velocímetro (calibrada) y velocidad física real sobre el suelo (m/s)
    private var targetDisplaySpeedKmH: Float = 0f
    private var smoothedDisplaySpeedKmH: Float = 0f
    private var targetGroundSpeedMps: Double = 0.0
    private var gpsAccelMps2: Double = 0.0
    private var recentPeakSpeedMps: Double = 0.0
    private var lastSpeedFixElapsedMs: Long = 0L

    // Estado cinemático inercial continuo del auto 3D
    private var hasSimState: Boolean = false
    private var simLat: Double = 0.0
    private var simLon: Double = 0.0
    private var simBearingDeg: Double = 0.0
    private var targetBearingDeg: Double = 0.0
    private var currentSimSpeedMps: Double = 0.0
    private var currentAngularVelDegPerSec: Double = 0.0
    private var predictedYawRateDegPerSec: Double = 0.0

    // Discrepancia suavizada respecto al GPS (absorbida con deriva suave sin tirones)
    private var lonErrorMeters: Double = 0.0
    private var latErrorMeters: Double = 0.0

    // Estado de maniobra (recta vs. frenado y giro)
    private var isTurningManeuver: Boolean = false
    private var isSpeedDescending: Boolean = false
    private var turnIntensity: Float = 0f
    private var stationaryNotified: Boolean = false

    private val vsyncFrameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isVsyncLoopActive) return
            onVsyncFrame(frameTimeNanos)
            if (isVsyncLoopActive) {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

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
        ensureVsyncLoopRunning()

        if (NavigationState.currentLocation.value == null &&
            ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                fusedClient?.lastLocation?.addOnSuccessListener { lastLoc ->
                    if (lastLoc != null && NavigationState.currentLocation.value == null && !AppSettings.isGpsSimulationMode.value) {
                        processLocation(lastLoc, isMock = false)
                    }
                }
            } catch (_: Exception) {}
        }
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
        val interval = if (batterySaver) 1800L else 500L
        val minInterval = if (batterySaver) 900L else 250L

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval)
            .setMinUpdateIntervalMillis(minInterval)
            .setMaxUpdateDelayMillis(0L)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .build()

        client.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        isRunning = true
        currentBatterySaverState = batterySaver
        ensureVsyncLoopRunning()
    }

    fun stop() {
        fusedClient?.removeLocationUpdates(locationCallback)
        isRunning = false
        currentBatterySaverState = null
        if (!AppSettings.isGpsSimulationMode.value) {
            stopVsyncLoop()
        }
    }

    private fun ensureVsyncLoopRunning() {
        if (isVsyncLoopActive) return
        isVsyncLoopActive = true
        lastFrameTimeNanos = 0L
        Choreographer.getInstance().postFrameCallback(vsyncFrameCallback)
    }

    private fun stopVsyncLoop() {
        if (!isVsyncLoopActive) return
        isVsyncLoopActive = false
        Choreographer.getInstance().removeFrameCallback(vsyncFrameCallback)
    }

    /**
     * Se ejecuta en cada pulso VSYNC de pantalla (cada 8.3ms a 120Hz o 16.6ms a 60Hz).
     * Integra velocidad y rumbo con inercia física real (cero cambios bruscos de velocidad).
     */
    private fun onVsyncFrame(frameTimeNanos: Long) {
        val dtSec = if (lastFrameTimeNanos > 0L) {
            ((frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000.0).coerceIn(0.002, 0.050)
        } else {
            0.016
        }
        lastFrameTimeNanos = frameTimeNanos

        val nowElapsed = SystemClock.elapsedRealtime()
        val dtSinceFixSec = if (lastSpeedFixElapsedMs > 0L) {
            ((nowElapsed - lastSpeedFixElapsedMs) / 1000.0).coerceAtLeast(0.0)
        } else 0.0

        // ── 1. Actualizar velocidad del velocímetro (a ~25 Hz para no sobrecargar recomposición UI) ──
        val maxCoastSec = if (AppSettings.batterySaverMode.value) 4.5 else 3.2
        val coastMultiplier = when {
            targetGroundSpeedMps <= 0.08 -> 0.0
            !AppSettings.isGpsSimulationMode.value && dtSinceFixSec > 2.2 && targetGroundSpeedMps < 1.2 -> 0.0
            !AppSettings.isGpsSimulationMode.value && dtSinceFixSec > maxCoastSec -> {
                exp(-(dtSinceFixSec - maxCoastSec) * 0.80)
            }
            else -> 1.0
        }

        // Si el auto viene frenando fuerte, proyectar esa desaceleración suavemente entre fixes GPS
        val brakingPredictMps = if (gpsAccelMps2 < -0.4 && dtSinceFixSec < 1.4) {
            gpsAccelMps2 * dtSinceFixSec.coerceAtMost(1.1) * 0.55
        } else 0.0

        val liveGroundTargetMps = ((targetGroundSpeedMps + brakingPredictMps) * coastMultiplier).coerceIn(0.0, 75.0)
        val liveDisplayTargetKmH = (targetDisplaySpeedKmH * coastMultiplier.toFloat() + (brakingPredictMps * 3.6).toFloat())
            .coerceIn(0f, 260f)

        val displayDiff = liveDisplayTargetKmH - smoothedDisplaySpeedKmH
        if (abs(displayDiff) > 0.01f) {
            val stepAlpha = (dtSec * 7.5).toFloat().coerceIn(0.04f, 0.35f)
            smoothedDisplaySpeedKmH += displayDiff * stepAlpha
            if (liveDisplayTargetKmH == 0f && smoothedDisplaySpeedKmH < 0.35f) {
                smoothedDisplaySpeedKmH = 0f
            }
        }

        if (nowElapsed - lastSpeedUiPushElapsedMs >= 40L ||
            (smoothedDisplaySpeedKmH == 0f && NavigationState.currentSpeedKmH.value != 0f)
        ) {
            if (abs(NavigationState.currentSpeedKmH.value - smoothedDisplaySpeedKmH) >= 0.08f ||
                (smoothedDisplaySpeedKmH == 0f && NavigationState.currentSpeedKmH.value != 0f)
            ) {
                NavigationState.currentSpeedKmH.value = smoothedDisplaySpeedKmH
                lastSpeedUiPushElapsedMs = nowElapsed
            }
        }

        recentPeakSpeedMps = maxOf(liveGroundTargetMps, (recentPeakSpeedMps - 0.45 * dtSec).coerceAtLeast(0.0))

        // ── 2. Cinemática continua del vehículo 3D sobre el mapa ──
        val baseLoc = NavigationState.currentLocation.value ?: return
        if (!hasSimState || (baseLoc.latitude == 0.0 && baseLoc.longitude == 0.0)) return

        // Ajuste longitudinal ultra-suave con zona muerta para ignorar el ruido natural del GPS (±4.5m):
        // Nunca frena ni acelera bruscamente (máximo ±10% de deriva gradual).
        val desiredSpeedMps = if (liveGroundTargetMps < 0.15) {
            0.0
        } else {
            val effectiveLonErr = when {
                abs(lonErrorMeters) <= 4.5 -> lonErrorMeters * 0.03 // Zona muerta: velocidad 99% constante
                lonErrorMeters > 4.5 -> (lonErrorMeters - 4.5) * 0.12
                else -> (lonErrorMeters + 4.5) * 0.12
            }
            val minSpeed = liveGroundTargetMps * 0.90
            val maxSpeed = maxOf(liveGroundTargetMps * 1.10, 0.8)
            (liveGroundTargetMps + effectiveLonErr).coerceIn(minSpeed, maxSpeed)
        }

        // Limitador físico de inercia (Slew-Rate Limiter):
        // Impide matemáticamente cualquier tirón o frenazo al actualizar el GPS
        val maxAccelMps2 = 3.5 // máx +12.6 km/h por segundo
        val maxDecelMps2 = if (isSpeedDescending || desiredSpeedMps == 0.0) 6.0 else 2.2 // crucero súper estable
        val speedDelta = desiredSpeedMps - currentSimSpeedMps
        val maxUp = maxAccelMps2 * dtSec
        val maxDown = maxDecelMps2 * dtSec
        currentSimSpeedMps = (currentSimSpeedMps + speedDelta.coerceIn(-maxDown, maxUp)).coerceAtLeast(0.0)

        if (desiredSpeedMps == 0.0 && currentSimSpeedMps < 0.04) {
            currentSimSpeedMps = 0.0
        }

        if (currentSimSpeedMps <= 0.0) {
            currentAngularVelDegPerSec = 0.0
            predictedYawRateDegPerSec = 0.0
            if (!stationaryNotified) {
                stationaryNotified = true
                dispatchToListeners(
                    baseLoc = baseLoc,
                    lat = simLat,
                    lon = simLon,
                    bearing = simBearingDeg,
                    speedKmH = 0f,
                    animMs = 0L
                )
            }
            return
        }
        stationaryNotified = false

        // Descontar lentamente el error longitudinal consumido
        val consumedLon = (currentSimSpeedMps - liveGroundTargetMps) * dtSec
        lonErrorMeters = (lonErrorMeters - consumedLon).coerceIn(-25.0, 25.0)
        lonErrorMeters *= (1.0 - 0.15 * dtSec)

        // ── 3. Dirección: Recta estable vs. Giro fluido cuando baja la velocidad y cambia la trayectoria ──
        if (isTurningManeuver && dtSinceFixSec < 1.8 && abs(predictedYawRateDegPerSec) > 0.2) {
            targetBearingDeg = normalizeDeg(targetBearingDeg + predictedYawRateDegPerSec * dtSec)
            val decayRate = if (isSpeedDescending) 0.85 else 1.45
            predictedYawRateDegPerSec *= exp(-decayRate * dtSec)
        } else {
            predictedYawRateDegPerSec = 0.0
        }

        val routeHintBearing = computeActiveRouteCurveHint(simLat, simLon, targetBearingDeg, currentSimSpeedMps)
        if (routeHintBearing != null) {
            val routeBlend = (if (isSpeedDescending || isTurningManeuver) 1.8 else 0.7) * dtSec
            targetBearingDeg = lerpAngleDeg(targetBearingDeg, routeHintBearing, routeBlend)
        }

        // Corrección suave de carril mediante ángulo de dirección (nunca desplazando el auto de lado en seco)
        val maxSteerBiasDeg = if (isTurningManeuver) 14.0 else 5.0
        val steerGain = if (isTurningManeuver) 1.8 else 0.65
        val steerBiasDeg = (latErrorMeters * steerGain).coerceIn(-maxSteerBiasDeg, maxSteerBiasDeg)
        val effectiveTargetBearing = normalizeDeg(targetBearingDeg + steerBiasDeg)

        val angleDiff = shortestAngleDeltaDeg(simBearingDeg, effectiveTargetBearing)
        val desiredAngVelDegPerSec = if (!isTurningManeuver) {
            // En recta: movimiento de volante muy amortiguado para que la cámara y el auto vayan firmes
            (angleDiff * 1.8).coerceIn(-20.0, 20.0)
        } else {
            // En giro (frenado + cambio de trayectoria): giro continuo y natural en la esquina
            val gain = 3.6 + 1.8 * turnIntensity
            (angleDiff * gain + predictedYawRateDegPerSec * 0.25).coerceIn(-80.0, 80.0)
        }

        // Limitador de aceleración angular (volante suave sin latigazos)
        val maxAngAccel = (if (isTurningManeuver) 150.0 else 45.0) * dtSec
        val angVelDelta = (desiredAngVelDegPerSec - currentAngularVelDegPerSec).coerceIn(-maxAngAccel, maxAngAccel)
        currentAngularVelDegPerSec += angVelDelta

        var stepAngle = currentAngularVelDegPerSec * dtSec
        if (abs(predictedYawRateDegPerSec) < 0.8 && abs(stepAngle) > abs(angleDiff)) {
            stepAngle = angleDiff
        }
        simBearingDeg = normalizeDeg(simBearingDeg + stepAngle)

        // ── 4. Integrar avance en coordenadas geográficas ──
        val forwardStepM = currentSimSpeedMps * dtSec
        val maxLatDriftM = forwardStepM * (if (isTurningManeuver) 0.28 else 0.10)
        val latStepM = (latErrorMeters * (if (isTurningManeuver) 1.4 else 0.5) * dtSec)
            .coerceIn(-maxLatDriftM, maxLatDriftM)
        latErrorMeters -= latStepM

        val radHeading = Math.toRadians(simBearingDeg)
        val cosH = cos(radHeading)
        val sinH = sin(radHeading)
        val northStepM = forwardStepM * cosH - latStepM * sinH
        val eastStepM = forwardStepM * sinH + latStepM * cosH

        val cosLat = cos(Math.toRadians(simLat)).coerceAtLeast(0.15)
        simLat += northStepM / METERS_PER_DEG_LAT
        simLon += eastStepM / (METERS_PER_DEG_LAT * cosLat)

        // Enviar actualización síncrona de cuadro VSYNC (animDurationMs = 0L)
        dispatchToListeners(
            baseLoc = baseLoc,
            lat = simLat,
            lon = simLon,
            bearing = simBearingDeg,
            speedKmH = smoothedDisplaySpeedKmH,
            animMs = 0L
        )
    }

    fun updateSimulationMode(enabled: Boolean) {
        simulationJob?.cancel()
        simulationJob = null
        lastFixElapsedMs = 0L
        if (!enabled) return

        ensureVsyncLoopRunning()

        simulationJob = mainScope.launch {
            var angle = 0.0
            val centerLat = NavigationState.currentLocation.value?.latitude?.takeIf { it != 0.0 } ?: 10.998
            val centerLon = NavigationState.currentLocation.value?.longitude?.takeIf { it != 0.0 } ?: -63.998
            val radiusDegrees = 0.0022
            var simSpeedKmH = 0f
            var tick = 0

            while (isActive && AppSettings.isGpsSimulationMode.value) {
                val cyclePos = tick % 220
                val desiredKmH = when {
                    cyclePos < 45 -> 68f * (cyclePos / 45f)
                    cyclePos < 130 -> 68f + 10f * sin((cyclePos - 45) * 0.08f)
                    cyclePos < 175 -> 68f * (1f - (cyclePos - 130) / 45f)
                    else -> 0f
                }.coerceAtLeast(0f)

                simSpeedKmH += (desiredKmH - simSpeedKmH) * 0.25f
                if (desiredKmH == 0f && simSpeedKmH < 0.5f) simSpeedKmH = 0f

                val speedMps = simSpeedKmH / 3.6f
                val arcStep = (speedMps * 0.10) / (radiusDegrees * METERS_PER_DEG_LAT)
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
        val rawIntervalMs = if (lastFixElapsedMs > 0L) (nowElapsed - lastFixElapsedMs) else 900L

        // Ignorar callbacks duplicados del FusedLocationProvider que llegan con < 60ms y distancia casi 0
        if (!isMock && prevAcceptedLoc != null && rawIntervalMs < 60L && prevAcceptedLoc!!.distanceTo(loc) < 0.15f) {
            return
        }

        val fixIntervalMs = rawIntervalMs.coerceIn(80L, 3500L)
        val dtFixSec = (fixIntervalMs / 1000.0).coerceIn(0.08, 3.5)
        lastFixElapsedMs = nowElapsed
        lastFixIsMock = isMock

        NavigationState.currentLocation.value = loc
        if (!NavigationState.isGpsReady.value) {
            NavigationState.isGpsReady.value = true
        }

        // ── 1. Velocidad Física Real (m/s) para el Mapa y Velocidad Calibrada (km/h) para el Velocímetro ──
        val distFromPrevMeters = prevAcceptedLoc?.distanceTo(loc) ?: 0f
        val fallbackSpeedMps = if (prevAcceptedLoc != null && dtFixSec >= 0.25 && distFromPrevMeters in 0.7f..110f) {
            (distFromPrevMeters / dtFixSec).toFloat()
        } else 0f

        val rawMps = when {
            loc.hasSpeed() && loc.speed > 0.1f -> loc.speed.coerceAtLeast(0f)
            fallbackSpeedMps > 0f -> fallbackSpeedMps
            else -> 0f
        }
        val groundMps = if (rawMps * 3.6f < 1.2f) 0.0 else rawMps.toDouble()
        val rawSpeedKmH = rawMps * 3.6f

        val calibratedSpeedKmH = when {
            rawSpeedKmH < 1.2f -> 0f
            rawSpeedKmH < 10f  -> rawSpeedKmH
            rawSpeedKmH < 30f  -> rawSpeedKmH * 1.04f
            rawSpeedKmH < 70f  -> rawSpeedKmH * 1.07f
            else               -> rawSpeedKmH * 1.10f
        }

        // ── 2. Detección de Frenado / Caída de Velocidad ──
        recentPeakSpeedMps = maxOf(recentPeakSpeedMps, groundMps)
        val speedDropMps = (recentPeakSpeedMps - groundMps).coerceAtLeast(0.0)
        val speedDropRatio = if (recentPeakSpeedMps > 1.8) {
            (speedDropMps / recentPeakSpeedMps).toFloat().coerceIn(0f, 1f)
        } else 0f

        if (lastSpeedFixElapsedMs > 0L) {
            val dtSpeedSec = ((nowElapsed - lastSpeedFixElapsedMs) / 1000.0).coerceIn(0.08, 3.0)
            val instantAccelMps2 = ((groundMps - targetGroundSpeedMps) / dtSpeedSec).coerceIn(-12.0, 9.0)
            gpsAccelMps2 = when {
                groundMps == 0.0 -> 0.0
                instantAccelMps2 < -0.6 -> gpsAccelMps2 * 0.35 + instantAccelMps2 * 0.65
                else -> gpsAccelMps2 * 0.55 + instantAccelMps2 * 0.45
            }
        } else {
            smoothedDisplaySpeedKmH = calibratedSpeedKmH
            currentSimSpeedMps = groundMps
            NavigationState.currentSpeedKmH.value = calibratedSpeedKmH
        }
        targetGroundSpeedMps = groundMps
        targetDisplaySpeedKmH = calibratedSpeedKmH
        lastSpeedFixElapsedMs = nowElapsed

        // ── 3. Análisis de Trayectoria con el Historial de Ubicaciones Registradas ──
        val geoBearingDeg = if (prevAcceptedLoc != null && distFromPrevMeters >= 0.85f) {
            normalizeDeg(prevAcceptedLoc!!.bearingTo(loc).toDouble())
        } else null

        val dopplerBearingDeg = if (loc.hasBearing() && (isMock || loc.speed >= 0.75f)) {
            normalizeDeg(loc.bearing.toDouble())
        } else null

        val measuredBearingDeg = when {
            dopplerBearingDeg != null && geoBearingDeg != null -> {
                val geoWeight = if (speedDropRatio >= 0.14f || calibratedSpeedKmH < 28f) 0.48 else 0.22
                lerpAngleDeg(dopplerBearingDeg, geoBearingDeg, geoWeight)
            }
            dopplerBearingDeg != null -> dopplerBearingDeg
            geoBearingDeg != null -> geoBearingDeg
            else -> lastValidBearingDeg
        }
        lastValidBearingDeg = measuredBearingDeg
        prevAcceptedLoc = Location(loc)

        val prevCrumb = breadcrumbs.lastOrNull()
        val prevCrumb2 = if (breadcrumbs.size >= 2) breadcrumbs[breadcrumbs.size - 2] else prevCrumb

        val deltaBearing1 = if (prevCrumb != null && groundMps >= 0.45) {
            shortestAngleDeltaDeg(prevCrumb.courseDeg, measuredBearingDeg)
        } else 0.0
        val deltaBearing2 = if (prevCrumb2 != null && groundMps >= 0.45) {
            shortestAngleDeltaDeg(prevCrumb2.courseDeg, measuredBearingDeg)
        } else deltaBearing1

        val absDelta1 = abs(deltaBearing1)
        val absDelta2 = abs(deltaBearing2)

        breadcrumbs.addLast(
            GpsBreadcrumb(
                lat = loc.latitude,
                lon = loc.longitude,
                speedMps = groundMps.toFloat(),
                courseDeg = measuredBearingDeg,
                elapsedMs = nowElapsed
            )
        )
        while (breadcrumbs.size > 8) {
            breadcrumbs.removeFirst()
        }

        // ── 4. Clasificador: Seguir Recto vs. Girar cuando baja la velocidad y cambia la trayectoria ──
        isSpeedDescending = speedDropRatio >= 0.13f ||
            speedDropMps >= 1.4 ||
            gpsAccelMps2 <= -0.75 ||
            (calibratedSpeedKmH in 4f..26f && gpsAccelMps2 < -0.20)

        val hasCornerTrajectoryChange = isSpeedDescending && (absDelta1 >= 3.8 || absDelta2 >= 5.5)
        val hasSustainedCurveChange = absDelta1 >= 6.8 || (absDelta2 >= 9.5 && deltaBearing1 * deltaBearing2 > 0.0)

        if (groundMps < 0.45) {
            isTurningManeuver = false
            turnIntensity = 0f
            predictedYawRateDegPerSec = 0.0
        } else if (hasCornerTrajectoryChange || hasSustainedCurveChange) {
            isTurningManeuver = true
            val speedDropBoost = if (isSpeedDescending) (1.0f + speedDropRatio * 1.2f).coerceIn(1.0f, 2.0f) else 1.0f
            turnIntensity = (((maxOf(absDelta1, absDelta2 * 0.7) / 22.0).toFloat()) * speedDropBoost).coerceIn(0.30f, 1.0f)
            targetBearingDeg = measuredBearingDeg

            val rawYawRate = (deltaBearing1 / dtFixSec).coerceIn(-70.0, 70.0)
            predictedYawRateDegPerSec = rawYawRate * (0.55 + 0.35 * turnIntensity)
        } else {
            // Modo recto: amortiguar variaciones del GPS para que el auto avance firme y constante
            isTurningManeuver = false
            turnIntensity = 0f
            predictedYawRateDegPerSec = 0.0
            targetBearingDeg = if (hasSimState) {
                lerpAngleDeg(targetBearingDeg, measuredBearingDeg, 0.32)
            } else {
                measuredBearingDeg
            }
        }

        // ── 5. Evaluación suave de posición respecto al GPS ──
        if (!hasSimState) {
            hasSimState = true
            simLat = loc.latitude
            simLon = loc.longitude
            simBearingDeg = measuredBearingDeg
            targetBearingDeg = measuredBearingDeg
            currentSimSpeedMps = groundMps
            lonErrorMeters = 0.0
            latErrorMeters = 0.0
            dispatchToListeners(
                baseLoc = loc,
                lat = simLat,
                lon = simLon,
                bearing = simBearingDeg,
                speedKmH = calibratedSpeedKmH,
                animMs = 0L
            )
        } else {
            val cosLat = cos(Math.toRadians(loc.latitude)).coerceAtLeast(0.15)
            val northDiffM = (loc.latitude - simLat) * METERS_PER_DEG_LAT
            val eastDiffM = (loc.longitude - simLon) * METERS_PER_DEG_LAT * cosLat
            val totalErrorM = hypot(northDiffM, eastDiffM)

            if (totalErrorM > 50.0) {
                simLat = loc.latitude
                simLon = loc.longitude
                simBearingDeg = measuredBearingDeg
                targetBearingDeg = measuredBearingDeg
                lonErrorMeters = 0.0
                latErrorMeters = 0.0
            } else {
                val radSim = Math.toRadians(simBearingDeg)
                val cosSim = cos(radSim)
                val sinSim = sin(radSim)
                val newLonErr = northDiffM * cosSim + eastDiffM * sinSim
                val newLatErr = -northDiffM * sinSim + eastDiffM * cosSim

                lonErrorMeters = (lonErrorMeters * 0.45 + newLonErr * 0.55).coerceIn(-25.0, 25.0)
                latErrorMeters = (latErrorMeters * 0.40 + newLatErr * 0.60).coerceIn(-15.0, 15.0)
            }
        }

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

        RouteTracker.onLocationUpdate(loc)
        DashcamRouteTracker.onLocationUpdate(loc)
        SpeedRecordTracker.onSpeedUpdate(calibratedSpeedKmH, loc)
        appContext?.let { WeatherService.onLocationAvailable(it, loc.latitude, loc.longitude) }

        ensureVsyncLoopRunning()
    }

    private fun computeActiveRouteCurveHint(
        carLat: Double,
        carLon: Double,
        currentTargetBearing: Double,
        speedMps: Double
    ): Double? {
        if (!NavigationState.isRouteActive.value) return null
        val pts = NavigationState.activeRoutePoints
        if (pts.size < 2) return null

        val cosLat = cos(Math.toRadians(carLat)).coerceAtLeast(0.15)
        var bestIdx = -1
        var bestDistM = Double.MAX_VALUE
        val limit = minOf(pts.size - 1, 25)

        for (i in 0 until limit) {
            val p = pts[i]
            val dN = (p.latitude() - carLat) * METERS_PER_DEG_LAT
            val dE = (p.longitude() - carLon) * METERS_PER_DEG_LAT * cosLat
            val d = hypot(dN, dE)
            if (d < bestDistM) {
                bestDistM = d
                bestIdx = i
            }
        }

        if (bestIdx < 0 || bestDistM > 22.0) return null

        val lookaheadTargetM = (speedMps * 0.75).coerceIn(5.0, 18.0)
        var accumM = 0.0
        var targetIdx = bestIdx
        while (targetIdx < pts.size - 1 && accumM < lookaheadTargetM) {
            val a = pts[targetIdx]
            val b = pts[targetIdx + 1]
            val dN = (b.latitude() - a.latitude()) * METERS_PER_DEG_LAT
            val dE = (b.longitude() - a.longitude()) * METERS_PER_DEG_LAT * cosLat
            accumM += hypot(dN, dE)
            targetIdx++
        }

        if (targetIdx <= bestIdx) return null
        val startPt = pts[bestIdx]
        val endPt = pts[targetIdx]
        val dN = (endPt.latitude() - startPt.latitude()) * METERS_PER_DEG_LAT
        val dE = (endPt.longitude() - startPt.longitude()) * METERS_PER_DEG_LAT * cosLat
        if (hypot(dN, dE) < 2.0) return null

        val segBearing = normalizeDeg(Math.toDegrees(kotlin.math.atan2(dE, dN)))
        return if (abs(shortestAngleDeltaDeg(currentTargetBearing, segBearing)) <= 105.0) {
            segBearing
        } else null
    }

    private fun dispatchToListeners(
        baseLoc: Location,
        lat: Double,
        lon: Double,
        bearing: Double,
        speedKmH: Float,
        animMs: Long
    ) {
        for (listener in listeners) {
            listener.onLocation(
                location = baseLoc,
                projectedLat = lat,
                projectedLon = lon,
                bearingDeg = bearing,
                calibratedSpeedKmH = speedKmH,
                animDurationMs = animMs,
                isMock = lastFixIsMock
            )
        }
    }

    private fun normalizeDeg(deg: Double): Double {
        return ((deg % 360.0) + 360.0) % 360.0
    }

    private fun shortestAngleDeltaDeg(fromDeg: Double, toDeg: Double): Double {
        return ((((toDeg - fromDeg) % 360.0) + 540.0) % 360.0) - 180.0
    }

    private fun lerpAngleDeg(fromDeg: Double, toDeg: Double, fraction: Double): Double {
        val delta = shortestAngleDeltaDeg(fromDeg, toDeg)
        return normalizeDeg(fromDeg + delta * fraction.coerceIn(0.0, 1.0))
    }

    fun addListener(listener: Listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
            val loc = NavigationState.currentLocation.value
            if (hasSimState && loc != null) {
                listener.onLocation(
                    location = loc,
                    projectedLat = simLat,
                    projectedLon = simLon,
                    bearingDeg = simBearingDeg,
                    calibratedSpeedKmH = smoothedDisplaySpeedKmH,
                    animDurationMs = 0L,
                    isMock = lastFixIsMock
                )
            }
        }
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }
}
