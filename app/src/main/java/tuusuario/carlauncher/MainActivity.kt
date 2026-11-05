package com.tuusuario.carlauncher

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tuusuario.carlauncher.services.AppLauncherRepository
import com.tuusuario.carlauncher.services.ArletAssistant
import com.tuusuario.carlauncher.services.GlobalState
import com.tuusuario.carlauncher.services.LocationEngine
import com.tuusuario.carlauncher.services.MusicNotificationService
import com.tuusuario.carlauncher.services.RouteTracker
import com.tuusuario.carlauncher.services.SpeedRecordTracker
import com.tuusuario.carlauncher.services.WeatherService
import com.tuusuario.carlauncher.ui.AppSettings
import com.tuusuario.carlauncher.ui.DashboardScreen
import com.tuusuario.carlauncher.ui.LocalBentoPalette
import com.tuusuario.carlauncher.ui.buildBentoPalette
import com.tuusuario.carlauncher.ui.buildMaterialColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    companion object {
        var instance: MainActivity? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        if (NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)) {
            MusicNotificationService.reconnect(this)
        }

        // Inicialización ligera y asíncrona de subsistemas
        AppSettings.init(this)
        GlobalState.init(this)
        WeatherService.init(this)
        AppLauncherRepository.preload(this)
        ArletAssistant.init(applicationContext, this)
        RouteTracker.init(this)
        RouteTracker.cleanupOldTrash()
        SpeedRecordTracker.init(this)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR

        setContent {
            val isDarkMode by AppSettings.isDarkMode
            val accentColor = Color(AppSettings.uiColor.value)

            val bentoPalette = remember(isDarkMode, accentColor) {
                buildBentoPalette(isDark = isDarkMode, accentColor = accentColor)
            }
            val colorScheme = remember(isDarkMode, accentColor) {
                buildMaterialColorScheme(isDark = isDarkMode, accentColor = accentColor)
            }

            CompositionLocalProvider(LocalBentoPalette provides bentoPalette) {
                MaterialTheme(colorScheme = colorScheme) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = bentoPalette.canvasBg
                    ) {
                        MainAppFlow(
                            isDarkMode = isDarkMode,
                            onToggleTheme = { AppSettings.setDarkMode(!isDarkMode) }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        LocationEngine.refreshHardwareRequest()
        if (AppSettings.arletVoiceWakeEnabled.value) {
            ArletAssistant.startContinuousListening()
        }
    }

    override fun onPause() {
        super.onPause()
        ArletAssistant.stopContinuousListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        LocationEngine.stop()
        ArletAssistant.destroy()
        if (instance == this) instance = null
    }
}

@Composable
fun MainAppFlow(isDarkMode: Boolean, onToggleTheme: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var locationGranted by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var notificationsGranted by rememberSaveable {
        mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName))
    }
    var cameraGranted by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var audioGranted by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var mediaGranted by rememberSaveable {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            else true
        )
    }
    var manageStorageGranted by rememberSaveable {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
        )
    }

    val checkPermissions = {
        locationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        notificationsGranted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        val audioWasGranted = audioGranted
        audioGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (locationGranted) {
            LocationEngine.start(context)
        }
        if (!audioWasGranted && audioGranted && AppSettings.arletVoiceWakeEnabled.value) {
            ArletAssistant.startContinuousListening()
        }
        mediaGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        else true
        manageStorageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkPermissions()
                if (notificationsGranted) {
                    MusicNotificationService.reconnect(context)
                    MusicNotificationService.instance?.refreshCurrentMedia()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { checkPermissions() }

    if (!locationGranted || !notificationsGranted || !cameraGranted || !audioGranted || !mediaGranted || !manageStorageGranted) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Configuración Inicial del Launcher",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            if (!locationGranted || !cameraGranted || !audioGranted || !mediaGranted) {
                Text(
                    "1. Necesitamos acceso a GPS, Cámara, Micrófono (Asistente Sara y Dashcam) y Archivos Multimedia.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
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
                Text(
                    "2. Concede permiso de Todos los Archivos para conservar tus rutas, récords y videos.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                }) { Text("Conceder Acceso Total") }
            } else if (!notificationsGranted) {
                Text(
                    "3. Activa el Acceso a Notificaciones para controlar Spotify, YouTube Music y reproductores Bluetooth.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }) { Text("Abrir Ajustes de Música") }
            }
        }
    } else {
        val prefs = context.getSharedPreferences("CarLauncherPrefs", android.content.Context.MODE_PRIVATE)
        var showMigrationDialog by rememberSaveable { mutableStateOf(false) }
        var migrationVideoCount by rememberSaveable { mutableStateOf(0) }
        var migrationRouteDays by rememberSaveable { mutableStateOf(0) }

        LaunchedEffect(Unit) {
            val isFirstLaunch = !prefs.getBoolean("welcomeShown", false)
            if (isFirstLaunch) {
                val (videoCount, routeDays) = withContext(Dispatchers.IO) {
                    val baseDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                        "CarLauncher"
                    )
                    val vCount = File(baseDir, "Videos").listFiles { f -> f.extension == "mp4" }?.size ?: 0
                    val rDays = File(baseDir, "Rutas").listFiles { f -> f.name.startsWith("ruta_") && f.name.endsWith(".json") }?.size ?: 0
                    vCount to rDays
                }
                if (videoCount > 0 || routeDays > 0) {
                    migrationVideoCount = videoCount
                    migrationRouteDays = routeDays
                    showMigrationDialog = true
                } else {
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
                    baseDir.listFiles()?.forEach { child -> child.deleteRecursively() }
                    prefs.edit().putBoolean("welcomeShown", true).apply()
                }
            )
        }

        DashboardScreen(onToggleTheme = onToggleTheme, isDarkMode = isDarkMode)
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
            onDismissRequest = { onKeep() },
            icon = { Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)) },
            title = { Text("¡Datos anteriores encontrados!", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Se encontraron datos compatibles de una instalación anterior:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    if (videoCount > 0) {
                        Text("🎥  $videoCount video${if (videoCount != 1) "s" else ""} de Dashcam", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (routeDays > 0) {
                        Text("🗺️  $routeDays día${if (routeDays != 1) "s" else ""} de historial de rutas", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                Button(onClick = onKeep) { Text("Conservar todo") }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { confirmWipe = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Borrar todo")
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("¿Estás seguro?", fontWeight = FontWeight.Bold) },
            text = { Text("Esta acción borrará permanentemente todos los videos y rutas guardadas.") },
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
