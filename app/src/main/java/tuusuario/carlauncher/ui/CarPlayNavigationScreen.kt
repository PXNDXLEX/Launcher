package com.tuusuario.carlauncher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuusuario.carlauncher.services.ArletAssistant
import com.tuusuario.carlauncher.services.DashcamManager
import com.tuusuario.carlauncher.services.GlobalState
import com.tuusuario.carlauncher.services.SpeedRecordTracker
import com.tuusuario.carlauncher.ui.widgets.SpeedometerWidget
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Interfaz estilo Apple CarPlay / Android Auto Coolwalk para Modo Navegación:
 *  - Horizontal: Dock izquierdo + Columna lateral derecha compacta (Velocímetro programado activo arriba + Música compacta con carátula de fondo abajo).
 *  - Vertical: Barra inferior compacta + Fila dual simétrica (Velocímetro programado a la izquierda + Música con carátula de fondo a la derecha).
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

            // ── PANEL LATERAL DERECHO COMPACTO (246.dp): Velocímetro Programado + Música con Carátula ──
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
                    animationSpec = tween(200, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(170)),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(246.dp)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Tarjeta Superior (57%): Velocímetro programado seleccionado en vivo
                    CarPlayLiveSpeedometerCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.57f),
                        onClick = onOpenFullSpeedometer
                    )

                    // Tarjeta Inferior (43%): Widget de Música compacto con carátula de fondo
                    CarPlayAlbumArtMusicCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.43f),
                        ultraCompact = false,
                        onOpenYoutube = onOpenYoutube
                    )
                }
            }
        }
    } else {
        // ── MODO VERTICAL: Fila Dual Simétrica (Velocímetro + Música del mismo tamaño) + Dock Inferior ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            AnimatedVisibility(
                visible = isSplitMode,
                enter = slideInVertically { it } + fadeIn(tween(200)),
                exit = slideOutVertically { it } + fadeOut(tween(170)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 54.dp)
                    .fillMaxWidth()
                    .height(166.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Izquierda (50%): Velocímetro programado seleccionado
                    CarPlayLiveSpeedometerCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = onOpenFullSpeedometer
                    )

                    // Derecha (50%): Widget de música compacto con carátula de fondo
                    CarPlayAlbumArtMusicCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        ultraCompact = true,
                        onOpenYoutube = onOpenYoutube
                    )
                }
            }

            // Dock inferior compacto en modo vertical
            CarPlayPortraitBottomDock(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .height(46.dp),
                isSplitMode = isSplitMode,
                onBackToHome = onBackToHome,
                onToggleSplitMode = onToggleSplitMode,
                onOpenSettings = onOpenSettings
            )
        }
    }
}

/**
 * Tarjeta de velocímetro para Modo Navegación que renderiza directamente el estilo programado
 * seleccionado por el usuario (de los 18 velocímetros disponibles).
 * Permite tocar para abrir en grande o deslizar horizontalmente para cambiar de estilo al vuelo.
 */
@Composable
private fun CarPlayLiveSpeedometerCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalBentoPalette.current
    val activeStyle = AppSettings.speedoStyle.value
    val todayMax = SpeedRecordTracker.todayMaxLive.value
    val isRouteActive = NavigationState.isRouteActive.value
    val routeDist = NavigationState.activeRouteDistance.value

    val styles = remember {
        listOf(
            "PREMIUM", "NEON", "RACING", "CYBER", "AURA", "VORTEX",
            "QUANTUM", "PULSAR", "PLASMA", "ANIME", "KAIJU", "OMNIMON",
            "SHONEN", "MECHA", "OVERDRIVE", "NEBULA", "DEMONIC", "CUSTOM"
        )
    }
    var dragAccum by remember { mutableStateOf(0f) }

    Surface(
        modifier = modifier
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragAccum > 65f) {
                            val idx = styles.indexOf(AppSettings.speedoStyle.value)
                            val prev = if (idx <= 0) styles.lastIndex else idx - 1
                            AppSettings.setSpeedoStyle(styles[prev])
                        } else if (dragAccum < -65f) {
                            val idx = styles.indexOf(AppSettings.speedoStyle.value)
                            val next = (idx + 1) % styles.size
                            AppSettings.setSpeedoStyle(styles[next])
                        }
                        dragAccum = 0f
                    }
                ) { change, amount ->
                    change.consume()
                    dragAccum += amount
                }
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = palette.cardBg.copy(alpha = 0.96f),
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, palette.cardBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(
                targetState = activeStyle,
                animationSpec = tween(220),
                label = "navLiveSpeedo"
            ) { _ ->
                SpeedometerWidget(scaleMultiplier = 1.04f)
            }

            // Etiqueta discreta superior derecha (Récord del día o distancia restante)
            val badgeText = when {
                isRouteActive && routeDist.isNotBlank() -> routeDist
                todayMax >= 10f -> "Máx ${todayMax.roundToInt()}"
                else -> null
            }
            if (badgeText != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = palette.routesTileBg.copy(alpha = 0.88f)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accent,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * Widget de música compacto para Modo Navegación con la carátula de la canción actual
 * ocupando todo el fondo de la tarjeta y velo oscuro cinematográfico para legibilidad perfecta.
 */
@Composable
private fun CarPlayAlbumArtMusicCard(
    modifier: Modifier = Modifier,
    ultraCompact: Boolean = false,
    onOpenYoutube: (() -> Unit)? = null
) {
    val palette = LocalBentoPalette.current
    val songTitle = GlobalState.songTitle.value
    val songArtist = GlobalState.songArtist.value
    val playerApp = GlobalState.playerAppName.value
    val albumArt = GlobalState.songAlbumArt.value
    val isPlaying = GlobalState.isPlaying.value
    val duration = GlobalState.songDuration.value
    val position = GlobalState.songPosition.value

    val artImageBitmap = remember(albumArt) { albumArt?.asImageBitmap() }
    var showExpandedModal by remember { mutableStateOf(false) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                GlobalState.mediaController?.playbackState?.let { st ->
                    GlobalState.songPosition.value = st.position
                }
                delay(1000)
            }
        }
    }

    val progress = if (duration > 0L) {
        (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val textShadow = remember {
        Shadow(color = Color.Black.copy(alpha = 0.75f), blurRadius = 8f)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF0A101D),
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // ── CAPA 1: Carátula de la canción como fondo completo de la tarjeta ──
            if (artImageBitmap != null) {
                Image(
                    bitmap = artImageBitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(palette.navyHeroStart, Color(0xFF091122))
                            )
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier
                            .size(88.dp)
                            .align(Alignment.Center)
                    )
                }
            }

            // ── CAPA 2: Velo degradado oscuro para garantizar contraste sobre carátulas claras ──
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF070C17).copy(alpha = 0.48f),
                                Color(0xFF070C17).copy(alpha = 0.66f),
                                Color(0xFF070C17).copy(alpha = 0.88f)
                            )
                        )
                    )
            )

            // ── CAPA 3: Controles e información compacta ──
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = if (ultraCompact) 11.dp else 13.dp,
                        vertical = if (ultraCompact) 10.dp else 11.dp
                    ),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Fila superior: Título y Artista + botón discreto YouTube
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showExpandedModal = true },
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = songTitle.ifBlank { "Sin reproducción" },
                            fontSize = if (ultraCompact) 13.sp else 14.sp,
                            lineHeight = if (ultraCompact) 15.sp else 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(shadow = textShadow)
                        )
                        val sub = buildString {
                            if (songArtist.isNotBlank()) {
                                append(songArtist)
                                if (!ultraCompact) {
                                    append(" · ")
                                    append(playerApp)
                                }
                            } else {
                                append(playerApp)
                            }
                        }
                        Text(
                            text = sub,
                            fontSize = if (ultraCompact) 10.sp else 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.80f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(shadow = textShadow)
                        )
                    }

                    if (onOpenYoutube != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            onClick = onOpenYoutube,
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.16f),
                            modifier = Modifier.size(if (ultraCompact) 26.dp else 28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.OndemandVideo,
                                    contentDescription = "YouTube",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                // Zona media: Barra de progreso interactiva + tiempos compactos
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .pointerInput(duration) {
                                detectTapGestures { offset ->
                                    if (duration > 0L && size.width > 0) {
                                        val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                        val targetMs = (frac * duration).toLong()
                                        GlobalState.seekTo(targetMs)
                                        GlobalState.songPosition.value = targetMs
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.26f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                                    .clip(RoundedCornerShape(50))
                                    .background(palette.accent)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDurationMs(position),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.78f),
                            style = TextStyle(fontFeatureSettings = "tnum")
                        )
                        Text(
                            text = formatDurationMs(duration),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.78f),
                            style = TextStyle(fontFeatureSettings = "tnum")
                        )
                    }
                }

                // Fila inferior: Controles Anterior / Play-Pause / Siguiente centrados y proporcionados
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { GlobalState.skipToPrevious() },
                        modifier = Modifier.size(if (ultraCompact) 32.dp else 34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Anterior",
                            tint = Color.White,
                            modifier = Modifier.size(if (ultraCompact) 22.dp else 24.dp)
                        )
                    }

                    Surface(
                        onClick = { GlobalState.togglePlayPause() },
                        shape = CircleShape,
                        color = palette.accent,
                        shadowElevation = 4.dp,
                        modifier = Modifier.size(if (ultraCompact) 36.dp else 40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Reproducir / Pausar",
                                tint = Color.White,
                                modifier = Modifier.size(if (ultraCompact) 21.dp else 23.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { GlobalState.skipToNext() },
                        modifier = Modifier.size(if (ultraCompact) 32.dp else 34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Siguiente",
                            tint = Color.White,
                            modifier = Modifier.size(if (ultraCompact) 22.dp else 24.dp)
                        )
                    }
                }
            }
        }
    }

    if (showExpandedModal) {
        ExpandedMusicModal(onDismiss = { showExpandedModal = false })
    }
}

@Composable
private fun CarPlayPortraitBottomDock(
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

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = palette.cardBg.copy(alpha = 0.95f),
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, palette.cardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DockIconButton(
                icon = Icons.Default.GridView,
                contentDescription = "Menú Principal",
                containerColor = palette.accent,
                contentColor = Color.White,
                sizeDp = 36,
                onClick = onBackToHome
            )

            DockIconButton(
                icon = if (isSplitMode) Icons.Default.Fullscreen else Icons.Default.VerticalSplit,
                contentDescription = "Mostrar / Ocultar Widgets",
                containerColor = if (isSplitMode) palette.routesTileBg else palette.subCardBg,
                contentColor = if (isSplitMode) palette.accent else palette.textPrimary,
                sizeDp = 36,
                onClick = onToggleSplitMode
            )

            DockIconButton(
                icon = Icons.Default.Mic,
                contentDescription = "Asistente Sara",
                containerColor = when {
                    isListening -> Color(0xFFFF007F)
                    isSpeaking -> Color(0xFF00B8D4)
                    else -> palette.subCardBg
                },
                contentColor = if (isListening || isSpeaking) Color.White else palette.accent,
                sizeDp = 36,
                onClick = {
                    if (isListening || isSpeaking) ArletAssistant.closeOverlay()
                    else ArletAssistant.triggerManualListening()
                }
            )

            DockIconButton(
                icon = if (isRec) Icons.Default.Stop else Icons.Default.Videocam,
                contentDescription = "Dashcam",
                containerColor = if (isRec) Color(0xFFE53935) else palette.subCardBg,
                contentColor = if (isRec) Color.White else palette.dashcamTileFg,
                sizeDp = 36,
                onClick = {
                    if (isRec) DashcamManager.stopRecording()
                    else DashcamManager.startRecording(context, lifecycleOwner)
                }
            )

            DockIconButton(
                icon = Icons.Default.Settings,
                contentDescription = "Ajustes",
                containerColor = palette.subCardBg,
                contentColor = palette.textSecondary,
                sizeDp = 36,
                onClick = onOpenSettings
            )
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
        border = BorderStroke(1.dp, palette.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
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

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DockIconButton(
                    icon = Icons.Default.GridView,
                    contentDescription = "Menú Principal",
                    containerColor = palette.accent,
                    contentColor = Color.White,
                    onClick = onBackToHome
                )

                DockIconButton(
                    icon = if (isSplitMode) Icons.Default.Fullscreen else Icons.Default.VerticalSplit,
                    contentDescription = "Alternar Panel Lateral",
                    containerColor = if (isSplitMode) palette.routesTileBg else palette.subCardBg,
                    contentColor = if (isSplitMode) palette.accent else palette.textPrimary,
                    onClick = onToggleSplitMode
                )

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
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    sizeDp: Int = 44,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape((sizeDp * 0.34f).dp),
        color = containerColor,
        modifier = Modifier.size(sizeDp.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size((sizeDp * 0.5f).dp)
            )
        }
    }
}
