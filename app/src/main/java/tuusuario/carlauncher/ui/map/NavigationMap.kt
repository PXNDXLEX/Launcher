package com.tuusuario.carlauncher.ui.map

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.view.MotionEvent
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.google.android.gms.location.*
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
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class PlaceResult(val name: String, val address: String, val lat: Double, val lon: Double, val iconType: String = "Star", val distanceKm: Double = 0.0)

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationMap(modifier: Modifier = Modifier, isFullScreen: Boolean = false, isDarkMode: Boolean = true) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current
    val vehicleType = AppSettings.vehicleType.value
    val uiColor = AppSettings.uiColor.value
    val mapIconColor = AppSettings.mapIconColor.value  // Color independiente del icono en el mapa
    val favManager = remember { FavoritesManager(context) }

    val coroutineScope = rememberCoroutineScope()
    val mapView = remember { MapView(context) }
    
    val isMapDarkMode = AppSettings.isMapDarkMode.value
    var isFollowingLocation by rememberSaveable { mutableStateOf(true) }
    var hasInitializedPosition by rememberSaveable { mutableStateOf(false) }
    
    var showSaveFavoriteDialog by remember { mutableStateOf(false) }
    var favoriteNameToSave by remember { mutableStateOf("") }
    var favoriteLocationToSave by remember { mutableStateOf<LatLng?>(null) }
    var selectedIconType by remember { mutableStateOf("Star") }

    // ELEVATED STATE: Now using NavigationState
    var selectedDestination by NavigationState.activeDestination
    var routeDistanceText by NavigationState.activeRouteDistance
    val currentRoutePoints = NavigationState.activeRoutePoints
    val activeRouteSteps = NavigationState.activeRouteSteps
    
    var showArrivalAlert by remember { mutableStateOf(false) }
    var isCalculatingRoute by remember { mutableStateOf(false) }
    var showStyleSelector by remember { mutableStateOf(false) }

    // Perspectiva 3D: activa cuando hay ruta activa Y se está siguiendo la ubicación
    val perspectiveProgress by animateFloatAsState(
        targetValue = if (NavigationState.isRouteActive.value && isFollowingLocation) 1f else 0f,
        animationSpec = tween(durationMillis = 700),
        label = "perspective_3d"
    )

    val currentStyle = AppSettings.mapStyle.value
    val routeColor = when (currentStyle) {
        "NEON" -> android.graphics.Color.parseColor("#FF9100") // Naranja Neón
        else -> android.graphics.Color.parseColor("#00B0FF")   // Azul Premium
    }

    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var carMarker by remember { mutableStateOf<Marker?>(null) }
    var destMarker by remember { mutableStateOf<Marker?>(null) }
    var routePolyline by remember { mutableStateOf<Polyline?>(null) }
    var historyPolyline by remember { mutableStateOf<Polyline?>(null) }
    var dashcamPolyline by remember { mutableStateOf<Polyline?>(null) }
    
    var animator: ValueAnimator? by remember { mutableStateOf(null) }
    var autoCenterJob by remember { mutableStateOf<Job?>(null) }
    
    var currentMapRotation by remember { mutableStateOf(0f) }
    var lastKnownBearing by remember { mutableStateOf(0f) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var showFavorites by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }

    val customIconPath = AppSettings.customVehicleIconPath.value
    LaunchedEffect(vehicleType, customIconPath, mapIconColor) {
        carMarker?.let { marker ->
            val iconBitmap = if (vehicleType == "CUSTOM" && customIconPath.isNotEmpty()) {
                try {
                    val file = java.io.File(customIconPath)
                    if (file.exists()) {
                        val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                        android.graphics.Bitmap.createScaledBitmap(bmp, 120, 120, true)
                    } else {
                        drawVehicleBitmap(context, "SEDAN", mapIconColor, 1.0f)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    drawVehicleBitmap(context, "SEDAN", mapIconColor, 1.0f)
                }
            } else {
                drawVehicleBitmap(context, vehicleType, mapIconColor, 1.0f)
            }
            val icon = org.maplibre.android.annotations.IconFactory.getInstance(context).fromBitmap(iconBitmap)
            marker.icon = icon
            mapLibreMap?.updateMarker(marker)
        }
    }

    // Función unificada para calcular/recalcular rutas con STEPS — motor: Valhalla OSM (gratuito, sin API key)
    val calculateRoute: (LatLng, LatLng) -> Unit = { startGeo, destGeo ->
        if (!isCalculatingRoute) {
            isCalculatingRoute = true
            coroutineScope.launch {
                routeDistanceText = "Calculando ruta..."
                try {
                    val body = """{"locations":[{"lon":${startGeo.longitude},"lat":${startGeo.latitude}},{"lon":${destGeo.longitude},"lat":${destGeo.latitude}}],"costing":"auto","directions_options":{"language":"es-ES"}}"""
                    val result = withContext(Dispatchers.IO) {
                        val url = URL("https://valhalla1.openstreetmap.de/route")
                        val conn = url.openConnection() as HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.setRequestProperty("Content-Type", "application/json")
                        conn.setRequestProperty("User-Agent", "CarLauncherApp")
                        conn.connectTimeout = 10000
                        conn.readTimeout = 10000
                        conn.doOutput = true
                        
                        try {
                            conn.outputStream.use { it.write(body.toByteArray()) }
                            val code = conn.responseCode
                            if (code >= 400) {
                                val err = conn.errorStream?.bufferedReader()?.readText() ?: "Error $code"
                                throw Exception(err)
                            }
                            conn.inputStream.bufferedReader().readText()
                        } finally {
                            conn.disconnect()
                        }
                    }

                    NavigationState.cachedRouteJson.value = result
                    val json = JSONObject(result)
                    val trip = json.getJSONObject("trip")
                    val summary = trip.getJSONObject("summary")
                    val distanceKm = summary.getDouble("length") // Valhalla devuelve km directamente
                    routeDistanceText = if (distanceKm > 1.0)
                        String.format("%.1f km restantes", distanceKm)
                    else
                        "${(distanceKm * 1000).toInt()} m restantes"

                    val legs = trip.getJSONArray("legs")
                    if (legs.length() > 0) {
                        val leg = legs.getJSONObject(0)
                        // Decodificar polyline de precisión 6 (formato Valhalla)
                        val decoded = decodePolyline6(leg.getString("shape"))
                        currentRoutePoints.clear()
                        currentRoutePoints.addAll(decoded)

                        // Parsear maniobras
                        activeRouteSteps.clear()
                        val maneuvers = leg.getJSONArray("maneuvers")
                        for (i in 0 until maneuvers.length()) {
                            val m = maneuvers.getJSONObject(i)
                            val (manType, manMod) = valhallaTypeToOsrmStyle(m.getInt("type"))
                            val shapeIdx = m.getInt("begin_shape_index")
                            val stepPoint = if (shapeIdx < decoded.size) decoded[shapeIdx] else startGeo
                            val names = m.optJSONArray("street_names")
                            val streetName = if (names != null && names.length() > 0) names.getString(0) else ""
                            activeRouteSteps.add(RouteStep(
                                maneuverType = manType,
                                modifier = manMod,
                                distance = m.getDouble("length") * 1000.0, // km → m
                                streetName = streetName,
                                maneuverLat = stepPoint.latitude,
                                maneuverLon = stepPoint.longitude
                            ))
                        }
                    }

                    NavigationState.isRouteActive.value = true
                    isFollowingLocation = true
                    autoCenterJob?.cancel()
                    // La vista de MapLibre reaccionará a isRouteActive y currentRoutePoints

                } catch (e: Exception) {
                    // LÓGICA OFFLINE: Usar caché si existe
                    if (NavigationState.cachedRouteJson.value != null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Sin Internet: Usando ruta en caché", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        val errorMessage = e.message ?: "Error desconocido"
                        val dist = startGeo.distanceTo(destGeo)
                        routeDistanceText = "Error: ${String.format("%.1f km (Directo)", dist / 1000)}"
                        currentRoutePoints.add(destGeo)
                        isFollowingLocation = true
                        autoCenterJob?.cancel()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Error de red: $errorMessage", Toast.LENGTH_LONG).show()
                        }
                    }
                } finally {
                    isCalculatingRoute = false
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(context) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val isBatterySaver = AppSettings.batterySaverMode.value
        val interval = if (isBatterySaver) 3000L else 1000L
        val minInterval = if (isBatterySaver) 2000L else 500L
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval)
            .setMinUpdateIntervalMillis(minInterval)
            .setMinUpdateDistanceMeters(1f)
            .setMaxUpdateDelayMillis(if (isBatterySaver) 4000L else 2000L)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                if (loc.hasAccuracy() && loc.accuracy > 40f) return

                val newGeo = LatLng(loc.latitude, loc.longitude)
                
                // RACING MODE: Bearing setup
                var targetBearing = lastKnownBearing
                if (loc.hasBearing() && loc.speed > 0.8f) {
                    targetBearing = loc.bearing
                    lastKnownBearing = targetBearing
                }

                // Registrar ruta automáticamente (ahora usa storage interno)
                com.tuusuario.carlauncher.services.RouteTracker.onLocationUpdate(loc)
                com.tuusuario.carlauncher.services.DashcamRouteTracker.onLocationUpdate(loc)

                NavigationState.currentLocation.value = loc
                val speedKmH = loc.speed * 3.6f
                if (loc.hasSpeed()) NavigationState.currentSpeedKmH.value = speedKmH

                if (carMarker != null) {
                    animator?.cancel()
                    val startGeo = carMarker!!.position
                    val startLat = startGeo.latitude
                    val startLon = startGeo.longitude
                    val deltaLat = newGeo.latitude - startLat
                    val deltaLon = newGeo.longitude - startLon
                    
                    val startBearing = lastKnownBearing // Marker doesn't have rotation, use last state
                    var diff = targetBearing - startBearing
                    if (diff > 180) diff -= 360
                    if (diff < -180) diff += 360
                    val deltaBearing = diff

                    animator = ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = 900 
                        interpolator = LinearInterpolator()
                        addUpdateListener { anim ->
                            val fraction = anim.animatedFraction
                            val currentPos = LatLng(startLat + (deltaLat * fraction), startLon + (deltaLon * fraction))
                            val currentBearing = startBearing + (deltaBearing * fraction)
                            
                            carMarker?.let {
                                it.position = currentPos
                                mapLibreMap?.updateMarker(it)
                                
                                if (isFollowingLocation) {
                                    val isRouteActive = NavigationState.isRouteActive.value
                                    val pitch = if (perspectiveProgress > 0f) 50.0 * perspectiveProgress else 0.0
                                    
                                    val camera = CameraPosition.Builder()
                                        .target(currentPos)
                                        .zoom(if (isRouteActive) 19.0 else 18.2)
                                        .bearing(if (isRouteActive) currentBearing.toDouble() else currentMapRotation.toDouble())
                                        .tilt(pitch)
                                        .build()
                                    
                                    mapLibreMap?.moveCamera(CameraUpdateFactory.newCameraPosition(camera))
                                }
                            }
                        }
                        start()
                    }
                }

                // LÓGICA DEL RECORRIDO, AUTO-RECÁLCULO Y TURN-INDICATORS
                if (currentRoutePoints.isNotEmpty() && selectedDestination != null) {
                    var minDistance = Float.MAX_VALUE
                    var closestIndex = 0
                    val searchLimit = minOf(100, currentRoutePoints.size) 
                    
                    for (i in 0 until searchLimit) {
                        val pt = currentRoutePoints[i]
                        val dist = FloatArray(1).also { android.location.Location.distanceBetween(loc.latitude, loc.longitude, pt.latitude, pt.longitude, it) }[0]
                        if (dist < minDistance) { minDistance = dist; closestIndex = i }
                    }

                    // AUTO-RECÁLCULO
                    if (minDistance > 100f && !isCalculatingRoute) {
                        calculateRoute(LatLng(loc.latitude, loc.longitude), selectedDestination!!)
                    } 
                    // SEGUIMIENTO
                    else if (closestIndex > 0 && minDistance < 60f) {
                        for (i in 0 until closestIndex) {
                            if (currentRoutePoints.isNotEmpty()) currentRoutePoints.removeAt(0)
                        }
                        // Polyline update is handled by AndroidView update block
                    }

                    // Actualizar steps (eliminar los que ya pasamos)
                    if (activeRouteSteps.isNotEmpty()) {
                        val nextStep = activeRouteSteps[0]
                        val distToStep = FloatArray(1).also { android.location.Location.distanceBetween(loc.latitude, loc.longitude, nextStep.maneuverLat, nextStep.maneuverLon, it) }[0]
                        if (distToStep < 20f) { // Hemos llegado al punto de maniobra
                            activeRouteSteps.removeAt(0)
                        }
                    }

                    // LLEGADA AL DESTINO
                    if (currentRoutePoints.size < 10) {
                        val endPt = currentRoutePoints.last()
                        val distToDest = FloatArray(1).also { android.location.Location.distanceBetween(loc.latitude, loc.longitude, endPt.latitude, endPt.longitude, it) }[0]
                        if (distToDest < 30f) { 
                            showArrivalAlert = true
                            NavigationState.clearActiveRoute()
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

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                mapView.apply {
                    getMapAsync { map ->
                        mapLibreMap = map
                        map.uiSettings.isAttributionEnabled = false
                        map.uiSettings.isLogoEnabled = false
                        map.uiSettings.isCompassEnabled = false
                        map.setStyle(Style.Builder().fromJson(getRasterStyleJson(currentStyle)))
                        
                        map.addOnMapClickListener { point ->
                            focusManager.clearFocus()
                            searchResults = emptyList()
                            showFavorites = false
                            false
                        }

                        map.addOnMapLongClickListener { point ->
                            if (NavigationState.isRouteActive.value) return@addOnMapLongClickListener true 
                            
                            selectedDestination = point
                            routeDistanceText = ""
                            isFollowingLocation = false 
                            
                            destMarker?.let { map.removeMarker(it) }
                            
                            val iconBitmap = drawCustomPin(uiColor)
                            val icon = org.maplibre.android.annotations.IconFactory.getInstance(ctx).fromBitmap(iconBitmap)
                            val markerOptions = MarkerOptions()
                                .position(point)
                                .icon(icon)
                                .title("Destino Seleccionado")
                            
                            destMarker = map.addMarker(markerOptions)
                            true
                        }

                        // Eventos de cámara para seguimiento automático
                        map.addOnCameraMoveStartedListener { reason ->
                            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                                if (NavigationState.isRouteActive.value) {
                                    // En ruta, evitar gestos bloqueándolos no es posible aquí directamente, pero podemos recentrar.
                                } else {
                                    isFollowingLocation = false
                                    autoCenterJob?.cancel()
                                    autoCenterJob = coroutineScope.launch {
                                        delay(6000)
                                        isFollowingLocation = true
                                    }
                                }
                            }
                        }
                    }
                }
            },
            update = { view ->
                val map = mapLibreMap ?: return@AndroidView
                val expectedStyle = if (currentStyle == "SATELLITE") "SATELLITE" else "MAPNIK"
                // Para mantenerlo simple, recargar estilo si cambia se omite aquí a menos que sea necesario.
                
                // --- RUTA ACTIVA ---
                if (NavigationState.isRouteActive.value && currentRoutePoints.isNotEmpty()) {
                    routePolyline?.let { map.removePolyline(it) }
                    val polylineOptions = PolylineOptions()
                        .addAll(currentRoutePoints.toList())
                        .color(routeColor)
                        .width(6f)
                    routePolyline = map.addPolyline(polylineOptions)
                } else if (!NavigationState.isRouteActive.value && routePolyline != null) {
                    map.removePolyline(routePolyline!!)
                    routePolyline = null
                }
                
                // --- RUTA HISTÓRICA ---
                val selectedRoute = NavigationState.selectedHistoryRoute.value
                val selectedSegment = NavigationState.selectedHistorySegment.value
                if (selectedRoute != null) {
                    historyPolyline?.let { map.removePolyline(it) }
                    val pts = if (selectedSegment != null) {
                        selectedSegment.points.map { LatLng(it.lat, it.lon) }
                    } else {
                        selectedRoute.segments.flatMap { s -> s.points.map { LatLng(it.lat, it.lon) } }
                    }
                    if (pts.isNotEmpty()) {
                        historyPolyline = map.addPolyline(PolylineOptions().addAll(pts).color(android.graphics.Color.parseColor("#4CAF50")).width(4f))
                        isFollowingLocation = false
                        map.cameraPosition = CameraPosition.Builder().target(pts.first()).zoom(16.0).tilt(0.0).bearing(0.0).build()
                    }
                } else if (historyPolyline != null) {
                    map.removePolyline(historyPolyline!!)
                    historyPolyline = null
                }
                
                // --- DASHCAM RUTA ---
                val selectedDashcamRoute = NavigationState.selectedDashcamRoute.value
                if (selectedDashcamRoute != null) {
                    dashcamPolyline?.let { map.removePolyline(it) }
                    val pts = selectedDashcamRoute.map { LatLng(it.lat, it.lon) }
                    if (pts.isNotEmpty()) {
                        dashcamPolyline = map.addPolyline(PolylineOptions().addAll(pts).color(android.graphics.Color.parseColor("#FF9800")).width(4f))
                        isFollowingLocation = false
                        map.cameraPosition = CameraPosition.Builder().target(pts.first()).zoom(17.0).tilt(0.0).bearing(0.0).build()
                    }
                } else if (dashcamPolyline != null) {
                    map.removePolyline(dashcamPolyline!!)
                    dashcamPolyline = null
                }
                
                // --- MARCADOR DESTINO ---
                val activeDest = NavigationState.activeDestination.value
                if (activeDest != null && destMarker == null) {
                    val iconBitmap = drawCustomPin(AppSettings.uiColor.value)
                    val icon = org.maplibre.android.annotations.IconFactory.getInstance(view.context).fromBitmap(iconBitmap)
                    destMarker = map.addMarker(MarkerOptions().position(activeDest).icon(icon))
                }
                
                // --- SEGUIMIENTO Y MARCADOR DE VEHÍCULO ---
                val loc = NavigationState.currentLocation.value
                if (loc != null) {
                    val pos = LatLng(loc.latitude, loc.longitude)
                    
                    if (carMarker == null) {
                        val iconBitmap = if (AppSettings.vehicleType.value == "CUSTOM" && AppSettings.customVehicleIconPath.value.isNotEmpty()) {
                            try {
                                val file = java.io.File(AppSettings.customVehicleIconPath.value)
                                if (file.exists()) {
                                    val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                                    android.graphics.Bitmap.createScaledBitmap(bmp, 120, 120, true)
                                } else { drawVehicleBitmap(view.context, "SEDAN", mapIconColor, 1.0f) }
                            } catch (e: Exception) { drawVehicleBitmap(view.context, "SEDAN", mapIconColor, 1.0f) }
                        } else { drawVehicleBitmap(view.context, vehicleType, mapIconColor, 1.0f) }
                        
                        val icon = org.maplibre.android.annotations.IconFactory.getInstance(view.context).fromBitmap(iconBitmap)
                        carMarker = map.addMarker(MarkerOptions().position(pos).icon(icon))
                        
                        // Centrar cámara la primera vez
                        val camera = CameraPosition.Builder().target(pos).zoom(18.2).build()
                        map.moveCamera(CameraUpdateFactory.newCameraPosition(camera))
                    }
                }
            }
        )

        // ── TOP OVERLAY: SEARCH (oculto cuando la ruta está activa) ──
        AnimatedVisibility(
            visible = !NavigationState.isRouteActive.value,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shadowElevation = 8.dp
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, null, tint = Color(uiColor), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) Text("¿A dónde vamos?", color = Color.Gray, fontSize = 14.sp)
                                androidx.compose.foundation.text.BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
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
                        onClick = { showFavorites = !showFavorites; searchResults = emptyList() },
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        contentColor = if (showFavorites) Color(uiColor) else MaterialTheme.colorScheme.onSurface
                    ) {
                        Icon(if (showFavorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, modifier = Modifier.size(20.dp))
                    }
                }

                AnimatedVisibility(visible = searchResults.isNotEmpty() || showFavorites, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(max = 280.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                    ) {
                        val list = if (showFavorites) favManager.getFavorites() else searchResults
                        LazyColumn {
                            items(list) { place ->
                                ListItem(
                                    headlineContent = { Text(place.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp) },
                                    supportingContent = { Text(place.address, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp) },
                                    leadingContent = { 
                                        Icon(
                                            when(place.iconType) {
                                                "Home" -> Icons.Default.Home
                                                "Work" -> Icons.Default.Work
                                                else -> Icons.Default.Place
                                            }, 
                                            null, 
                                            tint = Color(uiColor),
                                            modifier = Modifier.size(20.dp)
                                        ) 
                                    },
                                    modifier = Modifier.clickable {
                                        val dest = LatLng(place.lat, place.lon)
                                        selectedDestination = dest
                                        
                                        mapLibreMap?.let { map ->
                                            destMarker?.let { map.removeMarker(it) }
                                            
                                            val iconBitmap = drawCustomPin(uiColor)
                                            val icon = org.maplibre.android.annotations.IconFactory.getInstance(context).fromBitmap(iconBitmap)
                                            
                                            destMarker = map.addMarker(org.maplibre.android.annotations.MarkerOptions()
                                                .position(dest)
                                                .icon(icon)
                                                .title("Destino"))
                                                
                                            map.animateCamera(CameraUpdateFactory.newLatLng(dest))
                                        }
                                        
                                        searchResults = emptyList()
                                        showFavorites = false
                                        focusManager.clearFocus()
                                    },
                                    trailingContent = {
                                        if (place.distanceKm > 0) {
                                            Text(
                                                text = if (place.distanceKm < 1) 
                                                    "${(place.distanceKm * 1000).toInt()} m" 
                                                else 
                                                    String.format("%.1f km", place.distanceKm),
                                                fontSize = 11.sp,
                                                color = Color(uiColor).copy(alpha = 0.7f),
                                                fontWeight = FontWeight.Medium
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

        // ── NAVIGATION HUD (Turn-by-Turn) — compacto, esquina superior izquierda ──
        val nextStep = activeRouteSteps.firstOrNull()
        AnimatedVisibility(
            visible = NavigationState.isRouteActive.value && nextStep != null,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 8.dp)
        ) {
            val step = nextStep ?: return@AnimatedVisibility
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                shadowElevation = 6.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(uiColor).copy(alpha = 0.4f)),
                modifier = Modifier.widthIn(max = 220.dp)
            ) {
                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(36.dp).background(Color(uiColor).copy(alpha = 0.18f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(getManeuverIcon(step.modifier, step.maneuverType), null, tint = Color(uiColor), modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            if (step.distance > 1000) String.format("%.1f km", step.distance/1000) else "${step.distance.toInt()} m",
                            fontSize = 11.sp, color = Color(uiColor), fontWeight = FontWeight.Bold
                        )
                        Text(
                            step.streetName.ifEmpty { "Recto" },
                            fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // ── MAP CONTROL FABs (Derecha) ──
        Column(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Botón Norte
            AnimatedVisibility(visible = currentMapRotation != 0f) {
                SmallFloatingActionButton(
                    onClick = {
                        mapLibreMap?.animateCamera(CameraUpdateFactory.bearingTo(0.0))
                        currentMapRotation = 0f
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Icon(Icons.Default.Explore, "Norte", modifier = Modifier.size(20.dp))
                }
            }
            // Botón Selector de Estilos
            Box {
                FloatingActionButton(
                    onClick = { showStyleSelector = !showStyleSelector },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = when(currentStyle) {
                            "SATELLITE" -> Icons.Default.Public
                            "NEON" -> Icons.Default.Bolt
                            "DARK" -> Icons.Default.DarkMode
                            else -> Icons.Default.LightMode
                        }, 
                        contentDescription = "Estilo Mapa"
                    )
                }
                
                DropdownMenu(
                    expanded = showStyleSelector,
                    onDismissRequest = { showStyleSelector = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    DropdownMenuItem(
                        text = { Text("Día") },
                        leadingIcon = { Icon(Icons.Default.LightMode, null) },
                        onClick = { AppSettings.setMapStyle("LIGHT"); showStyleSelector = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Noche") },
                        leadingIcon = { Icon(Icons.Default.DarkMode, null) },
                        onClick = { AppSettings.setMapStyle("DARK"); showStyleSelector = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Neon Electric") },
                        leadingIcon = { Icon(Icons.Default.Bolt, null, tint = Color(0xFF00B0FF)) },
                        onClick = { AppSettings.setMapStyle("NEON"); showStyleSelector = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Satélite") },
                        leadingIcon = { Icon(Icons.Default.Public, null, tint = Color(0xFF4CAF50)) },
                        onClick = { AppSettings.setMapStyle("SATELLITE"); showStyleSelector = false }
                    )
                }
            }

            // Botón Centrar
            AnimatedVisibility(visible = !isFollowingLocation) {
                FloatingActionButton(
                    onClick = {
                        isFollowingLocation = true
                        autoCenterJob?.cancel()
                        NavigationState.currentLocation.value?.let {
                            mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLng(LatLng(it.latitude, it.longitude)))
                            currentMapRotation = 360f - lastKnownBearing
                        }
                    },
                    containerColor = Color(uiColor).copy(alpha = 0.9f),
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.MyLocation, "Centrar")
                }
            }
        }

        // ── BOTTOM OVERLAY: ROUTE ACTIONS ──
        AnimatedVisibility(
            visible = selectedDestination != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                        // Botón cancelar destino (X)
                        IconButton(
                            onClick = {
                                selectedDestination = null
                                routeDistanceText = ""
                                mapLibreMap?.let { map ->
                                    destMarker?.let { map.removeMarker(it) }
                                    destMarker = null
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(14.dp))
                        ) {
                            Icon(Icons.Default.Close, "Cancelar", tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        // Botón INICIAR ruta
                        Button(
                            onClick = {
                                NavigationState.currentLocation.value?.let { loc ->
                                    calculateRoute(LatLng(loc.latitude, loc.longitude), selectedDestination!!)
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(uiColor)),
                            modifier = Modifier.height(44.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp)
                        ) {
                            Icon(Icons.Default.Directions, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("INICIAR", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    } else {
                        // Botón PARAR ruta
                        Button(
                            onClick = {
                                NavigationState.clearActiveRoute()
                                selectedDestination = null
                                mapLibreMap?.let { map ->
                                    routePolyline?.let { map.removePolyline(it) }
                                    routePolyline = null
                                    destMarker?.let { map.removeMarker(it) }
                                    destMarker = null
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.height(44.dp),
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

        AnimatedVisibility(
            visible = showArrivalAlert,
            enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = 500f)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            LaunchedEffect(showArrivalAlert) {
                if (showArrivalAlert) {
                    delay(4000)
                    showArrivalAlert = false
                }
            }
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)),
                elevation = CardDefaults.cardElevation(16.dp),
                modifier = Modifier.padding(32.dp).fillMaxWidth(0.8f)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp).fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("¡Has Llegado!", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Has alcanzado tu destino.", style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onPrimaryContainer))
                }
            }
        }

        if (showSaveFavoriteDialog) {
            AlertDialog(
                onDismissRequest = { showSaveFavoriteDialog = false },
                title = { Text("Guardar Favorito") },
                text = {
                    Column {
                        TextField(value = favoriteNameToSave, onValueChange = { favoriteNameToSave = it }, label = { Text("Nombre") })
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Star", "Home", "Work").forEach { type ->
                                FilterChip(
                                    selected = selectedIconType == type,
                                    onClick = { selectedIconType = type },
                                    label = { Text(type) }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        favoriteLocationToSave?.let {
                            favManager.addFavorite(PlaceResult(favoriteNameToSave, "Favorito guardado", it.latitude, it.longitude, selectedIconType))
                            showSaveFavoriteDialog = false
                            Toast.makeText(context, "Guardado", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Guardar") }
                }
            )
        }
    }
}

private fun getManeuverIcon(modifier: String, type: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when {
        type.contains("arrive") -> Icons.Default.Flag
        modifier.contains("left") -> if (modifier.contains("sharp")) Icons.Default.TurnSharpLeft else Icons.Default.TurnLeft
        modifier.contains("right") -> if (modifier.contains("sharp")) Icons.Default.TurnSharpRight else Icons.Default.TurnRight
        modifier.contains("uturn") -> Icons.Default.UTurnLeft
        modifier.contains("straight") -> Icons.Default.Straight
        else -> Icons.Default.Navigation
    }
}

suspend fun searchPlaces(query: String, currentLoc: android.location.Location?): List<PlaceResult> = withContext(Dispatchers.IO) {
    try {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val urlStr = buildString {
            append("https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&addressdetails=1&limit=500")
            if (currentLoc != null) {
                val lat = currentLoc.latitude
                val lon = currentLoc.longitude
                // Quitamos bounded=1 para evitar que Nominatim excluya resultados locales por errores de bounding box
                append("&viewbox=${lon - 3.0},${lat + 3.0},${lon + 3.0},${lat - 3.0}&lat=$lat&lon=$lon")
            }
        }
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "CarLauncherApp")
        val response = conn.inputStream.bufferedReader().readText()
        val array = JSONArray(response)
        val list = mutableListOf<PlaceResult>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val lat = obj.getDouble("lat")
            val lon = obj.getDouble("lon")
            
            var distKm = 0.0
            if (currentLoc != null) {
                val results = FloatArray(1)
                android.location.Location.distanceBetween(currentLoc.latitude, currentLoc.longitude, lat, lon, results)
                distKm = (results[0] / 1000.0).toDouble()
            }

            // Filtrar localmente a 300km y descartar el resto al instante
            if (currentLoc == null || distKm <= 300.0) {
                list.add(PlaceResult(
                    obj.getString("display_name").split(",")[0], 
                    obj.getString("display_name"), 
                    lat, 
                    lon,
                    distanceKm = distKm
                ))
            }
        }
        
        // Ordenar por distancia
        list.sortBy { it.distanceKm }
        
        list
    } catch (e: Exception) { emptyList() }
}

/**
 * Renders a vehicle bitmap for use as a map marker.
 * For SEDAN and HATCHBACK types, uses the Vector Drawable XML resources (ic_sedan.xml / ic_hatchback.xml)
 * tinted with [color]. Other types fall back to Canvas-drawn primitives.
 * [color] should come from AppSettings.mapIconColor — independent of the UI accent color.
 */
fun drawVehicleBitmap(context: Context, type: String, color: Int, heightScale: Float = 1.0f): Bitmap {
    val width = 140
    val height = (140 * heightScale).toInt() 
    // Altura compensada para que no se vea "aplastado" por la inclinación 3D

    // Try to render from Vector Drawable for SEDAN and HATCHBACK
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

    val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(b)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = color
    p.setShadowLayer(8f, 0f, 4f, 0x80000000.toInt())

    when (type) {
        "SPORT" -> {
            val path = Path()
            path.moveTo(width/2f, 10f)
            path.lineTo(20f, height.toFloat() - 10f)
            path.lineTo(width/2f, height.toFloat() - 25f)
            path.lineTo(width.toFloat() - 20f, height.toFloat() - 10f)
            path.close()
            canvas.drawPath(path, p)
        }
        "TRUCK" -> canvas.drawRect(30f, 20f, width - 30f, height.toFloat() - 10f, p)
        else -> {
            val path = Path()
            path.moveTo(width/2f, 15f)
            path.lineTo(25f, height.toFloat() - 15f)
            path.lineTo(width/2f, height.toFloat() - 30f)
            path.lineTo(width.toFloat() - 25f, height.toFloat() - 15f)
            path.close()
            canvas.drawPath(path, p)
        }
    }
    return b
}

fun drawCustomPin(color: Int): Bitmap {
    val b = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(b)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = color
    canvas.drawCircle(40f, 30f, 25f, p)
    val path = Path()
    path.moveTo(15f, 35f)
    path.lineTo(40f, 75f)
    path.lineTo(65f, 35f)
    path.close()
    canvas.drawPath(path, p)
    p.color = android.graphics.Color.WHITE
    canvas.drawCircle(40f, 30f, 10f, p)
    return b
}

/**
 * Decodifica una polyline con precisión 6 (formato nativo de Valhalla).
 * Similar al algoritmo de Google Maps pero con factor 1e6 en vez de 1e5.
 */
fun decodePolyline6(encoded: String): List<LatLng> {
    val points = mutableListOf<LatLng>()
    var index = 0
    var lat = 0
    var lng = 0
    while (index < encoded.length) {
        var b: Int
        var shift = 0
        var result = 0
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        val dLat = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
        lat += dLat
        shift = 0
        result = 0
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        val dLng = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
        lng += dLng
        points.add(LatLng(lat.toDouble() / 1e6, lng.toDouble() / 1e6))
    }
    return points
}

/**
 * Convierte el tipo entero de maniobra de Valhalla al par (maneuverType, modifier)
 * que usa el HUD de giro existente (formato OSRM).
 * Referencia: https://valhalla.github.io/valhalla/api/turn-by-turn/api-reference/#maneuver-types
 */
fun valhallaTypeToOsrmStyle(type: Int): Pair<String, String> = when (type) {
    1, 2, 3   -> "depart"   to "straight"
    4, 5, 6   -> "arrive"   to "straight"
    7, 8      -> "continue" to "straight"
    9         -> "turn"     to "slight right"
    10        -> "turn"     to "right"
    11        -> "turn"     to "sharp right"
    12, 13    -> "turn"     to "uturn"
    14        -> "turn"     to "sharp left"
    15        -> "turn"     to "left"
    16        -> "turn"     to "slight left"
    17        -> "turn"     to "straight"     // ramp straight
    18        -> "turn"     to "right"        // ramp right
    19        -> "turn"     to "left"         // ramp left
    20        -> "turn"     to "slight right" // exit right
    21        -> "turn"     to "slight left"  // exit left
    24        -> "roundabout" to "right"
    25        -> "exit roundabout" to "straight"
    else      -> "turn"     to "straight"
}

fun getRasterStyleJson(style: String): String {
    val tileUrl = when (style) {
        "SATELLITE" -> "https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}" // ArcGIS uses Z/Y/X? Actually it varies.
        "DARK" -> "https://a.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png"
        "NEON" -> "https://b.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png"
        else -> "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
    }
    return """
    {
      "version": 8,
      "sources": {
        "raster-tiles": {
          "type": "raster",
          "tiles": ["$tileUrl"],
          "tileSize": 256,
          "attribution": "© OpenStreetMap contributors"
        }
      },
      "layers": [
        {
          "id": "background",
          "type": "background",
          "paint": { "background-color": "#AAD3DF" }
        },
        {
          "id": "simple-tiles",
          "type": "raster",
          "source": "raster-tiles",
          "minzoom": 0,
          "maxzoom": 20
        }
      ]
    }
    """.trimIndent()
}
