package com.tuusuario.carlauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Manejador de estado para Modo Oscuro/Claro
            var isDarkMode by remember { mutableStateOf(true) }
            
            MaterialTheme(colorScheme = if (isDarkMode) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainAppFlow(
                        toggleTheme = { isDarkMode = !isDarkMode }
                    )
                }
            }
        }
    }
}

@Composable
fun MainAppFlow(toggleTheme: () -> Unit) {
    // Lógica simple: Si no hay permisos, mostrar Welcome, si los hay, Dashboard.
    var permissionsGranted by remember { mutableStateOf(false) }

    if (!permissionsGranted) {
        WelcomeAndPermissionsScreen(onPermissionsGranted = { permissionsGranted = true })
    } else {
        DashboardScreen(toggleTheme = toggleTheme)
    }
}

@Composable
fun DashboardScreen(toggleTheme: () -> Unit) {
    // Aquí iría tu barra de navegación lateral o inferior
    Row(modifier = Modifier.fillMaxSize()) {
        // Área del Mapa GPS (Izquierda/Centro)
        Box(modifier = Modifier.weight(2f)) {
            Text("Mapa de Navegación (Mapbox/Google Maps) con Coche 3D")
        }
        
        // Área de Widgets (Derecha)
        Column(modifier = Modifier.weight(1f)) {
            Text("Velocímetro (Aguja + Digital)")
            Text("Reproductor de Música (NotificationListener)")
            Button(onClick = toggleTheme) {
                Text("Cambiar Tema Oscuro/Claro")
            }
        }
    }
}

@Composable
fun WelcomeAndPermissionsScreen(onPermissionsGranted: () -> Unit) {
    Column {
        Text("Bienvenido al Auto Launcher")
        Text("Por favor, concédenos los permisos de GPS, Cámara y Notificaciones.")
        Button(onClick = onPermissionsGranted) {
            Text("Aceptar Permisos")
        }
    }
}