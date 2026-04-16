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
    
    // Sistema de Pestañas Principales: "DASHBOARD", "MAPA_FULL", "YOUTUBE"
    var currentScreen by remember { mutableStateOf("DASHBOARD") } 

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
        
        Row(modifier = Modifier.fillMaxSize()) {
            
            // SIDEBAR LATERAL (Siempre visible para facilitar la navegación)
            NavigationRail(
                modifier = Modifier.width(80.dp).fillMaxHeight(),
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(currentTime, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                Text(currentDate, fontSize = 12.sp, color = Color.Gray)
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Botones de Navegación del Sistema
                IconButton(
                    onClick = { currentScreen = "DASHBOARD" },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = if (currentScreen == "DASHBOARD") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                ) {
                    Icon(Icons.Default.Dashboard, "Dashboard Principal")
                }
                
                IconButton(
                    onClick = { currentScreen = "MAPA_FULL" },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = if (currentScreen == "MAPA_FULL") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                ) {
                    Icon(Icons.Default.Map, "Navegación GPS")
                }
                
                IconButton(
                    onClick = { currentScreen = "YOUTUBE" },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = if (currentScreen == "YOUTUBE") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                ) {
                    Icon(Icons.Default.OndemandVideo, "YouTube")
                }

                Spacer(modifier = Modifier.height(16.dp))
                IconButton(onClick = onToggleOrientation) { Icon(Icons.Default.ScreenRotation, "Rotar Pantalla") }
                IconButton(onClick = onToggleTheme) { 
                    Icon(if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode, "Alternar Tema") 
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ÁREA PRINCIPAL DINÁMICA
            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                    when (screen) {
                        
                        "MAPA_FULL" -> {
                            Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).border(1.dp, Color.White.copy(alpha=0.1f), RoundedCornerShape(24.dp))) {
                                NavigationMap(isFullScreen = true)
                                
                                // Botón inteligente que lanza la app nativa de Google Maps para calcular rutas offline
                                ExtendedFloatingActionButton(
                                    onClick = {
                                        val gmmIntentUri = Uri.parse("google.navigation:q=")
                                        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
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
                            // YouTube ahora tiene toda la pantalla para él solo, con buscador y reproductor completo
                            Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(Color.Black)) {
                                YouTubeWidget()
                            }
                        }
                        
                        "DASHBOARD" -> {
                            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                
                                // Mapa (Ocupa el 60%)
                                Box(modifier = Modifier.weight(0.6f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).border(1.dp, Color.White.copy(alpha=0.1f), RoundedCornerShape(24.dp))) {
                                    NavigationMap()
                                }

                                // Widgets Fijos (Ocupan el 40%) - El reproductor de música ya no se cambia por YouTube aquí
                                Column(modifier = Modifier.weight(0.4f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Box(modifier = Modifier.weight(0.5f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, Color.White.copy(alpha=0.1f), RoundedCornerShape(24.dp))) {
                                        SpeedometerWidget()
                                    }
                                    Box(modifier = Modifier.weight(0.5f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, Color.White.copy(alpha=0.1f), RoundedCornerShape(24.dp))) {
                                        MusicPlayerWidget()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // POPUP ANIMADO DE NOTIFICACIONES (Ej: WhatsApp)
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