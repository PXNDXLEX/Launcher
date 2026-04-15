package com.tuusuario.carlauncher.ui.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// IMPORTANTE: Ahora llamamos al GlobalState, no al MusicState
import com.tuusuario.carlauncher.services.GlobalState

@Composable
fun MusicPlayerWidget() {
    // Leemos los valores en tiempo real del nuevo GlobalState
    val songTitle = GlobalState.songTitle.value
    val songArtist = GlobalState.songArtist.value

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Reproduciendo ahora", color = Color(0xFF03A9F4), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = songTitle, 
            color = Color.White, 
            fontSize = 20.sp, 
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = songArtist, 
            color = Color.Gray, 
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { /* Implementar saltar después */ }) { 
                Icon(Icons.Default.SkipPrevious, "Anterior", tint = Color.White, modifier = Modifier.size(32.dp)) 
            }
            IconButton(onClick = { /* Implementar play/pause después */ }) { 
                Icon(Icons.Default.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(48.dp)) 
            }
            IconButton(onClick = { /* Implementar saltar después */ }) { 
                Icon(Icons.Default.SkipNext, "Siguiente", tint = Color.White, modifier = Modifier.size(32.dp)) 
            }
        }
    }
}