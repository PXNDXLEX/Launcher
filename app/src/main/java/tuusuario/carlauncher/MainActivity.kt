package com.tuusuario.carlauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
// IMPORTANTE: Importamos el Dashboard real que creamos en la otra carpeta
import com.tuusuario.carlauncher.ui.DashboardScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Por defecto iniciamos en modo oscuro (ideal para el coche)
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainAppFlow()
                }
            }
        }
    }
}

@Composable
fun MainAppFlow() {
    // Lógica simple: Si no hay permisos, mostrar Welcome, si los hay, Dashboard.
    var permissionsGranted by remember { mutableStateOf(false) }

    if (!permissionsGranted) {
        WelcomeAndPermissionsScreen(onPermissionsGranted = { permissionsGranted = true })
    } else {
        // Llamamos al Dashboard increíble que diseñamos en DashboardScreen.kt
        DashboardScreen()
    }
}

@Composable
fun WelcomeAndPermissionsScreen(onPermissionsGranted: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Bienvenido al Auto Launcher", 
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Para que la navegación y el velocímetro funcionen correctamente, necesitamos permisos de GPS.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onPermissionsGranted) {
            Text("Aceptar Permisos y Arrancar")
        }
    }
}