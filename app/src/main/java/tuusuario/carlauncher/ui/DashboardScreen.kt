package com.tuusuario.carlauncher.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha as modifierAlpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.tuusuario.carlauncher.services.ArletAssistant
import com.tuusuario.carlauncher.services.DashcamManager
import com.tuusuario.carlauncher.services.GlobalState
import com.tuusuario.carlauncher.services.LocationEngine
import com.tuusuario.carlauncher.services.RouteTracker
import com.tuusuario.carlauncher.services.SpeedRecordTracker
import com.tuusuario.carlauncher.ui.map.NavigationMap
import com.tuusuario.carlauncher.ui.widgets.ArletOverlay
import com.tuusuario.carlauncher.ui.widgets.CropShape
import com.tuusuario.carlauncher.ui.widgets.ImageCropperDialog
import com.tuusuario.carlauncher.ui.widgets.SpeedometerWidget
import com.tuusuario.carlauncher.ui.widgets.YouTubeWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Turn-by-turn instruction from Mapbox Directions / OSRM steps.
 */
data class RouteStep(
    val maneuverType: String,
    val modifier: String,
    val distance: Double,
    val streetName: String,
    val maneuverLat: Double,
    val maneuverLon: Double
)

object NavigationState {
    val showGlowTuning = mutableStateOf(false)
    val currentSpeedKmH = mutableStateOf(0f)
    val isBraking = mutableStateOf(false)
    var lastZeroSpeedTime: Long = 0L
    val currentLocation = mutableStateOf<android.location.Location?>(null)
    val selectedHistoryRoute = mutableStateOf<com.tuusuario.carlauncher.services.DailyRoute?>(null)
    val selectedDashcamRoute = mutableStateOf<List<com.tuusuario.carlauncher.services.RoutePoint>?>(null)

    val isGpsReady = mutableStateOf(false)
    val isMapStyleReady = mutableStateOf(false)
    val isSettingsLoaded = mutableStateOf(false)

    // Navegación activa persistente entre pantallas
    val activeDestination = mutableStateOf<Point?>(null)
    val activeRoutePoints = mutableStateListOf<Point>()
    val activeRouteDistance = mutableStateOf("")
    val activeRouteSteps = mutableStateListOf<RouteStep>()
    val cachedRouteJson = mutableStateOf<String?>(null)
    val isRouteActive = mutableStateOf(false)

    val selectedHistorySegment = mutableStateOf<com.tuusuario.carlauncher.services.RouteSegment?>(null)
    val speedRecordPin = mutableStateOf<Point?>(null)
    val pendingRouteTrigger = mutableStateOf(0L)

    // Permite que el asistente de voz abra automáticamente el Modo Navegación al iniciar una ruta
    val requestedScreen = mutableStateOf<String?>(null)

    fun startRoute(dest: Point) {
        activeDestination.value = dest
        pendingRouteTrigger.value = System.currentTimeMillis()
        requestedScreen.value = "NAVIGATION"
    }

    fun clearActiveRoute() {
        activeDestination.value = null
        activeRoutePoints.clear()
        activeRouteDistance.value = ""
        activeRouteSteps.clear()
        cachedRouteJson.value = null
        isRouteActive.value = false
    }
}

@Composable
fun DashboardScreen(onToggleTheme: () -> Unit, isDarkMode: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val activeUiColor = Color(AppSettings.uiColor.value)
    val isNavSplit = AppSettings.navSplitMode.value
    val navPanelPct = AppSettings.navPanelPercent.value.coerceIn(25f, 50f)

    // Pantallas: "HOME", "NAVIGATION", "SPEEDOMETER", "ALL_APPS", "VIDEOS", "RUTAS", "RECORDS"
    var currentScreen by rememberSaveable { mutableStateOf("HOME") }
    // Modo de ventana YouTube: "NONE" (cerrado), "MINI" (ventana flotante junto al mapa), "FULL" (pantalla completa)
    var youtubeOverlayMode by rememberSaveable { mutableStateOf("NONE") }
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var showWelcome by rememberSaveable { mutableStateOf(true) }

    val youtubeContent = remember { movableContentOf { YouTubeWidget() } }

    // Sincronizar motor GPS unificado y modo simulación
    LaunchedEffect(Unit) {
        LocationEngine.start(context)
        ArletAssistant.init(context, lifecycleOwner)
    }

    LaunchedEffect(AppSettings.batterySaverMode.value) {
        LocationEngine.refreshHardwareRequest()
    }

    LaunchedEffect(AppSettings.isGpsSimulationMode.value) {
        LocationEngine.updateSimulationMode(AppSettings.isGpsSimulationMode.value)
    }

    // Si Sara/Arlet inicia una ruta por voz, abrir automáticamente el Modo Navegación fluido
    LaunchedEffect(NavigationState.requestedScreen.value) {
        val req = NavigationState.requestedScreen.value
        if (!req.isNullOrBlank()) {
            currentScreen = req
            NavigationState.requestedScreen.value = null
        }
    }

    // Arranque rápido sin bloqueos largos
    LaunchedEffect(Unit) {
        if (showWelcome) {
            val startMs = System.currentTimeMillis()
            while (System.currentTimeMillis() - startMs < 2200) {
                val elapsed = System.currentTimeMillis() - startMs
                if (elapsed >= 950 && NavigationState.isSettingsLoaded.value && NavigationState.isMapStyleReady.value) {
                    break
                }
                delay(60)
            }
            showWelcome = false
        }
    }

    val isNavigationVisible = currentScreen == "NAVIGATION"
    val isSubScreenVisible = currentScreen !in setOf("HOME", "NAVIGATION")

    // Padding dinámico sincronizado con el slider (25%..50% máx) y el dock ergonómico (80.dp)
    val availLandscapeWidth = (configuration.screenWidthDp.dp - 20.dp).coerceAtLeast(320.dp)
    val rightPanelWidthDp = (availLandscapeWidth * (navPanelPct / 100f)).coerceIn(220.dp, availLandscapeWidth * 0.50f)

    val availPortraitHeight = (configuration.screenHeightDp.dp - 16.dp).coerceAtLeast(400.dp)
    val portraitFraction = (0.22f + ((navPanelPct - 25f) / 25f) * 0.16f).coerceIn(0.22f, 0.38f)
    val bottomRowHeightDp = (availPortraitHeight * portraitFraction).coerceIn(168.dp, 310.dp)

    val mapStartPadding = if (isLandscape) 96.dp else 0.dp
    val mapEndPadding = if (isLandscape && isNavSplit) rightPanelWidthDp + 18.dp else 0.dp
    val mapBottomPadding = if (!isLandscape && isNavigationVisible) {
        if (isNavSplit) bottomRowHeightDp + 76.dp else 70.dp
    } else 0.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalBentoPalette.current.canvasBg)
    ) {
        // ══════════════════════════════════════════════════════════════════════
        // CAPA 0 (PERSISTENTE): Mapa 3D precalentado en memoria (0ms al abrir/cerrar)
        // ══════════════════════════════════════════════════════════════════════
        NavigationMap(
            modifier = Modifier.fillMaxSize(),
            isFullScreen = !isNavSplit,
            isDarkMode = isDarkMode,
            isVisible = isNavigationVisible,
            startOverlayPadding = mapStartPadding,
            endOverlayPadding = mapEndPadding,
            bottomOverlayPadding = mapBottomPadding
        )

        // ══════════════════════════════════════════════════════════════════════
        // CAPA 1: Interfaz CarPlay Split-Screen sobre el Mapa en Modo Navegación
        // ══════════════════════════════════════════════════════════════════════
        AnimatedVisibility(
            visible = isNavigationVisible,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize()
        ) {
            BackHandler(enabled = isNavigationVisible && !showSettingsDialog && youtubeOverlayMode != "FULL") {
                currentScreen = "HOME"
            }
            CarPlayNavigationOverlay(
                isLandscape = isLandscape,
                isSplitMode = isNavSplit,
                onToggleSplitMode = { AppSettings.setNavSplitMode(!isNavSplit) },
                onBackToHome = { currentScreen = "HOME" },
                onOpenFullSpeedometer = { currentScreen = "SPEEDOMETER" },
                onOpenYoutube = { youtubeOverlayMode = "MINI" },
                onOpenSettings = { showSettingsDialog = true }
            )
        }

        // ══════════════════════════════════════════════════════════════════════
        // CAPA 2: Menú Principal Bento (Kia Rio Amarillo + Clima + Accesos + Música)
        // ══════════════════════════════════════════════════════════════════════
        AnimatedVisibility(
            visible = currentScreen == "HOME",
            enter = fadeIn(tween(220)) + scaleIn(
                initialScale = 0.965f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
            exit = fadeOut(tween(180)) + scaleOut(
                targetScale = 0.97f,
                animationSpec = tween(180, easing = FastOutSlowInEasing)
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            HomeBentoScreen(
                isLandscape = isLandscape,
                onOpenNavigation = { currentScreen = "NAVIGATION" },
                onOpenSpeedometer = { currentScreen = "SPEEDOMETER" },
                onOpenDashcamGallery = { currentScreen = "VIDEOS" },
                onOpenRoutes = { currentScreen = "RUTAS" },
                onOpenRecords = { currentScreen = "RECORDS" },
                onOpenAllApps = { currentScreen = "ALL_APPS" },
                onOpenYoutube = {
                    currentScreen = "NAVIGATION"
                    youtubeOverlayMode = "MINI"
                },
                onOpenSettings = { showSettingsDialog = true },
                onToggleTheme = onToggleTheme
            )
        }

        // ══════════════════════════════════════════════════════════════════════
        // CAPA 3: Sub-ventanas fluidas (Velocímetro 18 Estilos, Apps, Videos, Rutas, Récords, YT)
        // ══════════════════════════════════════════════════════════════════════
        AnimatedVisibility(
            visible = isSubScreenVisible,
            enter = fadeIn(tween(200)) + slideInVertically(
                initialOffsetY = { it / 16 },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
            exit = fadeOut(tween(160)) + slideOutVertically(
                targetOffsetY = { it / 18 },
                animationSpec = tween(160)
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            SubScreenWindow(
                screenKey = currentScreen,
                youtubeContent = youtubeContent,
                onBackToHome = { currentScreen = "HOME" },
                onOpenNavigation = { currentScreen = "NAVIGATION" },
                onOpenScreen = { next ->
                    if (next == "YOUTUBE") {
                        currentScreen = "NAVIGATION"
                        youtubeOverlayMode = "MINI"
                    } else {
                        currentScreen = next
                    }
                },
                onOpenSettings = { showSettingsDialog = true }
            )
        }

        // ══════════════════════════════════════════════════════════════════════
        // CAPA 4: Overlays Globales (Asistente Voz Sara, Dashcam PiP, Logros Xbox, Notificaciones)
        // ══════════════════════════════════════════════════════════════════════

        // HUD Asistente de Voz Sara / Arlet
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 14.dp, start = 24.dp, end = 24.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            ArletOverlay(activeColor = activeUiColor)
        }

        // Logro de récord de velocidad estilo Xbox al reducir velocidad (< 10 km/h)
        val hasRecordAlert = SpeedRecordTracker.recordAlert.value != null
        LaunchedEffect(hasRecordAlert) {
            if (hasRecordAlert) {
                try {
                    val notifUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    val mp = MediaPlayer()
                    mp.setDataSource(context, notifUri)
                    mp.setOnPreparedListener { it.start() }
                    mp.setOnCompletionListener { it.release() }
                    mp.prepareAsync()
                } catch (_: Exception) {}
            }
        }

        AnimatedVisibility(
            visible = hasRecordAlert,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(320)),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(250)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        ) {
            RecordAlertOverlay()
        }

        // Banner deslizable de notificaciones entrantes (ej. WhatsApp)
        var notifOffsetX by remember { mutableStateOf(0f) }
        AnimatedVisibility(
            visible = GlobalState.showPopup.value,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp)
        ) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = LocalBentoPalette.current.cardBg),
                elevation = CardDefaults.cardElevation(10.dp),
                border = BorderStroke(1.dp, LocalBentoPalette.current.cardBorder),
                modifier = Modifier
                    .width(360.dp)
                    .offset { IntOffset(notifOffsetX.roundToInt(), 0) }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (abs(notifOffsetX) > 140f) GlobalState.showPopup.value = false
                                notifOffsetX = 0f
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            notifOffsetX += dragAmount
                        }
                    }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        GlobalState.popupApp.value,
                        fontWeight = FontWeight.Bold,
                        color = activeUiColor,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        GlobalState.popupMessage.value,
                        color = LocalBentoPalette.current.textPrimary,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Vista previa flotante arrastrable cuando la Dashcam está grabando
        if (DashcamManager.isRecording.value) {
            DashcamPreviewOverlay()
        }

        // Ventana flotante YouTube (PiP junto al Mapa + opción Pantalla Completa sin recargar video)
        YouTubeOverlayHost(
            mode = youtubeOverlayMode,
            isLandscape = isLandscape,
            youtubeContent = youtubeContent,
            onExpandFull = { youtubeOverlayMode = "FULL" },
            onShrinkMini = {
                youtubeOverlayMode = "MINI"
                if (currentScreen !in setOf("NAVIGATION", "HOME")) {
                    currentScreen = "NAVIGATION"
                }
            },
            onClose = { youtubeOverlayMode = "NONE" }
        )

        // Modal de Configuración
        AnimatedVisibility(
            visible = showSettingsDialog,
            enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.93f, animationSpec = tween(220, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.93f, animationSpec = tween(180)),
            modifier = Modifier.fillMaxSize()
        ) {
            BackHandler(enabled = showSettingsDialog) {
                showSettingsDialog = false
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showSettingsDialog = false }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                ) {
                    PremiumSettingsDialog(
                        onDismiss = { showSettingsDialog = false },
                        onNavigateToMap = {
                            currentScreen = "NAVIGATION"
                            showSettingsDialog = false
                            NavigationState.showGlowTuning.value = true
                        }
                    )
                }
            }
        }

        // Diálogo flotante de mapa para Rutas Históricas / Ruta de Video Dashcam / Pin de Récord
        if (NavigationState.selectedHistoryRoute.value != null || NavigationState.selectedDashcamRoute.value != null) {
            RouteMapFloatingDialog(
                onDismiss = {
                    NavigationState.selectedHistoryRoute.value = null
                    NavigationState.selectedHistorySegment.value = null
                    NavigationState.selectedDashcamRoute.value = null
                    NavigationState.speedRecordPin.value = null
                }
            )
        }

        // Pantalla de arranque cinemático rápida
        AnimatedVisibility(
            visible = showWelcome,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(450)),
            modifier = Modifier.fillMaxSize()
        ) {
            CinematicWelcomeScreen(
                accentColor = activeUiColor,
                gpsReady = NavigationState.isGpsReady.value,
                mapReady = NavigationState.isMapStyleReady.value,
                settingsReady = NavigationState.isSettingsLoaded.value
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumSettingsDialog(onDismiss: () -> Unit, onNavigateToMap: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var expandedSection by remember { mutableStateOf("") }
    var showSpeedoCropper by remember { mutableStateOf(false) }
    var showCarPhotoCropper by remember { mutableStateOf(false) }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }

    val speedoBgPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { pendingCropUri = it; showSpeedoCropper = true }
    }
    val homeCarPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { pendingCropUri = it; showCarPhotoCropper = true }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .fillMaxHeight(0.90f),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Cabecera
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
                    .padding(horizontal = 22.dp, vertical = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Configuración",
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Personaliza interfaz, vehículo, mapa 3D, velocímetros y asistente",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Cerrar", modifier = Modifier.size(26.dp))
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ── APARIENCIA Y TARJETA DE INICIO ──
                SettingsGroupCard("Apariencia e Inicio") {
                    SettingsRow(
                        Icons.Default.Palette,
                        "Color de Acento Principal",
                        "Personaliza botones e indicadores",
                        isExpanded = expandedSection == "color_ui",
                        onClick = { expandedSection = if (expandedSection == "color_ui") "" else "color_ui" }
                    ) {
                        ColorPicker(AppSettings.uiColor.value) { AppSettings.setUiColor(it) }
                    }

                    SettingsDivider()

                    SettingsRowSwitch(
                        icon = if (AppSettings.isDarkMode.value) Icons.Default.DarkMode else Icons.Default.LightMode,
                        title = "Modo Oscuro",
                        subtitle = "Alterna entre el tema Claro (estilo referencia) y Oscuro Nocturno",
                        checked = AppSettings.isDarkMode.value,
                        onCheckedChange = { AppSettings.setDarkMode(it) }
                    )

                    SettingsDivider()

                    SettingsRowSwitch(
                        icon = Icons.Default.Schedule,
                        title = "Cambio Automático Día / Noche por Hora",
                        subtitle = "Activa modo oscuro automáticamente entre 7:00 PM y 5:00 AM",
                        checked = AppSettings.autoThemeByTime.value,
                        onCheckedChange = { AppSettings.setAutoThemeByTime(it) }
                    )

                    SettingsDivider()

                    SettingsRow(
                        Icons.Default.DirectionsCarFilled,
                        "Datos e Imagen de tu Auto en Inicio",
                        "${AppSettings.carBrandName.value} · ${AppSettings.carModelName.value} (${AppSettings.carYearOrPlate.value})",
                        isExpanded = expandedSection == "home_car_badge",
                        onClick = { expandedSection = if (expandedSection == "home_car_badge") "" else "home_car_badge" }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = AppSettings.carBrandName.value,
                                onValueChange = { AppSettings.setCarBrandName(it) },
                                label = { Text("Marca (ej. KIA)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = AppSettings.carModelName.value,
                                onValueChange = { AppSettings.setCarModelName(it) },
                                label = { Text("Modelo (ej. RIO STYLUS)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = AppSettings.carYearOrPlate.value,
                                onValueChange = { AppSettings.setCarYearOrPlate(it) },
                                label = { Text("Placa o Año (ej. AD513IM)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(onClick = { homeCarPhotoPicker.launch("image/*") }) {
                                    Icon(Icons.Default.Image, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Cambiar foto PNG")
                                }
                                if (AppSettings.customHomeCarPhotoPath.value.isNotEmpty()) {
                                    TextButton(onClick = { AppSettings.setCustomHomeCarPhotoPath("") }) {
                                        Text("Restaurar Kia Rio Amarillo", color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            Text(
                                "Tamaño del auto en Inicio: ${(AppSettings.homeCarScale.value * 100).toInt()}%",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = AppSettings.homeCarScale.value,
                                onValueChange = { AppSettings.setHomeCarScale(it) },
                                valueRange = 0.80f..1.55f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // ── VEHÍCULO 3D EN EL MAPA ──
                SettingsGroupCard("Vehículo 3D en Mapa") {
                    SettingsRow(
                        Icons.Default.DirectionsCar,
                        "Modelo 3D en Navegación",
                        AppSettings.vehicleType.value,
                        isExpanded = expandedSection == "vehicle_type",
                        onClick = { expandedSection = if (expandedSection == "vehicle_type") "" else "vehicle_type" }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("STYLUS", "SEDAN", "HATCHBACK", "SPORT", "TAXI", "CORSA", "FLECHA").forEach { option ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (AppSettings.vehicleType.value == option)
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            else Color.Transparent
                                        )
                                        .clickable { AppSettings.setVehicleType(option) }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = AppSettings.vehicleType.value == option,
                                        onClick = { AppSettings.setVehicleType(option) }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        option,
                                        fontWeight = if (AppSettings.vehicleType.value == option) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            val isSmallUnitModel = AppSettings.vehicleType.value in setOf("STYLUS", "CORSA")
                            val sliderRange = if (isSmallUnitModel) 0.005f..0.03f else 0.05f..0.35f
                            Text(
                                "Escala del modelo 3D: ${String.format("%.4f", AppSettings.vehicle3DScale.value)}",
                                fontSize = 13.sp
                            )
                            Slider(
                                value = AppSettings.vehicle3DScale.value.coerceIn(sliderRange.start, sliderRange.endInclusive),
                                onValueChange = { AppSettings.setVehicle3DScale(it) },
                                valueRange = sliderRange,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    SettingsDivider()

                    SettingsRow(
                        Icons.Default.LocationOn,
                        "Color del Icono 2D en Mapa",
                        "Aplica para Flecha o respaldo 2D",
                        isExpanded = expandedSection == "map_icon_color",
                        onClick = { expandedSection = if (expandedSection == "map_icon_color") "" else "map_icon_color" }
                    ) {
                        ColorPicker(AppSettings.mapIconColor.value) { AppSettings.setMapIconColor(it) }
                    }

                    SettingsDivider()

                    SettingsRow(
                        icon = Icons.Default.Tune,
                        title = "Ajustar Luces y Dimensiones en Mapa",
                        subtitle = "Calibración en vivo de faros, luces de freno y escala",
                        onClick = onNavigateToMap
                    )
                }

                // ── NAVEGACIÓN Y MAPA ──
                SettingsGroupCard("Navegación y CarPlay") {
                    SettingsRowSwitch(
                        icon = Icons.Default.VerticalSplit,
                        title = "Vista Dividida CarPlay al Abrir Mapa",
                        subtitle = "Muestra el widget de música reducido y velocímetro a un costado del mapa",
                        checked = AppSettings.navSplitMode.value,
                        onCheckedChange = { AppSettings.setNavSplitMode(it) }
                    )

                    SettingsDivider()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val panelPctInt = AppSettings.navPanelPercent.value.roundToInt().coerceIn(25, 50)
                        val mapPctInt = 100 - panelPctInt
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Porcentaje de Mapa vs Música y Velocímetro",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Mapa: ${mapPctInt}%  ·  Música y Velocímetro: ${panelPctInt}% (Máx 50%)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Slider(
                            value = AppSettings.navPanelPercent.value.coerceIn(25f, 50f),
                            onValueChange = { AppSettings.setNavPanelPercent(it) },
                            valueRange = 25f..50f,
                            enabled = AppSettings.navSplitMode.value,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "75% Mapa / 25% Widgets",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "50% Mapa / 50% Widgets (Límite)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    SettingsDivider()

                    SettingsRow(
                        icon = Icons.Default.Map,
                        title = "Estilo Visual del Mapa",
                        subtitle = when (AppSettings.mapStyle.value) {
                            "DARK" -> "Modo Noche"
                            "NEON" -> "Neon Electric"
                            "SATELLITE" -> "Vista Satelital"
                            else -> "Modo Día"
                        },
                        isExpanded = expandedSection == "map_style",
                        onClick = { expandedSection = if (expandedSection == "map_style") "" else "map_style" }
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                            listOf("LIGHT" to "Día", "DARK" to "Noche", "NEON" to "Neon", "SATELLITE" to "Satélite").forEach { (style, label) ->
                                FilterChip(
                                    selected = AppSettings.mapStyle.value == style,
                                    onClick = { AppSettings.setMapStyle(style) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }

                    SettingsDivider()

                    SettingsRow(
                        Icons.Default.CloudDownload,
                        "Mapas Offline Inteligentes",
                        "Gestión automática de caché Mapbox 3D",
                        isExpanded = expandedSection == "offline_maps",
                        onClick = { expandedSection = if (expandedSection == "offline_maps") "" else "offline_maps" }
                    ) {
                        OfflineMapDownloader(context, coroutineScope)
                    }

                    SettingsDivider()

                    SettingsRowSwitch(
                        icon = Icons.Default.DirectionsCar,
                        title = "Modo Simulación de Movimiento GPS",
                        subtitle = "Prueba en vivo velocímetros, luces de freno y cámara 3D",
                        checked = AppSettings.isGpsSimulationMode.value,
                        onCheckedChange = { AppSettings.setGpsSimulationMode(it) }
                    )
                }

                // ── VELOCÍMETROS (18 ESTILOS) ──
                SettingsGroupCard("Velocímetro (18 Estilos)") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (AppSettings.isDarkMode.value) Color(0xFF141B2D) else Color(0xFFF1F5FB)),
                        contentAlignment = Alignment.Center
                    ) {
                        SpeedometerWidget()
                        Text(
                            "Vista Previa · ${AppSettings.speedoStyle.value}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 4.dp)
                        )
                    }

                    SettingsDivider()

                    SettingsRow(
                        Icons.Default.Speed,
                        "Estilo del Tablero",
                        AppSettings.speedoStyle.value,
                        isExpanded = expandedSection == "speedo_style",
                        onClick = { expandedSection = if (expandedSection == "speedo_style") "" else "speedo_style" }
                    ) {
                        val opts = listOf(
                            "PREMIUM", "NEON", "RACING", "CYBER", "AURA", "VORTEX",
                            "QUANTUM", "PULSAR", "PLASMA", "ANIME", "KAIJU", "OMNIMON",
                            "SHONEN", "MECHA", "OVERDRIVE", "DEMONIC", "NEBULA", "CUSTOM"
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            opts.chunked(3).forEach { row ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    row.forEach { o ->
                                        FilterChip(
                                            selected = AppSettings.speedoStyle.value == o,
                                            onClick = { AppSettings.setSpeedoStyle(o) },
                                            label = { Text(o, fontSize = 11.sp, maxLines = 1) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                                }
                            }
                        }
                    }

                    SettingsDivider()

                    SettingsRow(
                        Icons.Default.Lightbulb,
                        "Color de Retroiluminación",
                        "Color principal del velocímetro",
                        isExpanded = expandedSection == "speedo_color",
                        onClick = { expandedSection = if (expandedSection == "speedo_color") "" else "speedo_color" }
                    ) {
                        ColorPicker(AppSettings.speedoColor.value) { AppSettings.setSpeedoColor(it) }
                    }

                    AnimatedVisibility(visible = AppSettings.speedoStyle.value == "CUSTOM") {
                        Column {
                            SettingsDivider()
                            SettingsRow(
                                Icons.Default.Category,
                                "Forma Base",
                                AppSettings.customSpeedoShape.value,
                                isExpanded = expandedSection == "custom_shape",
                                onClick = { expandedSection = if (expandedSection == "custom_shape") "" else "custom_shape" }
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("ARC", "CIRCLE", "LINE").forEach { s ->
                                        FilterChip(
                                            selected = AppSettings.customSpeedoShape.value == s,
                                            onClick = { AppSettings.setCustomSpeedoShape(s) },
                                            label = { Text(s) }
                                        )
                                    }
                                }
                            }

                            SettingsDivider()
                            SettingsRow(
                                Icons.Default.Straighten,
                                "Estilo de Aguja",
                                AppSettings.customSpeedoNeedle.value,
                                isExpanded = expandedSection == "custom_needle",
                                onClick = { expandedSection = if (expandedSection == "custom_needle") "" else "custom_needle" }
                            ) {
                                val needleOptions = listOf(
                                    Triple("PLASMA", "Flama", "Aguja ondulante"),
                                    Triple("KATANA", "Katana", "Hoja afilada"),
                                    Triple("ARROW", "Flecha", "Clásica"),
                                    Triple("NEON", "Neón", "Halo brillante"),
                                    Triple("LASER", "Láser", "Partículas"),
                                    Triple("BOLT", "Rayo", "Eléctrico")
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    needleOptions.chunked(2).forEach { row ->
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            row.forEach { (key, title, desc) ->
                                                val isSel = AppSettings.customSpeedoNeedle.value == key
                                                Card(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable { AppSettings.setCustomSpeedoNeedle(key) },
                                                    shape = RoundedCornerShape(12.dp),
                                                    border = if (isSel) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                                                ) {
                                                    Column(modifier = Modifier.padding(10.dp)) {
                                                        Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                        Text(desc, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                            }
                                            repeat(2 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                                        }
                                    }
                                }
                            }

                            SettingsDivider()
                            SettingsRow(
                                Icons.Default.LineWeight,
                                "Grosor del Arco",
                                String.format("%.2f", AppSettings.customSpeedoThickness.value),
                                isExpanded = expandedSection == "custom_thickness",
                                onClick = { expandedSection = if (expandedSection == "custom_thickness") "" else "custom_thickness" }
                            ) {
                                Slider(
                                    value = AppSettings.customSpeedoThickness.value,
                                    onValueChange = { AppSettings.setCustomSpeedoThickness(it) },
                                    valueRange = 0.02f..0.15f
                                )
                            }

                            SettingsDivider()
                            SettingsRow(
                                Icons.Default.Image,
                                "Imagen o GIF de Fondo",
                                if (AppSettings.customSpeedoBgPath.value.isNotEmpty()) "Configurada ✓" else "Seleccionar imagen o GIF",
                                onClick = { speedoBgPicker.launch("image/*") }
                            )
                            if (AppSettings.customSpeedoBgPath.value.isNotEmpty()) {
                                Column(modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 8.dp)) {
                                    Text("Opacidad", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Slider(
                                        value = AppSettings.customSpeedoBgOpacity.value,
                                        onValueChange = { AppSettings.setCustomSpeedoBgOpacity(it) },
                                        valueRange = 0.1f..1.0f
                                    )
                                    TextButton(onClick = {
                                        AppSettings.setCustomSpeedoBgPath("")
                                        AppSettings.setCustomSpeedoBgUri("")
                                    }) {
                                        Text("Quitar imagen", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }

                // ── ASISTENTE DE VOZ SARA / ARLET ──
                SettingsGroupCard("Asistente de Voz Sara") {
                    SettingsRowSwitch(
                        icon = Icons.Default.RecordVoiceOver,
                        title = "Activación por Voz Offline (\"Sara\" / \"Oye Sara\")",
                        subtitle = "Escucha continua sin internet ni bips de sistema mediante Vosk",
                        checked = AppSettings.arletVoiceWakeEnabled.value,
                        onCheckedChange = { enabled ->
                            AppSettings.setArletVoiceWakeEnabled(enabled)
                            if (enabled) ArletAssistant.startContinuousListening()
                            else ArletAssistant.stopContinuousListening()
                        }
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.HelpOutline,
                        title = "Probar Voz y Comandos",
                        subtitle = "Escuchar demostración del copiloto virtual",
                        onClick = {
                            ArletAssistant.speak("Hola, soy Sara. Puedes decir 'Oye Sara llévame a casa', 'Sara graba video' o 'Sara pon música'.")
                        }
                    )
                }

                // ── SISTEMA Y PRUEBAS ──
                SettingsGroupCard("Sistema y Récords") {
                    SettingsRowSwitch(
                        icon = Icons.Default.BatterySaver,
                        title = "Modo Ahorro de Batería GPS",
                        subtitle = "Reduce frecuencia de muestreo GPS cuando no requieres máxima respuesta",
                        checked = AppSettings.batterySaverMode.value,
                        onCheckedChange = { AppSettings.setBatterySaverMode(it) }
                    )
                    SettingsDivider()
                    SettingsRow(
                        Icons.Default.BatteryAlert,
                        "Optimización de Batería de Android",
                        "Evitar cierre de música o asistente en segundo plano",
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            } catch (_: Exception) {}
                        }
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.Speed,
                        title = "Probar Logro — Récord del Día",
                        subtitle = "Muestra animación estilo Xbox sin alterar tus datos",
                        onClick = { SpeedRecordTracker.triggerTestAlert(isAllTime = false) }
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.EmojiEvents,
                        title = "Probar Logro — Récord Histórico",
                        subtitle = "Muestra notificación dorada de récord histórico",
                        onClick = { SpeedRecordTracker.triggerTestAlert(isAllTime = true) }
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }

    if (showSpeedoCropper && pendingCropUri != null) {
        ImageCropperDialog(
            imageUri = pendingCropUri!!,
            outputFileName = "custom_speedo_bg.png",
            initialCropShape = CropShape.CIRCLE,
            onCropped = { path ->
                AppSettings.setCustomSpeedoBgPath(path)
                AppSettings.setCustomSpeedoBgUri(path)
                showSpeedoCropper = false
                pendingCropUri = null
            },
            onDismiss = {
                showSpeedoCropper = false
                pendingCropUri = null
            }
        )
    }

    if (showCarPhotoCropper && pendingCropUri != null) {
        val uniqueFileName = remember(pendingCropUri) { "custom_home_car_${System.currentTimeMillis()}.png" }
        ImageCropperDialog(
            imageUri = pendingCropUri!!,
            outputFileName = uniqueFileName,
            initialCropShape = CropShape.WIDE,
            onCropped = { path ->
                // Limpiar recortes anteriores del auto para no acumular archivos
                try {
                    context.filesDir.listFiles { f ->
                        f.name.startsWith("custom_home_car") && f.absolutePath != path
                    }?.forEach { it.delete() }
                } catch (_: Exception) {}
                AppSettings.setCustomHomeCarPhotoPath(path)
                showCarPhotoCropper = false
                pendingCropUri = null
            },
            onDismiss = {
                showCarPhotoCropper = false
                pendingCropUri = null
            }
        )
    }
}

@Composable
fun RouteHistoryScreen() {
    val scope = rememberCoroutineScope()
    var allRoutes by remember { mutableStateOf<List<com.tuusuario.carlauncher.services.DailyRoute>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val routes = remember(allRoutes) { allRoutes.filter { !it.isDeleted } }
    val availableDates = remember(routes) { routes.map { it.date }.distinct().sorted() }

    var selectedDateIndex by remember { mutableStateOf(0) }
    var showingTrash by remember { mutableStateOf(false) }
    val activeUiColor = Color(AppSettings.uiColor.value)

    fun refreshRoutesAsync(selectLatest: Boolean = false) {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                RouteTracker.getAllRoutes()
            }
            allRoutes = loaded
            if (selectLatest) {
                val activeDates = loaded.filter { !it.isDeleted }.map { it.date }.distinct().sorted()
                selectedDateIndex = activeDates.lastIndex.coerceAtLeast(0)
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshRoutesAsync(selectLatest = true)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.History,
                    null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    if (showingTrash) "Papelera de Rutas" else "Historial de Rutas",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            IconButton(onClick = { showingTrash = !showingTrash }) {
                Icon(
                    if (showingTrash) Icons.Default.List else Icons.Default.DeleteOutline,
                    "Alternar Papelera",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = activeUiColor)
            }
        } else if (showingTrash) {
            val trashRoutes = allRoutes.filter { it.isDeleted }
            if (trashRoutes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(60.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("La papelera está vacía", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(trashRoutes.size) { i ->
                        val route = trashRoutes[i]
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Map, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(30.dp))
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Ruta del ${route.date}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    val totalPts = route.segments.sumOf { it.points.size }
                                    Text(
                                        "$totalPts puntos · ${route.segments.size} segmentos",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            route.isDeleted = false
                                            route.deletedAt = null
                                            RouteTracker.saveRoute(route)
                                        }
                                        refreshRoutesAsync()
                                    }
                                }) {
                                    Icon(Icons.Default.Restore, "Restaurar", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (availableDates.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.History,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(60.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("No hay rutas registradas", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "Conduce al menos 10 metros para empezar",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                val safeIndex = selectedDateIndex.coerceIn(0, availableDates.lastIndex)
                val currentDate = availableDates[safeIndex]
                val currentRoute = routes.find { it.date == currentDate }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { if (safeIndex > 0) selectedDateIndex = safeIndex - 1 },
                            enabled = safeIndex > 0
                        ) {
                            Icon(
                                Icons.Default.ChevronLeft,
                                "Día anterior",
                                tint = if (safeIndex > 0) activeUiColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(currentDate, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = activeUiColor)
                            currentRoute?.let { r ->
                                val totalPts = r.segments.sumOf { it.points.size }
                                Text(
                                    "${r.segments.size} segmentos · $totalPts puntos",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = { if (safeIndex < availableDates.lastIndex) selectedDateIndex = safeIndex + 1 },
                            enabled = safeIndex < availableDates.lastIndex
                        ) {
                            Icon(
                                Icons.Default.ChevronRight,
                                "Día siguiente",
                                tint = if (safeIndex < availableDates.lastIndex) activeUiColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }

                if (currentRoute != null && currentRoute.segments.isNotEmpty()) {
                    val reversedSegments = currentRoute.segments.reversed()

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        NavigationState.selectedHistorySegment.value = null
                                        NavigationState.speedRecordPin.value = null
                                        NavigationState.selectedHistoryRoute.value = currentRoute
                                    },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = activeUiColor.copy(alpha = 0.14f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(activeUiColor.copy(alpha = 0.22f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Map, null, tint = activeUiColor, modifier = Modifier.size(24.dp))
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Resumen de todo el día", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = activeUiColor)
                                        Text("Ver recorrido completo en el mapa", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.ChevronRight, null, tint = activeUiColor)
                                }
                            }
                        }

                        items(reversedSegments.size) { segIndex ->
                            val segment = reversedSegments[segIndex]
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        NavigationState.selectedHistorySegment.value = segment
                                        NavigationState.selectedHistoryRoute.value = currentRoute
                                        NavigationState.speedRecordPin.value = null
                                    },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(activeUiColor.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Timeline, null, tint = activeUiColor, modifier = Modifier.size(22.dp))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${segment.startTime} — ${segment.endTime}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text("${segment.points.size} puntos GPS", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("${segment.points.size}", fontWeight = FontWeight.Black, fontSize = 20.sp, color = activeUiColor)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(Icons.Default.ChevronRight, null, tint = activeUiColor.copy(alpha = 0.6f))
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            currentRoute.isDeleted = true
                                            currentRoute.deletedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                            RouteTracker.saveRoute(currentRoute)
                                        }
                                        selectedDateIndex = (selectedDateIndex - 1).coerceAtLeast(0)
                                        refreshRoutesAsync()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                            ) {
                                Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Mover este día a la papelera")
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Sin datos para este día", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ── Componentes Auxiliares de Settings ──

@Composable
fun SettingsGroupCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
        )
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.animateContentSize(animationSpec = tween(240))) { content() }
        }
    }
}

@Composable
fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isExpanded: Boolean = false,
    onClick: (() -> Unit)? = null,
    expandedContent: (@Composable () -> Unit)? = null
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expandedContent != null) {
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    "Expandir",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            } else if (onClick != null) {
                Icon(Icons.Default.ChevronRight, "Ir", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            }
        }
        if (expandedContent != null) {
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(180)) + expandVertically(tween(240)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(200))
            ) {
                Box(modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 12.dp)) { expandedContent() }
            }
        }
    }
}

@Composable
fun SettingsRowSwitch(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    )
}

@Composable
fun ColorPicker(selectedColor: Int, onColorSelected: (Int) -> Unit) {
    val colors = listOf(
        Color(0xFF1A6BFF),
        Color(0xFF007AFF),
        Color(0xFFE91E63),
        Color(0xFF9C27B0),
        Color(0xFF10B981),
        Color(0xFFFF5722),
        Color(0xFF00BCD4),
        Color(0xFFFFC107),
        Color(0xFFFFA500),
        Color(0xFF334155)
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        if (selectedColor == color.toArgb()) 3.dp else 1.dp,
                        if (selectedColor == color.toArgb()) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        CircleShape
                    )
                    .clickable { onColorSelected(color.toArgb()) }
            )
        }
    }
}

@Composable
fun OfflineMapDownloader(context: Context, coroutineScope: kotlinx.coroutines.CoroutineScope) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Mapbox gestiona automáticamente el caché vectorial y satelital 3D en almacenamiento local. Las zonas recorridas quedan guardadas automáticamente.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = { Toast.makeText(context, "Caché Inteligente Mapbox Activo ✓", Toast.LENGTH_SHORT).show() }) {
            Icon(Icons.Default.CloudDone, null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Caché Inteligente Activo", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun RouteMapFloatingDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val historyRoute = NavigationState.selectedHistoryRoute.value
    val historySegment = NavigationState.selectedHistorySegment.value
    val dashcamRoute = NavigationState.selectedDashcamRoute.value

    val routePoints: List<Point> = remember(historyRoute, historySegment, dashcamRoute) {
        when {
            historySegment != null -> historySegment.points.map { Point.fromLngLat(it.lon, it.lat) }
            historyRoute != null -> historyRoute.segments.flatMap { seg -> seg.points.map { Point.fromLngLat(it.lon, it.lat) } }
            dashcamRoute != null -> dashcamRoute.map { Point.fromLngLat(it.lon, it.lat) }
            else -> emptyList()
        }
    }

    val routeColorHex = if (historyRoute != null) "#10B981" else "#F59E0B"
    val speedRecordPinPoint = NavigationState.speedRecordPin.value

    val routeTitle = when {
        historySegment != null -> "Segmento ${historySegment.startTime}—${historySegment.endTime} · ${historySegment.points.size} puntos"
        historyRoute != null -> "Ruta del ${historyRoute.date} · ${historyRoute.segments.sumOf { it.points.size }} puntos"
        dashcamRoute != null -> "Ruta de Video · ${dashcamRoute.size} puntos"
        else -> ""
    }

    val dialogMapView = remember {
        MapView(context, MapInitOptions(context = context, textureView = true))
    }

    DisposableEffect(Unit) {
        dialogMapView.onStart()
        onDispose {
            dialogMapView.onStop()
            dialogMapView.onDestroy()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        dialogMapView.apply {
                            val styleToUse = if (AppSettings.isDarkMode.value) Style.DARK else Style.MAPBOX_STREETS
                            mapboxMap.loadStyle(styleToUse) { style ->
                                style.addSource(geoJsonSource("dialog-route-src") {
                                    if (routePoints.size >= 2) {
                                        featureCollection(
                                            FeatureCollection.fromFeature(
                                                Feature.fromGeometry(LineString.fromLngLats(routePoints))
                                            )
                                        )
                                    } else {
                                        featureCollection(FeatureCollection.fromFeatures(emptyList()))
                                    }
                                })
                                style.addLayer(lineLayer("dialog-route-layer", "dialog-route-src") {
                                    lineColor(routeColorHex)
                                    lineWidth(5.5)
                                    lineCap(LineCap.ROUND)
                                    lineJoin(LineJoin.ROUND)
                                })

                                val pm = annotations.createPointAnnotationManager()
                                if (routePoints.isNotEmpty()) {
                                    val startPin = com.tuusuario.carlauncher.ui.map.drawCustomPin(android.graphics.Color.parseColor("#10B981"))
                                    pm.create(PointAnnotationOptions().withPoint(routePoints.first()).withIconImage(startPin))

                                    if (routePoints.size > 1) {
                                        val endPin = com.tuusuario.carlauncher.ui.map.drawCustomPin(android.graphics.Color.parseColor("#EF4444"))
                                        pm.create(PointAnnotationOptions().withPoint(routePoints.last()).withIconImage(endPin))
                                    }
                                }

                                speedRecordPinPoint?.let { recPin ->
                                    val goldPin = com.tuusuario.carlauncher.ui.map.drawCustomPin(android.graphics.Color.parseColor("#FFD700"))
                                    pm.create(PointAnnotationOptions().withPoint(recPin).withIconImage(goldPin))
                                }

                                val allPoints = if (speedRecordPinPoint != null) routePoints + speedRecordPinPoint else routePoints
                                if (allPoints.isNotEmpty()) {
                                    val centerLng = (allPoints.minOf { it.longitude() } + allPoints.maxOf { it.longitude() }) / 2.0
                                    val centerLat = (allPoints.minOf { it.latitude() } + allPoints.maxOf { it.latitude() }) / 2.0
                                    mapboxMap.setCamera(cameraOptions {
                                        center(Point.fromLngLat(centerLng, centerLat))
                                        zoom(14.2)
                                        pitch(0.0)
                                    })
                                }
                            }
                        }
                    }
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                        .align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Map, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(routeTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Cerrar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashcamPreviewOverlay() {
    val preview = DashcamManager.activePreview.value ?: return

    var offsetX by remember { mutableStateOf(36f) }
    var offsetY by remember { mutableStateOf(96f) }

    Card(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
            .width(210.dp)
            .height(150.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(10.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).also { pv ->
                        pv.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        pv.scaleType = PreviewView.ScaleType.FILL_CENTER
                        preview.setSurfaceProvider(pv.surfaceProvider)
                    }
                }
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(Color(0xFFE53935).copy(alpha = 0.9f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(7.dp).background(Color.White, CircleShape))
                Spacer(modifier = Modifier.width(5.dp))
                Text("REC", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Contenedor flotante inteligente para YouTube:
 *  - Modo "MINI": ventana arrastrable sobre el Mapa 3D para ver video y navegación simultáneamente,
 *    con selector de tamaño y botón directo para pasar a Pantalla Completa.
 *  - Modo "FULL": ocupa toda la pantalla con botón directo para volver a ventana pequeña sobre el Mapa.
 * Usa `movableContentOf` para que el WebView jamás se recargue ni corte el audio/video al cambiar de tamaño.
 */
@Composable
fun YouTubeOverlayHost(
    mode: String,
    isLandscape: Boolean,
    youtubeContent: @Composable () -> Unit,
    onExpandFull: () -> Unit,
    onShrinkMini: () -> Unit,
    onClose: () -> Unit
) {
    if (mode == "NONE") return

    val palette = LocalBentoPalette.current
    var sizeStep by rememberSaveable { mutableStateOf(1) } // 0 = Compacto, 1 = Mediano, 2 = Grande
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    if (mode == "FULL") {
        BackHandler(onBack = onShrinkMini)

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0A0E17),
            shadowElevation = 18.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Barra superior de Pantalla Completa con opción directa a Ventana Pequeña + Mapa
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .background(Color(0xFF101726))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        onClick = onShrinkMini,
                        shape = RoundedCornerShape(14.dp),
                        color = palette.accent,
                        modifier = Modifier.height(38.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureInPictureAlt,
                                contentDescription = "Modo ventana pequeña",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                            Text(
                                text = "Ventana Pequeña · Ver Mapa",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OndemandVideo,
                            contentDescription = null,
                            tint = Color(0xFFFF3D00),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "YouTube · Pantalla Completa",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        onClick = onClose,
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar YouTube",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    youtubeContent()
                }
            }
        }
    } else {
        // Modo "MINI": ventana flotante arrastrable sobre el área del mapa
        val miniWidth = when (sizeStep) {
            0 -> 288.dp
            2 -> 436.dp
            else -> 356.dp
        }
        val miniHeight = when (sizeStep) {
            0 -> 196.dp
            2 -> 280.dp
            else -> 234.dp
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = if (isLandscape) 96.dp else 12.dp,
                    top = 74.dp,
                    end = 12.dp,
                    bottom = 12.dp
                )
        ) {
            val clampedWidth = miniWidth.coerceAtMost(maxWidth)
            val clampedHeight = miniHeight.coerceAtMost(maxHeight)

            Surface(
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .width(clampedWidth)
                    .height(clampedHeight),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF0A0E17),
                shadowElevation = 16.dp,
                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.22f))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Barra superior arrastrable + botones Tamaño / Pantalla Completa / Cerrar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .background(Color(0xFF11192B))
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    offsetX += dragAmount.x
                                    offsetY += dragAmount.y
                                }
                            }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DragIndicator,
                                contentDescription = "Mover ventana",
                                tint = Color.White.copy(alpha = 0.65f),
                                modifier = Modifier.size(18.dp)
                            )
                            Icon(
                                imageVector = Icons.Default.OndemandVideo,
                                contentDescription = null,
                                tint = Color(0xFFFF3D00),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "YouTube",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Cambiar tamaño de mini-ventana (Compacto / Mediano / Grande)
                            Surface(
                                onClick = { sizeStep = (sizeStep + 1) % 3 },
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.12f),
                                modifier = Modifier.size(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoSizeSelectLarge,
                                        contentDescription = "Cambiar tamaño",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Botón para expandir a Pantalla Completa
                            Surface(
                                onClick = onExpandFull,
                                shape = RoundedCornerShape(10.dp),
                                color = palette.accent,
                                modifier = Modifier.height(30.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInFull,
                                        contentDescription = "Pantalla completa",
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "Ampliar",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Botón Cerrar
                            Surface(
                                onClick = onClose,
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFE53935).copy(alpha = 0.85f),
                                modifier = Modifier.size(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cerrar",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        youtubeContent()
                    }
                }
            }
        }
    }
}

@Composable
fun CinematicWelcomeScreen(
    accentColor: Color,
    settingsReady: Boolean = false,
    mapReady: Boolean = false,
    gpsReady: Boolean = false
) {
    var time by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { nanos ->
                val dt = (nanos - lastNanos) / 1_000_000_000f
                lastNanos = nanos
                time += dt
            }
        }
    }

    val alpha = (time / 0.35f).coerceIn(0f, 1f)
    val ringAngle = time * 110f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070B14)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(220.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accentColor.copy(alpha = 0.28f * alpha), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = 110f
                ),
                radius = 110f,
                center = Offset(cx, cy)
            )
            rotate(ringAngle, Offset(cx, cy)) {
                drawArc(
                    color = accentColor.copy(alpha = 0.75f * alpha),
                    startAngle = 0f,
                    sweepAngle = 110f,
                    useCenter = false,
                    style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                )
                drawArc(
                    color = accentColor.copy(alpha = 0.35f * alpha),
                    startAngle = 180f,
                    sweepAngle = 90f,
                    useCenter = false,
                    style = Stroke(width = 2f, cap = StrokeCap.Round)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(alpha)
        ) {
            Icon(
                Icons.Default.DirectionsCar,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "${AppSettings.carBrandName.value} · ${AppSettings.carModelName.value}",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (mapReady && settingsReady) "SISTEMA LISTO" else "INICIANDO SISTEMA...",
                color = accentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.5.sp
            )
        }
    }
}

private fun Modifier.alpha(value: Float): Modifier =
    this.modifierAlpha(value.coerceIn(0f, 1f))
