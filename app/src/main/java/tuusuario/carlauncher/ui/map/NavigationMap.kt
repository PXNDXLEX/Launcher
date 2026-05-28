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
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.fillExtrusionLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.extension.style.layers.addLayerBelow
import com.mapbox.maps.extension.style.layers.addLayerAbove
import com.mapbox.maps.extension.style.layers.generated.fillLayer
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
private const val CAR_LIGHTS_SOURCE_ID   = "car-lights-source"
private const val CAR_LIGHTS_LAYER_ID    = "car-lights-layer"
private const val BUILDING_FACADE_IMAGE_ID = "building-facade-windows"

class MockLocationProvider : LocationProvider {
    val consumers = mutableSetOf<LocationConsumer>()
    override fun registerLocationConsumer(locationConsumer: LocationConsumer) {
        consumers.add(locationConsumer)
    }
    override fun unRegisterLocationConsumer(locationConsumer: LocationConsumer) {
        consumers.remove(locationConsumer)
    }
    fun updateLocation(point: com.mapbox.geojson.Point, bearing: Double) {
        consumers.forEach {
            it.onLocationUpdated(point)
            it.onBearingUpdated(bearing)
            
            // Update lights source
            val map = (it as? com.mapbox.maps.plugin.locationcomponent.LocationComponentPlugin)?.let {
                // In a real implementation we would have a reference to the map here
                // For now, we update global or map-specific context if possible
            }
        }
    }
}

// ── Composable Principal ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationMap(modifier: Modifier = Modifier, isFullScreen: Boolean = false, isDarkMode: Boolean = true) {
    val context         = LocalContext.current
    val mockLocationProvider = remember { MockLocationProvider() }
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
    val configuration = LocalConfiguration.current  // detectar rotación
    var isFollowingLocation by rememberSaveable { mutableStateOf(true) }
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
    val routeColorHex = when (currentStyle) {
        "NEON" -> "#FF00FF"
        "DARK" -> "#00FFFF"
        else   -> "#2979FF"
    }

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
            "NEON"      -> Style.DARK // Usamos DARK como base para asegurar que los edificios existan en el composite source
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
            loadedStyle.addSource(geoJsonSource(CAR_LIGHTS_SOURCE_ID) {
                featureCollection(FeatureCollection.fromFeatures(emptyList()))
            })

            // ── Entorno 3D y Estética ─────────────────
            val isNightMode = (style == "DARK" || style == "NEON")
            if (style != "SATELLITE") {
                try {
                    // Colores de edificios según el modo
                    val buildingColor = if (isNightMode) {
                        if (style == "NEON") "#090514" else "#1a1a2a" // Cyberpunk dark purple
                    } else "#e0e0e0"
                    val heightMult = if (isNightMode) 3.0 else 2.5

                    if (loadedStyle.styleLayerExists("3d-buildings")) {
                        loadedStyle.removeStyleLayer("3d-buildings")
                    }
                    if (loadedStyle.styleLayerExists("3d-buildings-roof")) {
                        loadedStyle.removeStyleLayer("3d-buildings-roof")
                    }

                    if (isNightMode) {
                        try {
                            loadedStyle.removeStyleImage(BUILDING_FACADE_IMAGE_ID)
                        } catch (e: Exception) {}
                        try {
                            val facadeBitmap = drawBuildingFacadeTexture(style == "NEON")
                            loadedStyle.addImage(BUILDING_FACADE_IMAGE_ID, facadeBitmap)
                        } catch (e: Exception) {}

                        // Cuerpo/Paredes de edificios con la textura de ventanas
                        loadedStyle.addLayer(fillExtrusionLayer("3d-buildings", "composite") {
                            sourceLayer("building")
                            filter(Expression.eq(Expression.get("extrude"), Expression.literal("true")))
                            minZoom(14.0)
                            fillExtrusionPattern(BUILDING_FACADE_IMAGE_ID)
                            fillExtrusionHeight(Expression.product(Expression.get("height"), Expression.literal(heightMult)))
                            fillExtrusionBase(Expression.product(Expression.get("min_height"), Expression.literal(heightMult)))
                            fillExtrusionOpacity(0.95)
                        })

                        // Techos de edificios con color sÃ³lido (para ocultar el patrÃ³n de ventanas en los techos)
                        loadedStyle.addLayerAbove(fillExtrusionLayer("3d-buildings-roof", "composite") {
                            sourceLayer("building")
                            filter(Expression.eq(Expression.get("extrude"), Expression.literal("true")))
                            minZoom(14.0)
                            fillExtrusionColor(buildingColor)
                            val heightExpr = Expression.product(Expression.get("height"), Expression.literal(heightMult))
                            val baseExpr = Expression.subtract(heightExpr, Expression.literal(0.15))
                            fillExtrusionHeight(heightExpr)
                            fillExtrusionBase(baseExpr)
                            fillExtrusionOpacity(0.95)
                        }, "3d-buildings")
                    } else {
                        // Modo DÃ­a (Sencillo, color sÃ³lido e iluminaciÃ³n nativa)
                        loadedStyle.addLayer(fillExtrusionLayer("3d-buildings", "composite") {
                            sourceLayer("building")
                            filter(Expression.eq(Expression.get("extrude"), Expression.literal("true")))
                            minZoom(14.0)
                            fillExtrusionColor(buildingColor)
                            fillExtrusionHeight(Expression.product(Expression.get("height"), Expression.literal(heightMult)))
                            fillExtrusionBase(Expression.product(Expression.get("min_height"), Expression.literal(heightMult)))
                            fillExtrusionOpacity(0.85)
                            fillExtrusionAmbientOcclusionIntensity(0.6)
                            fillExtrusionAmbientOcclusionRadius(4.0)
                        })
                    }
                    // ── Reflejos de ventanas en el suelo (solo modo noche) ───────
                    // Capa plana debajo de los edificios que simula la luz de ventanas
                    // derramándose sobre las aceras y el asfalto
                    if (isNightMode) {
                        val glowColor = if (style == "NEON") "#FF0055" else "#2a1800" // Cyberpunk Hot Pink glow
                        val glowOpacity = if (style == "NEON") 0.35 else 0.65
                        loadedStyle.addLayerBelow(fillLayer("building-glow", "composite") {
                            sourceLayer("building")
                            filter(Expression.eq(Expression.get("extrude"), Expression.literal("true")))
                            minZoom(14.0)
                            fillColor(glowColor)
                            fillOpacity(glowOpacity)
                        }, "3d-buildings")
                    }
                    
                    // ── Mejorar estética de Parques / Áreas Verdes ─────────────────
                    // Detectar primera capa de etiquetas para que nombres de calles queden visibles
                    val firstSymbolLayerId: String? = try {
                        loadedStyle.styleLayers.firstOrNull { it.type == "symbol" }?.id
                    } catch (e: Exception) { null }
                    val labelRef = firstSymbolLayerId ?: "3d-buildings"

                    val greenColor = if (isNightMode) {
                        if (style == "NEON") "#0A0014" else "#1a1a24" // Dark synthwave parks
                    } else "#b7e4c7"

                    try {
                        loadedStyle.addLayerBelow(fillLayer("custom-green-areas", "composite") {
                            sourceLayer("landuse")
                            filter(
                                Expression.any(
                                    Expression.eq(Expression.get("class"), Expression.literal("park")),
                                    Expression.eq(Expression.get("class"), Expression.literal("pitch")),
                                    Expression.eq(Expression.get("class"), Expression.literal("grass")),
                                    Expression.eq(Expression.get("class"), Expression.literal("forest")),
                                    Expression.eq(Expression.get("class"), Expression.literal("golf_course")),
                                    Expression.eq(Expression.get("class"), Expression.literal("garden"))
                                )
                            )
                            fillColor(greenColor)
                            fillOpacity(0.6)
                        }, labelRef)
                    } catch (e: Exception) {}

                    // ── Fondo (Agua y Tierra) para estilo Cyberpunk ──────────
                    if (style == "NEON") {
                        try {
                            loadedStyle.addLayerBelow(fillLayer("custom-water", "composite") {
                                sourceLayer("water")
                                fillColor("#000000") // Pitch black cyber water
                            }, labelRef)
                            loadedStyle.addLayerBelow(fillLayer("custom-landcover", "composite") {
                                sourceLayer("landcover")
                                fillColor("#000000") // Pitch black cyber ground
                            }, labelRef)
                        } catch (e: Exception) {}
                    }

                    val roadColor = if (isNightMode) {
                        if (style == "NEON") "#030308" else "#202020" // Pitch black roads
                    } else "#c0c0c0"

                    val casingColor = if (style == "NEON") "#00FFFF" else "#ffffff" // Cyan neon edges
                    val casingOpacity = if (style == "NEON") 0.7 else 0.35

                    // Borde blanco de carreteras (road casing) para modos oscuros
                    if (isNightMode) {
                        try {
                            loadedStyle.addLayerBelow(lineLayer("custom-roads-casing", "composite") {
                                sourceLayer("road")
                                filter(
                                    Expression.any(
                                        Expression.eq(Expression.get("class"), Expression.literal("motorway")),
                                        Expression.eq(Expression.get("class"), Expression.literal("trunk")),
                                        Expression.eq(Expression.get("class"), Expression.literal("primary")),
                                        Expression.eq(Expression.get("class"), Expression.literal("secondary")),
                                        Expression.eq(Expression.get("class"), Expression.literal("tertiary")),
                                        Expression.eq(Expression.get("class"), Expression.literal("street")),
                                        Expression.eq(Expression.get("class"), Expression.literal("street_limited"))
                                    )
                                )
                                lineColor(casingColor)
                                lineWidth(
                                    Expression.interpolate(
                                        Expression.linear(), Expression.zoom(),
                                        Expression.literal(12.0), Expression.literal(3.5),
                                        Expression.literal(18.0), Expression.literal(17.0)
                                    )
                                )
                                lineOpacity(casingOpacity)
                                lineCap(LineCap.ROUND)
                                lineJoin(LineJoin.ROUND)
                            }, labelRef)
                        } catch (e: Exception) {}
                    }

                    try {
                        loadedStyle.addLayerBelow(lineLayer("custom-roads", "composite") {
                            sourceLayer("road")
                            filter(
                                Expression.any(
                                    Expression.eq(Expression.get("class"), Expression.literal("motorway")),
                                    Expression.eq(Expression.get("class"), Expression.literal("trunk")),
                                    Expression.eq(Expression.get("class"), Expression.literal("primary")),
                                    Expression.eq(Expression.get("class"), Expression.literal("secondary")),
                                    Expression.eq(Expression.get("class"), Expression.literal("tertiary")),
                                    Expression.eq(Expression.get("class"), Expression.literal("street")),
                                    Expression.eq(Expression.get("class"), Expression.literal("street_limited"))
                                )
                            )
                            lineColor(roadColor)
                            lineWidth(
                                Expression.interpolate(
                                    Expression.linear(), Expression.zoom(),
                                    Expression.literal(12.0), Expression.literal(2.0),
                                    Expression.literal(18.0), Expression.literal(14.0)
                                )
                            )
                            lineCap(LineCap.ROUND)
                            lineJoin(LineJoin.ROUND)
                        }, labelRef)
                    } catch (e: Exception) {}

                } catch (e: Exception) {}
            }

            // ── Efectos Especiales ─────────────────
            if (isNightMode) {
                try {
                    // Añadir la textura holográfica de luces al estilo
                    loadedStyle.addImage("car-lights-glow", drawCarLightsGlow())
                    
                    loadedStyle.addLayer(com.mapbox.maps.extension.style.layers.generated.symbolLayer(CAR_LIGHTS_LAYER_ID, CAR_LIGHTS_SOURCE_ID) {
                        iconImage("car-lights-glow")
                        iconPitchAlignment(com.mapbox.maps.extension.style.layers.properties.generated.IconPitchAlignment.MAP)
                        iconRotationAlignment(com.mapbox.maps.extension.style.layers.properties.generated.IconRotationAlignment.MAP)
                        iconRotate(Expression.get("bearing"))
                        iconAllowOverlap(true)
                        iconIgnorePlacement(true)
                        // Tamaño adecuado para el nuevo bitmap
                        iconSize(AppSettings.glowIconSize.value.toDouble())
                    })
                } catch (e: Exception) {}
            }

            // ── Capas de Ruta Visuales ─────────────────
            // Añadidas AL FINAL para garantizar que se dibujen SOBRE carreteras y áreas verdes
            loadedStyle.addLayer(lineLayer(ROUTE_CASING_LAYER_ID, ROUTE_SOURCE_ID) {
                lineColor("#0D1B2A")
                lineWidth(14.0)
                lineCap(LineCap.ROUND)
                lineJoin(LineJoin.ROUND)
                lineOpacity(0.8)
            })
            loadedStyle.addLayer(lineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID) {
                lineColor(routeColorHex)
                lineWidth(9.0)
                lineCap(LineCap.ROUND)
                lineJoin(LineJoin.ROUND)
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
                    if (loc.hasBearing()) loc.bearing.toDouble() else gpsBearing
                )
            }

            // ── Heading-up SIEMPRE activo: brujula + GPS bearing ──────────────
            var lastIndicatorPos: com.mapbox.geojson.Point? = null
            var lastIndicatorBearing: Double = 0.0
            val updateGlow = {
                lastIndicatorPos?.let { pos ->
                    mapView.mapboxMap.getStyle { style ->
                        style.getSourceAs<com.mapbox.maps.extension.style.sources.generated.GeoJsonSource>("car-lights-source")?.featureCollection(
                            com.mapbox.geojson.FeatureCollection.fromFeature(
                                com.mapbox.geojson.Feature.fromGeometry(
                                    pos,
                                    com.google.gson.JsonObject().apply {
                                        addProperty("bearing", lastIndicatorBearing)
                                    }
                                )
                            )
                        )
                    }
                }
            }

            locPlugin.addOnIndicatorPositionChangedListener { point ->
                lastIndicatorPos = point
                updateGlow()
            }

            // addOnIndicatorBearingChangedListener usa GPS bearing cuando el auto
            // se mueve Y brujula del dispositivo cuando esta parado. Funciona siempre.
            // El mapa rota bajo el icono del auto, que siempre apunta hacia arriba.
            locPlugin.addOnIndicatorBearingChangedListener { newBearing ->
                lastIndicatorBearing = newBearing
                updateGlow()
                
                gpsBearing = newBearing
                lastKnownBearing = newBearing.toFloat()
                
                // No interrumpir si estamos en la intro o en modo test
                if (isIntroAnimating || AppSettings.isGpsSimulationMode.value) return@addOnIndicatorBearingChangedListener
                
                if (isFollowingLocation && hasInitializedPosition) {
                    val vp = mapView.viewport
                    if (vp.status is com.mapbox.maps.plugin.viewport.ViewportStatus.State) {
                        // Viewport activo — rotar suavemente la camara
                        mapView.camera.easeTo(
                            cameraOptions {
                                bearing(newBearing)
                                pitch(70.0)
                            },
                            com.mapbox.maps.plugin.animation.MapAnimationOptions
                                .mapAnimationOptions { duration(250) }
                        )
                    }
                }
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

                    // Activar viewport 3D de seguimiento tipo videojuego
                    // El padding inferior empuja el puck al 25% inferior de la pantalla
                    val vp = mapView.viewport
                    val followState = vp.makeFollowPuckViewportState(
                        FollowPuckViewportStateOptions.Builder()
                            .pitch(70.0)
                            .zoom(18.5)
                            .bearing(com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateBearing.SyncWithLocationPuck)
                            .padding(com.mapbox.maps.EdgeInsets(400.0, 0.0, 0.0, 0.0))
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

    // ── Callbacks de GPS y Simulación ─────────────────────────────────────────
    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                // Si la simulación está activa y este resultado NO es mock (no tiene proveedor "mock")
                // ignoramos el GPS real para que la simulación tome el control.
                val isMock = result.lastLocation?.provider == "mock"
                if (AppSettings.isGpsSimulationMode.value && !isMock) return 
                
                val loc = result.lastLocation ?: return
                if (loc.hasAccuracy() && loc.accuracy > 40f && !isMock) return

                // Servicios de rastreo
                com.tuusuario.carlauncher.services.RouteTracker.onLocationUpdate(loc)
                com.tuusuario.carlauncher.services.DashcamRouteTracker.onLocationUpdate(loc)

                NavigationState.currentLocation.value = loc
                if (loc.hasSpeed()) {
                    val spd = loc.speed * 3.6f
                    NavigationState.currentSpeedKmH.value = spd
                    if (spd < 1f) {
                        if (NavigationState.lastZeroSpeedTime == 0L) {
                            NavigationState.lastZeroSpeedTime = System.currentTimeMillis()
                        } else if (System.currentTimeMillis() - NavigationState.lastZeroSpeedTime > 2000L) {
                            NavigationState.isBraking.value = true
                        }
                    } else {
                        NavigationState.lastZeroSpeedTime = 0L
                        NavigationState.isBraking.value = false
                    }
                }

                // Propagar ubicación al provider de Mapbox (asegura movimiento del auto 3D)
                mockLocationProvider.updateLocation(
                    com.mapbox.geojson.Point.fromLngLat(loc.longitude, loc.latitude),
                    if (loc.hasBearing()) loc.bearing.toDouble() else gpsBearing
                )

                // ── Almacenar posición para la intro cinematográfica ─────────
                if (!hasInitializedPosition && loc.latitude != 0.0) {
                    hasInitializedPosition = true
                    val carPos     = Point.fromLngLat(loc.longitude, loc.latitude)
                    val carBearing = if (loc.hasBearing()) loc.bearing.toDouble() else gpsBearing

                    if (!hasPlayedIntro) {
                        hasPlayedIntro = true
                        // Bloquear INMEDIATAMENTE cualquier camera move del GPS
                        // antes de que el LaunchedEffect tenga oportunidad de arrancar
                        isIntroAnimating = true
                        pendingIntroPos = Pair(carPos, carBearing)
                    } else {
                        // Si ya se jugó el intro (rotación de pantalla), centrar directamente
                        val vp = mapView.viewport
                        val followState = vp.makeFollowPuckViewportState(
                            FollowPuckViewportStateOptions.Builder()
                                .pitch(70.0).zoom(18.5)
                                .bearing(com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateBearing.SyncWithLocationPuck)
                                .padding(com.mapbox.maps.EdgeInsets(400.0, 0.0, 0.0, 0.0))
                                .build()
                        )
                        vp.transitionTo(followState, vp.makeImmediateViewportTransition())
                    }
                }

                // Animar el LocationComponent del mapa es automático via el plugin;
                // aquí actualizamos la cámara si estamos en modo seguimiento sin ruta activa
                val isRouteActive = NavigationState.isRouteActive.value

                // ── Reactivar seguimiento automático cada 1 metro ─────────
                val userInteracting = isSavingFavorite || isPlacingDestination
                if (!isFollowingLocation && hasInitializedPosition && !userInteracting) {
                    val refLoc = locationWhenFollowDisabled
                    if (refLoc != null && loc.distanceTo(refLoc) >= 1f) {
                        isFollowingLocation = true
                        locationWhenFollowDisabled = null
                        autoCenterJob?.cancel()
                        val vp = mapView.viewport
                        val followState = vp.makeFollowPuckViewportState(
                            FollowPuckViewportStateOptions.Builder()
                                .pitch(70.0)
                                .zoom(18.5)
                                .bearing(com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateBearing.SyncWithLocationPuck)
                                .padding(com.mapbox.maps.EdgeInsets(400.0, 0.0, 0.0, 0.0))
                                .build()
                        )
                        vp.transitionTo(followState, vp.makeDefaultViewportTransition())
                    }
                }

                // No activar follow-mode durante la intro — interrumpiría la animación
                if (!isIntroAnimating && !isRouteActive && isFollowingLocation && hasInitializedPosition) {
                    val vp = mapView.viewport
                    if (vp.status !is com.mapbox.maps.plugin.viewport.ViewportStatus.State) {
                        val followState = vp.makeFollowPuckViewportState(
                            FollowPuckViewportStateOptions.Builder()
                                .pitch(70.0)
                                .zoom(18.5)
                                .bearing(com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateBearing.SyncWithLocationPuck)
                                .padding(com.mapbox.maps.EdgeInsets(400.0, 0.0, 0.0, 0.0))
                                .build()
                        )
                        vp.transitionTo(followState, vp.makeDefaultViewportTransition())
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
    } // Cierra el remember { ... }

    LaunchedEffect(AppSettings.isGpsSimulationMode.value) {
        if (AppSettings.isGpsSimulationMode.value) {
            var angle = 0.0
            // Usamos la posición actual como centro del círculo, o un valor por defecto si no hay fix
            val centerLat = NavigationState.currentLocation.value?.latitude ?: 10.998
            val centerLon = NavigationState.currentLocation.value?.longitude ?: -63.998
            val radiusDegrees = 0.002 // Aprox 200m de radio
            val speedMps = 15f / 3.6f // 15 km/h a metros/segundo

            while (isActive && AppSettings.isGpsSimulationMode.value) {
                // Calcular posición en el círculo
                val newLat = centerLat + radiusDegrees * Math.sin(angle)
                val newLon = centerLon + radiusDegrees * Math.cos(angle)
                
                // Calcular posición un poco atrás para obtener el bearing correcto
                val prevLat = centerLat + radiusDegrees * Math.sin(angle - 0.05)
                val prevLon = centerLon + radiusDegrees * Math.cos(angle - 0.05)
                
                val bearing = android.location.Location("").apply {
                    latitude = prevLat
                    longitude = prevLon
                }.bearingTo(android.location.Location("").apply {
                    latitude = newLat
                    longitude = newLon
                })

                val mockLoc = android.location.Location("mock").apply {
                    latitude = newLat
                    longitude = newLon
                    speed = speedMps
                    this.bearing = bearing
                    accuracy = 5f
                    time = System.currentTimeMillis()
                }

                // Inyectar en el callback como si fuera real
                locationCallback.onLocationResult(LocationResult.create(listOf(mockLoc)))
                
                // Intentar forzar la actualización del puck de Mapbox si el método existe
                try {
                    mapView.location.javaClass.getMethod("forceLocationUpdate", android.location.Location::class.java)
                        .invoke(mapView.location, mockLoc)
                } catch (e: Exception) {
                    // Ignorar si el método no existe en esta versión de Mapbox
                }
                
                // Incrementar ángulo para el próximo paso (determina qué tan rápido "gira")
                angle += 0.015
                
                // Pausa de simulación ~ 100ms igual que GPS real
                delay(100)
            }
        }
    }

    // ── Mover la cámara manualmente durante simulación ──────────────────────
    LaunchedEffect(NavigationState.currentLocation.value) {
        if (AppSettings.isGpsSimulationMode.value && !isIntroAnimating && isFollowingLocation) {
            val loc = NavigationState.currentLocation.value ?: return@LaunchedEffect
            mapView.camera.easeTo(
                cameraOptions {
                    center(Point.fromLngLat(loc.longitude, loc.latitude))
                    bearing(loc.bearing.toDouble())
                    pitch(70.0)
                    zoom(18.5)
                },
                com.mapbox.maps.plugin.animation.MapAnimationOptions
                    .mapAnimationOptions { duration(150) }
            )
        }
    }

    // ── GPS y seguimiento ────────────────────────────────────────────────────
    DisposableEffect(context) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val isBatterySaver = AppSettings.batterySaverMode.value
        val interval    = if (isBatterySaver) 3000L else 100L
        val minInterval = if (isBatterySaver) 2000L else 50L
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval)
            .setMinUpdateIntervalMillis(minInterval)
            .setMinUpdateDistanceMeters(0.5f)
            .setMaxUpdateDelayMillis(if (isBatterySaver) 4000L else 200L)
            .build()

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

    // ── Intro cinematográfica: espera a que el mapa esté listo ───────────────
    // Se dispara cuando mapReady y pendingIntroPos son ambos válidos.
    // Garantizamos que el estilo esté cargado antes de mover la cámara.
    LaunchedEffect(mapReady, pendingIntroPos) {
        val (carPos, carBearing) = pendingIntroPos ?: return@LaunchedEffect
        if (!mapReady) return@LaunchedEffect

        // Desactivar follow-mode y detener cualquier viewport activo
        // para que NADA pueda cancelar las animaciones de cámara que siguen
        isFollowingLocation = false
        mapView.viewport.idle()

        // isIntroAnimating ya es true (lo pusimos en onLocationResult)
        // pero aseguramos también aquí por si acaso
        isIntroAnimating = true

        try {
            // ── FASE 1: Frente inclinado del auto, acercamiento (zoom-in) ──
            // Posicionar un poco más lejos
            mapView.mapboxMap.setCamera(
                cameraOptions {
                    center(carPos)
                    zoom(19.5)
                    pitch(65.0)
                    bearing(carBearing + 165.0)
                }
            )
            // Hacer un easeTo acercándose lentamente para detallar el modelo
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
            delay(2000) // pausa para completar el acercamiento y que el usuario lo vea

            // ── FASE 2: Órbita suave alrededor del frente del auto ─────────
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
            delay(4200) // delay ligeramente mayor que duration para dejar terminar

            // ── FASE 3: Vuelo aéreo — vista del área (estilo águila) ───────
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

            // ── FASE 4: Volver a navegación — activar follow mode ──────────
            isFollowingLocation = true
            val vp = mapView.viewport
            val followState = vp.makeFollowPuckViewportState(
                FollowPuckViewportStateOptions.Builder()
                    .pitch(70.0)
                    .zoom(18.5)
                    .bearing(com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateBearing.SyncWithLocationPuck)
                    .padding(com.mapbox.maps.EdgeInsets(400.0, 0.0, 0.0, 0.0))
                    .build()
            )
            vp.transitionTo(
                followState,
                vp.makeDefaultViewportTransition(
                    DefaultViewportTransitionOptions.Builder().maxDurationMs(2500).build()
                )
            )
            delay(2600) // esperar que termine la transición antes de liberar el flag
        } finally {
            isIntroAnimating = false
            isFollowingLocation = true  // garantizar restauración en caso de error
            pendingIntroPos = null // Consumir el pendiente al final para evitar cancelación prematura
        }
    }

    // ── Animación dinámica de ventanas eliminada ─────────────────────────────
    // Las ventanas son fijas (seed constante). Se genera una sola vez al cargar
    // el estilo y no vuelve a cambiar, evitando el efecto de papel de regalos.


    // ── Re-centrar al cambiar orientación (portrait ↔ landscape) ────────────
    LaunchedEffect(configuration.orientation) {
        if (hasInitializedPosition && isFollowingLocation) {
            val vp = mapView.viewport
            val followState = vp.makeFollowPuckViewportState(
                FollowPuckViewportStateOptions.Builder()
                    .pitch(55.0)
                    .zoom(18.5)
                    .bearing(com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateBearing.SyncWithLocationPuck)
                    .padding(com.mapbox.maps.EdgeInsets(400.0, 0.0, 0.0, 0.0))
                    .build()
            )
            vp.transitionTo(followState, vp.makeImmediateViewportTransition())
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

    // Panel de ajuste de luces del auto en tiempo real
    val showGlowTuning = NavigationState.showGlowTuning.value

    // Regenerar el bitmap del glow y subirlo al estilo Mapbox inmediatamente
    val refreshGlowOnMap: () -> Unit = {
        mapView.mapboxMap.getStyle { style ->
            try {
                val bmp = drawCarLightsGlow()
                style.removeStyleImage("car-lights-glow")
                style.addImage("car-lights-glow", bmp)
                // Aplicar escala del modelo si cambió
                mapView.location.updateSettings {
                    locationPuck = getVehiclePuck(
                        context,
                        AppSettings.vehicleType.value,
                        AppSettings.customVehicleIconPath.value,
                        AppSettings.mapIconColor.value,
                        AppSettings.vehicle3DScale.value
                    )
                }
            } catch (e: Exception) { /* ignorar si el estilo no está listo */ }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {

        val isBraking = NavigationState.isBraking.value
        LaunchedEffect(isBraking) {
            refreshGlowOnMap()
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                mapView.apply {
                    // Prevenir el flashazo del planeta entero antes del primer fix GPS
                    mapView.mapboxMap.setCamera(cameraOptions {
                        zoom(14.0)
                        pitch(0.0)
                        bearing(0.0)
                    })

                    // Inicializar el mapa con el estilo correcto
                    applyMapStyle(currentStyle)

                    // Gestures: long-press para destino o para guardar favorito
                    gestures.addOnMapLongClickListener { point ->
                        val destPt = point
                        if (NavigationState.isRouteActive.value) {
                            // En ruta: abrir diálogo para guardar favorito en esta posición
                            favoriteLocationToSave = destPt
                            favoriteNameToSave     = ""
                            favoriteAddressToSave  = "${String.format("%.5f", destPt.latitude())}, ${String.format("%.5f", destPt.longitude())}"
                            selectedIconType       = "Star"
                            isSavingFavorite       = true
                            showSaveFavoriteDialog = true
                            autoCenterJob?.cancel()
                            return@addOnMapLongClickListener true
                        }

                        selectedDestination  = destPt
                        routeDistanceText    = ""
                        isFollowingLocation  = false
                        isPlacingDestination = true   // pausar auto-recentrado
                        autoCenterJob?.cancel()

                        // Anotar destino
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
                        true
                    }

                    // Click normal — limpiar UI
                    gestures.addOnMapClickListener { _ ->
                        focusManager.clearFocus()
                        searchResults = emptyList()
                        showFavorites = false
                        false
                    }

                    // Detectar gestos del usuario para desactivar el follow mode.
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
                            // Iniciar cuenta regresiva de 5s para recentrar (pausar si el usuario está interactuando)
                            autoCenterJob?.cancel()
                            autoCenterJob = coroutineScope.launch {
                                var elapsed = 0
                                while (elapsed < 5000) {
                                    delay(200)
                                    val userInteracting = isSavingFavorite || isPlacingDestination
                                    if (!userInteracting) elapsed += 200
                                }
                                val userInteracting = isSavingFavorite || isPlacingDestination
                                if (!userInteracting) {
                                    isFollowingLocation = true
                                    locationWhenFollowDisabled = null
                                    val vp = mapView.viewport
                                    val followState = vp.makeFollowPuckViewportState(
                                        FollowPuckViewportStateOptions.Builder()
                                            .pitch(55.0)
                                            .zoom(18.5)
                                            .bearing(com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateBearing.Constant(gpsBearing))
                                            .padding(com.mapbox.maps.EdgeInsets(400.0, 0.0, 0.0, 0.0))
                                            .build()
                                    )
                                    vp.transitionTo(followState, vp.makeDefaultViewportTransition())
                                }
                            }
                        }
                    })

                    // Primera posición — NO inicializar aquí para evitar
                    // que el mapa arranque en USA y luego haga glitch.
                    // La intro se maneja completamente en onLocationResult.
                    // Si ya hay posición previa (re-entrada), centrar sin intro.
                    NavigationState.currentLocation.value?.let { loc ->
                        if (!hasInitializedPosition && loc.latitude != 0.0) {
                            // Posicionar silenciosamente antes del primer fix de GPS
                            mapboxMap.setCamera(cameraOptions {
                                center(Point.fromLngLat(loc.longitude, loc.latitude))
                                zoom(20.0)   // muy cerca — evita ver USA
                                pitch(75.0)
                                bearing(gpsBearing + 180.0)
                            })
                        }
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

        // (ETA/speed HUD eliminado: ya existe el widget del velocimetro)

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

            // Botón guardar favorito (aparece en FABs cuando hay un pin sin ruta)
            AnimatedVisibility(
                visible = !NavigationState.isRouteActive.value
                    && favoriteLocationToSave != null
                    && selectedDestination == null
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        favoriteNameToSave    = ""
                        favoriteAddressToSave = favoriteLocationToSave?.let {
                            "${String.format("%.5f", it.latitude())}, ${String.format("%.5f", it.longitude())}"
                        } ?: ""
                        selectedIconType       = "Star"
                        isSavingFavorite       = true
                        showSaveFavoriteDialog = true
                        autoCenterJob?.cancel()
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                    contentColor   = Color(uiColor)
                ) {
                    Icon(Icons.Default.FavoriteBorder, "Guardar en favoritos", modifier = Modifier.size(18.dp))
                }
            }

            // Botón recentrar — primero vista aérea, luego 3D tras 2 segundos
            AnimatedVisibility(visible = !isFollowingLocation) {
                FloatingActionButton(
                    onClick = {
                        isFollowingLocation  = true
                        isPlacingDestination = false
                        locationWhenFollowDisabled = null
                        autoCenterJob?.cancel()
                        val vp = mapView.viewport

                        // Paso 1: vista aérea top-down para orientarse
                        val aerialState = vp.makeFollowPuckViewportState(
                            FollowPuckViewportStateOptions.Builder()
                                .pitch(0.0)
                                .zoom(17.0)
                                .bearing(com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateBearing.SyncWithLocationPuck)
                                .padding(com.mapbox.maps.EdgeInsets(0.0, 0.0, 0.0, 0.0))
                                .build()
                        )
                        vp.transitionTo(aerialState, vp.makeDefaultViewportTransition())

                        // Paso 2: volver a 3D inclinado después de 2 segundos
                        autoCenterJob = coroutineScope.launch {
                            delay(2000)
                            val followState = vp.makeFollowPuckViewportState(
                                FollowPuckViewportStateOptions.Builder()
                                    .pitch(70.0)
                                    .zoom(18.5)
                                    .bearing(com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateBearing.SyncWithLocationPuck)
                                    .padding(com.mapbox.maps.EdgeInsets(400.0, 0.0, 0.0, 0.0))
                                    .build()
                            )
                            vp.transitionTo(followState, vp.makeDefaultViewportTransition())
                        }
                    },
                    containerColor = Color(uiColor).copy(alpha = 0.9f),
                    contentColor   = Color.White
                ) {
                    Icon(Icons.Default.MyLocation, "Centrar")
                }
            }
        }

        // ── Panel de ajuste de luces en tiempo real ────
        // Panel deslizable desde el borde derecho
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 64.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            GlowTuningPanel(
                visible       = showGlowTuning,
                onDismiss     = { NavigationState.showGlowTuning.value = false },
                onGlowChanged = refreshGlowOnMap
            )
        }
        // Boton 🔦 para abrir/cerrar el panel (esquina superior izquierda, junto a la barra de busqueda)
        SmallFloatingActionButton(
            onClick        = { NavigationState.showGlowTuning.value = !NavigationState.showGlowTuning.value },
            modifier       = Modifier
                .align(Alignment.TopStart)
                .padding(top = 60.dp, start = 12.dp),
            containerColor = if (showGlowTuning) Color(0xFF00E5FF) else Color(0x99111122),
            contentColor   = Color.White
        ) {
            Text(if (showGlowTuning) "✕" else "🔦", fontSize = 16.sp)
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
                                selectedDestination  = null
                                routeDistanceText    = ""
                                isPlacingDestination = false
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
                onDismissRequest = {
                    showSaveFavoriteDialog = false
                    isSavingFavorite = false
                },
                title = { Text("Guardar Favorito", fontWeight = FontWeight.Bold) },
                text  = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Coordenadas de referencia
                        favoriteLocationToSave?.let { pt ->
                            Text(
                                "${String.format("%.5f", pt.latitude())}, ${String.format("%.5f", pt.longitude())}",
                                fontSize = 11.sp,
                                color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                        }
                        TextField(
                            value         = favoriteNameToSave,
                            onValueChange = { favoriteNameToSave = it },
                            label         = { Text("Nombre del lugar") },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth()
                        )
                        Text("Tipo de icono", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Star" to Icons.Default.Star, "Home" to Icons.Default.Home, "Work" to Icons.Default.Work).forEach { (type, icon) ->
                                FilterChip(
                                    selected = selectedIconType == type,
                                    onClick  = { selectedIconType = type },
                                    label    = { Text(type) },
                                    leadingIcon = { Icon(icon, null, modifier = Modifier.size(14.dp)) }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = favoriteNameToSave.isNotBlank(),
                        onClick = {
                            favoriteLocationToSave?.let { pt ->
                                favManager.addFavorite(
                                    PlaceResult(
                                        name      = favoriteNameToSave.trim(),
                                        address   = favoriteAddressToSave.ifEmpty { "Favorito guardado" },
                                        lat       = pt.latitude(),
                                        lon       = pt.longitude(),
                                        iconType  = selectedIconType
                                    )
                                )
                                showSaveFavoriteDialog = false
                                isSavingFavorite       = false
                                Toast.makeText(context, "✓ Guardado en favoritos", Toast.LENGTH_SHORT).show()
                                // Recentrar después de guardar
                                isFollowingLocation = true
                                locationWhenFollowDisabled = null
                                autoCenterJob?.cancel()
                                val vp = mapView.viewport
                                val followState = vp.makeFollowPuckViewportState(
                                    FollowPuckViewportStateOptions.Builder()
                                        .pitch(55.0).zoom(18.5)
                                        .bearing(com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateBearing.SyncWithLocationPuck)
                                        .padding(com.mapbox.maps.EdgeInsets(400.0, 0.0, 0.0, 0.0))
                                        .build()
                                )
                                vp.transitionTo(followState, vp.makeDefaultViewportTransition())
                            }
                        }
                    ) { Text("Guardar") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showSaveFavoriteDialog = false
                        isSavingFavorite       = false
                    }) { Text("Cancelar") }
                }
            )
        }

        // ── Botón guardar favorito en long-press desde ruta (pequeño, integrado en FABs) ──
        // (movido a la columna de FABs, eliminado el botón flotante central)
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
        "FLECHA"    -> "asset://models/Arrow.glb"
        "SEDAN"     -> "asset://models/Sedan.glb"
        "HATCHBACK" -> "asset://models/Hatchback.glb"
        "SPORT"     -> "asset://models/Sports.glb"
        // Si tienes "TAXI" en tus opciones futuras, aquí se mapeará
        "TAXI"      -> "asset://models/Taxi.glb"
        "STYLUS"    -> "asset://models/Stylus.glb"
        "CORSA"     -> "asset://models/Corsa.glb"
        else        -> null
    }

    if (modelAsset != null) {
        // Los modelos originales (Sedan, Hatchback) tienen un nodo interno en Blender con matriz x100.
        // Los nuevos modelos al exportarse desde Blender (sin modificar la escala de la escena) 
        // pueden quedar en escala de milímetros. Aplicamos un multiplicador de x500 para igualarlos
        // al tamaño de un auto real (~4.5 - 5 metros) dentro del mapa.
        val finalScale = if (vehicleType == "STYLUS" || vehicleType == "CORSA") scale * 500f else scale

        return com.mapbox.maps.plugin.LocationPuck3D(
            modelUri = modelAsset,
            // Escala vinculada al Slider de la UI (y ajustada por el multiplicador de base)
            modelScale = listOf(finalScale, finalScale, finalScale),
            // Los modelos GLB tienen el frente hacia atrás respecto a Mapbox;
            // se rota 180° en Z (yaw) para que el morro apunte en la dirección de movimiento.
            modelRotation = listOf(0f, 0f, 180f)
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

fun drawCarLightsGlow(
    carHalfW : Float = AppSettings.glowCarHalfW.value,
    headY    : Float = AppSettings.glowHeadY.value,
    headReach: Float = AppSettings.glowHeadReach.value,
    headSpread:Float = AppSettings.glowHeadSpread.value,
    tailY    : Float = AppSettings.glowTailY.value,
    tailRadius:Float = AppSettings.glowTailRadius.value,
    isBraking: Boolean = NavigationState.isBraking.value
): Bitmap {
    val w = 512
    val h = 512
    val cx = w / 2f  // 256 = center
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val beamColor   = android.graphics.Color.argb(100, 255, 255, 200)
    val transparent = android.graphics.Color.TRANSPARENT

    // === HEADLIGHTS (haz apuntando hacia arriba, Y=0) ===
    // Left headlight cone
    val leftPath = Path()
    leftPath.moveTo(cx - carHalfW - 2f, headY)           // origen faro izquierdo
    leftPath.lineTo(cx - carHalfW - headSpread, headReach) // abre hacia la izquierda
    leftPath.lineTo(cx - 5f, headReach)                    // cierra hacia el centro
    leftPath.close()
    paint.shader = LinearGradient(cx - carHalfW, headY, cx - carHalfW, headReach, beamColor, transparent, Shader.TileMode.CLAMP)
    canvas.drawPath(leftPath, paint)

    // Right headlight cone
    val rightPath = Path()
    rightPath.moveTo(cx + carHalfW + 2f, headY)           // origen faro derecho
    rightPath.lineTo(cx + carHalfW + headSpread, headReach) // abre hacia la derecha
    rightPath.lineTo(cx + 5f, headReach)                    // cierra hacia el centro
    rightPath.close()
    paint.shader = LinearGradient(cx + carHalfW, headY, cx + carHalfW, headReach, beamColor, transparent, Shader.TileMode.CLAMP)
    canvas.drawPath(rightPath, paint)

    // === TAILLIGHTS (pequeños glow rojos) ===
    val tailColor = android.graphics.Color.argb(180, 255, 15, 15)

    // Left taillight
    paint.shader = RadialGradient(cx - carHalfW, tailY, tailRadius, tailColor, transparent, Shader.TileMode.CLAMP)
    canvas.drawCircle(cx - carHalfW, tailY, tailRadius, paint)

    // Right taillight
    paint.shader = RadialGradient(cx + carHalfW, tailY, tailRadius, tailColor, transparent, Shader.TileMode.CLAMP)
    canvas.drawCircle(cx + carHalfW, tailY, tailRadius, paint)

    // === GROUND REFLECTION (pool de luz cálida bajo el auto) ===
    val reflColor = android.graphics.Color.argb(40, 255, 220, 150)
    paint.shader = RadialGradient(cx, 256f, 45f, reflColor, transparent, Shader.TileMode.CLAMP)
    canvas.drawCircle(cx, 256f, 45f, paint)

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

