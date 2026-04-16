package com.tuusuario.carlauncher.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@Composable
fun NavigationMap(modifier: Modifier = Modifier, isFullScreen: Boolean = false) {
    val context = LocalContext.current
    
    // Verificamos si tenemos permiso para mostrar el puntito azul de ubicación
    val hasLocationPermission = ContextCompat.checkSelfPermission(
        context, 
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    // Una ubicación de inicio por defecto (Ej: Caracas) hasta que agarre señal
    val defaultLocation = LatLng(10.4806, -66.9036)
    
    // Configuración de la cámara en 3D
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.Builder()
            .target(defaultLocation)
            .zoom(17f)
            .tilt(60f) // ¡ESTO ES LO QUE DA EL EFECTO 3D/GPS!
            .bearing(0f)
            .build()
    }

    // Opciones del mapa (Tema oscuro y gestos)
    val mapProperties = MapProperties(
        isMyLocationEnabled = hasLocationPermission, // Muestra el puntito azul de Google
        mapType = MapType.NORMAL // Podríamos intentar cambiarle el estilo a oscuro luego con JSON
    )
    
    val mapUiSettings = MapUiSettings(
        zoomControlsEnabled = false,
        compassEnabled = isFullScreen,
        rotationGesturesEnabled = isFullScreen,
        scrollGesturesEnabled = isFullScreen,
        tiltGesturesEnabled = isFullScreen,
        myLocationButtonEnabled = false // Lo ocultamos para que parezca un dashboard integrado
    )

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = mapProperties,
        uiSettings = mapUiSettings
    ) {
        // Aquí adentro podríamos agregar marcadores (Marker) si quisiéramos poner un autito
        // Pero por ahora, isMyLocationEnabled = true ya muestra tu ubicación actual con la flechita azul
    }
}