package com.tuusuario.carlauncher.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun NavigationMap(modifier: Modifier = Modifier, isFullScreen: Boolean = false) {
    val context = LocalContext.current

    // Configuración obligatoria para OSMDroid
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK) // Tema estándar de OpenStreetMap
                
                // Centramos el mapa por defecto en La Asunción, Nueva Esparta 🇻🇪
                controller.setZoom(17.0)
                controller.setCenter(GeoPoint(11.0333, -63.8500))

                // Encendemos el GPS y el radar que te sigue en el mapa
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)
                    locationOverlay.enableMyLocation()
                    locationOverlay.enableFollowLocation() // Centra la cámara en tu vehículo
                    overlays.add(locationOverlay)
                }
            }
        },
        update = { view ->
            // Si el mapa está en el cuadrito (Dashboard), bloqueamos los gestos. 
            // Si está en la pestaña de pantalla completa, permitimos hacer zoom y mover.
            view.setMultiTouchControls(isFullScreen)
        }
    )
}