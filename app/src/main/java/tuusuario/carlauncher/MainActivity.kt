package com.tuusuario.carlauncher

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.tuusuario.carlauncher.ui.AppSettings
import com.tuusuario.carlauncher.services.MusicNotificationService
import android.os.Environment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import java.io.File
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        if (NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)) {
            MusicNotificationService.reconnect(this)
        }

        // ¡AQUÍ ESTÁ LA MAGIA! Iniciamos la base de datos permanente antes de arrancar la interfaz
        AppSettings.init(this)
        
        // Limpiar rutas antiguas de la papelera
        com.tuusuario.carlauncher.services.RouteTracker.init(this)
        com.tuusuario.carlauncher.services.RouteTracker.cleanupOldTrash()

        setContent {
            // Ahora leemos el estado directamente de nuestro AppSettings permanente
            val isDarkMode by AppSettings.isDarkMode

            // MAGIA SENSORIAL: "FULL_SENSOR" lee el hardware del teléfono ignorando el candado de Android
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR

            MaterialTheme(colorScheme = if (isDarkMode) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainAppFlow(
                        isDarkMode = isDarkMode,
                        // Al hacer clic, guardamos la configuración para siempre
                        onToggleTheme = { AppSettings.setDarkMode(!isDarkMode) }
                    )
                }
            }
        }
    }
}

@Composable
fun MainAppFlow(isDarkMode: Boolean, onToggleTheme: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // FIX: Inicializar con el valor REAL para evitar el parpadeo. Si ya está concedido, nace en true.
    var locationGranted by rememberSaveable { 
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) 
    }
    var notificationsGranted by rememberSaveable { 
        mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)) 
    }
    var cameraGranted by rememberSaveable {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var audioGranted by rememberSaveable {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    // Android 13+ requiere READ_MEDIA_VIDEO para leer videos del almacenamiento externo
    var mediaGranted by rememberSaveable {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            else true // Android ≤ 12: cubierto por READ_EXTERNAL_STORAGE
        )
    }
    var manageStorageGranted by rememberSaveable {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) android.os.Environment.isExternalStorageManager() else true
        )
    }

    val checkPermissions = {
        locationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        notificationsGranted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        audioGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        mediaGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        else true
        manageStorageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) android.os.Environment.isExternalStorageManager() else true
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkPermissions()
                if (notificationsGranted) {
                    MusicNotificationService.reconnect(context)
                    // Resucitamos el control de música si el teléfono estaba bloqueado
                    MusicNotificationService.instance?.refreshCurrentMedia()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { checkPermissions() }

    if (!locationGranted || !notificationsGranted || !cameraGranted || !audioGranted || !mediaGranted || !manageStorageGranted) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Configuración de Launcher", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(24.dp))
            if (!locationGranted || !cameraGranted || !audioGranted || !mediaGranted) {
                Text("1. Necesitamos acceso al GPS, Cámara/Micrófono y Archivos de Video (Dashcam).", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    val perms = mutableListOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        perms.add(Manifest.permission.READ_MEDIA_VIDEO)
                    }
                    permissionLauncher.launch(perms.toTypedArray())
                }) { Text("Permitir Accesos") }
            } else if (!manageStorageGranted) {
                Text("2. Da permiso de acceso a Todos los Archivos para recuperar tus rutas antiguas de instalaciones previas.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { 
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                        context.startActivity(intent)
                    }
                }) { Text("Conceder Acceso Total") }
            } else if (!notificationsGranted) {
                Text("3. Todo Listo. Ahora da acceso a las Notificaciones para la música.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Abrir Ajustes") }
            }
        }
    } else {
        // Detectar primera apertura (en esta instalación) y datos previos
        val prefs = context.getSharedPreferences("CarLauncherPrefs", android.content.Context.MODE_PRIVATE)
        var showMigrationDialog by rememberSaveable { mutableStateOf(false) }
        var migrationVideoCount by rememberSaveable { mutableStateOf(0) }
        var migrationRouteDays by rememberSaveable { mutableStateOf(0) }

        LaunchedEffect(Unit) {
            val isFirstLaunch = !prefs.getBoolean("welcomeShown", false)
            if (isFirstLaunch) {
                val baseDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "CarLauncher"
                )
                // Contar datos existentes
                val videosDir = File(baseDir, "Videos")
                val rutasDir = File(baseDir, "Rutas")
                val videoCount = videosDir.listFiles { f -> f.extension == "mp4" }?.size ?: 0
                val routeDays = rutasDir.listFiles { f -> f.name.startsWith("ruta_") && f.name.endsWith(".json") }?.size ?: 0

                if (videoCount > 0 || routeDays > 0) {
                    migrationVideoCount = videoCount
                    migrationRouteDays = routeDays
                    showMigrationDialog = true
                } else {
                    // Marcar como visto si no hay datos
                    prefs.edit().putBoolean("welcomeShown", true).apply()
                }
            }
        }

        if (showMigrationDialog) {
            DataMigrationDialog(
                videoCount = migrationVideoCount,
                routeDays = migrationRouteDays,
                onKeep = {
                    showMigrationDialog = false
                    prefs.edit().putBoolean("welcomeShown", true).apply()
                },
                onWipeAll = {
                    showMigrationDialog = false
                    val baseDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                        "CarLauncher"
                    )
                    // Borrar todo el contenido
                    baseDir.listFiles()?.forEach { child ->
                        child.deleteRecursively()
                    }
                    prefs.edit().putBoolean("welcomeShown", true).apply()
                }
            )
        }

        DashboardScreen(onToggleTheme, isDarkMode)
    }
}

@Composable
fun DataMigrationDialog(
    videoCount: Int,
    routeDays: Int,
    onKeep: () -> Unit,
    onWipeAll: () -> Unit
) {
    var confirmWipe by remember { mutableStateOf(false) }

    if (!confirmWipe) {
        AlertDialog(
            onDismissRequest = { onKeep() }, // Cerrar = conservar
            icon = { Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)) },
            title = { Text("¡Datos anteriores encontrados!", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Se encontraron datos compatibles de una instalación anterior:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (videoCount > 0) {
                        Text("🎥  $videoCount video${if (videoCount != 1) "s" else ""} de Dashcam", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (routeDays > 0) {
                        Text("🗺️  $routeDays día${if (routeDays != 1) "s" else ""} de historial de rutas", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "¿Qué deseas hacer con estos datos?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = onKeep) {
                    Text("✅ Conservar todo")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { confirmWipe = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("🗑️ Borrar todo y empezar de cero")
                }
            }
        )
    } else {
        // Segunda confirmación antes de borrar
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("¿Estás seguro?", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
            text = { Text("Esta acción borrará permanentemente todos los videos y rutas guardadas. No se puede deshacer.") },
            confirmButton = {
                Button(
                    onClick = onWipeAll,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Sí, borrar todo") }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) { Text("Cancelar") }
            }
        )
    }
}