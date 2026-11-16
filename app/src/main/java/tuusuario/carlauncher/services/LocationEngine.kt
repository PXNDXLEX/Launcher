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
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Motor GPS + Cinemática Inercial Predictiva (Dead-Reckoning Continuo + Predicción de Giros por Frenado).
 *
 * Cómo elimina el retardo y los frenazos del modelo 3D sin gastar más batería:
 *  1. Desacopla el movimiento del modelo 3D de la frecuencia del hardware GPS (1 Hz / 0.5 Hz):
 *     un bucle cinemático propio corre cada ~50ms (20 Hz) proyectando hacia adelante un horizonte
 *     de 90ms que Mapbox interpola a 60/120 FPS de forma ininterrumpida (el ValueAnimator nunca
 *     llega al final de su recorrido mientras el auto se mueve, eliminando los "frenazos").
 *  2. En rectas: con las ubicaciones registradas y la velocidad en vivo del velocímetro, el auto
 *     simula avance rectilíneo continuo filtrando el ruido lateral del GPS.
 *  3. En curvas y esquinas: detecta caídas de velocidad (frenado previo al giro) combinadas con
 *     cambios de trayectoria en el historial de ubicaciones (y/o geometría de la ruta activa),
 *     reduciendo la proyección frontal para no pasarse de la esquina y aplicando velocidad angular
 *     predictiva (yaw rate) para girar con fluidez.
 *  4. Corrección elástica sin saltos hacia atrás: cuando llega un nuevo fix GPS, cualquier diferencia
 *     longitudinal se absorbe modulando suavemente la velocidad de avance (0.55x..1.45x) sin detener
 *     ni retroceder jamás el vehículo 3D.
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
        val speedKmH: Float,
        val courseDeg: Double,
        val elapsedMs: Long
    )

    private const val KINEMATIC_TICK_MS = 50L
    private const val MAPBOX_HORIZON_MS = 88L
    private const val METERS_PER_DEG_LAT = 111320.0

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val listeners = CopyOnWriteArrayList<Listener>()

    private var appContext: Context? = null
    private var fusedClient: FusedLocationProviderClient? = null
    private var isRunning = false
    private var currentBatterySaverState: Boolean? = null
    private var simulationJob: Job? = null
    private var kinematicLoopJob: Job? = null

    // Historial de ubicaciones registradas para análisis de trayectoria y giros
    private val breadcrumbs = ArrayDeque<GpsBreadcrumb>(8)
    private var lastFixElapsedMs: Long = 0L
    private var prevAcceptedLoc: Location? = null
    private var lastValidBearingDeg: Double = 0.0
    private var lastFixIsMock: Boolean = false

    // Estimador continuo de velocidad y desaceleración para el velocímetro y detector de curvas
    private var targetGpsSpeedKmH: Float = 0f
    private var smoothedSpeedKmH: Float = 0f
    private var gpsAccelKmHPerSec: Float = 0f
    private var recentPeakSpeedKmH: Float = 0f
    private var lastSpeedFixElapsedMs: Long = 0L

    // Estado cinemático continuo del vehículo 3D en el mapa
    private var hasSimState: Boolean = false
    private var simLat: Double = 0.0
    private var simLon: Double = 0.0
    private var simBearingDeg: Double = 0.0
    private var targetBearingDeg: Double = 0.0
    private var currentSimSpeedMps: Double = 0.0
    private var currentAngularVelDegPerSec: Double = 0.0
    private var predictedYawRateDegPerSec: Double = 0.0

    // Errores elásticos respecto al ancla GPS (se absorben progresivamente en movimiento)
    private var lonErrorMeters: Double = 0.0
    private var latErrorMeters: Double = 0.0

    // Indicadores dinámicos de maniobra de giro y frenado
    private var isTurningManeuver: Boolean = false
    private var isSpeedDescending: Boolean = false
    private var turnIntensity: Float = 0f
    private var stationaryNotified: Boolean = false

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
        ensureKinematicLoopRunning()

        // Semilla inicial rápida con la última ubicación conocida del sistema mientras llega el primer satélite
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
        // Intervalos eficientes para la batería sin retención (maxUpdateDelayMillis = 0L);
        // la fluidez a 60/120 FPS la aporta el motor cinemático inercial entre cada lectura GPS.
        val interval = if (batterySaver) 1800L else 450L
        val minInterval = if (batterySaver) 900L else 200L

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval)
            .setMinUpdateIntervalMillis(minInterval)
            .setMaxUpdateDelayMillis(0L)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .build()

        client.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        isRunning = true
        currentBatterySaverState = batterySaver
        ensureKinematicLoopRunning()
    }

    fun stop() {
        fusedClient?.removeLocationUpdates(locationCallback)
        isRunning = false
        currentBatterySaverState = null
        if (!AppSettings.isGpsSimulationMode.value) {
            kinematicLoopJob?.cancel()
            kinematicLoopJob = null
        }
    }

    /**
     * Bucle cinemático maestro (~20 Hz / cada 50ms):
     * Actualiza tanto el velocímetro como la posición/rotación simulada del auto 3D en el mapa.
     */
    private fun ensureKinematicLoopRunning() {
        if (kinematicLoopJob?.isActive == true) return
        kinematicLoopJob = mainScope.launch {
            var prevTickElapsedMs = SystemClock.elapsedRealtime()

            while (isActive) {
                delay(KINEMATIC_TICK_MS)
                val nowElapsed = SystemClock.elapsedRealtime()
                val dtSec = ((nowElapsed - prevTickElapsedMs) / 1000.0).coerceIn(0.015, 0.140)
                prevTickElapsedMs = nowElapsed

                val dtSinceFixSec = if (lastSpeedFixElapsedMs > 0L) {
                    ((nowElapsed - lastSpeedFixElapsedMs) / 1000f).coerceAtLeast(0f)
                } else 0f

                // ── 1. Interpolación continua de velocidad para el velocímetro y el motor inercial ──
                val maxCoastSec = if (AppSettings.batterySaverMode.value) 4.2f else 3.0f
                val effectiveTargetSpeedKmH = when {
                    targetGpsSpeedKmH <= 0.25f -> 0f
                    !AppSettings.isGpsSimulationMode.value && dtSinceFixSec > 2.0f && targetGpsSpeedKmH < 4.5f -> 0f
                    !AppSettings.isGpsSimulationMode.value && dtSinceFixSec > maxCoastSec -> {
                        val overtime = dtSinceFixSec - maxCoastSec
                        (targetGpsSpeedKmH * exp(-overtime * 0.85f)).coerceAtLeast(0f)
                    }
                    else -> {
                        // Si el auto viene frenando (aceleración negativa), reflejar la caída de velocidad de inmediato
                        val predWindow = if (gpsAccelKmHPerSec < 0f) {
                            dtSinceFixSec.coerceAtMost(1.5f)
                        } else {
                            dtSinceFixSec.coerceAtMost(0.75f)
                        }
                        val accelWeight = if (gpsAccelKmHPerSec < 0f) 0.68f else 0.36f
                        (targetGpsSpeedKmH + gpsAccelKmHPerSec * predWindow * accelWeight).coerceIn(0f, 260f)
                    }
                }

                val speedDiff = effectiveTargetSpeedKmH - smoothedSpeedKmH
                if (abs(speedDiff) > 0.02f) {
                    val alpha = when {
                        effectiveTargetSpeedKmH == 0f && smoothedSpeedKmH < 2.5f -> 0.34f
                        speedDiff < -3.0f -> 0.28f // Respuesta rápida al desacelerar antes de una esquina
                        else -> 0.22f
                    }
                    smoothedSpeedKmH += speedDiff * alpha
                    if (effectiveTargetSpeedKmH == 0f && smoothedSpeedKmH < 0.35f) {
                        smoothedSpeedKmH = 0f
                    }
                    if (abs(NavigationState.currentSpeedKmH.value - smoothedSpeedKmH) >= 0.04f ||
                        (smoothedSpeedKmH == 0f && NavigationState.currentSpeedKmH.value != 0f)
                    ) {
                        NavigationState.currentSpeedKmH.value = smoothedSpeedKmH
                    }
                }

                // decaimiento suave del pico reciente de velocidad
                recentPeakSpeedKmH = maxOf(smoothedSpeedKmH, (recentPeakSpeedKmH - (1.8f * dtSec.toFloat())).coerceAtLeast(0f))

                // ── 2. Avance cinemático continuo del modelo 3D sobre el mapa ──
                val baseLoc = NavigationState.currentLocation.value ?: continue
                if (!hasSimState || (baseLoc.latitude == 0.0 && baseLoc.longitude == 0.0)) continue

                val baseSpeedMps = (smoothedSpeedKmH / 3.6).coerceAtLeast(0.0)

                if (smoothedSpeedKmH < 0.65f && hypot(lonErrorMeters, latErrorMeters) < 0.35) {
                    // Auto detenido: frenar suavemente sin tirones ni rotaciones residuales
                    currentSimSpeedMps = 0.0
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
                            animMs = MAPBOX_HORIZON_MS
                        )
                    }
                    continue
                }
                stationaryNotified = false

                // 2A. Modulación elástica de velocidad longitudinal:
                // Si el modelo 3D quedó ligeramente detrás del GPS (lonErrorMeters > 0) acelera hasta 1.45x;
                // si la simulación inercial se adelantó ligeramente al GPS (lonErrorMeters < 0), NO frena en seco
                // ni retrocede: simplemente reduce el paso a 0.55x..0.85x para que el GPS lo alcance en movimiento.
                val desiredSimSpeedMps = if (baseSpeedMps < 0.18) {
                    0.0
                } else {
                    val lonGain = if (abs(lonErrorMeters) < 2.2) 0.72 else 1.18
                    val speedCorrection = lonErrorMeters * lonGain
                    val minSpeedFactor = if (isTurningManeuver || isSpeedDescending) 0.45 else 0.58
                    val maxSpeedFactor = 1.46
                    (baseSpeedMps + speedCorrection).coerceIn(
                        baseSpeedMps * minSpeedFactor,
                        maxOf(baseSpeedMps * maxSpeedFactor, 1.1)
                    )
                }

                // Filtro de velocidad continua (evita cambios bruscos de velocidad al llegar un paquete GPS)
                val velAlpha = if (desiredSimSpeedMps < currentSimSpeedMps && isSpeedDescending) 0.38 else 0.26
                currentSimSpeedMps += (desiredSimSpeedMps - currentSimSpeedMps) * velAlpha
                if (desiredSimSpeedMps == 0.0 && currentSimSpeedMps < 0.06) {
                    currentSimSpeedMps = 0.0
                }

                // Descontar del error longitudinal lo que acabamos de compensar en este paso
                val consumedLonError = (currentSimSpeedMps - baseSpeedMps) * dtSec
                lonErrorMeters = (lonErrorMeters - consumedLonError).coerceIn(-22.0, 22.0)
                if (dtSinceFixSec > 2.2f) {
                    // Sin fix reciente, converger gradualmente a la velocidad pura del velocímetro
                    lonErrorMeters *= 0.92
                }

                // 2B. Cálculo de trayectoria: seguir recto en crucero vs. girar cuando baja la velocidad y cambia el rumbo
                if (currentSimSpeedMps > 0.15) {
                    if (isTurningManeuver && dtSinceFixSec < 1.75f && abs(predictedYawRateDegPerSec) > 0.25) {
                        // Proyectar el giro continuamente entre paquetes GPS usando la tasa angular calculada
                        targetBearingDeg = normalizeDeg(targetBearingDeg + predictedYawRateDegPerSec * dtSec)
                        // Si la velocidad viene descendiendo (entrando a esquina), sostener el giro; al salir, enderezar
                        val yawDecay = if (isSpeedDescending) 0.93 else 0.87
                        predictedYawRateDegPerSec *= yawDecay
                    } else {
                        predictedYawRateDegPerSec = 0.0
                    }

                    // Asistencia sutil con la geometría de la ruta activa cuando el usuario se acerca a una curva/esquina
                    val routeHintBearing = computeActiveRouteCurveHint(simLat, simLon, targetBearingDeg, currentSimSpeedMps)
                    if (routeHintBearing != null) {
                        val blendWeight = if (isSpeedDescending || isTurningManeuver) 0.24 else 0.10
                        targetBearingDeg = lerpAngleDeg(targetBearingDeg, routeHintBearing, blendWeight)
                    }

                    // Sesgo suave de dirección hacia la línea de trayectoria real si existe error lateral
                    val maxSteerBias = if (isTurningManeuver) 18.0 else 6.5
                    val steerBiasGain = if (isTurningManeuver) 2.5 else 0.95
                    val steerBiasDeg = (latErrorMeters * steerBiasGain).coerceIn(-maxSteerBias, maxSteerBias)
                    val effectiveTargetBearing = normalizeDeg(targetBearingDeg + steerBiasDeg)

                    val angleDiff = shortestAngleDeltaDeg(simBearingDeg, effectiveTargetBearing)
                    val desiredAngVel = if (!isTurningManeuver) {
                        // Modo Recto: amortigua micro-oscilaciones del GPS para que el auto avance firme y derecho
                        if (abs(angleDiff) < 1.1) angleDiff * 1.1 else (angleDiff * 2.5).coerceIn(-32.0, 32.0)
                    } else {
                        // Modo Giro Activo (velocidad descendió + trayectoria cambió): rotación ágil y fluida en la esquina
                        val turnRateGain = 4.2 + 2.2 * turnIntensity
                        (angleDiff * turnRateGain + predictedYawRateDegPerSec * 0.30).coerceIn(-88.0, 88.0)
                    }

                    val angAlpha = if (isTurningManeuver) 0.44 else 0.24
                    currentAngularVelDegPerSec += (desiredAngVel - currentAngularVelDegPerSec) * angAlpha

                    var stepAngle = currentAngularVelDegPerSec * dtSec
                    if (abs(predictedYawRateDegPerSec) < 1.0 && abs(stepAngle) > abs(angleDiff)) {
                        stepAngle = angleDiff
                    }
                    simBearingDeg = normalizeDeg(simBearingDeg + stepAngle)
                } else {
                    currentAngularVelDegPerSec = 0.0
                }

                // 2C. Integrar posición (avance frontal + convergencia lateral diagonal suave)
                val forwardStepM = currentSimSpeedMps * dtSec
                val maxLatStepM = forwardStepM * (if (isTurningManeuver) 0.62 else 0.35)
                val latPullFactor = if (isTurningManeuver) 0.22 else 0.10
                val latStepM = if (forwardStepM > 0.01) {
                    (latErrorMeters * latPullFactor).coerceIn(-maxLatStepM, maxLatStepM)
                } else 0.0
                latErrorMeters -= latStepM

                val radHeading = Math.toRadians(simBearingDeg)
                val cosH = cos(radHeading)
                val sinH = sin(radHeading)
                // Vector avance (cosH, sinH) + Vector derecha (-sinH, cosH)
                val northStepM = forwardStepM * cosH - latStepM * sinH
                val eastStepM = forwardStepM * sinH + latStepM * cosH

                val cosLat = cos(Math.toRadians(simLat)).coerceAtLeast(0.15)
                simLat += northStepM / METERS_PER_DEG_LAT
                simLon += eastStepM / (METERS_PER_DEG_LAT * cosLat)

                // 2D. Proyección de horizonte exacto para Mapbox (MAPBOX_HORIZON_MS = 88ms):
                // Al proyectar el punto objetivo a 88ms con duración de animación de 88ms y actualizar cada 50ms,
                // la velocidad del modelo 3D dentro de Mapbox coincide al 100% con currentSimSpeedMps y el
                // animador jamás se detiene antes del siguiente pulso.
                val horizonSec = MAPBOX_HORIZON_MS / 1000.0
                val horizonDistM = currentSimSpeedMps * horizonSec
                val midHorizonBearingRad = Math.toRadians(
                    normalizeDeg(simBearingDeg + currentAngularVelDegPerSec * horizonSec * 0.5)
                )
                val horizonLat = simLat + (horizonDistM * cos(midHorizonBearingRad)) / METERS_PER_DEG_LAT
                val horizonLon = simLon + (horizonDistM * sin(midHorizonBearingRad)) / (METERS_PER_DEG_LAT * cosLat)
                val horizonBearing = normalizeDeg(simBearingDeg + currentAngularVelDegPerSec * horizonSec)

                dispatchToListeners(
                    baseLoc = baseLoc,
                    lat = horizonLat,
                    lon = horizonLon,
                    bearing = horizonBearing,
                    speedKmH = smoothedSpeedKmH,
                    animMs = MAPBOX_HORIZON_MS
                )
            }
        }
    }

    fun updateSimulationMode(enabled: Boolean) {
        simulationJob?.cancel()
        simulationJob = null
        lastFixElapsedMs = 0L
        if (!enabled) return

        ensureKinematicLoopRunning()

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
        val fixIntervalMs = if (lastFixElapsedMs > 0L) {
            (nowElapsed - lastFixElapsedMs).coerceIn(60L, 3500L)
        } else {
            if (isMock) 100L else 900L
        }
        val dtFixSec = (fixIntervalMs / 1000.0).coerceIn(0.08, 3.5)
        lastFixElapsedMs = nowElapsed
        lastFixIsMock = isMock

        NavigationState.currentLocation.value = loc
        if (!NavigationState.isGpsReady.value) {
            NavigationState.isGpsReady.value = true
        }

        // ── 1. Velocidad calibrada (Doppler GNSS + respaldo geométrico por distancia) ──
        val distFromPrevMeters = prevAcceptedLoc?.distanceTo(loc) ?: 0f
        val fallbackSpeedMps = if (prevAcceptedLoc != null && dtFixSec >= 0.22 && distFromPrevMeters in 0.7f..110f) {
            (distFromPrevMeters / dtFixSec).toFloat()
        } else 0f

        val rawMps = when {
            loc.hasSpeed() && loc.speed > 0.1f -> loc.speed.coerceAtLeast(0f)
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

        // ── 2. Detección de caída de velocidad (Frenado / Entrada en Curva o Esquina) ──
        recentPeakSpeedKmH = maxOf(recentPeakSpeedKmH, calibratedSpeedKmH)
        val speedDropKmH = (recentPeakSpeedKmH - calibratedSpeedKmH).coerceAtLeast(0f)
        val speedDropRatio = if (recentPeakSpeedKmH > 6f) {
            (speedDropKmH / recentPeakSpeedKmH).coerceIn(0f, 1f)
        } else 0f

        if (lastSpeedFixElapsedMs > 0L) {
            val dtSpeedSec = ((nowElapsed - lastSpeedFixElapsedMs) / 1000f).coerceIn(0.08f, 3.0f)
            val instantAccel = ((calibratedSpeedKmH - targetGpsSpeedKmH) / dtSpeedSec).coerceIn(-45f, 35f)
            gpsAccelKmHPerSec = when {
                calibratedSpeedKmH == 0f -> 0f
                instantAccel < -2.2f -> gpsAccelKmHPerSec * 0.30f + instantAccel * 0.70f // Priorizar detección de frenado
                else -> gpsAccelKmHPerSec * 0.48f + instantAccel * 0.52f
            }
        } else {
            smoothedSpeedKmH = calibratedSpeedKmH
            NavigationState.currentSpeedKmH.value = calibratedSpeedKmH
        }
        targetGpsSpeedKmH = calibratedSpeedKmH
        lastSpeedFixElapsedMs = nowElapsed

        // ── 3. Análisis de Trayectoria con las Ubicaciones Registradas ──
        val geoBearingDeg = if (prevAcceptedLoc != null && distFromPrevMeters >= 0.75f) {
            normalizeDeg(prevAcceptedLoc!!.bearingTo(loc).toDouble())
        } else null

        val dopplerBearingDeg = if (loc.hasBearing() && (isMock || loc.speed >= 0.70f)) {
            normalizeDeg(loc.bearing.toDouble())
        } else null

        val measuredBearingDeg = when {
            dopplerBearingDeg != null && geoBearingDeg != null -> {
                // Al desacelerar para doblar una esquina, el vector geométrico entre ubicaciones registradas
                // capta el nuevo rumbo antes que el filtro interno del chip GPS
                val geoWeight = if (speedDropRatio >= 0.14f || calibratedSpeedKmH < 28f) 0.50 else 0.25
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

        val deltaBearing1 = if (prevCrumb != null && calibratedSpeedKmH >= 1.5f) {
            shortestAngleDeltaDeg(prevCrumb.courseDeg, measuredBearingDeg)
        } else 0.0
        val deltaBearing2 = if (prevCrumb2 != null && calibratedSpeedKmH >= 1.5f) {
            shortestAngleDeltaDeg(prevCrumb2.courseDeg, measuredBearingDeg)
        } else deltaBearing1

        val absDelta1 = abs(deltaBearing1)
        val absDelta2 = abs(deltaBearing2)

        breadcrumbs.addLast(
            GpsBreadcrumb(
                lat = loc.latitude,
                lon = loc.longitude,
                speedKmH = calibratedSpeedKmH,
                courseDeg = measuredBearingDeg,
                elapsedMs = nowElapsed
            )
        )
        while (breadcrumbs.size > 8) {
            breadcrumbs.removeFirst()
        }

        // ── 4. Clasificador Inteligente: Seguir Recto vs. Girar por Descenso de Velocidad + Cambio de Trayectoria ──
        isSpeedDescending = speedDropRatio >= 0.13f ||
            speedDropKmH >= 5.0f ||
            gpsAccelKmHPerSec <= -2.8f ||
            (calibratedSpeedKmH in 3.5f..26f && gpsAccelKmHPerSec < -0.6f)

        val hasCornerTrajectoryChange = isSpeedDescending && (absDelta1 >= 3.6 || absDelta2 >= 5.2)
        val hasSustainedCurveChange = absDelta1 >= 6.8 || (absDelta2 >= 9.5 && deltaBearing1 * deltaBearing2 > 0.0)

        if (calibratedSpeedKmH < 1.5f) {
            isTurningManeuver = false
            turnIntensity = 0f
            predictedYawRateDegPerSec = 0.0
        } else if (hasCornerTrajectoryChange || hasSustainedCurveChange) {
            isTurningManeuver = true
            val speedDropBoost = if (isSpeedDescending) (1.0f + speedDropRatio * 1.25f).coerceIn(1.0f, 2.1f) else 1.0f
            turnIntensity = (((maxOf(absDelta1, absDelta2 * 0.7) / 22.0).toFloat()) * speedDropBoost).coerceIn(0.30f, 1.0f)
            targetBearingDeg = measuredBearingDeg

            // Velocidad angular (grados/seg) proyectada para que el auto gire de forma continua
            val rawYawRate = (deltaBearing1 / dtFixSec).coerceIn(-75.0, 75.0)
            predictedYawRateDegPerSec = rawYawRate * (0.60 + 0.38 * turnIntensity)
        } else {
            // Trayectoria recta: filtrar micro-variaciones del GPS para que el auto simule seguir derecho sin zigzagueo
            isTurningManeuver = false
            turnIntensity = 0f
            predictedYawRateDegPerSec = 0.0
            targetBearingDeg = if (hasSimState) {
                lerpAngleDeg(targetBearingDeg, measuredBearingDeg, 0.38)
            } else {
                measuredBearingDeg
            }
        }

        // ── 5. Ancla Física Compensada y Cálculo de Error Elástico (Sin Saltos ni Frenazos) ──
        val calibratedMps = calibratedSpeedKmH / 3.6
        // En rectas compensamos ~0.34s de latencia NMEA; si la velocidad desciende o está girando,
        // reducimos la proyección frontal para que el auto nunca se pase de largo en la esquina.
        val cornerDamping = if (isTurningManeuver) (1.0 - 0.58 * turnIntensity) else 1.0
        val brakingDamping = if (isSpeedDescending) 0.55 else 1.0
        val lookaheadSec = 0.34 * cornerDamping * brakingDamping
        val lookaheadMeters = if (!isMock && calibratedSpeedKmH >= 3.0f) {
            (calibratedMps * lookaheadSec).coerceAtMost(7.0)
        } else 0.0

        val radMeasured = Math.toRadians(targetBearingDeg)
        val cosLat = cos(Math.toRadians(loc.latitude)).coerceAtLeast(0.15)
        val anchorLat = loc.latitude + (lookaheadMeters * cos(radMeasured)) / METERS_PER_DEG_LAT
        val anchorLon = loc.longitude + (lookaheadMeters * sin(radMeasured)) / (METERS_PER_DEG_LAT * cosLat)

        if (!hasSimState) {
            hasSimState = true
            simLat = anchorLat
            simLon = anchorLon
            simBearingDeg = measuredBearingDeg
            targetBearingDeg = measuredBearingDeg
            currentSimSpeedMps = calibratedMps
            lonErrorMeters = 0.0
            latErrorMeters = 0.0
            dispatchToListeners(
                baseLoc = loc,
                lat = simLat,
                lon = simLon,
                bearing = simBearingDeg,
                speedKmH = calibratedSpeedKmH,
                animMs = MAPBOX_HORIZON_MS
            )
        } else {
            val northDiffM = (anchorLat - simLat) * METERS_PER_DEG_LAT
            val eastDiffM = (anchorLon - simLon) * METERS_PER_DEG_LAT * cosLat
            val totalErrorM = hypot(northDiffM, eastDiffM)

            if (totalErrorM > 45.0) {
                // Re-anclaje limpio solo ante saltos grandes (ej. primer fix real tras abrir la app o salir de túnel largo)
                simLat = anchorLat
                simLon = anchorLon
                simBearingDeg = measuredBearingDeg
                targetBearingDeg = measuredBearingDeg
                lonErrorMeters = 0.0
                latErrorMeters = 0.0
            } else {
                // Descomponer discrepancia en componente longitudinal (adelante/atrás) y lateral (izquierda/derecha)
                val radSim = Math.toRadians(simBearingDeg)
                val cosSim = cos(radSim)
                val sinSim = sin(radSim)
                val newLonErr = northDiffM * cosSim + eastDiffM * sinSim
                val newLatErr = -northDiffM * sinSim + eastDiffM * cosSim

                // Suavizar errores para que el bucle cinemático de 20 Hz los absorba progresivamente en movimiento
                lonErrorMeters = (lonErrorMeters * 0.25 + newLonErr * 0.75).coerceIn(-20.0, 20.0)
                latErrorMeters = (latErrorMeters * 0.25 + newLatErr * 0.75).coerceIn(-14.0, 14.0)
            }
        }

        // Estado de luces de freno al detenerse
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

        // Alimentar servicios en segundo plano con la ubicación GPS física real
        RouteTracker.onLocationUpdate(loc)
        DashcamRouteTracker.onLocationUpdate(loc)
        SpeedRecordTracker.onSpeedUpdate(calibratedSpeedKmH, loc)
        appContext?.let { WeatherService.onLocationAvailable(it, loc.latitude, loc.longitude) }

        ensureKinematicLoopRunning()
    }

    /**
     * Si existe una ruta activa cerca del vehículo, anticipa la curvatura de los próximos metros
     * de la vía para que los giros en intersecciones sean aún más precisos.
     */
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

        // Buscar un punto unos metros más adelante sobre la polilínea (según la velocidad actual)
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
        // Solo sugerir si el ángulo es coherente con el sentido de marcha (< 105°)
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
                    calibratedSpeedKmH = smoothedSpeedKmH,
                    animDurationMs = MAPBOX_HORIZON_MS,
                    isMock = lastFixIsMock
                )
            }
        }
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }
}
