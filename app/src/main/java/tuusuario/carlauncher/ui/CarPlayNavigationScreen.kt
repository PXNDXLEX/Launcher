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
 * Interfaz Cockpit HUD 3D para Modo Navegación / Seguimiento GPS:
 *  - Arriba Izquierda: Reloj Digital Interactivo con 6 modelos y estilos intercambiables (toque o deslizar).
 *  - Abajo Izquierda: Pod circular de Velocímetro con los 18 velocímetros creados (deslizar para cambiar estilo).
 *  - Abajo Derecha: Widget de Música HUD moderno con carátula, barra de progreso y controles circulares luminosos.
 *  - Abajo Centro: Barra Dock redondeada estilo cabina (Home, Velocímetro, Ajustes, Apps, Voz Sara / Dashcam).
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

        // ── 1. ARRIBA IZQUIERDA: RELOJ DIGITAL INTERACTIVO (6 MODELOS Y ESTILOS) ──
        CockpitTopLeftDigitalClock(
            modifier = Modifier
                .align(Alignment.TopStart)
                .widthIn(max = if (isLandscape) 235.dp else 210.dp)
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

        // ── 3. ABAJO DERECHA: WIDGET DE MÚSICA HUD COCKPIT ──
        AnimatedVisibility(
            visible = isSplitMode,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 360f)
            ) + fadeIn(tween(220)) + scaleIn(initialScale = 0.88f),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(200, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(180)) + scaleOut(targetScale = 0.88f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = if (isLandscape) 8.dp else 68.dp)
        ) {
            CockpitBottomRightMusicWidget(
                modifier = Modifier.width(if (isLandscape) 252.dp else 210.dp),
                onOpenYoutube = onOpenYoutube
            )
        }

        // ── 4. ABAJO CENTRO: BARRA DOCK REDONDEADA COCKPIT ──
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
    val activeClockStyle = AppSettings.digitalClockStyle.value.coerceIn(0, 5)
    val styleNames = remember {
        listOf("CYBER NEON", "FLIP RETRO", "GLASS ULTRA", "RACING GT", "MATRIX LED", "AURA HOLO")
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
                else -> {
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
                            // Anillo holográfico de segundos en vivo
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
            }
        }

        // Indicador discreto de los 6 modelos de reloj + nombre del estilo activo
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (i in 0..5) {
                val isSelected = i == activeClockStyle
                Box(
                    modifier = Modifier
                        .width(if (isSelected) 12.dp else 5.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (isSelected) Color(0xFF38F2A6) else Color.White.copy(alpha = 0.35f)
                        )
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = styleNames[activeClockStyle],
                fontSize = 8.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.6.sp,
                color = Color.White.copy(alpha = 0.70f)
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
 * Widget de Música HUD ubicado en la parte inferior derecha (frente al velocímetro izquierdo),
 * integrando carátula, título, artista, barra de progreso interactiva y botones circulares luminosos.
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

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF0A102E).copy(alpha = 0.90f),
        border = BorderStroke(
            1.3.dp,
            Brush.linearGradient(
                listOf(Color(0xFF4C6FFF).copy(alpha = 0.65f), Color(0xFF38F2A6).copy(alpha = 0.45f))
            )
        ),
        shadowElevation = 14.dp
    ) {
        Box {
            // Fondo sutil con la carátula difuminada/oscurecida si existe
            if (artImageBitmap != null) {
                Image(
                    bitmap = artImageBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(24.dp)),
                    contentScale = ContentScale.Crop,
                    alpha = 0.22f
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Fila 1: Mini carátula + Título + Artista (toque abre reproductor expandido)
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
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.40f), RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(9.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color(0xFF1A265A), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MusicNote, null, tint = Color(0xFF38F2A6), modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(9.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = songTitle.ifBlank { "Sin reproducción" },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(shadow = titleShadow)
                        )
                        val subtitleText = if (songArtist.isNotBlank()) songArtist else playerApp
                        Text(
                            text = subtitleText.ifBlank { "Reproductor Multimedia" },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFB8CEFF),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(shadow = titleShadow)
                        )
                    }
                }

                // Barra de progreso interactiva
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.22f))
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

                // Fila 2: Botones circulares con aro luminoso (|<, Play/Pause, >|, BT, YouTube)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
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

                    Surface(
                        onClick = onOpenYoutube,
                        shape = CircleShape,
                        color = Color(0xFF1A2A5E).copy(alpha = 0.78f),
                        border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.70f)),
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
        Color(0xFF162458).copy(alpha = 0.92f)
    } else {
        Color(0xFF1D3570).copy(alpha = 0.70f)
    }
    val borderColor = if (isPrimary) {
        Color(0xFF38F2A6).copy(alpha = 0.90f)
    } else {
        Color.White.copy(alpha = 0.55f)
    }

    Surface(
        shape = CircleShape,
        color = bgColor,
        border = BorderStroke(if (isPrimary) 1.6.dp else 1.2.dp, borderColor),
        shadowElevation = if (isPrimary) 8.dp else 4.dp,
        modifier = Modifier
            .size(if (isPrimary) 42.dp else 38.dp)
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
                modifier = Modifier.size(if (isPrimary) 22.dp else 19.dp)
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
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = this.size.minDimension / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)

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
