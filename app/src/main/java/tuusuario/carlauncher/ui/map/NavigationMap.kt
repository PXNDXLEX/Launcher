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
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory 
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.Polyline
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
    var favoriteLocationToSave by remember { mutableStateOf<GeoPoint?>(null) }
    var selectedIconType by remember { mutableStateOf("Star") }

    // ELEVATED STATE: Now using NavigationState
    var selectedDestination by NavigationState.activeDestination
    var routeDistanceText by NavigationState.activeRouteDistance
    val currentRoutePoints = NavigationState.activeRoutePoints
    val activeRouteSteps = NavigationState.activeRouteSteps
    
    var showArrivalAlert by remember { mutableStateOf(false) }
    var isCalculatingRoute by remember { mutableStateOf(false) }

    var carMarker by remember { mutableStateOf<Marker?>(null) }
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
                        drawVehicleBitmap(context, "SEDAN", mapIconColor)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    drawVehicleBitmap(context, "SEDAN", mapIconColor)
                }
            } else {
                drawVehicleBitmap(context, vehicleType, mapIconColor)
            }
            marker.icon = android.graphics.drawable.BitmapDrawable(context.resources, iconBitmap)
            mapView.invalidate()
        }
    }

    LaunchedEffect(Unit) { Configuration.getInstance().userAgentValue = context.packageName }

    // Función unificada para calcular/recalcular rutas con STEPS (indicadores de giro)
    val calculateRoute: (GeoPoint, GeoPoint) -> Unit = { startGeo, destGeo ->
        if (!isCalculatingRoute) {
            isCalculatingRoute = true
            coroutineScope.launch {
                routeDistanceText = "Calculando ruta..."
                try {
                    // Añadimos steps=true para obtener las maniobras
                    val urlStr = "https://router.project-osrm.org/route/v1/driving/${startGeo.longitude},${startGeo.latitude};${destGeo.longitude},${destGeo.latitude}?overview=full&geometries=geojson&steps=true"
                    val result = withContext(Dispatchers.IO) {
                        val conn = URL(urlStr).openConnection() as HttpURLConnection
                        conn.connectTimeout = 4000
                        conn.inputStream.bufferedReader().readText()
                    }
                    
                    NavigationState.cachedRouteJson.value = result // Cache for offline use
                    val json = JSONObject(result)
                    val routes = json.getJSONArray("routes")
                    if (routes.length() > 0) {
                        val route = routes.getJSONObject(0)
                        val distanceMeters = route.getDouble("distance")
                        routeDistanceText = if (distanceMeters > 1000) String.format("%.1f km restantes", distanceMeters / 1000) else "${distanceMeters.toInt()} m restantes"

                        // Parsear Puntos
                        val coordinates = route.getJSONObject("geometry").getJSONArray("coordinates")
                        currentRoutePoints.clear()
                        for (i in 0 until coordinates.length()) {
                            val pt = coordinates.getJSONArray(i)
                            currentRoutePoints.add(GeoPoint(pt.getDouble(1), pt.getDouble(0)))
                        }
                        
                        // Parsear Steps (Instrucciones de giro)
                        activeRouteSteps.clear()
                        val legs = route.getJSONArray("legs")
                        if (legs.length() > 0) {
                            val steps = legs.getJSONObject(0).getJSONArray("steps")
                            for (i in 0 until steps.length()) {
                                val step = steps.getJSONObject(i)
                                val maneuver = step.getJSONObject("maneuver")
                                val loc = maneuver.getJSONArray("location")
                                activeRouteSteps.add(RouteStep(
                                    maneuverType = maneuver.getString("type"),
                                    modifier = maneuver.optString("modifier", "straight"),
                                    distance = step.getDouble("distance"),
                                    streetName = step.optString("name", "Calle"),
                                    maneuverLat = loc.getDouble(1),
                                    maneuverLon = loc.getDouble(0)
                                ))
                            }
                        }

                        NavigationState.isRouteActive.value = true
                        
                        mapView.overlays.removeAll { it is Polyline && it.id == "ROUTE_MAIN" }
                        val polyline = Polyline(mapView).apply {
                            id = "ROUTE_MAIN"
                            setPoints(currentRoutePoints.toList())
                            outlinePaint.color = android.graphics.Color.parseColor("#007AFF")
                            outlinePaint.strokeWidth = 20f
                            outlinePaint.strokeCap = Paint.Cap.ROUND
                            outlinePaint.strokeJoin = Paint.Join.ROUND
                        }
                        mapView.overlays.add(0, polyline)
                        isFollowingLocation = true 
                        autoCenterJob?.cancel()
                        mapView.invalidate()
                    }
                } catch (e: Exception) { 
                    // LÓGICA OFFLINE: Usar caché si existe
                    if (NavigationState.cachedRouteJson.value != null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Sin Internet: Usando ruta en caché", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        val dist = startGeo.distanceToAsDouble(destGeo)
                        routeDistanceText = "Offline: ${String.format("%.1f km (Directo)", dist / 1000)}"
                        currentRoutePoints.clear()
                        currentRoutePoints.add(startGeo)
                        currentRoutePoints.add(destGeo)

                        mapView.overlays.removeAll { it is Polyline && it.id == "ROUTE_MAIN" }
                        val polyline = Polyline(mapView).apply {
                            id = "ROUTE_MAIN"
                            setPoints(currentRoutePoints.toList())
                            outlinePaint.color = android.graphics.Color.parseColor("#007AFF")
                            outlinePaint.strokeWidth = 12f
                            outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 20f), 0f)
                        }
                        mapView.overlays.add(0, polyline)
                        isFollowingLocation = true 
                        autoCenterJob?.cancel()
                        mapView.invalidate()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Sin Internet: Activando Ruta Directa", Toast.LENGTH_LONG).show()
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
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
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

                val newGeo = GeoPoint(loc.latitude, loc.longitude)
                
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
                val isStationary = speedKmH < 3f 

                if (carMarker == null) {
                    carMarker = Marker(mapView).apply {
                        val iconBitmap = if (AppSettings.vehicleType.value == "CUSTOM" && AppSettings.customVehicleIconPath.value.isNotEmpty()) {
                            try {
                                val file = java.io.File(AppSettings.customVehicleIconPath.value)
                                if (file.exists()) {
                                    val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                                    android.graphics.Bitmap.createScaledBitmap(bmp, 120, 120, true)
                                } else { drawVehicleBitmap(context, "SEDAN", mapIconColor) }
                            } catch (e: Exception) { drawVehicleBitmap(context, "SEDAN", mapIconColor) }
                        } else { drawVehicleBitmap(context, vehicleType, mapIconColor) }
                        
                        icon = BitmapDrawable(context.resources, iconBitmap)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        isFlat = false // Volvemos a false para controlar nosotros la rotación
                        position = newGeo
                        
                        // Calculamos el ángulo real en pantalla
                        val initialMapRot = if (!isStationary) 360f - targetBearing else currentMapRotation
                        rotation = (targetBearing + initialMapRot) % 360f 
                    }
                    mapView.overlays.add(carMarker)
                    
                    if (isFollowingLocation || !hasInitializedPosition) {
                        mapView.controller.setCenter(newGeo)
                        mapView.setMapCenterOffset(0, mapView.height / 4)
                        if (!hasInitializedPosition) {
                            mapView.controller.setZoom(18.5)
                            hasInitializedPosition = true
                        }
                        if (loc.hasBearing() && !isStationary) {
                            val newOrientation = 360f - targetBearing
                            mapView.mapOrientation = newOrientation
                            currentMapRotation = newOrientation
                        }
                    }
                } else {
                    animator?.cancel()
                    val startGeo = carMarker!!.position
                    val startLat = startGeo.latitude
                    val startLon = startGeo.longitude
                    val deltaLat = newGeo.latitude - startLat
                    val deltaLon = newGeo.longitude - startLon

                    // RACING MODE: El mapa rota
                    var startMapRot = currentMapRotation % 360f
                    if (startMapRot < 0) startMapRot += 360f
                    val targetMapRot = if (!isStationary) 360f - targetBearing else startMapRot
                    var deltaMapRot = targetMapRot - startMapRot
                    if (deltaMapRot > 180f) deltaMapRot -= 360f
                    if (deltaMapRot < -180f) deltaMapRot += 360f

                    // 🔥 LA SOLUCIÓN: Interpolar la rotación exacta en la pantalla
                    var startScreenRot = carMarker!!.rotation % 360f
                    if (startScreenRot < 0) startScreenRot += 360f
                    var targetScreenRot = (targetBearing + targetMapRot) % 360f
                    var deltaScreenRot = targetScreenRot - startScreenRot
                    if (deltaScreenRot > 180f) deltaScreenRot -= 360f
                    if (deltaScreenRot < -180f) deltaScreenRot += 360f

                    animator = ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = 500 
                        interpolator = LinearInterpolator()
                        addUpdateListener { anim ->
                            val fraction = anim.animatedFraction
                            val currentPos = GeoPoint(startLat + (deltaLat * fraction), startLon + (deltaLon * fraction))
                            
                            carMarker!!.position = currentPos
                            
                            // El auto siempre apuntará bien, ¡incluso si mueves el mapa con el dedo!
                            carMarker!!.rotation = startScreenRot + (deltaScreenRot * fraction)
                            
                            if (isFollowingLocation) {
                                mapView.controller.setCenter(currentPos)
                                mapView.setMapCenterOffset(0, mapView.height / 4)
                                var interpMapRot = startMapRot + (deltaMapRot * fraction)
                                interpMapRot %= 360f
                                if (interpMapRot < 0) interpMapRot += 360f
                                mapView.mapOrientation = interpMapRot
                                currentMapRotation = interpMapRot
                            }
                            mapView.invalidate()
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
                        calculateRoute(GeoPoint(loc.latitude, loc.longitude), selectedDestination!!)
                    } 
                    // SEGUIMIENTO
                    else if (closestIndex > 0 && minDistance < 60f) {
                        for (i in 0 until closestIndex) {
                            if (currentRoutePoints.isNotEmpty()) currentRoutePoints.removeAt(0)
                        }
                        val mainPoly = mapView.overlays.find { it is Polyline && it.id == "ROUTE_MAIN" } as? Polyline
                        mainPoly?.setPoints(currentRoutePoints.toList())
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
                            mapView.overlays.removeAll { it is Polyline || (it is Marker && it.id == "DEST") }
                            mapView.invalidate()
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
                    setTileSource(TileSourceFactory.MAPNIK)
                    isTilesScaledToDpi = true
                    
                    val lastLoc = NavigationState.currentLocation.value
                    if (lastLoc != null) {
                        controller.setCenter(GeoPoint(lastLoc.latitude, lastLoc.longitude))
                        controller.setZoom(18.5)
                    } else {
                        controller.setCenter(GeoPoint(10.996, -63.804)) 
                        controller.setZoom(15.0)
                    }
                    
                    addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
                        val h = bottom - top
                        if (h > 0) { setMapCenterOffset(0, h / 4) }
                    }

                    // Rotación manual habilitada: auto-vuelve al bearing GPS tras 6s de inactividad
                    val rotationGestureOverlay = RotationGestureOverlay(mapView).apply {
                        isEnabled = true
                    }
                    overlays.add(rotationGestureOverlay)
                    
                    setOnTouchListener { _, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                                isFollowingLocation = false
                                autoCenterJob?.cancel()
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                autoCenterJob?.cancel()
                                autoCenterJob = coroutineScope.launch {
                                    delay(6000)
                                    isFollowingLocation = true
                                    NavigationState.currentLocation.value?.let {
                                        controller.animateTo(GeoPoint(it.latitude, it.longitude))
                                        mapOrientation = 360f - lastKnownBearing
                                        currentMapRotation = 360f - lastKnownBearing
                                    }
                                }
                            }
                        }
                        false 
                    }

                    addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            if (event != null && (event.x != 0 || event.y != 0)) {
                                if (isFollowingLocation) {
                                    isFollowingLocation = false
                                    autoCenterJob?.cancel()
                                }
                                // Resetear auto-recentrado en cada scroll
                                autoCenterJob?.cancel()
                                autoCenterJob = coroutineScope.launch {
                                    delay(6000)
                                    isFollowingLocation = true
                                    NavigationState.currentLocation.value?.let {
                                        controller.animateTo(GeoPoint(it.latitude, it.longitude))
                                        mapOrientation = 360f - lastKnownBearing
                                        currentMapRotation = 360f - lastKnownBearing
                                    }
                                }
                            }
                            return false
                        }
                        override fun onZoom(event: ZoomEvent?): Boolean {
                            autoCenterJob?.cancel()
                            autoCenterJob = coroutineScope.launch {
                                delay(6000)
                                isFollowingLocation = true
                                NavigationState.currentLocation.value?.let {
                                    mapOrientation = 360f - lastKnownBearing
                                    currentMapRotation = 360f - lastKnownBearing
                                }
                            }
                            return false
                        }
                    })

                    setMultiTouchControls(true)
                    setBuiltInZoomControls(false)
                    setHasTransientState(true)

                    val mReceive = object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            focusManager.clearFocus()
                            searchResults = emptyList()
                            showFavorites = false
                            return false
                        }
                        override fun longPressHelper(p: GeoPoint?): Boolean {
                            if (NavigationState.isRouteActive.value) return true 
                            
                            if (p != null) {
                                selectedDestination = p
                                routeDistanceText = ""
                                isFollowingLocation = false 
                                overlays.removeAll { it is Marker && it.id == "DEST" }
                                
                                val marker = Marker(mapView).apply {
                                    position = p
                                    id = "DEST"
                                    icon = BitmapDrawable(ctx.resources, drawCustomPin(uiColor))
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    title = "Destino Seleccionado"
                                    setOnMarkerClickListener { _, _ ->
                                        favoriteLocationToSave = p
                                        favoriteNameToSave = "Mi Lugar"
                                        showSaveFavoriteDialog = true
                                        true
                                    }
                                }
                                overlays.add(marker)
                                invalidate()
                            }
                            return true
                        }
                    }
                    overlays.add(MapEventsOverlay(mReceive))
                }
            },
            update = { view ->
                if (isMapDarkMode) {
                    val inverseMatrix = android.graphics.ColorMatrix(floatArrayOf(
                        -1.0f, 0.0f, 0.0f, 0.0f, 255f,
                        0.0f, -1.0f, 0.0f, 0.0f, 255f,
                        0.0f, 0.0f, -1.0f, 0.0f, 255f,
                        0.0f, 0.0f, 0.0f, 1.0f, 0.0f
                    ))
                    val destinationMatrix = android.graphics.ColorMatrix()
                    destinationMatrix.setSaturation(0.2f)
                    inverseMatrix.postConcat(destinationMatrix)
                    view.overlayManager.tilesOverlay.setColorFilter(android.graphics.ColorMatrixColorFilter(inverseMatrix))
                } else {
                    view.overlayManager.tilesOverlay.setColorFilter(null)
                }

                // ── INICIALIZACIÓN RÁPIDA DEL CURSOR ──
                if (carMarker == null) {
                    NavigationState.currentLocation.value?.let { loc ->
                        val initialGeo = GeoPoint(loc.latitude, loc.longitude)
                        carMarker = Marker(view).apply {
                            val iconBitmap = if (AppSettings.vehicleType.value == "CUSTOM" && AppSettings.customVehicleIconPath.value.isNotEmpty()) {
                                try {
                                    val file = java.io.File(AppSettings.customVehicleIconPath.value)
                                    if (file.exists()) {
                                        val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                                        android.graphics.Bitmap.createScaledBitmap(bmp, 120, 120, true)
                                    } else { drawVehicleBitmap(view.context, "SEDAN", mapIconColor) }
                                } catch (e: Exception) { drawVehicleBitmap(view.context, "SEDAN", mapIconColor) }
                            } else { drawVehicleBitmap(view.context, vehicleType, mapIconColor) }
                            
                            icon = android.graphics.drawable.BitmapDrawable(view.context.resources, iconBitmap)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            isFlat = false
                            position = initialGeo
                            
                            val b = if (loc.hasBearing()) loc.bearing else 0f
                            val mapRot = if (loc.hasBearing() && loc.speed * 3.6f > 3f) 360f - b else currentMapRotation
                            rotation = (b + mapRot) % 360f
                        }
                        view.overlays.add(carMarker)
                        
                        if (!hasInitializedPosition) {
                            view.controller.setCenter(initialGeo)
                            view.controller.setZoom(18.5)
                            hasInitializedPosition = true
                            
                            // Restaurar rotación si es posible
                            if (loc.hasBearing() && loc.speed * 3.6f > 3f) {
                                view.mapOrientation = 360f - loc.bearing
                                currentMapRotation = 360f - loc.bearing
                            }
                        }
                    }
                } else if (!view.overlays.contains(carMarker)) {
                    view.overlays.add(carMarker)
                }

                // Renderizar la ruta histórica si hay una seleccionada
                val selectedRoute = NavigationState.selectedHistoryRoute.value
                val selectedSegment = NavigationState.selectedHistorySegment.value
                val existingHistoryPolyline = view.overlays.find { it is Polyline && it.id == "ROUTE_HISTORY" }
                
                if (selectedRoute != null) {
                    if (existingHistoryPolyline == null) {
                        val pts = if (selectedSegment != null) {
                            selectedSegment.points.map { GeoPoint(it.lat, it.lon) }
                        } else {
                            selectedRoute.segments.flatMap { s -> s.points.map { GeoPoint(it.lat, it.lon) } }
                        }
                        
                        if (pts.isNotEmpty()) {
                            val polyline = Polyline(view).apply {
                                id = "ROUTE_HISTORY"
                                setPoints(pts)
                                outlinePaint.color = android.graphics.Color.parseColor("#4CAF50")
                                outlinePaint.strokeWidth = 14f
                                outlinePaint.strokeCap = Paint.Cap.ROUND
                                outlinePaint.strokeJoin = Paint.Join.ROUND
                            }
                            view.overlays.add(polyline)
                            
                            isFollowingLocation = false
                            autoCenterJob?.cancel()
                            view.controller.animateTo(pts.first())
                            view.controller.setZoom(16.0)
                            view.mapOrientation = 0f
                            currentMapRotation = 0f
                        }
                    }
                } else {
                    if (existingHistoryPolyline != null) {
                        view.overlays.remove(existingHistoryPolyline)
                    }
                }

                // Renderizar ruta Dashcam si hay una seleccionada
                val selectedDashcamRoute = NavigationState.selectedDashcamRoute.value
                val existingDashcamPolyline = view.overlays.find { it is Polyline && it.id == "DASHCAM_ROUTE" }
                
                if (selectedDashcamRoute != null) {
                    if (existingDashcamPolyline == null) {
                        val pts = selectedDashcamRoute.map { GeoPoint(it.lat, it.lon) }
                        if (pts.isNotEmpty()) {
                            val polyline = Polyline(view).apply {
                                id = "DASHCAM_ROUTE"
                                setPoints(pts)
                                outlinePaint.color = android.graphics.Color.parseColor("#FF9800")
                                outlinePaint.strokeWidth = 14f
                                outlinePaint.strokeCap = Paint.Cap.ROUND
                                outlinePaint.strokeJoin = Paint.Join.ROUND
                            }
                            view.overlays.add(polyline)
                            
                            isFollowingLocation = false
                            autoCenterJob?.cancel()
                            view.controller.animateTo(pts.first())
                            view.controller.setZoom(17.0)
                            view.mapOrientation = 0f
                            currentMapRotation = 0f
                        }
                    }
                } else {
                    if (existingDashcamPolyline != null) {
                        view.overlays.remove(existingDashcamPolyline)
                    }
                }

                // ── RESTAURAR RUTA ACTIVA si se perdió (ej: rotación de pantalla) ──
                val existingMainPolyline = view.overlays.find { it is Polyline && it.id == "ROUTE_MAIN" }
                if (NavigationState.isRouteActive.value && NavigationState.activeRoutePoints.isNotEmpty()) {
                    if (existingMainPolyline == null) {
                        val polyline = Polyline(view).apply {
                            id = "ROUTE_MAIN"
                            setPoints(NavigationState.activeRoutePoints.toList())
                            outlinePaint.color = android.graphics.Color.parseColor("#007AFF")
                            outlinePaint.strokeWidth = 20f
                            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                            outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                        }
                        view.overlays.add(0, polyline)
                    }
                } else if (!NavigationState.isRouteActive.value && existingMainPolyline != null) {
                    view.overlays.remove(existingMainPolyline)
                }

                // Restaurar marcador de destino si se perdió
                val existingDestMarker = view.overlays.find { it is Marker && (it as Marker).id == "DEST" }
                val activeDest = NavigationState.activeDestination.value
                if (activeDest != null && existingDestMarker == null) {
                    val marker = Marker(view).apply {
                        position = activeDest
                        id = "DEST"
                        icon = android.graphics.drawable.BitmapDrawable(
                            view.context.resources,
                            drawCustomPin(AppSettings.uiColor.value)
                        )
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    view.overlays.add(marker)
                }

                view.invalidate()
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
                                        val dest = GeoPoint(place.lat, place.lon)
                                        selectedDestination = dest
                                        mapView.overlays.removeAll { it is Marker && it.id == "DEST" }
                                        val marker = Marker(mapView).apply {
                                            position = dest
                                            id = "DEST"
                                            icon = BitmapDrawable(context.resources, drawCustomPin(uiColor))
                                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                        }
                                        mapView.overlays.add(marker)
                                        mapView.controller.animateTo(dest)
                                        searchResults = emptyList()
                                        showFavorites = false
                                        focusManager.clearFocus()
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
                        mapView.controller.animateTo(mapView.mapCenter, mapView.zoomLevelDouble, 500, 0f)
                        currentMapRotation = 0f
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Icon(Icons.Default.Explore, "Norte", modifier = Modifier.size(20.dp))
                }
            }
            // Botón Centrar
            AnimatedVisibility(visible = !isFollowingLocation) {
                FloatingActionButton(
                    onClick = {
                        isFollowingLocation = true
                        autoCenterJob?.cancel()
                        NavigationState.currentLocation.value?.let {
                            mapView.controller.animateTo(GeoPoint(it.latitude, it.longitude))
                            mapView.mapOrientation = 360f - lastKnownBearing
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
                                mapView.overlays.removeAll { it is Marker && it.id == "DEST" }
                                mapView.invalidate()
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
                                    calculateRoute(GeoPoint(loc.latitude, loc.longitude), selectedDestination!!)
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
                                mapView.overlays.removeAll { it is Polyline || (it is Marker && it.id == "DEST") }
                                mapView.invalidate()
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
            append("https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&addressdetails=1&limit=15")
            if (currentLoc != null) {
                val lat = currentLoc.latitude
                val lon = currentLoc.longitude
                // Viewbox de ~100km alrededor (aprox 1 grado)
                append("&viewbox=${lon - 1.0},${lat + 1.0},${lon + 1.0},${lat - 1.0}")
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
            list.add(PlaceResult(obj.getString("display_name").split(",")[0], obj.getString("display_name"), obj.getDouble("lat"), obj.getDouble("lon")))
        }
        
        // Ordenar por distancia si tenemos ubicación
        if (currentLoc != null) {
            list.sortBy {
                val dist = FloatArray(1)
                android.location.Location.distanceBetween(currentLoc.latitude, currentLoc.longitude, it.lat, it.lon, dist)
                dist[0]
            }
        }
        
        list
    } catch (e: Exception) { emptyList() }
}

/**
 * Renders a vehicle bitmap for use as a map marker.
 * For SEDAN and HATCHBACK types, uses the Vector Drawable XML resources (ic_sedan.xml / ic_hatchback.xml)
 * tinted with [color]. Other types fall back to Canvas-drawn primitives.
 * [color] should come from AppSettings.mapIconColor — independent of the UI accent color.
 */
internal fun drawVehicleBitmap(context: Context, type: String, color: Int): Bitmap {
    val size = 120

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
            val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(b)
            vd.setBounds(0, 0, size, size)
            vd.draw(canvas)
            return b
        }
    }

    // Fallback: Canvas-drawn primitives for SPORT, TRUCK, and any unknown type
    val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(b)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = color
    p.setShadowLayer(8f, 0f, 4f, 0x80000000.toInt())

    when (type) {
        "SPORT" -> {
            val path = Path()
            path.moveTo(60f, 10f)
            path.lineTo(20f, 100f)
            path.lineTo(60f, 85f)
            path.lineTo(100f, 100f)
            path.close()
            canvas.drawPath(path, p)
        }
        "TRUCK" -> canvas.drawRect(30f, 20f, 90f, 100f, p)
        else -> {
            val path = Path()
            path.moveTo(60f, 15f)
            path.lineTo(25f, 105f)
            path.lineTo(60f, 90f)
            path.lineTo(95f, 105f)
            path.close()
            canvas.drawPath(path, p)
        }
    }
    return b
}

internal fun drawCustomPin(color: Int): Bitmap {
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
