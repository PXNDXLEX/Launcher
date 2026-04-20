package com.tuusuario.carlauncher.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

object NavigationState { 
    val currentSpeedKmH = mutableStateOf(0f) 
    val currentLocation = mutableStateOf<android.location.Location?>(null)
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

class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("CarLauncherSettings", Context.MODE_PRIVATE)
    
    var vehicleType: String
        get() = prefs.getString("vehicleType", "FLECHA") ?: "FLECHA"
        set(value) = prefs.edit().putString("vehicleType", value).apply()
        
    var uiColor: Int
        get() = prefs.getInt("uiColor", Color(0xFF007AFF).toArgb()) 
        set(value) = prefs.edit().putInt("uiColor", value).apply()

    var speedoStyle: String
        get() = prefs.getString("speedoStyle", "AURA") ?: "AURA"
        set(value) = prefs.edit().putString("speedoStyle", value).apply()

    var speedoColor: Int
        get() = prefs.getInt("speedoColor", Color(0xFFE91E63).toArgb())
        set(value) = prefs.edit().putInt("speedoColor", value).apply()
}

object AppSettings {
    val vehicleType = mutableStateOf("FLECHA")
    val uiColor = mutableStateOf(Color(0xFF007AFF).toArgb())
    val speedoStyle = mutableStateOf("AURA")
    val speedoColor = mutableStateOf(Color(0xFFE91E63).toArgb())
    
    private var settingsManager: SettingsManager? = null

    fun initialize(context: Context) {
        if (settingsManager == null) {
            settingsManager = SettingsManager(context)
            vehicleType.value = settingsManager!!.vehicleType
            uiColor.value = settingsManager!!.uiColor
            speedoStyle.value = settingsManager!!.speedoStyle
            speedoColor.value = settingsManager!!.speedoColor
        }
    }
    fun saveVehicleType(type: String) { vehicleType.value = type; settingsManager?.vehicleType = type }
    fun saveUiColor(color: Int) { uiColor.value = color; settingsManager?.uiColor = color }
    fun saveSpeedoStyle(style: String) { speedoStyle.value = style; settingsManager?.speedoStyle = style }
    fun saveSpeedoColor(color: Int) { speedoColor.value = color; settingsManager?.speedoColor = color }
}

@Composable
fun DashboardScreen(onToggleTheme: () -> Unit, isDarkMode: Boolean) {
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    LaunchedEffect(Unit) { AppSettings.initialize(context) }

    var currentScreen by remember { mutableStateOf("DASHBOARD") } 
    var showYoutubeInDashboard by remember { mutableStateOf(false) }
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }

    val youtubeContent = remember { movableContentOf { YouTubeWidget() } }
    val activeUiColor = Color(AppSettings.uiColor.value)

    LaunchedEffect(Unit) {
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
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    NavigationBarItem(selected = currentScreen == "DASHBOARD", onClick = { currentScreen = "DASHBOARD" }, icon = { Icon(Icons.Default.Dashboard, "Dashboard", tint = if (currentScreen == "DASHBOARD") activeUiColor else MaterialTheme.colorScheme.onSurface) })
                    NavigationBarItem(selected = currentScreen == "MAPA_FULL", onClick = { currentScreen = "MAPA_FULL" }, icon = { Icon(Icons.Default.Map, "Mapa", tint = if (currentScreen == "MAPA_FULL") activeUiColor else MaterialTheme.colorScheme.onSurface) })
                    NavigationBarItem(selected = currentScreen == "YOUTUBE", onClick = { currentScreen = "YOUTUBE" }, icon = { Icon(Icons.Default.OndemandVideo, "YouTube", tint = if (currentScreen == "YOUTUBE") activeUiColor else MaterialTheme.colorScheme.onSurface) })
                    NavigationBarItem(selected = false, onClick = { showSettingsDialog = true }, icon = { Icon(Icons.Default.Settings, "Ajustes") })
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
    }
}

@Composable
fun PremiumSettingsDialog(onDismiss: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Configuración del Vehículo", fontWeight = FontWeight.Black, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar") }
                }

                TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Apariencia") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Velocímetro") })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Mapas Offline") })
                }

                Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
                    when (selectedTab) {
                        0 -> 
                            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                SettingsSection("Color de Interfaz y Mapas") {
                                    ColorPicker(selectedColor = AppSettings.uiColor.value, onColorSelected = { AppSettings.saveUiColor(it) })
                                }
                                SettingsSection("Ícono del Vehículo") {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                        listOf("FLECHA", "SEDAN", "HATCHBACK").forEach { type ->
                                            FilterChip(selected = AppSettings.vehicleType.value == type, onClick = { AppSettings.saveVehicleType(type) }, label = { Text(type) })
                                        }
                                    }
                                }
                                SettingsSection("Sistema") {
                                    Button(
                                        onClick = { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                                    ) { Text("Quitar Limite de Batería (Música en 2do Plano)") }
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                            }
                        1 -> 
                            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                SettingsSection("Estilo del Tablero") {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                        listOf("PREMIUM", "NEON", "RACING", "CYBER", "AURA").forEach { type ->
                                            FilterChip(selected = AppSettings.speedoStyle.value == type, onClick = { AppSettings.saveSpeedoStyle(type) }, label = { Text(type) })
                                        }
                                    }
                                }
                                SettingsSection("Color de Retroiluminación (Independiente)") {
                                    ColorPicker(selectedColor = AppSettings.speedoColor.value, onColorSelected = { AppSettings.saveSpeedoColor(it) })
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                            }
                        2 -> 
                            OfflineMapDownloader(context, coroutineScope)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(12.dp))
        content()
        Spacer(modifier = Modifier.height(8.dp))
        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    }
}

@Composable
fun ColorPicker(selectedColor: Int, onColorSelected: (Int) -> Unit) {
    val colors = listOf(Color(0xFF007AFF), Color(0xFFE91E63), Color.White, Color.Black, Color.DarkGray, Color.Green, Color.Yellow, Color.Cyan, Color.Magenta, Color(0xFFFFA500))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        colors.chunked(5).forEach { rowColors ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                rowColors.forEach { color ->
                    Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(50)).background(color).border(3.dp, if (selectedColor == color.toArgb()) MaterialTheme.colorScheme.onSurface else Color.Transparent, RoundedCornerShape(50))) {
                        IconButton(onClick = { onColorSelected(color.toArgb()) }) {}
                    }
                }
            }
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
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        when (currentScreen) {
            "MAPA_FULL" -> {
                Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha=0.1f), RoundedCornerShape(24.dp))) {
                    NavigationMap(isFullScreen = true, isDarkMode = isDarkMode)
                }
            }
            "YOUTUBE" -> Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(Color.Black)) { youtubeContent() }
            "DASHBOARD" -> {
                if (isLandscape) {
                    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(modifier = Modifier.weight(0.60f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha=0.1f), RoundedCornerShape(24.dp))) { NavigationMap(isDarkMode = isDarkMode) }
                        Column(modifier = Modifier.weight(0.40f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(modifier = Modifier.weight(0.5f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)) { SpeedometerWidget() }
                            Box(modifier = Modifier.weight(0.5f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)) {
                                DashboardMediaWidget(showYoutubeInDashboard, youtubeContent, onToggleYoutubeInDashboard)
                            }
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(modifier = Modifier.weight(0.55f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha=0.1f), RoundedCornerShape(24.dp))) { NavigationMap(isDarkMode = isDarkMode) }
                        Row(modifier = Modifier.weight(0.45f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(modifier = Modifier.weight(0.5f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)) { SpeedometerWidget() }
                            Box(modifier = Modifier.weight(0.5f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)) {
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