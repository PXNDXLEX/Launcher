package com.tuusuario.carlauncher.ui.widgets

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun DashcamGalleryDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var videos by remember { mutableStateOf<List<File>>(emptyList()) }
    var addresses by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loadingAddressFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val dir = DashcamManager.getVideosDir()
        videos = dir.listFiles { file -> file.extension == "mp4" }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Galería Dashcam", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar", tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                }

                if (videos.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No hay videos grabados aún.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(videos.size) { i ->
                            val video = videos[i]
                            val videoId = video.nameWithoutExtension.removePrefix("VID_")
                            val address = addresses[videoId]

                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    // Reproducir video con intent nativo
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", video)
                                        setDataAndType(uri, "video/mp4")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(intent)
                                },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Movie, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(video.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        val sizeMb = video.length() / (1024 * 1024)
                                        Text("$sizeMb MB", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (address != null) {
                                            Text(address, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                                        }
                                    }

                                    if (address == null) {
                                        if (loadingAddressFor == videoId) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                        } else {
                                            TextButton(onClick = {
                                                loadingAddressFor = videoId
                                                scope.launch {
                                                    try {
                                                        // Leer JSON de metadatos
                                                        val metadataDir = File(DashcamManager.getVideosDir(), "Metadata")
                                                        val jsonFile = File(metadataDir, "VID_${videoId}.json")
                                                        if (jsonFile.exists()) {
                                                            val json = JSONObject(jsonFile.readText())
                                                            val pointsArray = json.getJSONArray("points")
                                                            val parsedPoints = mutableListOf<com.tuusuario.carlauncher.services.RoutePoint>()
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
                                                                    com.tuusuario.carlauncher.ui.NavigationState.selectedDashcamRoute.value = parsedPoints
                                                                    onDismiss()
                                                                } else {
                                                                    addresses = addresses + (videoId to "Sin datos GPS registrados")
                                                                }
                                                            }
                                                        } else {
                                                            addresses = addresses + (videoId to "No tiene metadatos de ruta")
                                                        }
                                                    } catch (e: Exception) {
                                                        addresses = addresses + (videoId to "Error al leer ruta")
                                                    }
                                                    loadingAddressFor = null
                                                }
                                            }) { Text("Ver Ruta en Mapa") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
