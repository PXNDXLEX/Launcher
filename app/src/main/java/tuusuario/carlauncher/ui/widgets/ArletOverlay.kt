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
                .clickable {
                    if (isListening || isSpeaking) {
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

    val infiniteTransition = rememberInfiniteTransition(label = "arlet_mini_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    AnimatedVisibility(
        visible = showOverlay,
        enter = fadeIn(tween(200)) + slideInVertically(initialOffsetY = { -it }),
        exit = fadeOut(tween(180)) + slideOutVertically(targetOffsetY = { -it }),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.wrapContentSize()
        ) {
            // Cápsula Principal Minimalista
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xF0121318),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .wrapContentHeight()
                    .widthIn(min = 160.dp, max = 360.dp)
                    .border(
                        width = 1.dp,
                        color = if (isSpeaking) activeColor else if (isListening) Color(0xFFFF007F).copy(alpha = pulseAlpha) else Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(22.dp)
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Indicador circular mínimo
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSpeaking) activeColor.copy(alpha = 0.25f)
                                else if (isListening) Color(0xFFFF007F).copy(alpha = 0.25f)
                                else Color.White.copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Default.VolumeUp else Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (isSpeaking) activeColor else if (isListening) Color(0xFFFF007F) else Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }

                    // Contenido textual condensado
                    Column(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (!hasMicPermission) {
                            Text(
                                text = "Toca para activar micrófono",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                modifier = Modifier.clickable { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                            )
                        } else if (isSpeaking && responseText.isNotBlank()) {
                            Text(
                                text = responseText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else if (queryText.isNotBlank()) {
                            Text(
                                text = queryText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else if (responseText.isNotBlank()) {
                            Text(
                                text = responseText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = "Arlet te escucha...",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = activeColor,
                                maxLines = 1
                            )
                        }
                    }

                    // Botón para cerrar sutil
                    IconButton(
                        onClick = { ArletAssistant.closeOverlay() },
                        modifier = Modifier.size(18.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            // Destinos en caso de desambiguación (Chips compactos)
            if (isAwaitingDisambiguation && disambiguationList.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    itemsIndexed(disambiguationList) { index, place ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xF21E1F26),
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .clickable { ArletAssistant.selectDestination(place) }
                                .border(1.dp, activeColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${index + 1}.",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = activeColor
                                )
                                Text(
                                    text = place.name,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
