package com.tuusuario.carlauncher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuusuario.carlauncher.services.ArletAssistant
import com.tuusuario.carlauncher.services.DashcamManager
import com.tuusuario.carlauncher.services.SpeedRecordTracker
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Interfaz estilo Apple CarPlay / Android Auto Coolwalk para Modo Navegación.
 * Flota sobre la instancia persistente del Mapa 3D sin recrear ni redimensionar el OpenGL MapView:
 *  - Izquierda: Dock compacto CarPlay (Hora, Batería, Volver a Inicio, Split/Full, Voz, Dashcam, Ajustes)
 *  - Derecha: Columna lateral reducida (Mini Velocímetro GPS + Widget de Música compacto), colapsable con fluidez.
 */
@Composable
fun CarPlayNavigationOverlay(
    isLandscape: Boolean,
    isSplitMode: Boolean,
    onToggleSplitMode: () -> Unit,
    onBackToHome: () -> Unit,
    onOpenFullSpeedometer: () -> Unit,
    onOpenYoutube: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val palette = LocalBentoPalette.current

    if (isLandscape) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            // ── DOCK LATERAL IZQUIERDO ESTILO CARPLAY (62.dp) ──
            CarPlayLeftDock(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(62.dp)
                    .fillMaxHeight(),
                isSplitMode = isSplitMode,
                onBackToHome = onBackToHome,
                onToggleSplitMode = onToggleSplitMode,
                onOpenSettings = onOpenSettings
            )

            // ── PANEL LATERAL DERECHO REDUCIDO (Velocímetro Compacto + Música Reducida) ──
            AnimatedVisibility(
                visible = isSplitMode,
                enter = slideInHorizontally(
                    initialOffsetX = { it + 40 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(tween(220)),
                exit = slideOutHorizontally(
                    targetOffsetX = { it + 40 },
                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(180)),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(292.dp)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Tarjeta Superior: Velocímetro Digital + Estado Ruta / Récord
                    CarPlayCompactSpeedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.36f),
                        onClick = onOpenFullSpeedometer
                    )

                    // Tarjeta Inferior: Widget de Música Reducido estilo CarPlay
                    CleanBentoMusicCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.64f),
                        compactMode = true,
                        onOpenYoutube = onOpenYoutube
                    )
                }
            }
        }
    } else {
        // Modo Vertical: Barra superior compacta + Panel inferior reducido opcional
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            // Pill flotante superior izquierda para regresar a Inicio o alternar panel
            Surface(
                modifier = Modifier.align(Alignment.BottomStart),
                shape = RoundedCornerShape(20.dp),
                color = palette.cardBg.copy(alpha = 0.94f),
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackToHome, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Apps, "Inicio", tint = palette.accent)
                    }
                    IconButton(onClick = onToggleSplitMode, modifier = Modifier.size(40.dp)) {
                        Icon(
                            if (isSplitMode) Icons.Default.Fullscreen else Icons.Default.VerticalSplit,
                            "Alternar Música",
                            tint = palette.textPrimary
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isSplitMode,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 58.dp)
                    .fillMaxWidth()
                    .height(175.dp)
            ) {
                CleanBentoMusicCard(
                    modifier = Modifier.fillMaxSize(),
                    compactMode = true,
                    onOpenYoutube = onOpenYoutube
                )
            }
        }
    }
}

@Composable
private fun CarPlayLeftDock(
    modifier: Modifier = Modifier,
    isSplitMode: Boolean,
    onBackToHome: () -> Unit,
    onToggleSplitMode: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val palette = LocalBentoPalette.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val isRec = DashcamManager.isRecording.value
    val isListening = ArletAssistant.isListening.value || ArletAssistant.isWakeWordActiveWindow.value
    val isSpeaking = ArletAssistant.isSpeaking.value

    var clockText by remember { mutableStateOf("") }
    var batteryPct by remember { mutableStateOf(100) }

    LaunchedEffect(Unit) {
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        while (true) {
            clockText = LocalDateTime.now().format(fmt)
            delay(1000L - (System.currentTimeMillis() % 1000L))
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    val lvl = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scl = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (lvl >= 0 && scl > 0) batteryPct = (lvl * 100 / scl)
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = palette.cardBg.copy(alpha = 0.94f),
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Reloj + estado de batería estilo barra de estado CarPlay
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = clockText.ifEmpty { "12:00" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.textPrimary,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                if (NavigationState.isGpsReady.value) Color(0xFF10B981) else Color(0xFFF59E0B),
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${batteryPct}%",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textSecondary
                    )
                }
            }

            // Botones centrales de acción rápida
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Volver al Menú Principal (Home Bento)
                DockIconButton(
                    icon = Icons.Default.GridView,
                    contentDescription = "Menú Principal",
                    containerColor = palette.accent,
                    contentColor = Color.White,
                    onClick = onBackToHome
                )

                // 2. Alternar Split-Screen (Mapa + Música) / Mapa 100% Pantalla Completa
                DockIconButton(
                    icon = if (isSplitMode) Icons.Default.Fullscreen else Icons.Default.VerticalSplit,
                    contentDescription = "Alternar Panel Lateral",
                    containerColor = if (isSplitMode) palette.routesTileBg else palette.subCardBg,
                    contentColor = if (isSplitMode) palette.accent else palette.textPrimary,
                    onClick = onToggleSplitMode
                )

                // 3. Micrófono Asistente Sara / Arlet
                val micBg by animateColorAsState(
                    targetValue = when {
                        isListening -> Color(0xFFFF007F)
                        isSpeaking -> Color(0xFF00B8D4)
                        else -> palette.subCardBg
                    },
                    animationSpec = tween(200),
                    label = "dockMicBg"
                )
                DockIconButton(
                    icon = Icons.Default.Mic,
                    contentDescription = "Asistente de Voz",
                    containerColor = micBg,
                    contentColor = if (isListening || isSpeaking) Color.White else palette.accent,
                    onClick = {
                        if (isListening || isSpeaking) ArletAssistant.closeOverlay()
                        else ArletAssistant.triggerManualListening()
                    }
                )

                // 4. Dashcam (Toque = grabar/detener, Pulsación larga = rotar cámara)
                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = if (isRec) Color(0xFFE53935) else palette.subCardBg,
                    modifier = Modifier
                        .size(44.dp)
                        .pointerInput(isRec) {
                            detectTapGestures(
                                onTap = {
                                    if (isRec) DashcamManager.stopRecording()
                                    else DashcamManager.startRecording(context, lifecycleOwner)
                                },
                                onLongPress = {
                                    DashcamManager.cycleCamera(context, lifecycleOwner)
                                }
                            )
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isRec) Icons.Default.Stop else Icons.Default.Videocam,
                            contentDescription = "Dashcam",
                            tint = if (isRec) Color.White else palette.dashcamTileFg,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Ajustes en el pie del Dock
            DockIconButton(
                icon = Icons.Default.Settings,
                contentDescription = "Ajustes",
                containerColor = palette.subCardBg,
                contentColor = palette.textSecondary,
                onClick = onOpenSettings
            )
        }
    }
}

@Composable
private fun DockIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(15.dp),
        color = containerColor,
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun CarPlayCompactSpeedCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalBentoPalette.current
    val speed = NavigationState.currentSpeedKmH.value
    val animatedSpeed by animateFloatAsState(
        targetValue = speed,
        animationSpec = tween(90, easing = FastOutSlowInEasing),
        label = "cpSpeed"
    )
    val todayMax = SpeedRecordTracker.todayMaxLive.value
    val isRouteActive = NavigationState.isRouteActive.value
    val routeDist = NavigationState.activeRouteDistance.value
    val speedFraction = (animatedSpeed / 160f).coerceIn(0f, 1f)

    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = palette.cardBg.copy(alpha = 0.96f),
        shadowElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = animatedSpeed.roundToInt().toString(),
                        fontSize = 46.sp,
                        lineHeight = 46.sp,
                        fontWeight = FontWeight.Black,
                        color = if (animatedSpeed >= 100f) Color(0xFFE53935) else palette.textPrimary,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "km/h",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textSecondary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = palette.routesTileBg
                ) {
                    Text(
                        text = if (todayMax >= 10f) "Máx ${todayMax.roundToInt()}" else "GPS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Barra dinámica reactiva de velocidad
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(palette.trackInactive)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(speedFraction.coerceAtLeast(0.03f))
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (animatedSpeed >= 100f) Color(0xFFE53935) else palette.accent
                            )
                    )
                }

                Text(
                    text = if (isRouteActive && routeDist.isNotBlank()) {
                        "Destino · $routeDist"
                    } else {
                        "Toca para abrir Velocímetro completo (${AppSettings.speedoStyle.value})"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
