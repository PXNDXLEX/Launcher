package com.tuusuario.carlauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition

class MapLibreTest : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        val map = MapView(this)
        map.getMapAsync { mapLibreMap ->
            mapLibreMap.setStyle(Style.Builder().fromJson("{}"))
            mapLibreMap.addMarker(MarkerOptions().position(LatLng(0.0, 0.0)))
            mapLibreMap.addPolyline(PolylineOptions().add(LatLng(0.0, 0.0)))
            val camera = CameraPosition.Builder().target(LatLng(0.0, 0.0)).zoom(10.0).tilt(45.0).bearing(90.0).build()
            mapLibreMap.cameraPosition = camera
        }
    }
}
