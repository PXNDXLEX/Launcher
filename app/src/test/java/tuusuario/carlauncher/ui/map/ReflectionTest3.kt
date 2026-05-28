package tuusuario.carlauncher.ui.map

import org.junit.Test
import java.io.File
import com.mapbox.maps.plugin.LocationPuck3D

class ReflectionTest3 {
    @Test
    fun dumpLocationPuck3D() {
        val file = File("puck3d_dump.txt")
        file.writeText("Properties in LocationPuck3D:\n")
        LocationPuck3D::class.java.declaredFields.forEach {
            file.appendText(it.name + " (" + it.type.name + ")\n")
        }
    }
}
