package com.tuusuario.carlauncher.ui.map

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.gestures.gestures

@Composable
fun NavigationMap(modifier: Modifier = Modifier, isFullScreen: Boolean = false) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mapView = remember { MapView(context) }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            view.mapboxMap.loadStyle(Style.DARK) {
                // Inclinación 3D para la conducción
                view.mapboxMap.setCamera(
                    CameraOptions.Builder().zoom(17.5).pitch(60.0).bearing(0.0).build()
                )
                // Habilitamos tu posición real
                view.location.updateSettings {
                    enabled = true
                    pulsingEnabled = false
                    puckBearingEnabled = true
                    puckBearing = PuckBearing.HEADING
                    locationPuck = createDefault2DPuck(withBearing = true)
                }
                view.gestures.updateSettings {
                    rotateEnabled = isFullScreen
                    pinchToZoomEnabled = isFullScreen
                }
            }
        }
    )
}