package com.tuusuario.carlauncher.ui.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.tuusuario.carlauncher.services.GlobalState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun MusicPlayerWidget() {
    val songTitle = GlobalState.songTitle.value
    val songArtist = GlobalState.songArtist.value
    val albumArt = GlobalState.songAlbumArt.value
    val isPlaying = GlobalState.isPlaying.value
    val songDuration = GlobalState.songDuration.value
    val songPosition = GlobalState.songPosition.value

    val textColor = Color.White 
    
    var isExpanded by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var autoCloseJob by remember { mutableStateOf<Job?>(null) }
    
    // Slider real basado en la posición de la canción
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableStateOf(0f) }
    
    val progressFraction = if (songDuration > 0 && !isSeeking) {
        (songPosition.toFloat() / songDuration.toFloat()).coerceIn(0f, 1f)
    } else if (isSeeking) {
        seekPosition
    } else 0f

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
    
    // Actualizar la posición en tiempo real mientras se reproduce
    LaunchedEffect(isPlaying, isExpanded) {
        if (isPlaying && isExpanded) {
            while (true) {
                val state = GlobalState.mediaController?.playbackState
                if (state != null) {
                    GlobalState.songPosition.value = state.position
                }
                delay(500)
            }
        }
    }

    fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

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
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            }
        }

        // 2. CAPA DE DEGRADADO
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.9f)
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
        
        // 4. CAPA DE EXPANSIÓN (POP-UP MAXIMIZADO) - Flotante real a pantalla completa
        if (isExpanded) {
            Dialog(
                onDismissRequest = { isExpanded = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                var showContent by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    showContent = true
                }
                
                AnimatedVisibility(
                    visible = showContent,
                    enter = fadeIn() + scaleIn(initialScale = 0.9f),
                    exit = fadeOut() + scaleOut(targetScale = 0.9f),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Fondo completo clickeable para cerrar
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.88f))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { 
                                showContent = false
                                coroutineScope.launch {
                                    delay(200) // Esperar animación de salida
                                    isExpanded = false
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Contenido que NO cierra al tocarlo
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth(0.6f) // Ajustado al 60% para verse elegante en modo horizontal
                                .clip(RoundedCornerShape(32.dp))
                                .background(Color(0xFF1A1A2E).copy(alpha = 0.95f))
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { resetAutoCloseTimer() }
                                .padding(32.dp)
                        ) {
                            // Botón cerrar
                            Box(modifier = Modifier.fillMaxWidth()) {
                                IconButton(
                                    onClick = { 
                                        showContent = false
                                        coroutineScope.launch {
                                            delay(200)
                                            isExpanded = false
                                        }
                                    },
                                    modifier = Modifier.align(Alignment.TopEnd)
                                ) {
                                    Icon(Icons.Default.Close, "Cerrar", tint = Color.White.copy(alpha = 0.6f))
                                }
                            }
                            
                            // Carátula grande
                            if (albumArt != null) {
                                Image(
                                    bitmap = albumArt.asImageBitmap(),
                                    contentDescription = "Album Art",
                                    modifier = Modifier
                                        .size(240.dp)
                                        .clip(RoundedCornerShape(20.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(240.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color.DarkGray),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(100.dp), tint = Color.White.copy(alpha = 0.3f))
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(text = songTitle.toString(), color = textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(text = songArtist.toString(), color = textColor.copy(alpha = 0.6f), fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            // Barra de progreso REAL con seekTo
                            Slider(
                                value = progressFraction,
                                onValueChange = { 
                                    isSeeking = true
                                    seekPosition = it
                                    resetAutoCloseTimer() 
                                },
                                onValueChangeFinished = {
                                    if (songDuration > 0) {
                                        val newPos = (seekPosition * songDuration).toLong()
                                        GlobalState.seekTo(newPos)
                                        GlobalState.songPosition.value = newPos
                                    }
                                    isSeeking = false
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            // Tiempos
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(formatTime(if (isSeeking) (seekPosition * songDuration).toLong() else songPosition), color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                                Text(formatTime(songDuration), color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Controles
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { GlobalState.skipToPrevious(); resetAutoCloseTimer() }) { Icon(Icons.Default.SkipPrevious, "Anterior", tint = textColor, modifier = Modifier.size(56.dp)) }
                                IconButton(onClick = { GlobalState.togglePlayPause(); resetAutoCloseTimer() }) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", tint = textColor, modifier = Modifier.size(80.dp)) }
                                IconButton(onClick = { GlobalState.skipToNext(); resetAutoCloseTimer() }) { Icon(Icons.Default.SkipNext, "Siguiente", tint = textColor, modifier = Modifier.size(56.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
}