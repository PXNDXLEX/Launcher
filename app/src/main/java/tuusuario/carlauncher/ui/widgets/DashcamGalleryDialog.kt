package com.tuusuario.carlauncher.ui.widgets

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.tuusuario.carlauncher.services.DashcamManager
import com.tuusuario.carlauncher.services.RouteTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

// ── Pantalla completa (para tab en dashboard) ──
@Composable
fun DashcamGalleryScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var videos by remember { mutableStateOf<List<File>>(emptyList()) }
    // Mapa de videoId → info del .ref.json (coordenada de inicio + fecha/hora)
    var videoRefs by remember { mutableStateOf<Map<String, VideoRefInfo>>(emptyMap()) }
    var loadingRouteFor by remember { mutableStateOf<String?>(null) }

    // ── Estado de selección ──
    var selectionMode by remember { mutableStateOf(false) }
    var selectedVideos by remember { mutableStateOf(setOf<String>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun reloadVideos() {
        val dir = DashcamManager.getVideosDir()
        videos = dir.listFiles { file -> file.extension == "mp4" }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

        // Leer .ref.json de cada video para mostrar info inicial
        val refs = mutableMapOf<String, VideoRefInfo>()
        videos.forEach { video ->
            val videoId = video.nameWithoutExtension.removePrefix("VID_")
            val refFile = File(DashcamManager.getMetadataDir(), "VID_${videoId}.ref.json")
            if (refFile.exists()) {
                try {
                    val json = JSONObject(refFile.readText())
                    refs[videoId] = VideoRefInfo(
                        date = json.optString("date", ""),
                        startTime = json.optString("startTime", ""),
                        startLat = if (json.has("startLat")) json.getDouble("startLat") else null,
                        startLon = if (json.has("startLon")) json.getDouble("startLon") else null
                    )
                } catch (_: Exception) {}
            }
        }
        videoRefs = refs
    }

    fun deleteFiles(filePaths: Set<String>) {
        filePaths.forEach { path ->
            val videoFile = File(path)
            val videoId = videoFile.nameWithoutExtension.removePrefix("VID_")
            videoFile.delete()
            // Borrar el .ref.json asociado
            val refFile = File(DashcamManager.getMetadataDir(), "VID_${videoId}.ref.json")
            if (refFile.exists()) refFile.delete()
            // Borrar también el JSON legacy si existiera
            val legacyMeta = File(DashcamManager.getMetadataDir(), "VID_${videoId}.json")
            if (legacyMeta.exists()) legacyMeta.delete()
        }
        selectedVideos = emptySet()
        selectionMode = false
        reloadVideos()
    }

    fun shareVideo(video: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", video)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Compartir video via..."))
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Error al compartir: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun useFallbackLocation(ref: VideoRefInfo) {
        val lat = ref.startLat
        val lon = ref.startLon
        if (lat != null && lon != null) {
            // Mostrar la coordenada de inicio como punto único en el mapa
            com.tuusuario.carlauncher.ui.NavigationState.selectedDashcamRoute.value =
                listOf(com.tuusuario.carlauncher.services.RoutePoint(lat, lon, ref.startTime))
        } else {
            android.widget.Toast.makeText(
                context,
                "Sin coordenada GPS para este video",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(Unit) { reloadVideos() }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    Icons.Default.VideoLibrary, null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        if (selectionMode) "${selectedVideos.size} seleccionados" else "Galería Dashcam",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (videos.isNotEmpty()) {
                        Text(
                            "${videos.size} video${if (videos.size != 1) "s" else ""}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Row {
                if (selectionMode) {
                    val allSelected = selectedVideos.size == videos.size && videos.isNotEmpty()
                    TextButton(
                        onClick = {
                            if (allSelected) selectedVideos = emptySet()
                            else { selectedVideos = videos.map { it.absolutePath }.toSet() }
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text(if (allSelected) "Ninguno" else "Todos", fontSize = 13.sp)
                    }
                    IconButton(
                        onClick = { if (selectedVideos.isNotEmpty()) showDeleteConfirm = true },
                        enabled = selectedVideos.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            "Borrar seleccionados",
                            tint = if (selectedVideos.isNotEmpty())
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
                        )
                    }
                    IconButton(onClick = { selectionMode = false; selectedVideos = emptySet() }) {
                        Icon(Icons.Default.Close, "Cancelar", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                } else if (videos.isNotEmpty()) {
                    IconButton(onClick = { selectionMode = true }) {
                        Icon(
                            Icons.Default.CheckBox,
                            "Seleccionar",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // ── Lista / Estado vacío ──
        if (videos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Videocam, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No hay videos grabados aún.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(videos.size) { i ->
                    val video = videos[i]
                    val isSelected = selectedVideos.contains(video.absolutePath)
                    val videoId = video.nameWithoutExtension.removePrefix("VID_")
                    val refInfo = videoRefs[videoId]

                    VideoCard(
                        video = video,
                        refInfo = refInfo,
                        isLoading = loadingRouteFor == videoId,
                        selectionMode = selectionMode,
                        isSelected = isSelected,
                        onPlayClick = {
                            if (selectionMode) {
                                if (isSelected) selectedVideos = selectedVideos - video.absolutePath
                                else selectedVideos = selectedVideos + video.absolutePath
                            } else {
                                try {
                                    val uri = FileProvider.getUriForFile(
                                        context, "${context.packageName}.provider", video
                                    )
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "video/mp4")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Error al abrir video: ${e.message}",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        onLongClick = {
                            selectionMode = true
                            selectedVideos = selectedVideos + video.absolutePath
                        },
                        onShowRouteClick = {
                            // Vincular con la ruta del historial usando fecha+hora del .ref.json
                            val ref = refInfo ?: return@VideoCard
                            if (ref.date.isEmpty() || ref.startTime.isEmpty()) return@VideoCard

                            loadingRouteFor = videoId
                            scope.launch {
                                try {
                                    val route = withContext(Dispatchers.IO) {
                                        RouteTracker.loadRoute(ref.date)
                                    }
                                    withContext(Dispatchers.Main) {
                                        if (route != null) {
                                            // Filtrar puntos del historial en el rango horario del video
                                            val allPoints = RouteTracker.getAllPoints(route)
                                            val startH = ref.startTime.substring(0, 2).toIntOrNull() ?: 0
                                            val startM = ref.startTime.substring(3, 5).toIntOrNull() ?: 0
                                            val startS = ref.startTime.substring(6, 8).toIntOrNull() ?: 0
                                            val startTotalSec = startH * 3600 + startM * 60 + startS
                                            val endTotalSec = startTotalSec + (4 * 60)

                                            val videoPoints = allPoints.filter { pt ->
                                                try {
                                                    val tH = pt.timestamp.substring(0, 2).toInt()
                                                    val tM = pt.timestamp.substring(3, 5).toInt()
                                                    val tS = pt.timestamp.substring(6, 8).toInt()
                                                    val tTotal = tH * 3600 + tM * 60 + tS
                                                    tTotal in startTotalSec..endTotalSec
                                                } catch (_: Exception) { false }
                                            }

                                            if (videoPoints.isNotEmpty()) {
                                                com.tuusuario.carlauncher.ui.NavigationState.selectedDashcamRoute.value = videoPoints
                                            } else {
                                                // No hay puntos en el historial para ese rango — usar coordenada de inicio del .ref.json
                                                useFallbackLocation(ref)
                                            }
                                        } else {
                                            // No hay ruta guardada para ese día — usar coordenada de inicio del .ref.json
                                            useFallbackLocation(ref)
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, "Error al leer ruta", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                                loadingRouteFor = null
                            }
                        },
                        onShareClick = { shareVideo(video) },
                        onDeleteClick = {
                            selectedVideos = setOf(video.absolutePath)
                            showDeleteConfirm = true
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    // ── Diálogo de confirmación de borrado ──
    if (showDeleteConfirm) {
        val count = selectedVideos.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false; if (!selectionMode) selectedVideos = emptySet() },
            icon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Borrar ${if (count == 1) "video" else "$count videos"}") },
            text = {
                Text(
                    if (count == 1)
                        "Se borrará el video y sus datos de forma permanente. Esta acción no se puede deshacer."
                    else
                        "Se borrarán $count videos y sus datos de forma permanente. Esta acción no se puede deshacer."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        deleteFiles(selectedVideos)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Borrar")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    if (!selectionMode) selectedVideos = emptySet()
                }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

// ── Modelo de datos del .ref.json ──
data class VideoRefInfo(
    val date: String,       // yyyy-MM-dd
    val startTime: String,  // HH:mm:ss
    val startLat: Double?,
    val startLon: Double?
) {
    fun hasLocation() = startLat != null && startLon != null

    fun locationText(): String {
        if (startLat == null || startLon == null) return ""
        return "📍 ${String.format("%.4f", startLat)}, ${String.format("%.4f", startLon)}"
    }
}

// ── Card de video ──
@Composable
private fun VideoCard(
    video: File,
    refInfo: VideoRefInfo?,
    isLoading: Boolean,
    selectionMode: Boolean,
    isSelected: Boolean,
    onPlayClick: () -> Unit,
    onLongClick: () -> Unit,
    onShowRouteClick: () -> Unit,
    onShareClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val cardColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        animationSpec = tween(200),
        label = "cardColor"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primary
        else
            Color.Transparent,
        animationSpec = tween(200),
        label = "borderColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onPlayClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox en modo selección / icono normal
            if (selectionMode) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check, null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
            } else {
                Icon(
                    Icons.Default.Movie, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
            }

            // Info del video
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    video.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val sizeMb = video.length() / (1024 * 1024)
                val lastMod = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(video.lastModified()))
                Text(
                    "$sizeMb MB  ·  $lastMod",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Mostrar coordenada de inicio si existe
                if (refInfo?.hasLocation() == true) {
                    Text(
                        refInfo.locationText(),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Acciones (ocultas en modo selección)
            if (!selectionMode) {
                // Botón Ver Ruta (solo si hay .ref.json con fecha)
                if (refInfo != null && refInfo.date.isNotEmpty()) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = onShowRouteClick, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("Ver Ruta", fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.width(2.dp))
                // Botón Compartir
                IconButton(
                    onClick = onShareClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Share, "Compartir",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                // Botón Borrar
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline, "Borrar",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ── Dialog (legacy, mantener por compatibilidad) ──
@Composable
fun DashcamGalleryDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Galería Dashcam", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar", tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                }
                DashcamGalleryScreen()
            }
        }
    }
}
