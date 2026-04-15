package com.tuusuario.carlauncher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

// Importamos el Dashboard restaurado
import com.tuusuario.carlauncher.ui.DashboardScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
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
    val context = LocalContext.current
    
    var locationGranted by remember { 
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) 
    }
    
    var notificationsGranted by remember {
        mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName))
    }

    val locationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> locationGranted = isGranted }
    )

    if (!locationGranted || !notificationsGranted) {
        WelcomeAndPermissionsScreen(
            locationGranted = locationGranted,
            notificationsGranted = notificationsGranted,
            onRequestLocation = { locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
            onRequestNotifications = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
            onCheckAgain = {
                notificationsGranted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
            }
        )
    } else {
        DashboardScreen()
    }
}

@Composable
fun WelcomeAndPermissionsScreen(
    locationGranted: Boolean,
    notificationsGranted: Boolean,
    onRequestLocation: () -> Unit,
    onRequestNotifications: () -> Unit,
    onCheckAgain: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Configuración Inicial", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(24.dp))
        
        if (!locationGranted) {
            Text("1. Necesitamos acceso al GPS para el velocímetro.", textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onRequestLocation) { Text("Permitir GPS") }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (locationGranted && !notificationsGranted) {
            Text("2. Para leer tu música, necesitamos acceso a las notificaciones.", textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onRequestNotifications) { Text("Abrir Ajustes") }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onCheckAgain) { Text("Ya lo activé, continuar") }
        }
    }
}