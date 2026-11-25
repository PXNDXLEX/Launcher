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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
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
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.MapView
import com.tuusuario.carlauncher.services.LocationEngine
import com.mapbox.maps.Style
import com.mapbox.maps.dsl.cameraOptions
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.backgroundLayer
import com.mapbox.maps.extension.style.layers.generated.fillExtrusionLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.layers.properties.generated.Visibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.extension.style.layers.addLayerBelow
import com.mapbox.maps.extension.style.layers.addLayerAbove
import com.mapbox.maps.extension.style.layers.generated.fillLayer
import com.mapbox.maps.extension.style.layers.getLayerAs
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
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateOptions
import com.mapbox.maps.plugin.viewport.data.OverviewViewportStateOptions
import com.mapbox.maps.plugin.viewport.viewport
import com.mapbox.maps.plugin.viewport.data.DefaultViewportTransitionOptions
import com.tuusuario.carlauncher.R
import com.tuusuario.carlauncher.services.WeatherService
import com.tuusuario.carlauncher.ui.AppSettings
import com.tuusuario.carlauncher.ui.NavigationState
import com.tuusuario.carlauncher.ui.RouteStep
import androidx.compose.animation.core.LinearEasing
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import com.mapbox.maps.plugin.locationcomponent.LocationProvider
import com.mapbox.maps.plugin.locationcomponent.LocationConsumer
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorBearingChangedListener
import com.tuusuario.carlauncher.ui.LocalBentoPalette
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
        if (list.none { it.name.equals(place.name, ignoreCase = true) }) {
            list.add(0, place)
            saveList(list)
        }
    }
    fun removeFavorite(name: String) {
        val list = getFavorites().filterNot { it.name.equals(name, ignoreCase = true) }
        saveList(list)
    }
    private fun saveList(list: List<PlaceResult>) {
        val array = JSONArray()
        list.forEach {
            val obj = JSONObject()
            obj.put("name", it.name); obj.put("address", it.address); obj.put("lat", it.lat); obj.put("lon", it.lon); obj.put("iconType", it.iconType)
            array.put(obj)
        }
        prefs.edit().putString("favs", array.toString()).apply()
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
private const val CAR_HEADLIGHTS_SOURCE_ID = "car-headlights-source"
private const val CAR_HEADLIGHTS_LAYER_ID  = "car-headlights-layer"
private const val CAR_TAILLIGHTS_SOURCE_ID = "car-taillights-source"
private const val CAR_TAILLIGHTS_LAYER_ID  = "car-taillights-layer"
private const val CAR_SHADOW_LAYER_ID      = "car-contact-shadow-layer"
private const val BUILDING_FACADE_IMAGE_ID = "building-facade-windows"

private class MapInternalMotionState {
    var gpsBearing: Double = 0.0
    var lastKnownBearing: Float = 0f
    var lastIndicatorPos: com.mapbox.geojson.Point? = null
    var lastIndicatorBearing: Double = 0.0
    var lastGlowUpdateMs: Long = 0L
    var cameraEaseEndElapsedMs: Long = 0L
    var cachedHeadlightsSource: GeoJsonSource? = null
}

class MockLocationProvider : LocationProvider {
    val consumers = java.util.concurrent.CopyOnWriteArraySet<LocationConsumer>()
    private var lastLat = Double.NaN
    private var lastLon = Double.NaN
    private var lastBearing = Double.NaN

    override fun registerLocationConsumer(locationConsumer: LocationConsumer) {
        consumers.add(locationConsumer)
        if (!lastLat.isNaN() && !lastLon.isNaN()) {
            val pt = com.mapbox.geojson.Point.fromLngLat(lastLon, lastLat)
            locationConsumer.onLocationUpdated(pt) { duration = 0L }
            if (!lastBearing.isNaN()) {
                locationConsumer.onBearingUpdated(lastBearing) { duration = 0L }
            }
        }
    }
    override fun unRegisterLocationConsumer(locationConsumer: LocationConsumer) {
        consumers.remove(locationConsumer)
    }
    fun updateLocation(
        point: com.mapbox.geojson.Point,
        bearing: Double,
        animDurationMs: Long = 0L
    ) {
        val dur = animDurationMs.coerceIn(0L, 1250L)
        val posChanged = lastLat.isNaN() ||
            kotlin.math.abs(point.latitude() - lastLat) > 1e-8 ||
            kotlin.math.abs(point.longitude() - lastLon) > 1e-8
        val brgChanged = lastBearing.isNaN() ||
            kotlin.math.abs(bearing - lastBearing) > 0.02

        if (!posChanged && !brgChanged) return

        lastLat = point.latitude()
        lastLon = point.longitude()
        lastBearing = bearing

        consumers.forEach { consumer ->
            if (posChanged) {
                if (dur == 0L) {
                    consumer.onLocationUpdated(point) { duration = 0L }
                } else {
                    consumer.onLocationUpdated(point) {
                        duration = dur
                        interpolator = LinearInterpolator()
                    }
                }
            }
            if (brgChanged) {
                if (dur == 0L) {
                    consumer.onBearingUpdated(bearing) { duration = 0L }
                } else {
                    consumer.onBearingUpdated(bearing) {
                        duration = dur
                        interpolator = LinearInterpolator()
                    }
                }
            }
        }
    }
}

// ── Composable Principal ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationMap(
    modifier: Modifier = Modifier,
    isFullScreen: Boolean = false,
    isDarkMode: Boolean = true,
    isVisible: Boolean = true,
    startOverlayPadding: Dp = 0.dp,
    endOverlayPadding: Dp = 0.dp,
    bottomOverlayPadding: Dp = 0.dp
) {
    val context         = LocalContext.current
    val density         = LocalDensity.current
    val isVisibleState  = rememberUpdatedState(isVisible)
    val leftInsetPx     = with(density) { startOverlayPadding.toPx().toDouble() }
    val rightInsetPx    = with(density) { endOverlayPadding.toPx().toDouble() }
    val bottomInsetPx   = with(density) { bottomOverlayPadding.toPx().toDouble() }
    val leftInsetRef    = rememberUpdatedState(leftInsetPx)
    val rightInsetRef   = rememberUpdatedState(rightInsetPx)
    val bottomInsetRef  = rememberUpdatedState(bottomInsetPx)

    val mockLocationProvider = remember { MockLocationProvider() }
    val lifecycleOwner  = LocalLifecycleOwner.current
    val focusManager    = LocalFocusManager.current
    val vehicleType     = AppSettings.vehicleType.value
    val uiColor         = AppSettings.uiColor.value
    val mapIconColor    = AppSettings.mapIconColor.value
    val favManager      = remember { FavoritesManager(context) }
    val coroutineScope  = rememberCoroutineScope()

    // MapView con TextureView habilitado: permite transiciones Compose a 120 FPS,
    // esquinas redondeadas limpias y cero parpadeos negros al entrar/salir de Navegación.
    val mapView = remember {
        MapView(context, MapInitOptions(context = context, textureView = true))
    }

    // Estado
    val isMapDarkMode = AppSettings.isMapDarkMode.value
    val configuration = LocalConfiguration.current  // detectar rotación
    var isFollowingLocation by rememberSaveable { mutableStateOf(true) }
    var isAerialExploreMode by rememberSaveable { mutableStateOf(false) }
    var favoritesVersion by remember { mutableStateOf(0) }
    var selectedDestinationTitle by remember { mutableStateOf("") }
    // NO usar rememberSaveable aquí: al rotar queremos forzar re-centrado
    var hasInitializedPosition by remember { mutableStateOf(false) }
    // Solo se reproduce una vez por sesión (rememberSaveable evita que se reinicie al rotar)
    var hasPlayedIntro by rememberSaveable { mutableStateOf(false) }
    var isIntroAnimating by remember { mutableStateOf(false) }
    // Posición GPS lista para la intro — se llena en onLocationResult,
    // la animación se dispara cuando mapReady también es true
    var pendingIntroPos by remember { mutableStateOf<Pair<Point, Double>?>(null) }

    var showSaveFavoriteDialog by remember { mutableStateOf(false) }
    var favoriteNameToSave     by remember { mutableStateOf("") }
    var favoriteLocationToSave by remember { mutableStateOf<Point?>(null) }
    var selectedIconType       by remember { mutableStateOf("Star") }
    var favoriteAddressToSave  by remember { mutableStateOf("") }

    // NavigationState compartido
    var selectedDestination by NavigationState.activeDestination
    var routeDistanceText   by NavigationState.activeRouteDistance
    val currentRoutePoints  = NavigationState.activeRoutePoints   // List<Point> de Mapbox GeoJSON
    val activeRouteSteps    = NavigationState.activeRouteSteps

    var showArrivalAlert    by remember { mutableStateOf(false) }
    var isCalculatingRoute  by remember { mutableStateOf(false) }
    var showStyleSelector   by remember { mutableStateOf(false) }
    var showWeatherSelector by remember { mutableStateOf(false) }

    val weatherSnapshot = WeatherService.weather.value
    val selectedWeatherMode = AppSettings.mapWeatherMode.value
    // Determina el efecto climático activo (Automático por GPS o seleccionado manualmente)
    val effectiveWeatherEffect = remember(
        selectedWeatherMode,
        weatherSnapshot.currentCode,
        weatherSnapshot.windSpeedKmH
    ) {
        if (selectedWeatherMode != "AUTO") {
            selectedWeatherMode
        } else {
            val code = weatherSnapshot.currentCode
            when {
                code in listOf(95, 96, 99) -> "STORM"
                code in 51..67 || code in 80..82 -> "RAIN"
                code in 71..77 || code in listOf(85, 86) -> "SNOW"
                weatherSnapshot.windSpeedKmH >= 20f -> "WIND"
                code in listOf(2, 3, 45, 48) -> "CLOUDS"
                else -> "CLEAR"
            }
        }
    }

    // Progreso animado para la transición 3D
    val perspectiveProgress by animateFloatAsState(
        targetValue = if (NavigationState.isRouteActive.value && isFollowingLocation) 1f else 0f,
        animationSpec = tween(durationMillis = 700),
        label = "perspective_3d"
    )

    val currentStyle = AppSettings.mapStyle.value
    // Trazado de ruta en azul vibrante de alto contraste (o verde neón en modo NEON)
    val routeColorHex = when (currentStyle) {
        "NEON" -> "#00FFA6"
        else   -> "#007AFF"
    }
    val routeCasingHex = when (currentStyle) {
        "NEON" -> "#00E676"
        else   -> "#3898FF"
    }

    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx().toDouble() }
    val screenHeightPxRef = rememberUpdatedState(screenHeightPx)

    val isPortraitMode = configuration.screenHeightDp > configuration.screenWidthDp
    val isPortraitRef = rememberUpdatedState(isPortraitMode)

    val mapTrackingMode = AppSettings.mapTrackingMode.value
    val mapTrackingModeRef = rememberUpdatedState(mapTrackingMode)
    val isAerialArrowTracking = mapTrackingMode == "2D_AERIAL_ARROW"

    // Helper unificado para cámara de seguimiento (3D Cockpit en medio del carril vs Aéreo 2D con Flecha Moderna)
    fun buildChaseFollowState() = run {
        val portrait = isPortraitRef.value
        val isAerialFollow = mapTrackingModeRef.value == "2D_AERIAL_ARROW"
        val topPad = when {
            isAerialFollow && portrait -> (screenHeightPxRef.value * 0.24).coerceAtLeast(110.0)
            isAerialFollow && !portrait -> (screenHeightPxRef.value * 0.28).coerceAtLeast(100.0)
            portrait -> (screenHeightPxRef.value * 0.35).coerceAtLeast(160.0)
            else -> (screenHeightPxRef.value * 0.46).coerceAtLeast(140.0)
        }
        val botPad = when {
            isAerialFollow -> maxOf(screenHeightPxRef.value * 0.08, bottomInsetRef.value)
            portrait -> maxOf(screenHeightPxRef.value * 0.11, bottomInsetRef.value)
            else -> maxOf(screenHeightPxRef.value * 0.04, bottomInsetRef.value)
        }
        val targetPitch = if (isAerialFollow) 0.0 else if (portrait) 65.0 else 67.5
        val targetZoom = if (isAerialFollow) 17.35 else 19.75
        mapView.viewport.makeFollowPuckViewportState(
            FollowPuckViewportStateOptions.Builder()
                .pitch(targetPitch)
                .zoom(targetZoom)
                .bearing(com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateBearing.SyncWithLocationPuck)
                .padding(com.mapbox.maps.EdgeInsets(topPad, leftInsetRef.value, botPad, rightInsetRef.value))
                .build()
        )
    }

    // Estado interno de movimiento NO-reactivo (evita recomponer los 3500 renglones de Compose a 60/120 FPS)
    val motionState = remember { MapInternalMotionState() }

    // Referencias al mapa — guardadas en remember para no recrear
    var mapReady          by remember { mutableStateOf(false) }
    var lastAppliedStyle  by remember { mutableStateOf("") }
    var animator: ValueAnimator? by remember { mutableStateOf(null) }
    var autoCenterJob     by remember { mutableStateOf<Job?>(null) }
    // Posición donde se desactivó el seguimiento (para reactivar cada 1m)
    var locationWhenFollowDisabled by remember { mutableStateOf<android.location.Location?>(null) }
    // Bloquea el auto-recentrado mientras el usuario está interactuando
    var isSavingFavorite     by remember { mutableStateOf(false) }
    var isPlacingDestination by remember { mutableStateOf(false) }

    var searchQuery      by remember { mutableStateOf("") }
    var isSearchFocused  by remember { mutableStateOf(false) }
    var searchResults    by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var showFavorites    by remember { mutableStateOf(false) }
    var isSearching      by remember { mutableStateOf(false) }
    var isStyleTransitioning by remember { mutableStateOf(false) }

    // Helper directo de cámara de seguimiento (3D Cockpit o Aéreo 2D con Flecha) para sincronización VSYNC instantánea
    fun buildChaseCameraOptions(
        centerPt: Point? = motionState.lastIndicatorPos,
        bearingDeg: Double = motionState.gpsBearing
    ): CameraOptions {
        val portrait = isPortraitRef.value
        val isAerialFollow = mapTrackingModeRef.value == "2D_AERIAL_ARROW"
        val topPad = when {
            isAerialFollow && portrait -> (screenHeightPxRef.value * 0.24).coerceAtLeast(110.0)
            isAerialFollow && !portrait -> (screenHeightPxRef.value * 0.28).coerceAtLeast(100.0)
            portrait -> (screenHeightPxRef.value * 0.35).coerceAtLeast(160.0)
            else -> (screenHeightPxRef.value * 0.46).coerceAtLeast(140.0)
        }
        val botPad = when {
            isAerialFollow -> maxOf(screenHeightPxRef.value * 0.08, bottomInsetRef.value)
            portrait -> maxOf(screenHeightPxRef.value * 0.11, bottomInsetRef.value)
            else -> maxOf(screenHeightPxRef.value * 0.04, bottomInsetRef.value)
        }
        val targetPitch = if (isAerialFollow) 0.0 else if (portrait) 65.0 else 67.5
        val targetZoom = if (isAerialFollow) 17.35 else 19.75
        return cameraOptions {
            if (centerPt != null) center(centerPt)
            zoom(targetZoom)
            pitch(targetPitch)
            bearing(bearingDeg)
            padding(com.mapbox.maps.EdgeInsets(topPad, leftInsetRef.value, botPad, rightInsetRef.value))
        }
    }

    fun smoothReturnToChaseCam(durationMs: Long = 650L) {
        mapView.viewport.idle()
        val pt = motionState.lastIndicatorPos ?: NavigationState.currentLocation.value?.let {
            if (it.latitude != 0.0) Point.fromLngLat(it.longitude, it.latitude) else null
        }
        if (pt != null) {
            if (durationMs <= 0L) {
                motionState.cameraEaseEndElapsedMs = 0L
                mapView.mapboxMap.setCamera(buildChaseCameraOptions(pt, motionState.gpsBearing))
            } else {
                motionState.cameraEaseEndElapsedMs = android.os.SystemClock.elapsedRealtime() + durationMs
                mapView.camera.easeTo(
                    buildChaseCameraOptions(pt, motionState.gpsBearing),
                    MapAnimationOptions.mapAnimationOptions { duration(durationMs) }
                )
            }
        }
    }

    // Escala efectiva de las luces sincronizada con el tamaño 3D del vehículo (0.300) y el modo Aéreo 2D / 3D
    fun computeEffectiveGlowIconSize(): Double {
        if (mapTrackingModeRef.value == "2D_AERIAL_ARROW") return 0.0
        val modelScaleFactor = (AppSettings.vehicle3DScale.value / 0.042f).coerceIn(1.0f, 8.5f)
        val aerialFactor = if (isAerialExploreMode) 0.14f else 1.0f
        return (AppSettings.glowIconSize.value * modelScaleFactor * aerialFactor).toDouble()
    }

    // Actualiza la posición, rotación y tamaño de la sombra de contacto 3D (Día/Noche) y faros/luces (Noche)
    fun pushGlowFeatureToMap(force: Boolean = false) {
        if (!mapReady) return
        val nowMs = android.os.SystemClock.elapsedRealtime()
        if (!force && (nowMs - motionState.lastGlowUpdateMs < 48L)) return
        motionState.lastGlowUpdateMs = nowMs

        val pos = motionState.lastIndicatorPos ?: NavigationState.currentLocation.value?.let { loc ->
            if (loc.latitude != 0.0) com.mapbox.geojson.Point.fromLngLat(loc.longitude, loc.latitude) else null
        } ?: return

        val bearingVal = if (motionState.lastIndicatorBearing != 0.0) motionState.lastIndicatorBearing else motionState.gpsBearing
        val effectiveSize = computeEffectiveGlowIconSize()

        val feature = com.mapbox.geojson.Feature.fromGeometry(
            pos,
            com.google.gson.JsonObject().apply {
                addProperty("bearing", bearingVal)
                addProperty("iconSize", effectiveSize)
            }
        )
        val cachedSrc = motionState.cachedHeadlightsSource
        if (cachedSrc != null) {
            cachedSrc.feature(feature)
        } else {
            mapView.mapboxMap.getStyle { style ->
                val src = style.getSourceAs<com.mapbox.maps.extension.style.sources.generated.GeoJsonSource>(CAR_HEADLIGHTS_SOURCE_ID)
                motionState.cachedHeadlightsSource = src
                src?.feature(feature)
            }
        }
    }

    // Mientras se ejecuta una búsqueda de red activa mantenemos vivo el temporizador;
    // cada toque o tecla restablece los 30 segundos exactos al dejar de interactuar
    val uiBusyNow = isVisible && isSearching
    LaunchedEffect(uiBusyNow) {
        NavigationState.isNavigatingUiBusy = uiBusyNow
        if (uiBusyNow) {
            NavigationState.notifyUserInteraction()
        }
    }
    LaunchedEffect(isVisible) {
        if (!isVisible) {
            isSearchFocused = false
            focusManager.clearFocus()
        }
    }

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

    // ── Función para aplicar estilo Mapbox (con fundido suave al cambiar Día/Noche en vivo) ──
    fun applyMapStyle(style: String) {
        if (lastAppliedStyle.isNotEmpty() && lastAppliedStyle != style && isVisibleState.value) {
            isStyleTransitioning = true
        }
        val styleUri = when (style) {
            "SATELLITE" -> Style.SATELLITE_STREETS
            "DARK"      -> Style.DARK
            "NEON"      -> Style.DARK // Usamos DARK como base para asegurar que los edificios existan en el composite source
            else        -> Style.MAPBOX_STREETS
        }
        mapView.mapboxMap.loadStyle(styleUri) { loadedStyle ->
            mapReady = true
            lastAppliedStyle = style
            isStyleTransitioning = false
            // Notificar a la pantalla de bienvenida que el mapa ya cargó
            NavigationState.isMapStyleReady.value = true

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
            val headlightsGeoSrc = geoJsonSource(CAR_HEADLIGHTS_SOURCE_ID) {
                featureCollection(FeatureCollection.fromFeatures(emptyList()))
            }
            loadedStyle.addSource(headlightsGeoSrc)
            motionState.cachedHeadlightsSource = headlightsGeoSrc
            loadedStyle.addSource(geoJsonSource(CAR_TAILLIGHTS_SOURCE_ID) {
                featureCollection(FeatureCollection.fromFeatures(emptyList()))
            })

            // ── Entorno 3D y Estética (Modo Día y Modo Noche Cockpit con Carriles Anchos) ──
            val isNightMode = (style == "DARK" || style == "NEON")
            if (style != "SATELLITE") {
                try {
                    // Limpiar capas personalizadas previas si se recarga el estilo
                    listOf(
                        "cockpit-ground-base", "3d-buildings", "3d-buildings-roof", "building-glow",
                        "custom-green-areas", "custom-water", "custom-landcover",
                        "custom-roads-casing", "custom-roads", "custom-roads-highway-casing", "custom-roads-highway",
                        "custom-roads-lane-dashes", "custom-roads-lane-dashes-left", "custom-roads-lane-dashes-right"
                    ).forEach { layerId ->
                        try {
                            if (loadedStyle.styleLayerExists(layerId)) loadedStyle.removeStyleLayer(layerId)
                        } catch (_: Exception) {}
                    }

                    // Eliminar capas base de edificios y carreteras por defecto en DARK y LIGHT
                    // para que no salgan cajas grises debajo del auto ni líneas superpuestas en Modo Día.
                    if (style == "DARK" || style == "LIGHT") {
                        try {
                            val layersSnapshot = loadedStyle.styleLayers.toList()
                            layersSnapshot.forEach { layerInfo ->
                                val idLower = layerInfo.id.lowercase()
                                val isSymbol = layerInfo.type == "symbol"
                                if (!isSymbol && (
                                        idLower.contains("building") ||
                                        idLower.contains("structure") ||
                                        idLower.contains("road") ||
                                        idLower.contains("bridge") ||
                                        idLower.contains("tunnel")
                                    )
                                ) {
                                    try { loadedStyle.removeStyleLayer(layerInfo.id) } catch (_: Exception) {}
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    // 1. Mayor profundidad y contraste en Modo Día (suelo con relieve #E1E7F0, parques verdes vivos #82DA68, agua #82C4FF)
                    //    y Royal Indigo / Neon en Modo Noche:
                    val baseGroundHex = when (style) {
                        "NEON"  -> "#07061C"
                        "DARK"  -> "#1A1E52"
                        else    -> "#E1E7F0" // Suelo urbano con mayor contraste y profundidad
                    }
                    val waterHex = when (style) {
                        "NEON"  -> "#040414"
                        "DARK"  -> "#121642"
                        else    -> "#7EC0F8" // Ríos y canales azul vibrante
                    }
                    val greenColor = when (style) {
                        "NEON"  -> "#0A001E"
                        "DARK"  -> "#162A3E"
                        else    -> "#84DC6C" // Áreas verdes más vivas y naturales como en la propuesta comercial
                    }

                    // Buscar la primera etiqueta de texto/nombre de calle para dibujar suelo y calles justo debajo de los nombres
                    val topSymbolLayerId: String? = try {
                        loadedStyle.styleLayers.firstOrNull { it.type == "symbol" }?.id
                    } catch (e: Exception) { null }

                    try {
                        val bgLayer = backgroundLayer("cockpit-ground-base") {
                            backgroundColor(baseGroundHex)
                        }
                        if (topSymbolLayerId != null) loadedStyle.addLayerBelow(bgLayer, topSymbolLayerId)
                        else loadedStyle.addLayer(bgLayer)
                    } catch (_: Exception) {}

                    // 2. Parques, Tierra y Agua
                    try {
                        val greenLayer = fillLayer("custom-green-areas", "composite") {
                            sourceLayer("landuse")
                            fillColor(greenColor)
                            fillOpacity(0.95)
                        }
                        val waterLayer = fillLayer("custom-water", "composite") {
                            sourceLayer("water")
                            fillColor(waterHex)
                        }
                        val landLayer = fillLayer("custom-landcover", "composite") {
                            sourceLayer("landcover")
                            fillColor(baseGroundHex)
                        }
                        if (topSymbolLayerId != null) {
                            loadedStyle.addLayerBelow(landLayer, topSymbolLayerId)
                            loadedStyle.addLayerBelow(greenLayer, topSymbolLayerId)
                            loadedStyle.addLayerBelow(waterLayer, topSymbolLayerId)
                        } else {
                            loadedStyle.addLayer(landLayer)
                            loadedStyle.addLayer(greenLayer)
                            loadedStyle.addLayer(waterLayer)
                        }
                    } catch (_: Exception) {}

                    // 3. Calles y Avenidas de Gran Escala con relieve y guías definidas:
                    val isDayStyle = style == "LIGHT"
                    val roadColor = when (style) {
                        "NEON" -> "#0D1030"
                        "DARK" -> "#242968"
                        else   -> "#F8FAFC" // Calzada clara con contraste limpio frente al suelo y edificios 3D
                    }

                    val casingColor = when (style) {
                        "NEON" -> "#00FFFF"
                        "DARK" -> "#4D68B8"
                        else   -> "#B0BDD0" // Bordillo con relieve definido en Modo Día
                    }
                    val casingOpacity = if (style == "NEON") 0.90 else 0.96
                    val dashColor = when (style) {
                        "NEON", "DARK" -> "#E6F0FF"
                        else           -> "#94A3B8" // Líneas divisorias de carril nítidas en Modo Día
                    }

                    // 3A. Líneas exteriores / bordillos de las calles de 3 carriles (Casing ancho)
                    try {
                        val casingLayer = lineLayer("custom-roads-casing", "composite") {
                            sourceLayer("road")
                            lineColor(casingColor)
                            lineWidth(
                                Expression.interpolate(
                                    Expression.linear(), Expression.zoom(),
                                    Expression.literal(12.0), Expression.literal(5.4),
                                    Expression.literal(15.5), Expression.literal(20.0),
                                    Expression.literal(18.0), Expression.literal(66.0),
                                    Expression.literal(20.0), Expression.literal(162.0)
                                )
                            )
                            lineOpacity(casingOpacity)
                            lineCap(LineCap.ROUND)
                            lineJoin(LineJoin.ROUND)
                        }
                        if (topSymbolLayerId != null) loadedStyle.addLayerBelow(casingLayer, topSymbolLayerId)
                        else loadedStyle.addLayer(casingLayer)
                    } catch (_: Exception) {}

                    // 3B. Cuerpo de calles 3D para 3 carriles bien proporcionados
                    try {
                        val roadSurfaceLayer = lineLayer("custom-roads", "composite") {
                            sourceLayer("road")
                            lineColor(roadColor)
                            lineWidth(
                                Expression.interpolate(
                                    Expression.linear(), Expression.zoom(),
                                    Expression.literal(12.0), Expression.literal(4.0),
                                    Expression.literal(15.5), Expression.literal(16.5),
                                    Expression.literal(18.0), Expression.literal(58.0),
                                    Expression.literal(20.0), Expression.literal(148.0)
                                )
                            )
                            lineCap(LineCap.ROUND)
                            lineJoin(LineJoin.ROUND)
                        }
                        if (topSymbolLayerId != null) loadedStyle.addLayerBelow(roadSurfaceLayer, topSymbolLayerId)
                        else loadedStyle.addLayer(roadSurfaceLayer)
                    } catch (_: Exception) {}

                    // 3B-2. Avenidas Principales y Autopistas con realce azul/ámbar en Modo Día
                    if (isDayStyle) {
                        val majorHighwayFilter = Expression.match(
                            Expression.get("class"),
                            Expression.literal("motorway"), Expression.literal(true),
                            Expression.literal("trunk"), Expression.literal(true),
                            Expression.literal("primary"), Expression.literal(true),
                            Expression.literal("secondary"), Expression.literal(true),
                            Expression.literal("motorway_link"), Expression.literal(true),
                            Expression.literal("trunk_link"), Expression.literal(true),
                            Expression.literal("primary_link"), Expression.literal(true),
                            Expression.literal(false)
                        )
                        try {
                            val hwCasingLayer = lineLayer("custom-roads-highway-casing", "composite") {
                                sourceLayer("road")
                                filter(majorHighwayFilter)
                                lineColor("#2574E8")
                                lineWidth(
                                    Expression.interpolate(
                                        Expression.linear(), Expression.zoom(),
                                        Expression.literal(11.0), Expression.literal(4.8),
                                        Expression.literal(15.5), Expression.literal(22.0),
                                        Expression.literal(18.0), Expression.literal(69.0),
                                        Expression.literal(20.0), Expression.literal(165.0)
                                    )
                                )
                                lineOpacity(0.85)
                                lineCap(LineCap.ROUND)
                                lineJoin(LineJoin.ROUND)
                            }
                            val hwSurfaceLayer = lineLayer("custom-roads-highway", "composite") {
                                sourceLayer("road")
                                filter(majorHighwayFilter)
                                lineColor("#FFFFFF")
                                lineWidth(
                                    Expression.interpolate(
                                        Expression.linear(), Expression.zoom(),
                                        Expression.literal(11.0), Expression.literal(3.6),
                                        Expression.literal(15.5), Expression.literal(17.5),
                                        Expression.literal(18.0), Expression.literal(59.0),
                                        Expression.literal(20.0), Expression.literal(150.0)
                                    )
                                )
                                lineCap(LineCap.ROUND)
                                lineJoin(LineJoin.ROUND)
                            }
                            if (topSymbolLayerId != null) {
                                loadedStyle.addLayerBelow(hwCasingLayer, topSymbolLayerId)
                                loadedStyle.addLayerBelow(hwSurfaceLayer, topSymbolLayerId)
                            } else {
                                loadedStyle.addLayer(hwCasingLayer)
                                loadedStyle.addLayer(hwSurfaceLayer)
                            }
                        } catch (_: Exception) {}
                    }

                    // 3C. Dos líneas divisorias discontinuas a ±1/6 del ancho de la calzada (delimitan 3 carriles)
                    try {
                        val dashWidthExpr = Expression.interpolate(
                            Expression.linear(), Expression.zoom(),
                            Expression.literal(14.5), Expression.literal(0.9),
                            Expression.literal(17.5), Expression.literal(2.2),
                            Expression.literal(20.0), Expression.literal(4.0)
                        )
                        val leftLaneOffsetExpr = Expression.interpolate(
                            Expression.linear(), Expression.zoom(),
                            Expression.literal(14.5), Expression.literal(-1.8),
                            Expression.literal(16.5), Expression.literal(-4.8),
                            Expression.literal(18.0), Expression.literal(-9.8),
                            Expression.literal(20.0), Expression.literal(-24.5)
                        )
                        val rightLaneOffsetExpr = Expression.interpolate(
                            Expression.linear(), Expression.zoom(),
                            Expression.literal(14.5), Expression.literal(1.8),
                            Expression.literal(16.5), Expression.literal(4.8),
                            Expression.literal(18.0), Expression.literal(9.8),
                            Expression.literal(20.0), Expression.literal(24.5)
                        )

                        val leftLaneDashLayer = lineLayer("custom-roads-lane-dashes-left", "composite") {
                            sourceLayer("road")
                            minZoom(14.5)
                            lineColor(dashColor)
                            lineOpacity(if (isDayStyle) 0.85 else 0.90)
                            lineDasharray(listOf(2.4, 2.8))
                            lineWidth(dashWidthExpr)
                            lineOffset(leftLaneOffsetExpr)
                            lineCap(LineCap.BUTT)
                            lineJoin(LineJoin.ROUND)
                        }
                        val rightLaneDashLayer = lineLayer("custom-roads-lane-dashes-right", "composite") {
                            sourceLayer("road")
                            minZoom(14.5)
                            lineColor(dashColor)
                            lineOpacity(if (isDayStyle) 0.85 else 0.90)
                            lineDasharray(listOf(2.4, 2.8))
                            lineWidth(dashWidthExpr)
                            lineOffset(rightLaneOffsetExpr)
                            lineCap(LineCap.BUTT)
                            lineJoin(LineJoin.ROUND)
                        }
                        if (topSymbolLayerId != null) {
                            loadedStyle.addLayerBelow(leftLaneDashLayer, topSymbolLayerId)
                            loadedStyle.addLayerBelow(rightLaneDashLayer, topSymbolLayerId)
                        } else {
                            loadedStyle.addLayer(leftLaneDashLayer)
                            loadedStyle.addLayer(rightLaneDashLayer)
                        }
                    } catch (_: Exception) {}

                    // 4. Edificios 3D sutiles con relieve arquitectónico (en Modo Día, Noche y Neon, debajo de las calles)
                    try {
                        val buildingColorHex = when (style) {
                            "NEON" -> "#090514"
                            "DARK" -> "#151D48"
                            else   -> "#B4C1D4" // Bloques 3D translúcidos gris-azulado suave como en la imagen de propuesta
                        }
                        val buildingOpacity = when (style) {
                            "NEON" -> 0.76
                            "DARK" -> 0.64
                            else   -> 0.56
                        }
                        val heightScale = if (style == "LIGHT") 1.35 else 1.65
                        loadedStyle.addLayerBelow(fillExtrusionLayer("3d-buildings", "composite") {
                            sourceLayer("building")
                            minZoom(14.5)
                            fillExtrusionColor(buildingColorHex)
                            fillExtrusionHeight(
                                Expression.interpolate(
                                    Expression.linear(), Expression.zoom(),
                                    Expression.literal(14.5), Expression.literal(0.0),
                                    Expression.literal(15.6), Expression.product(
                                        Expression.coalesce(Expression.get("height"), Expression.literal(12.0)),
                                        Expression.literal(heightScale)
                                    )
                                )
                            )
                            fillExtrusionBase(
                                Expression.product(
                                    Expression.coalesce(Expression.get("min_height"), Expression.literal(0.0)),
                                    Expression.literal(heightScale)
                                )
                            )
                            fillExtrusionOpacity(buildingOpacity)
                        }, "custom-roads-casing")
                    } catch (_: Exception) {}

                } catch (e: Exception) {}
            }

            // ── Capas de Ruta Visuales (Trazado Azul Vibrante con Halo) ──────
            loadedStyle.addLayer(lineLayer(ROUTE_CASING_LAYER_ID, ROUTE_SOURCE_ID) {
                lineColor(routeCasingHex)
                lineWidth(
                    Expression.interpolate(
                        Expression.linear(), Expression.zoom(),
                        Expression.literal(13.0), Expression.literal(12.0),
                        Expression.literal(17.5), Expression.literal(30.0),
                        Expression.literal(20.0), Expression.literal(58.0)
                    )
                )
                lineCap(LineCap.ROUND)
                lineJoin(LineJoin.ROUND)
                lineOpacity(0.42)
            })
            loadedStyle.addLayer(lineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID) {
                lineColor(routeColorHex)
                lineWidth(
                    Expression.interpolate(
                        Expression.linear(), Expression.zoom(),
                        Expression.literal(13.0), Expression.literal(7.0),
                        Expression.literal(17.5), Expression.literal(18.0),
                        Expression.literal(20.0), Expression.literal(38.0)
                    )
                )
                lineCap(LineCap.ROUND)
                lineJoin(LineJoin.ROUND)
                lineOpacity(0.96)
            })
            loadedStyle.addLayer(lineLayer(HISTORY_LAYER_ID, HISTORY_SOURCE_ID) {
                lineColor("#4CAF50")
                lineWidth(5.0)
                lineCap(LineCap.ROUND)
                lineJoin(LineJoin.ROUND)
            })
            loadedStyle.addLayer(lineLayer(DASHCAM_LAYER_ID, DASHCAM_SOURCE_ID) {
                lineColor("#FF9800")
                lineWidth(5.0)
                lineCap(LineCap.ROUND)
                lineJoin(LineJoin.ROUND)
            })

            // ── Sombra de Contacto 3D Realista (Día y Noche) + Faros/Luces Traseras (Noche) ──
            try {
                listOf(CAR_SHADOW_LAYER_ID, CAR_HEADLIGHTS_LAYER_ID, CAR_TAILLIGHTS_LAYER_ID).forEach { id ->
                    if (loadedStyle.styleLayerExists(id)) loadedStyle.removeStyleLayer(id)
                }
                try { loadedStyle.removeStyleImage("car-contact-shadow") } catch (_: Exception) {}
                try { loadedStyle.removeStyleImage("car-headlights-glow") } catch (_: Exception) {}
                try { loadedStyle.removeStyleImage("car-taillights-glow") } catch (_: Exception) {}

                loadedStyle.addImage("car-contact-shadow", drawCarContactShadowGlow(isNightMode))
                loadedStyle.addLayer(com.mapbox.maps.extension.style.layers.generated.symbolLayer(CAR_SHADOW_LAYER_ID, CAR_HEADLIGHTS_SOURCE_ID) {
                    iconImage("car-contact-shadow")
                    iconPitchAlignment(com.mapbox.maps.extension.style.layers.properties.generated.IconPitchAlignment.MAP)
                    iconRotationAlignment(com.mapbox.maps.extension.style.layers.properties.generated.IconRotationAlignment.MAP)
                    iconRotate(Expression.get("bearing"))
                    iconAllowOverlap(true)
                    iconIgnorePlacement(true)
                    iconSize(Expression.get("iconSize"))
                })

                if (isNightMode) {
                    loadedStyle.addImage("car-headlights-glow", drawHeadlightsGlow())
                    loadedStyle.addImage("car-taillights-glow", drawTaillightsGlow())

                    loadedStyle.addLayer(com.mapbox.maps.extension.style.layers.generated.symbolLayer(CAR_HEADLIGHTS_LAYER_ID, CAR_HEADLIGHTS_SOURCE_ID) {
                        iconImage("car-headlights-glow")
                        iconPitchAlignment(com.mapbox.maps.extension.style.layers.properties.generated.IconPitchAlignment.MAP)
                        iconRotationAlignment(com.mapbox.maps.extension.style.layers.properties.generated.IconRotationAlignment.MAP)
                        iconRotate(Expression.get("bearing"))
                        iconAllowOverlap(true)
                        iconIgnorePlacement(true)
                        iconSize(Expression.get("iconSize"))
                    })

                    loadedStyle.addLayer(com.mapbox.maps.extension.style.layers.generated.symbolLayer(CAR_TAILLIGHTS_LAYER_ID, CAR_HEADLIGHTS_SOURCE_ID) {
                        iconImage("car-taillights-glow")
                        iconPitchAlignment(com.mapbox.maps.extension.style.layers.properties.generated.IconPitchAlignment.MAP)
                        iconRotationAlignment(com.mapbox.maps.extension.style.layers.properties.generated.IconRotationAlignment.MAP)
                        iconRotate(Expression.get("bearing"))
                        iconAllowOverlap(true)
                        iconIgnorePlacement(true)
                        iconSize(Expression.get("iconSize"))
                    })
                }
            } catch (e: Exception) {}

            // LocationComponent — puck del vehículo
            val locPlugin = mapView.location
            try {
                locPlugin.setLocationProvider(mockLocationProvider)
            } catch (e: Exception) {}

            locPlugin.updateSettings {
                enabled = true
                pulsingEnabled = false
                puckBearingEnabled = true
                puckBearing = PuckBearing.HEADING
                locationPuck = getVehiclePuck(
                    context,
                    AppSettings.vehicleType.value,
                    AppSettings.customVehicleIconPath.value,
                    mapIconColor,
                    AppSettings.vehicle3DScale.value,
                    isAerialMode = isAerialExploreMode,
                    trackingMode = AppSettings.mapTrackingMode.value,
                    isNightStyle = isNightMode
                )
            }

            // Forzar actualización de ubicación e iluminar las luces de inmediato (incluso a 0 km/h)
            NavigationState.currentLocation.value?.let { loc ->
                val pt = com.mapbox.geojson.Point.fromLngLat(loc.longitude, loc.latitude)
                val brg = if (loc.hasBearing()) loc.bearing.toDouble() else motionState.gpsBearing
                motionState.lastIndicatorPos = pt
                motionState.lastIndicatorBearing = brg
                mockLocationProvider.updateLocation(pt, brg, animDurationMs = 0L)
                pushGlowFeatureToMap(force = true)
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
                    locationWhenFollowDisabled = null
                    autoCenterJob?.cancel()
                    updateRouteOnMap()
                    smoothReturnToChaseCam(durationMs = 850L)

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

    // ── Sincronizar visibilidad e insets sin recrear el MapView ──────
    LaunchedEffect(isVisible, leftInsetPx, rightInsetPx, bottomInsetPx, mapReady) {
        if (!mapReady) return@LaunchedEffect
        mapView.visibility = if (isVisible) android.view.View.VISIBLE else android.view.View.INVISIBLE
        if (isVisible) {
            NavigationState.currentLocation.value?.let { loc ->
                if (loc.latitude != 0.0) {
                    val carPos = Point.fromLngLat(loc.longitude, loc.latitude)
                    val carBearing = if (loc.hasBearing()) loc.bearing.toDouble() else motionState.gpsBearing
                    motionState.lastIndicatorPos = carPos
                    motionState.lastIndicatorBearing = carBearing
                    mockLocationProvider.updateLocation(carPos, carBearing, animDurationMs = 0L)
                    hasInitializedPosition = true
                }
            }
            if (isFollowingLocation && !isIntroAnimating && !isAerialExploreMode) {
                smoothReturnToChaseCam(durationMs = 0L)
            }
        }
    }

    // ── Suscripción al motor GPS e Inercial VSYNC en tiempo real (LocationEngine) ──
    DisposableEffect(Unit) {
        var lastRouteCheckMs = 0L
        val distBuf = FloatArray(1)

        val listener = LocationEngine.Listener { loc, projectedLat, projectedLon, bearingDeg, _, animDurationMs, _ ->
            val nowMs = android.os.SystemClock.elapsedRealtime()
            // Lógica de ruta activa (evaluada ~3 veces por segundo)
            if (currentRoutePoints.isNotEmpty() && selectedDestination != null && (nowMs - lastRouteCheckMs >= 350L)) {
                lastRouteCheckMs = nowMs
                var minDistance = Float.MAX_VALUE
                var closestIndex = 0
                val searchLimit = minOf(100, currentRoutePoints.size)

                for (i in 0 until searchLimit) {
                    val pt = currentRoutePoints[i]
                    android.location.Location.distanceBetween(projectedLat, projectedLon, pt.latitude(), pt.longitude(), distBuf)
                    if (distBuf[0] < minDistance) {
                        minDistance = distBuf[0]
                        closestIndex = i
                    }
                }

                if (minDistance > 100f && !isCalculatingRoute) {
                    val destPt = NavigationState.activeDestination.value
                    if (destPt != null) {
                        calculateRoute(Point.fromLngLat(loc.longitude, loc.latitude), destPt)
                    }
                } else if (closestIndex > 0 && minDistance < 60f) {
                    for (i in 0 until closestIndex) {
                        if (currentRoutePoints.isNotEmpty()) currentRoutePoints.removeAt(0)
                    }
                    if (isVisibleState.value) updateRouteOnMap()
                }

                if (activeRouteSteps.isNotEmpty()) {
                    val nextStep = activeRouteSteps[0]
                    android.location.Location.distanceBetween(projectedLat, projectedLon, nextStep.maneuverLat, nextStep.maneuverLon, distBuf)
                    if (distBuf[0] < 20f) activeRouteSteps.removeAt(0)
                }

                if (currentRoutePoints.isNotEmpty() && currentRoutePoints.size < 10) {
                    val endPt = currentRoutePoints.last()
                    android.location.Location.distanceBetween(projectedLat, projectedLon, endPt.latitude(), endPt.longitude(), distBuf)
                    if (distBuf[0] < 30f) {
                        showArrivalAlert = true
                        NavigationState.clearActiveRoute()
                        updateRouteOnMap()
                        mapView.viewport.idle()
                    }
                }
            }

            // Si el mapa está oculto tras el menú Home, evitamos renderizado GPU innecesario
            if (!isVisibleState.value) return@Listener

            val carPt = Point.fromLngLat(projectedLon, projectedLat)
            motionState.gpsBearing = bearingDeg
            motionState.lastKnownBearing = bearingDeg.toFloat()
            motionState.lastIndicatorPos = carPt
            motionState.lastIndicatorBearing = bearingDeg

            // Actualización síncrona en el cuadro VSYNC actual (duration = 0L, sin ValueAnimator intermedio)
            mockLocationProvider.updateLocation(
                carPt,
                bearingDeg,
                animDurationMs = animDurationMs
            )
            pushGlowFeatureToMap(force = false)

            val userInteracting = isSavingFavorite || isPlacingDestination || isAerialExploreMode
            if (!isFollowingLocation && hasInitializedPosition && !userInteracting) {
                val refLoc = locationWhenFollowDisabled
                if (refLoc != null && loc.distanceTo(refLoc) >= 1f) {
                    isFollowingLocation = true
                    locationWhenFollowDisabled = null
                    autoCenterJob?.cancel()
                    smoothReturnToChaseCam(durationMs = 600L)
                }
            }

            if (!hasInitializedPosition && loc.latitude != 0.0) {
                hasInitializedPosition = true
                mapView.viewport.idle()
                motionState.cameraEaseEndElapsedMs = 0L
                mapView.mapboxMap.setCamera(buildChaseCameraOptions(carPt, bearingDeg))
            } else if (!isIntroAnimating && isFollowingLocation && !isAerialExploreMode && nowMs >= motionState.cameraEaseEndElapsedMs) {
                // Bloqueo directo de cámara 3D cuadro por cuadro junto al modelo 3D (cero desfase y cero tirones)
                mapView.mapboxMap.setCamera(buildChaseCameraOptions(carPt, bearingDeg))
            }
        }

        LocationEngine.addListener(listener)
        onDispose {
            LocationEngine.removeListener(listener)
            animator?.cancel()
        }
    }

    // ── Reaccionar al cambio de estilo ───────────────────────────────────────
    LaunchedEffect(currentStyle) {
        if (currentStyle != lastAppliedStyle) {
            applyMapStyle(currentStyle)
        }
    }

    // ── Funciones para alternar entre Vista 3D Conducción y Vista Aérea Temporal ──
    val activateAerialExplore: () -> Unit = {
        isAerialExploreMode = true
        isFollowingLocation = false
        autoCenterJob?.cancel()
        mapView.viewport.idle()
        val targetPt = selectedDestination ?: NavigationState.currentLocation.value?.let {
            Point.fromLngLat(it.longitude, it.latitude)
        }
        mapView.camera.easeTo(
            cameraOptions {
                if (targetPt != null) center(targetPt)
                zoom(16.0)
                pitch(0.0)
                bearing(0.0)
                padding(com.mapbox.maps.EdgeInsets(0.0, 0.0, 0.0, 0.0))
            },
            MapAnimationOptions.mapAnimationOptions { duration(850) }
        )
    }

    val returnTo3DChaseView: () -> Unit = {
        isAerialExploreMode = false
        isFollowingLocation = true
        isPlacingDestination = false
        locationWhenFollowDisabled = null
        autoCenterJob?.cancel()
        smoothReturnToChaseCam(durationMs = 750L)
    }

    // ── Intro cinematográfica: espera a que el mapa esté listo ───────────────
    LaunchedEffect(mapReady, pendingIntroPos) {
        val (carPos, carBearing) = pendingIntroPos ?: return@LaunchedEffect
        if (!mapReady) return@LaunchedEffect

        isFollowingLocation = false
        mapView.viewport.idle()
        isIntroAnimating = true

        try {
            mapView.mapboxMap.setCamera(
                cameraOptions {
                    center(carPos)
                    zoom(19.5)
                    pitch(65.0)
                    bearing(carBearing + 165.0)
                }
            )
            mapView.camera.easeTo(
                cameraOptions {
                    center(carPos)
                    zoom(21.5)
                    pitch(78.0)
                    bearing(carBearing + 165.0)
                },
                com.mapbox.maps.plugin.animation.MapAnimationOptions
                    .mapAnimationOptions { duration(1800) }
            )
            delay(2000)

            mapView.camera.easeTo(
                cameraOptions {
                    center(carPos)
                    zoom(20.5)
                    pitch(72.0)
                    bearing(carBearing + 270.0)
                },
                com.mapbox.maps.plugin.animation.MapAnimationOptions
                    .mapAnimationOptions { duration(4000) }
            )
            delay(4200)

            mapView.camera.flyTo(
                cameraOptions {
                    center(carPos)
                    zoom(14.0)
                    pitch(0.0)
                    bearing(0.0)
                },
                com.mapbox.maps.plugin.animation.MapAnimationOptions
                    .mapAnimationOptions { duration(3500) }
            )
            delay(3700)

            isFollowingLocation = true
            smoothReturnToChaseCam(durationMs = 1500L)
            delay(1600)
        } finally {
            isIntroAnimating = false
            isFollowingLocation = true
            pendingIntroPos = null
        }
    }

    // ── Re-centrar al cambiar orientación (portrait ↔ landscape) ────────────
    LaunchedEffect(configuration.orientation) {
        if (hasInitializedPosition && isFollowingLocation && !isAerialExploreMode) {
            smoothReturnToChaseCam(durationMs = 0L)
        }
    }

    // ── Reaccionar a rutas históricas/dashcam ────────────────────────────────
    LaunchedEffect(NavigationState.selectedHistoryRoute.value, NavigationState.selectedHistorySegment.value) {
        updateHistoryOnMap()
    }
    LaunchedEffect(NavigationState.selectedDashcamRoute.value) {
        updateDashcamOnMap()
    }

    // ── Reaccionar a solicitud externa de ruta (ej. Asistente Arlet) ─────────
    LaunchedEffect(NavigationState.pendingRouteTrigger.value) {
        val trigger = NavigationState.pendingRouteTrigger.value
        val dest = NavigationState.activeDestination.value
        val loc = NavigationState.currentLocation.value
        if (trigger > 0L && dest != null && loc != null) {
            isAerialExploreMode = false
            selectedDestination = dest
            destAnnotation?.let { pointAnnotMgr?.delete(it) }
            val pm = pointAnnotMgr ?: mapView.annotations.createPointAnnotationManager().also { pointAnnotMgr = it }
            val iconBmp = drawCustomPin(uiColor)
            destAnnotation = pm.create(PointAnnotationOptions().withPoint(dest).withIconImage(iconBmp))
            
            calculateRoute(
                Point.fromLngLat(loc.longitude, loc.latitude),
                Point.fromLngLat(dest.longitude(), dest.latitude())
            )
        }
    }

    // ── Reaccionar a cambios de tipo de vehículo, escala 3D, modo de seguimiento (3D vs Aéreo con Flecha) o tema Día/Noche ──
    LaunchedEffect(
        vehicleType,
        AppSettings.customVehicleIconPath.value,
        mapIconColor,
        AppSettings.vehicle3DScale.value,
        AppSettings.glowIconSize.value,
        isAerialExploreMode,
        mapTrackingMode,
        currentStyle
    ) {
        if (!mapReady) return@LaunchedEffect
        val isNightNow = currentStyle == "DARK" || currentStyle == "NEON"
        mapView.location.updateSettings {
            locationPuck = getVehiclePuck(
                context,
                vehicleType,
                AppSettings.customVehicleIconPath.value,
                mapIconColor,
                AppSettings.vehicle3DScale.value,
                isAerialMode = isAerialExploreMode,
                trackingMode = mapTrackingMode,
                isNightStyle = isNightNow
            )
        }
        pushGlowFeatureToMap(force = true)
        if (isFollowingLocation && !isAerialExploreMode && !isIntroAnimating) {
            smoothReturnToChaseCam(durationMs = 550L)
        }
    }

    // ── UI ───────────────────────────────────────────────────────────────────

    val showGlowTuning = NavigationState.showGlowTuning.value

    val refreshGlowOnMap: () -> Unit = {
        mapView.mapboxMap.getStyle { style ->
            try {
                val isNightNow = (currentStyle == "DARK" || currentStyle == "NEON")
                val shadowBmp = drawCarContactShadowGlow(isNightNow)
                try { style.removeStyleImage("car-contact-shadow") } catch (_: Exception) {}
                style.addImage("car-contact-shadow", shadowBmp)
                if (isNightNow) {
                    val headBmp = drawHeadlightsGlow()
                    val tailBmp = drawTaillightsGlow()
                    try { style.removeStyleImage("car-headlights-glow") } catch (_: Exception) {}
                    try { style.removeStyleImage("car-taillights-glow") } catch (_: Exception) {}
                    style.addImage("car-headlights-glow", headBmp)
                    style.addImage("car-taillights-glow", tailBmp)
                }
                mapView.location.updateSettings {
                    locationPuck = getVehiclePuck(
                        context,
                        AppSettings.vehicleType.value,
                        AppSettings.customVehicleIconPath.value,
                        AppSettings.mapIconColor.value,
                        AppSettings.vehicle3DScale.value,
                        isAerialMode = isAerialExploreMode,
                        trackingMode = AppSettings.mapTrackingMode.value,
                        isNightStyle = isNightNow
                    )
                }
                pushGlowFeatureToMap(force = true)
            } catch (e: Exception) { }
        }
    }

    val isPortrait = configuration.screenHeightDp > configuration.screenWidthDp
    val outerMapBgColor = when (currentStyle) {
        "LIGHT" -> Color(0xFFEAECEF)
        "NEON"  -> Color(0xFF07061C)
        else    -> Color(0xFF1F237A)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(outerMapBgColor)
    ) {

        val isBraking = NavigationState.isBraking.value
        LaunchedEffect(isBraking) {
            refreshGlowOnMap()
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                mapView.apply {
                    try {
                        scalebar.enabled = false
                        compass.enabled = false
                    } catch (_: Exception) {}
                    try {
                        mapView.mapboxMap.setBounds(
                            com.mapbox.maps.CameraBoundsOptions.Builder()
                                .maxPitch(85.0)
                                .minPitch(0.0)
                                .build()
                        )
                    } catch (_: Exception) {}

                    mapView.mapboxMap.setCamera(cameraOptions {
                        zoom(15.0)
                        pitch(0.0)
                        bearing(0.0)
                    })

                    applyMapStyle(currentStyle)

                    // Gestures: long-press con vuelo fluido de cámara hacia el punto marcado
                    gestures.addOnMapLongClickListener { point ->
                        val destPt = point
                        val coordsStr = "${String.format("%.5f", destPt.latitude())}, ${String.format("%.5f", destPt.longitude())}"
                        if (NavigationState.isRouteActive.value) {
                            favoriteLocationToSave = destPt
                            favoriteNameToSave     = ""
                            favoriteAddressToSave  = coordsStr
                            selectedIconType       = "Star"
                            isSavingFavorite       = true
                            showSaveFavoriteDialog = true
                            autoCenterJob?.cancel()
                            return@addOnMapLongClickListener true
                        }

                        selectedDestination      = destPt
                        selectedDestinationTitle = "Punto seleccionado"
                        favoriteLocationToSave   = destPt
                        favoriteAddressToSave    = coordsStr
                        routeDistanceText        = ""
                        isFollowingLocation      = false
                        isPlacingDestination     = true
                        autoCenterJob?.cancel()

                        destAnnotation?.let { pointAnnotMgr?.delete(it) }
                        val pm = pointAnnotMgr ?: mapView.annotations.createPointAnnotationManager().apply {
                            addClickListener { annotation ->
                                favoriteLocationToSave = annotation.point
                                favoriteNameToSave = ""
                                favoriteAddressToSave = "${String.format("%.5f", annotation.point.latitude())}, ${String.format("%.5f", annotation.point.longitude())}"
                                selectedIconType = "Star"
                                isSavingFavorite = true
                                showSaveFavoriteDialog = true
                                true
                            }
                        }.also { pointAnnotMgr = it }
                        val iconBmp = drawCustomPin(uiColor)
                        destAnnotation = pm.create(PointAnnotationOptions().withPoint(destPt).withIconImage(iconBmp))

                        // Animación cinemática suave hacia el punto marcado en el mapa
                        mapView.viewport.idle()
                        mapView.camera.easeTo(
                            cameraOptions {
                                center(destPt)
                                zoom(if (isAerialExploreMode) 16.5 else 17.5)
                                pitch(if (isAerialExploreMode) 0.0 else 48.0)
                            },
                            MapAnimationOptions.mapAnimationOptions { duration(680) }
                        )
                        true
                    }

                    gestures.addOnMapClickListener { _ ->
                        focusManager.clearFocus()
                        searchResults = emptyList()
                        showFavorites = false
                        false
                    }

                    gestures.addOnMoveListener(object : com.mapbox.maps.plugin.gestures.OnMoveListener {
                        override fun onMoveBegin(detector: com.mapbox.android.gestures.MoveGestureDetector) {
                            if (isFollowingLocation) {
                                isFollowingLocation = false
                                locationWhenFollowDisabled = NavigationState.currentLocation.value
                                mapView.viewport.idle()
                                autoCenterJob?.cancel()
                            }
                        }
                        override fun onMove(detector: com.mapbox.android.gestures.MoveGestureDetector) = false
                        override fun onMoveEnd(detector: com.mapbox.android.gestures.MoveGestureDetector) {
                            autoCenterJob?.cancel()
                            if (isAerialExploreMode) return
                            autoCenterJob = coroutineScope.launch {
                                var elapsed = 0
                                while (elapsed < 5000) {
                                    delay(200)
                                    val userInteracting = isSavingFavorite || isPlacingDestination || isAerialExploreMode
                                    if (!userInteracting) elapsed += 200
                                }
                                val userInteracting = isSavingFavorite || isPlacingDestination || isAerialExploreMode
                                if (!userInteracting) {
                                    isFollowingLocation = true
                                    locationWhenFollowDisabled = null
                                    smoothReturnToChaseCam(durationMs = 680L)
                                }
                            }
                        }
                    })

                    NavigationState.currentLocation.value?.let { loc ->
                        if (!hasInitializedPosition && loc.latitude != 0.0) {
                            mapboxMap.setCamera(cameraOptions {
                                center(Point.fromLngLat(loc.longitude, loc.latitude))
                                zoom(19.75)
                                pitch(75.0)
                                bearing(motionState.gpsBearing)
                            })
                        }
                    }
                }
            },
            update = { _ ->
                if (mapReady) {
                    val activeDest = NavigationState.activeDestination.value
                    if (activeDest != null && destAnnotation == null) {
                        val pm = pointAnnotMgr ?: mapView.annotations.createPointAnnotationManager().also { pointAnnotMgr = it }
                        val iconBmp = drawCustomPin(uiColor)
                        destAnnotation = pm.create(
                            PointAnnotationOptions()
                                .withPoint(activeDest)
                                .withIconImage(iconBmp)
                        )
                    } else if (activeDest == null && destAnnotation != null) {
                        pointAnnotMgr?.delete(destAnnotation!!)
                        destAnnotation = null
                    }
                }
            }
        )

        // ── Horizonte Cielo + Silueta de Ciudad 3D (Acotado a la franja superior del cielo en modo 3D Cockpit) ──
        val horizonAlpha by animateFloatAsState(
            targetValue = if (isFollowingLocation && !isAerialExploreMode && !isAerialArrowTracking && !isIntroAnimating && currentStyle != "SATELLITE") 1f else 0f,
            animationSpec = tween(durationMillis = 380),
            label = "horizonSkyAlpha"
        )
        if (horizonAlpha > 0.01f) {
            CockpitHorizonSkyBackdrop(
                alpha = horizonAlpha,
                currentStyle = currentStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(if (isPortrait) 0.17f else 0.22f)
                    .align(Alignment.TopCenter)
            )
        }

        // ── Capa de Animaciones Climáticas Atmosféricas (Lluvia, Viento, Tormenta, Sol/Estrellas, Nubes, Nieve) ──
        if (isVisible && effectiveWeatherEffect != "OFF") {
            CockpitWeatherOverlay(
                weatherEffect = effectiveWeatherEffect,
                isDayMode = currentStyle == "LIGHT",
                modifier = Modifier.fillMaxSize()
            )
        }

        val styleVeilAlpha by animateFloatAsState(
            targetValue = if (isStyleTransitioning) 0.55f else 0f,
            animationSpec = tween(durationMillis = 420),
            label = "mapStyleVeil"
        )
        if (styleVeilAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LocalBentoPalette.current.canvasBg.copy(alpha = styleVeilAlpha))
            )
        }

        // ── Contenedor de controles HUD sobre el Mapa (Diseño Dark Frosted Glassmorphism Unificado sin superposiciones) ──
        val hudSurfaceBg = Color(0xFF151F33).copy(alpha = 0.90f)
        val hudPanelBg = Color(0xFF101829).copy(alpha = 0.96f)
        val hudItemBg = Color(0xFF1A253D).copy(alpha = 0.92f)
        val hudTextPrimary = Color.White
        val hudTextSecondary = Color(0xFFCBD5E1)
        val hudAccent = Color(0xFFF59E0B) // Ámbar deportivo de alta gama
        val hudBorderIdle = Color.White.copy(alpha = 0.22f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = startOverlayPadding,
                    end = endOverlayPadding,
                    bottom = bottomOverlayPadding
                )
        ) {
            // ── BARRA SUPERIOR UNIFICADA (Una sola fila a la derecha del reloj digital: CERO botones encimados) ──
            val nextStep = activeRouteSteps.firstOrNull()
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(
                        top = 10.dp,
                        start = if (isPortrait) 192.dp else 218.dp,
                        end = 10.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // FILA 1: Buscador con peso flexible (weight 1f) + Botones circulares alineados a la derecha sin solaparse jamás
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    // 1A. Cápsula de Búsqueda Glassmorphic con botón integrado de Favoritos/Objetivo
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(50),
                        color = hudSurfaceBg,
                        border = BorderStroke(
                            1.2.dp,
                            if (isSearchFocused) hudAccent else hudBorderIdle
                        ),
                        shadowElevation = 10.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 12.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = hudAccent,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = if (isPortrait) "Buscar..." else "¿A dónde vamos?",
                                        color = hudTextSecondary.copy(alpha = 0.85f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                androidx.compose.foundation.text.BasicTextField(
                                    value = searchQuery,
                                    onValueChange = {
                                        searchQuery = it
                                        NavigationState.notifyUserInteraction()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { focusState ->
                                            isSearchFocused = focusState.isFocused
                                            NavigationState.notifyUserInteraction()
                                        },
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = hudTextPrimary
                                    ),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = {
                                        if (searchQuery.isNotEmpty()) {
                                            isSearching = true
                                            showFavorites = false
                                            coroutineScope.launch {
                                                searchResults = searchPlaces(
                                                    searchQuery,
                                                    NavigationState.currentLocation.value
                                                )
                                                isSearching = false
                                            }
                                        }
                                        focusManager.clearFocus()
                                    })
                                )
                            }
                            if (isSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .padding(end = 6.dp)
                                        .size(15.dp),
                                    strokeWidth = 2.dp,
                                    color = hudAccent
                                )
                            } else if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = ""; searchResults = emptyList() },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Limpiar",
                                        tint = hudTextPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            // Botón circular integrado en el extremo derecho del buscador (Favoritos / Destinos)
                            Surface(
                                onClick = {
                                    showFavorites = !showFavorites
                                    searchResults = emptyList()
                                },
                                shape = CircleShape,
                                color = if (showFavorites) hudAccent else Color(0xFF26344E),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (showFavorites) Icons.Default.Favorite else Icons.Default.GpsFixed,
                                        contentDescription = "Favoritos y Destinos",
                                        tint = if (showFavorites) Color(0xFF0F172A) else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 1B. Botón Circular: Explorar Vista Aérea 2D / Recentrar Seguimiento 3D
                    val isOffCenterOrAerial = isAerialExploreMode || !isFollowingLocation
                    Surface(
                        onClick = {
                            if (isAerialExploreMode || !isFollowingLocation) {
                                returnTo3DChaseView()
                            } else {
                                activateAerialExplore()
                            }
                        },
                        shape = CircleShape,
                        color = if (isOffCenterOrAerial) Color(0xFF007AFF) else hudSurfaceBg,
                        border = BorderStroke(1.2.dp, if (isOffCenterOrAerial) Color.White else hudBorderIdle),
                        shadowElevation = 8.dp,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when {
                                    !isFollowingLocation && !isAerialExploreMode -> Icons.Default.MyLocation
                                    isAerialExploreMode -> Icons.Default.DirectionsCar
                                    else -> Icons.Default.Explore
                                },
                                contentDescription = "Alternar Vista Aérea / Centrar",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // 1C. Botón Circular: Ajuste de Luces y Escala 3D
                    Surface(
                        onClick = { NavigationState.showGlowTuning.value = !NavigationState.showGlowTuning.value },
                        shape = CircleShape,
                        color = if (showGlowTuning) hudAccent else hudSurfaceBg,
                        border = BorderStroke(1.2.dp, hudBorderIdle),
                        shadowElevation = 8.dp,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (showGlowTuning) Icons.Default.Close else Icons.Default.Lightbulb,
                                contentDescription = "Ajuste de luces 3D",
                                tint = if (showGlowTuning) Color(0xFF0F172A) else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // 1D. Cápsula de Clima en Vivo (ej. "☁️ 28°C")
                    Box {
                        val weatherEmoji = when (effectiveWeatherEffect) {
                            "RAIN"   -> "🌧️"
                            "STORM"  -> "⛈️"
                            "WIND"   -> "💨"
                            "SNOW"   -> "❄️"
                            "CLOUDS" -> "☁️"
                            "OFF"    -> "🚫"
                            else     -> "⛅"
                        }
                        Surface(
                            onClick = { showWeatherSelector = !showWeatherSelector },
                            shape = RoundedCornerShape(50),
                            color = hudSurfaceBg,
                            border = BorderStroke(
                                1.2.dp,
                                if (selectedWeatherMode != "AUTO") hudAccent else hudBorderIdle
                            ),
                            shadowElevation = 8.dp,
                            modifier = Modifier.height(38.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(text = weatherEmoji, fontSize = 13.sp)
                                Text(
                                    text = "${weatherSnapshot.currentTempC}°C",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showWeatherSelector,
                            onDismissRequest = { showWeatherSelector = false },
                            modifier = Modifier.background(Color(0xFF101829))
                        ) {
                            DropdownMenuItem(
                                text = { Text("🛰️ Automático (GPS: ${weatherSnapshot.currentLabel})", color = Color.White) },
                                onClick = { AppSettings.setMapWeatherMode("AUTO"); showWeatherSelector = false }
                            )
                            DropdownMenuItem(
                                text = { Text("🌧️ Lluvia", color = Color.White) },
                                onClick = { AppSettings.setMapWeatherMode("RAIN"); showWeatherSelector = false }
                            )
                            DropdownMenuItem(
                                text = { Text("💨 Viento Aerodinámico", color = Color.White) },
                                onClick = { AppSettings.setMapWeatherMode("WIND"); showWeatherSelector = false }
                            )
                            DropdownMenuItem(
                                text = { Text("⛈️ Tormenta Eléctrica", color = Color.White) },
                                onClick = { AppSettings.setMapWeatherMode("STORM"); showWeatherSelector = false }
                            )
                            DropdownMenuItem(
                                text = { Text("☀️ Despejado (Sol / Estrellas)", color = Color.White) },
                                onClick = { AppSettings.setMapWeatherMode("CLEAR"); showWeatherSelector = false }
                            )
                            DropdownMenuItem(
                                text = { Text("☁️ Nubes / Bruma", color = Color.White) },
                                onClick = { AppSettings.setMapWeatherMode("CLOUDS"); showWeatherSelector = false }
                            )
                            DropdownMenuItem(
                                text = { Text("❄️ Nieve", color = Color.White) },
                                onClick = { AppSettings.setMapWeatherMode("SNOW"); showWeatherSelector = false }
                            )
                            DropdownMenuItem(
                                text = { Text("🚫 Desactivar Clima", color = Color.White) },
                                onClick = { AppSettings.setMapWeatherMode("OFF"); showWeatherSelector = false }
                            )
                        }
                    }

                    // 1E. Botón Circular: Estilo de Mapa (Día / Noche / Neon / Satélite)
                    Box {
                        Surface(
                            onClick = { showStyleSelector = !showStyleSelector },
                            shape = CircleShape,
                            color = hudSurfaceBg,
                            border = BorderStroke(1.2.dp, hudBorderIdle),
                            shadowElevation = 8.dp,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = when (currentStyle) {
                                        "SATELLITE" -> Icons.Default.Public
                                        "NEON"      -> Icons.Default.Bolt
                                        "DARK"      -> Icons.Default.DarkMode
                                        else        -> Icons.Default.LightMode
                                    },
                                    contentDescription = "Estilo Mapa",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showStyleSelector,
                            onDismissRequest = { showStyleSelector = false },
                            modifier = Modifier.background(Color(0xFF101829))
                        ) {
                            DropdownMenuItem(text = { Text("Modo Día 3D", color = Color.White) },   leadingIcon = { Icon(Icons.Default.LightMode, null, tint = Color(0xFFF59E0B)) }, onClick = { AppSettings.setMapStyle("LIGHT");     showStyleSelector = false })
                            DropdownMenuItem(text = { Text("Noche Cockpit", color = Color.White) }, leadingIcon = { Icon(Icons.Default.DarkMode, null, tint = Color(0xFF60A5FA)) },  onClick = { AppSettings.setMapStyle("DARK");      showStyleSelector = false })
                            DropdownMenuItem(text = { Text("Neon Electric", color = Color.White) }, leadingIcon = { Icon(Icons.Default.Bolt, null, tint = Color(0xFF00E5FF)) },      onClick = { AppSettings.setMapStyle("NEON");      showStyleSelector = false })
                            DropdownMenuItem(text = { Text("Satélite", color = Color.White) },      leadingIcon = { Icon(Icons.Default.Public, null, tint = Color(0xFF10B981)) },    onClick = { AppSettings.setMapStyle("SATELLITE"); showStyleSelector = false })
                        }
                    }
                }

                // FILA 2A: Banner Turn-by-Turn estilo Cockpit cuando hay una ruta activa
                AnimatedVisibility(
                    visible = NavigationState.isRouteActive.value,
                    enter = slideInVertically(
                        initialOffsetY = { -it },
                        animationSpec = spring(dampingRatio = 0.72f, stiffness = 360f)
                    ) + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    val distDisplay = when {
                        nextStep != null && nextStep.distance >= 1000 ->
                            Pair(String.format("%.1f", nextStep.distance / 1000.0), "km")
                        nextStep != null ->
                            Pair("${nextStep.distance.toInt().coerceAtLeast(10)}", "m")
                        routeDistanceText.isNotBlank() ->
                            Pair(routeDistanceText.replace(" restantes", ""), "")
                        else -> Pair("200", "m")
                    }
                    val streetDisplay = nextStep?.streetName?.ifBlank { "Continúa por la vía" }
                        ?: "Ruta activa"

                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = hudPanelBg,
                        shadowElevation = 14.dp,
                        border = BorderStroke(1.4.dp, Color(0xFF3898FF).copy(alpha = 0.65f)),
                        modifier = Modifier.widthIn(min = 210.dp, max = 330.dp)
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (nextStep != null) {
                                            getManeuverIcon(nextStep.modifier, nextStep.maneuverType)
                                        } else {
                                            Icons.Default.TurnRight
                                        },
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(34.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = distDisplay.first,
                                            fontSize = 30.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White,
                                            lineHeight = 32.sp
                                        )
                                        if (distDisplay.second.isNotEmpty()) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = distDisplay.second,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = hudTextSecondary,
                                                modifier = Modifier.padding(bottom = 3.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = streetDisplay,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.92f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = {
                                    NavigationState.clearActiveRoute()
                                    selectedDestination = null
                                    destAnnotation?.let { pointAnnotMgr?.delete(it) }
                                    destAnnotation = null
                                    updateRouteOnMap()
                                    mapView.viewport.idle()
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Cancelar ruta",
                                    tint = hudTextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }

                // FILA 2B: Panel Flotante Glassmorphic para Favoritos y Resultados de Búsqueda
                AnimatedVisibility(
                    visible = searchResults.isNotEmpty() || showFavorites,
                    enter = scaleIn(
                        initialScale = 0.92f,
                        animationSpec = spring(dampingRatio = 0.72f, stiffness = 400f)
                    ) + fadeIn(tween(180)) + expandVertically(),
                    exit = scaleOut(targetScale = 0.92f) + fadeOut(tween(140)) + shrinkVertically()
                ) {
                    val list = remember(showFavorites, searchResults, favoritesVersion) {
                        if (showFavorites) favManager.getFavorites() else searchResults
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .heightIn(max = 250.dp),
                        shape = RoundedCornerShape(22.dp),
                        color = hudPanelBg,
                        border = BorderStroke(
                            1.2.dp,
                            Brush.linearGradient(
                                listOf(
                                    hudAccent.copy(alpha = 0.55f),
                                    Color(0xFF3898FF).copy(alpha = 0.45f)
                                )
                            )
                        ),
                        shadowElevation = 16.dp
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (showFavorites) "★ TUS DESTINOS FAVORITOS" else "RESULTADOS ENCONTRADOS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp,
                                    color = hudAccent
                                )
                                if (showFavorites) {
                                    Surface(
                                        onClick = {
                                            val loc = NavigationState.currentLocation.value
                                            if (loc != null && loc.latitude != 0.0) {
                                                val pt = Point.fromLngLat(loc.longitude, loc.latitude)
                                                favoriteLocationToSave = pt
                                                favoriteNameToSave = ""
                                                favoriteAddressToSave = "${String.format("%.5f", pt.latitude())}, ${String.format("%.5f", pt.longitude())}"
                                                selectedIconType = "Star"
                                                isSavingFavorite = true
                                                showSaveFavoriteDialog = true
                                                showFavorites = false
                                            }
                                        },
                                        shape = RoundedCornerShape(50),
                                        color = hudAccent.copy(alpha = 0.18f),
                                        border = BorderStroke(1.dp, hudAccent.copy(alpha = 0.50f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.AddLocationAlt, null, tint = hudAccent, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Guardar Aquí", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                            }

                            if (list.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.FavoriteBorder,
                                            null,
                                            tint = hudTextSecondary.copy(alpha = 0.5f),
                                            modifier = Modifier.size(26.dp)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            "Mantén presionado el mapa para marcar o guardar favoritos",
                                            fontSize = 11.sp,
                                            color = hudTextSecondary
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(list) { place ->
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = hudItemBg,
                                            border = BorderStroke(1.dp, hudBorderIdle.copy(alpha = 0.35f)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable {
                                                    val destPt = Point.fromLngLat(place.lon, place.lat)
                                                    selectedDestination = destPt
                                                    selectedDestinationTitle = place.name
                                                    favoriteLocationToSave = destPt
                                                    favoriteAddressToSave = place.address
                                                    isFollowingLocation = false
                                                    isPlacingDestination = true
                                                    autoCenterJob?.cancel()

                                                    destAnnotation?.let { pointAnnotMgr?.delete(it) }
                                                    val pm = pointAnnotMgr
                                                        ?: mapView.annotations.createPointAnnotationManager()
                                                            .also { pointAnnotMgr = it }
                                                    val iconBmp = drawCustomPin(uiColor)
                                                    destAnnotation = pm.create(
                                                        PointAnnotationOptions()
                                                            .withPoint(destPt)
                                                            .withIconImage(iconBmp)
                                                    )

                                                    mapView.viewport.idle()
                                                    mapView.camera.easeTo(
                                                        cameraOptions {
                                                            center(destPt)
                                                            zoom(16.8)
                                                            pitch(40.0)
                                                        },
                                                        MapAnimationOptions.mapAnimationOptions { duration(720) }
                                                    )

                                                    searchResults = emptyList()
                                                    showFavorites = false
                                                    focusManager.clearFocus()
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .background(hudAccent.copy(alpha = 0.18f), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        when (place.iconType) {
                                                            "Home" -> Icons.Default.Home
                                                            "Work" -> Icons.Default.Work
                                                            else -> Icons.Default.Star
                                                        },
                                                        null,
                                                        tint = hudAccent,
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        place.name,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Color.White,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        fontSize = 13.sp
                                                    )
                                                    Text(
                                                        place.address.ifEmpty { "Ubicación guardada" },
                                                        color = hudTextSecondary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                                if (place.distanceKm > 0) {
                                                    Text(
                                                        text = if (place.distanceKm < 1)
                                                            "${(place.distanceKm * 1000).toInt()} m"
                                                        else
                                                            String.format("%.1f km", place.distanceKm),
                                                        fontSize = 11.sp,
                                                        color = hudAccent,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(end = 6.dp)
                                                    )
                                                }
                                                Surface(
                                                    onClick = {
                                                        val destPt = Point.fromLngLat(place.lon, place.lat)
                                                        isAerialExploreMode = false
                                                        selectedDestination = destPt
                                                        selectedDestinationTitle = place.name
                                                        destAnnotation?.let { pointAnnotMgr?.delete(it) }
                                                        val pm = pointAnnotMgr
                                                            ?: mapView.annotations.createPointAnnotationManager()
                                                                .also { pointAnnotMgr = it }
                                                        val iconBmp = drawCustomPin(uiColor)
                                                        destAnnotation = pm.create(
                                                            PointAnnotationOptions()
                                                                .withPoint(destPt)
                                                                .withIconImage(iconBmp)
                                                        )
                                                        searchResults = emptyList()
                                                        showFavorites = false
                                                        focusManager.clearFocus()
                                                        NavigationState.currentLocation.value?.let { loc ->
                                                            calculateRoute(
                                                                Point.fromLngLat(loc.longitude, loc.latitude),
                                                                destPt
                                                            )
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = Color(0xFF007AFF)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(Icons.Default.Navigation, null, tint = Color.White, modifier = Modifier.size(13.dp))
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text("IR", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White)
                                                    }
                                                }
                                                if (showFavorites) {
                                                    IconButton(
                                                        onClick = {
                                                            favManager.removeFavorite(place.name)
                                                            favoritesVersion++
                                                        },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.DeleteOutline,
                                                            contentDescription = "Eliminar favorito",
                                                            tint = hudTextSecondary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // FILA 2C: Tarjeta Glassmorphic Animada al marcar un Punto en el Mapa
                AnimatedVisibility(
                    visible = selectedDestination != null && !NavigationState.isRouteActive.value,
                    enter = scaleIn(
                        initialScale = 0.86f,
                        animationSpec = spring(dampingRatio = 0.65f, stiffness = 380f)
                    ) + fadeIn(tween(180)) + expandVertically(),
                    exit = scaleOut(targetScale = 0.88f) + fadeOut(tween(150)) + shrinkVertically()
                ) {
                    val destPt = selectedDestination
                    val liveDistanceStr = remember(destPt, NavigationState.currentLocation.value) {
                        val loc = NavigationState.currentLocation.value
                        if (destPt != null && loc != null && loc.latitude != 0.0) {
                            val res = FloatArray(1)
                            android.location.Location.distanceBetween(
                                loc.latitude, loc.longitude,
                                destPt.latitude(), destPt.longitude(),
                                res
                            )
                            if (res[0] < 1000f) "${res[0].toInt()} m de distancia"
                            else String.format("%.1f km de distancia", res[0] / 1000f)
                        } else "Destino listo para navegar"
                    }

                    val pulseInfinite = rememberInfiniteTransition(label = "pinPulse")
                    val pulseScale by pulseInfinite.animateFloat(
                        initialValue = 0.92f,
                        targetValue = 1.18f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(850),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pinPulseScale"
                    )

                    Surface(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = hudPanelBg,
                        border = BorderStroke(
                            1.4.dp,
                            Brush.linearGradient(
                                listOf(hudAccent, Color(0xFF3898FF))
                            )
                        ),
                        shadowElevation = 16.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .scale(pulseScale)
                                    .background(hudAccent.copy(alpha = 0.18f), CircleShape)
                                    .border(1.dp, hudAccent.copy(alpha = 0.65f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Place,
                                    contentDescription = null,
                                    tint = hudAccent,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedDestinationTitle.ifBlank { "Punto seleccionado" },
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = routeDistanceText.ifEmpty { liveDistanceStr },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = hudAccent,
                                    maxLines = 1
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = {
                                    destPt?.let { pt ->
                                        favoriteLocationToSave = pt
                                        favoriteNameToSave = if (selectedDestinationTitle != "Punto seleccionado" && selectedDestinationTitle != "Punto seleccionado en el mapa") selectedDestinationTitle else ""
                                        favoriteAddressToSave = "${String.format("%.5f", pt.latitude())}, ${String.format("%.5f", pt.longitude())}"
                                        selectedIconType = "Star"
                                        isSavingFavorite = true
                                        showSaveFavoriteDialog = true
                                    }
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(Color(0xFF1F2B46), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.StarBorder,
                                    contentDescription = "Guardar en Favoritos",
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            IconButton(
                                onClick = {
                                    selectedDestination = null
                                    selectedDestinationTitle = ""
                                    routeDistanceText = ""
                                    isPlacingDestination = false
                                    destAnnotation?.let { pointAnnotMgr?.delete(it) }
                                    destAnnotation = null
                                    if (!isAerialExploreMode) {
                                        returnTo3DChaseView()
                                    }
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(Color(0xFF2D1B2E), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, "Cancelar", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Button(
                                onClick = {
                                    NavigationState.currentLocation.value?.let { loc ->
                                        val dest = selectedDestination!!
                                        isAerialExploreMode = false
                                        calculateRoute(
                                            Point.fromLngLat(loc.longitude, loc.latitude),
                                            Point.fromLngLat(dest.longitude(), dest.latitude())
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                                modifier = Modifier.height(36.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Icon(Icons.Default.Navigation, null, tint = Color.White, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("IR", fontWeight = FontWeight.Black, color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // ── Panel de ajuste de luces en tiempo real ────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 60.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                GlowTuningPanel(
                    visible       = showGlowTuning,
                    onDismiss     = { NavigationState.showGlowTuning.value = false },
                    onGlowChanged = refreshGlowOnMap
                )
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

            // ── Modal Glassmorphic Moderno para Guardar Favorito (Adaptado a Modo Día y Modo Noche) ──
            AnimatedVisibility(
                visible = showSaveFavoriteDialog,
                enter = fadeIn(tween(180)) + scaleIn(
                    initialScale = 0.85f,
                    animationSpec = spring(dampingRatio = 0.68f, stiffness = 420f)
                ),
                exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.88f),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Surface(
                    modifier = Modifier
                        .widthIn(min = 320.dp, max = 420.dp)
                        .padding(16.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = hudPanelBg,
                    border = BorderStroke(
                        1.6.dp,
                        Brush.linearGradient(
                            listOf(hudAccent, Color(0xFF00E5FF), Color(0xFF5C6BC0))
                        )
                    ),
                    shadowElevation = 24.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Cabecera con icono brillante
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(
                                            Brush.linearGradient(listOf(hudAccent, Color(0xFF00B0FF))),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Star, null, tint = Color.White, modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "Nuevo Lugar Favorito",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Black,
                                        color = hudTextPrimary
                                    )
                                    favoriteLocationToSave?.let { pt ->
                                        Text(
                                            "${String.format("%.5f", pt.latitude())}, ${String.format("%.5f", pt.longitude())}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = hudAccent
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = {
                                    showSaveFavoriteDialog = false
                                    isSavingFavorite = false
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.Close, null, tint = hudTextSecondary, modifier = Modifier.size(18.dp))
                            }
                        }

                        // Selector de categoría con tarjetas interactivas animadas
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val categories = listOf(
                                Triple("Star", "Favorito", Icons.Default.Star),
                                Triple("Home", "Casa", Icons.Default.Home),
                                Triple("Work", "Trabajo", Icons.Default.Work)
                            )
                            categories.forEach { (typeKey, label, iconVec) ->
                                val isSel = selectedIconType == typeKey
                                val cardScale by animateFloatAsState(
                                    targetValue = if (isSel) 1.04f else 0.97f,
                                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 450f),
                                    label = "catCardScale_$typeKey"
                                )
                                Surface(
                                    onClick = {
                                        selectedIconType = typeKey
                                        if (favoriteNameToSave.isBlank() && typeKey != "Star") {
                                            favoriteNameToSave = label
                                        }
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSel) hudAccent.copy(alpha = 0.16f) else hudItemBg,
                                    border = BorderStroke(
                                        if (isSel) 1.5.dp else 1.dp,
                                        if (isSel) hudAccent else hudBorderIdle
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .scale(cardScale)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            iconVec,
                                            contentDescription = label,
                                            tint = if (isSel) hudAccent else hudTextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSel) hudTextPrimary else hudTextSecondary
                                        )
                                    }
                                }
                            }
                        }

                        // Etiquetas rápidas (1 toque para rellenar nombre)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("Casa", "Trabajo", "Gym", "Centro", "Súper").forEach { quickTag ->
                                Surface(
                                    onClick = { favoriteNameToSave = quickTag },
                                    shape = RoundedCornerShape(50),
                                    color = if (favoriteNameToSave == quickTag) hudAccent.copy(alpha = 0.20f) else hudItemBg,
                                    border = BorderStroke(
                                        1.dp,
                                        if (favoriteNameToSave == quickTag) hudAccent else hudBorderIdle
                                    )
                                ) {
                                    Text(
                                        text = quickTag,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = hudTextPrimary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // Campo de texto moderno
                        OutlinedTextField(
                            value = favoriteNameToSave,
                            onValueChange = {
                                favoriteNameToSave = it
                                NavigationState.notifyUserInteraction()
                            },
                            placeholder = {
                                Text("Escribe un nombre (ej. Casa, Oficina...)", color = hudTextSecondary, fontSize = 13.sp)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = hudTextPrimary,
                                unfocusedTextColor = hudTextPrimary,
                                focusedBorderColor = hudAccent,
                                unfocusedBorderColor = hudBorderIdle,
                                cursorColor = hudAccent,
                                focusedContainerColor = hudItemBg,
                                unfocusedContainerColor = hudItemBg
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Botones de acción
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    showSaveFavoriteDialog = false
                                    isSavingFavorite = false
                                }
                            ) {
                                Text("Cancelar", color = hudTextSecondary, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                enabled = favoriteNameToSave.isNotBlank(),
                                onClick = {
                                    favoriteLocationToSave?.let { pt ->
                                        favManager.addFavorite(
                                            PlaceResult(
                                                name     = favoriteNameToSave.trim(),
                                                address  = favoriteAddressToSave.ifEmpty { "Favorito guardado" },
                                                lat      = pt.latitude(),
                                                lon      = pt.longitude(),
                                                iconType = selectedIconType
                                            )
                                        )
                                        favoritesVersion++
                                        showSaveFavoriteDialog = false
                                        isSavingFavorite       = false
                                        Toast.makeText(context, "★ Guardado en favoritos", Toast.LENGTH_SHORT).show()
                                        if (!isAerialExploreMode) {
                                            returnTo3DChaseView()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF10B981),
                                    disabledContainerColor = hudItemBg
                                )
                            ) {
                                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Guardar Favorito", color = Color.White, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }

        } // Cierra Box de HUD
    }
}

/**
 * Horizonte 3D con degradado de cielo y silueta de ciudad acotado exclusivamente a la franja superior del cielo (23%),
 * adaptando sus colores a Modo Día (LIGHT) y Modo Noche (DARK / NEON) sin crear neblina sobre las calles.
 */
@Composable
private fun CockpitHorizonSkyBackdrop(
    alpha: Float,
    currentStyle: String,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val horizonY = h * 0.90f

        val isDay = currentStyle == "LIGHT"

        val skyTop = when (currentStyle) {
            "NEON"  -> Color(0xFF06081E)
            "LIGHT" -> Color(0xFF6BB5F8)
            else    -> Color(0xFF0C3B75)
        }
        val skyMid = when (currentStyle) {
            "NEON"  -> Color(0xFF141D5E)
            "LIGHT" -> Color(0xFF9DD1FF)
            else    -> Color(0xFF2672BE)
        }
        val skyHorizon = when (currentStyle) {
            "NEON"  -> Color(0xFF263494)
            "LIGHT" -> Color(0xFFD9ECFF)
            else    -> Color(0xFF4D98E8)
        }
        val groundColor = when (currentStyle) {
            "NEON"  -> Color(0xFF07061C)
            "LIGHT" -> Color(0xFFEAECEF)
            else    -> Color(0xFF232584)
        }

        // 1. Degradado del cielo hasta la línea del horizonte
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(
                    skyTop.copy(alpha = alpha),
                    skyMid.copy(alpha = alpha),
                    skyHorizon.copy(alpha = alpha)
                ),
                startY = 0f,
                endY = horizonY
            ),
            size = androidx.compose.ui.geometry.Size(w, horizonY)
        )

        // 2. Silueta arquitectónica de ciudad en el horizonte
        val backBuildingColor = if (isDay) {
            Color(0xFF72B6F2).copy(alpha = alpha * 0.55f)
        } else {
            Color(0xFF326AB8).copy(alpha = alpha * 0.58f)
        }
        val frontBuildingColor = if (isDay) {
            Color(0xFF4F98DF).copy(alpha = alpha * 0.75f)
        } else {
            Color(0xFF224B96).copy(alpha = alpha * 0.82f)
        }
        val buildingHeightsBack = floatArrayOf(
            0.24f, 0.36f, 0.20f, 0.44f, 0.30f, 0.40f, 0.18f, 0.32f, 0.46f, 0.26f,
            0.20f, 0.13f, 0.11f, 0.14f, 0.22f, 0.38f, 0.28f, 0.42f, 0.22f, 0.36f,
            0.30f, 0.44f, 0.24f, 0.34f
        )
        val countBack = buildingHeightsBack.size
        val stepW = w / countBack
        for (i in 0 until countBack) {
            val centerDist = kotlin.math.abs(i - countBack / 2f) / (countBack / 2f)
            val scale = (0.38f + 0.62f * centerDist).coerceIn(0.28f, 1f)
            val bh = horizonY * buildingHeightsBack[i] * scale
            drawRect(
                color = backBuildingColor,
                topLeft = androidx.compose.ui.geometry.Offset(i * stepW, horizonY - bh),
                size = androidx.compose.ui.geometry.Size(stepW * 0.92f, bh)
            )
            val bhFront = bh * 0.68f
            drawRect(
                color = frontBuildingColor,
                topLeft = androidx.compose.ui.geometry.Offset(i * stepW + stepW * 0.25f, horizonY - bhFront),
                size = androidx.compose.ui.geometry.Size(stepW * 0.78f, bhFront)
            )
        }

        // 3. Borde suave mínimo en la línea exacta del horizonte (sin invadir la calle)
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(
                    groundColor.copy(alpha = alpha * 0.85f),
                    Color.Transparent
                ),
                startY = horizonY - 1f,
                endY = h
            ),
            topLeft = androidx.compose.ui.geometry.Offset(0f, horizonY - 1f),
            size = androidx.compose.ui.geometry.Size(w, h - horizonY + 1f)
        )
    }
}

/**
 * Motor de Animaciones Climáticas Atmosféricas 60 FPS para Modo Día y Modo Noche (no intrusivo pero notorio):
 *  - RAIN: Gotas diagonales con estela luminosa + ondas de impacto (splashes) en los bordes del asfalto.
 *  - WIND: Cinturones aerodinámicos de viento curvos (aero ribbons) + partículas rápidas arrastradas.
 *  - STORM: Lluvia intensa inclinada + ráfagas de viento + relámpagos ramificados en el horizonte superior.
 *  - CLEAR: En Modo Día rayos solares dinámicos (god rays) y partículas cálidas; en Modo Noche cielo estrellado centelleante + estrella fugaz.
 *  - CLOUDS: Nubes volumétricas suaves desplazándose por el horizonte superior.
 *  - SNOW: Copos de nieve con caída sinusoidal y destello cristalino.
 */
@Composable
private fun CockpitWeatherOverlay(
    weatherEffect: String,
    isDayMode: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cockpitWeatherLoop")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "weatherPhase"
    )
    val slowPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "weatherSlowPhase"
    )

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val twoPi = (Math.PI * 2.0).toFloat()

        // Atenuador suave en la zona central inferior donde va el auto 3D para nunca obstruir la visión del vehículo
        fun carZoneAttenuation(x: Float, y: Float): Float {
            val dx = kotlin.math.abs(x - w * 0.5f) / (w * 0.22f)
            val dy = kotlin.math.abs(y - h * 0.70f) / (h * 0.20f)
            return if (dx < 1f && dy < 1f) 0.32f else 1f
        }

        when (weatherEffect) {
            "RAIN", "STORM" -> {
                val isStorm = weatherEffect == "STORM"
                val dropCount = if (isStorm) 68 else 48
                val slantX = if (isStorm) -w * 0.14f else -w * 0.06f
                val dropColor = if (isDayMode) {
                    Color(0xFF1565C0)
                } else {
                    Color(0xFF80E8FF)
                }
                val dropHighlight = if (isDayMode) {
                    Color.White
                } else {
                    Color(0xFFE0F7FA)
                }

                // 1. Relámpago ramificado en el horizonte superior (solo en STORM)
                if (isStorm) {
                    val flashWindow = (phase * 3f) % 1f
                    if (flashWindow < 0.08f) {
                        val flashIntensity = (1f - (flashWindow / 0.08f)).coerceIn(0f, 1f)
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFD0F0FF).copy(alpha = flashIntensity * 0.38f),
                                    Color.Transparent
                                ),
                                startY = 0f,
                                endY = h * 0.45f
                            ),
                            size = androidx.compose.ui.geometry.Size(w, h * 0.45f)
                        )
                        val boltX = w * (0.28f + (((phase * 10f).toInt() % 3) * 0.22f))
                        val boltColor = Color.White.copy(alpha = flashIntensity * 0.92f)
                        drawLine(
                            color = boltColor,
                            start = androidx.compose.ui.geometry.Offset(boltX, h * 0.02f),
                            end = androidx.compose.ui.geometry.Offset(boltX - 18f, h * 0.10f),
                            strokeWidth = 3.2f
                        )
                        drawLine(
                            color = boltColor,
                            start = androidx.compose.ui.geometry.Offset(boltX - 18f, h * 0.10f),
                            end = androidx.compose.ui.geometry.Offset(boltX + 8f, h * 0.16f),
                            strokeWidth = 2.8f
                        )
                        drawLine(
                            color = boltColor,
                            start = androidx.compose.ui.geometry.Offset(boltX + 8f, h * 0.16f),
                            end = androidx.compose.ui.geometry.Offset(boltX - 12f, h * 0.24f),
                            strokeWidth = 2.2f
                        )
                    }
                }

                // 2. Gotas de lluvia diagonales a 60 FPS
                val speedMult = if (isStorm) 3f else 2f
                for (i in 0 until dropCount) {
                    val seed = (i * 73 + 19) % 100 / 100f
                    val speedVar = 1f + (i % 3) * 0.5f
                    val progress = ((phase * speedMult * speedVar) + seed) % 1f
                    val startX = ((i * 137) % 100 / 100f) * (w * 1.15f)
                    val curX = startX + slantX * progress
                    val curY = progress * h
                    val len = (if (isStorm) 38f else 28f) * (0.75f + (i % 4) * 0.15f)
                    val dx = (slantX / h) * len
                    val atten = carZoneAttenuation(curX, curY)
                    val alpha = (if (isDayMode) 0.58f else 0.65f) * atten

                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                dropColor.copy(alpha = alpha),
                                dropHighlight.copy(alpha = alpha * 0.95f)
                            ),
                            start = androidx.compose.ui.geometry.Offset(curX - dx, curY - len),
                            end = androidx.compose.ui.geometry.Offset(curX, curY)
                        ),
                        start = androidx.compose.ui.geometry.Offset(curX - dx, curY - len),
                        end = androidx.compose.ui.geometry.Offset(curX, curY),
                        strokeWidth = if (isStorm) 2.4f else 2.0f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }

                // 3. Ondas de impacto (splashes) sobre el asfalto en la mitad inferior
                val splashCount = if (isStorm) 18 else 12
                for (i in 0 until splashCount) {
                    val seed = (i * 41 + 7) % 100 / 100f
                    val rippleProg = ((phase * 3f) + seed) % 1f
                    val sx = ((i * 89 + 13) % 100 / 100f) * w
                    val sy = h * (0.45f + ((i * 53) % 100 / 100f) * 0.48f)
                    val atten = carZoneAttenuation(sx, sy)
                    val rx = 4f + rippleProg * 22f
                    val ry = rx * 0.36f
                    val rAlpha = (1f - rippleProg) * (if (isDayMode) 0.48f else 0.55f) * atten
                    drawOval(
                        color = dropHighlight.copy(alpha = rAlpha),
                        topLeft = androidx.compose.ui.geometry.Offset(sx - rx, sy - ry),
                        size = androidx.compose.ui.geometry.Size(rx * 2f, ry * 2f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
                    )
                }
            }

            "WIND" -> {
                val windColor = if (isDayMode) Color(0xFF0277BD) else Color(0xFF80F8FF)
                val windHighlight = if (isDayMode) Color.White else Color(0xFFB2EBF2)

                // 1. Cinturones aerodinámicos de viento (ribbons ondulados que cruzan la pantalla)
                for (i in 0 until 18) {
                    val seed = (i * 59 + 11) % 100 / 100f
                    val speed = if (i % 2 == 0) 2f else 1.5f
                    val prog = ((phase * speed) + seed) % 1f
                    val ribbonLen = w * (0.18f + (i % 4) * 0.05f)
                    val headX = -ribbonLen + prog * (w + ribbonLen * 2f)
                    val baseY = h * (0.06f + ((i * 47) % 100 / 100f) * 0.80f)
                    val atten = carZoneAttenuation(headX, baseY)
                    val alpha = (if (isDayMode) 0.52f else 0.58f) * atten

                    val segments = 8
                    val segW = ribbonLen / segments
                    for (s in 0 until segments) {
                        val x0 = headX - s * segW
                        val x1 = headX - (s + 1) * segW
                        val wave0 = sin((x0 / w) * twoPi * 2.2f + i) * 7f
                        val wave1 = sin((x1 / w) * twoPi * 2.2f + i) * 7f
                        val segAlpha = alpha * (1f - s.toFloat() / segments)
                        drawLine(
                            color = if (s < 2) windHighlight.copy(alpha = segAlpha) else windColor.copy(alpha = segAlpha),
                            start = androidx.compose.ui.geometry.Offset(x0, baseY + wave0),
                            end = androidx.compose.ui.geometry.Offset(x1, baseY + wave1),
                            strokeWidth = 2.4f * (1f - s.toFloat() / (segments * 1.3f)),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    }
                }

                // 2. Partículas aerodinámicas rápidas arrastradas por el viento
                for (i in 0 until 24) {
                    val seed = (i * 37 + 5) % 100 / 100f
                    val prog = ((phase * 3f) + seed) % 1f
                    val px = prog * w
                    val py = h * (0.08f + ((i * 67) % 100 / 100f) * 0.78f) + sin(prog * twoPi * 2f + i) * 10f
                    val atten = carZoneAttenuation(px, py)
                    drawCircle(
                        color = windHighlight.copy(alpha = 0.65f * atten),
                        radius = 2.2f + (i % 3) * 0.8f,
                        center = androidx.compose.ui.geometry.Offset(px, py)
                    )
                }
            }

            "SNOW" -> {
                val snowColor = if (isDayMode) Color(0xFF0288D1) else Color.White
                for (i in 0 until 46) {
                    val seed = (i * 61 + 17) % 100 / 100f
                    val prog = (phase + seed) % 1f
                    val sway = sin((prog * twoPi * 1.5f) + i) * 22f
                    val sx = (((i * 113) % 100 / 100f) * w + sway + w) % w
                    val sy = prog * h
                    val atten = carZoneAttenuation(sx, sy)
                    val r = 2.5f + (i % 4) * 1.2f
                    val alpha = (if (isDayMode) 0.68f else 0.80f) * atten

                    drawCircle(
                        color = snowColor.copy(alpha = alpha * 0.35f),
                        radius = r * 1.9f,
                        center = androidx.compose.ui.geometry.Offset(sx, sy)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = alpha),
                        radius = r,
                        center = androidx.compose.ui.geometry.Offset(sx, sy)
                    )
                }
            }

            "CLOUDS" -> {
                // Nubes volumétricas suaves desplazándose por la franja superior del horizonte
                val cloudColor = if (isDayMode) Color.White else Color(0xFF90CAF9)
                for (i in 0 until 7) {
                    val seed = i / 7f
                    val prog = (slowPhase + seed) % 1f
                    val cx = -w * 0.2f + prog * (w * 1.4f)
                    val cy = h * (0.05f + (i % 3) * 0.055f)
                    val cw = w * (0.22f + (i % 3) * 0.06f)
                    val ch = cw * 0.28f
                    val cAlpha = if (isDayMode) 0.34f else 0.22f

                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(cloudColor.copy(alpha = cAlpha), Color.Transparent),
                            center = androidx.compose.ui.geometry.Offset(cx, cy),
                            radius = cw * 0.6f
                        ),
                        topLeft = androidx.compose.ui.geometry.Offset(cx - cw * 0.5f, cy - ch * 0.5f),
                        size = androidx.compose.ui.geometry.Size(cw, ch)
                    )
                }
            }

            else -> {
                // CLEAR: En Modo Día -> Sol + Rayos Solares Dinámicos; En Modo Noche -> Estrellas Centelleantes + Estrella Fugaz
                if (isDayMode) {
                    val sunX = w * 0.72f
                    val sunY = h * 0.08f
                    val pulse = 0.90f + 0.10f * sin(phase * twoPi)

                    // Halo solar cálido
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFF59D).copy(alpha = 0.52f * pulse),
                                Color(0xFFFFD54F).copy(alpha = 0.20f * pulse),
                                Color.Transparent
                            ),
                            center = androidx.compose.ui.geometry.Offset(sunX, sunY),
                            radius = w * 0.22f
                        ),
                        radius = w * 0.22f,
                        center = androidx.compose.ui.geometry.Offset(sunX, sunY)
                    )

                    // Haces de luz solar diagonales suaves (Sunbeams)
                    for (b in 0 until 5) {
                        val angleOffset = sin(phase * twoPi + b) * 0.03f
                        val beamEndX = sunX - w * (0.12f + b * 0.11f + angleOffset)
                        val beamEndY = h * 0.42f
                        drawLine(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFFFF9C4).copy(alpha = 0.22f),
                                    Color.Transparent
                                ),
                                start = androidx.compose.ui.geometry.Offset(sunX, sunY),
                                end = androidx.compose.ui.geometry.Offset(beamEndX, beamEndY)
                            ),
                            start = androidx.compose.ui.geometry.Offset(sunX, sunY),
                            end = androidx.compose.ui.geometry.Offset(beamEndX, beamEndY),
                            strokeWidth = 28f + b * 8f,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    }
                } else {
                    // Modo Noche: Estrellas centelleantes en la franja superior del cielo + Estrella fugaz
                    for (i in 0 until 36) {
                        val sx = ((i * 97 + 13) % 100 / 100f) * w
                        val sy = ((i * 53 + 7) % 100 / 100f) * (h * 0.20f)
                        val twinkle = (0.35f + 0.65f * ((sin(phase * twoPi + i * 1.7f) + 1f) * 0.5f))
                        val starR = if (i % 5 == 0) 2.3f else 1.4f
                        drawCircle(
                            color = Color.White.copy(alpha = twinkle * 0.85f),
                            radius = starR,
                            center = androidx.compose.ui.geometry.Offset(sx, sy)
                        )
                    }

                    // Estrella fugaz sutil cada ciclo en el cielo nocturno
                    if (phase < 0.22f) {
                        val shootProg = phase / 0.22f
                        val startX = w * 0.78f
                        val startY = h * 0.03f
                        val curX = startX - shootProg * (w * 0.32f)
                        val curY = startY + shootProg * (h * 0.13f)
                        drawLine(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xFF80E8FF).copy(alpha = (1f - shootProg) * 0.85f),
                                    Color.White.copy(alpha = 1f - shootProg)
                                ),
                                start = androidx.compose.ui.geometry.Offset(curX + 45f, curY - 16f),
                                end = androidx.compose.ui.geometry.Offset(curX, curY)
                            ),
                            start = androidx.compose.ui.geometry.Offset(curX + 45f, curY - 16f),
                            end = androidx.compose.ui.geometry.Offset(curX, curY),
                            strokeWidth = 2.4f,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    }
                }
            }
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

// ── Helper para LocationPuck (3D protagonista en Chase Cam, compacto en Exploración Aérea y Flecha Moderna en Seguimiento Aéreo) ──
fun getVehiclePuck(
    context: Context,
    vehicleType: String,
    customPath: String,
    mapIconColor: Int,
    scale: Float = 0.300f,
    isAerialMode: Boolean = false,
    trackingMode: String = AppSettings.mapTrackingMode.value,
    isNightStyle: Boolean = (AppSettings.mapStyle.value == "DARK" || AppSettings.mapStyle.value == "NEON")
): com.mapbox.maps.plugin.LocationPuck {
    // Si el modo definitivo seleccionado en Ajustes es "2D_AERIAL_ARROW", usamos siempre la Flecha de Navegación Moderna
    // sin cargar el modelo 3D ni el icono personalizado de auto, adaptada a Modo Día y Modo Noche.
    if (trackingMode == "2D_AERIAL_ARROW" || vehicleType == "FLECHA") {
        val arrowBmp = drawModernNavigationArrowBitmap(
            isNightStyle = isNightStyle,
            isExploreZoom = isAerialMode
        )
        return com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck(withBearing = true).apply {
            bearingImage = com.mapbox.maps.ImageHolder.from(arrowBmp)
            topImage = com.mapbox.maps.ImageHolder.from(
                Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
            )
            shadowImage = com.mapbox.maps.ImageHolder.from(
                Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
            )
        }
    }

    val modelAsset = when (vehicleType) {
        "SEDAN"     -> "asset://models/Sedan.glb"
        "HATCHBACK" -> "asset://models/Hatchback.glb"
        "SPORT"     -> "asset://models/Sports.glb"
        "TAXI"      -> "asset://models/Taxi.glb"
        "STYLUS"    -> "asset://models/Stylus.glb"
        "CORSA"     -> "asset://models/Corsa.glb"
        else        -> null
    }

    if (modelAsset != null) {
        val baseScale = if (vehicleType == "STYLUS" || vehicleType == "CORSA") scale * 500f else scale
        // En vista 3D de conducción mantiene el tamaño protagonista (0.300 * 1.95f);
        // en Vista Aérea 2D se reduce a 0.24f (~8 veces más pequeño) para no tapar el mapa al hacer zoom.
        val modeMultiplier = if (isAerialMode) 0.24f else 1.95f
        val finalScale = baseScale * modeMultiplier

        return com.mapbox.maps.plugin.LocationPuck3D(
            modelUri = modelAsset,
            modelScale = listOf(finalScale, finalScale, finalScale),
            modelRotation = listOf(0f, 0f, 180f)
        )
    }

    val bmpSize = if (isAerialMode) 76 else 150
    val bmp = try {
        if (vehicleType == "CUSTOM" && customPath.isNotEmpty()) {
            val file = java.io.File(customPath)
            if (file.exists()) android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                .let { android.graphics.Bitmap.createScaledBitmap(it, bmpSize, bmpSize, true) }
            else drawVehicleBitmap(context, "SEDAN", mapIconColor)
        } else {
            drawVehicleBitmap(context, vehicleType, mapIconColor)
        }
    } catch (e: Exception) { drawVehicleBitmap(context, "SEDAN", mapIconColor) }

    return com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck(withBearing = true).apply {
        bearingImage = com.mapbox.maps.ImageHolder.from(bmp)
    }
}

/**
 * Dibuja la Flecha de Navegación Moderna para el modo de Seguimiento Aéreo (`2D_AERIAL_ARROW`):
 *  - Combina con Modo Día (azul cobalto / cian zafiro con disco cristalino blanco y sombra suave)
 *    y Modo Noche (turquesa esmeralda / cian neón con núcleo oscuro y halo luminoso).
 *  - Geometría tipo chevron aerodinámico de doble faceta (izquierda iluminada, derecha sombreada)
 *    sin utilizar ningún icono de auto personalizado.
 */
fun drawModernNavigationArrowBitmap(
    isNightStyle: Boolean,
    isExploreZoom: Boolean = false
): Bitmap {
    val size = if (isExploreZoom) 118 else 148
    val cx = size / 2f
    val cy = size / 2f
    val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(b)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)

    // 1. Halo exterior difuminado
    val haloRadius = size * 0.45f
    val haloCore = if (isNightStyle) {
        android.graphics.Color.argb(115, 0, 238, 190)
    } else {
        android.graphics.Color.argb(95, 26, 107, 255)
    }
    p.style = Paint.Style.FILL
    p.shader = RadialGradient(
        cx, cy, haloRadius,
        intArrayOf(haloCore, android.graphics.Color.TRANSPARENT),
        floatArrayOf(0.25f, 1f),
        Shader.TileMode.CLAMP
    )
    canvas.drawCircle(cx, cy, haloRadius, p)
    p.shader = null

    // 2. Disco base estilo cápsula radar con borde luminoso
    val discRadius = size * 0.31f
    p.setShadowLayer(size * 0.06f, 0f, size * 0.025f, 0x88000000.toInt())
    p.color = if (isNightStyle) {
        android.graphics.Color.parseColor("#0D1536")
    } else {
        android.graphics.Color.parseColor("#FFFFFF")
    }
    canvas.drawCircle(cx, cy, discRadius, p)
    p.clearShadowLayer()

    // Anillo perimetral del disco
    p.style = Paint.Style.STROKE
    p.strokeWidth = size * 0.028f
    p.color = if (isNightStyle) {
        android.graphics.Color.parseColor("#38F2A6")
    } else {
        android.graphics.Color.parseColor("#2563EB")
    }
    canvas.drawCircle(cx, cy, discRadius, p)
    p.style = Paint.Style.FILL

    // 3. Flecha chevron de doble faceta 3D apuntando al frente (Norte del bitmap)
    val tipY = cy - size * 0.23f
    val wingY = cy + size * 0.16f
    val notchY = cy + size * 0.06f
    val wingSpread = size * 0.175f

    // Faceta izquierda (más brillante)
    val leftFacet = Path().apply {
        moveTo(cx, tipY)
        lineTo(cx - wingSpread, wingY)
        lineTo(cx, notchY)
        close()
    }
    p.shader = LinearGradient(
        cx, tipY, cx - wingSpread, wingY,
        if (isNightStyle) android.graphics.Color.parseColor("#5CFFD0") else android.graphics.Color.parseColor("#3B82F6"),
        if (isNightStyle) android.graphics.Color.parseColor("#00D4FF") else android.graphics.Color.parseColor("#1D4ED8"),
        Shader.TileMode.CLAMP
    )
    canvas.drawPath(leftFacet, p)

    // Faceta derecha (contraste de profundidad)
    val rightFacet = Path().apply {
        moveTo(cx, tipY)
        lineTo(cx + wingSpread, wingY)
        lineTo(cx, notchY)
        close()
    }
    p.shader = LinearGradient(
        cx, tipY, cx + wingSpread, wingY,
        if (isNightStyle) android.graphics.Color.parseColor("#1EE3A8") else android.graphics.Color.parseColor("#1D4ED8"),
        if (isNightStyle) android.graphics.Color.parseColor("#008CFF") else android.graphics.Color.parseColor("#1E3A8A"),
        Shader.TileMode.CLAMP
    )
    canvas.drawPath(rightFacet, p)
    p.shader = null

    // Contorno nítido de la flecha
    val fullArrow = Path().apply {
        moveTo(cx, tipY)
        lineTo(cx + wingSpread, wingY)
        lineTo(cx, notchY)
        lineTo(cx - wingSpread, wingY)
        close()
    }
    p.style = Paint.Style.STROKE
    p.strokeWidth = size * 0.018f
    p.strokeJoin = Paint.Join.ROUND
    p.color = if (isNightStyle) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#EFF6FF")
    canvas.drawPath(fullArrow, p)

    return b
}

// ── Helpers de dibujo (fallback 2D) ───────────────────────────────────────
fun drawVehicleBitmap(context: Context, type: String, color: Int, heightScale: Float = 1.0f): Bitmap {
    val width  = 160
    val height = (160 * heightScale).toInt()

    val drawableResId = when (type) {
        "SEDAN", "STYLUS"    -> R.drawable.ic_sedan
        "HATCHBACK", "CORSA" -> R.drawable.ic_hatchback
        else                 -> null
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
    val size = 112
    val cx = size / 2f
    val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(b)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)

    // Halo de radar neón en la punta inferior del pin
    p.style = Paint.Style.FILL
    p.shader = RadialGradient(
        cx, 92f, 22f,
        android.graphics.Color.argb(150, 56, 242, 166),
        android.graphics.Color.TRANSPARENT,
        Shader.TileMode.CLAMP
    )
    canvas.drawCircle(cx, 92f, 22f, p)
    p.shader = null

    // Cuerpo del pin con brillo y sombra profunda
    p.color = android.graphics.Color.parseColor("#00E676")
    p.setShadowLayer(12f, 0f, 5f, 0xAA000000.toInt())
    canvas.drawCircle(cx, 38f, 26f, p)
    val path = Path()
    path.moveTo(cx - 23f, 46f)
    path.lineTo(cx, 92f)
    path.lineTo(cx + 23f, 46f)
    path.close()
    canvas.drawPath(path, p)
    p.clearShadowLayer()

    // Núcleo oscuro + centro blanco brillante
    p.color = android.graphics.Color.parseColor("#0B1338")
    canvas.drawCircle(cx, 38f, 14f, p)
    p.color = android.graphics.Color.WHITE
    canvas.drawCircle(cx, 38f, 6.5f, p)
    return b
}

// (Removidos decodePolyline6 y valhallaTypeToOsrmStyle porque Mapbox Directions API retorna GeoJSON y estilos de maniobra OSRM nativos)

fun drawHeadlightsGlow(
    carHalfW : Float = AppSettings.glowCarHalfW.value,
    headY    : Float = AppSettings.glowHeadY.value,
    headReach: Float = AppSettings.glowHeadReach.value,
    headSpread:Float = AppSettings.glowHeadSpread.value
): Bitmap {
    val w = 512
    val h = 512
    val cx = w / 2f
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val beamCoreColor = android.graphics.Color.argb(235, 255, 253, 225)
    val beamMidColor  = android.graphics.Color.argb(120, 200, 235, 255)
    val transparent   = android.graphics.Color.TRANSPARENT

    // === CONOS DE FAROS DELANTEROS ===
    val leftPath = Path()
    leftPath.moveTo(cx - carHalfW - 3f, headY)
    leftPath.lineTo(cx - carHalfW - headSpread, headReach)
    leftPath.lineTo(cx - 2f, headReach)
    leftPath.close()
    paint.shader = LinearGradient(
        cx - carHalfW, headY,
        cx - carHalfW, headReach,
        intArrayOf(beamCoreColor, beamMidColor, transparent),
        floatArrayOf(0f, 0.45f, 1f),
        Shader.TileMode.CLAMP
    )
    canvas.drawPath(leftPath, paint)

    val rightPath = Path()
    rightPath.moveTo(cx + carHalfW + 3f, headY)
    rightPath.lineTo(cx + carHalfW + headSpread, headReach)
    rightPath.lineTo(cx + 2f, headReach)
    rightPath.close()
    paint.shader = LinearGradient(
        cx + carHalfW, headY,
        cx + carHalfW, headReach,
        intArrayOf(beamCoreColor, beamMidColor, transparent),
        floatArrayOf(0f, 0.45f, 1f),
        Shader.TileMode.CLAMP
    )
    canvas.drawPath(rightPath, paint)

    // === FOCOS INTENSOS EN EL PARACHOQUES DELANTERO ===
    val bulbColor = android.graphics.Color.argb(245, 255, 255, 240)
    paint.shader = RadialGradient(cx - carHalfW, headY, 16f, bulbColor, transparent, Shader.TileMode.CLAMP)
    canvas.drawCircle(cx - carHalfW, headY, 16f, paint)
    paint.shader = RadialGradient(cx + carHalfW, headY, 16f, bulbColor, transparent, Shader.TileMode.CLAMP)
    canvas.drawCircle(cx + carHalfW, headY, 16f, paint)

    // === REFLEJO AMBIENTAL DELANTERO EN EL ASFALTO ===
    val reflColor = android.graphics.Color.argb(95, 255, 240, 195)
    paint.shader = RadialGradient(cx, (headY + headReach) * 0.5f, 68f, reflColor, transparent, Shader.TileMode.CLAMP)
    canvas.drawCircle(cx, (headY + headReach) * 0.5f, 68f, paint)

    return bitmap
}

/**
 * Sombra de contacto proyectada bajo el chasis y neumáticos del modelo 3D (con reflejo ambiental suave)
 * para que el auto tenga volumen y presencia realista sobre el asfalto en Modo Día y Modo Noche.
 */
fun drawCarContactShadowGlow(
    isNight: Boolean,
    carHalfW: Float = AppSettings.glowCarHalfW.value,
    headY: Float = AppSettings.glowHeadY.value,
    tailY: Float = AppSettings.glowTailY.value
): Bitmap {
    val w = 512
    val h = 512
    val cx = w / 2f
    val cy = (headY + tailY) * 0.5f + 4f
    val rx = (carHalfW * 1.38f).coerceIn(22f, 64f)
    val ry = ((tailY - headY) * 0.85f).coerceIn(26f, 76f)

    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Sombra suave desplazada ligeramente hacia abajo/izquierda (perspectiva solar en 3ra persona)
    val shadowCx = cx - rx * 0.24f
    val shadowCy = cy + ry * 0.14f
    val coreAlpha = if (isNight) 185 else 155
    val midAlpha = if (isNight) 95 else 75

    canvas.save()
    canvas.scale(1f, ry / rx, shadowCx, shadowCy)
    paint.shader = RadialGradient(
        shadowCx, shadowCy, rx * 1.45f,
        intArrayOf(
            android.graphics.Color.argb(coreAlpha, 10, 16, 28),
            android.graphics.Color.argb(midAlpha, 15, 23, 42),
            android.graphics.Color.TRANSPARENT
        ),
        floatArrayOf(0f, 0.58f, 1f),
        Shader.TileMode.CLAMP
    )
    canvas.drawCircle(shadowCx, shadowCy, rx * 1.45f, paint)
    canvas.restore()

    // Reflejo cálido sutil de la carrocería amarilla sobre el borde del asfalto
    paint.shader = RadialGradient(
        cx, cy + ry * 0.35f, rx * 1.15f,
        intArrayOf(
            android.graphics.Color.argb(if (isNight) 38 else 46, 255, 208, 40),
            android.graphics.Color.TRANSPARENT
        ),
        floatArrayOf(0f, 1f),
        Shader.TileMode.CLAMP
    )
    canvas.drawCircle(cx, cy + ry * 0.35f, rx * 1.15f, paint)

    return bitmap
}

fun drawTaillightsGlow(
    carHalfW : Float = AppSettings.glowCarHalfW.value,
    tailY    : Float = AppSettings.glowTailY.value,
    tailRadius:Float = AppSettings.glowTailRadius.value,
    isBraking: Boolean = NavigationState.isBraking.value
): Bitmap {
    val w = 512
    val h = 512
    val cx = w / 2f
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val transparent = android.graphics.Color.TRANSPARENT

    // Intensidad alta y visible en modo noche normal (215 alfa) y máxima al frenar (255 alfa)
    val alpha = if (isBraking) 255 else 215
    val effRadius = (if (isBraking) tailRadius * 1.6f else tailRadius * 1.15f).coerceAtLeast(16f)
    val tailCoreColor = android.graphics.Color.argb(alpha, 255, 30, 50)
    val tailMidColor  = android.graphics.Color.argb((alpha * 0.6f).toInt(), 255, 10, 25)

    // === LUCES TRASERAS IZQUIERDA Y DERECHA ===
    paint.shader = RadialGradient(
        cx - carHalfW, tailY, effRadius,
        intArrayOf(tailCoreColor, tailMidColor, transparent),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP
    )
    canvas.drawCircle(cx - carHalfW, tailY, effRadius, paint)

    paint.shader = RadialGradient(
        cx + carHalfW, tailY, effRadius,
        intArrayOf(tailCoreColor, tailMidColor, transparent),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP
    )
    canvas.drawCircle(cx + carHalfW, tailY, effRadius, paint)

    // === REFLEJO ROJO EN EL ASFALTO TRASERO ===
    val rearAmbColor = android.graphics.Color.argb(if (isBraking) 130 else 85, 255, 20, 40)
    paint.shader = RadialGradient(cx, tailY + effRadius * 0.35f, effRadius * 1.45f, rearAmbColor, transparent, Shader.TileMode.CLAMP)
    canvas.drawCircle(cx, tailY + effRadius * 0.35f, effRadius * 1.45f, paint)

    return bitmap
}

// ── Textura est\u00e1tica de fachada de edificio (modo DARK) ─────────────────────
//
// Genera un Bitmap 256\u00d7512 que se aplica como `fillExtrusionPattern` en los
// edificios 3D del modo noche. Ventanas blancas tenues sobre fondo oscuro.
//
// Dise\u00f1o deliberadamente minimalista:
//   - Seed FIJA (42) \u2192 el patr\u00f3n nunca cambia entre renders
//   - Pocas ventanas (4 col \u00d7 8 filas) \u2192 no se ve como papel de regalos
//   - Color \u00fanico: blanco muy suave \u2192 realista, sin colores llamativos
//   - ~45% de ventanas iluminadas \u2192 ciudad tranquila de noche
fun drawBuildingFacadeTexture(isNeon: Boolean): Bitmap {
    val w   = 256
    val h   = 256
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val cv  = Canvas(bmp)
    val rng = java.util.Random(42L)

    // Fondo del edificio: Azul oscuro tipo noche
    val bgHex = if (isNeon) "#0f0f18" else "#141724"
    val bgColor = android.graphics.Color.parseColor(bgHex)
    cv.drawColor(bgColor)

    // Grid de ventanas espaciadas (3 columnas x 3 filas) para evitar saturaciÃ³n
    val cols      = 3
    val rows      = 3
    val winW      = 40f
    val winH      = 30f
    val glowH     = 16f
    val hPad      = (w - cols * winW) / (cols + 1)
    val vPad      = (h - rows * (winH + glowH)) / (rows + 1)
    val litChance = 0.40f

    // Colores para ventanas y sus cruces/marcos
    val litColor = if (isNeon) {
        android.graphics.Color.argb(255, 0, 229, 255) // Cian neÃ³n
    } else {
        android.graphics.Color.argb(255, 255, 213, 79) // Amarillo cÃ¡lido
    }
    val darkColor = android.graphics.Color.parseColor("#090b11")
    val litCrossColor = bgColor // La cruz de la ventana encendida usa el color del edificio
    val darkCrossColor = android.graphics.Color.parseColor("#1b2030") // Cruz visible en ventanas apagadas

    // Brillo proyectado hacia abajo (glow)
    val glowColorStart = if (isNeon) {
        android.graphics.Color.argb(130, 0, 229, 255)
    } else {
        android.graphics.Color.argb(130, 255, 213, 79)
    }
    val glowColorEnd = android.graphics.Color.argb(0, 0, 0, 0)

    val p = Paint(Paint.ANTI_ALIAS_FLAG)

    for (row in 0 until rows) {
        val y = vPad + row * (winH + glowH + vPad)
        for (col in 0 until cols) {
            val x    = hPad + col * (winW + hPad)
            val rect = android.graphics.RectF(x, y, x + winW, y + winH)
            
            val isLit = rng.nextFloat() < litChance

            // 1. Dibujar el fondo de la ventana
            p.style = Paint.Style.FILL
            p.color = if (isLit) litColor else darkColor
            cv.drawRoundRect(rect, 3f, 3f, p)

            // 2. Si estÃ¡ iluminada, dibujar el brillo proyectado hacia abajo
            if (isLit) {
                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    shader = android.graphics.LinearGradient(
                        rect.left + winW / 2, rect.bottom,
                        rect.left + winW / 2, rect.bottom + glowH,
                        glowColorStart,
                        glowColorEnd,
                        android.graphics.Shader.TileMode.CLAMP
                    )
                }
                cv.drawRect(rect.left, rect.bottom, rect.right, rect.bottom + glowH, glowPaint)
            }

            // 3. Dibujar la cruz del marco de la ventana
            p.style = Paint.Style.STROKE
            p.color = if (isLit) litCrossColor else darkCrossColor
            p.strokeWidth = 2.5f
            
            // LÃ­nea vertical
            cv.drawLine(rect.left + winW / 2, rect.top, rect.left + winW / 2, rect.bottom, p)
            // LÃ­nea horizontal
            cv.drawLine(rect.left, rect.top + winH / 2, rect.right, rect.top + winH / 2, p)
        }
    }

    return bmp
}

