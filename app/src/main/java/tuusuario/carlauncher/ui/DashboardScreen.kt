package com.tuusuario.carlauncher.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
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
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
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
    val currentSpeedKmH = mutableStateOf(0f) 
    val currentLocation = mutableStateOf<android.location.Location?>(null)
    val selectedHistoryRoute = mutableStateOf<com.tuusuario.carlauncher.services.DailyRoute?>(null)
    val selectedDashcamRoute = mutableStateOf<List<com.tuusuario.carlauncher.services.RoutePoint>?>(null)
    
    // ── ACTIVE NAVIGATION (persists across tab switches) ──
    val activeDestination = mutableStateOf<org.osmdroid.util.GeoPoint?>(null)
    val activeRoutePoints = mutableStateListOf<org.osmdroid.util.GeoPoint>()
    val activeRouteDistance = mutableStateOf("")
    val activeRouteSteps = mutableStateListOf<RouteStep>()
    val cachedRouteJson = mutableStateOf<String?>(null)  // Full OSRM response for offline cache
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

// Bypass para descargas offline (evita el TileSourcePolicyException de OSM)
object CustomMapSource {
    fun create(): XYTileSource {
        return object : XYTileSource(
            "Mapnik_Bypass", 0, 19, 256, ".png", arrayOf(
                "https://a.tile.openstreetmap.org/",
                "https://b.tile.openstreetmap.org/",
                "https://c.tile.openstreetmap.org/"
            ), "© OpenStreetMap contributors"
        ) {}
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
            delay(2500)
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
                NavigationRail(modifier = Modifier.width(80.dp).fillMaxHeight(), containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(currentTime, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = activeUiColor)
                    Text(currentDate, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.weight(1f))
                    
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

        if (showSettingsDialog) {
            PremiumSettingsDialog(onDismiss = { showSettingsDialog = false })
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
            enter = fadeIn(animationSpec = tween(500)),
            exit = fadeOut(animationSpec = tween(1000)) + scaleOut(animationSpec = tween(1000), targetScale = 1.1f),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = activeUiColor, modifier = Modifier.size(120.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Bienvenido a", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Light, letterSpacing = 2.sp)
                    Text("CAR LAUNCHER", color = activeUiColor, fontSize = 42.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var expandedSection by remember { mutableStateOf("") }
    var showSpeedoCropper by remember { mutableStateOf(false) }
    var showVehicleCropper by remember { mutableStateOf(false) }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }

    val speedoBgPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { pendingCropUri = it; showSpeedoCropper = true }
    }
    val vehicleIconPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { pendingCropUri = it; showVehicleCropper = true }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                                listOf("FLECHA", "SEDAN", "HATCHBACK", "CUSTOM").forEach { option ->
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
                            }
                        }

                        AnimatedVisibility(visible = AppSettings.vehicleType.value == "CUSTOM") {
                            Column {
                                SettingsDivider()
                                SettingsRow(Icons.Default.AddPhotoAlternate, "Imagen Personalizada",
                                    if (AppSettings.customVehicleIconPath.value.isNotEmpty()) "Imagen configurada ✓" else "Selecciona una imagen",
                                    onClick = { vehicleIconPicker.launch("image/*") })
                                if (AppSettings.customVehicleIconPath.value.isNotEmpty()) {
                                    Row(modifier = Modifier.padding(start = 56.dp, bottom = 8.dp)) {
                                        TextButton(onClick = { AppSettings.setCustomVehicleIconPath(""); AppSettings.setVehicleType("SEDAN") }) {
                                            Text("Quitar imagen", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                        SettingsDivider()
                        SettingsRow(Icons.Default.LocationOn, "Color del Icono en Mapa", "Color independiente al de la interfaz",
                            isExpanded = expandedSection == "map_icon_color",
                            onClick = { expandedSection = if (expandedSection == "map_icon_color") "" else "map_icon_color" }
                        ) { ColorPicker(AppSettings.mapIconColor.value) { AppSettings.setMapIconColor(it) } }
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
    if (showVehicleCropper && pendingCropUri != null) {
        com.tuusuario.carlauncher.ui.widgets.ImageCropperDialog(
            imageUri = pendingCropUri!!, outputFileName = "custom_vehicle.png",
            initialCropShape = com.tuusuario.carlauncher.ui.widgets.CropShape.CIRCLE,
            onCropped = { path -> AppSettings.setCustomVehicleIconPath(path); showVehicleCropper = false; pendingCropUri = null },
            onDismiss = { showVehicleCropper = false; pendingCropUri = null }
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
    var showConfirmDialog by remember { mutableStateOf(false) }
    var estimatedSize by remember { mutableStateOf("Calculando...") }
    var downloadBox by remember { mutableStateOf<BoundingBox?>(null) }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Map, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Zonas Seguras (Offline)", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Evita descargar países completos (24GB+). Con esta opción, el sistema detectará tu ubicación GPS actual y descargará un cuadrante a tu alrededor de 20x20 KM, cubriendo todas las carreteras detalladas. Ideal y ligero para rodar en tu zona diaria.", 
            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        
        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                val loc = NavigationState.currentLocation.value
                if (loc != null) {
                    val lat = loc.latitude
                    val lon = loc.longitude
                    downloadBox = BoundingBox(lat + 0.1, lon + 0.1, lat - 0.1, lon - 0.1)
                    showConfirmDialog = true
                    
                    estimatedSize = "Calculando peso exacto..."
                    coroutineScope.launch {
                        val dummyMap = MapView(context)
                        dummyMap.setTileSource(CustomMapSource.create()) // BYPASS ACTIVO
                        val cm = CacheManager(dummyMap)
                        val tiles = withContext(Dispatchers.IO) { cm.possibleTilesInArea(downloadBox!!, 10, 16) } 
                        estimatedSize = "${(tiles * 18L) / 1024L} MB" 
                    }
                } else {
                    Toast.makeText(context, "Buscando satélites GPS. Intenta en 5 segundos...", Toast.LENGTH_SHORT).show()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(0.8f).height(50.dp)
        ) {
            Icon(Icons.Default.CloudDownload, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Descargar Área Actual (~200 MB)", fontWeight = FontWeight.Bold)
        }
    }

    if (showConfirmDialog && downloadBox != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Descargar Zona Actual") },
            text = { Text("Se descargarán todos los mapas de calles y carreteras (Zoom 10 al 16) en un radio de 10KM a la redonda de tu posición.\n\nPeso aproximado: $estimatedSize") },
            confirmButton = {
                Button(onClick = {
                    val dummyMap = MapView(context)
                    dummyMap.setTileSource(CustomMapSource.create()) // BYPASS ACTIVO
                    val cm = CacheManager(dummyMap)
                    Toast.makeText(context, "Iniciando descarga en segundo plano...", Toast.LENGTH_LONG).show()
                    cm.downloadAreaAsync(context, downloadBox!!, 10, 16)
                    showConfirmDialog = false
                }) { Text("Iniciar Descarga") }
            },
            dismissButton = { TextButton(onClick = { showConfirmDialog = false }) { Text("Cancelar") } }
        )
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
        when (currentScreen) {
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
                            Box(modifier = Modifier.weight(0.5f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha=0.8f)).then(speedoSwipeModifier)) { SpeedometerWidget() }
                            Box(modifier = Modifier.weight(0.5f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha=0.8f))) {
                                DashboardMediaWidget(showYoutubeInDashboard, youtubeContent, onToggleYoutubeInDashboard)
                            }
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(modifier = Modifier.weight(0.55f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha=0.1f), RoundedCornerShape(24.dp))) { NavigationMap(isDarkMode = isDarkMode) }
                        Row(modifier = Modifier.weight(0.45f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(modifier = Modifier.weight(0.5f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha=0.8f)).then(speedoSwipeModifier)) { SpeedometerWidget() }
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
    val historyRoute = NavigationState.selectedHistoryRoute.value
    val historySegment = NavigationState.selectedHistorySegment.value
    val dashcamRoute = NavigationState.selectedDashcamRoute.value

    val routePoints: List<GeoPoint> = remember(historyRoute, historySegment, dashcamRoute) {
        when {
            historySegment != null -> historySegment.points.map { GeoPoint(it.lat, it.lon) }
            historyRoute != null -> historyRoute.segments.flatMap { seg -> seg.points.map { GeoPoint(it.lat, it.lon) } }
            dashcamRoute != null -> dashcamRoute.map { GeoPoint(it.lat, it.lon) }
            else -> emptyList()
        }
    }

    val routeColor = if (historyRoute != null)
        android.graphics.Color.parseColor("#4CAF50")
    else
        android.graphics.Color.parseColor("#FF9800")

    val routeTitle = when {
        historySegment != null -> "Segmento ${historySegment.startTime}—${historySegment.endTime} · ${historySegment.points.size} puntos"
        historyRoute != null -> {
            val totalPts = historyRoute.segments.sumOf { it.points.size }
            "Ruta del ${historyRoute.date} · $totalPts puntos"
        }
        dashcamRoute != null -> "Ruta de Video · ${dashcamRoute.size} puntos"
        else -> ""
    }

    val mapView = remember { MapView(context) }

    LaunchedEffect(routePoints) {
        if (routePoints.isNotEmpty()) {
            delay(300)
            try {
                val bbox = org.osmdroid.util.BoundingBox.fromGeoPoints(routePoints)
                mapView.zoomToBoundingBox(bbox, true, 120)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    Dialog(
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
                        org.osmdroid.config.Configuration.getInstance().userAgentValue = ctx.packageName
                        mapView.apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            setBuiltInZoomControls(false)

                            if (routePoints.isNotEmpty()) {
                                val polyline = Polyline(this).apply {
                                    id = "FLOAT_ROUTE"
                                    setPoints(routePoints)
                                    outlinePaint.color = routeColor
                                    outlinePaint.strokeWidth = 14f
                                    outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                                    outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                                }
                                overlays.add(polyline)

                                val startMarker = Marker(this).apply {
                                    position = routePoints.first()
                                    icon = android.graphics.drawable.BitmapDrawable(
                                        ctx.resources,
                                        com.tuusuario.carlauncher.ui.map.drawCustomPin(android.graphics.Color.parseColor("#4CAF50"))
                                    )
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    title = "Inicio"
                                }
                                val endMarker = Marker(this).apply {
                                    position = routePoints.last()
                                    icon = android.graphics.drawable.BitmapDrawable(
                                        ctx.resources,
                                        com.tuusuario.carlauncher.ui.map.drawCustomPin(android.graphics.Color.parseColor("#F44336"))
                                    )
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    title = "Fin"
                                }
                                overlays.add(startMarker)
                                overlays.add(endMarker)

                                controller.setCenter(
                                    GeoPoint(
                                        (routePoints.first().latitude + routePoints.last().latitude) / 2,
                                        (routePoints.first().longitude + routePoints.last().longitude) / 2
                                    )
                                )
                                controller.setZoom(14.0)
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

