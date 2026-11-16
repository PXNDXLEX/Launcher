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
private const val BUILDING_FACADE_IMAGE_ID = "building-facade-windows"

class MockLocationProvider : LocationProvider {
    val consumers = java.util.concurrent.CopyOnWriteArraySet<LocationConsumer>()
    override fun registerLocationConsumer(locationConsumer: LocationConsumer) {
        consumers.add(locationConsumer)
    }
    override fun unRegisterLocationConsumer(locationConsumer: LocationConsumer) {
        consumers.remove(locationConsumer)
    }
    fun updateLocation(
        point: com.mapbox.geojson.Point,
        bearing: Double,
        animDurationMs: Long = 850L
    ) {
        val dur = animDurationMs.coerceIn(50L, 1250L)
        consumers.forEach { consumer ->
            consumer.onLocationUpdated(point) {
                duration = dur
                interpolator = LinearInterpolator()
            }
            consumer.onBearingUpdated(bearing) {
                duration = dur
                interpolator = LinearInterpolator()
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

    // Progreso animado para la transición 3D
    val perspectiveProgress by animateFloatAsState(
        targetValue = if (NavigationState.isRouteActive.value && isFollowingLocation) 1f else 0f,
        animationSpec = tween(durationMillis = 700),
        label = "perspective_3d"
    )

    val currentStyle = AppSettings.mapStyle.value
    // Ruta tipo alfombra de carril luminosa verde esmeralda (idéntica a la guía de carril 3D)
    val routeColorHex = when (currentStyle) {
        "NEON" -> "#00FFA6"
        else   -> "#38F8A8"
    }

    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx().toDouble() }
    val screenHeightPxRef = rememberUpdatedState(screenHeightPx)

    // Helper unificado para cámara de persecución 3D (Chase Cam a 76°, zoom 19.75, auto grande y centrado)
    fun buildChaseFollowState() = mapView.viewport.makeFollowPuckViewportState(
        FollowPuckViewportStateOptions.Builder()
            .pitch(76.0)
            .zoom(19.75)
            .bearing(com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateBearing.SyncWithLocationPuck)
            .padding(
                com.mapbox.maps.EdgeInsets(
                    (screenHeightPxRef.value * 0.28).coerceAtLeast(120.0),
                    leftInsetRef.value,
                    maxOf(screenHeightPxRef.value * 0.16, bottomInsetRef.value),
                    rightInsetRef.value
                )
            )
            .build()
    )

    // Referencias al mapa — guardadas en remember para no recrear
    var mapReady          by remember { mutableStateOf(false) }
    var lastAppliedStyle  by remember { mutableStateOf("") }
    var lastKnownBearing  by remember { mutableStateOf(0f) }
    var currentMapRotation by remember { mutableStateOf(0f) }
    var animator: ValueAnimator? by remember { mutableStateOf(null) }
    var autoCenterJob     by remember { mutableStateOf<Job?>(null) }
    // Posición donde se desactivó el seguimiento (para reactivar cada 1m)
    var locationWhenFollowDisabled by remember { mutableStateOf<android.location.Location?>(null) }
    // Bloquea el auto-recentrado mientras el usuario está interactuando
    var isSavingFavorite     by remember { mutableStateOf(false) }
    var isPlacingDestination by remember { mutableStateOf(false) }
    // Bearing explícito de GPS — actualizado con cada fix válido
    var gpsBearing by remember { mutableStateOf(0.0) }

    var searchQuery      by remember { mutableStateOf("") }
    var isSearchFocused  by remember { mutableStateOf(false) }
    var searchResults    by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var showFavorites    by remember { mutableStateOf(false) }
    var isSearching      by remember { mutableStateOf(false) }
    var isStyleTransitioning by remember { mutableStateOf(false) }
    var prevIndicatorPosListener by remember { mutableStateOf<OnIndicatorPositionChangedListener?>(null) }
    var prevIndicatorBearingListener by remember { mutableStateOf<OnIndicatorBearingChangedListener?>(null) }

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
            loadedStyle.addSource(geoJsonSource(CAR_HEADLIGHTS_SOURCE_ID) {
                featureCollection(FeatureCollection.fromFeatures(emptyList()))
            })
            loadedStyle.addSource(geoJsonSource(CAR_TAILLIGHTS_SOURCE_ID) {
                featureCollection(FeatureCollection.fromFeatures(emptyList()))
            })

            // ── Entorno 3D y Estética (Cockpit Indigo & Carriles Anchos) ─────────────────
            val isNightMode = (style == "DARK" || style == "NEON")
            if (style != "SATELLITE") {
                try {
                    val firstSymbolLayerId: String? = try {
                        loadedStyle.styleLayers.firstOrNull { it.type == "symbol" }?.id
                    } catch (e: Exception) { null }

                    // Limpiar capas personalizadas previas si se recarga el estilo
                    listOf(
                        "cockpit-ground-base", "3d-buildings", "3d-buildings-roof", "building-glow",
                        "custom-green-areas", "custom-water", "custom-landcover",
                        "custom-roads-casing", "custom-roads", "custom-roads-lane-dashes"
                    ).forEach { layerId ->
                        try {
                            if (loadedStyle.styleLayerExists(layerId)) loadedStyle.removeStyleLayer(layerId)
                        } catch (_: Exception) {}
                    }

                    // En modo DARK (Cockpit Original) eliminamos las capas grises de edificios base
                    // para que el suelo azul real y las avenidas anchas se vean 100% limpias como en la referencia.
                    if (style == "DARK") {
                        try {
                            loadedStyle.styleLayers.forEach { layerInfo ->
                                if (layerInfo.id.contains("building", ignoreCase = true) ||
                                    layerInfo.id.contains("structure", ignoreCase = true)
                                ) {
                                    try { loadedStyle.removeStyleLayer(layerInfo.id) } catch (_: Exception) {}
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    // 1. Suelo base Royal Indigo continuo (elimina el suelo gris oscuro de Style.DARK)
                    if (isNightMode) {
                        val baseGroundHex = if (style == "NEON") "#07061C" else "#232584"
                        try {
                            val bgLayer = backgroundLayer("cockpit-ground-base") {
                                backgroundColor(baseGroundHex)
                            }
                            if (firstSymbolLayerId != null) {
                                loadedStyle.addLayerBelow(bgLayer, firstSymbolLayerId)
                            } else {
                                loadedStyle.addLayer(bgLayer)
                            }
                        } catch (_: Exception) {}
                    }

                    // 2. Agua, Tierra y Áreas Verdes en tonos índigo armónicos
                    val greenColor = if (isNightMode) {
                        if (style == "NEON") "#0A001E" else "#1D2074"
                    } else "#b7e4c7"

                    try {
                        val greenLayer = fillLayer("custom-green-areas", "composite") {
                            sourceLayer("landuse")
                            fillColor(greenColor)
                            fillOpacity(0.85)
                        }
                        if (firstSymbolLayerId != null) loadedStyle.addLayerBelow(greenLayer, firstSymbolLayerId)
                        else loadedStyle.addLayer(greenLayer)
                    } catch (_: Exception) {}

                    if (isNightMode) {
                        val waterHex = if (style == "NEON") "#040414" else "#161960"
                        val landHex  = if (style == "NEON") "#07061C" else "#232584"
                        try {
                            val waterLayer = fillLayer("custom-water", "composite") {
                                sourceLayer("water")
                                fillColor(waterHex)
                            }
                            val landLayer = fillLayer("custom-landcover", "composite") {
                                sourceLayer("landcover")
                                fillColor(landHex)
                            }
                            if (firstSymbolLayerId != null) {
                                loadedStyle.addLayerBelow(waterLayer, firstSymbolLayerId)
                                loadedStyle.addLayerBelow(landLayer, firstSymbolLayerId)
                            } else {
                                loadedStyle.addLayer(waterLayer)
                                loadedStyle.addLayer(landLayer)
                            }
                        } catch (_: Exception) {}
                    }

                    // 3. Calles y Avenidas de Gran Escala (proporción de carril ancho tipo Cockpit 3D)
                    val roadColor = if (isNightMode) {
                        if (style == "NEON") "#0D1030" else "#282C94" // Asfalto azul real profundo
                    } else "#2E355B"

                    val casingColor = if (style == "NEON") "#00FFFF" else "#F4F8FF" // Líneas laterales blancas nítidas
                    val casingOpacity = if (style == "NEON") 0.90 else 0.96

                    // 3A. Líneas exteriores blancas de la carretera (Casing ancho)
                    try {
                        val casingLayer = lineLayer("custom-roads-casing", "composite") {
                            sourceLayer("road")
                            lineColor(casingColor)
                            lineWidth(
                                Expression.interpolate(
                                    Expression.linear(), Expression.zoom(),
                                    Expression.literal(12.0), Expression.literal(5.2),
                                    Expression.literal(15.5), Expression.literal(18.0),
                                    Expression.literal(18.0), Expression.literal(56.0),
                                    Expression.literal(20.0), Expression.literal(138.0)
                                )
                            )
                            lineOpacity(casingOpacity)
                            lineCap(LineCap.ROUND)
                            lineJoin(LineJoin.ROUND)
                        }
                        if (firstSymbolLayerId != null) loadedStyle.addLayerBelow(casingLayer, firstSymbolLayerId)
                        else loadedStyle.addLayer(casingLayer)
                    } catch (_: Exception) {}

                    // 3B. Cuerpo de asfalto 3D
                    try {
                        val roadSurfaceLayer = lineLayer("custom-roads", "composite") {
                            sourceLayer("road")
                            lineColor(roadColor)
                            lineWidth(
                                Expression.interpolate(
                                    Expression.linear(), Expression.zoom(),
                                    Expression.literal(12.0), Expression.literal(3.8),
                                    Expression.literal(15.5), Expression.literal(14.5),
                                    Expression.literal(18.0), Expression.literal(48.0),
                                    Expression.literal(20.0), Expression.literal(124.0)
                                )
                            )
                            lineCap(LineCap.ROUND)
                            lineJoin(LineCap.ROUND.let { LineJoin.ROUND })
                        }
                        if (firstSymbolLayerId != null) loadedStyle.addLayerBelow(roadSurfaceLayer, firstSymbolLayerId)
                        else loadedStyle.addLayer(roadSurfaceLayer)
                    } catch (_: Exception) {}

                    // 3C. Líneas divisorias blancas discontinuas en el centro de los carriles
                    try {
                        val laneDashLayer = lineLayer("custom-roads-lane-dashes", "composite") {
                            sourceLayer("road")
                            lineColor("#E6F0FF")
                            lineOpacity(0.85)
                            lineDasharray(listOf(2.2, 2.6))
                            lineWidth(
                                Expression.interpolate(
                                    Expression.linear(), Expression.zoom(),
                                    Expression.literal(14.0), Expression.literal(1.0),
                                    Expression.literal(17.5), Expression.literal(2.6),
                                    Expression.literal(20.0), Expression.literal(4.6)
                                )
                            )
                            lineCap(LineCap.BUTT)
                            lineJoin(LineJoin.ROUND)
                        }
                        if (firstSymbolLayerId != null) loadedStyle.addLayerBelow(laneDashLayer, firstSymbolLayerId)
                        else loadedStyle.addLayer(laneDashLayer)
                    } catch (_: Exception) {}

                    // Edificios 3D solo en modo NEON o LIGHT (y debajo de las etiquetas, sin tapar la calle en modo DARK Cockpit)
                    if (style == "NEON" || style == "LIGHT") {
                        val buildingColor = if (style == "NEON") "#090514" else "#e0e0e0"
                        val heightMult = if (style == "NEON") 1.8 else 2.0
                        try {
                            loadedStyle.addLayerBelow(fillExtrusionLayer("3d-buildings", "composite") {
                                sourceLayer("building")
                                filter(Expression.eq(Expression.get("extrude"), Expression.literal("true")))
                                minZoom(15.0)
                                fillExtrusionColor(buildingColor)
                                fillExtrusionHeight(Expression.product(Expression.get("height"), Expression.literal(heightMult)))
                                fillExtrusionBase(Expression.product(Expression.get("min_height"), Expression.literal(heightMult)))
                                fillExtrusionOpacity(0.75)
                            }, "custom-roads-casing")
                        } catch (_: Exception) {}
                    }

                } catch (e: Exception) {}
            }

            // ── Efectos Especiales (Faros y Luces Traseras) ─────────────────
            if (isNightMode) {
                try {
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

                    loadedStyle.addLayer(com.mapbox.maps.extension.style.layers.generated.symbolLayer(CAR_TAILLIGHTS_LAYER_ID, CAR_TAILLIGHTS_SOURCE_ID) {
                        iconImage("car-taillights-glow")
                        iconPitchAlignment(com.mapbox.maps.extension.style.layers.properties.generated.IconPitchAlignment.MAP)
                        iconRotationAlignment(com.mapbox.maps.extension.style.layers.properties.generated.IconRotationAlignment.MAP)
                        iconRotate(Expression.get("bearing"))
                        iconAllowOverlap(true)
                        iconIgnorePlacement(true)
                        iconSize(Expression.get("iconSize"))
                    })
                } catch (e: Exception) {}
            }

            // ── Capas de Ruta Visuales (Alfombra luminosa de carril 3D) ──────
            loadedStyle.addLayer(lineLayer(ROUTE_CASING_LAYER_ID, ROUTE_SOURCE_ID) {
                lineColor("#00E676")
                lineWidth(
                    Expression.interpolate(
                        Expression.linear(), Expression.zoom(),
                        Expression.literal(13.0), Expression.literal(12.0),
                        Expression.literal(17.5), Expression.literal(32.0),
                        Expression.literal(20.0), Expression.literal(62.0)
                    )
                )
                lineCap(LineCap.ROUND)
                lineJoin(LineJoin.ROUND)
                lineOpacity(0.34)
            })
            loadedStyle.addLayer(lineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID) {
                lineColor(routeColorHex)
                lineWidth(
                    Expression.interpolate(
                        Expression.linear(), Expression.zoom(),
                        Expression.literal(13.0), Expression.literal(7.0),
                        Expression.literal(17.5), Expression.literal(20.0),
                        Expression.literal(20.0), Expression.literal(42.0)
                    )
                )
                lineCap(LineCap.ROUND)
                lineJoin(LineJoin.ROUND)
                lineOpacity(0.95)
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

            // (Las capas estéticas terminan aquí)
            
            // LocationComponent — puck del vehículo
            val locPlugin = mapView.location
            try {
                // Si esto compila, lo usamos
                locPlugin.setLocationProvider(mockLocationProvider)
            } catch (e: Exception) {}

            locPlugin.updateSettings {
                enabled = true
                pulsingEnabled = true
                pulsingColor = android.graphics.Color.parseColor(
                    when (style) { "NEON" -> "#FF9100"; else -> "#2979FF" }
                )
                // Fundamental: habilitar el bearing del puck y usar HEADING (brújula)
                puckBearingEnabled = true
                puckBearing = PuckBearing.HEADING
                locationPuck = getVehiclePuck(context, AppSettings.vehicleType.value, AppSettings.customVehicleIconPath.value, mapIconColor, AppSettings.vehicle3DScale.value)
            }

            // Forzar actualización de ubicación para que el modelo 3D aparezca inmediatamente
            // al cambiar el estilo (día/noche), de lo contrario Mapbox espera un evento de GPS real
            NavigationState.currentLocation.value?.let { loc ->
                mockLocationProvider.updateLocation(
                    com.mapbox.geojson.Point.fromLngLat(loc.longitude, loc.latitude),
                    if (loc.hasBearing()) loc.bearing.toDouble() else gpsBearing,
                    animDurationMs = 80L
                )
            }

            // ── Heading-up SIEMPRE activo: brujula + GPS bearing (con actualización fluida a 30 FPS) ──
            var lastIndicatorPos: com.mapbox.geojson.Point? = null
            var lastIndicatorBearing: Double = 0.0
            var lastGlowUpdateMs: Long = 0L
            val updateGlow = {
                val nowMs = System.currentTimeMillis()
                if (isNightMode && isVisibleState.value && (nowMs - lastGlowUpdateMs >= 32L)) {
                    lastGlowUpdateMs = nowMs
                    lastIndicatorPos?.let { pos ->
                        mapView.mapboxMap.getStyle { style ->
                            val featureCollection = com.mapbox.geojson.FeatureCollection.fromFeature(
                                com.mapbox.geojson.Feature.fromGeometry(
                                    pos,
                                    com.google.gson.JsonObject().apply {
                                        addProperty("bearing", lastIndicatorBearing)
                                        addProperty("iconSize", AppSettings.glowIconSize.value.toDouble())
                                    }
                                )
                            )
                            style.getSourceAs<com.mapbox.maps.extension.style.sources.generated.GeoJsonSource>(CAR_HEADLIGHTS_SOURCE_ID)?.featureCollection(featureCollection)
                            style.getSourceAs<com.mapbox.maps.extension.style.sources.generated.GeoJsonSource>(CAR_TAILLIGHTS_SOURCE_ID)?.featureCollection(featureCollection)
                        }
                    }
                }
            }

            prevIndicatorPosListener?.let { locPlugin.removeOnIndicatorPositionChangedListener(it) }
            val posListener = OnIndicatorPositionChangedListener { point ->
                lastIndicatorPos = point
                updateGlow()
            }
            prevIndicatorPosListener = posListener
            locPlugin.addOnIndicatorPositionChangedListener(posListener)

            prevIndicatorBearingListener?.let { locPlugin.removeOnIndicatorBearingChangedListener(it) }
            val bearingListener = OnIndicatorBearingChangedListener { newBearing ->
                lastIndicatorBearing = newBearing
                updateGlow()
                gpsBearing = newBearing
                lastKnownBearing = newBearing.toFloat()
            }
            prevIndicatorBearingListener = bearingListener
            locPlugin.addOnIndicatorBearingChangedListener(bearingListener)

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

                    // Activar viewport 3D Chase Cam (78° de inclinación, auto centrado abajo)
                    val vp = mapView.viewport
                    vp.transitionTo(
                        buildChaseFollowState(),
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

    // ── Sincronizar visibilidad e insets sin recrear el MapView ──────
    LaunchedEffect(isVisible, leftInsetPx, rightInsetPx, bottomInsetPx, mapReady) {
        if (!mapReady) return@LaunchedEffect
        mapView.visibility = if (isVisible) android.view.View.VISIBLE else android.view.View.INVISIBLE
        if (isVisible) {
            NavigationState.currentLocation.value?.let { loc ->
                if (loc.latitude != 0.0) {
                    val carPos = Point.fromLngLat(loc.longitude, loc.latitude)
                    val carBearing = if (loc.hasBearing()) loc.bearing.toDouble() else gpsBearing
                    mockLocationProvider.updateLocation(carPos, carBearing, animDurationMs = 80L)
                    hasInitializedPosition = true
                }
            }
            if (isFollowingLocation && !isIntroAnimating) {
                val vp = mapView.viewport
                vp.transitionTo(
                    buildChaseFollowState(),
                    vp.makeDefaultViewportTransition(
                        DefaultViewportTransitionOptions.Builder().maxDurationMs(650).build()
                    )
                )
            }
        }
    }

    // ── Suscripción al motor GPS unificado en tiempo real (LocationEngine) ──
    DisposableEffect(Unit) {
        val listener = LocationEngine.Listener { loc, projectedLat, projectedLon, bearingDeg, _, animDurationMs, _ ->
            // Lógica de ruta activa funciona siempre (incluso en pantalla Home)
            if (currentRoutePoints.isNotEmpty() && selectedDestination != null) {
                var minDistance = Float.MAX_VALUE
                var closestIndex = 0
                val searchLimit = minOf(100, currentRoutePoints.size)
                val distBuf = FloatArray(1)

                for (i in 0 until searchLimit) {
                    val pt = currentRoutePoints[i]
                    android.location.Location.distanceBetween(loc.latitude, loc.longitude, pt.latitude(), pt.longitude(), distBuf)
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
                    android.location.Location.distanceBetween(loc.latitude, loc.longitude, nextStep.maneuverLat, nextStep.maneuverLon, distBuf)
                    if (distBuf[0] < 20f) activeRouteSteps.removeAt(0)
                }

                if (currentRoutePoints.isNotEmpty() && currentRoutePoints.size < 10) {
                    val endPt = currentRoutePoints.last()
                    android.location.Location.distanceBetween(loc.latitude, loc.longitude, endPt.latitude(), endPt.longitude(), distBuf)
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

            gpsBearing = bearingDeg
            mockLocationProvider.updateLocation(
                Point.fromLngLat(projectedLon, projectedLat),
                bearingDeg,
                animDurationMs = animDurationMs
            )

            if (!hasInitializedPosition && loc.latitude != 0.0) {
                hasInitializedPosition = true
                val vp = mapView.viewport
                vp.transitionTo(buildChaseFollowState(), vp.makeDefaultViewportTransition())
            }

            val userInteracting = isSavingFavorite || isPlacingDestination || isAerialExploreMode
            if (!isFollowingLocation && hasInitializedPosition && !userInteracting) {
                val refLoc = locationWhenFollowDisabled
                if (refLoc != null && loc.distanceTo(refLoc) >= 1f) {
                    isFollowingLocation = true
                    locationWhenFollowDisabled = null
                    autoCenterJob?.cancel()
                    val vp = mapView.viewport
                    vp.transitionTo(buildChaseFollowState(), vp.makeDefaultViewportTransition())
                }
            }

            if (!isIntroAnimating && !NavigationState.isRouteActive.value && isFollowingLocation && !isAerialExploreMode && hasInitializedPosition) {
                val vp = mapView.viewport
                if (vp.status !is com.mapbox.maps.plugin.viewport.ViewportStatus.State) {
                    vp.transitionTo(buildChaseFollowState(), vp.makeDefaultViewportTransition())
                }
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
        val vp = mapView.viewport
        vp.transitionTo(
            buildChaseFollowState(),
            vp.makeDefaultViewportTransition(
                DefaultViewportTransitionOptions.Builder().maxDurationMs(1050).build()
            )
        )
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
            val vp = mapView.viewport
            vp.transitionTo(
                buildChaseFollowState(),
                vp.makeDefaultViewportTransition(
                    DefaultViewportTransitionOptions.Builder().maxDurationMs(2500).build()
                )
            )
            delay(2600)
        } finally {
            isIntroAnimating = false
            isFollowingLocation = true
            pendingIntroPos = null
        }
    }

    // ── Re-centrar al cambiar orientación (portrait ↔ landscape) ────────────
    LaunchedEffect(configuration.orientation) {
        if (hasInitializedPosition && isFollowingLocation && !isAerialExploreMode) {
            val vp = mapView.viewport
            vp.transitionTo(buildChaseFollowState(), vp.makeImmediateViewportTransition())
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

    // ── Reaccionar a cambios de tipo de vehículo/escala/color ────────────
    LaunchedEffect(vehicleType, AppSettings.customVehicleIconPath.value, mapIconColor, AppSettings.vehicle3DScale.value) {
        if (!mapReady) return@LaunchedEffect
        mapView.location.updateSettings {
            locationPuck = getVehiclePuck(context, vehicleType, AppSettings.customVehicleIconPath.value, mapIconColor, AppSettings.vehicle3DScale.value)
        }
    }

    // ── UI ───────────────────────────────────────────────────────────────────

    val showGlowTuning = NavigationState.showGlowTuning.value

    val refreshGlowOnMap: () -> Unit = {
        mapView.mapboxMap.getStyle { style ->
            try {
                val headBmp = drawHeadlightsGlow()
                val tailBmp = drawTaillightsGlow()
                style.removeStyleImage("car-headlights-glow")
                style.removeStyleImage("car-taillights-glow")
                style.addImage("car-headlights-glow", headBmp)
                style.addImage("car-taillights-glow", tailBmp)
                mapView.location.updateSettings {
                    locationPuck = getVehiclePuck(
                        context,
                        AppSettings.vehicleType.value,
                        AppSettings.customVehicleIconPath.value,
                        AppSettings.mapIconColor.value,
                        AppSettings.vehicle3DScale.value
                    )
                }
            } catch (e: Exception) { }
        }
    }

    // Fondo base Royal Indigo para que jamás se vea un borde negro detrás del MapView
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1F237A))
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
                        selectedDestinationTitle = "Punto seleccionado en el mapa"
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
                                zoom(if (isAerialExploreMode) 16.5 else 17.2)
                                pitch(if (isAerialExploreMode) 0.0 else 42.0)
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
                                    val vp = mapView.viewport
                                    vp.transitionTo(buildChaseFollowState(), vp.makeDefaultViewportTransition())
                                }
                            }
                        }
                    })

                    NavigationState.currentLocation.value?.let { loc ->
                        if (!hasInitializedPosition && loc.latitude != 0.0) {
                            mapboxMap.setCamera(cameraOptions {
                                center(Point.fromLngLat(loc.longitude, loc.latitude))
                                zoom(19.75)
                                pitch(76.0)
                                bearing(gpsBearing)
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

        // ── Horizonte Cielo + Silueta de Ciudad 3D (Cubre hasta 45% para sellar el horizonte sin franjas negras) ──
        val horizonAlpha by animateFloatAsState(
            targetValue = if (isFollowingLocation && !isAerialExploreMode && !isIntroAnimating) 1f else 0f,
            animationSpec = tween(durationMillis = 420),
            label = "horizonSkyAlpha"
        )
        if (horizonAlpha > 0.01f) {
            CockpitHorizonSkyBackdrop(
                alpha = horizonAlpha,
                currentStyle = currentStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.45f)
                    .align(Alignment.TopCenter)
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

        // ── Contenedor de controles HUD sobre el Mapa ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = startOverlayPadding,
                    end = endOverlayPadding,
                    bottom = bottomOverlayPadding
                )
        ) {
            // ── ZONA SUPERIOR CENTRAL: Turn-by-Turn Card ("200 m / Calle 26") o Buscador + Favoritos ──
            val nextStep = activeRouteSteps.firstOrNull()
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp, start = 235.dp, end = 175.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Banner Turn-by-Turn estilo Cockpit ("↱ 200 m / Calle 26")
                AnimatedVisibility(
                    visible = NavigationState.isRouteActive.value,
                    enter = slideInVertically(
                        initialOffsetY = { -it },
                        animationSpec = spring(dampingRatio = 0.72f, stiffness = 360f)
                    ) + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut()
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
                        color = Color(0xFF1A256E).copy(alpha = 0.95f),
                        shadowElevation = 14.dp,
                        border = BorderStroke(1.5.dp, Color(0xFF4A66FF).copy(alpha = 0.60f)),
                        modifier = Modifier.widthIn(min = 215.dp, max = 315.dp)
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
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
                                        tint = Color(0xFF38F2A6),
                                        modifier = Modifier.size(38.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = distDisplay.first,
                                            fontSize = 34.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White,
                                            lineHeight = 36.sp
                                        )
                                        if (distDisplay.second.isNotEmpty()) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = distDisplay.second,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White.copy(alpha = 0.90f),
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = streetDisplay,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.95f),
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
                                    tint = Color.White.copy(alpha = 0.65f),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }

                // 2. Barra de búsqueda y Favoritos con animaciones elásticas modernas
                AnimatedVisibility(
                    visible = !NavigationState.isRouteActive.value,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.widthIn(max = 350.dp)
                    ) {
                        val favHeartScale by animateFloatAsState(
                            targetValue = if (showFavorites) 1.12f else 1f,
                            animationSpec = spring(dampingRatio = 0.5f, stiffness = 450f),
                            label = "favHeartScale"
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(22.dp),
                                color = Color(0xFF0E1638).copy(alpha = 0.90f),
                                border = BorderStroke(
                                    1.2.dp,
                                    if (isSearchFocused) Color(0xFF38F2A6) else Color.White.copy(alpha = 0.25f)
                                ),
                                shadowElevation = 10.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        null,
                                        tint = Color(0xFF38F2A6),
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                "¿A dónde vamos?",
                                                color = Color.White.copy(alpha = 0.65f),
                                                fontSize = 13.sp,
                                                maxLines = 1
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
                                                color = Color.White
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
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = Color(0xFF38F2A6)
                                        )
                                    } else if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { searchQuery = ""; searchResults = emptyList() },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                null,
                                                tint = Color.White,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Botón de Favoritos con rebote elástico
                            Surface(
                                onClick = {
                                    showFavorites = !showFavorites
                                    searchResults = emptyList()
                                },
                                shape = CircleShape,
                                color = if (showFavorites) Color(0xFF183652) else Color(0xFF0E1638).copy(alpha = 0.90f),
                                border = BorderStroke(
                                    1.3.dp,
                                    if (showFavorites) Color(0xFF38F2A6) else Color.White.copy(alpha = 0.25f)
                                ),
                                shadowElevation = 8.dp,
                                modifier = Modifier
                                    .size(44.dp)
                                    .scale(favHeartScale)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (showFavorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "Favoritos",
                                        tint = if (showFavorites) Color(0xFF38F2A6) else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Panel Flotante Glassmorphic para Favoritos y Resultados de Búsqueda
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
                                    .heightIn(max = 260.dp),
                                shape = RoundedCornerShape(22.dp),
                                color = Color(0xFF0B112E).copy(alpha = 0.96f),
                                border = BorderStroke(
                                    1.2.dp,
                                    Brush.linearGradient(
                                        listOf(
                                            Color(0xFF38F2A6).copy(alpha = 0.55f),
                                            Color(0xFF4A78FF).copy(alpha = 0.45f)
                                        )
                                    )
                                ),
                                shadowElevation = 16.dp
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    // Cabecera del panel
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
                                            color = Color(0xFF38F2A6)
                                        )
                                        // Botón rápido para guardar ubicación actual como favorito
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
                                                color = Color(0xFF1A2A66),
                                                border = BorderStroke(1.dp, Color(0xFF38F2A6).copy(alpha = 0.45f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.AddLocationAlt, null, tint = Color(0xFF38F2A6), modifier = Modifier.size(13.dp))
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
                                                .padding(vertical = 22.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(
                                                    Icons.Default.FavoriteBorder,
                                                    null,
                                                    tint = Color.White.copy(alpha = 0.35f),
                                                    modifier = Modifier.size(28.dp)
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    "Mantén presionado el mapa para marcar o guardar favoritos",
                                                    fontSize = 11.sp,
                                                    color = Color.White.copy(alpha = 0.65f)
                                                )
                                            }
                                        }
                                    } else {
                                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items(list) { place ->
                                                Surface(
                                                    shape = RoundedCornerShape(14.dp),
                                                    color = Color(0xFF151E4A).copy(alpha = 0.85f),
                                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
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
                                                                .size(34.dp)
                                                                .background(
                                                                    Color(0xFF38F2A6).copy(alpha = 0.16f),
                                                                    CircleShape
                                                                ),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                when (place.iconType) {
                                                                    "Home" -> Icons.Default.Home
                                                                    "Work" -> Icons.Default.Work
                                                                    else -> Icons.Default.Star
                                                                },
                                                                null,
                                                                tint = Color(0xFF38F2A6),
                                                                modifier = Modifier.size(18.dp)
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
                                                                color = Color.White.copy(alpha = 0.65f),
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
                                                                color = Color(0xFF38F2A6),
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(end = 6.dp)
                                                            )
                                                        }
                                                        // Botón directo "IR" con 1 toque
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
                                                            color = Color(0xFF00E676)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Icon(Icons.Default.Navigation, null, tint = Color(0xFF052216), modifier = Modifier.size(13.dp))
                                                                Spacer(modifier = Modifier.width(3.dp))
                                                                Text("IR", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF052216))
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
                                                                    tint = Color.White.copy(alpha = 0.55f),
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
                    }
                }

                // 3. Tarjeta Glassmorphic Animada al marcar un Punto en el Mapa (con pulso neón + Guardar Favorito + Ruta 3D)
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
                            .widthIn(min = 290.dp, max = 365.dp),
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xFF0D1438).copy(alpha = 0.95f),
                        border = BorderStroke(
                            1.4.dp,
                            Brush.linearGradient(
                                listOf(Color(0xFF38F2A6), Color(0xFF4B7BFF))
                            )
                        ),
                        shadowElevation = 16.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Icono de destino con pulso radar animado
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .scale(pulseScale)
                                    .background(Color(0xFF38F2A6).copy(alpha = 0.18f), CircleShape)
                                    .border(1.dp, Color(0xFF38F2A6).copy(alpha = 0.65f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Place,
                                    contentDescription = null,
                                    tint = Color(0xFF38F2A6),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedDestinationTitle.ifBlank { "Punto marcado" },
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = routeDistanceText.ifEmpty { liveDistanceStr },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38F2A6),
                                    maxLines = 1
                                )
                            }

                            // Botón para Guardar este punto en Favoritos con animación
                            IconButton(
                                onClick = {
                                    destPt?.let { pt ->
                                        favoriteLocationToSave = pt
                                        favoriteNameToSave = if (selectedDestinationTitle != "Punto seleccionado en el mapa") selectedDestinationTitle else ""
                                        favoriteAddressToSave = "${String.format("%.5f", pt.latitude())}, ${String.format("%.5f", pt.longitude())}"
                                        selectedIconType = "Star"
                                        isSavingFavorite = true
                                        showSaveFavoriteDialog = true
                                    }
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(Color(0xFF1A265C), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.StarBorder,
                                    contentDescription = "Guardar en Favoritos",
                                    tint = Color(0xFFFFD54F),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Botón Cancelar punto
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
                                    .background(Color(0xFF2A1832), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, "Cancelar", tint = Color(0xFFFF6E7F), modifier = Modifier.size(16.dp))
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Botón Iniciar Ruta 3D
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
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                modifier = Modifier.height(36.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Icon(Icons.Default.Navigation, null, tint = Color(0xFF052015), modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("IR 3D", fontWeight = FontWeight.Black, color = Color(0xFF052015), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // ── CONTROLES RÁPIDOS DE MAPA (Esquina Superior Derecha: Vista Aérea 2D / 3D, Centrar, Luces, Estilo) ──
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botón Principal: Alternar Vista Aérea Temporal (2D Explorar) <-> Conducción 3D (Chase Cam)
                Surface(
                    onClick = {
                        if (isAerialExploreMode) {
                            returnTo3DChaseView()
                        } else {
                            activateAerialExplore()
                        }
                    },
                    shape = RoundedCornerShape(50),
                    color = if (isAerialExploreMode) Color(0xFF00E5FF) else Color(0xFF10173A).copy(alpha = 0.90f),
                    border = BorderStroke(
                        1.2.dp,
                        if (isAerialExploreMode) Color.White else Color(0xFF38F2A6).copy(alpha = 0.55f)
                    ),
                    shadowElevation = 8.dp,
                    modifier = Modifier.height(40.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = if (isAerialExploreMode) Icons.Default.DirectionsCar else Icons.Default.Explore,
                            contentDescription = "Alternar Vista Aérea / 3D",
                            tint = if (isAerialExploreMode) Color(0xFF051824) else Color(0xFF38F2A6),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (isAerialExploreMode) "3D AUTO" else "2D AÉREA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isAerialExploreMode) Color(0xFF051824) else Color.White
                        )
                    }
                }

                // Botón recentrar — vuelve inmediatamente al ángulo 3D Chase Cam (76°)
                AnimatedVisibility(visible = !isFollowingLocation && !isAerialExploreMode) {
                    SmallFloatingActionButton(
                        onClick = returnTo3DChaseView,
                        containerColor = Color(0xFF00E676),
                        contentColor = Color(0xFF071E16),
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.MyLocation, "Centrar 3D", modifier = Modifier.size(20.dp))
                    }
                }

                // Botón ajuste de luces del vehículo 3D
                SmallFloatingActionButton(
                    onClick = { NavigationState.showGlowTuning.value = !NavigationState.showGlowTuning.value },
                    containerColor = if (showGlowTuning) Color(0xFF00E5FF) else Color(0xFF10173A).copy(alpha = 0.88f),
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Text(if (showGlowTuning) "✕" else "🔦", fontSize = 14.sp)
                }

                // Selector de estilo de mapa
                Box {
                    SmallFloatingActionButton(
                        onClick = { showStyleSelector = !showStyleSelector },
                        containerColor = Color(0xFF10173A).copy(alpha = 0.88f),
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = when (currentStyle) {
                                "SATELLITE" -> Icons.Default.Public
                                "NEON"      -> Icons.Default.Bolt
                                "DARK"      -> Icons.Default.DarkMode
                                else        -> Icons.Default.LightMode
                            },
                            contentDescription = "Estilo Mapa",
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showStyleSelector,
                        onDismissRequest = { showStyleSelector = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        DropdownMenuItem(text = { Text("Día") },           leadingIcon = { Icon(Icons.Default.LightMode, null) },                                     onClick = { AppSettings.setMapStyle("LIGHT");     showStyleSelector = false })
                        DropdownMenuItem(text = { Text("Noche Cockpit") }, leadingIcon = { Icon(Icons.Default.DarkMode, null) },                                      onClick = { AppSettings.setMapStyle("DARK");      showStyleSelector = false })
                        DropdownMenuItem(text = { Text("Neon Electric") }, leadingIcon = { Icon(Icons.Default.Bolt, null, tint = Color(0xFF00B0FF)) },                 onClick = { AppSettings.setMapStyle("NEON");      showStyleSelector = false })
                        DropdownMenuItem(text = { Text("Satélite") },      leadingIcon = { Icon(Icons.Default.Public, null, tint = Color(0xFF4CAF50)) },               onClick = { AppSettings.setMapStyle("SATELLITE"); showStyleSelector = false })
                    }
                }
            }

            // ── Píldora flotante inferior cuando está en Modo Exploración Aérea 2D ──
            AnimatedVisibility(
                visible = isAerialExploreMode,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 18.dp)
            ) {
                Surface(
                    onClick = returnTo3DChaseView,
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF00E676),
                    shadowElevation = 12.dp,
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.7f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Navigation,
                            contentDescription = null,
                            tint = Color(0xFF042214),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "VOLVER A VISTA 3D",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF042214)
                        )
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

            // ── Modal Glassmorphic Moderno para Guardar Favorito (reemplaza el AlertDialog simple) ──
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
                    color = Color(0xFF0B1130).copy(alpha = 0.97f),
                    border = BorderStroke(
                        1.6.dp,
                        Brush.linearGradient(
                            listOf(Color(0xFF38F2A6), Color(0xFF00E5FF), Color(0xFF5C6BC0))
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
                                            Brush.linearGradient(listOf(Color(0xFF38F2A6), Color(0xFF00B0FF))),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Star, null, tint = Color(0xFF051B14), modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "Nuevo Lugar Favorito",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                    favoriteLocationToSave?.let { pt ->
                                        Text(
                                            "${String.format("%.5f", pt.latitude())}, ${String.format("%.5f", pt.longitude())}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF38F2A6)
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
                                Icon(Icons.Default.Close, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
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
                                    color = if (isSel) Color(0xFF193664) else Color(0xFF131B42),
                                    border = BorderStroke(
                                        if (isSel) 1.5.dp else 1.dp,
                                        if (isSel) Color(0xFF38F2A6) else Color.White.copy(alpha = 0.14f)
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
                                            tint = if (isSel) Color(0xFF38F2A6) else Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSel) Color.White else Color.White.copy(alpha = 0.7f)
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
                                    color = if (favoriteNameToSave == quickTag) Color(0xFF38F2A6).copy(alpha = 0.22f) else Color(0xFF151E48),
                                    border = BorderStroke(
                                        1.dp,
                                        if (favoriteNameToSave == quickTag) Color(0xFF38F2A6) else Color.White.copy(alpha = 0.15f)
                                    )
                                ) {
                                    Text(
                                        text = quickTag,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
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
                                Text("Escribe un nombre (ej. Casa, Oficina...)", color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF38F2A6),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                                cursorColor = Color(0xFF38F2A6),
                                focusedContainerColor = Color(0xFF121A40),
                                unfocusedContainerColor = Color(0xFF121A40)
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
                                Text("Cancelar", color = Color.White.copy(alpha = 0.75f), fontWeight = FontWeight.Bold)
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
                                    containerColor = Color(0xFF00E676),
                                    disabledContainerColor = Color(0xFF233258)
                                )
                            ) {
                                Icon(Icons.Default.Check, null, tint = Color(0xFF042014), modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Guardar Favorito", color = Color(0xFF042014), fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }

        } // Cierra Box de HUD
    }
}

/**
 * Horizonte 3D con degradado de cielo, silueta de ciudad y faldón Royal Indigo continuo
 * que sella el horizonte del mapa al 100% evitando cualquier franja negra de Mapbox.
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
        val horizonY = h * 0.56f
        val solidGroundEndY = h * 0.84f

        val skyTop = when (currentStyle) {
            "NEON" -> Color(0xFF06081E)
            else   -> Color(0xFF0C3B75)
        }
        val skyMid = when (currentStyle) {
            "NEON" -> Color(0xFF141D5E)
            else   -> Color(0xFF2672BE)
        }
        val skyHorizon = when (currentStyle) {
            "NEON" -> Color(0xFF263494)
            else   -> Color(0xFF4D98E8)
        }
        val groundColor = when (currentStyle) {
            "NEON" -> Color(0xFF07061C)
            else   -> Color(0xFF232584)
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
        val backBuildingColor = Color(0xFF326AB8).copy(alpha = alpha * 0.58f)
        val frontBuildingColor = Color(0xFF224B96).copy(alpha = alpha * 0.82f)
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

        // 3. Faldón sólido Royal Indigo bajo la línea del horizonte + fundido hacia las calles 3D
        drawRect(
            color = groundColor.copy(alpha = alpha),
            topLeft = androidx.compose.ui.geometry.Offset(0f, horizonY - 1f),
            size = androidx.compose.ui.geometry.Size(w, solidGroundEndY - horizonY + 2f)
        )
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(
                    groundColor.copy(alpha = alpha),
                    groundColor.copy(alpha = alpha * 0.55f),
                    Color.Transparent
                ),
                startY = solidGroundEndY,
                endY = h
            ),
            topLeft = androidx.compose.ui.geometry.Offset(0f, solidGroundEndY),
            size = androidx.compose.ui.geometry.Size(w, h - solidGroundEndY)
        )
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

// ── Helper para LocationPuck (3D o 2D con presencia ampliada tipo Cockpit) ─────────
fun getVehiclePuck(context: Context, vehicleType: String, customPath: String, mapIconColor: Int, scale: Float = 4f): com.mapbox.maps.plugin.LocationPuck {
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
        // Multiplicador base x1.95 adicional para que el vehículo 3D tenga presencia protagonista en tercera persona
        val baseScale = if (vehicleType == "STYLUS" || vehicleType == "CORSA") scale * 500f else scale
        val finalScale = baseScale * 1.95f

        return com.mapbox.maps.plugin.LocationPuck3D(
            modelUri = modelAsset,
            modelScale = listOf(finalScale, finalScale, finalScale),
            modelRotation = listOf(0f, 0f, 180f)
        )
    }

    val bmp = try {
        if (vehicleType == "CUSTOM" && customPath.isNotEmpty()) {
            val file = java.io.File(customPath)
            if (file.exists()) android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                .let { android.graphics.Bitmap.createScaledBitmap(it, 150, 150, true) }
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

    val beamColor   = android.graphics.Color.argb(100, 255, 255, 200)
    val transparent = android.graphics.Color.TRANSPARENT

    // === HEADLIGHTS ===
    val leftPath = Path()
    leftPath.moveTo(cx - carHalfW - 2f, headY)
    leftPath.lineTo(cx - carHalfW - headSpread, headReach)
    leftPath.lineTo(cx - 5f, headReach)
    leftPath.close()
    paint.shader = LinearGradient(cx - carHalfW, headY, cx - carHalfW, headReach, beamColor, transparent, Shader.TileMode.CLAMP)
    canvas.drawPath(leftPath, paint)

    val rightPath = Path()
    rightPath.moveTo(cx + carHalfW + 2f, headY)
    rightPath.lineTo(cx + carHalfW + headSpread, headReach)
    rightPath.lineTo(cx + 5f, headReach)
    rightPath.close()
    paint.shader = LinearGradient(cx + carHalfW, headY, cx + carHalfW, headReach, beamColor, transparent, Shader.TileMode.CLAMP)
    canvas.drawPath(rightPath, paint)

    // === GROUND REFLECTION ===
    val reflColor = android.graphics.Color.argb(40, 255, 220, 150)
    paint.shader = RadialGradient(cx, 256f, 45f, reflColor, transparent, Shader.TileMode.CLAMP)
    canvas.drawCircle(cx, 256f, 45f, paint)

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
    
    // Si está frenando, la intensidad al 100% y radio +50%. Si no, tenues (70 alfa).
    val alpha = if (isBraking) 255 else 70
    val radius = if (isBraking) tailRadius * 1.5f else tailRadius
    val tailColor = android.graphics.Color.argb(alpha, 255, 15, 15)

    // === TAILLIGHTS ===
    paint.shader = RadialGradient(cx - carHalfW, tailY, radius, tailColor, transparent, Shader.TileMode.CLAMP)
    canvas.drawCircle(cx - carHalfW, tailY, radius, paint)

    paint.shader = RadialGradient(cx + carHalfW, tailY, radius, tailColor, transparent, Shader.TileMode.CLAMP)
    canvas.drawCircle(cx + carHalfW, tailY, radius, paint)

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

