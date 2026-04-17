package com.tuusuario.carlauncher

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tuusuario.carlauncher.ui.DashboardScreen
import com.tuusuario.carlauncher.services.MusicNotificationService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // --- SOLUCIÓN AL TEDIO: Despertamos el servicio de música cada vez que la app abre ---
        if (NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)) {
            MusicNotificationService.reconnect(this)
        }

        setContent {
            var isDarkMode by remember { mutableStateOf(true) }
            var isLandscape by remember { mutableStateOf(true) }

            requestedOrientation = if (isLandscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT

            MaterialTheme(colorScheme = if (isDarkMode) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainAppFlow(
                        isDarkMode = isDarkMode,
                        onToggleTheme = { isDarkMode = !isDarkMode },
                        isLandscape = isLandscape,
                        onToggleOrientation = { isLandscape = !isLandscape }
                    )
                }
            }
        }
    }
}

@Composable
fun MainAppFlow(isDarkMode: Boolean, onToggleTheme: () -> Unit, isLandscape: Boolean, onToggleOrientation: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var locationGranted by remember { mutableStateOf(false) }
    var notificationsGranted by remember { mutableStateOf(false) }

    val checkPermissions = {
        locationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        notificationsGranted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkPermissions()
                // También reintentamos conexión al volver a la app
                if (notificationsGranted) MusicNotificationService.reconnect(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val locationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { checkPermissions() }

    if (!locationGranted || !notificationsGranted) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Configuración de tu Kia Rio", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(24.dp))
            
            if (!locationGranted) {
                Text("1. Necesitamos acceso al GPS para el velocímetro y el mapa.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) { Text("Permitir GPS") }
            } else if (!notificationsGranted) {
                Text("2. GPS Listo. Ahora da acceso a las Notificaciones para la música y WhatsApp.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Abrir Ajustes de Notificaciones") }
            }
        }
    } else {
        DashboardScreen(onToggleTheme, onToggleOrientation, isDarkMode, isLandscape)
    }
}