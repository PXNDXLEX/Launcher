package com.tuusuario.carlauncher.ui

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
    
    // Controla si mostramos Música o YouTube
    var activeMediaTab by remember { mutableStateOf("MUSIC") } 

    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalDateTime.now()
            currentTime = now.format(DateTimeFormatter.ofPattern("HH:mm"))
            currentDate = now.format(DateTimeFormatter.ofPattern("EEE, dd MMM"))
            delay(1000)
        }
    }

    // Ocultar popup de notificación después de 6 segundos
    if (GlobalState.showPopup.value) {
        LaunchedEffect(GlobalState.showPopup.value) {
            delay(6000)
            GlobalState.showPopup.value = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(modifier = Modifier.fillMaxSize()) {
            
            // SIDEBAR LATERAL (Moderna)
            NavigationRail(
                modifier = Modifier.width(80.dp).fillMaxHeight(),
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(currentTime, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                Text(currentDate, fontSize = 12.sp, color = Color.Gray)
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Botones de Control
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
                }

                // Widgets (Ocupan el 40%)
                Column(modifier = Modifier.weight(0.4f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(modifier = Modifier.weight(0.5f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, Color.White.copy(alpha=0.1f), RoundedCornerShape(24.dp))) {
                        SpeedometerWidget()
                    }
                    Box(modifier = Modifier.weight(0.5f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, Color.White.copy(alpha=0.1f), RoundedCornerShape(24.dp))) {
                        // Animación cruzada entre YouTube y Música
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