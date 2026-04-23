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
import com.tuusuario.carlauncher.services.DashcamManager
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
    var addresses by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loadingAddressFor by remember { mutableStateOf<String?>(null) }

    // ── Estado de selección ──
    var selectionMode by remember { mutableStateOf(false) }
    // Usamos mutableStateListOf para asegurar compatibilidad con versiones antiguas de Compose
    val selectedVideos = remember { mutableStateListOf<String>() } 
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun reloadVideos() {
        val dir = DashcamManager.getVideosDir()
        videos = dir.listFiles { file -> file.extension == "mp4" }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun deleteFiles(filePaths: List<String>) {
        filePaths.forEach { path ->
            val videoFile = File(path)
            val videoId = videoFile.nameWithoutExtension.removePrefix("VID_")
            // Borrar el video
            if (videoFile.exists()) videoFile.delete()
            // Borrar el JSON de metadatos asociado
            val metaFile = File(DashcamManager.getVideosDir(), "Metadata/VID_${videoId}.json")
            if (metaFile.exists()) metaFile.delete()
        }
        selectedVideos.clear()
        selectionMode = false
        reloadVideos()
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
                    // Seleccionar / deseleccionar todo
                    val allSelected = selectedVideos.size == videos.size && videos.isNotEmpty()
                    TextButton(
                        onClick = {
                            if (allSelected) {
                                selectedVideos.clear()
                            } else {
                                selectedVideos.clear()
                                selectedVideos.addAll(videos.map { it.absolutePath })
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text(if (allSelected) "Ninguno" else "Todos", fontSize = 13.sp)
                    }
                    // Borrar seleccionados
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
                    // Cancelar selección
                    IconButton(onClick = { selectionMode = false; selectedVideos.clear() }) {
                        Icon(Icons.Default.Close, "Cancelar", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                } else if (videos.isNotEmpty()) {
                    // Activar modo selección
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

                    VideoCard(
                        video = video,
                        address = addresses[video.nameWithoutExtension.removePrefix("VID_")],
                        isLoading = loadingAddressFor == video.nameWithoutExtension.removePrefix("VID_"),
                        selectionMode = selectionMode,
                        isSelected = isSelected,
                        onPlayClick = {
                            if (selectionMode) {
                                // En modo selección, el clic toggle-ea la selección
                                if (isSelected) {
                                    selectedVideos.remove(video.absolutePath)
                                } else {
                                    selectedVideos.add(video.absolutePath)
                                }
                            } else {
                                try {
                                    val uri = androidx.core.content.FileProvider.getUriForFile(
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
                            if (!selectedVideos.contains(video.absolutePath)) {
                                selectedVideos.add(video.absolutePath)
                            }
                        },
                        onShowRouteClick = {
                            val videoId = video.nameWithoutExtension.removePrefix("VID_")
                            loadingAddressFor = videoId
                            scope.launch {
                                try {
                                    val metadataDir = File(DashcamManager.getVideosDir(), "Metadata")
                                    val jsonFile = File(metadataDir, "VID_${videoId}.json")
                                    if (jsonFile.exists()) {
                                        val json = JSONObject(jsonFile.readText())
                                        val pointsArray = json.getJSONArray("points")
                                        val parsedPoints =
                                            mutableListOf<com.tuusuario.carlauncher.services.RoutePoint>()
                                        for (j in 0 until pointsArray.length()) {
                                            val pt = pointsArray.getJSONObject(j)
                                            parsedPoints.add(
                                                com.tuusuario.carlauncher.services.RoutePoint(
                                                    lat = pt.getDouble("lat"),
                                                    lon = pt.getDouble("lon"),
                                                    timestamp = pt.getString("timestamp")
                                                )
                                            )
                                        }
                                        withContext(Dispatchers.Main) {
                                            if (parsedPoints.isNotEmpty()) {
                                                com.tuusuario.carlauncher.ui.NavigationState.selectedDashcamRoute.value =
                                                    parsedPoints
                                            } else {
                                                addresses =
                                                    addresses + (videoId to "Sin datos GPS registrados")
                                            }
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            addresses = addresses + (videoId to "No tiene metadatos de ruta")
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        addresses = addresses + (videoId to "Error al leer ruta")
                                    }
                                }
                                loadingAddressFor = null
                            }
                        },
                        onDeleteClick = {
                            // Borrado rápido individual (sin modo selección)
                            selectedVideos.clear()
                            selectedVideos.add(video.absolutePath)
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
            onDismissRequest = { showDeleteConfirm = false; if (!selectionMode) selectedVideos.clear() },
            icon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Borrar ${if (count == 1) "video" else "$count videos"}") },
            text = {
                Text(
                    if (count == 1)
                        "Se borrará el video y sus datos de ruta GPS de forma permanente. Esta acción no se puede deshacer."
                    else
                        "Se borrarán $count videos y sus datos de ruta GPS de forma permanente. Esta acción no se puede deshacer."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        deleteFiles(selectedVideos.toList())
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
                    // Si era borrado individual fuera de selectionMode, limpiar
                    if (!selectionMode) selectedVideos.clear()
                }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

// ── Card de video con soporte de selección y borrado ──
@Composable
private fun VideoCard(
    video: File,
    address: String?,
    isLoading: Boolean,
    selectionMode: Boolean,
    isSelected: Boolean,
    onPlayClick: () -> Unit,
    onLongClick: () -> Unit,
    onShowRouteClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val cardColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        animationSpec = tween(200)
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primary
        else
            Color.Transparent,
        animationSpec = tween(200)
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
                if (address != null) {
                    Text(
                        address,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Acciones (ocultas en modo selección)
            if (!selectionMode) {
                if (address == null) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = onShowRouteClick, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("Ver Ruta", fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
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
