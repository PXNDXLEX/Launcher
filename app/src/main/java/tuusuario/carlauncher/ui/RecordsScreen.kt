package com.tuusuario.carlauncher.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuusuario.carlauncher.services.RouteTracker
import com.tuusuario.carlauncher.services.SpeedRecord
import com.tuusuario.carlauncher.services.SpeedRecordTracker
import kotlin.math.sin
import kotlin.math.cos

@Composable
fun RecordsScreen() {
    val accentColor = Color(AppSettings.uiColor.value)
    val records     = remember { mutableStateOf(SpeedRecordTracker.getAllRecords()) }
    val allTime     = records.value.maxByOrNull { it.maxSpeedKmH }
    val todayRecord = SpeedRecordTracker.getTodayRecord()

    // Refrescar al entrar
    LaunchedEffect(Unit) {
        records.value = SpeedRecordTracker.getAllRecords()
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── HEADER ────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(
                        accentColor.copy(alpha = 0.18f),
                        accentColor.copy(alpha = 0.04f)
                    ))
                )
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.EmojiEvents, null,
                        tint = accentColor, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Récords de Velocidad",
                        fontWeight = FontWeight.Black, fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface)
                }
                Text("Tus marcas personales · ${records.value.size} días registrados",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, start = 38.dp))
            }
        }

        if (records.value.isEmpty()) {
            // Estado vacío
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Speed, null,
                        tint = accentColor.copy(alpha = 0.3f),
                        modifier = Modifier.size(72.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Sin récords aún",
                        fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    Text("Conduce para registrar tu primera marca",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 6.dp))
                }
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // ── TARJETA RÉCORD HISTÓRICO ──────────────────────────────────────
            allTime?.let { champion ->
                item {
                    AllTimeChampionCard(
                        record = champion,
                        accentColor = accentColor,
                        onClick = { openRouteForRecord(champion) }
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }

            // ── SECCIÓN: HOY ──────────────────────────────────────────────────
            todayRecord?.let { today ->
                item {
                    Text("  HOY", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = accentColor, letterSpacing = 2.sp)
                    Spacer(Modifier.height(6.dp))
                    RecordCard(
                        record    = today,
                        rank      = -1, // especial = "hoy"
                        isToday   = true,
                        isAllTime = today.date == allTime?.date && today.maxSpeedKmH == allTime.maxSpeedKmH,
                        accentColor = accentColor,
                        onClick   = { openRouteForRecord(today) }
                    )
                }
            }

            // ── SECCIÓN: HISTORIAL ────────────────────────────────────────────
            val historyRecords = records.value
                .filter { it.date != (todayRecord?.date) }
                .sortedByDescending { it.maxSpeedKmH }   // ordenado por velocidad

            if (historyRecords.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text("  HISTORIAL", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 2.sp)
                    Spacer(Modifier.height(6.dp))
                }
                itemsIndexed(historyRecords) { index, record ->
                    RecordCard(
                        record    = record,
                        rank      = index + 1,
                        isToday   = false,
                        isAllTime = record.date == allTime?.date && record.maxSpeedKmH == allTime.maxSpeedKmH,
                        accentColor = accentColor,
                        onClick   = { openRouteForRecord(record) }
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ── Abre la ruta en el mapa, marcando el punto exacto del récord ──────────────
private fun openRouteForRecord(record: SpeedRecord) {
    val route = RouteTracker.loadRoute(record.date)
    if (route != null) {
        val snippet = RouteTracker.extractWindowSegment(route, record.time, 30)
        NavigationState.selectedHistorySegment.value = snippet
        NavigationState.selectedHistoryRoute.value = route
    }
    // Pin exacto del récord (lat/lon del SpeedRecord)
    if (record.lat != 0.0 && record.lon != 0.0) {
        NavigationState.speedRecordPin.value = com.mapbox.geojson.Point.fromLngLat(record.lon, record.lat)
    } else {
        NavigationState.speedRecordPin.value = null
    }
}

// Mantener función antigua para compatibilidad
private fun openRouteForDate(date: String, timeStr: String) {
    val route = RouteTracker.loadRoute(date)
    if (route != null) {
        val snippet = RouteTracker.extractWindowSegment(route, timeStr, 30)
        NavigationState.selectedHistorySegment.value = snippet
        NavigationState.selectedHistoryRoute.value = route
    }
    NavigationState.speedRecordPin.value = null
}

// ── Tarjeta grande del campeón histórico ─────────────────────────────────────
@Composable
fun AllTimeChampionCard(record: SpeedRecord, accentColor: Color, onClick: () -> Unit) {
    // Animación de brillo giratorio
    val infiniteTransition = rememberInfiniteTransition(label = "championGlow")
    val glowAngle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "glowAngle"
    )
    val pulseFrac by infiniteTransition.animateFloat(
        initialValue = 0.7f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(
            containerColor = accentColor.copy(alpha = 0.10f)
        ),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, accentColor.copy(alpha = 0.35f * pulseFrac), RoundedCornerShape(20.dp))
        ) {
            // Brillo de fondo rotante
            Canvas(modifier = Modifier.matchParentSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val rad = Math.toRadians(glowAngle.toDouble())
                val gx = cx + cos(rad).toFloat() * size.width * 0.3f
                val gy = cy + sin(rad).toFloat() * size.height * 0.5f
                drawCircle(
                    brush  = Brush.radialGradient(
                        listOf(accentColor.copy(alpha = 0.12f), Color.Transparent),
                        Offset(gx, gy), size.width * 0.6f
                    ),
                    radius = size.width * 0.6f,
                    center = Offset(gx, gy)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Trofeo animado
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(accentColor.copy(alpha = 0.15f), CircleShape)
                        .border(2.dp, accentColor.copy(alpha = 0.5f * pulseFrac), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.EmojiEvents, null,
                        tint = accentColor, modifier = Modifier.size(34.dp))
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text("🏆  RÉCORD HISTÓRICO",
                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = accentColor, letterSpacing = 3.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${record.maxSpeedKmH.toInt()}",
                            fontSize = 52.sp, fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 52.sp)
                        Text(" km/h",
                            fontSize = 16.sp, fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 10.dp))
                    }
                    // Velocímetro visual de relleno
                    val fillFraction = (record.maxSpeedKmH / 200f).coerceIn(0f, 1f)
                    Box(
                        Modifier.fillMaxWidth().height(3.dp)
                            .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(2.dp))
                    ) {
                        Box(
                            Modifier.fillMaxHeight().fillMaxWidth(fillFraction)
                                .background(
                                    Brush.horizontalGradient(listOf(
                                        accentColor.copy(0.5f), accentColor, Color.White.copy(0.8f)
                                    )),
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarToday, null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(formatDateLabel(record.date),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Icon(Icons.Default.AccessTime, null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(record.time.take(5),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (record.locationLabel.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, null,
                                modifier = Modifier.size(12.dp),
                                tint = accentColor.copy(alpha = 0.7f))
                            Spacer(Modifier.width(4.dp))
                            Text(record.locationLabel,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    // Hint: toca para ver el punto exacto
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Map, null,
                            modifier = Modifier.size(11.dp),
                            tint = accentColor.copy(alpha = 0.5f))
                        Spacer(Modifier.width(4.dp))
                        Text("Toca para ver el punto exacto en el mapa",
                            fontSize = 9.sp,
                            color = accentColor.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// ── Tarjeta de récord individual ─────────────────────────────────────────────
@Composable
fun RecordCard(
    record:      SpeedRecord,
    rank:        Int,       // -1 = HOY especial, 1+ = posición en historial
    isToday:     Boolean,
    isAllTime:   Boolean,
    accentColor: Color,
    onClick:     () -> Unit
) {
    val medal = when (rank) {
        -1   -> if (isAllTime) "🏆" else "📅"
        1    -> "🥇"
        2    -> "🥈"
        3    -> "🥉"
        else -> "  #$rank"
    }
    val cardBg = when {
        isToday   -> accentColor.copy(alpha = 0.07f)
        isAllTime -> accentColor.copy(alpha = 0.09f)
        else      -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    val borderAlpha = if (isToday || isAllTime) 0.3f else 0.0f
    val fillFraction = (record.maxSpeedKmH / 200f).coerceIn(0f, 1f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(if (isToday) 4.dp else 1.dp)
    ) {
        Box(
            Modifier.border(
                if (isToday || isAllTime) 1.dp else 0.dp,
                accentColor.copy(alpha = borderAlpha),
                RoundedCornerShape(16.dp)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Medalla / posición
                Text(medal, fontSize = 22.sp, modifier = Modifier.width(36.dp),
                    textAlign = TextAlign.Center)

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            formatDateLabel(record.date),
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                            fontSize   = 14.sp,
                            color      = if (isToday) accentColor else MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "${record.maxSpeedKmH.toInt()}",
                                fontSize   = 26.sp,
                                fontWeight = FontWeight.Black,
                                color      = if (isAllTime) accentColor else MaterialTheme.colorScheme.onSurface
                            )
                            Text(" km/h",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp))
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Barra de velocidad relativa
                    Box(
                        Modifier.fillMaxWidth().height(2.dp)
                            .background(accentColor.copy(alpha = 0.10f), RoundedCornerShape(1.dp))
                    ) {
                        Box(
                            Modifier.fillMaxHeight().fillMaxWidth(fillFraction)
                                .background(
                                    if (isAllTime)
                                        Brush.horizontalGradient(listOf(accentColor.copy(0.4f), accentColor))
                                    else
                                        Brush.horizontalGradient(listOf(
                                            MaterialTheme.colorScheme.onSurface.copy(0.2f),
                                            MaterialTheme.colorScheme.onSurface.copy(0.4f)
                                        )),
                                    RoundedCornerShape(1.dp)
                                )
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccessTime, null,
                            modifier = Modifier.size(11.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(3.dp))
                        Text(record.time.take(5), fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (record.locationLabel.isNotBlank()) {
                            Spacer(Modifier.width(10.dp))
                            Icon(Icons.Default.LocationOn, null,
                                modifier = Modifier.size(11.dp),
                                tint = accentColor.copy(alpha = 0.5f))
                            Spacer(Modifier.width(3.dp))
                            Text(record.locationLabel, fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f))
                        }
                        // Hint de tap si hay ruta disponible
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.Map, null,
                            modifier = Modifier.size(12.dp),
                            tint = accentColor.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}

// ── Overlay de alerta de récord estilo LOGRO DE XBOX ──────────────────────────
@Composable
fun RecordAlertOverlay() {
    val alert = SpeedRecordTracker.recordAlert.value ?: return
    val accentColor = Color(AppSettings.uiColor.value)
    val isAllTime = alert.type == com.tuusuario.carlauncher.services.RecordAlertType.ALL_TIME_RECORD

    // Colores del logro
    val goldColor = Color(0xFFFFD700)
    val darkBg = Color(0xFF0A0A0A)
    val borderColor = if (isAllTime) goldColor else accentColor

    // Animaciones
    val infiniteTransition = rememberInfiniteTransition(label = "xboxAchievement")
    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "shimmer"
    )
    val pulseBorder by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseBorder"
    )

    // Barra de progreso de auto-dismiss
    var dismissProgress by remember { mutableStateOf(1f) }
    LaunchedEffect(alert) {
        val totalMs = 6000L
        val startMs = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - startMs
            dismissProgress = 1f - (elapsed.toFloat() / totalMs).coerceIn(0f, 1f)
            if (elapsed >= totalMs) break
            kotlinx.coroutines.delay(50)
        }
        SpeedRecordTracker.dismissAlert()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Sombra/glow exterior
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            borderColor.copy(alpha = 0.3f * pulseBorder),
                            borderColor.copy(alpha = 0.1f),
                            borderColor.copy(alpha = 0.3f * pulseBorder)
                        )
                    )
                )
                .padding(2.dp)
        ) {
            // Cuerpo principal del logro
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(darkBg.copy(alpha = 0.95f))
                    .clickable { SpeedRecordTracker.dismissAlert() }
            ) {
                // Shimmer de fondo
                Canvas(modifier = Modifier.matchParentSize()) {
                    val shimX = shimmer * size.width * 1.5f - size.width * 0.25f
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                borderColor.copy(alpha = 0.06f),
                                Color.Transparent
                            ),
                            startX = shimX - 80f,
                            endX = shimX + 80f
                        )
                    )
                }

                Column {
                    // ── Franja superior tipo Xbox ──
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        borderColor.copy(alpha = 0.8f),
                                        borderColor,
                                        borderColor.copy(alpha = 0.8f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // ── Contenido principal ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Icono del logro (circulo con trofeo)
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            borderColor.copy(alpha = 0.25f),
                                            darkBg
                                        )
                                    ),
                                    CircleShape
                                )
                                .border(1.5.dp, borderColor.copy(alpha = 0.7f * pulseBorder), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (isAllTime) "🏆" else "⚡",
                                fontSize = 24.sp
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            // Etiqueta tipo "LOGRO DESBLOQUEADO"
                            Text(
                                if (isAllTime) "🎮 RÉCORD HISTÓRICO" else "🎮 RÉCORD DEL DÍA",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = borderColor,
                                letterSpacing = 2.sp
                            )
                            Spacer(Modifier.height(2.dp))
                            // Velocidad grande
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    "${alert.speedKmH.toInt()}",
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    lineHeight = 34.sp
                                )
                                Text(
                                    " km/h",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(bottom = 5.dp)
                                )
                            }
                            if (alert.previousRecord > 0f) {
                                Text(
                                    "Antes: ${alert.previousRecord.toInt()} km/h  +${(alert.speedKmH - alert.previousRecord).toInt()} km/h",
                                    fontSize = 10.sp,
                                    color = borderColor.copy(alpha = 0.75f)
                                )
                            } else {
                                Text(
                                    "¡Primera marca del día!",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }

                        // Puntos "G" estilo Xbox
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (isAllTime) "100" else "50",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = borderColor
                            )
                            Text(
                                "G",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = borderColor.copy(alpha = 0.7f)
                            )
                        }
                    }

                    // ── Barra de auto-dismiss (progreso) ──
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(Color.White.copy(alpha = 0.06f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(dismissProgress)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(borderColor.copy(alpha = 0.4f), borderColor)
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────
private fun formatDateLabel(date: String): String {
    return try {
        val ld = java.time.LocalDate.parse(date)
        val today    = java.time.LocalDate.now()
        val yesterday = today.minusDays(1)
        when (ld) {
            today     -> "Hoy"
            yesterday -> "Ayer"
            else      -> ld.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM yyyy",
                java.util.Locale("es")))
        }
    } catch (e: Exception) { date }
}
