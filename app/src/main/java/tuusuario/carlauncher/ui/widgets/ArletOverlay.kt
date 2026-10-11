package com.tuusuario.carlauncher.ui.widgets

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.tuusuario.carlauncher.services.ArletAssistant
import com.tuusuario.carlauncher.ui.AppSettings

/**
 * Botón del micrófono de Arlet para tocar y hablar directamente
 */
@Composable
fun ArletMicButton(
    modifier: Modifier = Modifier,
    activeColor: Color = Color(AppSettings.uiColor.value)
) {
    val context = LocalContext.current
    val isListening by ArletAssistant.isListening
    val isSpeaking by ArletAssistant.isSpeaking
    val rms by ArletAssistant.audioRms

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            ArletAssistant.triggerManualListening()
        } else {
            Toast.makeText(
                context,
                "Se requiere permiso de micrófono para activar a Arlet",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Animación de pulso cuando está escuchando o hablando
    val infiniteTransition = rememberInfiniteTransition(label = "arlet_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening || isSpeaking) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val arletColor = if (isListening) Color(0xFFFF007F) else if (isSpeaking) Color(0xFF00E5FF) else activeColor

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Anillo de pulso resplandeciente
        if (isListening || isSpeaking) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .scale(pulseScale + (rms * 0.3f))
                    .clip(CircleShape)
                    .background(arletColor.copy(alpha = 0.25f))
            )
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(52.dp)
                .border(
                    width = if (isListening || isSpeaking) 2.5.dp else 1.dp,
                    brush = Brush.sweepGradient(listOf(arletColor, Color(0xFF9C27B0), arletColor)),
                    shape = CircleShape
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (isListening) {
                                ArletAssistant.closeOverlay()
                            } else {
                                val hasMic = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED

                                if (hasMic) {
                                    ArletAssistant.triggerManualListening()
                                } else {
                                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        onLongPress = {
                            val hasMic = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED

                            if (!hasMic) {
                                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                // Alternar activación continua por voz
                                val current = AppSettings.arletVoiceWakeEnabled.value
                                AppSettings.setArletVoiceWakeEnabled(!current)
                                if (!current) {
                                    ArletAssistant.startContinuousListening()
                                    ArletAssistant.speak("Activación por voz de Arlet habilitada")
                                } else {
                                    ArletAssistant.stopContinuousListening()
                                    ArletAssistant.speak("Activación por voz pausada. Usa el botón para hablar.")
                                }
                            }
                        }
                    )
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isListening) Icons.Default.Mic else if (isSpeaking) Icons.Default.VolumeUp else Icons.Default.MicNone,
                    contentDescription = "Asistente Arlet",
                    tint = if (isListening || isSpeaking) arletColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

/**
 * Overlay HUD de Arlet:
 * Muestra el estado de escucha, lo que el usuario dijo, la respuesta de Arlet
 * y las tarjetas de desambiguación si hay múltiples destinos.
 */
@Composable
fun ArletOverlay(
    modifier: Modifier = Modifier,
    activeColor: Color = Color(AppSettings.uiColor.value)
) {
    val showOverlay by ArletAssistant.showOverlay
    val isListening by ArletAssistant.isListening
    val isSpeaking by ArletAssistant.isSpeaking
    val rms by ArletAssistant.audioRms
    val queryText by ArletAssistant.lastSpokenQuery
    val responseText by ArletAssistant.assistantResponse
    val isAwaitingDisambiguation by ArletAssistant.isAwaitingDisambiguation
    val disambiguationList = ArletAssistant.disambiguationOptions

    val context = LocalContext.current
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (isGranted) {
            ArletAssistant.triggerManualListening()
        } else {
            Toast.makeText(context, "Permiso de micrófono denegado", Toast.LENGTH_SHORT).show()
        }
    }

    AnimatedVisibility(
        visible = showOverlay,
        enter = fadeIn(tween(250)) + slideInVertically(initialOffsetY = { -it / 2 }),
        exit = fadeOut(tween(200)) + slideOutVertically(targetOffsetY = { -it / 2 }),
        modifier = modifier
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentHeight()
                .border(
                    width = 1.5.dp,
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFFFF007F), Color(0xFF9C27B0), Color(0xFF00E5FF))
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                // Cabecera: Icono Arlet + Ondas de audio + Botón cerrar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Badge con icono de Arlet
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFFFF007F), Color(0xFF9C27B0)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "ARLET",
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                letterSpacing = 1.sp,
                                color = Color(0xFFFF007F)
                            )
                            Text(
                                text = if (isSpeaking) "Hablando..." else if (isListening) "Escuchando..." else "Listo",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                            )
                        }
                    }

                    // Visualizador de ondas de audio
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0..4) {
                            val barHeight = remember(rms, i) {
                                val factor = if (isListening || isSpeaking) (0.2f + rms * (0.8f + (i % 3) * 0.2f)) else 0.2f
                                (18.dp * factor.coerceIn(0.15f, 1f))
                            }
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(barHeight)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (isSpeaking) Color(0xFF00E5FF) else if (isListening) Color(0xFFFF007F) else Color.Gray.copy(alpha = 0.4f)
                                    )
                            )
                        }
                    }

                    // Botón para cerrar
                    IconButton(
                        onClick = { ArletAssistant.closeOverlay() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Aviso si falta el permiso de micrófono
                if (!hasMicPermission) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Arlet necesita permiso para acceder al micrófono y escuchar tus órdenes.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF007F))
                            ) {
                                Icon(Icons.Default.Mic, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Permitir Micrófono", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Transcripción de lo que el usuario dijo
                if (queryText.isNotBlank()) {
                    Text(
                        text = "Tú: \"$queryText\"",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Respuesta de Arlet
                if (responseText.isNotBlank()) {
                    Text(
                        text = responseText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // ── MODO DESAMBIGUACIÓN (Tarjetas de destinos) ──────────────
                if (isAwaitingDisambiguation && disambiguationList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Toca tu destino o di 'la primera' / 'la segunda':",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(disambiguationList) { index, place ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                                ),
                                modifier = Modifier
                                    .width(220.dp)
                                    .clickable {
                                        ArletAssistant.selectDestination(place)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Número ordinal
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(activeColor.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = activeColor
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = place.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = place.address,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.Default.Directions,
                                        contentDescription = "Ir",
                                        tint = activeColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
