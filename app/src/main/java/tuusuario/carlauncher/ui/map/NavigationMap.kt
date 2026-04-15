package com.tuusuario.carlauncher.ui.map

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck // Importante para el indicador base
import com.mapbox.maps.plugin.locationcomponent.LocationPuck3D      // Importante para el coche 3D
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.gestures.gestures

@Composable
fun NavigationMap(
    modifier: Modifier = Modifier,
    isFullScreen: Boolean = false,
    carModelPath: String = "asset://kia_rio_2009.glb"
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mapView = remember { MapView(context) }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            view.mapboxMap.loadStyle(Style.DARK) {
                
                // 1. Inclinación de la cámara estilo GPS
                view.mapboxMap.setCamera(
                    CameraOptions.Builder()
                        .zoom(17.5)
                        .pitch(60.0)
                        .bearing(0.0)
                        .build()
                )

                // 2. Configuración del Rastreo GPS
                view.location.updateSettings {
                    enabled = true
                    pulsingEnabled = false
                    puckBearingEnabled = true
                    puckBearing = PuckBearing.HEADING
                    
                    // ==========================================
                    // AQUÍ ESTÁ EL LOCATION PUCK
                    // ==========================================
                    
                    // OPCIÓN 1: El coche 3D (Descomenta esto cuando subas el archivo .glb a la carpeta assets)
                    /*
                    locationPuck = LocationPuck3D(
                        modelUri = carModelPath,
                        modelScale = listOf(1.5f, 1.5f, 1.5f)
                    )
                    */

                    // OPCIÓN 2: El cursor genérico de Mapbox (Usa este HOY para que la app compile sin errores)
                    locationPuck = createDefault2DPuck(withBearing = true)
                    
                    // ==========================================
                }

                // 3. Control de la pantalla táctil
                view.gestures.updateSettings {
                    rotateEnabled = isFullScreen
                    pinchToZoomEnabled = isFullScreen
                }
            }
        }
    )
}