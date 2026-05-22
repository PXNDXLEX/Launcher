package com.tuusuario.carlauncher.ui.map

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Looper
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.google.android.gms.location.*
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.dsl.cameraOptions
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.locationcomponent.LocationComponentPlugin
import com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateOptions
import com.mapbox.maps.plugin.viewport.data.OverviewViewportStateOptions
import com.mapbox.maps.plugin.viewport.viewport
import com.mapbox.maps.plugin.viewport.data.DefaultViewportTransitionOptions
import com.tuusuario.carlauncher.R
import com.tuusuario.carlauncher.ui.AppSettings
import com.tuusuario.carlauncher.ui.NavigationState
import com.tuusuario.carlauncher.ui.RouteStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// ── Modelos ──────────────────────────────────────────────────────────────────

data class PlaceResult(
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    val iconType: String = "Star",
    val distanceKm: Double = 0.0
)

class FavoritesManager(context: Context) {
    private val prefs = context.getSharedPreferences("CarFavorites", Context.MODE_PRIVATE)
    fun getFavorites(): List<PlaceResult> {
        val jsonString = prefs.getString("favs", "[]") ?: "[]"
        val array = JSONArray(jsonString)
        val list = mutableListOf<PlaceResult>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(PlaceResult(obj.getString("name"), obj.optString("address", ""), obj.getDouble("lat"), obj.getDouble("lon"), obj.optString("iconType", "Star")))
        }
        return list
    }
    fun addFavorite(place: PlaceResult) {
        val list = getFavorites().toMutableList()
        if (list.none { it.name == place.name }) {
            list.add(place)
            val array = JSONArray()
            list.forEach {
                val obj = JSONObject()
                obj.put("name", it.name); obj.put("address", it.address); obj.put("lat", it.lat); obj.put("lon", it.lon); obj.put("iconType", it.iconType)
                array.put(obj)
            }
            prefs.edit().putString("favs", array.toString()).apply()
        }
    }
}

// ── IDs de capas/fuentes Mapbox ──────────────────────────────────────────────
private const val ROUTE_SOURCE_ID        = "route-source"
private const val ROUTE_LAYER_ID         = "route-layer"
private const val ROUTE_CASING_LAYER_ID  = "route-casing-layer"
private const val HISTORY_SOURCE_ID      = "history-source"
private const val HISTORY_LAYER_ID       = "history-layer"
private const val DASHCAM_SOURCE_ID      = "dashcam-source"
private const val DASHCAM_LAYER_ID       = "dashcam-layer"

// ── Composable Principal ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationMap(modifier: Modifier = Modifier, isFullScreen: Boolean = false, isDarkMode: Boolean = true) {
    val context         = LocalContext.current
    val lifecycleOwner  = LocalLifecycleOwner.current
    val focusManager    = LocalFocusManager.current
    val vehicleType     = AppSettings.vehicleType.value
    val uiColor         = AppSettings.uiColor.value
    val mapIconColor    = AppSettings.mapIconColor.value
    val favManager      = remember { FavoritesManager(context) }
    val coroutineScope  = rememberCoroutineScope()

    // MapView instance — persiste entre recomposiciones
    val mapView = remember { MapView(context) }

    // Estado
    val isMapDarkMode = AppSettings.isMapDarkMode.value
    var isFollowingLocation by rememberSaveable { mutableStateOf(true) }
    var hasInitializedPosition by rememberSaveable { mutableStateOf(false) }

    var showSaveFavoriteDialog by remember { mutableStateOf(false) }
    var favoriteNameToSave     by remember { mutableStateOf("") }
    var favoriteLocationToSave by remember { mutableStateOf<Point?>(null) }
    var selectedIconType       by remember { mutableStateOf("Star") }

    // NavigationState compartido
    var selectedDestination by NavigationState.activeDestination
    var routeDistanceText   by NavigationState.activeRouteDistance
    val currentRoutePoints  = NavigationState.activeRoutePoints   // List<Point> de Mapbox GeoJSON
    val activeRouteSteps    = NavigationState.activeRouteSteps

    var showArrivalAlert    by remember { mutableStateOf(false) }
    var isCalculatingRoute  by remember { mutableStateOf(false) }
    var showStyleSelector   by remember { mutableStateOf(false) }

    // Progreso animado para la transición 3D
    val perspectiveProgress by animateFloatAsState(
        targetValue = if (NavigationState.isRouteActive.value && isFollowingLocation) 1f else 0f,
        animationSpec = tween(durationMillis = 700),
        label = "perspective_3d"
    )

    val currentStyle = AppSettings.mapStyle.value
    val routeColorHex = when (currentStyle) {
        "NEON" -> "#FF9100"
        else   -> "#2979FF"
    }

    // Referencias al mapa — guardadas en remember para no recrear
    var mapReady          by remember { mutableStateOf(false) }
    var lastAppliedStyle  by remember { mutableStateOf("") }
    var lastKnownBearing  by remember { mutableStateOf(0f) }
    var currentMapRotation by remember { mutableStateOf(0f) }
    var animator: ValueAnimator? by remember { mutableStateOf(null) }
    var autoCenterJob     by remember { mutableStateOf<Job?>(null) }

    var searchQuery      by remember { mutableStateOf("") }
    var searchResults    by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var showFavorites    by remember { mutableStateOf(false) }
    var isSearching      by remember { mutableStateOf(false) }

    // Punto de destino como Mapbox Point
    var destAnnotation   by remember { mutableStateOf<PointAnnotation?>(null) }
    var pointAnnotMgr    by remember { mutableStateOf<PointAnnotationManager?>(null) }

    // ── Función de actualización de la fuente de ruta ─────────────────────
    fun updateRouteOnMap() {
        if (!mapReady) return
        val map = mapView.mapboxMap
        map.getStyle { style ->
            val source = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID) ?: return@getStyle
            if (NavigationState.isRouteActive.value && currentRoutePoints.isNotEmpty()) {
                // currentRoutePoints ya son List<Point> de Mapbox — sin conversión
                source.featureCollection(
                    FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(currentRoutePoints)))
                )
            } else {
                source.featureCollection(FeatureCollection.fromFeatures(emptyList()))
            }
        }
    }

    fun updateHistoryOnMap() {
        if (!mapReady) return
        val map = mapView.mapboxMap
        map.getStyle { style ->
            val source = style.getSourceAs<GeoJsonSource>(HISTORY_SOURCE_ID) ?: return@getStyle
            val selectedRoute   = NavigationState.selectedHistoryRoute.value
            val selectedSegment = NavigationState.selectedHistorySegment.value
            if (selectedRoute != null) {
                val pts = if (selectedSegment != null) {
                    selectedSegment.points.map { Point.fromLngLat(it.lon, it.lat) }
                } else {
                    selectedRoute.segments.flatMap { s -> s.points.map { Point.fromLngLat(it.lon, it.lat) } }
                }
                if (pts.isNotEmpty()) {
                    source.featureCollection(FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(pts))))
                    isFollowingLocation = false
                    val first = pts.first()
                    mapView.mapboxMap.setCamera(cameraOptions {
                        center(first); zoom(16.0); pitch(0.0); bearing(0.0)
                    })
                }
            } else {
                source.featureCollection(FeatureCollection.fromFeatures(emptyList()))
            }
        }
    }

    fun updateDashcamOnMap() {
        if (!mapReady) return
        val map = mapView.mapboxMap
        map.getStyle { style ->
            val source = style.getSourceAs<GeoJsonSource>(DASHCAM_SOURCE_ID) ?: return@getStyle
            val selectedDashcam = NavigationState.selectedDashcamRoute.value
            if (selectedDashcam != null) {
                val pts = selectedDashcam.map { Point.fromLngLat(it.lon, it.lat) }
                if (pts.isNotEmpty()) {
                    source.featureCollection(FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(pts))))
                    isFollowingLocation = false
                    mapView.mapboxMap.setCamera(cameraOptions {
                        center(pts.first()); zoom(17.0); pitch(0.0); bearing(0.0)
                    })
                }
            } else {
                source.featureCollection(FeatureCollection.fromFeatures(emptyList()))
            }
        }
    }

    // ── Función para aplicar estilo Mapbox ───────────────────────────────────
    fun applyMapStyle(style: String) {
        val styleUri = when (style) {
            "SATELLITE" -> Style.SATELLITE_STREETS
            "DARK"      -> Style.DARK
            "NEON"      -> Style.TRAFFIC_NIGHT
            else        -> Style.MAPBOX_STREETS
        }
        mapView.mapboxMap.loadStyle(styleUri) { loadedStyle ->
            mapReady = true
            lastAppliedStyle = style

            // Fuente + capas de ruta (casing + fill)
            loadedStyle.addSource(geoJsonSource(ROUTE_SOURCE_ID) {
                featureCollection(FeatureCollection.fromFeatures(emptyList()))
            })
            loadedStyle.addSource(geoJsonSource(HISTORY_SOURCE_ID) {
                featureCollection(FeatureCollection.fromFeatures(emptyList()))
            })
            loadedStyle.addSource(geoJsonSource(DASHCAM_SOURCE_ID) {
                featureCollection(FeatureCollection.fromFeatures(emptyList()))
            })

            // Casing de la ruta (borde oscuro)
            loadedStyle.addLayer(lineLayer(ROUTE_CASING_LAYER_ID, ROUTE_SOURCE_ID) {
                lineColor("#0D1B2A")
                lineWidth(14.0)
                lineCap(LineCap.ROUND)
                lineJoin(LineJoin.ROUND)
                lineOpacity(0.8)
            })
            // Ruta principal
            loadedStyle.addLayer(lineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID) {
                lineColor(routeColorHex)
                lineWidth(9.0)
                lineCap(LineCap.ROUND)
                lineJoin(LineJoin.ROUND)
            })
            // Ruta histórica
            loadedStyle.addLayer(lineLayer(HISTORY_LAYER_ID, HISTORY_SOURCE_ID) {
                lineColor("#4CAF50")
                lineWidth(5.0)
                lineCap(LineCap.ROUND)
                lineJoin(LineJoin.ROUND)
            })
            // Ruta dashcam
            loadedStyle.addLayer(lineLayer(DASHCAM_LAYER_ID, DASHCAM_SOURCE_ID) {
                lineColor("#FF9800")
                lineWidth(5.0)
                lineCap(LineCap.ROUND)
                lineJoin(LineJoin.ROUND)
            })

            // LocationComponent — puck del vehículo
            val locPlugin = mapView.location
            locPlugin.updateSettings {
                enabled = true
                pulsingEnabled = true
                pulsingColor = android.graphics.Color.parseColor(
                    when (style) { "NEON" -> "#FF9100"; else -> "#2979FF" }
                )
                locationPuck = getVehiclePuck(context, AppSettings.vehicleType.value, AppSettings.customVehicleIconPath.value, mapIconColor, AppSettings.vehicle3DScale.value)
            }

            // Recrear anotaciones si ya hay destino activo
            val activeDest = NavigationState.activeDestination.value
            if (activeDest != null) {
                val pm = mapView.annotations.createPointAnnotationManager()
                pointAnnotMgr = pm
                val iconBmp = drawCustomPin(uiColor)
                val opts = PointAnnotationOptions()
                    .withPoint(activeDest)  // activeDest ya es Point
                    .withIconImage(iconBmp)
                destAnnotation = pm.create(opts)
            }

            // Re-aplicar rutas
            updateRouteOnMap()
            updateHistoryOnMap()
            updateDashcamOnMap()
        }
    }

    // ── Función de cálculo de ruta (Valhalla OSM) ────────────────────────────
    val calculateRoute: (com.mapbox.geojson.Point, com.mapbox.geojson.Point) -> Unit = { startGeo, destGeo ->
        if (!isCalculatingRoute) {
            isCalculatingRoute = true
            coroutineScope.launch {
                routeDistanceText = "Calculando ruta..."
                try {
                    val mapboxToken = context.getString(R.string.mapbox_access_token)
                    val urlStr = "https://api.mapbox.com/directions/v5/mapbox/driving/${startGeo.longitude()},${startGeo.latitude()};${destGeo.longitude()},${destGeo.latitude()}?alternatives=false&geometries=geojson&language=es&overview=full&steps=true&access_token=$mapboxToken"
                    val result = withContext(Dispatchers.IO) {
                        val url = URL(urlStr)
                        val conn = url.openConnection() as HttpURLConnection
                        conn.requestMethod = "GET"
                        conn.setRequestProperty("User-Agent", "CarLauncherApp")
                        conn.connectTimeout = 10000
                        conn.readTimeout = 10000
                        try {
                            val code = conn.responseCode
                            if (code >= 400) {
                                val err = conn.errorStream?.bufferedReader()?.readText() ?: "Error $code"
                                throw Exception(err)
                            }
                            conn.inputStream.bufferedReader().readText()
                        } finally { conn.disconnect() }
                    }

                    NavigationState.cachedRouteJson.value = result
                    val json   = JSONObject(result)
                    val routes = json.getJSONArray("routes")
                    if (routes.length() > 0) {
                        val route  = routes.getJSONObject(0)
                        val distKm = route.getDouble("distance") / 1000.0
                        routeDistanceText = if (distKm > 1.0)
                            String.format("%.1f km restantes", distKm)
                        else
                            "${(distKm * 1000).toInt()} m restantes"

                        // Parsear geometría GeoJSON directamente
                        val geometry = route.getJSONObject("geometry")
                        val coordinates = geometry.getJSONArray("coordinates")
                        currentRoutePoints.clear()
                        for (i in 0 until coordinates.length()) {
                            val coord = coordinates.getJSONArray(i)
                            currentRoutePoints.add(Point.fromLngLat(coord.getDouble(0), coord.getDouble(1)))
                        }

                        activeRouteSteps.clear()
                        val legs = route.getJSONArray("legs")
                        if (legs.length() > 0) {
                            val leg   = legs.getJSONObject(0)
                            val steps = leg.getJSONArray("steps")
                            for (i in 0 until steps.length()) {
                                val step     = steps.getJSONObject(i)
                                val maneuver = step.getJSONObject("maneuver")
                                val locArray = maneuver.getJSONArray("location")
                                
                                val manType    = maneuver.optString("type", "turn")
                                val manMod     = maneuver.optString("modifier", "straight")
                                val streetName = step.optString("name", "")
                                
                                activeRouteSteps.add(RouteStep(
                                    maneuverType = manType,
                                    modifier     = manMod,
                                    distance     = step.getDouble("distance"),
                                    streetName   = streetName,
                                    maneuverLat  = locArray.getDouble(1),
                                    maneuverLon  = locArray.getDouble(0)
                                ))
                            }
                        }
                    }

                    NavigationState.isRouteActive.value = true
                    isFollowingLocation = true
                    autoCenterJob?.cancel()
                    updateRouteOnMap()

                    // Activar viewport 3D de seguimiento
                    val vp = mapView.viewport
                    val followState = vp.makeFollowPuckViewportState(
                        FollowPuckViewportStateOptions.Builder()
                            .pitch(60.0)
                            .zoom(18.5)
                            .bearing(com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateBearing.SyncWithLocationPuck)
                            .build()
                    )
                    vp.transitionTo(followState,
                        vp.makeDefaultViewportTransition(
                            DefaultViewportTransitionOptions.Builder().maxDurationMs(1200).build()
                        )
                    )

                } catch (e: Exception) {
                    if (NavigationState.cachedRouteJson.value != null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Sin Internet: Usando ruta en caché", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        val dist = android.location.Location("").apply {
                            latitude = startGeo.latitude(); longitude = startGeo.longitude()
                        }.distanceTo(android.location.Location("").apply {
                            latitude = destGeo.latitude(); longitude = destGeo.longitude()
                        })
                        routeDistanceText = "Error: ${String.format("%.1f km (Directo)", dist / 1000)}"
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Error de red: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } finally {
                    isCalculatingRoute = false
                }
            }
        }
    }

    // ── Ciclo de vida del MapView ────────────────────────────────────────────
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START   -> mapView.onStart()
                Lifecycle.Event.ON_STOP    -> mapView.onStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── GPS y seguimiento ────────────────────────────────────────────────────
    DisposableEffect(context) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val isBatterySaver = AppSettings.batterySaverMode.value
        // 100ms en modo normal para máxima respuesta de navegación
        val interval    = if (isBatterySaver) 3000L else 100L
        val minInterval = if (isBatterySaver) 2000L else 50L
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval)
            .setMinUpdateIntervalMillis(minInterval)
            .setMinUpdateDistanceMeters(0.5f)
            .setMaxUpdateDelayMillis(if (isBatterySaver) 4000L else 200L)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                if (loc.hasAccuracy() && loc.accuracy > 40f) return

                // Bearing
                var targetBearing = lastKnownBearing
                if (loc.hasBearing() && loc.speed > 0.8f) {
                    targetBearing    = loc.bearing
                    lastKnownBearing = targetBearing
                }

                // Servicios de rastreo
                com.tuusuario.carlauncher.services.RouteTracker.onLocationUpdate(loc)
                com.tuusuario.carlauncher.services.DashcamRouteTracker.onLocationUpdate(loc)

                NavigationState.currentLocation.value = loc
                if (loc.hasSpeed()) NavigationState.currentSpeedKmH.value = loc.speed * 3.6f

                // Animar el LocationComponent del mapa es automático via el plugin;
                // aquí actualizamos la cámara si estamos en modo seguimiento sin ruta activa
                val isRouteActive = NavigationState.isRouteActive.value
                if (!isRouteActive && isFollowingLocation && hasInitializedPosition) {
                    val vp = mapView.viewport
                    // Solo actualizar la cámara manualmente si el viewport no está en follow mode
                    if (vp.status !is com.mapbox.maps.plugin.viewport.ViewportStatus.State) {
                        mapView.camera.easeTo(
                            cameraOptions {
                                center(Point.fromLngLat(loc.longitude, loc.latitude))
                                zoom(18.2)
                                bearing(targetBearing.toDouble())
                                pitch(0.0)
                            },
                            MapAnimationOptions.mapAnimationOptions { duration(900) }
                        )
                    }
                }

                // ── Lógica de ruta activa ────────────────────────────────
                if (currentRoutePoints.isNotEmpty() && selectedDestination != null) {
                    var minDistance  = Float.MAX_VALUE
                    var closestIndex = 0
                    val searchLimit  = minOf(100, currentRoutePoints.size)

                    for (i in 0 until searchLimit) {
                        val pt = currentRoutePoints[i]
                        // Point usa .latitude() y .longitude() con paréntesis
                        val dist = FloatArray(1).also {
                            android.location.Location.distanceBetween(loc.latitude, loc.longitude, pt.latitude(), pt.longitude(), it)
                        }[0]
                        if (dist < minDistance) { minDistance = dist; closestIndex = i }
                    }

                    // Auto-recálculo si se desvía >100m
                    if (minDistance > 100f && !isCalculatingRoute) {
                        val destPt = NavigationState.activeDestination.value!!
                        calculateRoute(
                            Point.fromLngLat(loc.longitude, loc.latitude),
                            destPt  // ya es Point
                        )
                    }
                    // Seguimiento: borrar puntos ya recorridos (la ruta se "consume")
                    else if (closestIndex > 0 && minDistance < 60f) {
                        for (i in 0 until closestIndex) {
                            if (currentRoutePoints.isNotEmpty()) currentRoutePoints.removeAt(0)
                        }
                        updateRouteOnMap() // actualizar polyline en tiempo real
                    }

                    // Pasar steps
                    if (activeRouteSteps.isNotEmpty()) {
                        val nextStep = activeRouteSteps[0]
                        val distToStep = FloatArray(1).also {
                            android.location.Location.distanceBetween(loc.latitude, loc.longitude, nextStep.maneuverLat, nextStep.maneuverLon, it)
                        }[0]
                        if (distToStep < 20f) activeRouteSteps.removeAt(0)
                    }

                    // Llegada
                    if (currentRoutePoints.size < 10) {
                        val endPt = currentRoutePoints.last()
                        val distToDest = FloatArray(1).also {
                            android.location.Location.distanceBetween(loc.latitude, loc.longitude, endPt.latitude(), endPt.longitude(), it)
                        }[0]
                        if (distToDest < 30f) {
                            showArrivalAlert = true
                            NavigationState.clearActiveRoute()
                            updateRouteOnMap()
                            // Volver a vista normal
                            mapView.viewport.idle()
                        }
                    }
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
        onDispose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            animator?.cancel()
        }
    }

    // ── Reaccionar al cambio de estilo ───────────────────────────────────────
    LaunchedEffect(currentStyle) {
        if (currentStyle != lastAppliedStyle) {
            applyMapStyle(currentStyle)
        }
    }

    // ── Reaccionar a rutas históricas/dashcam ────────────────────────────────
    LaunchedEffect(NavigationState.selectedHistoryRoute.value, NavigationState.selectedHistorySegment.value) {
        updateHistoryOnMap()
    }
    LaunchedEffect(NavigationState.selectedDashcamRoute.value) {
        updateDashcamOnMap()
    }

    // ── Reaccionar a cambios de tipo de vehículo/escala/color ────────────
    LaunchedEffect(vehicleType, AppSettings.customVehicleIconPath.value, mapIconColor, AppSettings.vehicle3DScale.value) {
        if (!mapReady) return@LaunchedEffect
        mapView.location.updateSettings {
            locationPuck = getVehiclePuck(context, vehicleType, AppSettings.customVehicleIconPath.value, mapIconColor, AppSettings.vehicle3DScale.value)
        }
    }

    // ── UI ───────────────────────────────────────────────────────────────────
    Box(modifier = modifier.fillMaxSize()) {

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                mapView.apply {
                    // Inicializar el mapa con el estilo correcto
                    applyMapStyle(currentStyle)

                    // Gestures: long-press para destino
                    gestures.addOnMapLongClickListener { point ->
                        if (NavigationState.isRouteActive.value) return@addOnMapLongClickListener true

                        val destPt = point   // ya es Point de Mapbox
                        selectedDestination = destPt  // Point? → Point
                        routeDistanceText   = ""
                        isFollowingLocation = false

                        // Anotar destino
                        destAnnotation?.let { pointAnnotMgr?.delete(it) }
                        val pm = pointAnnotMgr ?: mapView.annotations.createPointAnnotationManager().also { pointAnnotMgr = it }
                        val iconBmp = drawCustomPin(uiColor)
                        destAnnotation = pm.create(PointAnnotationOptions().withPoint(destPt).withIconImage(iconBmp))
                        true
                    }

                    // Click normal — limpiar UI
                    gestures.addOnMapClickListener { _ ->
                        focusManager.clearFocus()
                        searchResults = emptyList()
                        showFavorites = false
                        false
                    }

                    // Detectar gestos del usuario para desactivar el follow mode
                    gestures.addOnMoveListener(object : com.mapbox.maps.plugin.gestures.OnMoveListener {
                        override fun onMoveBegin(detector: com.mapbox.android.gestures.MoveGestureDetector) {
                            if (!NavigationState.isRouteActive.value) {
                                isFollowingLocation = false
                                mapView.viewport.idle()
                                autoCenterJob?.cancel()
                                autoCenterJob = coroutineScope.launch {
                                    delay(6000)
                                    isFollowingLocation = true
                                    // Volver al follow puck mode suavemente
                                    val vp = mapView.viewport
                                    val followState = vp.makeFollowPuckViewportState(
                                        FollowPuckViewportStateOptions.Builder()
                                            .pitch(0.0).zoom(18.2)
                                            .bearing(com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateBearing.SyncWithLocationPuck)
                                            .build()
                                    )
                                    vp.transitionTo(followState, vp.makeDefaultViewportTransition())
                                }
                            }
                        }
                        override fun onMove(detector: com.mapbox.android.gestures.MoveGestureDetector) = false
                        override fun onMoveEnd(detector: com.mapbox.android.gestures.MoveGestureDetector) {}
                    })

                    // Primera posición
                    NavigationState.currentLocation.value?.let { loc ->
                        mapboxMap.setCamera(cameraOptions {
                            center(Point.fromLngLat(loc.longitude, loc.latitude))
                            zoom(18.2)
                            pitch(0.0)
                        })
                        hasInitializedPosition = true
                    }
                }
            },
            update = { _ ->
                // El mapa se actualiza vía LaunchedEffects y callbacks; aquí solo
                // sincronizamos el marcador de destino cuando cambia el estado global
                if (mapReady) {
                    val activeDest = NavigationState.activeDestination.value
                    if (activeDest != null && destAnnotation == null) {
                        val pm = pointAnnotMgr ?: mapView.annotations.createPointAnnotationManager().also { pointAnnotMgr = it }
                        val iconBmp = drawCustomPin(uiColor)
                        destAnnotation = pm.create(
                            PointAnnotationOptions()
                                .withPoint(activeDest)  // activeDest ya es Point
                                .withIconImage(iconBmp)
                        )
                    } else if (activeDest == null && destAnnotation != null) {
                        pointAnnotMgr?.delete(destAnnotation!!)
                        destAnnotation = null
                    }
                }
            }
        )

        // ── Barra de búsqueda (oculta en modo ruta) ──────────────────────────
        AnimatedVisibility(
            visible = !NavigationState.isRouteActive.value,
            enter   = fadeIn() + expandVertically(),
            exit    = fadeOut() + shrinkVertically()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier      = Modifier.weight(1f).height(48.dp),
                        shape         = RoundedCornerShape(24.dp),
                        color         = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shadowElevation = 8.dp
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, null, tint = Color(uiColor), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) Text("¿A dónde vamos?", color = Color.Gray, fontSize = 14.sp)
                                androidx.compose.foundation.text.BasicTextField(
                                    value         = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier      = Modifier.fillMaxWidth(),
                                    textStyle     = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = {
                                        if (searchQuery.isNotEmpty()) {
                                            isSearching = true
                                            coroutineScope.launch {
                                                searchResults = searchPlaces(searchQuery, NavigationState.currentLocation.value)
                                                isSearching = false
                                            }
                                        }
                                        focusManager.clearFocus()
                                    })
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = ""; searchResults = emptyList() }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    SmallFloatingActionButton(
                        onClick        = { showFavorites = !showFavorites; searchResults = emptyList() },
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        contentColor   = if (showFavorites) Color(uiColor) else MaterialTheme.colorScheme.onSurface
                    ) {
                        Icon(if (showFavorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, modifier = Modifier.size(20.dp))
                    }
                }

                AnimatedVisibility(visible = searchResults.isNotEmpty() || showFavorites, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(max = 280.dp),
                        shape    = RoundedCornerShape(20.dp),
                        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                    ) {
                        val list = if (showFavorites) favManager.getFavorites() else searchResults
                        LazyColumn {
                            items(list) { place ->
                                ListItem(
                                    headlineContent   = { Text(place.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp) },
                                    supportingContent = { Text(place.address, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp) },
                                    leadingContent    = {
                                        Icon(
                                            when (place.iconType) {
                                                "Home" -> Icons.Default.Home
                                                "Work" -> Icons.Default.Work
                                                else   -> Icons.Default.Place
                                            },
                                            null, tint = Color(uiColor), modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        val destPt = Point.fromLngLat(place.lon, place.lat)
                                        selectedDestination = destPt  // Point

                                        destAnnotation?.let { pointAnnotMgr?.delete(it) }
                                        val pm = pointAnnotMgr ?: mapView.annotations.createPointAnnotationManager().also { pointAnnotMgr = it }
                                        val iconBmp = drawCustomPin(uiColor)
                                        destAnnotation = pm.create(PointAnnotationOptions().withPoint(destPt).withIconImage(iconBmp))

                                        mapView.camera.easeTo(cameraOptions { center(destPt); zoom(15.0) }, MapAnimationOptions.mapAnimationOptions { duration(800) })

                                        searchResults = emptyList()
                                        showFavorites = false
                                        focusManager.clearFocus()
                                    },
                                    trailingContent = {
                                        if (place.distanceKm > 0) {
                                            Text(
                                                text = if (place.distanceKm < 1) "${(place.distanceKm * 1000).toInt()} m" else String.format("%.1f km", place.distanceKm),
                                                fontSize = 11.sp, color = Color(uiColor).copy(alpha = 0.7f), fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── HUD Navegación Turn-by-Turn ──────────────────────────────────────
        val nextStep = activeRouteSteps.firstOrNull()
        AnimatedVisibility(
            visible  = NavigationState.isRouteActive.value && nextStep != null,
            enter    = slideInHorizontally { -it } + fadeIn(),
            exit     = slideOutHorizontally { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 8.dp)
        ) {
            val step = nextStep ?: return@AnimatedVisibility
            Surface(
                shape          = RoundedCornerShape(16.dp),
                color          = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                shadowElevation = 6.dp,
                border         = androidx.compose.foundation.BorderStroke(1.dp, Color(uiColor).copy(alpha = 0.4f)),
                modifier       = Modifier.widthIn(max = 220.dp)
            ) {
                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier        = Modifier.size(36.dp).background(Color(uiColor).copy(alpha = 0.18f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(getManeuverIcon(step.modifier, step.maneuverType), null, tint = Color(uiColor), modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            if (step.distance > 1000) String.format("%.1f km", step.distance / 1000) else "${step.distance.toInt()} m",
                            fontSize = 11.sp, color = Color(uiColor), fontWeight = FontWeight.Bold
                        )
                        Text(
                            step.streetName.ifEmpty { "Recto" },
                            fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // ── FABs de control (derecha) ─────────────────────────────────────────
        Column(
            modifier             = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
            horizontalAlignment  = Alignment.End,
            verticalArrangement  = Arrangement.spacedBy(8.dp)
        ) {
            // Selector de estilo
            Box {
                FloatingActionButton(
                    onClick        = { showStyleSelector = !showStyleSelector },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                    contentColor   = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier       = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = when (currentStyle) {
                            "SATELLITE" -> Icons.Default.Public
                            "NEON"      -> Icons.Default.Bolt
                            "DARK"      -> Icons.Default.DarkMode
                            else        -> Icons.Default.LightMode
                        },
                        contentDescription = "Estilo Mapa"
                    )
                }
                DropdownMenu(
                    expanded          = showStyleSelector,
                    onDismissRequest  = { showStyleSelector = false },
                    modifier          = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    DropdownMenuItem(text = { Text("Día") },           leadingIcon = { Icon(Icons.Default.LightMode, null) },                                     onClick = { AppSettings.setMapStyle("LIGHT");     showStyleSelector = false })
                    DropdownMenuItem(text = { Text("Noche") },         leadingIcon = { Icon(Icons.Default.DarkMode, null) },                                      onClick = { AppSettings.setMapStyle("DARK");      showStyleSelector = false })
                    DropdownMenuItem(text = { Text("Neon Electric") }, leadingIcon = { Icon(Icons.Default.Bolt, null, tint = Color(0xFF00B0FF)) },                 onClick = { AppSettings.setMapStyle("NEON");      showStyleSelector = false })
                    DropdownMenuItem(text = { Text("Satélite") },      leadingIcon = { Icon(Icons.Default.Public, null, tint = Color(0xFF4CAF50)) },               onClick = { AppSettings.setMapStyle("SATELLITE"); showStyleSelector = false })
                }
            }

            // Botón recentrar
            AnimatedVisibility(visible = !isFollowingLocation) {
                FloatingActionButton(
                    onClick        = {
                        isFollowingLocation = true
                        autoCenterJob?.cancel()
                        val isRouteActive = NavigationState.isRouteActive.value
                        val vp = mapView.viewport
                        val followState = vp.makeFollowPuckViewportState(
                            FollowPuckViewportStateOptions.Builder()
                                .pitch(if (isRouteActive) 60.0 else 0.0)
                                .zoom(if (isRouteActive) 18.5 else 18.2)
                                .bearing(com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateBearing.SyncWithLocationPuck)
                                .build()
                        )
                        vp.transitionTo(followState, vp.makeDefaultViewportTransition())
                    },
                    containerColor = Color(uiColor).copy(alpha = 0.9f),
                    contentColor   = Color.White
                ) {
                    Icon(Icons.Default.MyLocation, "Centrar")
                }
            }
        }

        // ── Panel inferior: acciones de ruta ─────────────────────────────────
        AnimatedVisibility(
            visible  = selectedDestination != null,
            enter    = slideInVertically { it } + fadeIn(),
            exit     = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Surface(
                modifier        = Modifier.fillMaxWidth(),
                shape           = RoundedCornerShape(24.dp),
                color           = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 8.dp
            ) {
                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (NavigationState.isRouteActive.value) "En ruta" else "Destino",
                            fontWeight = FontWeight.SemiBold, color = Color.Gray, fontSize = 11.sp
                        )
                        Text(
                            routeDistanceText.ifEmpty { if (NavigationState.isRouteActive.value) "Calculando..." else "Listo para iniciar" },
                            fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(uiColor)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))

                    if (!NavigationState.isRouteActive.value) {
                        IconButton(
                            onClick  = {
                                selectedDestination = null
                                routeDistanceText   = ""
                                destAnnotation?.let { pointAnnotMgr?.delete(it) }
                                destAnnotation = null
                            },
                            modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(14.dp))
                        ) {
                            Icon(Icons.Default.Close, "Cancelar", tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                NavigationState.currentLocation.value?.let { loc ->
                                    val dest = selectedDestination!!
                                    calculateRoute(
                                        Point.fromLngLat(loc.longitude, loc.latitude),
                                        Point.fromLngLat(dest.longitude(), dest.latitude())
                                    )
                                }
                            },
                            shape           = RoundedCornerShape(16.dp),
                            colors          = ButtonDefaults.buttonColors(containerColor = Color(uiColor)),
                            modifier        = Modifier.height(44.dp),
                            contentPadding  = PaddingValues(horizontal = 14.dp)
                        ) {
                            Icon(Icons.Default.Directions, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("INICIAR", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    } else {
                        Button(
                            onClick = {
                                NavigationState.clearActiveRoute()
                                selectedDestination = null
                                destAnnotation?.let { pointAnnotMgr?.delete(it) }
                                destAnnotation = null
                                updateRouteOnMap()
                                mapView.viewport.idle()
                            },
                            shape          = RoundedCornerShape(16.dp),
                            colors         = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier       = Modifier.height(44.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp)
                        ) {
                            Icon(Icons.Default.Stop, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PARAR", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // ── Alerta de llegada ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = showArrivalAlert,
            enter    = scaleIn(spring(dampingRatio = 0.5f, stiffness = 500f)) + fadeIn(),
            exit     = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            LaunchedEffect(showArrivalAlert) {
                if (showArrivalAlert) { delay(4000); showArrivalAlert = false }
            }
            Card(
                shape    = RoundedCornerShape(24.dp),
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)),
                elevation = CardDefaults.cardElevation(16.dp),
                modifier = Modifier.padding(32.dp).fillMaxWidth(0.8f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp).fillMaxWidth()) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("¡Has Llegado!", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Has alcanzado tu destino.", style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onPrimaryContainer))
                }
            }
        }

        // ── Diálogo guardar favorito ──────────────────────────────────────────
        if (showSaveFavoriteDialog) {
            AlertDialog(
                onDismissRequest = { showSaveFavoriteDialog = false },
                title            = { Text("Guardar Favorito") },
                text             = {
                    Column {
                        TextField(value = favoriteNameToSave, onValueChange = { favoriteNameToSave = it }, label = { Text("Nombre") })
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Star", "Home", "Work").forEach { type ->
                                FilterChip(selected = selectedIconType == type, onClick = { selectedIconType = type }, label = { Text(type) })
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        favoriteLocationToSave?.let {
                            favManager.addFavorite(PlaceResult(favoriteNameToSave, "Favorito guardado", it.latitude(), it.longitude(), selectedIconType))
                            showSaveFavoriteDialog = false
                            Toast.makeText(context, "Guardado", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Guardar") }
                }
            )
        }
    }
}

// ── Helpers de UI ─────────────────────────────────────────────────────────────

private fun getManeuverIcon(modifier: String, type: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when {
        type.contains("arrive")   -> Icons.Default.Flag
        modifier.contains("left") -> if (modifier.contains("sharp")) Icons.Default.TurnSharpLeft else Icons.Default.TurnLeft
        modifier.contains("right") -> if (modifier.contains("sharp")) Icons.Default.TurnSharpRight else Icons.Default.TurnRight
        modifier.contains("uturn") -> Icons.Default.UTurnLeft
        modifier.contains("straight") -> Icons.Default.Straight
        else -> Icons.Default.Navigation
    }
}

// ── Búsqueda de lugares (Nominatim) ──────────────────────────────────────────

suspend fun searchPlaces(query: String, currentLoc: android.location.Location?): List<PlaceResult> = withContext(Dispatchers.IO) {
    try {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val urlStr = buildString {
            append("https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&addressdetails=1&limit=500")
            if (currentLoc != null) {
                val lat = currentLoc.latitude; val lon = currentLoc.longitude
                append("&viewbox=${lon - 3.0},${lat + 3.0},${lon + 3.0},${lat - 3.0}&lat=$lat&lon=$lon")
            }
        }
        val url  = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "CarLauncherApp")
        val response = conn.inputStream.bufferedReader().readText()
        val array    = JSONArray(response)
        val list     = mutableListOf<PlaceResult>()
        for (i in 0 until array.length()) {
            val obj    = array.getJSONObject(i)
            val lat    = obj.getDouble("lat")
            val lon    = obj.getDouble("lon")
            var distKm = 0.0
            if (currentLoc != null) {
                val r = FloatArray(1)
                android.location.Location.distanceBetween(currentLoc.latitude, currentLoc.longitude, lat, lon, r)
                distKm = (r[0] / 1000.0).toDouble()
            }
            if (currentLoc == null || distKm <= 300.0) {
                list.add(PlaceResult(obj.getString("display_name").split(",")[0], obj.getString("display_name"), lat, lon, distanceKm = distKm))
            }
        }
        list.sortBy { it.distanceKm }
        list
    } catch (e: Exception) { emptyList() }
}

// ── Helper para LocationPuck (3D o 2D) ───────────────────────────────────
fun getVehiclePuck(context: Context, vehicleType: String, customPath: String, mapIconColor: Int, scale: Float = 4f): com.mapbox.maps.plugin.LocationPuck {
    val modelAsset = when (vehicleType) {
        "SEDAN"     -> "asset://models/Sedan.glb"
        "HATCHBACK" -> "asset://models/Hatchback.glb"
        "SPORT"     -> "asset://models/Sports.glb"
        // Si tienes "TAXI" en tus opciones futuras, aquí se mapeará
        "TAXI"      -> "asset://models/Taxi.glb"
        else        -> null
    }

    if (modelAsset != null) {
        return com.mapbox.maps.plugin.LocationPuck3D(
            modelUri = modelAsset,
            // Escala vinculada al Slider de la UI
            modelScale = listOf(scale, scale, scale),
            // Rotación inicial, a veces los modelos apuntan hacia +Y o +X, si maneja de lado ajustaremos modelRotation
            modelRotation = listOf(0f, 0f, 0f)
        )
    }

    // Fallback a 2D para TRUCK o CUSTOM
    val bmp = try {
        if (vehicleType == "CUSTOM" && customPath.isNotEmpty()) {
            val file = java.io.File(customPath)
            if (file.exists()) android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                .let { android.graphics.Bitmap.createScaledBitmap(it, 120, 120, true) }
            else drawVehicleBitmap(context, "SEDAN", mapIconColor)
        } else {
            drawVehicleBitmap(context, vehicleType, mapIconColor)
        }
    } catch (e: Exception) { drawVehicleBitmap(context, "SEDAN", mapIconColor) }

    return com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck(withBearing = true).apply {
        bearingImage = com.mapbox.maps.ImageHolder.from(bmp)
    }
}

// ── Helpers de dibujo (fallback 2D) ───────────────────────────────────────
fun drawVehicleBitmap(context: Context, type: String, color: Int, heightScale: Float = 1.0f): Bitmap {
    val width  = 140
    val height = (140 * heightScale).toInt()

    val drawableResId = when (type) {
        "SEDAN"     -> R.drawable.ic_sedan
        "HATCHBACK" -> R.drawable.ic_hatchback
        else        -> null
    }
    if (drawableResId != null) {
        val vd = VectorDrawableCompat.create(context.resources, drawableResId, null)
        if (vd != null) {
            vd.setTint(color)
            val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(b)
            vd.setBounds(0, 0, width, height)
            vd.draw(canvas)
            return b
        }
    }

    val b      = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(b)
    val p      = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = color
    p.setShadowLayer(8f, 0f, 4f, 0x80000000.toInt())
    when (type) {
        "SPORT" -> {
            val path = Path()
            path.moveTo(width / 2f, 10f)
            path.lineTo(20f, height.toFloat() - 10f)
            path.lineTo(width / 2f, height.toFloat() - 25f)
            path.lineTo(width.toFloat() - 20f, height.toFloat() - 10f)
            path.close()
            canvas.drawPath(path, p)
        }
        "TRUCK" -> canvas.drawRect(30f, 20f, width - 30f, height.toFloat() - 10f, p)
        else    -> {
            val path = Path()
            path.moveTo(width / 2f, 15f)
            path.lineTo(25f, height.toFloat() - 15f)
            path.lineTo(width / 2f, height.toFloat() - 30f)
            path.lineTo(width.toFloat() - 25f, height.toFloat() - 15f)
            path.close()
            canvas.drawPath(path, p)
        }
    }
    return b
}

fun drawCustomPin(color: Int): Bitmap {
    val b      = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(b)
    val p      = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = color
    // Sombra premium
    p.setShadowLayer(10f, 0f, 5f, 0x99000000.toInt())
    canvas.drawCircle(40f, 30f, 25f, p)
    val path = Path()
    path.moveTo(15f, 35f)
    path.lineTo(40f, 75f)
    path.lineTo(65f, 35f)
    path.close()
    canvas.drawPath(path, p)
    p.clearShadowLayer()
    p.color = android.graphics.Color.WHITE
    canvas.drawCircle(40f, 30f, 10f, p)
    return b
}

// (Removidos decodePolyline6 y valhallaTypeToOsrmStyle porque Mapbox Directions API retorna GeoJSON y estilos de maniobra OSRM nativos)
