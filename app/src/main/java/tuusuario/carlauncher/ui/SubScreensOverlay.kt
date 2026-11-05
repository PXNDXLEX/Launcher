package com.tuusuario.carlauncher.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuusuario.carlauncher.services.AppLauncherRepository
import com.tuusuario.carlauncher.services.SpeedRecordTracker
import com.tuusuario.carlauncher.ui.widgets.DashcamGalleryScreen
import com.tuusuario.carlauncher.ui.widgets.SpeedometerWidget
import kotlin.math.roundToInt

/**
 * Contenedor fluido para las sub-ventanas (Velocímetro Completo 18 Estilos,
 * Cajón "Todas las apps", Videos Dashcam, Historial de Rutas, Récords y YouTube).
 */
@Composable
fun SubScreenWindow(
    screenKey: String,
    youtubeContent: @Composable () -> Unit,
    onBackToHome: () -> Unit,
    onOpenNavigation: () -> Unit,
    onOpenScreen: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val palette = LocalBentoPalette.current

    BackHandler(onBack = onBackToHome)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.canvasBg)
            .padding(12.dp)
    ) {
        // Barra Superior Unificada con Botón Regresar al Menú Principal
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(18.dp),
            color = palette.cardBg,
            shadowElevation = if (palette.isDark) 0.dp else 3.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onBackToHome)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = palette.accent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Inicio",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accent
                    )
                }

                Text(
                    text = when (screenKey) {
                        "SPEEDOMETER" -> "Velocímetro GPS · ${AppSettings.speedoStyle.value}"
                        "ALL_APPS" -> "Todas las aplicaciones"
                        "VIDEOS" -> "Galería Dashcam"
                        "RUTAS" -> "Historial de Rutas"
                        "RECORDS" -> "Récords de Velocidad"
                        "YOUTUBE" -> "YouTube Multimedia"
                        else -> ""
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.textPrimary
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TextButton(onClick = onOpenNavigation) {
                        Icon(Icons.Default.Navigation, null, modifier = Modifier.size(18.dp), tint = palette.accent)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mapa", fontWeight = FontWeight.Bold, color = palette.accent)
                    }
                    IconButton(onClick = onOpenSettings, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.Settings, "Ajustes", tint = palette.textSecondary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Área Principal de Contenido
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            shape = RoundedCornerShape(24.dp),
            color = if (screenKey == "YOUTUBE") Color.Black else palette.cardBg,
            shadowElevation = if (palette.isDark) 0.dp else 3.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder)
        ) {
            when (screenKey) {
                "SPEEDOMETER" -> FullSpeedometerShowcase(onOpenSettings = onOpenSettings)
                "ALL_APPS" -> AppDrawerGridScreen(
                    onOpenNavigation = onOpenNavigation,
                    onOpenScreen = onOpenScreen,
                    onOpenSettings = onOpenSettings
                )
                "VIDEOS" -> DashcamGalleryScreen()
                "RUTAS" -> RouteHistoryScreen()
                "RECORDS" -> RecordsScreen()
                "YOUTUBE" -> Box(modifier = Modifier.fillMaxSize()) { youtubeContent() }
            }
        }
    }
}

@Composable
private fun FullSpeedometerShowcase(onOpenSettings: () -> Unit) {
    val palette = LocalBentoPalette.current
    val styles = remember {
        listOf(
            "PREMIUM", "NEON", "RACING", "CYBER", "AURA", "VORTEX",
            "QUANTUM", "PULSAR", "PLASMA", "ANIME", "KAIJU", "OMNIMON",
            "SHONEN", "MECHA", "OVERDRIVE", "NEBULA", "DEMONIC", "CUSTOM"
        )
    }
    val activeStyle = AppSettings.speedoStyle.value
    val todayMax = SpeedRecordTracker.todayMaxLive.value
    val allTimeMax = SpeedRecordTracker.allTimeMaxLive.value
    var dragAccum by remember { mutableStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (palette.isDark) Color(0xFF0A0F1C) else palette.cardBg)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragAccum > 90f) {
                            val idx = styles.indexOf(AppSettings.speedoStyle.value)
                            val prev = if (idx <= 0) styles.lastIndex else idx - 1
                            AppSettings.setSpeedoStyle(styles[prev])
                        } else if (dragAccum < -90f) {
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
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Indicadores rápidos de récord del día e histórico
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = palette.subCardBg) {
                    Text(
                        text = "Máx Hoy: ${todayMax.roundToInt()} km/h",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accent,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Surface(shape = RoundedCornerShape(12.dp), color = palette.recordsTileBg) {
                    Text(
                        text = "Récord: ${allTimeMax.roundToInt()} km/h",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.recordsTileFg,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            TextButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Palette, null, modifier = Modifier.size(16.dp), tint = palette.textSecondary)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Personalizar", fontSize = 12.sp, color = palette.textSecondary)
            }
        }

        // Velocímetro central animado
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(targetState = activeStyle, animationSpec = tween(250), label = "fullSpeedoStyle") { _ ->
                SpeedometerWidget()
            }
        }

        // Barra horizontal deslizable de los 18 estilos
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(styles) { styleName ->
                val selected = styleName == activeStyle
                FilterChip(
                    selected = selected,
                    onClick = { AppSettings.setSpeedoStyle(styleName) },
                    label = {
                        Text(
                            text = styleName,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = palette.accent,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
    }
}

private data class InternalModuleItem(
    val title: String,
    val icon: ImageVector,
    val bg: Color,
    val fg: Color,
    val onClick: () -> Unit
)

@Composable
private fun AppDrawerGridScreen(
    onOpenNavigation: () -> Unit,
    onOpenScreen: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val palette = LocalBentoPalette.current
    val context = LocalContext.current
    val apps = AppLauncherRepository.installedApps.value
    val isLoading = AppLauncherRepository.isLoading.value
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        AppLauncherRepository.preload(context)
    }

    val internalModules = remember(palette) {
        listOf(
            InternalModuleItem("Navegación", Icons.Default.Explore, palette.routesTileBg, palette.accent, onOpenNavigation),
            InternalModuleItem("Velocímetro", Icons.Default.Speed, palette.routesTileBg, palette.accent) { onOpenScreen("SPEEDOMETER") },
            InternalModuleItem("YouTube", Icons.Default.OndemandVideo, Color(0xFFFFECEB), Color(0xFFE53935)) { onOpenScreen("YOUTUBE") },
            InternalModuleItem("Dashcam", Icons.Default.Videocam, palette.dashcamTileBg, palette.dashcamTileFg) { onOpenScreen("VIDEOS") },
            InternalModuleItem("Rutas", Icons.Default.Timeline, palette.routesTileBg, palette.routesTileFg) { onOpenScreen("RUTAS") },
            InternalModuleItem("Récords", Icons.Default.EmojiEvents, palette.recordsTileBg, palette.recordsTileFg) { onOpenScreen("RECORDS") },
            InternalModuleItem("Ajustes App", Icons.Default.Tune, palette.subCardBg, palette.textPrimary, onOpenSettings),
            InternalModuleItem("Ajustes Android", Icons.Default.SettingsApplications, palette.subCardBg, palette.textSecondary) {
                try {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {}
            }
        )
    }

    val filteredApps = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Buscador instantáneo + botón refrescar lista
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = palette.subCardBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, null, tint = palette.textSecondary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text("Buscar aplicación (Spotify, Radio, ZLink, Waze...)", fontSize = 13.sp, color = palette.textSecondary)
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 14.sp, color = palette.textPrimary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, null, tint = palette.textSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            IconButton(
                onClick = { AppLauncherRepository.preload(context, force = true) },
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.subCardBg)
            ) {
                Icon(Icons.Default.Refresh, "Actualizar apps", tint = palette.textPrimary, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 112.dp),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            if (query.isBlank()) {
                items(internalModules) { mod ->
                    Surface(
                        onClick = mod.onClick,
                        shape = RoundedCornerShape(18.dp),
                        color = palette.subCardBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(15.dp))
                                    .background(mod.bg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(mod.icon, null, tint = mod.fg, modifier = Modifier.size(26.dp))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = mod.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            items(filteredApps, key = { it.packageName }) { app ->
                Surface(
                    onClick = { AppLauncherRepository.launchApp(context, app.packageName) },
                    shape = RoundedCornerShape(18.dp),
                    color = palette.subCardBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (app.icon != null) {
                            Image(
                                bitmap = app.icon,
                                contentDescription = app.label,
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(13.dp)),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(palette.routesTileBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Android, null, tint = palette.accent, modifier = Modifier.size(28.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = app.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            if (isLoading && filteredApps.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = palette.accent, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}
