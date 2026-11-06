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
import androidx.compose.animation.core.animateDpAsState
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
 *  - Horizontal: Dock izquierdo ergonómico (80.dp) con botones VOZ y CAM grandes y físicamente separados
 *    + Panel lateral derecho adaptable mediante slider (25% hasta máximo 50% de la pantalla) sin deformarse.
 *  - Vertical: Fila dual adaptable (Velocímetro + Música a partes iguales) + Barra inferior ergonómica
 *    con botones VOZ y CAM en extremos opuestos para evitar toques accidentales al conducir.
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
    val navPanelPct = AppSettings.navPanelPercent.value.coerceIn(25f, 50f)

    if (isLandscape) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            // Ancho dinámico del panel Música + Velocímetro (25%..50% máx del ancho disponible, siempre min <= max)
            val maxPanelW = (maxWidth * 0.50f).coerceAtLeast(0.dp)
            val minPanelW = 220.dp.coerceAtMost(maxPanelW)
            val targetPanelWidth = (maxWidth * (navPanelPct / 100f)).coerceIn(minPanelW, maxPanelW)
            val animatedPanelWidth by animateDpAsState(
                targetValue = targetPanelWidth,
                animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
                label = "navPanelWidth"
            )

            // ── DOCK LATERAL IZQUIERDO ERGONÓMICO (80.dp): VOZ y CAM grandes y separados ──
            CarPlayLeftDock(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(80.dp)
                    .fillMaxHeight(),
                isSplitMode = isSplitMode,
                onBackToHome = onBackToHome,
                onToggleSplitMode = onToggleSplitMode,
                onOpenSettings = onOpenSettings
            )

            // ── PANEL LATERAL DERECHO ADAPTABLE (hasta 50% máx): Velocímetro + Música con Carátula ──
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
                    .width(animatedPanelWidth)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Tarjeta Superior (54%): Velocímetro programado seleccionado en vivo (escala circular sin deformación)
                    CarPlayLiveSpeedometerCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.54f),
                        onClick = onOpenFullSpeedometer
                    )

                    // Tarjeta Inferior (46%): Widget de Música adaptable con carátula de fondo
                    CarPlayAlbumArtMusicCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.46f),
                        ultraCompact = false,
                        onOpenYoutube = onOpenYoutube
                    )
                }
            }
        }
    } else {
        // ── MODO VERTICAL: Fila Dual Simétrica Adaptable + Dock Inferior con VOZ y CAM separados ──
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            // Altura adaptable según el slider (manteniendo siempre min <= max incluso en ventanas reducidas)
            val portraitFraction = (0.22f + ((navPanelPct - 25f) / 25f) * 0.16f).coerceIn(0.22f, 0.38f)
            val maxRowH = 310.dp.coerceAtMost((maxHeight * 0.48f).coerceAtLeast(0.dp))
            val minRowH = 168.dp.coerceAtMost(maxRowH)
            val targetRowHeight = (maxHeight * portraitFraction).coerceIn(minRowH, maxRowH)
            val animatedRowHeight by animateDpAsState(
                targetValue = targetRowHeight,
                animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
                label = "portraitRowHeight"
            )

            AnimatedVisibility(
                visible = isSplitMode,
                enter = slideInVertically { it } + fadeIn(tween(200)),
                exit = slideOutVertically { it } + fadeOut(tween(170)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 66.dp)
                    .fillMaxWidth()
                    .height(animatedRowHeight)
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

                    // Derecha (50%): Widget de música con carátula de fondo
                    CarPlayAlbumArtMusicCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        ultraCompact = true,
                        onOpenYoutube = onOpenYoutube
                    )
                }
            }

            // Dock inferior ergonómico con VOZ a la izquierda y CAM a la derecha
            CarPlayPortraitBottomDock(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(58.dp),
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
 * Escala proporcionalmente con `min(maxWidth, maxHeight)` para nunca deformarse al ampliar el panel.
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
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(
                targetState = activeStyle,
                animationSpec = tween(220),
                label = "navLiveSpeedo"
            ) { _ ->
                SpeedometerWidget(scaleMultiplier = 1.06f)
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
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accent,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * Widget de música adaptable para Modo Navegación con la carátula de la canción actual
 * ocupando todo el fondo de la tarjeta. Se adapta fluidamente desde ancho compacto (25%)
 * hasta mitad de pantalla (50%) manteniendo proporciones armónicas sin estirarse.
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
        Shadow(color = Color.Black.copy(alpha = 0.78f), blurRadius = 8f)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF0A101D),
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isSpacious = maxWidth >= 300.dp && maxHeight >= 150.dp
            val isWide = maxWidth >= 360.dp

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
                            .size(92.dp)
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
                                Color(0xFF070C17).copy(alpha = 0.46f),
                                Color(0xFF070C17).copy(alpha = 0.66f),
                                Color(0xFF070C17).copy(alpha = 0.88f)
                            )
                        )
                    )
            )

            // ── CAPA 3: Controles e información con escalado proporcional ──
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = when {
                            isWide -> 18.dp
                            ultraCompact -> 11.dp
                            else -> 14.dp
                        },
                        vertical = when {
                            isSpacious -> 13.dp
                            ultraCompact -> 10.dp
                            else -> 11.dp
                        }
                    ),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Fila superior: Miniatura nítida opcional (en vista amplia) + Título/Artista + Botón YouTube PiP
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showExpandedModal = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSpacious && artImageBitmap != null) {
                        Image(
                            bitmap = artImageBitmap,
                            contentDescription = null,
                            modifier = Modifier
                                .size(if (isWide) 50.dp else 42.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = songTitle.ifBlank { "Sin reproducción" },
                            fontSize = when {
                                isWide -> 18.sp
                                isSpacious -> 16.sp
                                ultraCompact -> 13.sp
                                else -> 15.sp
                            },
                            lineHeight = when {
                                isWide -> 21.sp
                                isSpacious -> 19.sp
                                else -> 17.sp
                            },
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
                            fontSize = when {
                                isWide -> 13.sp
                                isSpacious -> 12.sp
                                ultraCompact -> 10.sp
                                else -> 11.sp
                            },
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.82f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(shadow = textShadow)
                        )
                    }

                    if (onOpenYoutube != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            onClick = onOpenYoutube,
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFE53935).copy(alpha = 0.86f),
                            modifier = Modifier.height(if (ultraCompact) 28.dp else 32.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = if (isSpacious) 10.dp else 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureInPictureAlt,
                                    contentDescription = "YouTube en ventana",
                                    tint = Color.White,
                                    modifier = Modifier.size(if (ultraCompact) 14.dp else 16.dp)
                                )
                                if (isSpacious) {
                                    Text(
                                        text = "YT",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                // Zona media: Barra de progreso interactiva + tiempos
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
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
                                .height(if (isSpacious) 5.dp else 4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.28f))
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
                            fontSize = if (isSpacious) 11.sp else 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.80f),
                            style = TextStyle(fontFeatureSettings = "tnum")
                        )
                        Text(
                            text = formatDurationMs(duration),
                            fontSize = if (isSpacious) 11.sp else 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.80f),
                            style = TextStyle(fontFeatureSettings = "tnum")
                        )
                    }
                }

                // Fila inferior: Controles Anterior / Play-Pause / Siguiente con separación acotada sin deformarse
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val playBtnSize = when {
                        isWide -> 48.dp
                        isSpacious -> 44.dp
                        ultraCompact -> 36.dp
                        else -> 40.dp
                    }
                    val skipBtnSize = when {
                        isWide -> 40.dp
                        isSpacious -> 36.dp
                        else -> 32.dp
                    }

                    Row(
                        modifier = Modifier
                            .widthIn(max = 260.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { GlobalState.skipToPrevious() },
                            modifier = Modifier.size(skipBtnSize)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Anterior",
                                tint = Color.White,
                                modifier = Modifier.size(if (isSpacious) 28.dp else 23.dp)
                            )
                        }

                        Surface(
                            onClick = { GlobalState.togglePlayPause() },
                            shape = CircleShape,
                            color = palette.accent,
                            shadowElevation = 4.dp,
                            modifier = Modifier.size(playBtnSize)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Reproducir / Pausar",
                                    tint = Color.White,
                                    modifier = Modifier.size(if (isSpacious) 26.dp else 22.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { GlobalState.skipToNext() },
                            modifier = Modifier.size(skipBtnSize)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Siguiente",
                                tint = Color.White,
                                modifier = Modifier.size(if (isSpacious) 28.dp else 23.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showExpandedModal) {
        ExpandedMusicModal(onDismiss = { showExpandedModal = false })
    }
}

/**
 * Barra inferior para modo vertical: coloca VOZ en el extremo izquierdo y CAM en el extremo derecho,
 * grandes y con etiqueta visible, separados por los controles centrales para evitar errores al conducir.
 */
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

    val micBg by animateColorAsState(
        targetValue = when {
            isListening -> Color(0xFFFF007F)
            isSpeaking -> Color(0xFF00B8D4)
            else -> palette.routesTileBg
        },
        animationSpec = tween(200),
        label = "portraitMicBg"
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = palette.cardBg.copy(alpha = 0.96f),
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, palette.cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── EXTREMO IZQUIERDO: BOTÓN GRANDE ASISTENTE DE VOZ ──
            Surface(
                onClick = {
                    if (isListening || isSpeaking) ArletAssistant.closeOverlay()
                    else ArletAssistant.triggerManualListening()
                },
                shape = RoundedCornerShape(16.dp),
                color = micBg,
                border = BorderStroke(
                    1.dp,
                    if (isListening || isSpeaking) Color.Transparent else palette.accent.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .width(92.dp)
                    .fillMaxHeight()
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Asistente Sara",
                        tint = if (isListening || isSpeaking) Color.White else palette.accent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isListening) "OÍR" else "VOZ",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isListening || isSpeaking) Color.White else palette.accent
                    )
                }
            }

            // ── ZONA CENTRAL SEPARADORA: Inicio · Vista · Ajustes ──
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockIconButton(
                    icon = Icons.Default.GridView,
                    contentDescription = "Menú Principal",
                    containerColor = palette.accent,
                    contentColor = Color.White,
                    sizeDp = 42,
                    onClick = onBackToHome
                )

                DockIconButton(
                    icon = if (isSplitMode) Icons.Default.Fullscreen else Icons.Default.VerticalSplit,
                    contentDescription = "Mostrar / Ocultar Widgets",
                    containerColor = if (isSplitMode) palette.routesTileBg else palette.subCardBg,
                    contentColor = if (isSplitMode) palette.accent else palette.textPrimary,
                    sizeDp = 42,
                    onClick = onToggleSplitMode
                )

                DockIconButton(
                    icon = Icons.Default.Settings,
                    contentDescription = "Ajustes",
                    containerColor = palette.subCardBg,
                    contentColor = palette.textSecondary,
                    sizeDp = 42,
                    onClick = onOpenSettings
                )
            }

            // ── EXTREMO DERECHO: BOTÓN GRANDE CÁMARA / DASHCAM ──
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isRec) Color(0xFFE53935) else palette.dashcamTileBg,
                border = BorderStroke(
                    1.dp,
                    if (isRec) Color.Transparent else palette.dashcamTileFg.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .width(92.dp)
                    .fillMaxHeight()
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
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isRec) Icons.Default.Stop else Icons.Default.Videocam,
                        contentDescription = "Cámara Dashcam",
                        tint = if (isRec) Color.White else palette.dashcamTileFg,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isRec) "REC" else "CAM",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isRec) Color.White else palette.dashcamTileFg
                    )
                }
            }
        }
    }
}

/**
 * Dock izquierdo ergonómico (80.dp) para conducción horizontal:
 * Separa físicamente el botón grande de VOZ (arriba) y el botón grande de CAM (abajo),
 * colocando los botones de Vista Dividida y Ajustes en el centro como barrera táctil.
 */
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

    val micBg by animateColorAsState(
        targetValue = when {
            isListening -> Color(0xFFFF007F)
            isSpeaking -> Color(0xFF00B8D4)
            else -> palette.routesTileBg
        },
        animationSpec = tween(200),
        label = "dockMicBg"
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = palette.cardBg.copy(alpha = 0.95f),
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, palette.cardBorder)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 10.dp, horizontal = 6.dp)
        ) {
            val isCompactHeight = maxHeight < 390.dp
            val heroBtnHeight = if (isCompactHeight) 52.dp else 64.dp
            val heroIconSize = if (isCompactHeight) 24.dp else 28.dp

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. CABECERA + BOTÓN INICIO
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = clockText.ifEmpty { "12:00" },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.textPrimary,
                            style = TextStyle(fontFeatureSettings = "tnum")
                        )
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

                    Surface(
                        onClick = onBackToHome,
                        shape = RoundedCornerShape(15.dp),
                        color = palette.accent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isCompactHeight) 38.dp else 44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.GridView,
                                contentDescription = "Menú Principal",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // 2. MITAD SUPERIOR: BOTÓN GRANDE DE VOZ (ASISTENTE SARA)
                Surface(
                    onClick = {
                        if (isListening || isSpeaking) ArletAssistant.closeOverlay()
                        else ArletAssistant.triggerManualListening()
                    },
                    shape = RoundedCornerShape(18.dp),
                    color = micBg,
                    border = BorderStroke(
                        1.5.dp,
                        if (isListening || isSpeaking) Color.Transparent else palette.accent.copy(alpha = 0.40f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heroBtnHeight)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Asistente de Voz",
                            tint = if (isListening || isSpeaking) Color.White else palette.accent,
                            modifier = Modifier.size(heroIconSize)
                        )
                        Text(
                            text = if (isListening) "OYENDO" else "VOZ",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            color = if (isListening || isSpeaking) Color.White else palette.accent
                        )
                    }
                }

                // 3. ZONA INTERMEDIA SEPARADORA: Vista Dividida + Ajustes (evita tocar CAM por error al buscar VOZ)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onToggleSplitMode,
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSplitMode) palette.routesTileBg else palette.subCardBg,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isSplitMode) Icons.Default.Fullscreen else Icons.Default.VerticalSplit,
                                contentDescription = "Alternar Panel Lateral",
                                tint = if (isSplitMode) palette.accent else palette.textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = onOpenSettings,
                        shape = RoundedCornerShape(12.dp),
                        color = palette.subCardBg,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Ajustes",
                                tint = palette.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // 4. MITAD INFERIOR: BOTÓN GRANDE DE CÁMARA (DASHCAM) — Físicamente alejado de VOZ
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (isRec) Color(0xFFE53935) else palette.dashcamTileBg,
                    border = BorderStroke(
                        1.5.dp,
                        if (isRec) Color.Transparent else palette.dashcamTileFg.copy(alpha = 0.40f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heroBtnHeight)
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
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isRec) Icons.Default.Stop else Icons.Default.Videocam,
                            contentDescription = "Cámara Dashcam",
                            tint = if (isRec) Color.White else palette.dashcamTileFg,
                            modifier = Modifier.size(heroIconSize)
                        )
                        Text(
                            text = if (isRec) "STOP" else "CAM",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            color = if (isRec) Color.White else palette.dashcamTileFg
                        )
                    }
                }
            }
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
