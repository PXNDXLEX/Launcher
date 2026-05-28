package tuusuario.carlauncher.ui.map

import org.junit.Test
import java.io.File

class ReflectionTestPuck {
    @Test
    fun testPuck() {
        val file = File("puck_dump.txt")
        try {
            val clazz = Class.forName("com.mapbox.maps.plugin.LocationPuck3D")
            file.writeText("Found LocationPuck3D\n")
            clazz.declaredFields.forEach { 
                file.appendText("Field: " + it.name + " type " + it.type.name + "\n")
            }
        } catch (e: Exception) {
            file.writeText("Error: " + e.message)
        }
    }
}
