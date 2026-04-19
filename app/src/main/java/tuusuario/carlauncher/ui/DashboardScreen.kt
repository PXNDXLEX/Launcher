package com.tuusuario.carlauncher.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt
import com.tuusuario.carlauncher.services.GlobalState
import com.tuusuario.carlauncher.ui.map.NavigationMap
import com.tuusuario.carlauncher.ui.widgets.SpeedometerWidget
import com.tuusuario.carlauncher.ui.widgets.MusicPlayerWidget
import com.tuusuario.carlauncher.ui.widgets.YouTubeWidget

// Estado global para compartir velocidad con el Mapa
object NavigationState {
    val currentSpeedKmH = mutableStateOf(0f)
}

class SettingsManager(context: Context) {
    private val sharedPreferences = context.getSharedPreferences("CarLauncherSettings", Context.MODE_PRIVATE)
    
    var vehicleType: String
        get() = sharedPreferences.getString("vehicleType", "FLECHA") ?: "FLECHA"
        set(value) = sharedPreferences.edit().putString("vehicleType", value).apply()
        
    var vehicleColor: Int
        get() = sharedPreferences.getInt("vehicleColor", Color.Blue.toArgb())
        set(value) = sharedPreferences.edit().putInt("vehicleColor", value).apply()

    var speedoStyle: String
        get() = sharedPreferences.getString("speedoStyle", "PREMIUM") ?: "PREMIUM"
        set(value) = sharedPreferences.edit().putString("speedoStyle", value).apply()

    var speedoColor: Int
        get() = sharedPreferences.getInt("speedoColor", Color(0xFFE91E63).toArgb())
        set(value) = sharedPreferences.edit().putInt("speedoColor", value).apply()
}

object AppSettings {
    val vehicleType = mutableStateOf("FLECHA")
    val vehicleColor = mutableStateOf(Color.Blue.toArgb())
    val speedoStyle = mutableStateOf("PREMIUM")
    val speedoColor = mutableStateOf(Color(0xFFE91E63).toArgb())
    
    private var settingsManager: SettingsManager? = null

    fun initialize(context: Context) {
        if (settingsManager == null) {
            settingsManager = SettingsManager(context)
            vehicleType.value = settingsManager!!.vehicleType
            vehicleColor.value = settingsManager!!.vehicleColor
            speedoStyle.value = settingsManager!!.speedoStyle
            speedoColor.value = settingsManager!!.speedoColor
        }
    }
    fun saveVehicleType(type: String) { vehicleType.value = type; settingsManager?.vehicleType = type }
    fun saveVehicleColor(color: Int) { vehicleColor.value = color; settingsManager?.vehicleColor = color }
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
                    Text(currentTime, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                    Text(currentDate, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.weight(1f))
                    
                    IconButton(onClick = { currentScreen = "DASHBOARD" }, colors = IconButtonDefaults.iconButtonColors(contentColor = if (currentScreen == "DASHBOARD") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)) { Icon(Icons.Default.Dashboard, "Dashboard") }
                    IconButton(onClick = { currentScreen = "MAPA_FULL" }, colors = IconButtonDefaults.iconButtonColors(contentColor = if (currentScreen == "MAPA_FULL") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)) { Icon(Icons.Default.Map, "Mapa") }
                    IconButton(onClick = { currentScreen = "YOUTUBE" }, colors = IconButtonDefaults.iconButtonColors(contentColor = if (currentScreen == "YOUTUBE") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)) { Icon(Icons.Default.OndemandVideo, "YouTube") }
                    
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
                    NavigationBarItem(selected = currentScreen == "DASHBOARD", onClick = { currentScreen = "DASHBOARD" }, icon = { Icon(Icons.Default.Dashboard, "Dashboard") })
                    NavigationBarItem(selected = currentScreen == "MAPA_FULL", onClick = { currentScreen = "MAPA_FULL" }, icon = { Icon(Icons.Default.Map, "Mapa") })
                    NavigationBarItem(selected = currentScreen == "YOUTUBE", onClick = { currentScreen = "YOUTUBE" }, icon = { Icon(Icons.Default.OndemandVideo, "YouTube") })
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
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("Ajustes del Sistema") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Vehículo en Mapa:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf("FLECHA", "SEDAN", "HATCHBACK").forEach { type ->
                                FilterChip(selected = AppSettings.vehicleType.value == type, onClick = { AppSettings.saveVehicleType(type) }, label = { Text(type.take(3), fontSize = 10.sp) })
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Estilo del Velocímetro:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf("PREMIUM", "NEON", "MINIMAL").forEach { type ->
                                FilterChip(selected = AppSettings.speedoStyle.value == type, onClick = { AppSettings.saveSpeedoStyle(type) }, label = { Text(type.take(5), fontSize = 10.sp) })
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Color Principal:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        val colors = listOf(Color.Blue, Color(0xFFE91E63), Color.White, Color.Black, Color.DarkGray, Color.Green, Color.Yellow, Color.Cyan, Color.Magenta, Color(0xFFFFA500))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            colors.chunked(5).forEach { rowColors ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    rowColors.forEach { color ->
                                        Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(50)).background(color).border(2.dp, if (AppSettings.speedoColor.value == color.toArgb()) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(50))) {
                                            IconButton(onClick = { AppSettings.saveSpeedoColor(color.toArgb()); AppSettings.saveVehicleColor(color.toArgb()) }) {}
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Quitar Límite Batería a Música") }
                    }
                },
                confirmButton = { TextButton(onClick = { showSettingsDialog = false }) { Text("Cerrar") } }
            )
        }
    }
}

@Composable
fun MainContentArea(
    currentScreen: String, 
    isLandscape: Boolean, 
    youtubeContent: @Composable () -> Unit,
    showYoutubeInDashboard: Boolean,
    onToggleYoutubeInDashboard: (Boolean) -> Unit,
    isDarkMode: Boolean
) {
    val context = LocalContext.current
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