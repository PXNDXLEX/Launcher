package com.tuusuario.carlauncher.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import com.tuusuario.carlauncher.ui.AppSettings
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun NavigationMap(modifier: Modifier = Modifier, isFullScreen: Boolean = false) {
    val context = LocalContext.current
    val vehicleType = AppSettings.vehicleType.value
    val vehicleColor = AppSettings.vehicleColor.value

    // Recordamos el mapa y el overlay de ubicación para poder manejarlos desde el botón
    val mapView = remember { MapView(context) }
    var locationOverlay by remember { mutableStateOf<MyLocationNewOverlay?>(null) }

    LaunchedEffect(Unit) { Configuration.getInstance().userAgentValue = context.packageName }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                mapView.apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    controller.setZoom(17.5)
                    controller.setCenter(GeoPoint(11.0333, -63.8500))

                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        val provider = GpsMyLocationProvider(ctx)
                        val overlay = MyLocationNewOverlay(provider, this)
                        
                        overlay.enableMyLocation()
                        overlay.enableFollowLocation()
                        
                        // Forzamos el icono al inicializar
                        val icon = drawVehicleBitmap(vehicleType, vehicleColor)
                        overlay.setDirectionArrow(icon, icon)
                        overlay.setPersonIcon(icon)
                        
                        overlays.add(overlay)
                        locationOverlay = overlay // Lo guardamos para el botón
                    }
                }
            },
            update = { view ->
                view.setMultiTouchControls(isFullScreen)
                
                // Actualizamos el icono de forma agresiva si cambias ajustes
                locationOverlay?.let { overlay ->
                    val icon = drawVehicleBitmap(vehicleType, vehicleColor)
                    overlay.setDirectionArrow(icon, icon)
                    overlay.setPersonIcon(icon)
                    view.invalidate() // Fuerza al mapa a redibujar tu auto
                }
            }
        )

        // BOTÓN FLOTANTE PARA CENTRAR UBICACIÓN
        IconButton(
            onClick = {
                locationOverlay?.let { overlay ->
                    // 1. Reactiva el seguimiento automático si habías movido el mapa
                    overlay.enableFollowLocation()
                    // 2. Hace un salto suave hacia donde estás
                    val myLoc = overlay.myLocation
                    if (myLoc != null) {
                        mapView.controller.animateTo(myLoc)
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(if (isFullScreen) 32.dp else 16.dp) // Sube un poco si estamos en FullScreen para no tapar el otro botón
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f), RoundedCornerShape(50))
        ) {
            Icon(Icons.Default.MyLocation, "Centrar GPS", tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

// MOTOR DE DIBUJO DE VEHÍCULOS (Intacto, listo para dibujar)
fun drawVehicleBitmap(type: String, color: Int): Bitmap {
    val size = 120 // Lo aumenté a 120 para que OSMDroid no lo ignore por pequeño
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.DKGRAY; style = Paint.Style.FILL }
    val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.parseColor("#FFF59D"); style = Paint.Style.FILL }
    
    val scale = 1.2f // Factor de escala para hacerlo más visible
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
        "CAMIONETA" -> {
            canvas.drawRoundRect(20f, 5f, 80f, 95f, 10f, 10f, bodyPaint)
            canvas.drawRoundRect(25f, 25f, 75f, 40f, 5f, 5f, glassPaint)
            canvas.drawRoundRect(25f, 50f, 75f, 90f, 2f, 2f, glassPaint)
        }
        "MOTO" -> {
            canvas.drawRoundRect(45f, 10f, 55f, 30f, 5f, 5f, glassPaint)
            canvas.drawRoundRect(40f, 30f, 60f, 75f, 10f, 10f, bodyPaint)
            canvas.drawRoundRect(30f, 35f, 70f, 40f, 2f, 2f, glassPaint)
            canvas.drawRoundRect(45f, 75f, 55f, 95f, 5f, 5f, glassPaint)
        }
    }
    return bitmap
}