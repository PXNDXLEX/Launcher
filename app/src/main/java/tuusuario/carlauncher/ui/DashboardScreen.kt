package com.tuusuario.carlauncher.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.tuusuario.carlauncher.services.GlobalState
import com.tuusuario.carlauncher.ui.map.NavigationMap
import com.tuusuario.carlauncher.ui.widgets.SpeedometerWidget
import com.tuusuario.carlauncher.ui.widgets.MusicPlayerWidget
import com.tuusuario.carlauncher.ui.widgets.YouTubeWidget

@Composable
fun DashboardScreen(onToggleTheme: () -> Unit, onToggleOrientation: () -> Unit, isDarkMode: Boolean, isLandscape: Boolean) {
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    val context = LocalContext.current
    
    // Controles de estado
    var activeMediaTab by remember { mutableStateOf("MUSIC") } 
    var isMapFullScreen by remember { mutableStateOf(false) } // Nuevo estado para pantalla completa

    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalDateTime.now()
            currentTime = now.format(DateTimeFormatter.ofPattern("HH:mm"))
            currentDate = now.format(DateTimeFormatter.ofPattern("EEE, dd MMM"))
            delay(1000)
        }
    }

    if (GlobalState.showPopup.value) {
        LaunchedEffect(GlobalState.showPopup.value) {
            delay(6000)
            GlobalState.showPopup.value = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        
        // INTERFAZ ALTERNATIVA: MAPA EN PANTALLA COMPLETA
        if (isMapFullScreen) {
            Box(modifier = Modifier.fillMaxSize()) {
                NavigationMap(isFullScreen = true)
                
                // Botón para salir de pantalla completa
                FloatingActionButton(
                    onClick = { isMapFullScreen = false },
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Icon(Icons.Default.ArrowBack, "Volver al Dashboard")
                }

                // Botón inteligente que lanza la app nativa de Google Maps para calcular rutas
                ExtendedFloatingActionButton(
                    onClick = {
                        val gmmIntentUri = Uri.parse("google.navigation:q=") // Abre el modo conducción
                        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                        mapIntent.setPackage("com.google.android.apps.maps")
                        context.startActivity(mapIntent)
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    icon = { Icon(Icons.Default.Navigation, "Navegar") },
                    text = { Text("Iniciar Ruta") },
                    containerColor = MaterialTheme.colorScheme.primary
                )
            }
        } 
        // INTERFAZ NORMAL: DASHBOARD DIVIDIDO
        else {
            Row(modifier = Modifier.fillMaxSize()) {
                // SIDEBAR LATERAL
                NavigationRail(
                    modifier = Modifier.width(80.dp).fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(currentTime, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                    Text(currentDate, fontSize = 12.sp, color = Color.Gray)
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // NUEVO: Botón para ver el mapa en grande
                    IconButton(onClick = { isMapFullScreen = true }) {
                        Icon(Icons.Default.Map, "Mapa Grande")
                    }
                    
                    IconButton(onClick = { activeMediaTab = if (activeMediaTab == "MUSIC") "YOUTUBE" else "MUSIC" }) {
                        Icon(if (activeMediaTab == "MUSIC") Icons.Default.OndemandVideo else Icons.Default.MusicNote, "Alternar Multimedia")
                    }
                    IconButton(onClick = onToggleOrientation) { Icon(Icons.Default.ScreenRotation, "Rotar Pantalla") }
                    IconButton(onClick = onToggleTheme) { 
                        Icon(if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode, "Alternar Tema") 
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // ÁREA PRINCIPAL
                Row(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    
                    // Mapa (Ocupa el 60%)
                    Box(modifier = Modifier.weight(0.6f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).border(1.dp, Color.White.copy(alpha=0.1f), RoundedCornerShape(24.dp))) {
                        NavigationMap()
                        // Botón flotante sutil para expandir el mapa
                        IconButton(
                            onClick = { isMapFullScreen = true },
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha=0.5f), RoundedCornerShape(50))
                        ) {
                            Icon(Icons.Default.Fullscreen, "Expandir", tint = Color.White)
                        }
                    }

                    // Widgets (Ocupan el 40%)
                    Column(modifier = Modifier.weight(0.4f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(modifier = Modifier.weight(0.5f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, Color.White.copy(alpha=0.1f), RoundedCornerShape(24.dp))) {
                            SpeedometerWidget()
                        }
                        Box(modifier = Modifier.weight(0.5f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, Color.White.copy(alpha=0.1f), RoundedCornerShape(24.dp))) {
                            Crossfade(targetState = activeMediaTab, label = "MediaSwap") { tab ->
                                when (tab) {
                                    "MUSIC" -> MusicPlayerWidget()
                                    "YOUTUBE" -> YouTubeWidget()
                                }
                            }
                        }
                    }
                }
            }
        }

        // POPUP ANIMADO DE NOTIFICACIONES
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
                modifier = Modifier.width(350.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(GlobalState.popupApp.value, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(GlobalState.popupMessage.value, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=0.8f))
                }
            }
        }
    }
}