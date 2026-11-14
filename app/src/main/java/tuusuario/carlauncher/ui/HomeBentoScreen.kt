package com.tuusuario.carlauncher.ui

import android.graphics.BitmapFactory
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tuusuario.carlauncher.R
import com.tuusuario.carlauncher.services.ArletAssistant
import com.tuusuario.carlauncher.services.DashcamManager
import com.tuusuario.carlauncher.services.GlobalState
import com.tuusuario.carlauncher.services.SpeedRecordTracker
import com.tuusuario.carlauncher.services.WeatherService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Menú Principal inspirado directamente en las dos imágenes de referencia (Modo Día y Modo Noche):
 *  - Fondo con ondas fluidas diagonales (lavanda/azul cielo de día, índigo/azul marino profundo de noche).
 *  - Cabecera superior con saludo dinámico ("Buenos días, Lorena" / "Buenas noches, Lorena"), fecha en español,
 *    hora en formato 12h (a. m. / p. m.), acceso rápido a Voz Sara, Tema Día/Noche y botón de Ajustes.
 *  - Columna izquierda: Tarjeta "Google Maps / Tu ubicación" con el mapa en miniatura idéntico a la imagen
 *    (calles, Parque, Av. del Parque, Paseo Central, río, ruta azul con flecha de origen, pin de destino y botón recentrar)
 *    y botón inferior "Abrir mapa ↗".
 *  - Área derecha superior: Vehículo flotante sobre el fondo con sombra de neumáticos y pastilla "Mi [Modelo]".
 *  - Área derecha inferior: Reproductor de música estilo Spotify con controles circulares a la derecha + Tarjeta de Clima
 *    con ilustración de sol/nube de día y luna/estrellas de noche y Máx./Mín.
 *  - Dock inferior centrado con los 6 botones squircle adaptados a las opciones reales del Launcher:
 *    Apps, Mapas, Velocímetro, Dashcam, Rutas y Récords.
 */
@Composable
fun HomeBentoScreen(
    isLandscape: Boolean,
    onOpenNavigation: () -> Unit,
    onOpenSpeedometer: () -> Unit,
    onOpenDashcamGallery: () -> Unit,
    onOpenRoutes: () -> Unit,
    onOpenRecords: () -> Unit,
    onOpenAllApps: () -> Unit,
    onOpenYoutube: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleTheme: () -> Unit
) {
    val palette = LocalBentoPalette.current
    val isDark = palette.isDark

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. FONDO DE ONDAS FLUIDAS DÍA / NOCHE IDÉNTICO A LAS IMÁGENES DE REFERENCIA
        ReferenceWaveBackground(
            isDark = isDark,
            modifier = Modifier.fillMaxSize()
        )

        if (isLandscape) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // ── CABECERA SUPERIOR: Saludo + Fecha a la izquierda | Hora + Voz + Tema + Ajustes a la derecha ──
                ReferenceTopHeader(
                    isDark = isDark,
                    onOpenSettings = onOpenSettings,
                    onToggleTheme = onToggleTheme,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ── ZONA CENTRAL: Tarjeta Mapa en Miniatura (Izquierda) + Auto Flotante / Música / Clima (Derecha) ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Izquierda (~33%): Tarjeta Google Maps con mapa en miniatura y botón "Abrir mapa ↗"
                    ReferenceMiniMapCard(
                        isDark = isDark,
                        onOpenNavigation = onOpenNavigation,
                        modifier = Modifier
                            .weight(0.33f)
                            .fillMaxHeight()
                    )

                    // Derecha (~67%): Auto protagonista arriba + (Reproductor Música + Tarjeta Clima) abajo
                    Column(
                        modifier = Modifier
                            .weight(0.67f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Mitad superior: Auto flotando sobre las ondas + pastilla "Mi [Modelo]" abajo a la izquierda
                        ReferenceFloatingCarSection(
                            isDark = isDark,
                            onOpenSpeedometer = onOpenSpeedometer,
                            onOpenSettings = onOpenSettings,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.56f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Mitad inferior: Reproductor de Música (izquierda) + Clima (derecha)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.44f),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            ReferenceMusicPlayerCard(
                                isDark = isDark,
                                onOpenYoutube = onOpenYoutube,
                                modifier = Modifier
                                    .weight(0.67f)
                                    .fillMaxHeight()
                            )

                            ReferenceWeatherCard(
                                isDark = isDark,
                                modifier = Modifier
                                    .weight(0.33f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ── DOCK INFERIOR CENTRADO CON LAS 6 OPCIONES DEL LAUNCHER ──
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    ReferenceBottomDock(
                        isDark = isDark,
                        onOpenAllApps = onOpenAllApps,
                        onOpenNavigation = onOpenNavigation,
                        onOpenSpeedometer = onOpenSpeedometer,
                        onOpenDashcamGallery = onOpenDashcamGallery,
                        onOpenRoutes = onOpenRoutes,
                        onOpenRecords = onOpenRecords
                    )
                }
            }
        } else {
            // ── MODO VERTICAL ADAPTABLE CON EL MISMO DISEÑO DE LA REFERENCIA ──
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ReferenceTopHeader(
                    isDark = isDark,
                    onOpenSettings = onOpenSettings,
                    onToggleTheme = onToggleTheme,
                    modifier = Modifier.fillMaxWidth()
                )

                ReferenceFloatingCarSection(
                    isDark = isDark,
                    onOpenSpeedometer = onOpenSpeedometer,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.28f)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.36f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ReferenceMiniMapCard(
                        isDark = isDark,
                        onOpenNavigation = onOpenNavigation,
                        modifier = Modifier
                            .weight(0.54f)
                            .fillMaxHeight()
                    )

                    ReferenceWeatherCard(
                        isDark = isDark,
                        modifier = Modifier
                            .weight(0.46f)
                            .fillMaxHeight()
                    )
                }

                ReferenceMusicPlayerCard(
                    isDark = isDark,
                    onOpenYoutube = onOpenYoutube,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.22f)
                )

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    ReferenceBottomDock(
                        isDark = isDark,
                        onOpenAllApps = onOpenAllApps,
                        onOpenNavigation = onOpenNavigation,
                        onOpenSpeedometer = onOpenSpeedometer,
                        onOpenDashcamGallery = onOpenDashcamGallery,
                        onOpenRoutes = onOpenRoutes,
                        onOpenRecords = onOpenRecords
                    )
                }
            }
        }
    }
}

/**
 * Fondo artístico con ondas diagonales suaves idéntico a las imágenes de referencia (Día y Noche).
 */
@Composable
private fun ReferenceWaveBackground(
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val animSpec = tween<Color>(durationMillis = 650, easing = FastOutSlowInEasing)

    val bgTopLeft by animateColorAsState(
        targetValue = if (isDark) Color(0xFF060920) else Color(0xFFD5DDF7),
        animationSpec = animSpec, label = "bgTL"
    )
    val bgMid by animateColorAsState(
        targetValue = if (isDark) Color(0xFF0E1A4C) else Color(0xFFC0D1FA),
        animationSpec = animSpec, label = "bgMid"
    )
    val bgBottomRight by animateColorAsState(
        targetValue = if (isDark) Color(0xFF0F356B) else Color(0xFF428BF9),
        animationSpec = animSpec, label = "bgBR"
    )

    val wave1Start by animateColorAsState(
        targetValue = if (isDark) Color(0xFF122361) else Color(0xFFAEC4FA),
        animationSpec = animSpec, label = "w1S"
    )
    val wave1End by animateColorAsState(
        targetValue = if (isDark) Color(0xFF1B4B94) else Color(0xFF5D9BFF),
        animationSpec = animSpec, label = "w1E"
    )

    val wave2Start by animateColorAsState(
        targetValue = if (isDark) Color(0xFF0D2159) else Color(0xFF75A9FF),
        animationSpec = animSpec, label = "w2S"
    )
    val wave2End by animateColorAsState(
        targetValue = if (isDark) Color(0xFF13688A) else Color(0xFF1CB5E8),
        animationSpec = animSpec, label = "w2E"
    )

    val crestHighlight by animateColorAsState(
        targetValue = if (isDark) Color(0xFF5396F5).copy(alpha = 0.32f) else Color.White.copy(alpha = 0.52f),
        animationSpec = animSpec, label = "crest"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Capa base: degradado diagonal completo
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(bgTopLeft, bgMid, bgBottomRight),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
        )

        // Onda diagonal 1 (barre desde abajo-izquierda hacia arriba-derecha como en el wallpaper de referencia)
        val wavePath1 = Path().apply {
            moveTo(0f, h * 0.92f)
            cubicTo(
                w * 0.32f, h * 0.68f,
                w * 0.68f, h * 0.42f,
                w, h * 0.04f
            )
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = wavePath1,
            brush = Brush.linearGradient(
                colors = listOf(wave1Start.copy(alpha = 0.78f), wave1End.copy(alpha = 0.88f)),
                start = Offset(0f, h * 0.85f),
                end = Offset(w, h * 0.10f)
            )
        )

        // Línea luminosa sobre la cresta de la Onda 1
        val crestPath1 = Path().apply {
            moveTo(0f, h * 0.92f)
            cubicTo(
                w * 0.32f, h * 0.68f,
                w * 0.68f, h * 0.42f,
                w, h * 0.04f
            )
        }
        drawPath(
            path = crestPath1,
            color = crestHighlight,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        // Onda diagonal 2 (curva inferior derecha más intensa en cian/azul real)
        val wavePath2 = Path().apply {
            moveTo(w * 0.08f, h)
            cubicTo(
                w * 0.42f, h * 0.80f,
                w * 0.76f, h * 0.62f,
                w, h * 0.42f
            )
            lineTo(w, h)
            close()
        }
        drawPath(
            path = wavePath2,
            brush = Brush.linearGradient(
                colors = listOf(wave2Start.copy(alpha = 0.85f), wave2End.copy(alpha = 0.92f)),
                start = Offset(w * 0.15f, h),
                end = Offset(w, h * 0.45f)
            )
        )

        // Línea luminosa suave sobre la cresta de la Onda 2
        val crestPath2 = Path().apply {
            moveTo(w * 0.08f, h)
            cubicTo(
                w * 0.42f, h * 0.80f,
                w * 0.76f, h * 0.62f,
                w, h * 0.42f
            )
        }
        drawPath(
            path = crestPath2,
            color = crestHighlight.copy(alpha = if (isDark) 0.25f else 0.45f),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * Cabecera superior idéntica a la referencia:
 *  - Izquierda: "Buenos días, Lorena" / "Buenas noches, Lorena" + "Jueves, 12 de noviembre"
 *  - Derecha: "10:24 a. m." / "9:40 p. m." + Botón Voz Sara + Botón Tema Día/Noche + Botón Ajustes (engranaje)
 */
@Composable
private fun ReferenceTopHeader(
    isDark: Boolean,
    onOpenSettings: () -> Unit,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val userName = AppSettings.userName.value.trim()
    var time12Str by remember { mutableStateOf("") }
    var dateSpanishStr by remember { mutableStateOf("") }
    var currentHour by remember { mutableStateOf(10) }

    LaunchedEffect(Unit) {
        val localeEs = Locale("es", "ES")
        val dayFmt = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", localeEs)
        while (true) {
            val now = LocalDateTime.now()
            currentHour = now.hour
            val hour12 = when {
                now.hour == 0 -> 12
                now.hour > 12 -> now.hour - 12
                else -> now.hour
            }
            val amPm = if (now.hour < 12) "a. m." else "p. m."
            time12Str = "%d:%02d %s".format(hour12, now.minute, amPm)

            val rawDate = now.format(dayFmt)
            dateSpanishStr = rawDate.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(localeEs) else it.toString()
            }

            val msToNextSecond = 1000L - (System.currentTimeMillis() % 1000L)
            delay(msToNextSecond.coerceIn(250L, 1000L))
        }
    }

    val greetingPrefix = remember(isDark, currentHour) {
        if (isDark) {
            "Buenas noches"
        } else {
            if (currentHour in 12..18) "Buenas tardes" else "Buenos días"
        }
    }
    val fullGreeting = if (userName.isNotEmpty()) "$greetingPrefix, $userName" else greetingPrefix

    val titleColor = if (isDark) Color.White else Color(0xFF141B2D)
    val subtitleColor = if (isDark) Color(0xFFC8D4EC) else Color(0xFF3E4C66)
    val btnBg = if (isDark) Color(0xFF232F5B).copy(alpha = 0.78f) else Color(0xFFF3F7FE).copy(alpha = 0.92f)
    val btnBorder = if (isDark) Color(0xFF4E63A6).copy(alpha = 0.55f) else Color.White

    val isListening = ArletAssistant.isListening.value || ArletAssistant.isWakeWordActiveWindow.value
    val isSpeaking = ArletAssistant.isSpeaking.value

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Saludo grande y fecha debajo
        Column {
            Text(
                text = fullGreeting,
                fontSize = 28.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = dateSpanishStr.ifEmpty { "Jueves, 12 de noviembre" },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = subtitleColor
            )
        }

        // Reloj 12h + Controles rápidos (Voz Sara, Día/Noche y Engranaje Ajustes)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = time12Str.ifEmpty { "10:24 a. m." },
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = titleColor,
                style = TextStyle(fontFeatureSettings = "tnum")
            )

            // Botón Asistente de Voz Sara
            Surface(
                onClick = {
                    if (isListening || isSpeaking) ArletAssistant.closeOverlay()
                    else ArletAssistant.triggerManualListening()
                },
                shape = RoundedCornerShape(13.dp),
                color = when {
                    isListening -> Color(0xFFFF007F)
                    isSpeaking -> Color(0xFF00B8D4)
                    else -> btnBg
                },
                border = BorderStroke(1.dp, if (isListening || isSpeaking) Color.Transparent else btnBorder),
                shadowElevation = if (isDark) 0.dp else 3.dp,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Asistente Sara",
                        tint = if (isListening || isSpeaking) Color.White else titleColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Botón rápido Modo Día / Noche
            Surface(
                onClick = onToggleTheme,
                shape = RoundedCornerShape(13.dp),
                color = btnBg,
                border = BorderStroke(1.dp, btnBorder),
                shadowElevation = if (isDark) 0.dp else 3.dp,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Cambiar Día/Noche",
                        tint = titleColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Botón de Ajustes (idéntico al de la esquina superior derecha de las imágenes de referencia)
            Surface(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(13.dp),
                color = btnBg,
                border = BorderStroke(1.dp, btnBorder),
                shadowElevation = if (isDark) 0.dp else 3.dp,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Configuración",
                        tint = titleColor,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
    }
}

/**
 * Tarjeta izquierda con cabecera "Google Maps / Tu ubicación", el mapa en miniatura idéntico a la imagen
 * (con soporte Día y Noche) y el botón inferior "Abrir mapa ↗".
 */
@Composable
private fun ReferenceMiniMapCard(
    isDark: Boolean,
    onOpenNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRouteActive = NavigationState.isRouteActive.value
    val routeDist = NavigationState.activeRouteDistance.value

    val cardBg = if (isDark) Color(0xFF131B3C).copy(alpha = 0.86f) else Color(0xFFE4ECFA).copy(alpha = 0.90f)
    val cardBorder = if (isDark) Color(0xFF2E3F78).copy(alpha = 0.72f) else Color.White.copy(alpha = 0.85f)
    val titleColor = if (isDark) Color.White else Color(0xFF141B2D)
    val subtitleColor = if (isDark) Color(0xFFAAB8D8) else Color(0xFF475569)

    val pinBoxBg = if (isDark) Color(0xFF1C2854) else Color(0xFFD6E2F9)
    val pinBoxBorder = if (isDark) Color(0xFF394F94) else Color.White.copy(alpha = 0.7f)
    val pinIconTint = if (isDark) Color(0xFF8AB4F8) else Color(0xFF2B5CE6)

    val btnBrush = if (isDark) {
        Brush.horizontalGradient(listOf(Color(0xFF9BB8F8), Color(0xFF87A9F4)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF3357D1), Color(0xFF4268E6)))
    }
    val btnTextColor = if (isDark) Color(0xFF0E162E) else Color.White

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        shadowElevation = if (isDark) 0.dp else 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Cabecera de la tarjeta: Icono Pin + "Google Maps" / "Tu ubicación"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(pinBoxBg)
                        .border(1.dp, pinBoxBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = pinIconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = "Google Maps",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = titleColor,
                        maxLines = 1
                    )
                    Text(
                        text = if (isRouteActive && routeDist.isNotBlank()) {
                            "En ruta · $routeDist"
                        } else {
                            "Tu ubicación"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = subtitleColor,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Mapa en miniatura idéntico a la imagen (Modo Día y Modo Noche)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        width = 1.dp,
                        color = if (isDark) Color(0xFF283766) else Color.White.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable(onClick = onOpenNavigation)
            ) {
                StylizedReferenceMiniMap(
                    isDark = isDark,
                    modifier = Modifier.fillMaxSize()
                )

                // Botón recentrar (esquina inferior derecha del mapa en miniatura)
                Surface(
                    onClick = onOpenNavigation,
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDark) Color(0xFF1F2B44).copy(alpha = 0.94f) else Color(0xFFF8FAFC).copy(alpha = 0.95f),
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Centrar ubicación",
                            tint = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Botón inferior "Abrir mapa ↗"
            Surface(
                onClick = onOpenNavigation,
                shape = RoundedCornerShape(14.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(btnBrush, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Abrir mapa",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = btnTextColor
                    )
                    // Flecha diagonal ↗ en el extremo derecho igual a la imagen
                    Canvas(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(12.dp)
                    ) {
                        val s = size.width
                        val strokeW = 1.8.dp.toPx()
                        drawLine(
                            color = btnTextColor,
                            start = Offset(s * 0.18f, s * 0.82f),
                            end = Offset(s * 0.82f, s * 0.18f),
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = btnTextColor,
                            start = Offset(s * 0.35f, s * 0.18f),
                            end = Offset(s * 0.82f, s * 0.18f),
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = btnTextColor,
                            start = Offset(s * 0.82f, s * 0.18f),
                            end = Offset(s * 0.82f, s * 0.65f),
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dibuja el mapa en miniatura con la geometría exacta de las imágenes de referencia:
 *  - Cuadrícula urbana en ángulo, avenidas principales curvas, zona verde "Parque" arriba a la derecha,
 *    río en el borde derecho, etiquetas "Av. del Parque", "Parque" y "Paseo Central",
 *    trazado de ruta en S con flecha de navegación en el origen y pin circular en el destino.
 */
@Composable
private fun StylizedReferenceMiniMap(
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val landColor = if (isDark) Color(0xFF131D31) else Color(0xFFE3E8F0)
    val blockHighlight = if (isDark) Color(0xFF17233A) else Color(0xFFECEFF5)
    val parkColor = if (isDark) Color(0xFF16383A) else Color(0xFFC5E8C8)
    val waterColor = if (isDark) Color(0xFF0A2246) else Color(0xFF9AC3F8)
    val waterEdgeColor = if (isDark) Color(0xFF173768) else Color(0xFFB7D4FA)
    val minorStreetColor = if (isDark) Color(0xFF23324D) else Color(0xFFFFFFFF)
    val majorRoadColor = if (isDark) Color(0xFF2E4163) else Color(0xFFFFFFFF)
    val roadCasingColor = if (isDark) Color(0xFF0E1626) else Color(0xFFD0D8E4)

    val routeMainColor = if (isDark) Color(0xFF8AB4F8) else Color(0xFF2A5BE6)
    val routeCasingColor = if (isDark) Color(0xFF3B69B8) else Color(0xFF183CA3)

    val labelColorInt = if (isDark) Color(0xFFB8C7E0).toArgb() else Color(0xFF2D3A4F).toArgb()
    val parkLabelColorInt = if (isDark) Color(0xFF8CC4C2).toArgb() else Color(0xFF2F5E41).toArgb()
    val labelHaloColorInt = if (isDark) Color(0xFF0F172A).toArgb() else Color(0xFFFFFFFF).toArgb()

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 1. Fondo base de manzanas urbanas
        drawRect(color = landColor)

        // Polígono sutil para variación de manzanas centrales
        val blockPoly = Path().apply {
            moveTo(w * 0.12f, h * 0.18f)
            lineTo(w * 0.65f, h * 0.14f)
            lineTo(w * 0.58f, h * 0.82f)
            lineTo(w * 0.08f, h * 0.76f)
            close()
        }
        drawPath(blockPoly, color = blockHighlight)

        // 2. Zona Verde "Parque" arriba a la derecha (como en la imagen de referencia)
        val parkPath = Path().apply {
            moveTo(w * 0.66f, 0f)
            lineTo(w * 0.90f, 0f)
            lineTo(w * 0.82f, h * 0.34f)
            lineTo(w * 0.61f, h * 0.30f)
            close()
        }
        drawPath(parkPath, color = parkColor)

        // 3. Río / Canal de agua en el borde derecho
        val waterEdgePath = Path().apply {
            moveTo(w * 0.94f, 0f)
            cubicTo(
                w * 0.90f, h * 0.35f,
                w * 0.85f, h * 0.68f,
                w * 0.78f, h
            )
            lineTo(w, h)
            lineTo(w, 0f)
            close()
        }
        drawPath(waterEdgePath, color = waterEdgeColor)

        val waterPath = Path().apply {
            moveTo(w * 0.96f, 0f)
            cubicTo(
                w * 0.92f, h * 0.36f,
                w * 0.87f, h * 0.69f,
                w * 0.80f, h
            )
            lineTo(w, h)
            lineTo(w, 0f)
            close()
        }
        drawPath(waterPath, color = waterColor)

        // 4. Cuadrícula de calles secundarias
        val minorStroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Butt)
        // Calles horizontales/inclinadas
        val hLines = listOf(
            Offset(0f, h * 0.14f) to Offset(w * 0.92f, h * 0.20f),
            Offset(0f, h * 0.28f) to Offset(w * 0.90f, h * 0.35f),
            Offset(0f, h * 0.44f) to Offset(w * 0.88f, h * 0.52f),
            Offset(0f, h * 0.61f) to Offset(w * 0.84f, h * 0.68f),
            Offset(0f, h * 0.77f) to Offset(w * 0.82f, h * 0.83f),
            Offset(0f, h * 0.91f) to Offset(w * 0.78f, h * 0.96f)
        )
        hLines.forEach { (start, end) ->
            drawLine(color = minorStreetColor, start = start, end = end, strokeWidth = minorStroke.width)
        }

        // Calles verticales/inclinadas
        val vLines = listOf(
            Offset(w * 0.16f, 0f) to Offset(w * 0.04f, h),
            Offset(w * 0.33f, 0f) to Offset(w * 0.18f, h),
            Offset(w * 0.49f, 0f) to Offset(w * 0.34f, h),
            Offset(w * 0.66f, 0f) to Offset(w * 0.51f, h),
            Offset(w * 0.89f, 0f) to Offset(w * 0.72f, h)
        )
        vLines.forEach { (start, end) ->
            drawLine(color = minorStreetColor, start = start, end = end, strokeWidth = minorStroke.width)
        }

        // 5. Avenidas principales curvas
        val ave1 = Path().apply {
            moveTo(0f, h * 0.64f)
            cubicTo(
                w * 0.28f, h * 0.42f,
                w * 0.52f, h * 0.56f,
                w * 0.86f, h * 0.62f
            )
        }
        val ave2 = Path().apply {
            moveTo(w * 0.83f, 0f)
            cubicTo(
                w * 0.77f, h * 0.35f,
                w * 0.68f, h * 0.68f,
                w * 0.62f, h
            )
        }
        val ave3 = Path().apply {
            moveTo(w * 0.10f, h)
            cubicTo(
                w * 0.32f, h * 0.74f,
                w * 0.50f, h * 0.66f,
                w * 0.82f, h * 0.84f
            )
        }

        val majorCasingStroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val majorFillStroke = Stroke(width = 4.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

        listOf(ave1, ave2, ave3).forEach { path ->
            drawPath(path, color = roadCasingColor, style = majorCasingStroke)
            drawPath(path, color = majorRoadColor, style = majorFillStroke)
        }

        // 6. Trazado de Ruta Azul (idéntica a la curva en S de la imagen de referencia)
        val orig = Offset(w * 0.25f, h * 0.76f)
        val dest = Offset(w * 0.75f, h * 0.33f)

        val routePath = Path().apply {
            moveTo(orig.x, orig.y)
            lineTo(w * 0.34f, h * 0.77f)
            cubicTo(
                w * 0.38f, h * 0.77f,
                w * 0.40f, h * 0.62f,
                w * 0.43f, h * 0.56f
            )
            cubicTo(
                w * 0.46f, h * 0.51f,
                w * 0.64f, h * 0.57f,
                w * 0.69f, h * 0.51f
            )
            cubicTo(
                w * 0.72f, h * 0.47f,
                w * 0.73f, h * 0.40f,
                dest.x, dest.y
            )
        }

        drawPath(
            path = routePath,
            color = routeCasingColor,
            style = Stroke(width = 7.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = routePath,
            color = routeMainColor,
            style = Stroke(width = 4.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // 7. Pin de Destino (arriba a la derecha de la ruta)
        drawCircle(
            color = Color.White,
            radius = 7.5.dp.toPx(),
            center = dest
        )
        drawCircle(
            color = routeMainColor,
            radius = 5.8.dp.toPx(),
            center = dest
        )
        drawCircle(
            color = if (isDark) Color(0xFF0F172A) else Color.White,
            radius = 2.4.dp.toPx(),
            center = dest
        )

        // 8. Puck de Origen con Flecha de Navegación (abajo a la izquierda de la ruta)
        drawCircle(
            color = routeMainColor.copy(alpha = 0.26f),
            radius = 15.dp.toPx(),
            center = orig
        )
        drawCircle(
            color = Color.White,
            radius = 9.5.dp.toPx(),
            center = orig
        )
        drawCircle(
            color = if (isDark) Color(0xFF1E293B) else Color(0xFF2A5BE6),
            radius = 7.5.dp.toPx(),
            center = orig
        )

        // Flecha triangular blanca/azul claro dentro del puck de origen
        rotate(degrees = 15f, pivot = orig) {
            val r = 4.2.dp.toPx()
            val arrowPath = Path().apply {
                moveTo(orig.x, orig.y - r)
                lineTo(orig.x + r * 0.78f, orig.y + r * 0.85f)
                lineTo(orig.x, orig.y + r * 0.35f)
                lineTo(orig.x - r * 0.78f, orig.y + r * 0.85f)
                close()
            }
            drawPath(arrowPath, color = if (isDark) Color(0xFF9BB8F8) else Color.White)
        }

        // 9. Etiquetas de calles del mapa ("Parque", "Av. del Parque", "Paseo Central")
        val textPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            textAlign = AndroidPaint.Align.CENTER
            textSize = 9.5.dp.toPx()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val haloPaint = AndroidPaint(textPaint).apply {
            style = AndroidPaint.Style.STROKE
            strokeWidth = 2.5.dp.toPx()
            color = labelHaloColorInt
        }

        fun drawMapLabel(text: String, x: Float, y: Float, colorInt: Int) {
            textPaint.color = colorInt
            drawContext.canvas.nativeCanvas.drawText(text, x, y, haloPaint)
            drawContext.canvas.nativeCanvas.drawText(text, x, y, textPaint)
        }

        drawMapLabel("Parque", w * 0.73f, h * 0.14f, parkLabelColorInt)
        drawMapLabel("Av. del Parque", w * 0.26f, h * 0.39f, labelColorInt)
        drawMapLabel("Paseo Central", w * 0.54f, h * 0.86f, labelColorInt)
    }
}

/**
 * Sección superior derecha: Vehículo flotante sobre el fondo de ondas (sin caja alrededor)
 * con sombra realista bajo las ruedas y pastilla "Mi [Modelo]" en la esquina inferior izquierda.
 */
@Composable
private fun ReferenceFloatingCarSection(
    isDark: Boolean,
    onOpenSpeedometer: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val customCarPath = AppSettings.customHomeCarPhotoPath.value
    val carScale = AppSettings.homeCarScale.value
    val modelName = AppSettings.carModelName.value
    val speedKmH = NavigationState.currentSpeedKmH.value
    val smoothSpeed by animateFloatAsState(
        targetValue = speedKmH,
        animationSpec = tween(durationMillis = 120, easing = LinearEasing),
        label = "carPillSpeed"
    )

    var carBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(customCarPath) {
        carBitmap = withContext(Dispatchers.IO) {
            try {
                val rawBmp = if (customCarPath.isNotBlank()) {
                    val f = File(customCarPath)
                    if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
                } else {
                    BitmapFactory.decodeResource(context.resources, R.drawable.car_hero_kia_rio)
                }
                rawBmp?.let { trimTransparentBounds(it).asImageBitmap() }
            } catch (_: Exception) {
                null
            }
        }
    }

    val pillBg = if (isDark) Color(0xFF1A2550).copy(alpha = 0.85f) else Color(0xFFE3ECFA).copy(alpha = 0.92f)
    val pillBorder = if (isDark) Color(0xFF3B4F8C).copy(alpha = 0.75f) else Color.White.copy(alpha = 0.90f)
    val pillText = if (isDark) Color.White else Color(0xFF141B2D)

    Box(modifier = modifier) {
        // Contenedor del vehículo desplazado ligeramente a la derecha como en la imagen de referencia
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.86f)
                .align(Alignment.CenterEnd)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenSpeedometer
                ),
            contentAlignment = Alignment.Center
        ) {
            // Sombra elíptica realista bajo los neumáticos del vehículo
            Canvas(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .height(34.dp)
                    .align(Alignment.BottomCenter)
                    .offset(y = (-2).dp)
            ) {
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = if (isDark) 0.62f else 0.32f),
                            Color.Black.copy(alpha = if (isDark) 0.24f else 0.10f),
                            Color.Transparent
                        ),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.width / 2f
                    ),
                    size = Size(size.width, size.height)
                )
            }

            if (carBitmap != null) {
                Image(
                    bitmap = carBitmap!!,
                    contentDescription = "Vehículo",
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .fillMaxHeight(0.96f)
                        .graphicsLayer {
                            scaleX = carScale
                            scaleY = carScale
                        },
                    contentScale = ContentScale.Fit
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.car_hero_kia_rio),
                    contentDescription = "Vehículo",
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .fillMaxHeight(0.96f)
                        .graphicsLayer {
                            scaleX = carScale
                            scaleY = carScale
                        },
                    contentScale = ContentScale.Fit
                )
            }
        }

        // Pastilla "Mi [Modelo]" en la esquina inferior izquierda junto al auto (idéntica a "Mi RAV4" en la imagen)
        Surface(
            onClick = {
                if (smoothSpeed >= 3f) onOpenSpeedometer() else onOpenSettings()
            },
            shape = RoundedCornerShape(14.dp),
            color = pillBg,
            border = BorderStroke(1.dp, pillBorder),
            shadowElevation = if (isDark) 0.dp else 4.dp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val badgeLabel = if (smoothSpeed >= 2f) {
                    "${smoothSpeed.roundToInt()} km/h · $modelName"
                } else {
                    val cleanModel = modelName.trim()
                    if (cleanModel.startsWith("Mi ", ignoreCase = true)) cleanModel else "Mi $cleanModel"
                }
                Text(
                    text = badgeLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = pillText,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Tarjeta de Reproductor de Música estilo Spotify de la imagen de referencia:
 *  - Izquierda: Carátula cuadrada redondeada (con arte de atardecer estilo "Midnight City" cuando no hay carátula).
 *  - Centro: Badge verde Spotify / App reproductora + Título de la canción + Artista.
 *  - Derecha (arriba de la barra de progreso): 3 botones circulares (Anterior, Play/Pause destacado, Siguiente).
 *  - Abajo: Barra de progreso interactiva + tiempos a los extremos (1:23 ... 4:04).
 */
@Composable
private fun ReferenceMusicPlayerCard(
    isDark: Boolean,
    onOpenYoutube: (() -> Unit)?,
    modifier: Modifier = Modifier
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
    } else 0.34f

    val cardBrush = if (isDark) {
        Brush.horizontalGradient(
            listOf(
                Color(0xFF162048).copy(alpha = 0.86f),
                Color(0xFF1A295C).copy(alpha = 0.86f)
            )
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                Color(0xFFE3ECFC).copy(alpha = 0.90f),
                Color(0xFFC9DBFA).copy(alpha = 0.88f)
            )
        )
    }
    val cardBorder = if (isDark) Color(0xFF334982).copy(alpha = 0.72f) else Color.White.copy(alpha = 0.85f)
    val titleColor = if (isDark) Color.White else Color(0xFF141B2D)
    val subtitleColor = if (isDark) Color(0xFFB8C7E6) else Color(0xFF3E4C66)

    val playCircleBg = if (isDark) Color(0xFF9BB8F8) else Color(0xFF3A5AD9)
    val playIconTint = if (isDark) Color(0xFF0F172A) else Color.White
    val sideCircleBg = if (isDark) Color(0xFF25356B).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.65f)
    val sideIconTint = if (isDark) Color.White else Color(0xFF141B2D)

    val trackBg = if (isDark) Color(0xFF33447A) else Color(0xFF9AB4E8).copy(alpha = 0.55f)
    val trackFill = if (isDark) Color(0xFF9BB8F8) else Color(0xFF3A5AD9)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, cardBorder),
        shadowElevation = if (isDark) 0.dp else 5.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(cardBrush)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Fila Superior: Carátula + Info Canción + Controles Circulares a la derecha
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Carátula cuadrada redondeada (con arte sunset si no hay carátula cargada)
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .shadow(4.dp, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showExpandedModal = true }
                ) {
                    if (artImageBitmap != null) {
                        Image(
                            bitmap = artImageBitmap,
                            contentDescription = "Carátula",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        SunsetAlbumArtPlaceholder(modifier = Modifier.fillMaxSize())
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Columna central: Badge Spotify/App + Título + Artista
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showExpandedModal = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // Icono circular verde estilo Spotify
                        Box(
                            modifier = Modifier
                                .size(13.dp)
                                .background(Color(0xFF1ED760), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(9.dp)
                            )
                        }
                        Text(
                            text = playerApp.ifBlank { "Spotify" },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = subtitleColor,
                            maxLines = 1
                        )

                        if (onOpenYoutube != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                onClick = onOpenYoutube,
                                shape = RoundedCornerShape(8.dp),
                                color = if (isDark) Color(0xFF28386E) else Color.White.copy(alpha = 0.7f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayCircleFilled,
                                        contentDescription = "YouTube",
                                        tint = Color(0xFFE53935),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "YT",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = titleColor
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = songTitle.ifBlank { "Midnight City" },
                        fontSize = 17.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = songArtist.ifBlank { "M83" },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 3 Controles Circulares a la derecha (Anterior, Play/Pausa, Siguiente)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        onClick = { GlobalState.skipToPrevious() },
                        shape = CircleShape,
                        color = sideCircleBg,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Anterior",
                                tint = sideIconTint,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = { GlobalState.togglePlayPause() },
                        shape = CircleShape,
                        color = playCircleBg,
                        shadowElevation = 4.dp,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Reproducir / Pausar",
                                tint = playIconTint,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = { GlobalState.skipToNext() },
                        shape = CircleShape,
                        color = sideCircleBg,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Siguiente",
                                tint = sideIconTint,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }

            // Fila Inferior: Barra de progreso interactiva + tiempos en los extremos
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                            .background(trackBg)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.coerceIn(0.03f, 1f))
                                .clip(RoundedCornerShape(50))
                                .background(trackFill)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (duration > 0L) formatDurationMs(position) else "1:23",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = subtitleColor,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                    Text(
                        text = if (duration > 0L) formatDurationMs(duration) else "4:04",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = subtitleColor,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                }
            }
        }
    }

    if (showExpandedModal) {
        ExpandedMusicModal(onDismiss = { showExpandedModal = false })
    }
}

/**
 * Ilustración de carátula synthwave (sol de atardecer + montañas) idéntica a la carátula de "Midnight City"
 * de la imagen de referencia cuando no hay carátula activa.
 */
@Composable
private fun SunsetAlbumArtPlaceholder(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Cielo degradado violeta/rosa atardecer
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF2D1B4E),
                    Color(0xFF7B3B6B),
                    Color(0xFFD97B73)
                )
            )
        )

        // Sol cálido en el centro
        drawCircle(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFE599), Color(0xFFFF9E7D))
            ),
            radius = w * 0.28f,
            center = Offset(w * 0.5f, h * 0.50f)
        )

        // Silueta de montañas en la base
        val mountain1 = Path().apply {
            moveTo(0f, h * 0.72f)
            lineTo(w * 0.34f, h * 0.50f)
            lineTo(w * 0.65f, h * 0.70f)
            lineTo(w, h * 0.52f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(mountain1, color = Color(0xFF1F1235))

        val mountain2 = Path().apply {
            moveTo(w * 0.22f, h)
            lineTo(w * 0.62f, h * 0.58f)
            lineTo(w, h * 0.78f)
            lineTo(w, h)
            close()
        }
        drawPath(mountain2, color = Color(0xFF120924))
    }
}

/**
 * Tarjeta de Clima de la esquina inferior derecha idéntica a las dos imágenes de referencia:
 *  - Arriba: "Clima"
 *  - Centro: Temperatura grande ("22°" / "15°") a la izquierda + Ilustración vectorial (Sol con nube de día, Luna con estrellas de noche)
 *  - Abajo: Condición ("Parcialmente nublado" / "Despejado") + "Máx. 24° · Mín. 14°"
 */
@Composable
private fun ReferenceWeatherCard(
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snap = WeatherService.weather.value

    val cardBrush = if (isDark) {
        Brush.verticalGradient(
            listOf(
                Color(0xFF162048).copy(alpha = 0.86f),
                Color(0xFF1A295C).copy(alpha = 0.86f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color(0xFFE3ECFC).copy(alpha = 0.90f),
                Color(0xFFCCDCFA).copy(alpha = 0.88f)
            )
        )
    }
    val cardBorder = if (isDark) Color(0xFF334982).copy(alpha = 0.72f) else Color.White.copy(alpha = 0.85f)
    val titleColor = if (isDark) Color.White else Color(0xFF141B2D)
    val subtitleColor = if (isDark) Color(0xFFB8C7E6) else Color(0xFF3E4C66)

    Surface(
        modifier = modifier.clickable {
            val loc = NavigationState.currentLocation.value
            if (loc != null) {
                WeatherService.onLocationAvailable(context, loc.latitude, loc.longitude, force = true)
            }
        },
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, cardBorder),
        shadowElevation = if (isDark) 0.dp else 5.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(cardBrush)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Clima",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = subtitleColor
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${snap.currentTempC}°",
                    fontSize = 34.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = titleColor
                )

                ReferenceWeatherIllustration(
                    isDark = isDark,
                    weatherCode = snap.currentCode,
                    modifier = Modifier.size(44.dp)
                )
            }

            Column {
                val conditionDisplay = if (!snap.isLoaded) {
                    if (isDark) "Despejado" else "Parcialmente nublado"
                } else {
                    snap.currentLabel
                }
                Text(
                    text = conditionDisplay,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Máx. ${snap.maxTempC}° · Mín. ${snap.minTempC}°",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = subtitleColor,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Dibuja el icono artístico de clima idéntico a las referencias:
 *  - Modo Noche: Luna creciente con estrellas brillantes en trazo azul/blanco.
 *  - Modo Día: Sol dorado con rayos asomando detrás de una nube contorneada.
 */
@Composable
private fun ReferenceWeatherIllustration(
    isDark: Boolean,
    weatherCode: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        if (isDark && weatherCode !in 51..99) {
            // Luna creciente + estrellas (como en la imagen de referencia de Noche)
            val moonColor = Color(0xFFB4C8F8)
            val strokeW = 2.2.dp.toPx()

            val moonPath = Path().apply {
                moveTo(w * 0.56f, h * 0.16f)
                cubicTo(
                    w * 0.24f, h * 0.18f,
                    w * 0.14f, h * 0.62f,
                    w * 0.42f, h * 0.80f
                )
                cubicTo(
                    w * 0.64f, h * 0.90f,
                    w * 0.86f, h * 0.74f,
                    w * 0.86f, h * 0.54f
                )
                cubicTo(
                    w * 0.64f, h * 0.64f,
                    w * 0.42f, h * 0.46f,
                    w * 0.56f, h * 0.16f
                )
                close()
            }
            drawPath(
                path = moonPath,
                color = moonColor,
                style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Estrella de 4 puntas arriba a la derecha
            val sx = w * 0.76f
            val sy = h * 0.28f
            val sr = 4.dp.toPx()
            drawLine(moonColor, Offset(sx - sr, sy), Offset(sx + sr, sy), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
            drawLine(moonColor, Offset(sx, sy - sr), Offset(sx, sy + sr), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
        } else {
            // Sol dorado detrás de una nube suave (como en la imagen de referencia de Día)
            val sunColor = Color(0xFFF59E0B)
            val sunCenter = Offset(w * 0.46f, h * 0.38f)
            val sunRadius = w * 0.18f

            // Rayos del sol
            for (angleDeg in 0 until 360 step 45) {
                val rad = Math.toRadians(angleDeg.toDouble())
                val r1 = sunRadius * 1.32f
                val r2 = sunRadius * 1.68f
                drawLine(
                    color = sunColor,
                    start = Offset(
                        sunCenter.x + (kotlin.math.cos(rad) * r1).toFloat(),
                        sunCenter.y + (kotlin.math.sin(rad) * r1).toFloat()
                    ),
                    end = Offset(
                        sunCenter.x + (kotlin.math.cos(rad) * r2).toFloat(),
                        sunCenter.y + (kotlin.math.sin(rad) * r2).toFloat()
                    ),
                    strokeWidth = 1.8.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            drawCircle(
                color = sunColor,
                radius = sunRadius,
                center = sunCenter
            )

            // Nube en primer plano
            val cloudFill = if (isDark) Color(0xFF1E2D5E) else Color(0xFFEBF2FF)
            val cloudStroke = if (isDark) Color(0xFF9BB8F8) else Color(0xFF283A66)

            val cloudRectTop = h * 0.48f
            val cloudRectBottom = h * 0.78f
            drawRoundRect(
                color = cloudFill,
                topLeft = Offset(w * 0.18f, cloudRectTop),
                size = Size(w * 0.66f, cloudRectBottom - cloudRectTop),
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
            )
            drawCircle(
                color = cloudFill,
                radius = w * 0.16f,
                center = Offset(w * 0.42f, h * 0.52f)
            )
            drawCircle(
                color = cloudFill,
                radius = w * 0.13f,
                center = Offset(w * 0.62f, h * 0.56f)
            )

            drawRoundRect(
                color = cloudStroke,
                topLeft = Offset(w * 0.18f, cloudRectTop),
                size = Size(w * 0.66f, cloudRectBottom - cloudRectTop),
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

/**
 * Dock inferior centrado con los 6 botones squircle adaptados a las opciones que ya existen en el Launcher:
 *  1. Apps (Cajón de aplicaciones instaladas) — Morado
 *  2. Mapas (Modo Navegación 3D / CarPlay) — Azul Cielo
 *  3. Velocímetro (18 Estilos de Velocímetro GPS) — Verde Esmeralda
 *  4. Dashcam (Galería de videos + mantener presionado para iniciar/detener grabación REC) — Naranja Coral
 *  5. Rutas (Historial de Rutas GPS) — Azul Real
 *  6. Récords (Récords de Velocidad) — Gris Pizarra Oscuro
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReferenceBottomDock(
    isDark: Boolean,
    onOpenAllApps: () -> Unit,
    onOpenNavigation: () -> Unit,
    onOpenSpeedometer: () -> Unit,
    onOpenDashcamGallery: () -> Unit,
    onOpenRoutes: () -> Unit,
    onOpenRecords: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isRec = DashcamManager.isRecording.value
    val todayMax = SpeedRecordTracker.todayMaxLive.value

    val dockBg = if (isDark) Color(0xFF141E42).copy(alpha = 0.84f) else Color(0xFFE7EFFC).copy(alpha = 0.90f)
    val dockBorder = if (isDark) Color(0xFF31447E).copy(alpha = 0.75f) else Color.White.copy(alpha = 0.88f)

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = dockBg,
        border = BorderStroke(1.dp, dockBorder),
        shadowElevation = if (isDark) 0.dp else 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. APPS (Morado igual al 1er botón de la imagen)
            DockAppItem(
                label = "Apps",
                icon = Icons.Default.Apps,
                gradientColors = listOf(Color(0xFF7B61FF), Color(0xFF4A32D4)),
                isDark = isDark,
                onClick = onOpenAllApps
            )

            // 2. MAPAS (Azul celeste igual al 2do botón de la imagen)
            DockAppItem(
                label = "Mapas",
                icon = Icons.Default.LocationOn,
                gradientColors = listOf(Color(0xFF60B6FF), Color(0xFF2E6FE8)),
                isDark = isDark,
                onClick = onOpenNavigation
            )

            // 3. VELOCÍMETRO (Verde esmeralda igual al 3er botón de la imagen)
            DockAppItem(
                label = "Velocímetro",
                icon = Icons.Default.Speed,
                gradientColors = listOf(Color(0xFF34D374), Color(0xFF168A42)),
                isDark = isDark,
                onClick = onOpenSpeedometer
            )

            // 4. DASHCAM (Naranja coral igual al 4to botón de la imagen; pulsación larga = iniciar/detener REC)
            DockAppItem(
                label = if (isRec) "Grabando" else "Dashcam",
                icon = if (isRec) Icons.Default.Videocam else Icons.Default.PhotoCameraFront,
                gradientColors = if (isRec) {
                    listOf(Color(0xFFFF4B4B), Color(0xFFB71C1C))
                } else {
                    listOf(Color(0xFFFF8F66), Color(0xFFE05336))
                },
                isDark = isDark,
                showBadgeDot = isRec,
                onClick = onOpenDashcamGallery,
                onLongClick = {
                    if (isRec) DashcamManager.stopRecording()
                    else DashcamManager.startRecording(context, lifecycleOwner)
                }
            )

            // 5. RUTAS (Azul índigo igual al 5to botón de la imagen)
            DockAppItem(
                label = "Rutas",
                icon = Icons.Default.Timeline,
                gradientColors = listOf(Color(0xFF6482FF), Color(0xFF3752D6)),
                isDark = isDark,
                onClick = onOpenRoutes
            )

            // 6. RÉCORDS (Gris pizarra oscuro igual al 6to botón de la imagen)
            DockAppItem(
                label = if (todayMax >= 15f) "Récords" else "Récords",
                icon = Icons.Default.EmojiEvents,
                gradientColors = listOf(Color(0xFF475569), Color(0xFF1E293B)),
                isDark = isDark,
                onClick = onOpenRecords
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockAppItem(
    label: String,
    icon: ImageVector,
    gradientColors: List<Color>,
    isDark: Boolean,
    showBadgeDot: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.91f else 1f, tween(100), label = "dockItemScale")
    val labelColor = if (isDark) Color.White else Color(0xFF141B2D)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .shadow(4.dp, RoundedCornerShape(13.dp))
                .clip(RoundedCornerShape(13.dp))
                .background(Brush.verticalGradient(gradientColors))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.30f),
                    shape = RoundedCornerShape(13.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(23.dp)
            )

            if (showBadgeDot) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(7.dp)
                        .background(Color.White, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = labelColor,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Elimina márgenes transparentes sobrantes alrededor de PNGs recortados de autos
 * para que la carrocería ocupe todo el ancho/alto visual disponible.
 */
private fun trimTransparentBounds(src: android.graphics.Bitmap): android.graphics.Bitmap {
    if (!src.hasAlpha()) return src
    val w = src.width
    val h = src.height
    if (w <= 16 || h <= 16) return src

    val pixels = IntArray(w * h)
    src.getPixels(pixels, 0, w, 0, 0, w, h)

    var minX = w
    var minY = h
    var maxX = -1
    var maxY = -1

    for (y in 0 until h) {
        val rowOffset = y * w
        for (x in 0 until w) {
            val alpha = (pixels[rowOffset + x] ushr 24) and 0xFF
            if (alpha > 14) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
    }

    if (maxX <= minX || maxY <= minY) return src

    val padX = ((maxX - minX) * 0.02f).toInt()
    val padY = ((maxY - minY) * 0.02f).toInt()
    val cropLeft = (minX - padX).coerceAtLeast(0)
    val cropTop = (minY - padY).coerceAtLeast(0)
    val cropRight = (maxX + padX).coerceAtMost(w - 1)
    val cropBottom = (maxY + padY).coerceAtMost(h - 1)

    val cropW = cropRight - cropLeft + 1
    val cropH = cropBottom - cropTop + 1
    if (cropW >= w * 0.96f && cropH >= h * 0.96f) return src

    return android.graphics.Bitmap.createBitmap(src, cropLeft, cropTop, cropW, cropH)
}

@Composable
internal fun ExpandedMusicModal(onDismiss: () -> Unit) {
    val palette = LocalBentoPalette.current
    val songTitle = GlobalState.songTitle.value
    val songArtist = GlobalState.songArtist.value
    val albumArt = GlobalState.songAlbumArt.value
    val isPlaying = GlobalState.isPlaying.value
    val duration = GlobalState.songDuration.value
    val position = GlobalState.songPosition.value
    val artBmp = remember(albumArt) { albumArt?.asImageBitmap() }

    var isSeeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableStateOf(0f) }

    DisposableEffect(Unit) {
        NavigationState.activeModalCount += 1
        NavigationState.notifyUserInteraction()
        onDispose {
            NavigationState.activeModalCount = (NavigationState.activeModalCount - 1).coerceAtLeast(0)
            NavigationState.isTouchingScreen.value = false
            NavigationState.notifyUserInteraction()
        }
    }

    val sliderValue = if (isSeeking) seekFraction else if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val anyPressed = event.changes.any { it.pressed }
                            NavigationState.isTouchingScreen.value = anyPressed
                            NavigationState.notifyUserInteraction()
                        }
                    }
                }
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                shape = RoundedCornerShape(30.dp),
                color = palette.cardBg,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = palette.textSecondary)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(palette.routesTileBg),
                        contentAlignment = Alignment.Center
                    ) {
                        if (artBmp != null) {
                            Image(bitmap = artBmp, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            SunsetAlbumArtPlaceholder(modifier = Modifier.fillMaxSize())
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    Text(songTitle, fontSize = 24.sp, fontWeight = FontWeight.Black, color = palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(songArtist.ifBlank { GlobalState.playerAppName.value }, fontSize = 15.sp, color = palette.textSecondary, maxLines = 1)

                    Spacer(modifier = Modifier.height(16.dp))
                    Slider(
                        value = sliderValue,
                        onValueChange = {
                            isSeeking = true
                            seekFraction = it
                        },
                        onValueChangeFinished = {
                            if (duration > 0) {
                                val target = (seekFraction * duration).toLong()
                                GlobalState.seekTo(target)
                                GlobalState.songPosition.value = target
                            }
                            isSeeking = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = palette.accent,
                            activeTrackColor = palette.accent,
                            inactiveTrackColor = palette.trackInactive
                        )
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatDurationMs(if (isSeeking) (seekFraction * duration).toLong() else position), fontSize = 12.sp, color = palette.textSecondary)
                        Text(formatDurationMs(duration), fontSize = 12.sp, color = palette.textSecondary)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { GlobalState.skipToPrevious() }, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Default.SkipPrevious, null, tint = palette.textPrimary, modifier = Modifier.size(38.dp))
                        }
                        Surface(
                            onClick = { GlobalState.togglePlayPause() },
                            shape = CircleShape,
                            color = palette.accent,
                            modifier = Modifier.size(68.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(36.dp))
                            }
                        }
                        IconButton(onClick = { GlobalState.skipToNext() }, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Default.SkipNext, null, tint = palette.textPrimary, modifier = Modifier.size(38.dp))
                        }
                    }
                }
            }
        }
    }
}

internal fun formatDurationMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
