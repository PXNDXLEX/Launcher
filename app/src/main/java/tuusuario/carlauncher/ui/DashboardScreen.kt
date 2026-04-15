package com.tuusuario.carlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tuusuario.carlauncher.ui.map.NavigationMap
import com.tuusuario.carlauncher.ui.widgets.SpeedometerWidget
import com.tuusuario.carlauncher.ui.widgets.MusicPlayerWidget

@Composable
fun DashboardScreen() {
    var isMapFullScreen by remember { mutableStateOf(false) }
    
    val backgroundBrush = Brush.linearGradient(
        colors = listOf(Color(0xFF121212), Color(0xFF1E1E24))
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!isMapFullScreen) {
            Box(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF25252D))
                    .clickable { isMapFullScreen = true }
            ) {
                NavigationMap(isFullScreen = false)
            }

            Column(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(modifier = Modifier.weight(0.5f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF25252D))) {
                    SpeedometerWidget()
                }
                Box(modifier = Modifier.weight(0.5f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF25252D))) {
                    MusicPlayerWidget()
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                NavigationMap(isFullScreen = true)
                Button(
                    onClick = { isMapFullScreen = false },
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, "Volver")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Volver al Dashboard")
                }
            }
        }
    }
}