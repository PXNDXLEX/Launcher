package tuusuario.carlauncher.ui.map

import org.junit.Test
import java.io.File

class ReflectionTest4 {
    @Test
    fun testValue() {
        val file = File("value_dump.txt")
        try {
            val clazz = Class.forName("com.mapbox.bindgen.Value")
            file.writeText("Found com.mapbox.bindgen.Value\n")
            clazz.declaredMethods.forEach { 
                file.appendText("Method: " + it.name + " returns " + it.returnType.name + "\n")
            }
        } catch (e: Exception) {
            file.writeText("Error: " + e.message)
        }
    }
}
