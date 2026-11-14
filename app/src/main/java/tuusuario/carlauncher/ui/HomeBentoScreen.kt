package com.tuusuario.carlauncher.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.location.LocationManager
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
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.dsl.cameraOptions
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.location
import com.tuusuario.carlauncher.R
import com.tuusuario.carlauncher.services.ArletAssistant
import com.tuusuario.carlauncher.services.DashcamManager
import com.tuusuario.carlauncher.services.GlobalState
import com.tuusuario.carlauncher.services.LocationEngine
import com.tuusuario.carlauncher.services.WeatherService
import com.tuusuario.carlauncher.ui.map.MockLocationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Menú Principal inspirado en las imágenes de referencia (Modo Día y Modo Noche):
 *  - Reloj grande inteligente en el lado izquierdo (en la cabecera izquierda en horizontal y sobre la insignia del auto en vertical).
 *  - Minimapa Mapbox 100% funcional en tiempo real (sigue el GPS en vivo, dibuja la ruta activa, soporta Día/Noche y botón recentrar).
 *  - Vehículo protagonista de gran tamaño en modo horizontal y vertical con sombra realista bajo los neumáticos.
 *  - Tarjetas de Música y Clima optimizadas para que ningún texto ni barra de progreso se recorte.
 *  - Modo vertical perfectamente encuadrado sin solapamientos en cabecera ni recortes en el dock inferior.
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
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // ── CABECERA SUPERIOR: Reloj Grande Izquierdo + Saludo/Fecha | Botones Voz, Tema y Ajustes a la derecha ──
                ReferenceTopHeader(
                    isDark = isDark,
                    isLandscape = true,
                    onOpenSettings = onOpenSettings,
                    onToggleTheme = onToggleTheme,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                // ── ZONA CENTRAL: Minimapa Funcional en Vivo (Izquierda) + Auto Grande / Música / Clima (Derecha) ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Izquierda (34%): Tarjeta Google Maps con Minimapa Mapbox en tiempo real y botón "Abrir mapa ↗"
                    ReferenceMiniMapCard(
                        isDark = isDark,
                        isLandscape = true,
                        onOpenNavigation = onOpenNavigation,
                        modifier = Modifier
                            .weight(0.34f)
                            .fillMaxHeight()
                    )

                    // Derecha (66%): Auto grande arriba + (Reproductor Música + Tarjeta Clima) abajo
                    Column(
                        modifier = Modifier
                            .weight(0.66f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Parte superior: Auto protagonista grande y prominente + pastilla "Mi [Modelo]"
                        ReferenceFloatingCarSection(
                            isDark = isDark,
                            isLandscape = true,
                            onOpenSpeedometer = onOpenSpeedometer,
                            onOpenSettings = onOpenSettings,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.54f)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Parte inferior: Reproductor de Música (izquierda) + Clima (derecha) sin recortes
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.46f),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ReferenceMusicPlayerCard(
                                isDark = isDark,
                                isLandscape = true,
                                onOpenYoutube = onOpenYoutube,
                                modifier = Modifier
                                    .weight(0.66f)
                                    .fillMaxHeight()
                            )

                            ReferenceWeatherCard(
                                isDark = isDark,
                                isLandscape = true,
                                modifier = Modifier
                                    .weight(0.34f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // ── DOCK INFERIOR CENTRADO CON LAS 6 OPCIONES DEL LAUNCHER ──
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    ReferenceBottomDock(
                        isDark = isDark,
                        isLandscape = true,
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
            // ── MODO VERTICAL PERFECTAMENTE ENCUADRADO SIN SOLAPAMIENTOS NI RECORTES ──
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Cabecera sin colisión: Saludo a la izquierda + 3 botones bien encuadrados a la derecha
                ReferenceTopHeader(
                    isDark = isDark,
                    isLandscape = false,
                    onOpenSettings = onOpenSettings,
                    onToggleTheme = onToggleTheme,
                    modifier = Modifier.fillMaxWidth()
                )

                // Sección Vehículo + Reloj Grande ubicado inteligentemente a la izquierda sobre la insignia del auto
                ReferenceFloatingCarSection(
                    isDark = isDark,
                    isLandscape = false,
                    onOpenSpeedometer = onOpenSpeedometer,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.26f)
                )

                // Fila central: Minimapa funcional en tiempo real (izquierda) + Clima (derecha)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.37f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ReferenceMiniMapCard(
                        isDark = isDark,
                        isLandscape = false,
                        onOpenNavigation = onOpenNavigation,
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxHeight()
                    )

                    ReferenceWeatherCard(
                        isDark = isDark,
                        isLandscape = false,
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxHeight()
                    )
                }

                // Reproductor de Música completo
                ReferenceMusicPlayerCard(
                    isDark = isDark,
                    isLandscape = false,
                    onOpenYoutube = onOpenYoutube,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.21f)
                )

                // Dock inferior ocupando todo el ancho con los 6 botones distribuidos uniformemente
                ReferenceBottomDock(
                    isDark = isDark,
                    isLandscape = false,
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

/**
 * Estado compartido del reloj en formato 12h para mostrarse con tipografía grande en el lado izquierdo.
 */
@Composable
private fun rememberClockState(): Triple<String, String, String> {
    var timeDigits by remember { mutableStateOf("12:00") }
    var amPmStr by remember { mutableStateOf("a. m.") }
    var dateSpanishStr by remember { mutableStateOf("Sábado, 14 de noviembre") }

    LaunchedEffect(Unit) {
        val localeEs = Locale("es", "ES")
        val dayFmt = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", localeEs)
        while (true) {
            val now = LocalDateTime.now()
            val hour12 = when {
                now.hour == 0 -> 12
                now.hour > 12 -> now.hour - 12
                else -> now.hour
            }
            timeDigits = "%d:%02d".format(hour12, now.minute)
            amPmStr = if (now.hour < 12) "a. m." else "p. m."

            val rawDate = now.format(dayFmt)
            dateSpanishStr = rawDate.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(localeEs) else it.toString()
            }

            val msToNextSecond = 1000L - (System.currentTimeMillis() % 1000L)
            delay(msToNextSecond.coerceIn(250L, 1000L))
        }
    }

    return Triple(timeDigits, amPmStr, dateSpanishStr)
}

/**
 * Widget de Reloj Grande ubicado en el lado izquierdo.
 */
@Composable
private fun LargeLeftClockBadge(
    timeDigits: String,
    amPmStr: String,
    isDark: Boolean,
    fontSizeSp: Int = 40
) {
    val titleColor = if (isDark) Color.White else Color(0xFF141B2D)
    val accentAmPm = if (isDark) Color(0xFF9BB8F8) else Color(0xFF2B5CE6)

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = timeDigits,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp + 2).sp,
            fontWeight = FontWeight.Black,
            color = titleColor,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
        Text(
            text = amPmStr,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = accentAmPm,
            modifier = Modifier.padding(bottom = 5.dp)
        )
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

        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(bgTopLeft, bgMid, bgBottomRight),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
        )

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
 * Cabecera superior adaptada inteligentemente:
 *  - En Horizontal: Reloj Grande a la izquierda + divisor luminoso + Saludo ("Buenas noches, Alex") y fecha.
 *  - En Vertical: Saludo y fecha a la izquierda (sin chocar con el reloj) + los 3 botones a la derecha (el Reloj Grande se ubica abajo a la izquierda junto al auto).
 */
@Composable
private fun ReferenceTopHeader(
    isDark: Boolean,
    isLandscape: Boolean,
    onOpenSettings: () -> Unit,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val userName = AppSettings.userName.value.trim()
    val (timeDigits, amPmStr, dateSpanishStr) = rememberClockState()
    val currentHour = remember(timeDigits, amPmStr) { LocalDateTime.now().hour }

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
    val dividerColor = if (isDark) Color(0xFF4E63A6).copy(alpha = 0.65f) else Color(0xFF3A5AD9).copy(alpha = 0.35f)

    val isListening = ArletAssistant.isListening.value || ArletAssistant.isWakeWordActiveWindow.value
    val isSpeaking = ArletAssistant.isSpeaking.value
    val btnSize = if (isLandscape) 38.dp else 36.dp

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Lado izquierdo: En Horizontal incluye el Reloj Grande a la izquierda junto al saludo
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLandscape) {
                LargeLeftClockBadge(
                    timeDigits = timeDigits,
                    amPmStr = amPmStr,
                    isDark = isDark,
                    fontSizeSp = 40
                )

                Spacer(modifier = Modifier.width(14.dp))

                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(dividerColor)
                )

                Spacer(modifier = Modifier.width(14.dp))
            }

            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = fullGreeting,
                    fontSize = if (isLandscape) 23.sp else 21.sp,
                    lineHeight = if (isLandscape) 26.sp else 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateSpanishStr,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Lado derecho: 3 botones squircle bien encuadrados (Voz Sara, Día/Noche, Ajustes)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Botón Asistente de Voz Sara
            Surface(
                onClick = {
                    if (isListening || isSpeaking) ArletAssistant.closeOverlay()
                    else ArletAssistant.triggerManualListening()
                },
                shape = RoundedCornerShape(12.dp),
                color = when {
                    isListening -> Color(0xFFFF007F)
                    isSpeaking -> Color(0xFF00B8D4)
                    else -> btnBg
                },
                border = BorderStroke(1.dp, if (isListening || isSpeaking) Color.Transparent else btnBorder),
                shadowElevation = if (isDark) 0.dp else 3.dp,
                modifier = Modifier.size(btnSize)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Asistente Sara",
                        tint = if (isListening || isSpeaking) Color.White else titleColor,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            // Botón rápido Modo Día / Noche
            Surface(
                onClick = onToggleTheme,
                shape = RoundedCornerShape(12.dp),
                color = btnBg,
                border = BorderStroke(1.dp, btnBorder),
                shadowElevation = if (isDark) 0.dp else 3.dp,
                modifier = Modifier.size(btnSize)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Cambiar Día/Noche",
                        tint = titleColor,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            // Botón de Ajustes
            Surface(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(12.dp),
                color = btnBg,
                border = BorderStroke(1.dp, btnBorder),
                shadowElevation = if (isDark) 0.dp else 3.dp,
                modifier = Modifier.size(btnSize)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Configuración",
                        tint = titleColor,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

/**
 * Tarjeta izquierda con cabecera "Google Maps / Tu ubicación", Minimapa Mapbox 100% funcional en tiempo real
 * y botón inferior "Abrir mapa ↗".
 */
@Composable
private fun ReferenceMiniMapCard(
    isDark: Boolean,
    isLandscape: Boolean,
    onOpenNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRouteActive = NavigationState.isRouteActive.value
    val routeDist = NavigationState.activeRouteDistance.value
    val isGpsReady = NavigationState.isGpsReady.value
    val speedKmH = NavigationState.currentSpeedKmH.value
    var recenterTrigger by remember { mutableStateOf(0L) }

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
                .padding(if (isLandscape) 12.dp else 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Cabecera de la tarjeta: Icono Pin + "Google Maps" / Estado GPS en vivo
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(pinBoxBg)
                        .border(1.dp, pinBoxBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = pinIconTint,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Google Maps",
                        fontSize = 15.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = titleColor,
                        maxLines = 1
                    )
                    val statusSubtitle = when {
                        isRouteActive && routeDist.isNotBlank() -> "En ruta · $routeDist"
                        speedKmH >= 2f -> "En vivo · ${speedKmH.roundToInt()} km/h"
                        isGpsReady -> "Tu ubicación · En vivo"
                        else -> "Tu ubicación"
                    }
                    Text(
                        text = statusSubtitle,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Minimapa Mapbox 100% funcional en tiempo real (Modo Día y Modo Noche)
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
            ) {
                LiveMapboxMiniMap(
                    isDark = isDark,
                    recenterTrigger = recenterTrigger,
                    onOpenNavigation = onOpenNavigation,
                    modifier = Modifier.fillMaxSize()
                )

                // Botón recentrar (esquina inferior derecha del minimapa)
                Surface(
                    onClick = { recenterTrigger = System.currentTimeMillis() },
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

            Spacer(modifier = Modifier.height(8.dp))

            // Botón inferior "Abrir mapa ↗"
            Surface(
                onClick = onOpenNavigation,
                shape = RoundedCornerShape(13.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(btnBrush, RoundedCornerShape(13.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Abrir mapa",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = btnTextColor
                    )
                    Canvas(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(11.dp)
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

private const val MINI_ROUTE_SOURCE_ID = "mini-map-route-source"
private const val MINI_ROUTE_CASING_ID = "mini-map-route-casing"
private const val MINI_ROUTE_LAYER_ID = "mini-map-route-layer"

/**
 * Minimapa Mapbox real y funcional en tiempo real:
 *  - Se actualiza en vivo con cada cambio de ubicación GPS o simulación de movimiento (LocationEngine).
 *  - Muestra el puck de ubicación con halo pulsante y rumbo en vivo.
 *  - Dibuja la ruta activa en azul si existe una navegación en curso.
 *  - Cambia automáticamente entre estilo Día (MAPBOX_STREETS) y Noche (DARK).
 */
@Composable
private fun LiveMapboxMiniMap(
    isDark: Boolean,
    recenterTrigger: Long,
    onOpenNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val miniLocationProvider = remember { MockLocationProvider() }
    var miniStyleReady by remember { mutableStateOf(false) }

    val miniMapView = remember {
        MapView(context, MapInitOptions(context = context, textureView = true))
    }

    // Obtener última ubicación conocida del sistema para arranque instantáneo antes del primer tick GPS
    val initialFallbackPoint = remember {
        val current = NavigationState.currentLocation.value
        if (current != null && (current.latitude != 0.0 || current.longitude != 0.0)) {
            Point.fromLngLat(current.longitude, current.latitude)
        } else {
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val lastGps = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    ?: lm?.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
                if (lastGps != null) {
                    Point.fromLngLat(lastGps.longitude, lastGps.latitude)
                } else {
                    Point.fromLngLat(-66.9036, 10.4806)
                }
            } catch (_: SecurityException) {
                Point.fromLngLat(-66.9036, 10.4806)
            } catch (_: Exception) {
                Point.fromLngLat(-66.9036, 10.4806)
            }
        }
    }

    fun refreshMiniRoute() {
        if (!miniStyleReady) return
        val pts = NavigationState.activeRoutePoints.toList()
        miniMapView.mapboxMap.getStyle { style ->
            val src = style.getSourceAs<GeoJsonSource>(MINI_ROUTE_SOURCE_ID) ?: return@getStyle
            if (NavigationState.isRouteActive.value && pts.size >= 2) {
                src.featureCollection(
                    FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(pts)))
                )
            } else {
                src.featureCollection(FeatureCollection.fromFeatures(emptyList()))
            }
        }
    }

    // Ciclo de vida del MapView en miniatura
    DisposableEffect(lifecycleOwner) {
        miniMapView.onStart()
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> miniMapView.onStart()
                Lifecycle.Event.ON_STOP -> miniMapView.onStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            miniMapView.onStop()
            miniMapView.onDestroy()
        }
    }

    // Escuchar actualizaciones de GPS / Simulación en tiempo real a través de LocationEngine
    DisposableEffect(Unit) {
        val listener = LocationEngine.Listener { loc, projectedLat, projectedLon, bearingDeg, _, animDurationMs, _ ->
            if (loc.latitude == 0.0 && loc.longitude == 0.0) return@Listener
            val pt = Point.fromLngLat(projectedLon, projectedLat)
            miniLocationProvider.updateLocation(pt, bearingDeg, animDurationMs)

            miniMapView.camera.easeTo(
                cameraOptions {
                    center(pt)
                    zoom(15.5)
                    pitch(22.0)
                    bearing(bearingDeg)
                },
                MapAnimationOptions.mapAnimationOptions {
                    duration(animDurationMs.coerceIn(120L, 900L))
                }
            )
        }
        LocationEngine.addListener(listener)
        onDispose {
            LocationEngine.removeListener(listener)
        }
    }

    // Reaccionar también si currentLocation cambia directamente
    val currentLoc = NavigationState.currentLocation.value
    LaunchedEffect(currentLoc, miniStyleReady) {
        if (miniStyleReady && currentLoc != null && (currentLoc.latitude != 0.0 || currentLoc.longitude != 0.0)) {
            val pt = Point.fromLngLat(currentLoc.longitude, currentLoc.latitude)
            val brg = if (currentLoc.hasBearing()) currentLoc.bearing.toDouble() else 0.0
            miniLocationProvider.updateLocation(pt, brg, animDurationMs = 150L)
        }
    }

    // Actualizar estilo Día / Noche dinámicamente
    LaunchedEffect(isDark) {
        miniStyleReady = false
        val targetStyle = if (isDark) Style.DARK else Style.MAPBOX_STREETS
        val routeColorHex = if (isDark) "#8AB4F8" else "#2A5BE6"
        val casingColorHex = if (isDark) "#3B69B8" else "#183CA3"

        miniMapView.mapboxMap.loadStyle(targetStyle) { loadedStyle ->
            try {
                val pts = NavigationState.activeRoutePoints.toList()
                loadedStyle.addSource(geoJsonSource(MINI_ROUTE_SOURCE_ID) {
                    if (NavigationState.isRouteActive.value && pts.size >= 2) {
                        featureCollection(
                            FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(pts)))
                        )
                    } else {
                        featureCollection(FeatureCollection.fromFeatures(emptyList()))
                    }
                })
                loadedStyle.addLayer(lineLayer(MINI_ROUTE_CASING_ID, MINI_ROUTE_SOURCE_ID) {
                    lineColor(casingColorHex)
                    lineWidth(7.5)
                    lineCap(LineCap.ROUND)
                    lineJoin(LineJoin.ROUND)
                    lineOpacity(0.85)
                })
                loadedStyle.addLayer(lineLayer(MINI_ROUTE_LAYER_ID, MINI_ROUTE_SOURCE_ID) {
                    lineColor(routeColorHex)
                    lineWidth(4.8)
                    lineCap(LineCap.ROUND)
                    lineJoin(LineJoin.ROUND)
                })
            } catch (_: Exception) {}

            val locPlugin = miniMapView.location
            try {
                locPlugin.setLocationProvider(miniLocationProvider)
            } catch (_: Exception) {}

            locPlugin.updateSettings {
                enabled = true
                pulsingEnabled = true
                pulsingColor = android.graphics.Color.parseColor(routeColorHex)
                puckBearingEnabled = true
                puckBearing = PuckBearing.HEADING
            }

            val seedLoc = NavigationState.currentLocation.value
            val seedPt = if (seedLoc != null && (seedLoc.latitude != 0.0 || seedLoc.longitude != 0.0)) {
                Point.fromLngLat(seedLoc.longitude, seedLoc.latitude)
            } else {
                initialFallbackPoint
            }
            val seedBrg = if (seedLoc != null && seedLoc.hasBearing()) seedLoc.bearing.toDouble() else 0.0

            miniLocationProvider.updateLocation(seedPt, seedBrg, animDurationMs = 80L)
            miniMapView.mapboxMap.setCamera(
                cameraOptions {
                    center(seedPt)
                    zoom(15.5)
                    pitch(22.0)
                    bearing(seedBrg)
                }
            )
            miniStyleReady = true
        }
    }

    // Actualizar polilínea cuando cambia la ruta activa
    val routeActive = NavigationState.isRouteActive.value
    val routeSize = NavigationState.activeRoutePoints.size
    LaunchedEffect(routeActive, routeSize, miniStyleReady) {
        if (miniStyleReady) {
            refreshMiniRoute()
        }
    }

    // Botón de recentrado manual
    LaunchedEffect(recenterTrigger) {
        if (recenterTrigger > 0L) {
            val loc = NavigationState.currentLocation.value
            val pt = if (loc != null && (loc.latitude != 0.0 || loc.longitude != 0.0)) {
                Point.fromLngLat(loc.longitude, loc.latitude)
            } else {
                initialFallbackPoint
            }
            val brg = if (loc != null && loc.hasBearing()) loc.bearing.toDouble() else 0.0
            miniMapView.camera.easeTo(
                cameraOptions {
                    center(pt)
                    zoom(15.8)
                    pitch(22.0)
                    bearing(brg)
                },
                MapAnimationOptions.mapAnimationOptions { duration(350L) }
            )
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            miniMapView.apply {
                mapboxMap.setCamera(
                    cameraOptions {
                        center(initialFallbackPoint)
                        zoom(15.2)
                        pitch(20.0)
                        bearing(0.0)
                    }
                )
                gestures.addOnMapClickListener {
                    onOpenNavigation()
                    true
                }
            }
        }
    )
}

/**
 * Sección del Vehículo Flotante:
 *  - En modo Horizontal el auto recibe una escala ampliada (1.38x adicional) y sin recorte de bordes (clip = false)
 *    para que se note mucho más y tenga presencia protagonista como en la referencia.
 *  - En modo Vertical muestra además el Reloj Grande inteligentemente ubicado en el lado izquierdo,
 *    justo encima de la pastilla "Mi [Modelo]".
 */
@Composable
private fun ReferenceFloatingCarSection(
    isDark: Boolean,
    isLandscape: Boolean,
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

    val pillBg = if (isDark) Color(0xFF1A2550).copy(alpha = 0.88f) else Color(0xFFE3ECFA).copy(alpha = 0.92f)
    val pillBorder = if (isDark) Color(0xFF3B4F8C).copy(alpha = 0.78f) else Color.White.copy(alpha = 0.90f)
    val pillText = if (isDark) Color.White else Color(0xFF141B2D)

    // Escala visual reforzada en modo horizontal para que el vehículo destaque con fuerza
    val effectiveScale = if (isLandscape) carScale * 1.38f else carScale * 1.08f

    Box(modifier = modifier) {
        // Contenedor del vehículo protagonista
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(if (isLandscape) 0.88f else 0.84f)
                .align(Alignment.CenterEnd)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenSpeedometer
                ),
            contentAlignment = Alignment.Center
        ) {
            // Sombra elíptica amplia bajo las ruedas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(if (isLandscape) 38.dp else 30.dp)
                    .align(Alignment.BottomCenter)
                    .offset(y = 2.dp)
            ) {
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = if (isDark) 0.68f else 0.36f),
                            Color.Black.copy(alpha = if (isDark) 0.28f else 0.12f),
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
                        .fillMaxSize()
                        .offset(y = if (isLandscape) (-4).dp else 0.dp)
                        .graphicsLayer {
                            scaleX = effectiveScale
                            scaleY = effectiveScale
                            clip = false
                        },
                    contentScale = ContentScale.Fit
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.car_hero_kia_rio),
                    contentDescription = "Vehículo",
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(y = if (isLandscape) (-4).dp else 0.dp)
                        .graphicsLayer {
                            scaleX = effectiveScale
                            scaleY = effectiveScale
                            clip = false
                        },
                    contentScale = ContentScale.Fit
                )
            }
        }

        // En Modo Vertical: Reloj Grande ubicado inteligentemente en la esquina superior izquierda sobre la insignia del auto
        if (!isLandscape) {
            val (timeDigits, amPmStr, _) = rememberClockState()
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 2.dp)
            ) {
                LargeLeftClockBadge(
                    timeDigits = timeDigits,
                    amPmStr = amPmStr,
                    isDark = isDark,
                    fontSizeSp = 38
                )
            }
        }

        // Pastilla "Mi [Modelo]" en la esquina inferior izquierda junto al auto
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
                .padding(bottom = 2.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
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
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = pillText,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Tarjeta de Reproductor de Música estilo Spotify con proporciones compactas en horizontal
 * para que la barra de progreso y los tiempos nunca se corten.
 */
@Composable
private fun ReferenceMusicPlayerCard(
    isDark: Boolean,
    isLandscape: Boolean,
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

    val artSize = if (isLandscape) 46.dp else 50.dp
    val playBtnSize = if (isLandscape) 38.dp else 42.dp
    val skipBtnSize = if (isLandscape) 30.dp else 32.dp

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, cardBorder),
        shadowElevation = if (isDark) 0.dp else 5.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(cardBrush)
                .padding(horizontal = 14.dp, vertical = if (isLandscape) 8.dp else 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Fila Superior: Carátula + Info Canción + Controles Circulares a la derecha
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(artSize)
                        .shadow(3.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
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

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showExpandedModal = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(Color(0xFF1ED760), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(8.dp)
                            )
                        }
                        Text(
                            text = playerApp.ifBlank { "Spotify" },
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = subtitleColor,
                            maxLines = 1
                        )

                        if (onOpenYoutube != null) {
                            Spacer(modifier = Modifier.width(2.dp))
                            Surface(
                                onClick = onOpenYoutube,
                                shape = RoundedCornerShape(7.dp),
                                color = if (isDark) Color(0xFF28386E) else Color.White.copy(alpha = 0.7f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayCircleFilled,
                                        contentDescription = "YouTube",
                                        tint = Color(0xFFE53935),
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = "YT",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = titleColor
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = songTitle.ifBlank { "Midnight City" },
                        fontSize = if (isLandscape) 15.sp else 16.sp,
                        lineHeight = if (isLandscape) 17.sp else 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = songArtist.ifBlank { "M83" },
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // 3 Controles Circulares a la derecha
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        onClick = { GlobalState.skipToPrevious() },
                        shape = CircleShape,
                        color = sideCircleBg,
                        modifier = Modifier.size(skipBtnSize)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Anterior",
                                tint = sideIconTint,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = { GlobalState.togglePlayPause() },
                        shape = CircleShape,
                        color = playCircleBg,
                        shadowElevation = 3.dp,
                        modifier = Modifier.size(playBtnSize)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Reproducir / Pausar",
                                tint = playIconTint,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = { GlobalState.skipToNext() },
                        shape = CircleShape,
                        color = sideCircleBg,
                        modifier = Modifier.size(skipBtnSize)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Siguiente",
                                tint = sideIconTint,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            // Fila Inferior: Barra de progreso interactiva + tiempos en los extremos
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
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
                            .height(3.5.dp)
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
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = subtitleColor,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                    Text(
                        text = if (duration > 0L) formatDurationMs(duration) else "4:04",
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
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
 * Ilustración de carátula synthwave (sol de atardecer + montañas) cuando no hay carátula activa.
 */
@Composable
private fun SunsetAlbumArtPlaceholder(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF2D1B4E),
                    Color(0xFF7B3B6B),
                    Color(0xFFD97B73)
                )
            )
        )

        drawCircle(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFE599), Color(0xFFFF9E7D))
            ),
            radius = w * 0.28f,
            center = Offset(w * 0.5f, h * 0.50f)
        )

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
 * Tarjeta de Clima optimizada para mostrar siempre la condición y Máx./Mín. sin recortes.
 */
@Composable
private fun ReferenceWeatherCard(
    isDark: Boolean,
    isLandscape: Boolean,
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
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, cardBorder),
        shadowElevation = if (isDark) 0.dp else 5.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(cardBrush)
                .padding(horizontal = 14.dp, vertical = if (isLandscape) 8.dp else 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Clima",
                fontSize = 11.sp,
                lineHeight = 13.sp,
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
                    fontSize = if (isLandscape) 28.sp else 32.sp,
                    lineHeight = if (isLandscape) 30.sp else 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = titleColor
                )

                ReferenceWeatherIllustration(
                    isDark = isDark,
                    weatherCode = snap.currentCode,
                    modifier = Modifier.size(if (isLandscape) 36.dp else 42.dp)
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
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Máx. ${snap.maxTempC}° · Mín. ${snap.minTempC}°",
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = subtitleColor,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Icono artístico de clima (Luna creciente + estrellas de noche; Sol dorado + nube de día).
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
            val moonColor = Color(0xFFB4C8F8)
            val strokeW = 2.dp.toPx()

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

            val sx = w * 0.76f
            val sy = h * 0.28f
            val sr = 3.5.dp.toPx()
            drawLine(moonColor, Offset(sx - sr, sy), Offset(sx + sr, sy), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
            drawLine(moonColor, Offset(sx, sy - sr), Offset(sx, sy + sr), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
        } else {
            val sunColor = Color(0xFFF59E0B)
            val sunCenter = Offset(w * 0.46f, h * 0.38f)
            val sunRadius = w * 0.18f

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
                    strokeWidth = 1.6.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            drawCircle(
                color = sunColor,
                radius = sunRadius,
                center = sunCenter
            )

            val cloudFill = if (isDark) Color(0xFF1E2D5E) else Color(0xFFEBF2FF)
            val cloudStroke = if (isDark) Color(0xFF9BB8F8) else Color(0xFF283A66)

            val cloudRectTop = h * 0.48f
            val cloudRectBottom = h * 0.78f
            drawRoundRect(
                color = cloudFill,
                topLeft = Offset(w * 0.18f, cloudRectTop),
                size = Size(w * 0.66f, cloudRectBottom - cloudRectTop),
                cornerRadius = CornerRadius(9.dp.toPx(), 9.dp.toPx())
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
                cornerRadius = CornerRadius(9.dp.toPx(), 9.dp.toPx()),
                style = Stroke(width = 1.8.dp.toPx())
            )
        }
    }
}

/**
 * Dock inferior adaptable:
 *  - En Horizontal: Píldora centrada con los 6 botones squircle.
 *  - En Vertical: Ocupa todo el ancho con Arrangement.SpaceEvenly para que los 6 botones ("Apps", "Mapas",
 *    "Velocímetro", "Dashcam", "Rutas", "Récords") queden perfectamente encuadrados sin recortarse jamás.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReferenceBottomDock(
    isDark: Boolean,
    isLandscape: Boolean,
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

    val dockBg = if (isDark) Color(0xFF141E42).copy(alpha = 0.84f) else Color(0xFFE7EFFC).copy(alpha = 0.90f)
    val dockBorder = if (isDark) Color(0xFF31447E).copy(alpha = 0.75f) else Color.White.copy(alpha = 0.88f)

    Surface(
        modifier = if (isLandscape) Modifier else Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = dockBg,
        border = BorderStroke(1.dp, dockBorder),
        shadowElevation = if (isDark) 0.dp else 6.dp
    ) {
        Row(
            modifier = if (isLandscape) {
                Modifier.padding(horizontal = 22.dp, vertical = 7.dp)
            } else {
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            },
            horizontalArrangement = if (isLandscape) {
                Arrangement.spacedBy(22.dp)
            } else {
                Arrangement.SpaceEvenly
            },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. APPS
            DockAppItem(
                label = "Apps",
                icon = Icons.Default.Apps,
                gradientColors = listOf(Color(0xFF7B61FF), Color(0xFF4A32D4)),
                isDark = isDark,
                isLandscape = isLandscape,
                onClick = onOpenAllApps
            )

            // 2. MAPAS
            DockAppItem(
                label = "Mapas",
                icon = Icons.Default.LocationOn,
                gradientColors = listOf(Color(0xFF60B6FF), Color(0xFF2E6FE8)),
                isDark = isDark,
                isLandscape = isLandscape,
                onClick = onOpenNavigation
            )

            // 3. VELOCÍMETRO
            DockAppItem(
                label = "Velocímetro",
                icon = Icons.Default.Speed,
                gradientColors = listOf(Color(0xFF34D374), Color(0xFF168A42)),
                isDark = isDark,
                isLandscape = isLandscape,
                onClick = onOpenSpeedometer
            )

            // 4. DASHCAM
            DockAppItem(
                label = if (isRec) "Grabando" else "Dashcam",
                icon = if (isRec) Icons.Default.Videocam else Icons.Default.PhotoCameraFront,
                gradientColors = if (isRec) {
                    listOf(Color(0xFFFF4B4B), Color(0xFFB71C1C))
                } else {
                    listOf(Color(0xFFFF8F66), Color(0xFFE05336))
                },
                isDark = isDark,
                isLandscape = isLandscape,
                showBadgeDot = isRec,
                onClick = onOpenDashcamGallery,
                onLongClick = {
                    if (isRec) DashcamManager.stopRecording()
                    else DashcamManager.startRecording(context, lifecycleOwner)
                }
            )

            // 5. RUTAS
            DockAppItem(
                label = "Rutas",
                icon = Icons.Default.Timeline,
                gradientColors = listOf(Color(0xFF6482FF), Color(0xFF3752D6)),
                isDark = isDark,
                isLandscape = isLandscape,
                onClick = onOpenRoutes
            )

            // 6. RÉCORDS
            DockAppItem(
                label = "Récords",
                icon = Icons.Default.EmojiEvents,
                gradientColors = listOf(Color(0xFF475569), Color(0xFF1E293B)),
                isDark = isDark,
                isLandscape = isLandscape,
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
    isLandscape: Boolean,
    showBadgeDot: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.91f else 1f, tween(100), label = "dockItemScale")
    val labelColor = if (isDark) Color.White else Color(0xFF141B2D)
    val boxSize = if (isLandscape) 40.dp else 38.dp
    val iconSize = if (isLandscape) 21.dp else 20.dp

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
                .size(boxSize)
                .shadow(4.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.verticalGradient(gradientColors))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.30f),
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(iconSize)
            )

            if (showBadgeDot) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(6.dp)
                        .background(Color.White, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = label,
            fontSize = if (isLandscape) 10.sp else 9.5.sp,
            lineHeight = 12.sp,
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
