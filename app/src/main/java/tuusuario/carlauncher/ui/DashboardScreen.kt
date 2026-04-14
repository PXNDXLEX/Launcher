package com.tuusuario.carlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen() {
    // Estado para personalización del icono
    var selectedCarIcon by remember { mutableStateOf(R.drawable.ic_kia_rio_3d) } 
    var isMapFullScreen by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxSize()) {
        // Si no está en pantalla completa, mostramos los widgets
        if (!isMapFullScreen) {
            Box(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
                    .clickable { isMapFullScreen = true } // Al tocar, se expande
            ) {
                NavigationMap(carIconRes = selectedCarIcon)
            }
            
            // Columna de Widgets (Velocímetro, Música)
            Column(modifier = Modifier.weight(0.4f)) {
                SpeedometerWidget()
                MusicPlayerWidget()
            }
        } else {
            // MODO MAPA PANTALLA COMPLETA
            Box(modifier = Modifier.fillMaxSize()) {
                NavigationMap(carIconRes = selectedCarIcon, isFullScreen = true)
                
                // Botón flotante para volver al Dashboard
                Button(
                    onClick = { isMapFullScreen = false },
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, "Volver")
                }
            }
        }
    }
}