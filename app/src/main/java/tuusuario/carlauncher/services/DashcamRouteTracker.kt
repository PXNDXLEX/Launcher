package com.tuusuario.carlauncher.services

import android.location.Location

/**
 * DashcamRouteTracker — simplificado.
 *
 * Ya no duplica puntos GPS. El historial de ruta se maneja íntegramente por RouteTracker.
 * Este objeto ahora solo mantiene actualizada la última coordenada conocida en DashcamManager
 * para que el .ref.json incluya la posición de inicio de cada grabación.
 */
object DashcamRouteTracker {

    fun onLocationUpdate(loc: Location) {
        // Actualizar la última coordenada conocida en DashcamManager
        // Esto permite que el .ref.json guarde la posición inicial al comenzar una grabación
        DashcamManager.updateLastKnownLocation(loc.latitude, loc.longitude)
    }
}
