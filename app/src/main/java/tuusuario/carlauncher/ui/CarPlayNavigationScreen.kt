package com.tuusuario.carlauncher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Interfaz Premium de Seguimiento CarPlay / Cockpit HUD:
 *  - Izquierda (Mapa Grande):
 *      • Arriba Izquierda: Reloj Digital Interactivo con 8 modelos configurables (adaptados a Día y Noche).
 *      • Centro-Arriba Izquierda: Botón dedicado de Grabación (`GRABAR` / `REC MM:SS`).
 *      • Abajo Centro del Mapa: Barra Dock flotante estilo cabina (Home, Panel Dividido, Velocímetro, Ajustes, Apps, Voz Sara).
 *  - Derecha (Columna de 2 Widgets Premium cuando `isSplitMode` está activo):
 *      • Widget Superior Derecho: Tarjeta de Velocímetro y Telemetría (18 velocímetros con selector táctil y gestual + Máx Hoy).
 *      • Widget Inferior Derecho: Tarjeta Multimedia HUD con carátula, tiempos MM:SS, barra interactiva, Bluetooth y YouTube PiP.
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
    val navPanelPct = AppSettings.navPanelPercent.value.coerceIn(25f, 48f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        val rightColumnWidth = if (isSplitMode && isLandscape) {
            (maxWidth * (navPanelPct / 100f)).coerceIn(244.dp, 352.dp)
        } else 0.dp

        val animatedMapEndInset by animateDpAsState(
            targetValue = if (isSplitMode && isLandscape) rightColumnWidth + 10.dp else 0.dp,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "mapLeftAreaEndInset"
        )

        // ══════════════════════════════════════════════════════════════════════
        // ZONA IZQUIERDA: CONTROLES DEL MAPA GRANDE (Reloj Digital, Grabación y Dock)
        // ══════════════════════════════════════════════════════════════════════
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = animatedMapEndInset)
        ) {
            // 1. Arriba Izquierda: Reloj Digital Interactivo (8 Modelos Configurables)
            CockpitTopLeftDigitalClock(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .widthIn(max = if (isLandscape) 228.dp else 205.dp)
            )

            // 2. Izquierda (debajo del reloj): Botón dedicado de Grabación
            CockpitLeftRecordingButton(
                isLandscape = isLandscape,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        top = if (isLandscape) 94.dp else 104.dp,
                        start = 2.dp
                    )
            )

            // 3. Abajo Centro del Mapa Grande: Barra Dock Flotante
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

        // ══════════════════════════════════════════════════════════════════════
        // ZONA DERECHA: LOS 2 WIDGETS PREMIUM (1. Velocímetro 18 Estilos + 2. Música)
        // ══════════════════════════════════════════════════════════════════════
        if (isLandscape) {
            AnimatedVisibility(
                visible = isSplitMode,
                enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = spring(dampingRatio = 0.80f, stiffness = 380f)
                ) + fadeIn(tween(220)) + scaleIn(initialScale = 0.94f),
                exit = slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(200, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(180)) + scaleOut(targetScale = 0.94f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(rightColumnWidth)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Widget 1 (Arriba Derecha): Tarjeta Premium de Velocímetro (18 Estilos)
                    CockpitRightSpeedometerCard(
                        onOpenFullSpeedometer = onOpenFullSpeedometer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.54f)
                    )

                    // Widget 2 (Abajo Derecha): Tarjeta Premium de Música y Multimedia
                    CockpitBottomRightMusicWidget(
                        onOpenYoutube = onOpenYoutube,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.46f)
                    )
                }
            }
        } else {
            // En Modo Vertical: Los 2 widgets se ubican lado a lado sobre el Dock inferior
            AnimatedVisibility(
                visible = isSplitMode,
                enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.92f),
                exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.92f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
                    .fillMaxWidth()
                    .height(166.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CockpitRightSpeedometerCard(
                        onOpenFullSpeedometer = onOpenFullSpeedometer,
                        modifier = Modifier
                            .weight(0.46f)
                            .fillMaxHeight()
                    )
                    CockpitBottomRightMusicWidget(
                        onOpenYoutube = onOpenYoutube,
                        modifier = Modifier
                            .weight(0.54f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

/**
 * Reloj Digital Interactivo en la esquina superior izquierda con 6 modelos y estilos distintos:
 *  0 -> CYBER NEON (HUD futurista con cápsula de segundos y estado GPS)
 *  1 -> FLIP RETRO (Tarjetas mecánicas Split-Flap divididas para Hora y Minuto)
 *  2 -> GLASS ULTRA (Cristal flotante minimalista con separador pulsante)
 *  3 -> RACING GT (Cronógrafo de telemetría deportiva con barra de RPM superior)
 *  4 -> MATRIX LED (Pantalla digital retro-futurista con dígitos fantasma 88:88)
 *  5 -> AURA HOLOGRAM (Holográfico con números en degradado y anillo circular de segundos)
 *
 * Se puede intercalar tocando el reloj o deslizando horizontalmente.
 */
@Composable
private fun CockpitTopLeftDigitalClock(
    modifier: Modifier = Modifier
) {
    val activeClockStyle = AppSettings.digitalClockStyle.value.coerceIn(0, 7)
    val isDayMap = AppSettings.mapStyle.value == "LIGHT"
    val accentColor = Color(AppSettings.uiColor.value)
    val styleNames = remember {
        listOf(
            "CYBER NEON", "FLIP RETRO", "GLASS ULTRA", "RACING GT",
            "MATRIX LED", "AURA HOLO", "CHRONO SWISS", "BENTO DAY"
        )
    }

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000L - (System.currentTimeMillis() % 1000L))
        }
    }

    val hour12 = remember(now) {
        val h = now.hour % 12
        String.format("%02d", if (h == 0) 12 else h)
    }
    val minuteStr = remember(now) { String.format("%02d", now.minute) }
    val secondStr = remember(now) { String.format("%02d", now.second) }
    val amPmStr = remember(now) { if (now.hour < 12) "AM" else "PM" }
    val dateStr = remember(now) {
        val fmt = DateTimeFormatter.ofPattern("EEE, dd MMM", Locale("es", "ES"))
        now.format(fmt).uppercase(Locale.getDefault()).replace(".", "")
    }
    val secondFraction = now.second / 60f

    val pulseTransition = rememberInfiniteTransition(label = "clockPulse")
    val colonAlpha by pulseTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "colonPulse"
    )

    var dragAccum by remember { mutableStateOf(0f) }

    Column(
        modifier = modifier
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragAccum > 42f) {
                            AppSettings.setDigitalClockStyle(AppSettings.digitalClockStyle.value - 1)
                        } else if (dragAccum < -42f) {
                            AppSettings.setDigitalClockStyle(AppSettings.digitalClockStyle.value + 1)
                        }
                        dragAccum = 0f
                    }
                ) { change, amount ->
                    change.consume()
                    dragAccum += amount
                }
            }
            .clip(RoundedCornerShape(20.dp))
            .clickable {
                AppSettings.setDigitalClockStyle(AppSettings.digitalClockStyle.value + 1)
            },
        horizontalAlignment = Alignment.Start
    ) {
        Crossfade(
            targetState = activeClockStyle,
            animationSpec = tween(240),
            label = "digitalClockCrossfade"
        ) { styleIdx ->
            when (styleIdx) {
                // ── MODELO 1: CYBER NEON HUD ─────────────────────────────────────
                0 -> {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF0A1232).copy(alpha = 0.88f),
                        border = BorderStroke(
                            1.3.dp,
                            Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF38F2A6)))
                        ),
                        shadowElevation = 10.dp
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "$hour12:$minuteStr",
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    lineHeight = 32.sp,
                                    style = TextStyle(
                                        fontFeatureSettings = "tnum",
                                        shadow = Shadow(Color(0xFF00E5FF).copy(alpha = 0.7f), blurRadius = 12f)
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.padding(bottom = 3.dp)) {
                                    Text(
                                        text = amPmStr,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF38F2A6)
                                    )
                                    Text(
                                        text = ":$secondStr",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00E5FF),
                                        style = TextStyle(fontFeatureSettings = "tnum")
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF38F2A6), CircleShape)
                                )
                                Text(
                                    text = dateStr,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    color = Color(0xFFB8CEFF)
                                )
                            }
                        }
                    }
                }

                // ── MODELO 2: FLIP RETRO (SPLIT-FLAP) ────────────────────────────
                1 -> {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF0C1024).copy(alpha = 0.92f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                        shadowElevation = 10.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                FlipDigitCard(text = hour12)
                                Text(
                                    text = ":",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFFFB300).copy(alpha = colonAlpha)
                                )
                                FlipDigitCard(text = minuteStr)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(start = 2.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF1B2448)
                                    ) {
                                        Text(
                                            text = amPmStr,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFFFFB300),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = secondStr,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White.copy(alpha = 0.78f),
                                        style = TextStyle(fontFeatureSettings = "tnum")
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = dateStr,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = Color(0xFFFFD54F)
                            )
                        }
                    }
                }

                // ── MODELO 3: GLASS ULTRA (MINIMAL CRISTAL) ──────────────────────
                2 -> {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xFF162458).copy(alpha = 0.68f),
                        border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.35f)),
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = hour12,
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.Light,
                                    color = Color.White,
                                    style = TextStyle(fontFeatureSettings = "tnum")
                                )
                                Text(
                                    text = ":",
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38F2A6).copy(alpha = colonAlpha),
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )
                                Text(
                                    text = minuteStr,
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    style = TextStyle(fontFeatureSettings = "tnum")
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(28.dp)
                                    .background(Color.White.copy(alpha = 0.28f))
                            )
                            Column {
                                Text(
                                    text = "$amPmStr · $secondStr s",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF38F2A6)
                                )
                                Text(
                                    text = dateStr,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }

                // ── MODELO 4: RACING GT TELEMETRY ────────────────────────────────
                3 -> {
                    Surface(
                        shape = RoundedCornerShape(topStart = 18.dp, bottomEnd = 18.dp, topEnd = 6.dp, bottomStart = 6.dp),
                        color = Color(0xFF0B0E22).copy(alpha = 0.94f),
                        border = BorderStroke(1.3.dp, Color(0xFFFF3B4E).copy(alpha = 0.75f)),
                        shadowElevation = 10.dp
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)) {
                            // Barra de telemetría segmentada tipo F1
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val activeBars = ((secondFraction * 12).toInt()).coerceIn(1, 12)
                                for (i in 1..12) {
                                    val barColor = when {
                                        i > activeBars -> Color.White.copy(alpha = 0.14f)
                                        i <= 6 -> Color(0xFF00E676)
                                        i <= 9 -> Color(0xFFFFEA00)
                                        else -> Color(0xFFFF1744)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(10.dp)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(barColor)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "CHRONO GT",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFFF5252)
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "$hour12:$minuteStr",
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Black,
                                    fontStyle = FontStyle.Italic,
                                    color = Color.White,
                                    lineHeight = 30.sp,
                                    style = TextStyle(fontFeatureSettings = "tnum")
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = ".$secondStr",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    fontStyle = FontStyle.Italic,
                                    color = Color(0xFFFF5252),
                                    modifier = Modifier.padding(bottom = 2.dp),
                                    style = TextStyle(fontFeatureSettings = "tnum")
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = amPmStr,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(bottom = 3.dp)
                                )
                            }
                            Text(
                                text = dateStr,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF9AB0FF)
                            )
                        }
                    }
                }

                // ── MODELO 5: MATRIX LED (88:88 RETRO-FUTURISTA) ─────────────────
                4 -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF050B14).copy(alpha = 0.95f),
                        border = BorderStroke(1.4.dp, Color(0xFF00E676).copy(alpha = 0.65f)),
                        shadowElevation = 10.dp
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "QUARTZ LED",
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E676).copy(alpha = 0.7f)
                                )
                                Text(
                                    text = amPmStr,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF00E676)
                                )
                            }
                            Box(contentAlignment = Alignment.CenterStart) {
                                // Dígitos fantasma 88:88:88 de fondo
                                Text(
                                    text = "88:88:88",
                                    fontSize = 25.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF00E676).copy(alpha = 0.12f)
                                )
                                Text(
                                    text = "$hour12:$minuteStr:$secondStr",
                                    fontSize = 25.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF38F8A8),
                                    style = TextStyle(
                                        shadow = Shadow(Color(0xFF00E676).copy(alpha = 0.8f), blurRadius = 10f)
                                    )
                                )
                            }
                            Text(
                                text = dateStr,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF80CBC4)
                            )
                        }
                    }
                }

                // ── MODELO 6: AURA HOLOGRAM (ANILLO DE SEGUNDOS + DEGRADADO) ─────
                5 -> {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xFF0F133A).copy(alpha = 0.90f),
                        border = BorderStroke(
                            1.3.dp,
                            Brush.linearGradient(
                                listOf(Color(0xFF00E5FF), Color(0xFFB388FF), Color(0xFF38F2A6))
                            )
                        ),
                        shadowElevation = 10.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(42.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val strokeW = 3.5.dp.toPx()
                                    drawCircle(
                                        color = Color.White.copy(alpha = 0.14f),
                                        style = Stroke(width = strokeW)
                                    )
                                    drawArc(
                                        brush = Brush.sweepGradient(
                                            listOf(Color(0xFF00E5FF), Color(0xFFB388FF), Color(0xFF38F2A6))
                                        ),
                                        startAngle = -90f,
                                        sweepAngle = secondFraction * 360f,
                                        useCenter = false,
                                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                                    )
                                }
                                Text(
                                    text = secondStr,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    style = TextStyle(fontFeatureSettings = "tnum")
                                )
                            }

                            Column {
                                Text(
                                    text = "$hour12:$minuteStr $amPmStr",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    style = TextStyle(
                                        brush = Brush.linearGradient(
                                            listOf(Color.White, Color(0xFF80D8FF), Color(0xFF38F2A6))
                                        ),
                                        fontFeatureSettings = "tnum"
                                    )
                                )
                                Text(
                                    text = dateStr,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD1C4E9)
                                )
                            }
                        }
                    }
                }

                // ── MODELO 7: CHRONO SWISS (CRONÓGRAFO EJECUTIVO CON ANILLO DE SEGUNDOS) ──
                6 -> {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isDayMap) Color(0xFF141B2D).copy(alpha = 0.94f) else Color(0xFF0D111A).copy(alpha = 0.94f),
                        border = BorderStroke(
                            1.3.dp,
                            Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFFDE68A)))
                        ),
                        shadowElevation = 10.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(38.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val r = size.minDimension / 2f
                                    drawCircle(
                                        color = Color(0xFFF59E0B).copy(alpha = 0.20f),
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                    val angleRad = Math.toRadians((secondFraction * 360f - 90f).toDouble())
                                    val hx = center.x + (r * 0.72f * kotlin.math.cos(angleRad)).toFloat()
                                    val hy = center.y + (r * 0.72f * kotlin.math.sin(angleRad)).toFloat()
                                    drawLine(
                                        color = Color(0xFFFBBF24),
                                        start = center,
                                        end = Offset(hx, hy),
                                        strokeWidth = 2.2.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                    drawCircle(color = Color.White, radius = 2.5.dp.toPx())
                                }
                            }
                            Column {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = "$hour12:$minuteStr",
                                        fontSize = 27.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFFFEF3C7),
                                        lineHeight = 28.sp,
                                        style = TextStyle(fontFeatureSettings = "tnum")
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "$secondStr $amPmStr",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFFFBBF24),
                                        modifier = Modifier.padding(bottom = 3.dp)
                                    )
                                }
                                Text(
                                    text = "SWISS CHRONO · $dateStr",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD97706)
                                )
                            }
                        }
                    }
                }

                // ── MODELO 8: BENTO DAYLIGHT (PORCELANA / OBSIDIANA ADAPTABLE DÍA Y NOCHE) ──
                else -> {
                    val bg = if (isDayMap) Color.White.copy(alpha = 0.95f) else Color(0xFF101829).copy(alpha = 0.94f)
                    val fg = if (isDayMap) Color(0xFF0F172A) else Color.White
                    val sub = if (isDayMap) Color(0xFF475569) else Color(0xFF94A3B8)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = bg,
                        border = BorderStroke(1.3.dp, accentColor.copy(alpha = 0.55f)),
                        shadowElevation = 9.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(accentColor)
                            )
                            Column {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = "$hour12:$minuteStr",
                                        fontSize = 29.sp,
                                        fontWeight = FontWeight.Black,
                                        color = fg,
                                        lineHeight = 30.sp,
                                        style = TextStyle(fontFeatureSettings = "tnum")
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = accentColor.copy(alpha = 0.14f),
                                        modifier = Modifier.padding(bottom = 3.dp)
                                    ) {
                                        Text(
                                            text = "$amPmStr · $secondStr",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = accentColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = dateStr,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = sub
                                )
                            }
                        }
                    }
                }
            }
        }

        // Indicador discreto de los 8 modelos de reloj + nombre del estilo activo
        val pillLabelColor = if (isDayMap) Color(0xFF1E293B) else Color.White.copy(alpha = 0.78f)
        val dotIdleColor = if (isDayMap) Color(0xFF64748B).copy(alpha = 0.45f) else Color.White.copy(alpha = 0.35f)
        val dotActiveColor = if (isDayMap) accentColor else Color(0xFF38F2A6)
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            for (i in 0..7) {
                val isSelected = i == activeClockStyle
                Box(
                    modifier = Modifier
                        .width(if (isSelected) 11.dp else 4.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (isSelected) dotActiveColor else dotIdleColor)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = styleNames[activeClockStyle],
                fontSize = 8.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.6.sp,
                color = pillLabelColor
            )
        }
    }
}

@Composable
private fun FlipDigitCard(text: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF182042),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        shadowElevation = 4.dp
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = text,
                fontSize = 27.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                style = TextStyle(fontFeatureSettings = "tnum")
            )
            // Línea horizontal divisoria mecánica Split-Flap
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.55f))
                )
            }
        }
    }
}

/**
 * Widget 2 (Abajo Derecha): Tarjeta Multimedia HUD Premium adaptada a Modo Día y Modo Noche,
 * con carátula, título, artista, tiempos MM:SS en vivo, barra de progreso interactiva y controles táctiles.
 */
@Composable
private fun CockpitBottomRightMusicWidget(
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

    val isDayMap = AppSettings.mapStyle.value == "LIGHT"
    val accentColor = Color(AppSettings.uiColor.value)

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

    fun formatDurationMs(ms: Long): String {
        if (ms <= 0L) return "0:00"
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val m = totalSec / 60L
        val s = totalSec % 60L
        return String.format(Locale.US, "%d:%02d", m, s)
    }

    // Diseño Premium Dark Frosted Glassmorphism unificado (como en la propuesta visual de alta gama)
    val cardBg = Color(0xFF101726).copy(alpha = 0.92f)
    val cardBorder = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = 0.22f),
            Color(0xFFF59E0B).copy(alpha = 0.28f)
        )
    )
    val titleColor = Color.White
    val subColor = Color(0xFF94A3B8)
    val barFillColor = Color(0xFFF59E0B) // Ámbar-Naranja vibrante de la propuesta comercial
    val barTrackColor = Color.White.copy(alpha = 0.16f)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = cardBg,
        border = BorderStroke(1.2.dp, cardBorder),
        shadowElevation = 14.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Fondo sutil con la carátula difuminada/oscurecida si existe
            if (artImageBitmap != null) {
                Image(
                    bitmap = artImageBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(24.dp)),
                    contentScale = ContentScale.Crop,
                    alpha = 0.18f
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Fila 1: Mini carátula + Título + Artista + Botón expandir
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showExpandedModal = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (artImageBitmap != null) {
                        Image(
                            bitmap = artImageBitmap,
                            contentDescription = "Carátula",
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                                .border(1.dp, barFillColor.copy(alpha = 0.40f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MusicNote, null, tint = barFillColor, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = songTitle.ifBlank { "Sin reproducción" },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = titleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subtitleText = if (songArtist.isNotBlank()) songArtist else playerApp
                        Text(
                            text = subtitleText.ifBlank { "Audio Bluetooth / Multimedia" },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = subColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { showExpandedModal = true },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = "Expandir reproductor",
                            tint = Color.White.copy(alpha = 0.80f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Fila 2: Barra de progreso interactiva con punto ámbar luminoso + Tiempos MM:SS
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
                        contentAlignment = Alignment.CenterStart
                    ) {
                        val clampedProg = progress.coerceIn(0.03f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.5.dp)
                                .clip(RoundedCornerShape(50))
                                .background(barTrackColor)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(clampedProg)
                                    .clip(RoundedCornerShape(50))
                                    .background(barFillColor)
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
                            fontWeight = FontWeight.Bold,
                            color = subColor,
                            style = TextStyle(fontFeatureSettings = "tnum")
                        )
                        Text(
                            text = if (duration > 0L) formatDurationMs(duration) else "EN VIVO",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = barFillColor,
                            style = TextStyle(fontFeatureSettings = "tnum")
                        )
                    }
                }

                // Fila 3: Botones circulares Dark Glass + Botón Play/Pause Ámbar Destacado
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HudCircleMediaButton(
                        icon = Icons.Default.SkipPrevious,
                        contentDescription = "Anterior",
                        isPrimary = false,
                        accentColor = barFillColor,
                        onClick = { GlobalState.skipToPrevious() }
                    )

                    HudCircleMediaButton(
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Reproducir / Pausar",
                        isPrimary = true,
                        accentColor = barFillColor,
                        onClick = { GlobalState.togglePlayPause() }
                    )

                    HudCircleMediaButton(
                        icon = Icons.Default.SkipNext,
                        contentDescription = "Siguiente",
                        isPrimary = false,
                        accentColor = barFillColor,
                        onClick = { GlobalState.skipToNext() }
                    )

                    HudCircleMediaButton(
                        icon = Icons.Default.BluetoothAudio,
                        contentDescription = "Reproductor / Audio",
                        isPrimary = false,
                        accentColor = barFillColor,
                        onClick = { showExpandedModal = true },
                        onLongClick = onOpenYoutube
                    )

                    Surface(
                        onClick = onOpenYoutube,
                        shape = CircleShape,
                        color = Color(0xFF1E192B).copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.75f)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayCircleOutline,
                                contentDescription = "YouTube Mini",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(19.dp)
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

@Composable
private fun HudCircleMediaButton(
    icon: ImageVector,
    contentDescription: String,
    isPrimary: Boolean,
    accentColor: Color = Color(0xFFF59E0B),
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val bgColor = if (isPrimary) {
        accentColor
    } else {
        Color(0xFF1A2336).copy(alpha = 0.88f)
    }
    val borderColor = if (isPrimary) {
        Color(0xFFFDE68A).copy(alpha = 0.85f)
    } else {
        Color.White.copy(alpha = 0.16f)
    }

    Surface(
        shape = CircleShape,
        color = bgColor,
        border = BorderStroke(if (isPrimary) 1.5.dp else 1.dp, borderColor),
        shadowElevation = if (isPrimary) 8.dp else 3.dp,
        modifier = Modifier
            .size(if (isPrimary) 44.dp else 36.dp)
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
                modifier = Modifier.size(if (isPrimary) 22.dp else 18.dp)
            )
        }
    }
}

/**
 * Widget 1 (Arriba Derecha): Tarjeta Premium de Velocímetro y Telemetría en Dark Frosted Glassmorphism:
 *  - Integra los 18 Velocímetros creados dentro de un dial deportivo con halo ámbar/azul.
 *  - Cabecera con cápsula oscura `< ESTILO >` e insignia `🏆 MÁX ... KM/H` con margen protegido.
 */
@Composable
private fun CockpitRightSpeedometerCard(
    onOpenFullSpeedometer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeStyle = AppSettings.speedoStyle.value
    val todayMax = com.tuusuario.carlauncher.services.SpeedRecordTracker.todayMaxLive.value

    val styles = remember {
        listOf(
            "PREMIUM", "NEON", "RACING", "CYBER", "AURA", "VORTEX",
            "QUANTUM", "PULSAR", "PLASMA", "ANIME", "KAIJU", "OMNIMON",
            "SHONEN", "MECHA", "OVERDRIVE", "NEBULA", "DEMONIC", "CUSTOM"
        )
    }
    var dragAccum by remember { mutableStateOf(0f) }

    val cardBg = Color(0xFF101726).copy(alpha = 0.92f)
    val cardBorder = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = 0.22f),
            Color(0xFF3898FF).copy(alpha = 0.28f)
        )
    )
    val badgeAmber = Color(0xFFF59E0B)

    Surface(
        modifier = modifier
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragAccum > 48f) {
                            val idx = styles.indexOf(AppSettings.speedoStyle.value)
                            val prev = if (idx <= 0) styles.lastIndex else idx - 1
                            AppSettings.setSpeedoStyle(styles[prev])
                        } else if (dragAccum < -48f) {
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
            },
        shape = RoundedCornerShape(24.dp),
        color = cardBg,
        border = BorderStroke(1.2.dp, cardBorder),
        shadowElevation = 14.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Cabecera de la tarjeta: Cápsula oscura < ESTILO > + Cápsula MÁX KM/H
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF1B2438),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Anterior estilo",
                            tint = badgeAmber,
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .clickable {
                                    val idx = styles.indexOf(AppSettings.speedoStyle.value)
                                    val prev = if (idx <= 0) styles.lastIndex else idx - 1
                                    AppSettings.setSpeedoStyle(styles[prev])
                                }
                        )

                        Text(
                            text = activeStyle,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.6.sp,
                            color = Color.White
                        )

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Siguiente estilo",
                            tint = badgeAmber,
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .clickable {
                                    val idx = styles.indexOf(AppSettings.speedoStyle.value)
                                    val next = (idx + 1) % styles.size
                                    AppSettings.setSpeedoStyle(styles[next])
                                }
                        )
                    }
                }

                Surface(
                    onClick = onOpenFullSpeedometer,
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF28211E),
                    border = BorderStroke(1.dp, badgeAmber.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.EmojiEvents, null, tint = badgeAmber, modifier = Modifier.size(11.dp))
                        Text(
                            text = "MÁX ${todayMax.toInt()} KM/H",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = badgeAmber
                        )
                    }
                }
            }

            // Dial Circular Deportivo del Velocímetro
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onOpenFullSpeedometer),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = this.size.minDimension / 2f
                    val center = Offset(this.size.width / 2f, this.size.height / 2f)

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF131B3A),
                                Color(0xFF0A0E22),
                                Color(0xFF060814)
                            ),
                            center = center,
                            radius = radius
                        ),
                        radius = radius * 0.98f,
                        center = center
                    )

                    val arcInset = radius * 0.045f
                    drawArc(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                badgeAmber.copy(alpha = 0.68f),
                                Color(0xFF2563EB).copy(alpha = 0.38f)
                            )
                        ),
                        startAngle = 140f,
                        sweepAngle = 260f,
                        useCenter = false,
                        topLeft = Offset(arcInset, arcInset),
                        size = Size(this.size.width - arcInset * 2f, this.size.height - arcInset * 2f),
                        style = Stroke(width = radius * 0.042f, cap = StrokeCap.Round)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(
                        targetState = activeStyle,
                        animationSpec = tween(220),
                        label = "cockpitLiveSpeedo"
                    ) { _ ->
                        SpeedometerWidget(
                            scaleMultiplier = 1.04f,
                            forceDarkBackground = true
                        )
                    }
                }
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

    // Barra Dock Inferior Flotante en Tono Oscuro Unificado (Dark Frosted Glassmorphism)
    val dockBg = Color(0xFF121A2D).copy(alpha = 0.94f)
    val dockBorder = Color.White.copy(alpha = 0.18f)
    val dockIconIdle = Color.White.copy(alpha = 0.85f)
    val dockAmber = Color(0xFFF59E0B)

    val voiceTint by animateColorAsState(
        targetValue = when {
            isListening -> Color(0xFFFF4081)
            isSpeaking -> Color(0xFF38BDF8)
            else -> dockIconIdle
        },
        animationSpec = tween(200),
        label = "dockVoiceTint"
    )

    Surface(
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(50),
        color = dockBg,
        shadowElevation = 14.dp,
        border = BorderStroke(1.2.dp, dockBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                    modifier = Modifier.size(23.dp)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(dockAmber)
                )
            }

            // Botón directo para mostrar/ocultar los 2 widgets derechos (resaltado en círculo ámbar)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isSplitMode) dockAmber.copy(alpha = 0.22f) else Color.Transparent)
                    .clickable(onClick = onToggleSplitMode)
            ) {
                Icon(
                    imageVector = Icons.Default.VerticalSplit,
                    contentDescription = "Alternar Widgets Derechos",
                    tint = if (isSplitMode) dockAmber else dockIconIdle.copy(alpha = 0.55f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onOpenFullSpeedometer)
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "Velocímetro Pantalla Completa",
                    tint = dockIconIdle,
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Ajustes",
                    tint = dockIconIdle,
                    modifier = Modifier.size(21.dp)
                )
            }

            IconButton(
                onClick = onOpenAllApps,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = "Aplicaciones",
                    tint = dockIconIdle,
                    modifier = Modifier.size(22.dp)
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
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
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Asistente Sara / Dashcam",
                    tint = if (isRec) Color(0xFFFF5252) else voiceTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Botón dedicado de Grabación en la parte izquierda del HUD (debajo del reloj digital):
 *  - Toque corto: Inicia o detiene la grabación de inmediato con cronómetro en vivo (`MM:SS`) y punto `REC` pulsante.
 *  - Pulsación larga: Alterna entre las cámaras disponibles (`DashcamManager.cycleCamera`).
 *  - Se adapta automáticamente al Modo Día y Modo Noche del mapa.
 */
@Composable
private fun CockpitLeftRecordingButton(
    isLandscape: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isRecording = DashcamManager.isRecording.value
    val isDayMap = AppSettings.mapStyle.value == "LIGHT"

    var elapsedSec by remember { mutableStateOf(0) }
    LaunchedEffect(isRecording) {
        if (isRecording) {
            elapsedSec = 0
            while (DashcamManager.isRecording.value) {
                delay(1000L)
                elapsedSec++
            }
        } else {
            elapsedSec = 0
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "leftRecPulse")
    val recDotScale by pulseTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recDotScale"
    )
    val recGlowAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recGlowAlpha"
    )

    // Estilo Dark Frosted Glassmorphism unificado para el botón GRABAR / REC
    val containerColor by animateColorAsState(
        targetValue = if (isRecording) {
            Color(0xFF3A0D18).copy(alpha = 0.94f)
        } else {
            Color(0xFF151F33).copy(alpha = 0.92f)
        },
        animationSpec = tween(220),
        label = "recBtnBg"
    )

    val borderColor = if (isRecording) {
        Color(0xFFFF405C).copy(alpha = recGlowAlpha)
    } else {
        Color.White.copy(alpha = 0.22f)
    }

    val textColor = Color.White

    val timerText = remember(elapsedSec) {
        val mins = elapsedSec / 60
        val secs = elapsedSec % 60
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        border = BorderStroke(if (isRecording) 1.6.dp else 1.2.dp, borderColor),
        shadowElevation = 10.dp,
        modifier = modifier
            .height(if (isLandscape) 38.dp else 36.dp)
            .clip(RoundedCornerShape(50))
            .pointerInput(isRecording) {
                detectTapGestures(
                    onTap = {
                        if (DashcamManager.isRecording.value) {
                            DashcamManager.stopRecording()
                        } else {
                            DashcamManager.startRecording(context, lifecycleOwner)
                        }
                    },
                    onLongPress = {
                        DashcamManager.cycleCamera(context, lifecycleOwner)
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            // Indicador circular rojo de grabación con pulso en vivo
            Box(
                modifier = Modifier.size(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isRecording) (11.dp * recDotScale) else 10.dp)
                        .clip(CircleShape)
                        .background(
                            if (isRecording) Color(0xFFFF1744)
                            else Color(0xFFFF3355)
                        )
                )
            }

            Icon(
                imageVector = if (isRecording) Icons.Default.StopCircle else Icons.Default.Videocam,
                contentDescription = "Activar Grabación",
                tint = if (isRecording) Color(0xFFFF8A9B) else textColor,
                modifier = Modifier.size(17.dp)
            )

            Text(
                text = if (isRecording) "REC $timerText" else "GRABAR",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.6.sp,
                color = textColor
            )
        }
    }
}
