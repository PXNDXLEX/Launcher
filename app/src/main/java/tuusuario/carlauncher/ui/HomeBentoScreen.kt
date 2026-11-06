package com.tuusuario.carlauncher.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
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
 * Pantalla Principal Bento moderna (inspirada en la referencia del usuario + su Kia Rio amarillo).
 * Cada sub-widget observa únicamente su propio estado (reloj, km/h, clima, canción)
 * evitando recomposiciones globales y asegurando animaciones a 60/120 FPS sin tirones.
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

    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.canvasBg)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── COLUMNA IZQUIERDA (57%): Reloj, Kia Rio Hero, Velocímetro GPS y Clima ──
            VehicleShowcaseCard(
                modifier = Modifier
                    .weight(0.57f)
                    .fillMaxHeight(),
                onOpenSpeedometer = onOpenSpeedometer,
                onOpenSettings = onOpenSettings
            )

            // ── COLUMNA DERECHA (43%): Modo Navegación, 3 Accesos Fijos, Todas las Apps, Música ──
            RightBentoColumn(
                modifier = Modifier
                    .weight(0.43f)
                    .fillMaxHeight(),
                onOpenNavigation = onOpenNavigation,
                onOpenDashcamGallery = onOpenDashcamGallery,
                onOpenRoutes = onOpenRoutes,
                onOpenRecords = onOpenRecords,
                onOpenAllApps = onOpenAllApps,
                onOpenYoutube = onOpenYoutube,
                onOpenSettings = onOpenSettings,
                onToggleTheme = onToggleTheme
            )
        }
    } else {
        // Modo Vertical adaptable
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.canvasBg)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            VehicleShowcaseCard(
                modifier = Modifier
                    .weight(0.48f)
                    .fillMaxWidth(),
                onOpenSpeedometer = onOpenSpeedometer,
                onOpenSettings = onOpenSettings
            )
            RightBentoColumn(
                modifier = Modifier
                    .weight(0.52f)
                    .fillMaxWidth(),
                onOpenNavigation = onOpenNavigation,
                onOpenDashcamGallery = onOpenDashcamGallery,
                onOpenRoutes = onOpenRoutes,
                onOpenRecords = onOpenRecords,
                onOpenAllApps = onOpenAllApps,
                onOpenYoutube = onOpenYoutube,
                onOpenSettings = onOpenSettings,
                onToggleTheme = onToggleTheme
            )
        }
    }
}

@Composable
private fun VehicleShowcaseCard(
    modifier: Modifier = Modifier,
    onOpenSpeedometer: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val palette = LocalBentoPalette.current
    val context = LocalContext.current
    val customCarPath = AppSettings.customHomeCarPhotoPath.value
    val carScale = AppSettings.homeCarScale.value
    var carBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // Cargar y recortar márgenes transparentes automáticamente en segundo plano
    // para que cualquier PNG del auto aproveche al máximo el espacio disponible
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

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = if (palette.isDark) palette.cardBg else Color(0xFFF7FAFF),
        shadowElevation = if (palette.isDark) 0.dp else 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // ── CAPA CENTRAL AMPLIA: Auto protagonista (aprovecha el espacio central entre reloj y emblema) ──
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 38.dp, bottom = 68.dp),
                contentAlignment = Alignment.Center
            ) {
                // Sombra elíptica suave bajo los neumáticos
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth(0.84f)
                        .height(36.dp)
                        .align(Alignment.BottomCenter)
                        .offset(y = (-2).dp)
                ) {
                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = if (palette.isDark) 0.55f else 0.22f),
                                Color.Black.copy(alpha = if (palette.isDark) 0.20f else 0.06f),
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
                            .fillMaxWidth(0.92f)
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
                        contentDescription = "Kia Rio Amarillo",
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .fillMaxHeight(0.96f)
                            .graphicsLayer {
                                scaleX = carScale
                                scaleY = carScale
                            },
                        contentScale = ContentScale.Fit
                    )
                }
            }

            // ── FILA SUPERIOR: Reloj grande a la izquierda + Emblema Vehículo a la derecha ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                IsolatedClockHeader()
                VehicleIdentityBadge(onClick = onOpenSettings)
            }

            // ── FILA INFERIOR: Pastilla Velocímetro GPS (tocar = abre 18 Velocímetros) + Clima Doble ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeGpsSpeedPill(
                    modifier = Modifier
                        .weight(0.35f)
                        .fillMaxHeight(),
                    onClick = onOpenSpeedometer
                )

                HomeDualWeatherPill(
                    modifier = Modifier
                        .weight(0.65f)
                        .fillMaxHeight()
                )
            }
        }
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
private fun IsolatedClockHeader() {
    val palette = LocalBentoPalette.current
    var timeStr by remember { mutableStateOf("") }
    var dateStr by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val localeEs = Locale("es", "ES")
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
        val dayFmt = DateTimeFormatter.ofPattern("EEEE · d MMM", localeEs)
        while (true) {
            val now = LocalDateTime.now()
            timeStr = now.format(timeFmt)
            dateStr = now.format(dayFmt).uppercase(localeEs).replace(".", "")
            val msToNextSecond = 1000L - (System.currentTimeMillis() % 1000L)
            delay(msToNextSecond.coerceIn(200L, 1000L))
        }
    }

    Column {
        Text(
            text = timeStr.ifEmpty { "12:00" },
            fontSize = 52.sp,
            lineHeight = 54.sp,
            fontWeight = FontWeight.ExtraBold,
            color = palette.textPrimary,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = dateStr,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            color = palette.textSecondary
        )
    }
}

@Composable
private fun VehicleIdentityBadge(onClick: () -> Unit) {
    val palette = LocalBentoPalette.current
    val brand = AppSettings.carBrandName.value
    val model = AppSettings.carModelName.value
    val yearOrPlate = AppSettings.carYearOrPlate.value
    val isGpsReady = NavigationState.isGpsReady.value
    val isRec = DashcamManager.isRecording.value
    val isVoiceReady = ArletAssistant.isVoskModelReady.value && AppSettings.arletVoiceWakeEnabled.value

    val statusDotColor = when {
        isRec -> Color(0xFFE53935)
        isGpsReady -> Color(0xFF10B981)
        isVoiceReady -> palette.accent
        else -> palette.textSecondary.copy(alpha = 0.5f)
    }
    val statusText = when {
        isRec -> "Grabando Dashcam"
        isGpsReady -> "GPS activo"
        isVoiceReady -> "Sara lista"
        else -> "En espera GPS"
    }

    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Insignia automotriz azul marino brillante estilo OEM
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .shadow(4.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF15459E), Color(0xFF091E47))
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 22.dp)
                        .border(1.5.dp, Color.White.copy(alpha = 0.88f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = brand.take(4).uppercase(),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.6.sp
                    )
                }
            }

            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = brand.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.5.sp,
                    color = palette.textSecondary
                )
                Text(
                    text = model,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    color = palette.textPrimary,
                    maxLines = 1
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(2.5.dp)
                            .background(Color(0xFFE53935), RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = yearOrPlate,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(statusDotColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = statusText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = palette.textSecondary
            )
        }
    }
}

@Composable
private fun HomeGpsSpeedPill(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalBentoPalette.current
    val speed = NavigationState.currentSpeedKmH.value
    val smoothSpeed by animateFloatAsState(
        targetValue = speed,
        animationSpec = tween(durationMillis = 120, easing = LinearEasing),
        label = "homeSpeedAnim"
    )
    val gpsReady = NavigationState.isGpsReady.value
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(110), label = "speedPillScale")

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = palette.cardBg,
        shadowElevation = if (palette.isDark) 0.dp else 3.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (!gpsReady && smoothSpeed <= 0f) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(12.dp).height(4.dp).background(palette.accent, RoundedCornerShape(2.dp)))
                    Box(Modifier.width(12.dp).height(4.dp).background(palette.accent, RoundedCornerShape(2.dp)))
                }
            } else {
                Text(
                    text = smoothSpeed.roundToInt().toString(),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = palette.accent,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "km/h",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.textPrimary
                )
                Text(
                    text = "GPS · Abrir",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textSecondary
                )
            }
        }
    }
}

@Composable
private fun HomeDualWeatherPill(modifier: Modifier = Modifier) {
    val palette = LocalBentoPalette.current
    val context = LocalContext.current
    val snap = WeatherService.weather.value

    Surface(
        modifier = modifier.clickable {
            val loc = NavigationState.currentLocation.value
            if (loc != null) {
                WeatherService.onLocationAvailable(context, loc.latitude, loc.longitude, force = true)
            }
        },
        shape = RoundedCornerShape(20.dp),
        color = palette.cardBg,
        shadowElevation = if (palette.isDark) 0.dp else 3.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // AHORA
            WeatherHalfColumn(
                modifier = Modifier.weight(1f),
                header = "Ahora",
                tempC = snap.currentTempC,
                condition = snap.currentLabel,
                weatherCode = snap.currentCode,
                isPrimary = true
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .width(1.dp)
                    .fillMaxHeight(0.72f)
                    .background(palette.cardBorder)
            )

            // SIGUIENTE HORA
            WeatherHalfColumn(
                modifier = Modifier.weight(1f),
                header = "Siguiente · ${snap.nextHourTime}",
                tempC = snap.nextTempC,
                condition = snap.nextLabel,
                weatherCode = snap.nextCode,
                isPrimary = false
            )
        }
    }
}

@Composable
private fun WeatherHalfColumn(
    modifier: Modifier,
    header: String,
    tempC: Int,
    condition: String,
    weatherCode: Int,
    isPrimary: Boolean
) {
    val palette = LocalBentoPalette.current
    val (icon, iconTint, iconBg) = remember(weatherCode, palette.isDark) {
        when (weatherCode) {
            0, 1 -> Triple(
                Icons.Default.WbSunny,
                Color(0xFFF59E0B),
                if (palette.isDark) Color(0xFF332610) else Color(0xFFFFF5E0)
            )
            2, 3, 45, 48 -> Triple(
                Icons.Default.Cloud,
                if (isPrimary) Color(0xFFF59E0B) else Color(0xFF2563EB),
                if (palette.isDark) Color(0xFF1E293B) else (if (isPrimary) Color(0xFFFFF4E5) else Color(0xFFEAF2FF))
            )
            in 51..86 -> Triple(
                Icons.Default.Grain,
                Color(0xFF0EA5E9),
                if (palette.isDark) Color(0xFF122B3D) else Color(0xFFE0F2FE)
            )
            else -> Triple(
                Icons.Default.Thunderstorm,
                Color(0xFF8B5CF6),
                if (palette.isDark) Color(0xFF261A42) else Color(0xFFF3E8FF)
            )
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = header,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${tempC}°",
                fontSize = 22.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Black,
                color = if (isPrimary) palette.accent else palette.textPrimary
            )
            Text(
                text = condition,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RightBentoColumn(
    modifier: Modifier = Modifier,
    onOpenNavigation: () -> Unit,
    onOpenDashcamGallery: () -> Unit,
    onOpenRoutes: () -> Unit,
    onOpenRecords: () -> Unit,
    onOpenAllApps: () -> Unit,
    onOpenYoutube: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleTheme: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. BOTÓN HERO SUPERIOR: Modo Navegación (abre el Mapa fluido con Música reducida estilo CarPlay)
        NavigationHeroButton(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.24f),
            onClick = onOpenNavigation
        )

        // 2. FILA DE 3 CUADROS FIJOS PARA FUNCIONES INTERNAS: Dashcam/Videos, Rutas, Récords
        InternalQuickFeaturesRow(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.27f),
            onOpenDashcamGallery = onOpenDashcamGallery,
            onOpenRoutes = onOpenRoutes,
            onOpenRecords = onOpenRecords
        )

        // 3. BARRA "Todas las apps >" + Accesos rápidos amplios a Voz Sara, Tema y Ajustes
        AllAppsAndToolsBar(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            onOpenAllApps = onOpenAllApps,
            onOpenSettings = onOpenSettings,
            onToggleTheme = onToggleTheme
        )

        // 4. REPRODUCTOR DE MÚSICA LIMPIO ESTILO REFERENCIA
        CleanBentoMusicCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.37f),
            onOpenYoutube = onOpenYoutube
        )
    }
}

@Composable
private fun NavigationHeroButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalBentoPalette.current
    val isRouteActive = NavigationState.isRouteActive.value
    val routeDist = NavigationState.activeRouteDistance.value

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.975f else 1f, tween(110), label = "navHeroScale")

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 6.dp,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(palette.navyHeroStart, palette.navyHeroEnd)
                    )
                )
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E6BFF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isRouteActive) Icons.Default.Navigation else Icons.Default.Explore,
                    contentDescription = "Modo Navegación",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Modo Navegación",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isRouteActive && routeDist.isNotBlank()) {
                        "En ruta · $routeDist"
                    } else {
                        "Mapa 3D · CarPlay · Rutas GPS"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isRouteActive) Color(0xFF67E8F9) else Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Entrar",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InternalQuickFeaturesRow(
    modifier: Modifier = Modifier,
    onOpenDashcamGallery: () -> Unit,
    onOpenRoutes: () -> Unit,
    onOpenRecords: () -> Unit
) {
    val palette = LocalBentoPalette.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isRec = DashcamManager.isRecording.value
    val todayMax = SpeedRecordTracker.todayMaxLive.value

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. DASHCAM / VIDEOS (Tocar = abre Videos; botón REC en esquina o pulsación larga = inicia/detiene cámara)
        QuickSquareTile(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            title = if (isRec) "Grabando" else "Dashcam",
            subtitle = if (isRec) "REC activo" else "Videos",
            icon = if (isRec) Icons.Default.Videocam else Icons.Default.VideoLibrary,
            iconBg = if (isRec) Color(0xFFE53935) else palette.dashcamTileBg,
            iconTint = if (isRec) Color.White else palette.dashcamTileFg,
            onClick = onOpenDashcamGallery,
            onLongClick = {
                if (isRec) DashcamManager.stopRecording()
                else DashcamManager.startRecording(context, lifecycleOwner)
            },
            badgeContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (isRec) Color(0xFFE53935) else palette.dashcamTileBg)
                        .clickable {
                            if (isRec) DashcamManager.stopRecording()
                            else DashcamManager.startRecording(context, lifecycleOwner)
                        }
                        .padding(horizontal = 11.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(if (isRec) Color.White else palette.dashcamTileFg, CircleShape)
                    )
                    Text(
                        text = if (isRec) "STOP" else "REC",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isRec) Color.White else palette.dashcamTileFg
                    )
                }
            }
        )

        // 2. HISTORIAL DE RUTAS
        QuickSquareTile(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            title = "Rutas",
            subtitle = "Historial",
            icon = Icons.Default.Timeline,
            iconBg = palette.routesTileBg,
            iconTint = palette.routesTileFg,
            onClick = onOpenRoutes
        )

        // 3. RÉCORDS DE VELOCIDAD
        QuickSquareTile(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            title = "Récords",
            subtitle = if (todayMax >= 10f) "${todayMax.roundToInt()} km/h hoy" else "Marcas",
            icon = Icons.Default.EmojiEvents,
            iconBg = palette.recordsTileBg,
            iconTint = palette.recordsTileFg,
            onClick = onOpenRecords
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickSquareTile(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    badgeContent: (@Composable () -> Unit)? = null
) {
    val palette = LocalBentoPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, tween(100), label = "tileScale")

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(22.dp),
        color = palette.cardBg,
        shadowElevation = if (palette.isDark) 0.dp else 3.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            if (badgeContent != null) {
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    badgeContent()
                }
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = title, tint = iconTint, modifier = Modifier.size(25.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AllAppsAndToolsBar(
    modifier: Modifier = Modifier,
    onOpenAllApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleTheme: () -> Unit
) {
    val palette = LocalBentoPalette.current
    val isListening = ArletAssistant.isListening.value || ArletAssistant.isWakeWordActiveWindow.value
    val isSpeaking = ArletAssistant.isSpeaking.value

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Barra principal "Todas las apps >" idéntica a la referencia
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(onClick = onOpenAllApps),
            shape = RoundedCornerShape(18.dp),
            color = palette.cardBg,
            shadowElevation = if (palette.isDark) 0.dp else 3.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Todas las apps",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Botón amplio y claro para Asistente de Voz Sara (fácil de presionar al conducir)
        val micColor by animateColorAsState(
            targetValue = when {
                isListening -> Color(0xFFFF007F)
                isSpeaking -> Color(0xFF00B8D4)
                else -> palette.routesTileBg
            },
            animationSpec = tween(220),
            label = "micBg"
        )
        Surface(
            modifier = Modifier
                .width(102.dp)
                .fillMaxHeight()
                .clickable {
                    if (isListening || isSpeaking) ArletAssistant.closeOverlay()
                    else ArletAssistant.triggerManualListening()
                },
            shape = RoundedCornerShape(18.dp),
            color = micColor,
            shadowElevation = if (palette.isDark) 0.dp else 3.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isListening || isSpeaking) Color.Transparent else palette.accent.copy(alpha = 0.35f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Asistente Sara",
                    tint = if (isListening || isSpeaking) Color.White else palette.accent,
                    modifier = Modifier.size(25.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isListening) "Oír" else "Voz",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isListening || isSpeaking) Color.White else palette.accent,
                    maxLines = 1
                )
            }
        }

        // Botón rápido Tema Día/Noche
        Surface(
            modifier = Modifier
                .size(56.dp)
                .clickable(onClick = onToggleTheme),
            shape = RoundedCornerShape(18.dp),
            color = palette.cardBg,
            shadowElevation = if (palette.isDark) 0.dp else 3.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (palette.isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Tema",
                    tint = palette.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Botón rápido Configuración
        Surface(
            modifier = Modifier
                .size(56.dp)
                .clickable(onClick = onOpenSettings),
            shape = RoundedCornerShape(18.dp),
            color = palette.cardBg,
            shadowElevation = if (palette.isDark) 0.dp else 3.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Ajustes",
                    tint = palette.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun CleanBentoMusicCard(
    modifier: Modifier = Modifier,
    compactMode: Boolean = false,
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

    // Actualizar progreso de reproducción suavemente cada 1s cuando está reproduciendo
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

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = palette.cardBg,
        shadowElevation = if (palette.isDark) 0.dp else 3.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (compactMode) 14.dp else 18.dp, vertical = if (compactMode) 12.dp else 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Fila Superior: Miniatura + Título/Artista + Botón YouTube opcional
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showExpandedModal = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(if (compactMode) 46.dp else 52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.routesTileBg),
                    contentAlignment = Alignment.Center
                ) {
                    if (artImageBitmap != null) {
                        Image(
                            bitmap = artImageBitmap,
                            contentDescription = "Carátula",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = songTitle.ifBlank { "Sin reproducción" },
                        fontSize = if (compactMode) 15.sp else 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = buildString {
                        if (songArtist.isNotBlank()) {
                            append(songArtist)
                            append(" · ")
                        }
                        append(playerApp)
                    }
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (onOpenYoutube != null) {
                    IconButton(
                        onClick = onOpenYoutube,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(palette.subCardBg)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OndemandVideo,
                            contentDescription = "YouTube",
                            tint = palette.textSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            // Barra de progreso táctil (permite adelantar con un toque)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
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
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(palette.trackInactive)
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

            // Fila Inferior: Tiempo actual/total a la izquierda + Controles multimedia a la derecha
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${formatDurationMs(position)} / ${formatDurationMs(duration)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textSecondary,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (compactMode) 6.dp else 10.dp)
                ) {
                    IconButton(
                        onClick = { GlobalState.skipToPrevious() },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Anterior",
                            tint = palette.textPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Botón Circular Azul Principal (Play / Pause) idéntico a referencia
                    Surface(
                        onClick = { GlobalState.togglePlayPause() },
                        shape = CircleShape,
                        color = palette.accent,
                        shadowElevation = 5.dp,
                        modifier = Modifier.size(if (compactMode) 44.dp else 48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Reproducir / Pausar",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { GlobalState.skipToNext() },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Siguiente",
                            tint = palette.textPrimary,
                            modifier = Modifier.size(26.dp)
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
                            Icon(Icons.Default.MusicNote, null, tint = palette.accent, modifier = Modifier.size(72.dp))
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
    if (ms <= 0L) return "00:00"
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}

