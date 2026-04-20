package com.tuusuario.carlauncher.ui.map

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.view.MotionEvent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// NUEVO: Servidor "CARTO Dark". Fondo negro y todas las carreteras del mapa blancas y brillantes.
object CartoDarkSource {
    fun create(): XYTileSource {
        return object : XYTileSource(
            "CartoDark", 0, 20, 256, ".png", arrayOf(
                "https://a.basemaps.cartocdn.com/dark_all/",
                "https://b.basemaps.cartocdn.com/dark_all/",
                "https://c.basemaps.cartocdn.com/dark_all/"
            ), "© CARTO"
        ) {}
    }
}

data class PlaceResult(val name: String, val lat: Double, val lon: Double)

class FavoritesManager(context: Context) {
    private val prefs = context.getSharedPreferences("CarFavorites", Context.MODE_PRIVATE)
    fun getFavorites(): List<PlaceResult> {
        val jsonString = prefs.getString("favs", "[]") ?: "[]"
        val array = JSONArray(jsonString)
        val list = mutableListOf<PlaceResult>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(PlaceResult(obj.getString("name"), obj.getDouble("lat"), obj.getDouble("lon")))
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
                obj.put("name", it.name); obj.put("lat", it.lat); obj.put("lon", it.lon)
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
    
    var selectedDestination by remember { mutableStateOf<GeoPoint?>(null) }
    var routeDistanceText by remember { mutableStateOf("") }
    val currentRoutePoints = remember { mutableStateListOf<GeoPoint>() }
    var isFollowingLocation by remember { mutableStateOf(true) }
    var showArrivalAlert by remember { mutableStateOf(false) }

    var carMarker by remember { mutableStateOf<Marker?>(null) }
    var animator: ValueAnimator? by remember { mutableStateOf(null) }
    var autoCenterJob by remember { mutableStateOf<Job?>(null) }
    
    // Variables Buscador y Rotación
    var currentMapRotation by remember { mutableStateOf(0f) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var showFavorites by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { Configuration.getInstance().userAgentValue = context.packageName }

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
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500).build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val newGeo = GeoPoint(loc.latitude, loc.longitude)
                
                NavigationState.currentLocation.value = loc
                val speedKmH = loc.speed * 3.6f
                if (loc.hasSpeed()) NavigationState.currentSpeedKmH.value = speedKmH
                val isStationary = speedKmH < 3f 

                if (carMarker == null) {
                    carMarker = Marker(mapView).apply {
                        icon = BitmapDrawable(context.resources, drawVehicleBitmap(vehicleType, uiColor))
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        position = newGeo
                        rotation = if (loc.hasBearing() && !isStationary) loc.bearing else 0f
                    }
                    mapView.overlays.add(carMarker)
                    if (isFollowingLocation) {
                        mapView.controller.setCenter(newGeo)
                        if (loc.hasBearing() && !isStationary) {
                            mapView.mapOrientation = -loc.bearing
                            currentMapRotation = -loc.bearing
                        }
                    }
                } else {
                    animator?.cancel()
                    val startGeo = carMarker!!.position
                    val startRot = carMarker!!.rotation
                    val targetRot = if (loc.hasBearing() && !isStationary) loc.bearing else startRot
                    
                    var deltaRot = targetRot - startRot
                    while (deltaRot > 180) deltaRot -= 360
                    while (deltaRot < -180) deltaRot += 360

                    animator = ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = 500 
                        val startLat = startGeo.latitude
                        val startLon = startGeo.longitude
                        val deltaLat = newGeo.latitude - startLat
                        val deltaLon = newGeo.longitude - startLon

                        addUpdateListener { anim ->
                            val fraction = anim.animatedFraction
                            val currentPos = GeoPoint(startLat + (deltaLat * fraction), startLon + (deltaLon * fraction))
                            val interpolatedRot = startRot + (deltaRot * fraction)
                            
                            carMarker!!.position = currentPos
                            carMarker!!.rotation = interpolatedRot
                            
                            if (isFollowingLocation) {
                                mapView.controller.setCenter(currentPos)
                                mapView.mapOrientation = -interpolatedRot 
                                currentMapRotation = -interpolatedRot
                            }
                            mapView.invalidate()
                        }
                        start()
                    }
                }

                if (currentRoutePoints.isNotEmpty()) {
                    var minDistance = Float.MAX_VALUE
                    var closestIndex = 0
                    val searchLimit = minOf(15, currentRoutePoints.size) 
                    
                    for (i in 0 until searchLimit) {
                        val pt = currentRoutePoints[i]
                        val l1 = android.location.Location("").apply { latitude = loc.latitude; longitude = loc.longitude }
                        val l2 = android.location.Location("").apply { latitude = pt.latitude; longitude = pt.longitude }
                        val dist = l1.distanceTo(l2)
                        if (dist < minDistance) { minDistance = dist; closestIndex = i }
                    }

                    if (closestIndex > 0 && minDistance < 60f) {
                        for (i in 0 until closestIndex) {
                            if (currentRoutePoints.isNotEmpty()) currentRoutePoints.removeAt(0)
                        }
                        val mainPoly = mapView.overlays.find { it is Polyline && it.id == "ROUTE_MAIN" } as? Polyline
                        mainPoly?.setPoints(currentRoutePoints.toList())
                    }

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
                    // MAPA MODO NOCTURNO CON CARRETERAS RESALTADAS. Todo es transitable y claro.
                    setTileSource(if (isDarkMode) CartoDarkSource.create() else TileSourceFactory.MAPNIK)
                    controller.setCenter(GeoPoint(10.996, -63.804)) 
                    
                    addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
                        val h = bottom - top
                        if (h > 0) { setMapCenterOffset(0, h / 4) }
                    }

                    // HABILITAR ROTACIÓN CON 2 DEDOS
                    val rotationGestureOverlay = RotationGestureOverlay(this)
                    rotationGestureOverlay.isEnabled = true
                    overlays.add(rotationGestureOverlay)

                    addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            if (event != null && (event.x != 0 || event.y != 0)) {
                                isFollowingLocation = false
                                currentMapRotation = this@apply.mapOrientation
                            }
                            return false
                        }
                        override fun onZoom(event: ZoomEvent?): Boolean = false
                    })

                    setOnTouchListener { v, event ->
                        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                            isFollowingLocation = false
                            autoCenterJob?.cancel()
                            autoCenterJob = coroutineScope.launch {
                                delay(6000)
                                isFollowingLocation = true
                                NavigationState.currentLocation.value?.let {
                                    controller.animateTo(GeoPoint(it.latitude, it.longitude))
                                }
                                autoCenterJob = null
                            }
                        }
                        v.performClick()
                        false 
                    }

                    post { 
                        controller.setZoom(19.0)
                        invalidate() 
                    }
                    setMultiTouchControls(true)
                    setBuiltInZoomControls(false)
                    setHasTransientState(true)

                    val mReceive = object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            focusManager.clearFocus()
                            searchResults = emptyList()
                            return false
                        }
                        override fun longPressHelper(p: GeoPoint?): Boolean {
                            if (p != null) {
                                selectedDestination = p
                                routeDistanceText = ""
                                currentRoutePoints.clear()
                                isFollowingLocation = false 
                                overlays.removeAll { it is Marker && it.id == "DEST" || it is Polyline }
                                
                                val marker = Marker(mapView).apply {
                                    position = p
                                    id = "DEST"
                                    icon = BitmapDrawable(ctx.resources, drawCustomPin(uiColor))
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    title = "Destino Seleccionado"
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
                view.setMultiTouchControls(isFullScreen)
                view.setTileSource(if (isDarkMode) CartoDarkSource.create() else TileSourceFactory.MAPNIK)
                carMarker?.icon = BitmapDrawable(context.resources, drawVehicleBitmap(vehicleType, uiColor))
                view.invalidate()
            }
        )

        // BARRA DE BÚSQUEDA Y FAVORITOS SUPERIOR
        Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(horizontal = 8.dp)) {
                IconButton(onClick = { 
                    showFavorites = !showFavorites
                    if(showFavorites) searchResults = favManager.getFavorites() else searchResults = emptyList()
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
                        isSearching = true
                        coroutineScope.launch {
                            try {
                                val query = URLEncoder.encode(searchQuery, "UTF-8")
                                val urlStr = "https://nominatim.openstreetmap.org/search?q=$query&format=json&limit=4"
                                val result = withContext(Dispatchers.IO) {
                                    val conn = URL(urlStr).openConnection() as HttpURLConnection
                                    conn.setRequestProperty("User-Agent", "CarLauncher")
                                    conn.inputStream.bufferedReader().readText()
                                }
                                val jsonArray = JSONArray(result)
                                val list = mutableListOf<PlaceResult>()
                                for (i in 0 until jsonArray.length()) {
                                    val obj = jsonArray.getJSONObject(i)
                                    list.add(PlaceResult(obj.getString("display_name").split(",")[0], obj.getDouble("lat"), obj.getDouble("lon")))
                                }
                                searchResults = list
                            } catch (e: Exception) { Toast.makeText(context, "Error al buscar", Toast.LENGTH_SHORT).show() }
                            isSearching = false
                        }
                    })
                )
                if (isSearching) CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 8.dp), strokeWidth = 2.dp)
                else IconButton(onClick = { searchQuery = ""; searchResults = emptyList(); showFavorites = false; focusManager.clearFocus() }) { Icon(Icons.Default.Close, "Limpiar") }
            }

            AnimatedVisibility(visible = searchResults.isNotEmpty()) {
                LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 4.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).shadow(4.dp)) {
                    items(searchResults) { place ->
                        Row(modifier = Modifier.fillMaxWidth().clickable {
                            focusManager.clearFocus()
                            searchQuery = place.name
                            val p = GeoPoint(place.lat, place.lon)
                            selectedDestination = p
                            mapView.controller.animateTo(p)
                            mapView.overlays.removeAll { it is Marker && it.id == "DEST" || it is Polyline }
                            mapView.overlays.add(Marker(mapView).apply { position = p; id = "DEST"; icon = BitmapDrawable(context.resources, drawCustomPin(uiColor)); setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) })
                            mapView.invalidate()
                            searchResults = emptyList()
                        }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Place, null, tint = Color(uiColor))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(place.name, modifier = Modifier.weight(1f), maxLines = 1)
                            
                            if (!showFavorites) {
                                IconButton(onClick = { favManager.addFavorite(place); Toast.makeText(context, "Guardado en favoritos", Toast.LENGTH_SHORT).show() }) {
                                    Icon(Icons.Default.StarBorder, "Guardar")
                                }
                            }
                        }
                        Divider()
                    }
                }
            }
        }

        // BOTÓN NORTE (BRÚJULA) y CENTRAR
        Column(modifier = Modifier.align(Alignment.BottomEnd).padding(if (isFullScreen) 32.dp else 16.dp), horizontalAlignment = Alignment.End) {
            
            // Si el mapa fue rotado por el usuario con 2 dedos, mostramos el botón de Norte
            AnimatedVisibility(visible = !isFollowingLocation && currentMapRotation != 0f) {
                FloatingActionButton(
                    onClick = { mapView.controller.animateTo(mapView.mapCenter, mapView.zoomLevelDouble, 500, 0f); currentMapRotation = 0f },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(Icons.Default.Explore, "Norte", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            AnimatedVisibility(visible = !isFollowingLocation) {
                ExtendedFloatingActionButton(
                    onClick = {
                        isFollowingLocation = true
                        NavigationState.currentLocation.value?.let { 
                            mapView.controller.animateTo(GeoPoint(it.latitude, it.longitude))
                            if (it.hasBearing()) mapView.mapOrientation = -it.bearing
                        }
                    },
                    icon = { Icon(Icons.Default.MyLocation, "Centrar") },
                    text = { Text("Centrar") },
                    containerColor = MaterialTheme.colorScheme.primary,
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
                                coroutineScope.launch {
                                    val start = NavigationState.currentLocation.value
                                    val dest = selectedDestination
                                    if (start != null && dest != null) {
                                        routeDistanceText = "Calculando ruta..."
                                        try {
                                            val urlStr = "https://router.project-osrm.org/route/v1/driving/${start.longitude},${start.latitude};${dest.longitude},${dest.latitude}?overview=full&geometries=geojson"
                                            val result = withContext(Dispatchers.IO) {
                                                val conn = URL(urlStr).openConnection() as HttpURLConnection
                                                conn.connectTimeout = 3000 // Timeout corto para detectar rápido si no hay internet
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
                                                
                                                mapView.overlays.removeAll { it is Polyline }
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
                                                mapView.invalidate()
                                            }
                                        } catch (e: Exception) { 
                                            // RUTEO OFFLINE (MODO BRÚJULA/LINEA RECTA)
                                            // OSRM requiere internet, pero si falla trazamos una linea directa para que el usuario no se quede ciego.
                                            val startGeo = GeoPoint(start.latitude, start.longitude)
                                            val dist = startGeo.distanceToAsDouble(dest)
                                            routeDistanceText = "Ruta Offline: ${String.format("%.1f km (Linea Recta)", dist / 1000)}"
                                            currentRoutePoints.clear()
                                            currentRoutePoints.add(startGeo)
                                            currentRoutePoints.add(dest)

                                            mapView.overlays.removeAll { it is Polyline }
                                            val polyline = Polyline(mapView).apply {
                                                id = "ROUTE_MAIN"
                                                setPoints(currentRoutePoints.toList())
                                                outlinePaint.color = android.graphics.Color.parseColor("#007AFF")
                                                outlinePaint.strokeWidth = 12f
                                                outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 20f), 0f) // Linea punteada para indicar que es directo
                                            }
                                            mapView.overlays.add(0, polyline)
                                            isFollowingLocation = true 
                                            mapView.invalidate()
                                            Toast.makeText(context, "Sin Internet: Activando Ruta Directa Offline", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        routeDistanceText = "Esperando GPS..."
                                    }
                                }
                            },
                            icon = { Icon(Icons.Default.Directions, "Trazar") }, text = { Text("Ir") }, 
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }

        if (showArrivalAlert) {
            AlertDialog(
                onDismissRequest = { showArrivalAlert = false },
                title = { Text("Navegación Finalizada", fontWeight = FontWeight.Bold) },
                text = { Text("¡Has llegado exitosamente a tu destino!", fontSize = 18.sp) },
                confirmButton = { Button(onClick = { showArrivalAlert = false }) { Text("Aceptar") } }
            )
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
        "SEDAN" -> { canvas.drawRoundRect(25f, 10f, 75f, 90f, 15f, 15f, bodyPaint); canvas.drawRoundRect(30f, 30f, 70f, 45f, 5f, 5f, glassPaint); canvas.drawRoundRect(30f, 65f, 70f, 75f, 5f, 5f, glassPaint); canvas.drawCircle(35f, 15f, 6f, lightPaint); canvas.drawCircle(65f, 15f, 6f, lightPaint) }
        "HATCHBACK" -> { canvas.drawRoundRect(25f, 20f, 75f, 85f, 12f, 12f, bodyPaint); canvas.drawRoundRect(30f, 40f, 70f, 55f, 5f, 5f, glassPaint); canvas.drawRoundRect(30f, 75f, 70f, 82f, 3f, 3f, glassPaint); canvas.drawCircle(35f, 25f, 6f, lightPaint); canvas.drawCircle(65f, 25f, 6f, lightPaint) }
    }
    return bitmap
}