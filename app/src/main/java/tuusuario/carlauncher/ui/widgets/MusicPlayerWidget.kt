package com.tuusuario.carlauncher.ui.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.tuusuario.carlauncher.services.GlobalState

@Composable
fun MusicPlayerWidget() {
    val songTitle = GlobalState.songTitle.value
    val songArtist = GlobalState.songArtist.value
    val albumArt = GlobalState.songAlbumArt.value
    val isPlaying = GlobalState.isPlaying.value

    // Forzamos el texto a blanco para que siempre contraste con el fondo oscuro
    val textColor = Color.White 
    
    var isExpanded by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var autoCloseJob by remember { mutableStateOf<Job?>(null) }
    var sliderPosition by remember { mutableStateOf(0f) }

    fun resetAutoCloseTimer() {
        autoCloseJob?.cancel()
        if (isExpanded) {
            autoCloseJob = coroutineScope.launch {
                delay(5000)
                isExpanded = false
            }
        }
    }

    LaunchedEffect(isExpanded) {
        if (isExpanded) resetAutoCloseTimer()
        else autoCloseJob?.cancel()
    }

    // Box es perfecto para superponer elementos (Fondo -> Sombra -> Textos)
    Box(
        modifier = Modifier.fillMaxSize().clickable { 
            isExpanded = true 
            resetAutoCloseTimer()
        }
    ) {
        // 1. CAPA DE FONDO (CARÁTULA DEL ÁLBUM)
        if (albumArt != null) {
            Image(
                bitmap = albumArt.asImageBitmap(),
                contentDescription = "Album Art",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop // Recorta la imagen para llenar el fondo sin deformarse
            )
        } else {
            // Fondo por defecto si no hay música
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            }
        }

        // 2. CAPA DE DEGRADADO (Para que los textos siempre se lean bien sobre cualquier imagen)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.3f), // Arriba un poco más claro
                            Color.Black.copy(alpha = 0.9f)  // Abajo muy oscuro para resaltar botones
                        )
                    )
                )
        )

        // 3. CAPA DE TEXTOS Y BOTONES
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = songTitle.toString(), 
                color = textColor, 
                fontSize = 24.sp, 
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = songArtist.toString(), 
                color = textColor.copy(alpha = 0.7f), 
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            // CONTROLES FUNCIONALES CONECTADOS AL GLOBAL STATE
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { GlobalState.skipToPrevious() }) { 
                    Icon(Icons.Default.SkipPrevious, "Anterior", tint = textColor, modifier = Modifier.size(40.dp)) 
                }
                
                // El icono cambia automáticamente entre Play y Pausa
                IconButton(onClick = { GlobalState.togglePlayPause() }) { 
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, 
                        "Play/Pause", 
                        tint = textColor, 
                        modifier = Modifier.size(56.dp)
                    ) 
                }
                
                IconButton(onClick = { 
                    GlobalState.skipToNext() 
                    resetAutoCloseTimer()
                }) { 
                    Icon(Icons.Default.SkipNext, "Siguiente", tint = textColor, modifier = Modifier.size(40.dp)) 
                }
            }
        }
        
        // 4. CAPA DE EXPANSIÓN (POP-UP MAXIMIZADO)
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable { resetAutoCloseTimer() }
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (albumArt != null) {
                        Image(
                            bitmap = albumArt.asImageBitmap(),
                            contentDescription = "Album Art",
                            modifier = Modifier.size(200.dp),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(100.dp), tint = Color.White.copy(alpha = 0.3f))
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(text = songTitle.toString(), color = textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = songArtist.toString(), color = textColor.copy(alpha = 0.7f), fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Simulación de progreso de música
                    Slider(
                        value = sliderPosition,
                        onValueChange = { 
                            sliderPosition = it
                            resetAutoCloseTimer() 
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth(0.8f)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(0.8f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { GlobalState.skipToPrevious(); resetAutoCloseTimer() }) { Icon(Icons.Default.SkipPrevious, "Anterior", tint = textColor, modifier = Modifier.size(48.dp)) }
                        IconButton(onClick = { GlobalState.togglePlayPause(); resetAutoCloseTimer() }) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", tint = textColor, modifier = Modifier.size(72.dp)) }
                        IconButton(onClick = { GlobalState.skipToNext(); resetAutoCloseTimer() }) { Icon(Icons.Default.SkipNext, "Siguiente", tint = textColor, modifier = Modifier.size(48.dp)) }
                    }
                }
                
                IconButton(
                    onClick = { isExpanded = false },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(Icons.Default.Pause, "Cerrar", tint = Color.Transparent) // Botón invisible o cruz si se desea
                }
            }
        }
    }
}