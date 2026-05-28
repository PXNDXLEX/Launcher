package com.tuusuario.carlauncher

import org.junit.Test
import com.mapbox.common.location.DeviceLocationProvider
import com.mapbox.common.location.LocationObserver
import com.mapbox.common.location.Location

class MockLocationProvider : DeviceLocationProvider {
    override fun addLocationObserver(observer: LocationObserver) {}
    override fun removeLocationObserver(observer: LocationObserver) {}
    override fun getLastLocation(callback: com.mapbox.common.location.LocationCallback) {}
    override fun getName(): String = "Mock"
}

class LocationProviderTest {
    @Test
    fun test() {
    }
}
