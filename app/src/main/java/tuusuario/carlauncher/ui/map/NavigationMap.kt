package com.tuusuario.carlauncher.ui.map

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.view.MotionEvent
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.google.android.gms.location.*
import com.tuusuario.carlauncher.ui.AppSettings
import com.tuusuario.carlauncher.ui.NavigationState
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
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
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

    var selectedDestination by remember { mutableStateOf<GeoPoint?>(null) }
    var routeDistanceText by remember { mutableStateOf("") }
    val currentRoutePoints = remember { mutableStateListOf<GeoPoint>() }
    
    var showArrivalAlert by remember { mutableStateOf(false) }
    var isCalculatingRoute by remember { mutableStateOf(false) }

    var carMarker by remember { mutableStateOf<Marker?>(null) }
    var animator: ValueAnimator? by remember { mutableStateOf(null) }
    var autoCenterJob by remember { mutableStateOf<Job?>(null) }
    
    var currentMapRotation by remember { mutableStateOf(0f) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var showFavorites by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { Configuration.getInstance().userAgentValue = context.packageName }

    // Función unificada para calcular/recalcular rutas
    val calculateRoute: (GeoPoint, GeoPoint) -> Unit = { startGeo, destGeo ->
        if (!isCalculatingRoute) {
            isCalculatingRoute = true
            coroutineScope.launch {
                routeDistanceText = "Calculando ruta..."
                try {
                    val urlStr = "https://router.project-osrm.org/route/v1/driving/${startGeo.longitude},${startGeo.latitude};${destGeo.longitude},${destGeo.latitude}?overview=full&geometries=geojson"
                    val result = withContext(Dispatchers.IO) {
                        val conn = URL(urlStr).openConnection() as HttpURLConnection
                        conn.connectTimeout = 3000
                        conn.inputStream.bufferedReader().readText()
                    }
                    val json = JSONObject(result)
                    val routes = json.getJSONArray("routes")
                    if (routes.length() > 0) {
                        val route = routes.getJSONObject(0)
                        val distanceMeters = route.getDouble("distance")
                        routeDistanceText = if (distanceMeters > 1000) String.format("%.1f km restantes", distanceMeters / 1000) else "${distanceMeters.toInt()} m restantes"

                        val coordinates = route.getJSONObject("geometry").getJSONArray("coordinates")
                        currentRoutePoints.clear()
                        for (i in 0 until coordinates.length()) {
                            val pt = coordinates.getJSONArray(i)
                            currentRoutePoints.add(GeoPoint(pt.getDouble(1), pt.getDouble(0)))
                        }
                        
                        mapView.overlays.removeAll { it is Polyline && it.id == "ROUTE_MAIN" }
                        val polyline = Polyline(mapView).apply {
                            id = "ROUTE_MAIN"
                            setPoints(currentRoutePoints.toList())
                            outlinePaint.color = android.graphics.Color.parseColor("#007AFF")
                            outlinePaint.strokeWidth = 18f
                            outlinePaint.strokeCap = Paint.Cap.ROUND
                            outlinePaint.strokeJoin = Paint.Join.ROUND
                        }
                        mapView.overlays.add(0, polyline)
                        isFollowingLocation = true 
                        autoCenterJob?.cancel()
                        mapView.invalidate()
                    }
                } catch (e: Exception) { 
                    val dist = startGeo.distanceToAsDouble(destGeo)
                    routeDistanceText = "Ruta Offline: ${String.format("%.1f km (Linea Recta)", dist / 1000)}"
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
                        Toast.makeText(context, "Sin Internet: Activando Ruta Directa Offline", Toast.LENGTH_LONG).show()
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
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500)
            .setMinUpdateIntervalMillis(250)
            .setMinUpdateDistanceMeters(0f)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val newGeo = GeoPoint(loc.latitude, loc.longitude)
                
                // Usamos el bearing nativo si estamos en movimiento, de lo contrario mantenemos el anterior
                var targetBearing = carMarker?.rotation ?: 0f
                if (loc.hasBearing() && loc.speed > 1.0f) {
                    targetBearing = loc.bearing
                }

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
                                    val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, 120, 120, true)
                                    scaled
                                } else {
                                    drawVehicleBitmap("SEDAN", uiColor)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                drawVehicleBitmap("SEDAN", uiColor)
                            }
                        } else {
                            drawVehicleBitmap(vehicleType, uiColor)
                        }
                        icon = BitmapDrawable(context.resources, iconBitmap)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        isFlat = true
                        position = newGeo
                        rotation = 0f // SIEMPRE apunta hacia arriba; el mapa rota alrededor
                    }
                    mapView.overlays.add(carMarker)
                    
                    if (isFollowingLocation || !hasInitializedPosition) {
                        mapView.controller.setCenter(newGeo)
                        mapView.setMapCenterOffset(0, mapView.height / 4) // Offset Bottom 1/3
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

                    // Calcular la rotación del mapa suavemente
                    var startMapRot = currentMapRotation % 360f
                    if (startMapRot < 0) startMapRot += 360f
                    val targetMapRot = if (!isStationary && loc.hasBearing()) 360f - targetBearing else startMapRot
                    var deltaMapRot = targetMapRot - startMapRot
                    if (deltaMapRot > 180f) deltaMapRot -= 360f
                    if (deltaMapRot < -180f) deltaMapRot += 360f

                    animator = ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = 500 
                        interpolator = LinearInterpolator()

                        addUpdateListener { anim ->
                            val fraction = anim.animatedFraction
                            val currentPos = GeoPoint(startLat + (deltaLat * fraction), startLon + (deltaLon * fraction))
                            
                            carMarker!!.position = currentPos
                            carMarker!!.rotation = 0f // SIEMPRE fijo mirando arriba
                            
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

                // LÓGICA DEL RECORRIDO Y AUTO-RECÁLCULO
                if (currentRoutePoints.isNotEmpty() && selectedDestination != null) {
                    var minDistance = Float.MAX_VALUE
                    var closestIndex = 0
                    val searchLimit = minOf(100, currentRoutePoints.size) 
                    
                    for (i in 0 until searchLimit) {
                        val pt = currentRoutePoints[i]
                        val l1 = android.location.Location("").apply { latitude = loc.latitude; longitude = loc.longitude }
                        val l2 = android.location.Location("").apply { latitude = pt.latitude; longitude = pt.longitude }
                        val dist = l1.distanceTo(l2)
                        if (dist < minDistance) { minDistance = dist; closestIndex = i }
                    }

                    // AUTO-RECÁLCULO: Si nos alejamos más de 100m del punto más cercano de la ruta
                    if (minDistance > 100f && !isCalculatingRoute) {
                        calculateRoute(GeoPoint(loc.latitude, loc.longitude), selectedDestination!!)
                    } 
                    // SEGUIMIENTO: Borramos el rastro por detrás de nosotros
                    else if (closestIndex > 0 && minDistance < 60f) {
                        for (i in 0 until closestIndex) {
                            if (currentRoutePoints.isNotEmpty()) currentRoutePoints.removeAt(0)
                        }
                        val mainPoly = mapView.overlays.find { it is Polyline && it.id == "ROUTE_MAIN" } as? Polyline
                        mainPoly?.setPoints(currentRoutePoints.toList())
                    }

                    // LLEGADA AL DESTINO
                    if (currentRoutePoints.size < 10) {
                        val endPt = currentRoutePoints.last()
                        val lDest = android.location.Location("").apply { latitude = endPt.latitude; longitude = endPt.longitude }
                        if (loc.distanceTo(lDest) < 30f) { 
                            showArrivalAlert = true
                            currentRoutePoints.clear()
                            routeDistanceText = ""
                            selectedDestination = null
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

                    val rotationGestureOverlay = RotationGestureOverlay(this)
                    rotationGestureOverlay.isEnabled = true
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
                                        if (it.hasBearing()) mapOrientation = 360f - it.bearing
                                    }
                                }
                            }
                        }
                        false 
                    }

                    addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            if (event != null && (event.x != 0 || event.y != 0)) {
                                isFollowingLocation = false
                                currentMapRotation = this@apply.mapOrientation
                                
                                autoCenterJob?.cancel()
                                autoCenterJob = coroutineScope.launch {
                                    delay(6000)
                                    isFollowingLocation = true
                                    NavigationState.currentLocation.value?.let {
                                        controller.animateTo(GeoPoint(it.latitude, it.longitude))
                                        if (it.hasBearing()) mapOrientation = 360f - it.bearing
                                    }
                                    autoCenterJob = null
                                }
                            }
                            return false
                        }
                        override fun onZoom(event: ZoomEvent?): Boolean {
                            isFollowingLocation = false
                            autoCenterJob?.cancel()
                            autoCenterJob = coroutineScope.launch {
                                delay(6000)
                                isFollowingLocation = true
                                NavigationState.currentLocation.value?.let {
                                    controller.animateTo(GeoPoint(it.latitude, it.longitude))
                                    if (it.hasBearing()) mapOrientation = 360f - it.bearing
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
                            if (currentRoutePoints.isNotEmpty()) {
                                return true 
                            }
                            
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
                                Toast.makeText(context, "Toca el pin para guardar en Favoritos", Toast.LENGTH_SHORT).show()
                            }
                            return true
                        }
                    }
                    overlays.add(MapEventsOverlay(mReceive))
                }
            },
            update = { view ->
                if (isMapDarkMode) {
                    val inverseMatrix = ColorMatrix(floatArrayOf(
                        -1.0f, 0.0f, 0.0f, 0.0f, 255f,
                        0.0f, -1.0f, 0.0f, 0.0f, 255f,
                        0.0f, 0.0f, -1.0f, 0.0f, 255f,
                        0.0f, 0.0f, 0.0f, 1.0f, 0.0f
                    ))
                    view.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(inverseMatrix))
                } else {
                    view.overlayManager.tilesOverlay.setColorFilter(null)
                }
                
                carMarker?.icon = BitmapDrawable(context.resources, drawVehicleBitmap(vehicleType, uiColor))
                view.invalidate()
            }
        )

        // BARRA DE BÚSQUEDA Y FAVORITOS SUPERIOR
        Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp)) {
                IconButton(onClick = { 
                    showFavorites = !showFavorites
                    if(showFavorites) {
                        val loc = NavigationState.currentLocation.value
                        val favs = favManager.getFavorites().map { fav ->
                            var dist = 0.0
                            loc?.let {
                                val res = FloatArray(1)
                                android.location.Location.distanceBetween(it.latitude, it.longitude, fav.lat, fav.lon, res)
                                dist = res[0] / 1000.0
                            }
                            fav.copy(distanceKm = dist)
                        }.sortedBy { it.distanceKm } 
                        searchResults = favs
                    } else {
                        searchResults = emptyList()
                    }
                }) {
                    Icon(if(showFavorites) Icons.Default.Star else Icons.Default.StarBorder, "Favoritos", tint = Color(uiColor))
                }
                
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar lugar...") },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.textFieldColors(containerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focusManager.clearFocus()
                        if (searchQuery.isNotEmpty()) {
                            isSearching = true
                            coroutineScope.launch {
                                try {
                                    val query = URLEncoder.encode(searchQuery, "UTF-8")
                                    val loc = NavigationState.currentLocation.value
                                    
                                    val urlStr = buildString {
                                        append("https://nominatim.openstreetmap.org/search?q=$query&format=json&limit=10&addressdetails=1")
                                        if (loc != null) {
                                            append("&lat=${loc.latitude}&lon=${loc.longitude}") 
                                        }
                                    }
                                    
                                    val result = withContext(Dispatchers.IO) {
                                        val conn = URL(urlStr).openConnection() as HttpURLConnection
                                        conn.setRequestProperty("User-Agent", "CarLauncher")
                                        conn.inputStream.bufferedReader().readText()
                                    }
                                    val jsonArray = JSONArray(result)
                                    val list = mutableListOf<PlaceResult>()
                                    for (i in 0 until jsonArray.length()) {
                                        val obj = jsonArray.getJSONObject(i)
                                        val lat = obj.getDouble("lat")
                                        val lon = obj.getDouble("lon")
                                        
                                        var dist = 0.0
                                        loc?.let {
                                            val res = FloatArray(1)
                                            android.location.Location.distanceBetween(it.latitude, it.longitude, lat, lon, res)
                                            dist = res[0] / 1000.0
                                        }

                                        val displayName = obj.getString("display_name")
                                        val parts = displayName.split(",", limit = 2)
                                        val placeName = parts.getOrNull(0)?.trim() ?: "Lugar"
                                        val placeAddress = parts.getOrNull(1)?.trim() ?: ""
                                        list.add(PlaceResult(placeName, placeAddress, lat, lon, "Place", dist))
                                    }
                                    searchResults = list.sortedBy { it.distanceKm }.take(5)
                                    if(list.isEmpty()) Toast.makeText(context, "No se encontraron resultados", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) { Toast.makeText(context, "Error al buscar", Toast.LENGTH_SHORT).show() }
                                isSearching = false
                            }
                        }
                    })
                )
                if (isSearching) CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 8.dp), strokeWidth = 2.dp)
                else IconButton(onClick = { searchQuery = ""; searchResults = emptyList(); showFavorites = false; focusManager.clearFocus() }) { Icon(Icons.Default.Close, "Limpiar") }
            }

            AnimatedVisibility(visible = searchResults.isNotEmpty()) {
                LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 4.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(12.dp)).shadow(4.dp)) {
                    items(searchResults) { place ->
                        Row(modifier = Modifier.fillMaxWidth().clickable {
                            focusManager.clearFocus()
                            searchQuery = place.name
                            val p = GeoPoint(place.lat, place.lon)
                            selectedDestination = p
                            
                            isFollowingLocation = false
                            autoCenterJob?.cancel()

                            mapView.controller.animateTo(p)
                            mapView.overlays.removeAll { it is Marker && it.id == "DEST" }
                            val m = Marker(mapView).apply { position = p; id = "DEST"; icon = BitmapDrawable(context.resources, drawCustomPin(uiColor)); setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) }
                            mapView.overlays.add(m)
                            mapView.invalidate()
                            searchResults = emptyList()
                            showFavorites = false
                        }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            
                            val icon = when (place.iconType) {
                                "Home" -> Icons.Default.Home
                                "Work" -> Icons.Default.Work
                                "Store" -> Icons.Default.Store
                                "LocalGasStation" -> Icons.Default.LocalGasStation
                                else -> Icons.Default.Place
                            }
                            Icon(icon, null, tint = Color(uiColor), modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(place.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (place.address.isNotEmpty()) {
                                    Text(place.address, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }

                            if (place.distanceKm > 0.0) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(String.format("%.1f km", place.distanceKm), fontWeight = FontWeight.Bold, color = Color(uiColor), fontSize = 14.sp)
                            }
                        }
                        Divider()
                    }
                }
            }
        }

        // CONTROLES FLOTANTES (Abajo Derecha)
        Column(modifier = Modifier.align(Alignment.BottomEnd).padding(if (isFullScreen) 32.dp else 16.dp), horizontalAlignment = Alignment.End) {
            
            FloatingActionButton(
                onClick = { AppSettings.setMapDarkMode(!AppSettings.isMapDarkMode.value) },
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(if (isMapDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode, "Tema Mapa", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            AnimatedVisibility(visible = !isFollowingLocation && currentMapRotation != 0f) {
                FloatingActionButton(
                    onClick = { mapView.controller.animateTo(mapView.mapCenter, mapView.zoomLevelDouble, 500, 0f); currentMapRotation = 0f },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(Icons.Default.Explore, "Norte", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            AnimatedVisibility(visible = !isFollowingLocation) {
                ExtendedFloatingActionButton(
                    onClick = {
                        isFollowingLocation = true
                        autoCenterJob?.cancel()
                        NavigationState.currentLocation.value?.let { 
                            mapView.controller.animateTo(GeoPoint(it.latitude, it.longitude))
                            if (it.hasBearing()) mapView.mapOrientation = 360f - it.bearing
                        }
                    },
                    icon = { Icon(Icons.Default.MyLocation, "Centrar") },
                    text = { Text("Centrar") },
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            if (routeDistanceText.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(routeDistanceText, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            if (selectedDestination != null || currentRoutePoints.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            selectedDestination = null
                            routeDistanceText = ""
                            currentRoutePoints.clear()
                            mapView.overlays.removeAll { it is Polyline || (it is Marker && it.id == "DEST") }
                            mapView.invalidate()
                        },
                        icon = { Icon(Icons.Default.Close, "Cancelar") }, text = { Text("Cancelar") }, 
                        containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError
                    )

                    if (currentRoutePoints.isEmpty()) {
                        ExtendedFloatingActionButton(
                            onClick = {
                                val start = NavigationState.currentLocation.value
                                val dest = selectedDestination
                                if (start != null && dest != null) {
                                    calculateRoute(GeoPoint(start.latitude, start.longitude), dest)
                                } else {
                                    routeDistanceText = "Esperando GPS..."
                                }
                            },
                            icon = { Icon(Icons.Default.Directions, "Trazar") }, text = { Text("Ir") }, 
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }

        // DIÁLOGO PARA GUARDAR FAVORITO
        if (showSaveFavoriteDialog && favoriteLocationToSave != null) {
            AlertDialog(
                onDismissRequest = { showSaveFavoriteDialog = false },
                title = { Text("Guardar Favorito") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        TextField(
                            value = favoriteNameToSave,
                            onValueChange = { favoriteNameToSave = it },
                            label = { Text("Nombre del lugar") },
                            singleLine = true
                        )
                        Text("Elige un ícono:")
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            IconButton(onClick = { selectedIconType = "Home" }) { Icon(Icons.Default.Home, null, tint = if(selectedIconType=="Home") Color(uiColor) else MaterialTheme.colorScheme.onSurface) }
                            IconButton(onClick = { selectedIconType = "Work" }) { Icon(Icons.Default.Work, null, tint = if(selectedIconType=="Work") Color(uiColor) else MaterialTheme.colorScheme.onSurface) }
                            IconButton(onClick = { selectedIconType = "Store" }) { Icon(Icons.Default.Store, null, tint = if(selectedIconType=="Store") Color(uiColor) else MaterialTheme.colorScheme.onSurface) }
                            IconButton(onClick = { selectedIconType = "LocalGasStation" }) { Icon(Icons.Default.LocalGasStation, null, tint = if(selectedIconType=="LocalGasStation") Color(uiColor) else MaterialTheme.colorScheme.onSurface) }
                            IconButton(onClick = { selectedIconType = "Star" }) { Icon(Icons.Default.Star, null, tint = if(selectedIconType=="Star") Color(uiColor) else MaterialTheme.colorScheme.onSurface) }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        favManager.addFavorite(PlaceResult(favoriteNameToSave, "", favoriteLocationToSave!!.latitude, favoriteLocationToSave!!.longitude, selectedIconType))
                        Toast.makeText(context, "Favorito guardado", Toast.LENGTH_SHORT).show()
                        showSaveFavoriteDialog = false
                    }) { Text("Guardar") }
                },
                dismissButton = { TextButton(onClick = { showSaveFavoriteDialog = false }) { Text("Cancelar") } }
            )
        }

        AnimatedVisibility(
            visible = showArrivalAlert,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50)),
                elevation = CardDefaults.cardElevation(12.dp),
                modifier = Modifier.width(300.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("¡Has llegado a tu destino!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }
        
        LaunchedEffect(showArrivalAlert) {
            if (showArrivalAlert) {
                delay(4000)
                showArrivalAlert = false
            }
        }
    }
}

fun drawCustomPin(color: Int): Bitmap {
    val size = 90
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    val path = Path().apply { moveTo(45f, 90f); cubicTo(45f, 90f, 10f, 50f, 10f, 35f); cubicTo(10f, 15f, 25f, 5f, 45f, 5f); cubicTo(65f, 5f, 80f, 15f, 80f, 35f); cubicTo(80f, 50f, 45f, 90f, 45f, 90f); close() }
    canvas.drawPath(path, paint)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(45f, 35f, 12f, paint)
    return bitmap 
}

fun drawVehicleBitmap(type: String, color: Int): Bitmap {
    val size = 120
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.DKGRAY; style = Paint.Style.FILL }
    val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.parseColor("#FFF59D"); style = Paint.Style.FILL }
    val scale = 1.2f
    canvas.scale(scale, scale)

    when (type) {
        "FLECHA" -> { val path = Path().apply { moveTo(size / 2f / scale, 10f); lineTo((size - 20f) / scale, (size - 15f) / scale); lineTo(size / 2f / scale, (size - 30f) / scale); lineTo(20f / scale, (size - 15f) / scale); close() }; canvas.drawPath(path, bodyPaint) }
        "SEDAN", "CUSTOM" -> { canvas.drawRoundRect(25f, 10f, 75f, 90f, 15f, 15f, bodyPaint); canvas.drawRoundRect(30f, 30f, 70f, 45f, 5f, 5f, glassPaint); canvas.drawRoundRect(30f, 65f, 70f, 75f, 5f, 5f, glassPaint); canvas.drawCircle(35f, 15f, 6f, lightPaint); canvas.drawCircle(65f, 15f, 6f, lightPaint) }
        "HATCHBACK" -> { canvas.drawRoundRect(25f, 20f, 75f, 85f, 12f, 12f, bodyPaint); canvas.drawRoundRect(30f, 40f, 70f, 55f, 5f, 5f, glassPaint); canvas.drawRoundRect(30f, 75f, 70f, 82f, 3f, 3f, glassPaint); canvas.drawCircle(35f, 25f, 6f, lightPaint); canvas.drawCircle(65f, 25f, 6f, lightPaint) }
    }
    return bitmap
}