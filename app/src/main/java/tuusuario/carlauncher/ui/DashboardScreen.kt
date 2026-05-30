package com.tuusuario.carlauncher.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.BatteryManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tuusuario.carlauncher.services.GlobalState
import com.tuusuario.carlauncher.ui.map.NavigationMap
import com.tuusuario.carlauncher.ui.widgets.SpeedometerWidget
import com.tuusuario.carlauncher.ui.widgets.MusicPlayerWidget
import com.tuusuario.carlauncher.ui.widgets.YouTubeWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mapbox.geojson.Point
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.dsl.cameraOptions
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.ui.platform.LocalLifecycleOwner

/**
 * Turn-by-turn instruction from OSRM steps.
 */
data class RouteStep(
    val maneuverType: String,   // "turn", "new name", "depart", "arrive", "end of road", "roundabout", "fork", "merge"
    val modifier: String,       // "left", "right", "straight", "uturn", "slight left", "slight right", "sharp left", "sharp right"
    val distance: Double,       // meters for this step
    val streetName: String,
    val maneuverLat: Double,
    val maneuverLon: Double
)

object NavigationState { 
    val showGlowTuning = mutableStateOf(false)
    val currentSpeedKmH = mutableStateOf(0f) 
    val isBraking = mutableStateOf(false)
    var lastZeroSpeedTime: Long = 0L
    val currentLocation = mutableStateOf<android.location.Location?>(null)
    val selectedHistoryRoute = mutableStateOf<com.tuusuario.carlauncher.services.DailyRoute?>(null)
    val selectedDashcamRoute = mutableStateOf<List<com.tuusuario.carlauncher.services.RoutePoint>?>(null)

    // ── Flags de inicialización real para la pantalla de bienvenida ──────────
    /** true en cuanto llega el primer fix de GPS con velocidad */
    val isGpsReady = mutableStateOf(false)
    /** true cuando el mapa Mapbox termina de cargar su primer estilo */
    val isMapStyleReady = mutableStateOf(false)
    /** true cuando AppSettings ya terminó de leer SharedPreferences */
    val isSettingsLoaded = mutableStateOf(false)

    // ── ACTIVE NAVIGATION (persists across tab switches) ──
    // Usamos Point de Mapbox GeoJSON como tipo estándar de coordenada
    val activeDestination = mutableStateOf<Point?>(null)
    val activeRoutePoints = mutableStateListOf<Point>()
    val activeRouteDistance = mutableStateOf("")
    val activeRouteSteps = mutableStateListOf<RouteStep>()
    val cachedRouteJson = mutableStateOf<String?>(null)
    val isRouteActive = mutableStateOf(false)
    
    /** Selected segment for history viewing */
    val selectedHistorySegment = mutableStateOf<com.tuusuario.carlauncher.services.RouteSegment?>(null)
    
    fun clearActiveRoute() {
        activeDestination.value = null
        activeRoutePoints.clear()
        activeRouteDistance.value = ""
        activeRouteSteps.clear()
        cachedRouteJson.value = null
        isRouteActive.value = false
    }
}



@Composable
fun DashboardScreen(onToggleTheme: () -> Unit, isDarkMode: Boolean) {
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    // Leemos el color activo directamente del AppSettings global
    val activeUiColor = Color(AppSettings.uiColor.value)

    // FIX: rememberSaveable impide que te saque de la pantalla actual o reinicie YouTube al rotar
    var currentScreen by rememberSaveable { mutableStateOf("DASHBOARD") } 
    var showYoutubeInDashboard by rememberSaveable { mutableStateOf(false) }
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var showWelcome by rememberSaveable { mutableStateOf(true) }

    val youtubeContent = remember { movableContentOf { YouTubeWidget() } }

    LaunchedEffect(Unit) {
        if (showWelcome) {
            // Esperar al menos 2 segundos para que la animación se aprecie
            val startMs = System.currentTimeMillis()
            // Luego esperar a que los 3 sistemas estén listos (o max 8s de timeout)
            while (System.currentTimeMillis() - startMs < 8000) {
                val elapsed = System.currentTimeMillis() - startMs
                val allReady = NavigationState.isSettingsLoaded.value &&
                               NavigationState.isMapStyleReady.value &&
                               NavigationState.isGpsReady.value
                if (elapsed >= 2000 && allReady) break
                delay(100)
            }
            showWelcome = false
        }
        while (true) {
            val now = LocalDateTime.now()
            currentTime = now.format(DateTimeFormatter.ofPattern("HH:mm"))
            currentDate = now.format(DateTimeFormatter.ofPattern("EEE, dd MMM"))
            delay(1000)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (isLandscape) {
        Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier.width(80.dp).fillMaxHeight(), 
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(currentTime, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = activeUiColor)
                        Text(currentDate, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        
                        // Indicadores de sistema (Batería, Señal) - Ahora más compactos
                        SystemStatusRow()

                        Spacer(modifier = Modifier.height(16.dp))
                        
                        IconButton(onClick = { currentScreen = "DASHBOARD" }) { Icon(Icons.Default.Dashboard, "Dashboard", tint = if (currentScreen == "DASHBOARD") activeUiColor else MaterialTheme.colorScheme.onSurface) }
                        IconButton(onClick = { currentScreen = "MAPA_FULL" }) { Icon(Icons.Default.Map, "Mapa", tint = if (currentScreen == "MAPA_FULL") activeUiColor else MaterialTheme.colorScheme.onSurface) }
                        IconButton(onClick = { currentScreen = "YOUTUBE" }) { Icon(Icons.Default.OndemandVideo, "YouTube", tint = if (currentScreen == "YOUTUBE") activeUiColor else MaterialTheme.colorScheme.onSurface) }
                        IconButton(onClick = { currentScreen = "RUTAS" }) { Icon(Icons.Default.History, "Rutas", tint = if (currentScreen == "RUTAS") activeUiColor else MaterialTheme.colorScheme.onSurface) }
                        IconButton(onClick = { currentScreen = "VIDEOS" }) { Icon(Icons.Default.VideoLibrary, "Videos", tint = if (currentScreen == "VIDEOS") activeUiColor else MaterialTheme.colorScheme.onSurface) }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        IconButton(onClick = { showSettingsDialog = true }) { Icon(Icons.Default.Settings, "Ajustes") }
                        IconButton(onClick = onToggleTheme) { Icon(if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode, "Tema") }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                MainContentArea(currentScreen, isLandscape, youtubeContent, showYoutubeInDashboard, { showYoutubeInDashboard = it }, isDarkMode)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    MainContentArea(currentScreen, isLandscape, youtubeContent, showYoutubeInDashboard, { showYoutubeInDashboard = it }, isDarkMode)
                }
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.8f)) {
                    NavigationBarItem(selected = currentScreen == "DASHBOARD", onClick = { currentScreen = "DASHBOARD" }, icon = { Icon(Icons.Default.Dashboard, "Dashboard", tint = if (currentScreen == "DASHBOARD") activeUiColor else MaterialTheme.colorScheme.onSurface) })
                    NavigationBarItem(selected = currentScreen == "MAPA_FULL", onClick = { currentScreen = "MAPA_FULL" }, icon = { Icon(Icons.Default.Map, "Mapa", tint = if (currentScreen == "MAPA_FULL") activeUiColor else MaterialTheme.colorScheme.onSurface) })
                    NavigationBarItem(selected = currentScreen == "YOUTUBE", onClick = { currentScreen = "YOUTUBE" }, icon = { Icon(Icons.Default.OndemandVideo, "YouTube", tint = if (currentScreen == "YOUTUBE") activeUiColor else MaterialTheme.colorScheme.onSurface) })
                    NavigationBarItem(selected = currentScreen == "RUTAS", onClick = { currentScreen = "RUTAS" }, icon = { Icon(Icons.Default.History, "Rutas", tint = if (currentScreen == "RUTAS") activeUiColor else MaterialTheme.colorScheme.onSurface) })
                    NavigationBarItem(selected = currentScreen == "VIDEOS", onClick = { currentScreen = "VIDEOS" }, icon = { Icon(Icons.Default.VideoLibrary, "Videos", tint = if (currentScreen == "VIDEOS") activeUiColor else MaterialTheme.colorScheme.onSurface) })
                    NavigationBarItem(selected = false, onClick = { showSettingsDialog = true }, icon = { Icon(Icons.Default.Settings, "Ajustes") })
                }
            }
        }

        // ── BOTÓN DASHCAM ──
        // En landscape: arriba a la derecha (separado del widget de música que está abajo)
        // En portrait: arriba a la derecha como siempre
        val lifecycleOwner = LocalLifecycleOwner.current
        val isRec = com.tuusuario.carlauncher.services.DashcamManager.isRecording.value
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = if (isLandscape) 96.dp else 0.dp, top = 16.dp, end = 16.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Surface(
                shape = CircleShape,
                color = if (isRec) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isRec) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .size(56.dp)
                    .pointerInput(isRec) {
                        detectTapGestures(
                            onTap = {
                                if (isRec) {
                                    com.tuusuario.carlauncher.services.DashcamManager.stopRecording()
                                } else {
                                    com.tuusuario.carlauncher.services.DashcamManager.startRecording(context, lifecycleOwner)
                                }
                            },
                            onLongPress = {
                                com.tuusuario.carlauncher.services.DashcamManager.cycleCamera(context, lifecycleOwner)
                            }
                        )
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (isRec) Icons.Default.Stop else Icons.Default.Videocam, "Dashcam")
                }
            }
        }


        var offsetX by remember { mutableStateOf(0f) }
        AnimatedVisibility(
            visible = GlobalState.showPopup.value,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(8.dp),
                modifier = Modifier.width(350.dp).offset { IntOffset(offsetX.roundToInt(), 0) }.pointerInput(Unit) {
                    detectHorizontalDragGestures(onDragEnd = { if (abs(offsetX) > 150f) GlobalState.showPopup.value = false; offsetX = 0f }) { change, dragAmount -> change.consume(); offsetX += dragAmount }
                }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(GlobalState.popupApp.value, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(GlobalState.popupMessage.value, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=0.8f))
                }
            }
        }

        // Menú de configuración con animación fluida
        AnimatedVisibility(
            visible = showSettingsDialog,
            enter = fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.9f, animationSpec = tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)),
            exit = fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 0.9f, animationSpec = tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            androidx.activity.compose.BackHandler(enabled = showSettingsDialog) {
                showSettingsDialog = false
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = { showSettingsDialog = false }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Evitar cerrar al hacer clic dentro de la tarjeta
                )) {
                    PremiumSettingsDialog(
                        onDismiss = { showSettingsDialog = false },
                        onNavigateToMap = {
                            currentScreen = "MAPA_FULL"
                            showSettingsDialog = false
                            NavigationState.showGlowTuning.value = true
                        }
                    )
                }
            }
        }

        if (NavigationState.selectedHistoryRoute.value != null || NavigationState.selectedDashcamRoute.value != null) {
            RouteMapFloatingDialog(onDismiss = {
                NavigationState.selectedHistoryRoute.value = null
                NavigationState.selectedHistorySegment.value = null
                NavigationState.selectedDashcamRoute.value = null
            })
        }

        if (com.tuusuario.carlauncher.services.DashcamManager.isRecording.value) {
            DashcamPreviewOverlay()
        }

        AnimatedVisibility(
            visible = showWelcome,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(800)) + scaleOut(animationSpec = tween(800), targetScale = 1.08f),
            modifier = Modifier.fillMaxSize()
        ) {
            CinematicWelcomeScreen(
                accentColor   = activeUiColor,
                gpsReady      = NavigationState.isGpsReady.value,
                mapReady      = NavigationState.isMapStyleReady.value,
                settingsReady = NavigationState.isSettingsLoaded.value
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumSettingsDialog(onDismiss: () -> Unit, onNavigateToMap: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var expandedSection by remember { mutableStateOf("") }
    var showSpeedoCropper by remember { mutableStateOf(false) }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }

    val speedoBgPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { pendingCropUri = it; showSpeedoCropper = true }
    }

    // Ya no usamos Dialog() nativo, la tarjeta se renderiza directamente para ser animada
    Card(
        modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
    ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), MaterialTheme.colorScheme.surface)))
                        .padding(20.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Configuración", fontWeight = FontWeight.Black, fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("Personaliza tu experiencia", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar", modifier = Modifier.size(28.dp)) }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ── APARIENCIA ──
                    SettingsGroupCard("Apariencia") {
                        SettingsRow(Icons.Default.Palette, "Color de Interfaz", "Personaliza el color principal",
                            isExpanded = expandedSection == "color_ui",
                            onClick = { expandedSection = if (expandedSection == "color_ui") "" else "color_ui" }
                        ) { ColorPicker(AppSettings.uiColor.value) { AppSettings.setUiColor(it) } }

                        SettingsDivider()

                        SettingsRowSwitch(
                            icon = if (AppSettings.isDarkMode.value) Icons.Default.DarkMode else Icons.Default.LightMode,
                            title = "Modo Oscuro",
                            checked = AppSettings.isDarkMode.value,
                            onCheckedChange = { AppSettings.setDarkMode(it) }
                        )
                    }

                    // ── VEHÍCULO ──
                    SettingsGroupCard("Vehículo") {
                        SettingsRow(Icons.Default.DirectionsCar, "Ícono del Vehículo", AppSettings.vehicleType.value,
                            isExpanded = expandedSection == "vehicle_type",
                            onClick = { expandedSection = if (expandedSection == "vehicle_type") "" else "vehicle_type" }
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("FLECHA", "SEDAN", "HATCHBACK", "SPORT", "TAXI", "STYLUS", "CORSA").forEach { option ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                            .background(if (AppSettings.vehicleType.value == option) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                                            .clickable { AppSettings.setVehicleType(option) }.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(selected = AppSettings.vehicleType.value == option, onClick = { AppSettings.setVehicleType(option) }, colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(option, fontWeight = if (AppSettings.vehicleType.value == option) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Escala del modelo 3D: ${String.format("%.4f", AppSettings.vehicle3DScale.value)}", fontSize = 14.sp)
                                Slider(
                                    value = AppSettings.vehicle3DScale.value,
                                    onValueChange = { AppSettings.setVehicle3DScale(it) },
                                    valueRange = 0.005f..0.02f,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                                )
                            }
                        }
                        SettingsDivider()
                        SettingsRow(Icons.Default.LocationOn, "Color del Icono en Mapa", "Color independiente al de la interfaz",
                            isExpanded = expandedSection == "map_icon_color",
                            onClick = { expandedSection = if (expandedSection == "map_icon_color") "" else "map_icon_color" }
                        ) { ColorPicker(AppSettings.mapIconColor.value) { AppSettings.setMapIconColor(it) } }
                        
                        SettingsDivider()
                        SettingsRow(
                            icon = Icons.Default.Tune,
                            title = "Ajustar Luces y Dimensiones",
                            subtitle = "Calibración fina de faros, escala y brillos en el mapa",
                            onClick = onNavigateToMap
                        )
                    }

                    // ── NAVEGACIÓN ──
                    SettingsGroupCard("Navegación") {
                        SettingsRow(
                            icon = Icons.Default.Map,
                            title = "Estilo de Mapa",
                            subtitle = when(AppSettings.mapStyle.value) {
                                "DARK" -> "Modo Noche"
                                "NEON" -> "Neon Electric"
                                "SATELLITE" -> "Vista Satelital"
                                else -> "Modo Día"
                            },
                            isExpanded = expandedSection == "map_style",
                            onClick = { expandedSection = if (expandedSection == "map_style") "" else "map_style" }
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                                listOf("LIGHT" to "Día", "DARK" to "Noche", "NEON" to "Neon", "SATELLITE" to "Sat.").forEach { (style, label) ->
                                    FilterChip(
                                        selected = AppSettings.mapStyle.value == style,
                                        onClick = { AppSettings.setMapStyle(style) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }

                        SettingsDivider()
                        SettingsRowSwitch(
                            icon = Icons.Default.DirectionsCar,
                            title = "Modo Test de Movimiento",
                            checked = AppSettings.isGpsSimulationMode.value,
                            onCheckedChange = { AppSettings.setGpsSimulationMode(it) }
                        )
                    }

                    // ── VELOCÍMETRO ──
                    SettingsGroupCard("Velocímetro") {
                        // Vista previa en vivo del velocímetro
                        val configuration = LocalConfiguration.current
                        val previewIsLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                        val previewHeight = if (previewIsLandscape) 280.dp else 200.dp
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(previewHeight)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (AppSettings.isDarkMode.value) Color(0xFF1A1A2E) 
                                    else Color(0xFFF0F0F5)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            SpeedometerWidget()
                            // Etiqueta
                            Text(
                                "Vista Previa • ${AppSettings.speedoStyle.value}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
                            )
                        }

                        SettingsDivider()

                        SettingsRow(Icons.Default.Speed, "Estilo del Tablero", AppSettings.speedoStyle.value,
                            isExpanded = expandedSection == "speedo_style",
                            onClick = { expandedSection = if (expandedSection == "speedo_style") "" else "speedo_style" }
                        ) {
                            val opts = listOf("PREMIUM", "NEON", "RACING", "CYBER", "AURA", "VORTEX", "QUANTUM", "PULSAR", "PLASMA", "ANIME", "KAIJU", "OMNIMON", "SHONEN", "MECHA", "OVERDRIVE", "DEMONIC", "NEBULA", "CUSTOM")
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                opts.chunked(3).forEach { row ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        row.forEach { o -> FilterChip(selected = AppSettings.speedoStyle.value == o, onClick = { AppSettings.setSpeedoStyle(o) }, label = { Text(o, fontSize = 11.sp, maxLines = 1) }, modifier = Modifier.weight(1f), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary)) }
                                        repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }

                        SettingsDivider()
                        SettingsRow(Icons.Default.Lightbulb, "Color Retroiluminación", "Color independiente",
                            isExpanded = expandedSection == "speedo_color",
                            onClick = { expandedSection = if (expandedSection == "speedo_color") "" else "speedo_color" }
                        ) { ColorPicker(AppSettings.speedoColor.value) { AppSettings.setSpeedoColor(it) } }

                        AnimatedVisibility(visible = AppSettings.speedoStyle.value == "CUSTOM") {
                            Column {
                                SettingsDivider()
                                SettingsRow(Icons.Default.Category, "Forma Base", AppSettings.customSpeedoShape.value,
                                    isExpanded = expandedSection == "custom_shape",
                                    onClick = { expandedSection = if (expandedSection == "custom_shape") "" else "custom_shape" }
                                ) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("ARC", "CIRCLE", "LINE").forEach { s -> FilterChip(selected = AppSettings.customSpeedoShape.value == s, onClick = { AppSettings.setCustomSpeedoShape(s) }, label = { Text(s) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary)) } } }

                                SettingsDivider()
                                // Selector moderno de agujas con descripciones
                                SettingsRow(Icons.Default.Straighten, "Estilo de Aguja", AppSettings.customSpeedoNeedle.value,
                                    isExpanded = expandedSection == "custom_needle",
                                    onClick = { expandedSection = if (expandedSection == "custom_needle") "" else "custom_needle" }
                                ) {
                                    val needleOptions = listOf(
                                        Triple("PLASMA", "🔥 Flama", "Aguja ondulante de fuego"),
                                        Triple("KATANA", "⚔️ Katana", "Hoja afilada japonesa"),
                                        Triple("ARROW", "➤ Flecha", "Punto + línea clásica"),
                                        Triple("NEON", "💡 Neón", "Barra con halo brillante"),
                                        Triple("LASER", "✨ Láser", "Rayo con partículas"),
                                        Triple("BOLT", "⚡ Rayo", "Zigzag eléctrico")
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        needleOptions.chunked(2).forEach { row ->
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                row.forEach { (key, icon, desc) ->
                                                    val isSelected = AppSettings.customSpeedoNeedle.value == key
                                                    Card(
                                                        modifier = Modifier.weight(1f).clickable { AppSettings.setCustomSpeedoNeedle(key) },
                                                        shape = RoundedCornerShape(12.dp),
                                                        colors = CardDefaults.cardColors(
                                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) 
                                                                           else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                                        ),
                                                        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                                                    ) {
                                                        Column(modifier = Modifier.padding(10.dp)) {
                                                            Text(icon, fontSize = 16.sp)
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            Text(key, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                                            Text(desc, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                                        }
                                                    }
                                                }
                                                repeat(2 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                                            }
                                        }
                                    }
                                }

                                SettingsDivider()
                                SettingsRow(Icons.Default.LineWeight, "Grosor", "Ajusta el grosor del arco",
                                    isExpanded = expandedSection == "custom_thickness",
                                    onClick = { expandedSection = if (expandedSection == "custom_thickness") "" else "custom_thickness" }
                                ) { Slider(value = AppSettings.customSpeedoThickness.value, onValueChange = { AppSettings.setCustomSpeedoThickness(it) }, valueRange = 0.02f..0.15f) }

                                SettingsDivider()
                                SettingsRow(Icons.Default.Image, "Imagen de Fondo",
                                    if (AppSettings.customSpeedoBgPath.value.isNotEmpty()) "Imagen configurada ✓" else "Selecciona imagen o GIF",
                                    onClick = { speedoBgPicker.launch("image/*") })
                                if (AppSettings.customSpeedoBgPath.value.isNotEmpty()) {
                                    Column(modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 8.dp)) {
                                        Text("Opacidad del fondo", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Slider(value = AppSettings.customSpeedoBgOpacity.value, onValueChange = { AppSettings.setCustomSpeedoBgOpacity(it) }, valueRange = 0.1f..1.0f)
                                        TextButton(onClick = { AppSettings.setCustomSpeedoBgPath(""); AppSettings.setCustomSpeedoBgUri("") }) {
                                            Text("Quitar imagen", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── MAPA ──
                    SettingsGroupCard("Mapa") {
                        SettingsRowSwitch(
                            icon = if (AppSettings.isMapDarkMode.value) Icons.Default.DarkMode else Icons.Default.LightMode,
                            title = "Modo Oscuro del Mapa",
                            checked = AppSettings.isMapDarkMode.value,
                            onCheckedChange = { AppSettings.setMapDarkMode(it) }
                        )
                        SettingsDivider()
                        SettingsRow(Icons.Default.CloudDownload, "Mapas Offline", "Descargar zona actual",
                            isExpanded = expandedSection == "offline_maps",
                            onClick = { expandedSection = if (expandedSection == "offline_maps") "" else "offline_maps" }
                        ) { OfflineMapDownloader(context, coroutineScope) }
                        SettingsDivider()
                    }

                    // ── SISTEMA ──
                    SettingsGroupCard("Sistema") {
                        SettingsRowSwitch(
                            icon = Icons.Default.BatterySaver,
                            title = "Ahorro de Batería",
                            checked = AppSettings.batterySaverMode.value,
                            onCheckedChange = { AppSettings.setBatterySaverMode(it) }
                        )
                        SettingsDivider()
                        SettingsRow(Icons.Default.BatteryAlert, "Optimización de Batería de Android", "Música en segundo plano",
                            onClick = { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) })
                        
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }



    // Croppers
    if (showSpeedoCropper && pendingCropUri != null) {
        com.tuusuario.carlauncher.ui.widgets.ImageCropperDialog(
            imageUri = pendingCropUri!!, outputFileName = "custom_speedo_bg.png",
            initialCropShape = com.tuusuario.carlauncher.ui.widgets.CropShape.CIRCLE,
            onCropped = { path -> AppSettings.setCustomSpeedoBgPath(path); AppSettings.setCustomSpeedoBgUri(path); showSpeedoCropper = false; pendingCropUri = null },
            onDismiss = { showSpeedoCropper = false; pendingCropUri = null }
        )
    }
}

@Composable
fun RouteHistoryScreen() {
    val allRoutes = remember { mutableStateOf(com.tuusuario.carlauncher.services.RouteTracker.getAllRoutes()) }
    val routes = allRoutes.value.filter { !it.isDeleted }
    val availableDates = routes.map { it.date }.distinct().sorted()
    
    var selectedDateIndex by remember { mutableStateOf(availableDates.lastIndex.coerceAtLeast(0)) }
    var showingTrash by remember { mutableStateOf(false) }
    val activeUiColor = Color(AppSettings.uiColor.value)

    // Refresh when coming back
    LaunchedEffect(Unit) {
        allRoutes.value = com.tuusuario.carlauncher.services.RouteTracker.getAllRoutes()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── HEADER ──
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    if (showingTrash) "Papelera" else "Historial de Rutas",
                    fontWeight = FontWeight.Bold, fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            IconButton(onClick = { showingTrash = !showingTrash }) {
                Icon(if (showingTrash) Icons.Default.List else Icons.Default.DeleteOutline, "Alternar Papelera", tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        if (showingTrash) {
            // ── TRASH VIEW (unchanged logic) ──
            val trashRoutes = allRoutes.value.filter { it.isDeleted }
            if (trashRoutes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("La papelera está vacía", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(trashRoutes.size) { i ->
                        val route = trashRoutes[i]
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Map, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Ruta del ${route.date}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    val totalPts = route.segments.sumOf { it.points.size }
                                    Text("$totalPts puntos · ${route.segments.size} segmentos", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    route.deletedAt?.let {
                                        Text("Borrado: ${it.split("T").getOrElse(0) { it }}", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                                IconButton(onClick = {
                                    route.isDeleted = false; route.deletedAt = null
                                    com.tuusuario.carlauncher.services.RouteTracker.saveRoute(route)
                                    allRoutes.value = com.tuusuario.carlauncher.services.RouteTracker.getAllRoutes()
                                }) { Icon(Icons.Default.Restore, "Restaurar", tint = MaterialTheme.colorScheme.primary) }
                            }
                        }
                    }
                }
            }
        } else {
            // ── MAIN VIEW: Day Navigation + Segments ──
            if (availableDates.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No hay rutas registradas", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Conduce al menos 10 metros para empezar", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
            } else {
                val safeIndex = selectedDateIndex.coerceIn(0, availableDates.lastIndex)
                val currentDate = availableDates[safeIndex]
                val currentRoute = routes.find { it.date == currentDate }

                // ── DAY NAVIGATION BAR ──
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { if (safeIndex > 0) selectedDateIndex = safeIndex - 1 },
                            enabled = safeIndex > 0
                        ) {
                            Icon(Icons.Default.ChevronLeft, "Día anterior",
                                tint = if (safeIndex > 0) activeUiColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(32.dp))
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(currentDate, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = activeUiColor)
                            currentRoute?.let { r ->
                                val totalPts = r.segments.sumOf { it.points.size }
                                Text("${r.segments.size} segmentos · $totalPts puntos", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        IconButton(
                            onClick = { if (safeIndex < availableDates.lastIndex) selectedDateIndex = safeIndex + 1 },
                            enabled = safeIndex < availableDates.lastIndex
                        ) {
                            Icon(Icons.Default.ChevronRight, "Día siguiente",
                                tint = if (safeIndex < availableDates.lastIndex) activeUiColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(32.dp))
                        }
                    }
                }

                // ── SEGMENTS LIST ──
                if (currentRoute != null && currentRoute.segments.isNotEmpty()) {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(currentRoute.segments.size) { segIndex ->
                            val segment = currentRoute.segments[segIndex]
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    // Open this segment's route on the map
                                    NavigationState.selectedHistorySegment.value = segment
                                    NavigationState.selectedHistoryRoute.value = currentRoute
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Time icon with accent
                                    Box(
                                        modifier = Modifier.size(48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(activeUiColor.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Timeline, null, tint = activeUiColor, modifier = Modifier.size(24.dp))
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "${segment.startTime} — ${segment.endTime}",
                                            fontWeight = FontWeight.Bold, fontSize = 16.sp
                                        )
                                        Text(
                                            "${segment.points.size} puntos registrados",
                                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(Icons.Default.ChevronRight, null, tint = activeUiColor.copy(alpha = 0.6f))
                                }
                            }
                        }

                        // Delete day button at bottom
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    currentRoute.isDeleted = true
                                    currentRoute.deletedAt = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                    com.tuusuario.carlauncher.services.RouteTracker.saveRoute(currentRoute)
                                    allRoutes.value = com.tuusuario.carlauncher.services.RouteTracker.getAllRoutes()
                                    // Adjust index
                                    val newDates = allRoutes.value.filter { !it.isDeleted }.map { it.date }.distinct().sorted()
                                    selectedDateIndex = (selectedDateIndex - 1).coerceAtLeast(0)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                            ) {
                                Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Eliminar este día")
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Sin datos para este día", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ── Componentes de Settings ──

@Composable
fun SettingsGroupCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), letterSpacing = 1.sp, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.animateContentSize(animationSpec = tween(300))) { content() }
        }
    }
}

@Composable
fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String,
    isExpanded: Boolean = false, onClick: (() -> Unit)? = null,
    expandedContent: (@Composable () -> Unit)? = null
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expandedContent != null) {
                Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "Expandir", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            } else if (onClick != null) {
                Icon(Icons.Default.ChevronRight, "Ir", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            }
        }
        if (expandedContent != null) {
            AnimatedVisibility(visible = isExpanded, enter = fadeIn(tween(200)) + expandVertically(tween(300)), exit = fadeOut(tween(150)) + shrinkVertically(tween(250))) {
                Box(modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 12.dp)) { expandedContent() }
            }
        }
    }
}

@Composable
fun SettingsRowSwitch(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(if (checked) "Activado" else "Desactivado", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary))
    }
}

@Composable
fun SettingsDivider() {
    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
}

@Composable
fun ColorPicker(selectedColor: Int, onColorSelected: (Int) -> Unit) {
    val colors = listOf(Color(0xFF007AFF), Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF4CAF50), Color(0xFFFF5722), Color.White, Color(0xFF00BCD4), Color.Yellow, Color.Cyan, Color(0xFFFFA500))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        colors.forEach { color ->
            Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(50)).background(color).border(if (selectedColor == color.toArgb()) 3.dp else 1.dp, if (selectedColor == color.toArgb()) MaterialTheme.colorScheme.onSurface else Color.Transparent, RoundedCornerShape(50)).clickable { onColorSelected(color.toArgb()) })
        }
    }
}



@Composable
fun OfflineMapDownloader(context: Context, coroutineScope: kotlinx.coroutines.CoroutineScope) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Map, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Zonas Seguras (Offline)", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Mapbox gestiona el caché de los mapas vectoriales y satelitales automáticamente en 3D de manera súper eficiente. Solo navega una vez por tu zona y quedará guardada sin necesidad de descargas manuales pesadas.", 
            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        
        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                Toast.makeText(context, "Caché Automático Activado por Mapbox", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(0.8f).height(50.dp)
        ) {
            Icon(Icons.Default.CloudDownload, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Caché Inteligente Activo", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MainContentArea(currentScreen: String, isLandscape: Boolean, youtubeContent: @Composable () -> Unit, showYoutubeInDashboard: Boolean, onToggleYoutubeInDashboard: (Boolean) -> Unit, isDarkMode: Boolean) {
    var swipeOffsetX by remember { mutableStateOf(0f) }
    val speedoOptions = listOf("PREMIUM", "NEON", "RACING", "CYBER", "AURA", "VORTEX", "QUANTUM", "PULSAR", "PLASMA", "ANIME", "KAIJU", "OMNIMON", "SHONEN", "MECHA", "OVERDRIVE", "NEBULA", "DEMONIC", "CUSTOM")
    
    val context = LocalContext.current
    val speedoSwipeModifier = Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragEnd = {
                if (swipeOffsetX > 100f) {
                    val idx = speedoOptions.indexOf(AppSettings.speedoStyle.value)
                    val newIdx = if (idx - 1 < 0) speedoOptions.size - 1 else idx - 1
                    AppSettings.setSpeedoStyle(speedoOptions[newIdx])
                } else if (swipeOffsetX < -100f) {
                    val idx = speedoOptions.indexOf(AppSettings.speedoStyle.value)
                    val newIdx = (idx + 1) % speedoOptions.size
                    AppSettings.setSpeedoStyle(speedoOptions[newIdx])
                }
                swipeOffsetX = 0f
            }
        ) { change, dragAmount -> 
            change.consume()
            swipeOffsetX += dragAmount
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Crossfade(targetState = currentScreen, animationSpec = tween(400), label = "ScreenTransition") { targetScreen ->
            when (targetScreen) {
                "MAPA_FULL" -> {
                    Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha=0.1f), RoundedCornerShape(24.dp))) {
                        NavigationMap(isFullScreen = true, isDarkMode = isDarkMode)
                    }
                }
                "YOUTUBE" -> Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(Color.Black)) { youtubeContent() }
                "RUTAS" -> Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)) { RouteHistoryScreen() }
                "VIDEOS" -> Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)) { com.tuusuario.carlauncher.ui.widgets.DashcamGalleryScreen() }
                "DASHBOARD" -> {
                    if (isLandscape) {
                        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(modifier = Modifier.weight(0.60f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha=0.1f), RoundedCornerShape(24.dp))) { NavigationMap(isDarkMode = isDarkMode) }
                            Column(modifier = Modifier.weight(0.40f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Box(modifier = Modifier.weight(0.5f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha=0.8f)).then(speedoSwipeModifier)) { 
                                    Crossfade(targetState = AppSettings.speedoStyle.value, animationSpec = tween(350), label = "SpeedoTransition") { _ ->
                                        SpeedometerWidget() 
                                    }
                                }
                                Box(modifier = Modifier.weight(0.5f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha=0.8f))) {
                                    DashboardMediaWidget(showYoutubeInDashboard, youtubeContent, onToggleYoutubeInDashboard)
                                }
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(modifier = Modifier.weight(0.55f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha=0.1f), RoundedCornerShape(24.dp))) { NavigationMap(isDarkMode = isDarkMode) }
                            Row(modifier = Modifier.weight(0.45f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Box(modifier = Modifier.weight(0.5f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha=0.8f)).then(speedoSwipeModifier)) { 
                                    Crossfade(targetState = AppSettings.speedoStyle.value, animationSpec = tween(350), label = "SpeedoTransition") { _ ->
                                        SpeedometerWidget() 
                                    }
                                }
                                Box(modifier = Modifier.weight(0.5f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha=0.8f))) {
                                    DashboardMediaWidget(showYoutubeInDashboard, youtubeContent, onToggleYoutubeInDashboard)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardMediaWidget(showYoutubeInDashboard: Boolean, youtubeContent: @Composable () -> Unit, onToggle: (Boolean) -> Unit) {
    if (showYoutubeInDashboard) {
        Box(modifier = Modifier.fillMaxSize()) {
            youtubeContent()
            IconButton(onClick = { onToggle(false) }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha=0.6f), RoundedCornerShape(50))) { Icon(Icons.Default.MusicNote, null, tint = Color.White) }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            MusicPlayerWidget()
            IconButton(onClick = { onToggle(true) }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.8f), RoundedCornerShape(50))) { Icon(Icons.Default.OndemandVideo, null, tint = MaterialTheme.colorScheme.onSurface) }
        }
    }
}

@Composable
fun RouteMapFloatingDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val historyRoute   = NavigationState.selectedHistoryRoute.value
    val historySegment = NavigationState.selectedHistorySegment.value
    val dashcamRoute   = NavigationState.selectedDashcamRoute.value

    val routePoints: List<Point> = remember(historyRoute, historySegment, dashcamRoute) {
        when {
            historySegment != null -> historySegment.points.map { Point.fromLngLat(it.lon, it.lat) }
            historyRoute   != null -> historyRoute.segments.flatMap { seg -> seg.points.map { Point.fromLngLat(it.lon, it.lat) } }
            dashcamRoute   != null -> dashcamRoute.map { Point.fromLngLat(it.lon, it.lat) }
            else -> emptyList()
        }
    }

    val routeColorHex = if (historyRoute != null) "#4CAF50" else "#FF9800"

    val routeTitle = when {
        historySegment != null -> "Segmento ${historySegment.startTime}\u2014${historySegment.endTime} \u00b7 ${historySegment.points.size} puntos"
        historyRoute   != null -> {
            val totalPts = historyRoute.segments.sumOf { it.points.size }
            "Ruta del ${historyRoute.date} \u00b7 $totalPts puntos"
        }
        dashcamRoute != null -> "Ruta de Video \u00b7 ${dashcamRoute.size} puntos"
        else -> ""
    }

    // MapView de Mapbox para el diálogo
    val mapView = remember { MapView(context) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.88f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        mapView.apply {
                            mapboxMap.loadStyle(Style.MAPBOX_STREETS) { style ->
                                // Fuente de ruta
                                style.addSource(geoJsonSource("dialog-route-src") {
                                    if (routePoints.size >= 2) {
                                        featureCollection(
                                            FeatureCollection.fromFeature(
                                                Feature.fromGeometry(LineString.fromLngLats(routePoints))
                                            )
                                        )
                                    } else {
                                        featureCollection(FeatureCollection.fromFeatures(emptyList()))
                                    }
                                })
                                // Capa de ruta
                                style.addLayer(lineLayer("dialog-route-layer", "dialog-route-src") {
                                    lineColor(routeColorHex)
                                    lineWidth(5.0)
                                    lineCap(LineCap.ROUND)
                                    lineJoin(LineJoin.ROUND)
                                })

                                // Marcadores inicio/fin
                                if (routePoints.isNotEmpty()) {
                                    val pm = annotations.createPointAnnotationManager()
                                    val pinBmp = com.tuusuario.carlauncher.ui.map.drawCustomPin(
                                        android.graphics.Color.parseColor(routeColorHex)
                                    )
                                    pm.create(PointAnnotationOptions().withPoint(routePoints.first()).withIconImage(pinBmp))
                                    if (routePoints.size > 1) {
                                        pm.create(PointAnnotationOptions().withPoint(routePoints.last()).withIconImage(pinBmp))
                                    }

                                    // Ajustar cámara a la ruta
                                    val lngValues = routePoints.map { it.longitude() }
                                    val latValues = routePoints.map { it.latitude() }
                                    val centerLng = (lngValues.min() + lngValues.max()) / 2.0
                                    val centerLat = (latValues.min() + latValues.max()) / 2.0
                                    mapboxMap.setCamera(cameraOptions {
                                        center(Point.fromLngLat(centerLng, centerLat))
                                        zoom(14.0)
                                        pitch(0.0)
                                    })
                                }
                            }
                        }
                    }
                )

                // Header overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Map, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(routeTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Cerrar", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
fun DashcamPreviewOverlay() {
    val preview = com.tuusuario.carlauncher.services.DashcamManager.activePreview.value ?: return

    var offsetX by remember { mutableStateOf(40f) }
    var offsetY by remember { mutableStateOf(120f) }

    Card(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
            .width(220.dp)
            .height(160.dp),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).also { pv ->
                        pv.scaleType = PreviewView.ScaleType.FILL_CENTER
                        preview.setSurfaceProvider(pv.surfaceProvider)
                    }
                }
            )
            // REC badge
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(Color.Red.copy(alpha = 0.88f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(Color.White, androidx.compose.foundation.shape.CircleShape)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text("REC", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            // Drag hint
            Text(
                "⠿",
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun SystemStatusRow() {
    val context = LocalContext.current
    var batteryLevel by remember { mutableStateOf(100) }
    var isCharging by remember { mutableStateOf(false) }
    var signalStatus by remember { mutableStateOf("...") }
    var signalIcon by remember { mutableStateOf(Icons.Default.SignalCellularAlt) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (level != -1 && scale != -1) {
                        batteryLevel = (level * 100 / scale.toFloat()).toInt()
                    }
                    
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                 status == BatteryManager.BATTERY_STATUS_FULL
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { try { context.unregisterReceiver(receiver) } catch(_: Exception) {} }
    }

    LaunchedEffect(Unit) {
        while(true) {
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val activeNetwork = cm.activeNetwork
                val caps = cm.getNetworkCapabilities(activeNetwork)
                
                if (caps == null) {
                    signalStatus = "Sin señal"
                    signalIcon = Icons.Default.SignalCellularConnectedNoInternet0Bar
                } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    signalStatus = "WIFI"
                    signalIcon = Icons.Default.Wifi
                } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    // Detección simplificada de generación (4G/5G)
                    signalStatus = "Red Móvil"
                    signalIcon = Icons.Default.SignalCellular4Bar
                }
            } catch (_: Exception) {}
            delay(10000)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(top = 10.dp)
            .background(Color.Black.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        // Batería
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isCharging) Icons.Default.BatteryChargingFull else if (batteryLevel < 20) Icons.Default.BatteryAlert else Icons.Default.BatteryFull, 
                null, 
                modifier = Modifier.size(15.dp),
                tint = if (batteryLevel < 20 && !isCharging) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("${batteryLevel}%", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(6.dp))

        // Señal
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(signalIcon, null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (signalStatus == "WIFI") "WIFI" else "LTE", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        }
    }
}

// ── Pantalla de Bienvenida Cinemática ───────────────────────────────────────

private data class WelcomeParticle(
    val x: Float,          // posición horizontal (0-1 relativo al ancho)
    val startY: Float,     // Y inicial (0-1 relativo al alto, empieza desde abajo)
    val speed: Float,      // velocidad de subida (0.05 - 0.2 por segundo)
    val size: Float,       // tamaño en dp
    val alpha: Float,      // opacidad base
    val twinkle: Float     // frecuencia de parpadeo
)

@Composable
fun CinematicWelcomeScreen(
    accentColor:   Color,
    settingsReady: Boolean = false,
    mapReady:      Boolean = false,
    gpsReady:      Boolean = false
) {
    // Generar partículas estables (no se recrean en cada frame)
    val particles = remember {
        List(55) {
            WelcomeParticle(
                x       = Math.random().toFloat(),
                startY  = Math.random().toFloat(),
                speed   = (0.04f + Math.random().toFloat() * 0.12f),
                size    = (2f + Math.random().toFloat() * 4f),
                alpha   = (0.3f + Math.random().toFloat() * 0.6f),
                twinkle = (3f + Math.random().toFloat() * 8f)
            )
        }
    }

    // Acumulador de tiempo para todas las animaciones visuales
    var time by remember { mutableStateOf(0f) }

    // ── Progreso REAL por fases ───────────────────────────────────────────────
    // Fase 1 → Settings (0.00 – 0.33):  mínimo 0.5s de animación de llegada
    // Fase 2 → Mapa     (0.33 – 0.66):  mínimo 0.5s adicional
    // Fase 3 → GPS      (0.66 – 1.00):  mínimo 0.5s adicional
    //
    // Cada fase tiene un "suelo" de avance lento (~0.06/s) para que la barra
    // nunca esté completamente estática, y un "techo" determinado por el flag real.
    // Cuando el flag real llega, la barra acelera hasta el siguiente hito.

    val phase1Target = if (settingsReady) 0.33f else 0.15f   // Settings
    val phase2Target = if (mapReady)      0.66f else (if (settingsReady) 0.45f else 0.15f)
    val phase3Target = if (gpsReady)      1.00f else (if (mapReady)      0.82f else phase2Target)

    val realTarget = phase3Target

    // Progreso animado: avanza suavemente hacia realTarget
    var barProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { nanos ->
                val dt = (nanos - lastNanos) / 1_000_000_000f
                lastNanos = nanos
                time = (time + dt).coerceAtMost(60f) // sin límite — espera GPS real

                // Velocidad de avance de la barra:
                // Rápida cuando el sistema ya está listo, lenta cuando espera
                val speed = if (barProgress < realTarget) {
                    if (realTarget - barProgress > 0.1f) 0.35f else 0.12f
                } else 0f
                barProgress = (barProgress + speed * dt).coerceAtMost(realTarget)
            }
        }
    }

    // ── Fase activa y texto de estado ─────────────────────────────────────────
    data class PhaseInfo(val label: String, val done: Boolean, val progress: Float)

    val phases = listOf(
        PhaseInfo("AJUSTES",  settingsReady, if (settingsReady) 1f else (barProgress / 0.33f).coerceIn(0f, 1f)),
        PhaseInfo("MAPA",     mapReady,      if (mapReady)      1f else ((barProgress - 0.33f) / 0.33f).coerceIn(0f, 1f)),
        PhaseInfo("GPS",      gpsReady,      if (gpsReady)      1f else ((barProgress - 0.66f) / 0.34f).coerceIn(0f, 1f))
    )

    val allDone = settingsReady && mapReady && gpsReady
    val activePhaseLabel = when {
        allDone        -> "SISTEMA LISTO  ✓"
        !settingsReady -> "CARGANDO AJUSTES..."
        !mapReady      -> "CARGANDO MAPA..."
        else           -> "ESPERANDO GPS..."
    }

    // Pulso de escaneo (scanner line que sube y baja sobre la barra)
    val scannerPos = (sin(time * 2.5f).toFloat() * 0.5f + 0.5f)  // 0→1→0

    // ── Texto typewriter ─────────────────────────────────────────────────────
    val fullTitle  = "CAR LAUNCHER"
    val fullSub    = "BIENVENIDO"
    val titleChars = ((time - 1.2f).coerceAtLeast(0f) / 0.08f).toInt().coerceIn(0, fullTitle.length)
    val subChars   = ((time - 0.6f).coerceAtLeast(0f) / 0.06f).toInt().coerceIn(0, fullSub.length)
    val visibleTitle = fullTitle.substring(0, titleChars)
    val visibleSub   = fullSub.substring(0, subChars)

    val iconAlpha  = ((time - 0.3f) / 0.5f).coerceIn(0f, 1f)
    val ringPulse  = 0.85f + sin(time * 4.5f).toFloat() * 0.15f

    // ─────────────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {

        // ── Capa 1: Partículas flotantes ──────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            particles.forEach { p ->
                val rawY   = p.startY - (time * p.speed) % 1f
                val yNorm  = if (rawY < 0f) rawY + 1f else rawY
                val twinkA = (0.4f + sin(time * p.twinkle + p.x * 10f).toFloat() * 0.4f).coerceIn(0f, 1f)
                drawCircle(
                    color  = accentColor.copy(alpha = p.alpha * twinkA * iconAlpha),
                    radius = p.size,
                    center = Offset(p.x * w, yNorm * h)
                )
            }
        }

        // ── Capa 2: Speed-lines + anillos ──────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f; val cy = size.height / 2f
            val burst = ((time - 0.2f) / 0.7f).coerceIn(0f, 1f)

            if (burst > 0f) {
                rotate(degrees = time * 18f, pivot = Offset(cx, cy)) {
                    for (i in 0 until 24) {
                        val rad  = Math.toRadians((i.toFloat() / 24f * 360f).toDouble())
                        val lA   = if (i % 3 == 0) 0.35f else 0.15f
                        val ls   = 90f + (if (i % 2 == 0) 20f else 0f)
                        val le   = ls + burst * (200f + (i % 5) * 40f)
                        drawLine(
                            color = accentColor.copy(alpha = lA * burst),
                            start = Offset(cx + cos(rad).toFloat() * ls, cy + sin(rad).toFloat() * ls),
                            end   = Offset(cx + cos(rad).toFloat() * le, cy + sin(rad).toFloat() * le),
                            strokeWidth = if (i % 3 == 0) 2.5f else 1f, cap = StrokeCap.Round
                        )
                    }
                }
            }
            if (burst > 0.3f) {
                val r = 110f * ringPulse
                drawCircle(accentColor.copy(alpha = 0.25f * burst), r,       Offset(cx,cy), style = Stroke(2f))
                drawCircle(accentColor.copy(alpha = 0.10f * burst), r + 18f, Offset(cx,cy), style = Stroke(1f))
            }
            if (burst > 0.5f) {
                val aA = ((burst - 0.5f) / 0.5f).coerceIn(0f, 1f)
                rotate(time * -35f, Offset(cx, cy)) {
                    for (a in 0 until 4)
                        drawArc(accentColor.copy(alpha = 0.45f * aA), a * 90f + 10f, 65f, false,
                            Offset(cx-78f,cy-78f), androidx.compose.ui.geometry.Size(156f,156f),
                            style = Stroke(3f, cap = StrokeCap.Round))
                }
                rotate(time * 25f, Offset(cx, cy)) {
                    for (a in 0 until 3)
                        drawArc(accentColor.copy(alpha = 0.25f * aA), a * 120f + 20f, 50f, false,
                            Offset(cx-95f,cy-95f), androidx.compose.ui.geometry.Size(190f,190f),
                            style = Stroke(1.5f, cap = StrokeCap.Round))
                }
            }
            if (iconAlpha > 0f) {
                drawCircle(
                    brush  = Brush.radialGradient(
                        listOf(accentColor.copy(0.35f * iconAlpha), accentColor.copy(0.10f * iconAlpha), Color.Transparent),
                        Offset(cx,cy), 130f),
                    radius = 130f, center = Offset(cx,cy))
            }
        }

        // ── Capa 3: Contenido UI ──────────────────────────────────
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.DirectionsCar, null,
                tint = accentColor.copy(alpha = iconAlpha), modifier = Modifier.size(100.dp))

            Spacer(Modifier.height(28.dp))
            Text(visibleSub, color = Color.White.copy(alpha = 0.55f * iconAlpha),
                fontSize = 13.sp, fontWeight = FontWeight.Light, letterSpacing = 6.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(visibleTitle, color = accentColor.copy(alpha = iconAlpha.coerceAtLeast(0.01f)),
                    fontSize = 36.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp)
                if (titleChars < fullTitle.length) {
                    val cA = (0.5f + sin(time * 8f).toFloat() * 0.5f).coerceIn(0f, 1f)
                    Text("|", color = accentColor.copy(alpha = cA), fontSize = 36.sp, fontWeight = FontWeight.Black)
                }
            }

            Spacer(Modifier.height(40.dp))

            // ── HUD de carga por fases ─────────────────────────────
            val hudAlpha = ((time - 0.4f) / 0.4f).coerceIn(0f, 1f)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(hudAlpha)
            ) {
                // ── Tres mini-indicadores de fase ─────────────────
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    phases.forEach { phase ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Ícono de check o spinner
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(
                                        if (phase.done) accentColor.copy(alpha = 0.2f)
                                        else Color.White.copy(alpha = 0.05f),
                                        CircleShape
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (phase.done) accentColor.copy(alpha = 0.8f)
                                                else accentColor.copy(alpha = 0.3f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (phase.done) {
                                    Text("✓", color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
                                } else {
                                    // Mini arc giratorio mientras carga
                                    Canvas(Modifier.size(16.dp)) {
                                        val a = (sin(time * 4f + phases.indexOf(phase).toFloat()).toFloat() * 0.5f + 0.5f) * 0.6f + 0.2f
                                        drawArc(
                                            color = accentColor.copy(alpha = a),
                                            startAngle = time * 180f,
                                            sweepAngle = 220f,
                                            useCenter = false,
                                            style = Stroke(2f, cap = StrokeCap.Round)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = phase.label,
                                fontSize = 8.sp,
                                color = if (phase.done) accentColor.copy(alpha = 0.9f)
                                        else Color.White.copy(alpha = 0.35f),
                                fontWeight = if (phase.done) FontWeight.Bold else FontWeight.Normal,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                // ── Etiqueta de estado activo ──────────────────────
                Text(
                    text = activePhaseLabel,
                    color = if (allDone) accentColor else accentColor.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontWeight = if (allDone) FontWeight.Bold else FontWeight.Medium,
                    letterSpacing = 3.sp
                )
                Spacer(Modifier.height(10.dp))

                // ── Barra principal HUD con scanner ───────────────
                Box(
                    modifier = Modifier.width(240.dp).height(5.dp)
                ) {
                    // Track de fondo
                    Box(
                        Modifier.fillMaxSize()
                            .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(3.dp))
                    )
                    // Relleno real
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth(barProgress)
                            .background(
                                Brush.horizontalGradient(listOf(
                                    accentColor.copy(alpha = 0.5f),
                                    accentColor,
                                    Color.White.copy(alpha = 0.85f)
                                )),
                                RoundedCornerShape(3.dp)
                            )
                    )
                    // Línea de escaneo ("scanner") que recorre el relleno
                    if (barProgress > 0.02f) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth((barProgress * scannerPos).coerceIn(0f, 1f))
                                .padding(end = 0.dp)
                        ) {
                            Box(
                                Modifier
                                    .align(Alignment.CenterEnd)
                                    .width(2.dp)
                                    .fillMaxHeight(1.5f)
                                    .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(1.dp))
                            )
                        }
                    }
                    // Divisores de fases (en 33% y 66%)
                    listOf(0.333f, 0.666f).forEach { mark ->
                        Box(
                            Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = (240 * mark).dp)
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(
                                    if (barProgress >= mark) Color.White.copy(alpha = 0.5f)
                                    else accentColor.copy(alpha = 0.25f)
                                )
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                // Porcentaje y texto de fases completadas
                Row(
                    modifier = Modifier.width(240.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${(barProgress * 100).toInt()}%",
                        color = accentColor.copy(alpha = 0.6f),
                        fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp
                    )
                    val donePhasesCount = phases.count { it.done }
                    Text(
                        "$donePhasesCount / 3 SISTEMAS",
                        color = Color.White.copy(alpha = 0.25f),
                        fontSize = 9.sp, letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

// Extensión para usar alpha directamente en Modifier desde el contexto de DashboardScreen
private fun Modifier.alpha(value: Float): Modifier = this.then(
    androidx.compose.ui.draw.alpha(value.coerceIn(0f, 1f))
)

