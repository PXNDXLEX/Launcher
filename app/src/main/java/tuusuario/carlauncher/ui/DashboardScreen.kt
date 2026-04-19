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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

// Base de datos de regiones para descarga Offline
val mapRegions = mapOf(
    "América del Sur" to mapOf(
        "Venezuela" to BoundingBox(12.5, -59.5, 0.5, -73.5),
        "Colombia" to BoundingBox(13.0, -66.5, -4.5, -79.5),
        "Argentina" to BoundingBox(-21.0, -53.0, -55.0, -74.0),
        "Brasil" to BoundingBox(5.0, -34.0, -33.0, -73.0)
    ),
    "América del Norte" to mapOf(
        "México" to BoundingBox(33.0, -86.0, 14.0, -119.0),
        "EEUU (Sur/Florida)" to BoundingBox(35.0, -79.0, 24.0, -88.0)
    ),
    "Europa" to mapOf(
        "España" to BoundingBox(44.0, 4.5, 35.0, -9.5)
    )
)

object NavigationState { val currentSpeedKmH = mutableStateOf(0f) }

class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("CarLauncherSettings", Context.MODE_PRIVATE)
    
    var vehicleType: String
        get() = prefs.getString("vehicleType", "FLECHA") ?: "FLECHA"
        set(value) = prefs.edit().putString("vehicleType", value).apply()
        
    var uiColor: Int
        get() = prefs.getInt("uiColor", Color(0xFF007AFF).toArgb()) // Azul CarPlay por defecto
        set(value) = prefs.edit().putInt("uiColor", value).apply()

    var speedoStyle: String
        get() = prefs.getString("speedoStyle", "PREMIUM") ?: "PREMIUM"
        set(value) = prefs.edit().putString("speedoStyle", value).apply()

    var speedoColor: Int
        get() = prefs.getInt("speedoColor", Color(0xFFE91E63).toArgb())
        set(value) = prefs.edit().putInt("speedoColor", value).apply()
}

object AppSettings {
    val vehicleType = mutableStateOf("FLECHA")
    val uiColor = mutableStateOf(Color(0xFF007AFF).toArgb())
    val speedoStyle = mutableStateOf("PREMIUM")
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
    var showSettingsDialog by remember { mutableStateOf(false) }

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

        // --- POPUPS DE NOTIFICACIÓN ---
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

        // --- MENÚ DE AJUSTES PREMIUM ---
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
            modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Configuración del Vehículo", fontWeight = FontWeight.Black, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar") }
                }

                TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Apariencia") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Velocímetro") })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Mapas Offline") })
                }

                Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    when (selectedTab) {
                        0 -> // TABA 1: APARIENCIA GLOBAL
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
                            }
                        1 -> // TABA 2: VELOCÍMETRO
                            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                SettingsSection("Estilo del Tablero") {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                        listOf("PREMIUM", "NEON", "RACING", "CYBER").forEach { type ->
                                            FilterChip(selected = AppSettings.speedoStyle.value == type, onClick = { AppSettings.saveSpeedoStyle(type) }, label = { Text(type) })
                                        }
                                    }
                                }
                                SettingsSection("Color de Retroiluminación (Independiente)") {
                                    ColorPicker(selectedColor = AppSettings.speedoColor.value, onColorSelected = { AppSettings.saveSpeedoColor(it) })
                                }
                            }
                        2 -> // TABA 3: MAPAS OFFLINE
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
    var selectedContinent by remember { mutableStateOf<String?>(null) }
    var showConfirmDialog by remember { mutableStateOf<Pair<String, BoundingBox>?>(null) }
    var estimatedSize by remember { mutableStateOf("Calculando...") }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Descarga mapas completos de tu país para navegar sin usar datos móviles. Las descargas cubren calles y autopistas.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            if (selectedContinent == null) {
                items(mapRegions.keys.toList()) { continent ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { selectedContinent = continent }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Text(continent, modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                item { TextButton(onClick = { selectedContinent = null }) { Text("← Volver a Continentes") } }
                val countries = mapRegions[selectedContinent]!!
                items(countries.keys.toList()) { country ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { 
                            showConfirmDialog = Pair(country, countries[country]!!)
                            estimatedSize = "Calculando peso exacto..."
                            coroutineScope.launch {
                                val dummyMap = MapView(context)
                                val cm = CacheManager(dummyMap)
                                val tiles = withContext(Dispatchers.IO) { cm.possibleTilesInArea(countries[country]!!, 10, 15) }
                                estimatedSize = "${(tiles * 15L) / 1024L} MB"
                            }
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(country, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Icon(Icons.Default.CloudDownload, "Descargar", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
        }
    }

    if (showConfirmDialog != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = null },
            title = { Text("Descargar ${showConfirmDialog!!.first}") },
            text = { Text("Se descargarán todos los mapas de calles y carreteras (Zoom 10 al 15) para evitar saturar la memoria.\n\nPeso aproximado: $estimatedSize") },
            confirmButton = {
                Button(onClick = {
                    val dummyMap = MapView(context)
                    val cm = CacheManager(dummyMap)
                    Toast.makeText(context, "Iniciando descarga en segundo plano...", Toast.LENGTH_LONG).show()
                    cm.downloadAreaAsync(context, showConfirmDialog!!.second, 10, 15)
                    showConfirmDialog = null
                }) { Text("Iniciar Descarga") }
            },
            dismissButton = { TextButton(onClick = { showConfirmDialog = null }) { Text("Cancelar") } }
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
                        Box(modifier = Modifier.weight(0.6f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha=0.1f), RoundedCornerShape(24.dp))) { NavigationMap(isDarkMode = isDarkMode) }
                        Column(modifier = Modifier.weight(0.4f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(modifier = Modifier.weight(0.5f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)) { SpeedometerWidget() }
                            Box(modifier = Modifier.weight(0.5f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)) {
                                DashboardMediaWidget(showYoutubeInDashboard, youtubeContent, onToggleYoutubeInDashboard)
                            }
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(modifier = Modifier.weight(0.5f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha=0.1f), RoundedCornerShape(24.dp))) { NavigationMap(isDarkMode = isDarkMode) }
                        Row(modifier = Modifier.weight(0.5f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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