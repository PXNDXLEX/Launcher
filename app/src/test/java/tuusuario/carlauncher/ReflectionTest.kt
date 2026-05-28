package com.tuusuario.carlauncher

import org.junit.Test
import java.io.File
import com.mapbox.maps.plugin.locationcomponent.LocationComponentPlugin

class ReflectionTest {
    @Test
    fun dumpMethods() {
        val file = File("method_dump.txt")
        file.writeText("Methods:\n")
        LocationComponentPlugin::class.java.methods.forEach {
            file.appendText(it.name + " (" + it.parameterTypes.joinToString { p -> p.name } + ")\n")
        }
    }
}
