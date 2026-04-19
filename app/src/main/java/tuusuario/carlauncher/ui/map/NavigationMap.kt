package com.tuusuario.carlauncher.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight // ESTE ES EL IMPORT QUE FALTABA
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
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
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun NavigationMap(modifier: Modifier = Modifier, isFullScreen: Boolean = false, isDarkMode: Boolean = true) {
    val context = LocalContext.current
    val vehicleType = AppSettings.vehicleType.value
    val vehicleColor = AppSettings.vehicleColor.value
    val currentSpeed = NavigationState.currentSpeedKmH.value

    val mapView = remember { MapView(context) }
    var locationOverlay by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    var selectedDestination by remember { mutableStateOf<GeoPoint?>(null) }
    var routeDistance by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) { Configuration.getInstance().userAgentValue = context.packageName }

    // AUTO-CENTRADO: Al pasar de 5 km/h, se engancha al GPS automáticamente
    LaunchedEffect(currentSpeed) {
        if (currentSpeed >= 5f && locationOverlay?.isFollowLocationEnabled == false) {
            locationOverlay?.enableFollowLocation()
            locationOverlay?.myLocation?.let { mapView.controller.animateTo(it) }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                mapView.apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    controller.setZoom(20.0) // Zoom máximo para ver bien las calles
                    setMultiTouchControls(true)
                    setHasTransientState(true)

                    // Detector de pulsación larga para trazar ruta
                    val mReceive = object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
                        override fun longPressHelper(p: GeoPoint?): Boolean {
                            if (p != null) {
                                selectedDestination = p
                                overlays.removeAll { it is Marker && it.id == "DEST" }
                                val marker = Marker(mapView).apply {
                                    position = p
                                    id = "DEST"
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

                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        val provider = GpsMyLocationProvider(ctx).apply { locationUpdateMinTime = 300; locationUpdateMinDistance = 0f }
                        val overlay = object : MyLocationNewOverlay(provider, this) {
                            override fun onLocationChanged(location: android.location.Location?, source: org.osmdroid.views.overlay.mylocation.IMyLocationProvider?) {
                                super.onLocationChanged(location, source)
                                if (location != null && location.hasBearing() && isFollowLocationEnabled) {
                                    mapOrientation = -location.bearing
                                }
                            }
                        }
                        overlay.enableMyLocation()
                        overlay.enableFollowLocation()
                        val icon = drawVehicleBitmap(vehicleType, vehicleColor)
                        overlay.setDirectionArrow(icon, icon)
                        overlay.setPersonIcon(icon)
                        overlays.add(overlay)
                        locationOverlay = overlay
                    }
                }
            },
            update = { view ->
                view.setMultiTouchControls(isFullScreen)
                
                // MODO NOCHE: Filtro que invierte los colores para modo nocturno elegante
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

                locationOverlay?.let { overlay ->
                    val icon = drawVehicleBitmap(vehicleType, vehicleColor)
                    overlay.setDirectionArrow(icon, icon)
                    overlay.setPersonIcon(icon)
                    view.invalidate()
                }
            }
        )

        Column(modifier = Modifier.align(Alignment.BottomEnd).padding(if (isFullScreen) 32.dp else 16.dp), horizontalAlignment = Alignment.End) {
            
            if (routeDistance.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    Text("Destino a: $routeDistance", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            if (selectedDestination != null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            val start = locationOverlay?.myLocation
                            val dest = selectedDestination
                            if (start != null && dest != null) {
                                // AHORA CON HTTPS Y AVISO SI EL GPS NO ESTÁ LISTO
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
                                        
                                        routeDistance = if (distanceMeters > 1000) String.format("%.1f km", distanceMeters / 1000) else "${distanceMeters.toInt()} m"

                                        val coordinates = route.getJSONObject("geometry").getJSONArray("coordinates")
                                        val geoPoints = ArrayList<GeoPoint>()
                                        for (i in 0 until coordinates.length()) {
                                            val pt = coordinates.getJSONArray(i)
                                            geoPoints.add(GeoPoint(pt.getDouble(1), pt.getDouble(0)))
                                        }
                                        
                                        mapView.overlays.removeAll { it is Polyline }
                                        val polyline = Polyline(mapView).apply {
                                            setPoints(geoPoints)
                                            outlinePaint.color = vehicleColor
                                            outlinePaint.strokeWidth = 15f
                                        }
                                        mapView.overlays.add(0, polyline)
                                        mapView.invalidate()
                                    }
                                } catch (e: Exception) { 
                                    e.printStackTrace() 
                                    // Si falla la conexión o el GPS, mostramos "Calculando..." para que sepas que el botón sí se presionó
                                    routeDistance = "Calculando... ¿GPS listo?"
                                }
                            } else {
                                routeDistance = "Esperando señal GPS..."
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.Directions, "Trazar") }, text = { Text("Ir Aquí") }, containerColor = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            FloatingActionButton(
                onClick = {
                    locationOverlay?.let { overlay ->
                        overlay.enableFollowLocation()
                        overlay.myLocation?.let { mapView.controller.animateTo(it) }
                        mapView.mapOrientation = -overlay.lastFix?.bearing!! ?: 0f
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
            ) {
                Icon(Icons.Default.MyLocation, "Centrar", tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
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
            val path = Path()
            path.moveTo(size / 2f / scale, 10f) 
            path.lineTo((size - 20f) / scale, (size - 15f) / scale) 
            path.lineTo(size / 2f / scale, (size - 30f) / scale) 
            path.lineTo(20f / scale, (size - 15f) / scale) 
            path.close()
            canvas.drawPath(path, bodyPaint)
        }
        "SEDAN" -> {
            canvas.drawRoundRect(25f, 10f, 75f, 90f, 15f, 15f, bodyPaint)
            canvas.drawRoundRect(30f, 30f, 70f, 45f, 5f, 5f, glassPaint)
            canvas.drawRoundRect(30f, 65f, 70f, 75f, 5f, 5f, glassPaint)
            canvas.drawCircle(35f, 15f, 6f, lightPaint)
            canvas.drawCircle(65f, 15f, 6f, lightPaint)
        }
        "HATCHBACK" -> {
            canvas.drawRoundRect(25f, 20f, 75f, 85f, 12f, 12f, bodyPaint)
            canvas.drawRoundRect(30f, 40f, 70f, 55f, 5f, 5f, glassPaint) 
            canvas.drawRoundRect(30f, 75f, 70f, 82f, 3f, 3f, glassPaint)
            canvas.drawCircle(35f, 25f, 6f, lightPaint)
            canvas.drawCircle(65f, 25f, 6f, lightPaint)
        }
    }
    return bitmap
}