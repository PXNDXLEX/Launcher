package tuusuario.carlauncher.ui.map

import org.junit.Test
import java.io.File
import com.mapbox.maps.extension.style.expressions.generated.Expression

class ReflectionTest2 {
    @Test
    fun dumpLiteralMethods() {
        val file = File("method_dump2.txt")
        file.writeText("Literal Methods in Expression:\n")
        Expression.Companion::class.java.methods.filter { it.name == "literal" }.forEach {
            file.appendText("Expression.literal(" + it.parameterTypes.joinToString { p -> p.name } + ")\n")
        }
    }
}
