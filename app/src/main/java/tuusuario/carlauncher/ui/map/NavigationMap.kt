package com.tuusuario.carlauncher.ui.map

import android.Manifest
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.tuusuario.carlauncher.ui.AppSettings
import com.tuusuario.carlauncher.ui.NavigationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun NavigationMap(modifier: Modifier = Modifier, isFullScreen: Boolean = false, isDarkMode: Boolean = true) {
    val context = LocalContext.current
    val vehicleType = AppSettings.vehicleType.value
    // uiColor ahora controla la estética del mapa (Marcadores, vehículo), speedoColor es independiente
    val uiColor = AppSettings.uiColor.value 

    val coroutineScope = rememberCoroutineScope()
    val mapView = remember { MapView(context) }
    
    var selectedDestination by remember { mutableStateOf<GeoPoint?>(null) }
    var routeDistanceText by remember { mutableStateOf("") }
    val currentRoutePoints = remember { mutableStateListOf<GeoPoint>() }
    var isFollowingLocation by remember { mutableStateOf(true) }
    var showArrivalAlert by remember { mutableStateOf(false) }

    var carMarker by remember { mutableStateOf<Marker?>(null) }
    var animator: ValueAnimator? by remember { mutableStateOf(null) }
    var lastKnownLocation by remember { mutableStateOf<android.location.Location?>(null) }

    LaunchedEffect(Unit) { Configuration.getInstance().userAgentValue = context.packageName }

    DisposableEffect(context) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500).build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val newGeo = GeoPoint(loc.latitude, loc.longitude)
                
                if (loc.hasSpeed()) NavigationState.currentSpeedKmH.value = loc.speed * 3.6f
                if ((loc.speed * 3.6f) > 5f) isFollowingLocation = true

                if (carMarker == null) {
                    carMarker = Marker(mapView).apply {
                        icon = drawVehicleBitmap(vehicleType, uiColor)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        position = newGeo
                        rotation = if (loc.hasBearing()) -loc.bearing else 0f
                    }
                    mapView.overlays.add(carMarker)
                    if (isFollowingLocation) {
                        mapView.controller.setCenter(newGeo)
                        if (loc.hasBearing()) mapView.mapOrientation = -loc.bearing
                    }
                } else {
                    animator?.cancel()
                    val startGeo = carMarker!!.position
                    val startRot = carMarker!!.rotation
                    val endRot = if (loc.hasBearing()) -loc.bearing else startRot
                    
                    var deltaRot = endRot - startRot
                    if (deltaRot > 180) deltaRot -= 360
                    if (deltaRot < -180) deltaRot += 360

                    animator = ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = 500 
                        addUpdateListener { anim ->
                            val fraction = anim.animatedFraction
                            val lat = startGeo.latitude + (newGeo.latitude - startGeo.latitude) * fraction
                            val lon = startGeo.longitude + (newGeo.longitude - startGeo.longitude) * fraction
                            val currentPos = GeoPoint(lat, lon)
                            
                            carMarker!!.position = currentPos
                            carMarker!!.rotation = startRot + (deltaRot * fraction)
                            
                            if (isFollowingLocation) {
                                mapView.controller.setCenter(currentPos)
                                mapView.mapOrientation = carMarker!!.rotation
                            }
                            mapView.invalidate()
                        }
                        start()
                    }
                }
                lastKnownLocation = loc

                if (currentRoutePoints.isNotEmpty()) {
                    var minDistance = Float.MAX_VALUE
                    var closestIndex = 0
                    val searchLimit = minOf(15, currentRoutePoints.size) 
                    
                    for (i in 0 until searchLimit) {
                        val pt = currentRoutePoints[i]
                        val l1 = android.location.Location("").apply { latitude = loc.latitude; longitude = loc.longitude }
                        val l2 = android.location.Location("").apply { latitude = pt.latitude; longitude = pt.longitude }
                        val dist = l1.distanceTo(l2)
                        
                        if (dist < minDistance) {
                            minDistance = dist
                            closestIndex = i
                        }
                    }

                    if (closestIndex > 0 && minDistance < 60f) {
                        for (i in 0 until closestIndex) {
                            if (currentRoutePoints.isNotEmpty()) currentRoutePoints.removeAt(0)
                        }
                        val polyline = mapView.overlays.find { it is Polyline && it.id == "ROUTE" } as? Polyline
                        polyline?.setPoints(currentRoutePoints.toList())
                    }

                    if (currentRoutePoints.size < 10) {
                        val endPt = currentRoutePoints.last()
                        val lDest = android.location.Location("").apply { latitude = endPt.latitude; longitude = endPt.longitude }
                        val distToDest = loc.distanceTo(lDest)
                        
                        if (distToDest < 30f) { 
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
                    controller.setCenter(GeoPoint(10.996, -63.804)) 
                    
                    post { 
                        controller.setZoom(19.0)
                        invalidate() 
                    }
                    onResume()
                    setMultiTouchControls(true)
                    setHasTransientState(true)

                    val mReceive = object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
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
                                    // ADIÓS MANO VERDE: Pintamos un PIN Minimalista y profesional del color de la UI
                                    icon = BitmapDrawable(ctx.resources, drawCustomPin(uiColor))
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    title = "Destino"
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
                view.onResume()
                view.setMultiTouchControls(isFullScreen)
                
                if (isDarkMode) {
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

                carMarker?.icon = drawVehicleBitmap(vehicleType, uiColor)
                view.invalidate()
            }
        )

        Column(modifier = Modifier.align(Alignment.BottomEnd).padding(if (isFullScreen) 32.dp else 16.dp), horizontalAlignment = Alignment.End) {
            
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
                        icon = { Icon(Icons.Default.Close, "Cancelar") }, 
                        text = { Text("Cancelar") }, 
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )

                    if (currentRoutePoints.isEmpty()) {
                        ExtendedFloatingActionButton(
                            onClick = {
                                coroutineScope.launch {
                                    val start = lastKnownLocation
                                    val dest = selectedDestination
                                    if (start != null && dest != null) {
                                        routeDistanceText = "Calculando ruta..."
                                        val urlStr = "https://router.project-osrm.org/route/v1/driving/${start.longitude},${start.latitude};${dest.longitude},${dest.latitude}?overview=full&geometries=geojson"
                                        try {
                                            val result = withContext(Dispatchers.IO) {
                                                val url = URL(urlStr)
                                                val conn = url.openConnection() as HttpURLConnection
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
                                                    id = "ROUTE"
                                                    setPoints(currentRoutePoints.toList())
                                                    // LÍNEA CARPLAY BLUE FIJA
                                                    outlinePaint.color = android.graphics.Color.parseColor("#007AFF")
                                                    outlinePaint.strokeWidth = 22f
                                                    outlinePaint.strokeCap = Paint.Cap.ROUND
                                                    outlinePaint.strokeJoin = Paint.Join.ROUND
                                                }
                                                mapView.overlays.add(0, polyline)
                                                
                                                isFollowingLocation = true 
                                                mapView.invalidate()
                                            }
                                        } catch (e: Exception) { 
                                            routeDistanceText = "Error de red/GPS"
                                        }
                                    } else {
                                        routeDistanceText = "Esperando GPS..."
                                    }
                                }
                            },
                            icon = { Icon(Icons.Default.Directions, "Trazar") }, 
                            text = { Text("Ir") }, 
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            FloatingActionButton(
                onClick = {
                    isFollowingLocation = true
                    lastKnownLocation?.let { 
                        mapView.controller.animateTo(GeoPoint(it.latitude, it.longitude))
                        if (it.hasBearing()) mapView.mapOrientation = -it.bearing
                    }
                },
                containerColor = if(isFollowingLocation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Icon(Icons.Default.MyLocation, "Centrar", tint = if(isFollowingLocation) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
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
    
    // Gota / Pin moderno
    val path = Path().apply {
        moveTo(45f, 90f)
        cubicTo(45f, 90f, 10f, 50f, 10f, 35f)
        cubicTo(10f, 15f, 25f, 5f, 45f, 5f)
        cubicTo(65f, 5f, 80f, 15f, 80f, 35f)
        cubicTo(80f, 50f, 45f, 90f, 45f, 90f)
        close()
    }
    canvas.drawPath(path, paint)
    
    // Circulo interno blanco
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(45f, 35f, 12f, paint)
    
    return BitmapDrawable(android.content.res.Resources.getSystem(), bitmap).bitmap
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
        "FLECHA" -> {
            val path = Path().apply { moveTo(size / 2f / scale, 10f); lineTo((size - 20f) / scale, (size - 15f) / scale); lineTo(size / 2f / scale, (size - 30f) / scale); lineTo(20f / scale, (size - 15f) / scale); close() }
            canvas.drawPath(path, bodyPaint)
        }
        "SEDAN" -> {
            canvas.drawRoundRect(25f, 10f, 75f, 90f, 15f, 15f, bodyPaint)
            canvas.drawRoundRect(30f, 30f, 70f, 45f, 5f, 5f, glassPaint)
            canvas.drawRoundRect(30f, 65f, 70f, 75f, 5f, 5f, glassPaint)
            canvas.drawCircle(35f, 15f, 6f, lightPaint); canvas.drawCircle(65f, 15f, 6f, lightPaint)
        }
        "HATCHBACK" -> {
            canvas.drawRoundRect(25f, 20f, 75f, 85f, 12f, 12f, bodyPaint)
            canvas.drawRoundRect(30f, 40f, 70f, 55f, 5f, 5f, glassPaint) 
            canvas.drawRoundRect(30f, 75f, 70f, 82f, 3f, 3f, glassPaint)
            canvas.drawCircle(35f, 25f, 6f, lightPaint); canvas.drawCircle(65f, 25f, 6f, lightPaint)
        }
    }
    return bitmap
}