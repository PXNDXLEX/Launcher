package com.tuusuario.carlauncher.ui

import android.content.Intent
import android.net.Uri
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

object AppSettings {
    val vehicleType = mutableStateOf("FLECHA")
    val vehicleColor = mutableStateOf(Color.Blue.toArgb())
}

@Composable
fun DashboardScreen(onToggleTheme: () -> Unit, onToggleOrientation: () -> Unit, isDarkMode: Boolean, isLandscape: Boolean) {
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    val context = LocalContext.current
    
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
        Row(modifier = Modifier.fillMaxSize()) {
            
            // SIDEBAR LATERAL
            NavigationRail(
                modifier = Modifier.width(80.dp).fillMaxHeight(),
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(currentTime, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                Text(currentDate, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                Spacer(modifier = Modifier.weight(1f))
                
                IconButton(
                    onClick = { currentScreen = "DASHBOARD" },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = if (currentScreen == "DASHBOARD") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                ) { Icon(Icons.Default.Dashboard, "Dashboard Principal") }
                
                IconButton(
                    onClick = { currentScreen = "MAPA_FULL" },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = if (currentScreen == "MAPA_FULL") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                ) { Icon(Icons.Default.Map, "Navegación GPS") }
                
                IconButton(
                    onClick = { currentScreen = "YOUTUBE" },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = if (currentScreen == "YOUTUBE") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                ) { Icon(Icons.Default.OndemandVideo, "YouTube Full") }

                Spacer(modifier = Modifier.height(16.dp))
                IconButton(onClick = { showSettingsDialog = true }) { Icon(Icons.Default.Settings, "Ajustes del Auto") }
                IconButton(onClick = onToggleTheme) { Icon(if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode, "Tema") }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ÁREA PRINCIPAL DINÁMICA (Sin Crossfade para evitar reinicios)
            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                when (currentScreen) {
                    "MAPA_FULL" -> {
                        Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha=0.1f), RoundedCornerShape(24.dp))) {
                            NavigationMap(isFullScreen = true)
                            ExtendedFloatingActionButton(
                                onClick = {
                                    val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q="))
                                    mapIntent.setPackage("com.google.android.apps.maps")
                                    context.startActivity(mapIntent)
                                },
                                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                                icon = { Icon(Icons.Default.Navigation, "Navegar") },
                                text = { Text("Iniciar Ruta") },
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    "YOUTUBE" -> {
                        Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(Color.Black)) {
                            youtubeContent()
                        }
                    }
                    "DASHBOARD" -> {
                        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(modifier = Modifier.weight(0.6f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha=0.1f), RoundedCornerShape(24.dp))) {
                                NavigationMap()
                            }
                            Column(modifier = Modifier.weight(0.4f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Box(modifier = Modifier.weight(0.5f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha=0.1f), RoundedCornerShape(24.dp))) {
                                    SpeedometerWidget()
                                }
                                Box(modifier = Modifier.weight(0.5f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha=0.1f), RoundedCornerShape(24.dp))) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        if (showYoutubeInDashboard) {
                                            youtubeContent()
                                            IconButton(onClick = { showYoutubeInDashboard = false }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha=0.6f), RoundedCornerShape(50))) {
                                                Icon(Icons.Default.MusicNote, "Volver a Música", tint = Color.White)
                                            }
                                        } else {
                                            MusicPlayerWidget()
                                            IconButton(onClick = { showYoutubeInDashboard = true }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))) {
                                                Icon(Icons.Default.OndemandVideo, "Ver YouTube", tint = MaterialTheme.colorScheme.onSurface)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ... El bloque de AnimatedVisibility (Notificaciones deslizable) que tenías va aquí, déjalo igual ...
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
                modifier = Modifier
                    .width(350.dp)
                    .offset { IntOffset(offsetX.roundToInt(), 0) }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (abs(offsetX) > 150f) GlobalState.showPopup.value = false
                                offsetX = 0f
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount
                        }
                    }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(GlobalState.popupApp.value, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(GlobalState.popupMessage.value, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=0.8f))
                }
            }
        }

        // MENÚ DE AJUSTES DEL VEHÍCULO
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("Personalizar Mapa") },
                text = {
                    Column {
                        Text("Tipo de Vehículo:", fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf("FLECHA", "SEDAN", "HATCHBACK", "CAMIONETA", "MOTO").forEach { type ->
                                FilterChip(
                                    selected = AppSettings.vehicleType.value == type,
                                    onClick = { AppSettings.vehicleType.value = type },
                                    label = { Text(type.take(3)) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Color del Vehículo:", fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf(Color.Blue, Color.Red, Color.White, Color.Black, Color.DarkGray, Color.Green).forEach { color ->
                                Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(50)).background(color).border(2.dp, if (AppSettings.vehicleColor.value == color.toArgb()) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(50))) {
                                    IconButton(onClick = { AppSettings.vehicleColor.value = color.toArgb() }) {}
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showSettingsDialog = false }) { Text("Guardar") } }
            )
        }
    }
}