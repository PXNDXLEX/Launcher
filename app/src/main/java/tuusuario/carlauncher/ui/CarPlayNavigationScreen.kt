package com.tuusuario.carlauncher.ui

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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuusuario.carlauncher.services.ArletAssistant
import com.tuusuario.carlauncher.services.DashcamManager
import com.tuusuario.carlauncher.services.GlobalState
import com.tuusuario.carlauncher.ui.widgets.SpeedometerWidget
import kotlinx.coroutines.delay

/**
 * Interfaz Cockpit HUD 3D limpia y moderna para Modo Navegación / Seguimiento GPS:
 *  - Arriba Izquierda: Reproductor de Música HUD con título, carátula, barra de progreso y botones circulares.
 *  - Abajo Izquierda: Pod circular de Velocímetro con los 18 velocímetros creados (deslizar para cambiar estilo).
 *  - Abajo Centro: Barra Dock redondeada estilo cabina (Home, Velocímetro, Ajustes, Apps, Voz Sara / Dashcam).
 *  (Sin tacómetro derecho para dejar el mapa 3D completamente despejado).
 */
@Composable
fun CarPlayNavigationOverlay(
    isLandscape: Boolean,
    isSplitMode: Boolean,
    onToggleSplitMode: () -> Unit,
    onBackToHome: () -> Unit,
    onOpenFullSpeedometer: () -> Unit,
    onOpenAllApps: () -> Unit = {},
    onOpenYoutube: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val navPanelPct = AppSettings.navPanelPercent.value.coerceIn(25f, 50f)
    val scaleFactor = 0.90f + ((navPanelPct - 25f) / 25f) * 0.24f

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        val gaugeSizeRaw = if (isLandscape) {
            (minOf(maxWidth * 0.27f, maxHeight * 0.56f) * scaleFactor).coerceIn(172.dp, 255.dp)
        } else {
            (minOf(maxWidth * 0.44f, maxHeight * 0.28f) * scaleFactor).coerceIn(150.dp, 210.dp)
        }
        val animatedGaugeSize by animateDpAsState(
            targetValue = gaugeSizeRaw,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "cockpitGaugeSize"
        )

        // ── 1. ARRIBA IZQUIERDA: WIDGET DE MÚSICA HUD COCKPIT ──
        CockpitTopLeftMusicHud(
            modifier = Modifier
                .align(Alignment.TopStart)
                .widthIn(max = if (isLandscape) 275.dp else 235.dp),
            onOpenYoutube = onOpenYoutube
        )

        // ── 2. ABAJO IZQUIERDA: POD DE VELOCÍMETRO (CON LOS 18 VELOCÍMETROS CREADOS) ──
        AnimatedVisibility(
            visible = isSplitMode,
            enter = slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 360f)
            ) + fadeIn(tween(220)) + scaleIn(initialScale = 0.88f),
            exit = slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(200, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(180)) + scaleOut(targetScale = 0.88f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = if (isLandscape) 4.dp else 68.dp)
        ) {
            CockpitSpeedometerPod(
                size = animatedGaugeSize,
                onClick = onOpenFullSpeedometer
            )
        }

        // ── 3. ABAJO CENTRO: BARRA DOCK REDONDEADA COCKPIT ──
        CockpitBottomCenterDock(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp),
            isSplitMode = isSplitMode,
            onBackToHome = onBackToHome,
            onOpenFullSpeedometer = onOpenFullSpeedometer,
            onOpenSettings = onOpenSettings,
            onOpenAllApps = onOpenAllApps,
            onToggleSplitMode = onToggleSplitMode
        )
    }
}

/**
 * Widget de Música HUD ubicado en la esquina superior izquierda,
 * integrando título de la canción, artista, miniatura de carátula, barra de progreso interactiva,
 * botones circulares con aro luminoso, acceso a YouTube PiP y modal expandido al tocar el título.
 */
@Composable
private fun CockpitTopLeftMusicHud(
    modifier: Modifier = Modifier,
    onOpenYoutube: () -> Unit
) {
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

    val titleShadow = remember {
        Shadow(color = Color.Black.copy(alpha = 0.85f), offset = Offset(0f, 2f), blurRadius = 8f)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        // Fila 1: Mini carátula + Título + Subtítulo artista (toque abre reproductor completo)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable { showExpandedModal = true }
                .padding(end = 6.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (artImageBitmap != null) {
                Image(
                    bitmap = artImageBitmap,
                    contentDescription = "Carátula",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.40f), RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(9.dp))
            }

            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = songTitle.ifBlank { "Sin reproducción" },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(shadow = titleShadow)
                )
                val subtitleText = if (songArtist.isNotBlank()) songArtist else playerApp
                if (subtitleText.isNotBlank()) {
                    Text(
                        text = subtitleText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFC2D6FF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(shadow = titleShadow)
                    )
                }
            }
        }

        // Barra de progreso fina interactiva cuando hay canción activa
        if (duration > 0L) {
            Box(
                modifier = Modifier
                    .width(204.dp)
                    .height(10.dp)
                    .pointerInput(duration) {
                        detectTapGestures { offset ->
                            if (size.width > 0) {
                                val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                val targetMs = (frac * duration).toLong()
                                GlobalState.seekTo(targetMs)
                                GlobalState.songPosition.value = targetMs
                            }
                        }
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.coerceIn(0.03f, 1f))
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF38F2A6))
                    )
                }
            }
        }

        // Fila 2: Botones circulares translúcidos con aro luminoso (|<, Play/Pause, >|, BT, YouTube)
        Row(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HudCircleMediaButton(
                icon = Icons.Default.SkipPrevious,
                contentDescription = "Anterior",
                isPrimary = false,
                onClick = { GlobalState.skipToPrevious() }
            )

            HudCircleMediaButton(
                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Reproducir / Pausar",
                isPrimary = true,
                onClick = { GlobalState.togglePlayPause() }
            )

            HudCircleMediaButton(
                icon = Icons.Default.SkipNext,
                contentDescription = "Siguiente",
                isPrimary = false,
                onClick = { GlobalState.skipToNext() }
            )

            HudCircleMediaButton(
                icon = Icons.Default.BluetoothAudio,
                contentDescription = "Reproductor / Audio",
                isPrimary = false,
                onClick = { showExpandedModal = true },
                onLongClick = onOpenYoutube
            )

            // Botón compacto YouTube PiP
            Surface(
                onClick = onOpenYoutube,
                shape = CircleShape,
                color = Color(0xFF1A2A5E).copy(alpha = 0.72f),
                border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.65f)),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayCircleOutline,
                        contentDescription = "YouTube Mini",
                        tint = Color(0xFFFF6E6E),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }

    if (showExpandedModal) {
        ExpandedMusicModal(onDismiss = { showExpandedModal = false })
    }
}

@Composable
private fun HudCircleMediaButton(
    icon: ImageVector,
    contentDescription: String,
    isPrimary: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val bgColor = if (isPrimary) {
        Color(0xFF162458).copy(alpha = 0.88f)
    } else {
        Color(0xFF1D3570).copy(alpha = 0.65f)
    }
    val borderColor = if (isPrimary) {
        Color(0xFF7CA8FF).copy(alpha = 0.85f)
    } else {
        Color.White.copy(alpha = 0.55f)
    }

    Surface(
        shape = CircleShape,
        color = bgColor,
        border = BorderStroke(if (isPrimary) 1.6.dp else 1.2.dp, borderColor),
        shadowElevation = if (isPrimary) 8.dp else 4.dp,
        modifier = Modifier
            .size(if (isPrimary) 44.dp else 40.dp)
            .pointerInput(onLongClick) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick?.invoke() }
                )
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(if (isPrimary) 23.dp else 20.dp)
            )
        }
    }
}

/**
 * Pod Circular Izquierdo: Integra nuestros 18 Velocímetros programados dentro del bisel circular Cockpit.
 * Permite deslizar horizontalmente para alternar entre los 18 estilos en vivo o tocar para pantalla completa.
 */
@Composable
private fun CockpitSpeedometerPod(
    size: Dp,
    onClick: () -> Unit
) {
    val activeStyle = AppSettings.speedoStyle.value
    val styles = remember {
        listOf(
            "PREMIUM", "NEON", "RACING", "CYBER", "AURA", "VORTEX",
            "QUANTUM", "PULSAR", "PLASMA", "ANIME", "KAIJU", "OMNIMON",
            "SHONEN", "MECHA", "OVERDRIVE", "NEBULA", "DEMONIC", "CUSTOM"
        )
    }
    var dragAccum by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .size(size)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragAccum > 55f) {
                            val idx = styles.indexOf(AppSettings.speedoStyle.value)
                            val prev = if (idx <= 0) styles.lastIndex else idx - 1
                            AppSettings.setSpeedoStyle(styles[prev])
                        } else if (dragAccum < -55f) {
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
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Fondo y bisel exterior tipo Cockpit (igual al dial izquierdo de la referencia)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = this.size.minDimension / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)

            // Sombra radial profunda del reloj
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF141842).copy(alpha = 0.94f),
                        Color(0xFF090B24).copy(alpha = 0.97f),
                        Color(0xFF050614).copy(alpha = 0.92f)
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius * 0.96f,
                center = center
            )

            // Arco metálico exterior de 240° (estilo tacómetro automotriz)
            val arcInset = radius * 0.05f
            drawArc(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF8A94B8).copy(alpha = 0.68f),
                        Color(0xFF3E4668).copy(alpha = 0.35f)
                    )
                ),
                startAngle = 150f,
                sweepAngle = 240f,
                useCenter = false,
                topLeft = Offset(arcInset, arcInset),
                size = Size(this.size.width - arcInset * 2f, this.size.height - arcInset * 2f),
                style = Stroke(width = radius * 0.055f, cap = StrokeCap.Round)
            )
        }

        // Velocímetro seleccionado por el usuario (los 18 diseños creados)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(size * 0.06f)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(
                targetState = activeStyle,
                animationSpec = tween(220),
                label = "cockpitLiveSpeedo"
            ) { _ ->
                SpeedometerWidget(
                    scaleMultiplier = 1.05f,
                    forceDarkBackground = true
                )
            }
        }
    }
}

/**
 * Barra Dock Inferior Central estilo Cockpit:
 * Contiene los 5 iconos principales:
 *  1. Home (con indicador luminoso cian debajo)
 *  2. Velocímetro
 *  3. Ajustes (Llave inglesa)
 *  4. Aplicaciones (Cuadrícula)
 *  5. Voz Sara / Conectividad (Toque activa asistente Sara; pulsación larga inicia/detiene Dashcam)
 */
@Composable
private fun CockpitBottomCenterDock(
    modifier: Modifier = Modifier,
    isSplitMode: Boolean,
    onBackToHome: () -> Unit,
    onOpenFullSpeedometer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAllApps: () -> Unit,
    onToggleSplitMode: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isRec = DashcamManager.isRecording.value
    val isListening = ArletAssistant.isListening.value || ArletAssistant.isWakeWordActiveWindow.value
    val isSpeaking = ArletAssistant.isSpeaking.value

    val voiceTint by animateColorAsState(
        targetValue = when {
            isListening -> Color(0xFFFF4081)
            isSpeaking -> Color(0xFF00E5FF)
            else -> Color.White.copy(alpha = 0.85f)
        },
        animationSpec = tween(200),
        label = "dockVoiceTint"
    )

    Surface(
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(50),
        color = Color(0xFF080A16).copy(alpha = 0.92f),
        shadowElevation = 12.dp,
        border = BorderStroke(1.2.dp, Color(0xFF3B4675).copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. HOME (con indicador cian luminoso debajo, como en la imagen)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onBackToHome)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Inicio",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF00E5FF))
                )
            }

            // 2. VELOCÍMETRO (Toque abre velocímetro completo; pulsación larga muestra/oculta reloj HUD)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onOpenFullSpeedometer() },
                            onLongPress = { onToggleSplitMode() }
                        )
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "Velocímetro",
                    tint = if (isSplitMode) Color.White.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(23.dp)
                )
            }

            // 3. AJUSTES (Icono llave inglesa / Build igual a la imagen)
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = "Ajustes",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(21.dp)
                )
            }

            // 4. APLICACIONES (Icono cuadrícula 3x3 igual a la imagen)
            IconButton(
                onClick = onOpenAllApps,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = "Aplicaciones",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(23.dp)
                )
            }

            // 5. CONECTIVIDAD / ASISTENTE SARA & DASHCAM
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .pointerInput(isRec, isListening, isSpeaking) {
                        detectTapGestures(
                            onTap = {
                                if (isListening || isSpeaking) ArletAssistant.closeOverlay()
                                else ArletAssistant.triggerManualListening()
                            },
                            onLongPress = {
                                if (isRec) DashcamManager.stopRecording()
                                else DashcamManager.startRecording(context, lifecycleOwner)
                            }
                        )
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.PermPhoneMsg,
                    contentDescription = "Asistente Sara / Dashcam",
                    tint = if (isRec) Color(0xFFFF5252) else voiceTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
