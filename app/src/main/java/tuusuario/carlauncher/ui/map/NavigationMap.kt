package com.tuusuario.carlauncher.ui.map

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.locationcomponent.location

@Composable
fun NavigationMap(
    modifier: Modifier = Modifier,
    carIconRes: Int, // Aquí pasaremos el ID del icono (Sedán, Moto, etc.)
    isFullScreen: Boolean = false
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Recordamos la instancia del MapView para no recrearla innecesariamente
    val mapView = remember { MapView(context) }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            view.mapboxMap.loadStyle(Style.DARK) { // Modo oscuro por defecto
                // Configuración de la cámara tipo Navegación
                view.mapboxMap.setCamera(
                    CameraOptions.Builder()
                        .zoom(17.0)
                        .pitch(45.0) // Inclinación para ver el horizonte
                        .build()
                )
                
                // Activamos el componente de ubicación (el "auto")
                view.location.updateSettings {
                    enabled = true
                    pulsingEnabled = false
                    // Aquí es donde personalizas el icono según tu preferencia
                    // Por ahora usamos un marcador, pero se vincula al recurso 3D
                    locationPuck = com.mapbox.maps.plugin.locationcomponent.generated.DefaultLocationPuck2D(
                        bearingImage = context.getDrawable(carIconRes)
                    )
                }
            }
        }
    )
}