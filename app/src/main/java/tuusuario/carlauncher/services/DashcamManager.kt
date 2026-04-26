package com.tuusuario.carlauncher.services

import android.annotation.SuppressLint
import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import androidx.camera.core.Preview as CameraPreview
import com.tuusuario.carlauncher.ui.AppSettings
import com.tuusuario.carlauncher.ui.NavigationState
import androidx.camera.camera2.interop.Camera2CameraInfo
import android.hardware.camera2.CameraCharacteristics

object DashcamManager {
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var cameraProvider: ProcessCameraProvider? = null

    // Scope dedicado con Job propio para poder cancelarlo limpiamente
    private var managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var autoStopJob: Job? = null
    private val isStopping = AtomicBoolean(false)

    var isRecording = mutableStateOf(false)
    var currentVideoId: String? = null
    var activePreview = mutableStateOf<CameraPreview?>(null)
    var hasWideAngleLens = mutableStateOf(false)

    // Última coordenada GPS conocida (actualizada por DashcamRouteTracker en cada fix)
    var lastKnownLat: Double? = null
    var lastKnownLon: Double? = null

    // Indica si el .ref.json del video en curso ya tiene coordenada guardada
    private var refHasLocation: Boolean = false

    // Puntos para generar el archivo de subtítulos (.srt)
    private val videoPoints = mutableListOf<VideoPoint>()
    private var recordingStartTime: Long = 0

    data class VideoPoint(val elapsedMillis: Long, val timestamp: String, val speed: Double)

    fun updateLastKnownLocation(lat: Double, lon: Double) {
        lastKnownLat = lat
        lastKnownLon = lon

        // Si estamos grabando y el ref.json todavía no tiene coordenada, actualizarlo ahora
        val vid = currentVideoId
        if (isRecording.value && vid != null && !refHasLocation) {
            writeRefJson(vid)
        }
    }

    fun getVideosDir(): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "CarLauncher/Videos"
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getMetadataDir(): File {
        val dir = File(getVideosDir(), "Metadata")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun getSelectedCameraSelector(provider: ProcessCameraProvider): CameraSelector {
        val cameras = provider.availableCameraInfos
        if (cameras.isEmpty()) {
            return CameraSelector.DEFAULT_BACK_CAMERA
        }

        val safeIndex = AppSettings.dashcamCameraIndex.value % cameras.size
        val selectedInfo = cameras[safeIndex]
        
        return CameraSelector.Builder()
            .addCameraFilter { mutableListOf(selectedInfo) }
            .build()
    }
    
    fun cycleCamera(context: Context, lifecycleOwner: LifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val cameras = provider.availableCameraInfos
            val cameraCount = cameras.size
            
            if (cameraCount > 1) {
                val newIndex = (AppSettings.dashcamCameraIndex.value + 1) % cameraCount
                AppSettings.setDashcamCameraIndex(newIndex)
                
                val camInfo = cameras[newIndex]
                val facingStr = when(camInfo.lensFacing) {
                    CameraSelector.LENS_FACING_BACK -> "Trasera"
                    CameraSelector.LENS_FACING_FRONT -> "Frontal"
                    else -> "Externa/Desconocida"
                }
                
                android.widget.Toast.makeText(context, "Cámara ${newIndex + 1} de $cameraCount ($facingStr) seleccionada", android.widget.Toast.LENGTH_SHORT).show()
                
                if (isRecording.value) {
                    stopRecording()
                    // Start new recording after a brief delay to allow unbind
                    managerScope.launch {
                        delay(1000)
                        startRecording(context, lifecycleOwner)
                    }
                } else {
                    // Si no estaba grabando, arrancamos una prueba
                    startRecording(context, lifecycleOwner)
                }
            } else {
                android.widget.Toast.makeText(context, "Solo se detectó 1 cámara en el sistema", android.widget.Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Escribe un archivo .ref.json ligero vinculando el video con la ruta del historial.
     * Guarda la fecha, hora de inicio, hora de fin (si se proporciona) y coordenada inicial.
     */
    private fun writeRefJson(videoId: String, endTime: String? = null) {
        try {
            val date = "${videoId.substring(0, 4)}-${videoId.substring(4, 6)}-${videoId.substring(6, 8)}"
            val startTime = "${videoId.substring(9, 11)}:${videoId.substring(11, 13)}:${videoId.substring(13, 15)}"

            val json = JSONObject()
            json.put("videoId", videoId)
            json.put("date", date)
            json.put("startTime", startTime)

            if (endTime != null) {
                json.put("endTime", endTime)
            }

            val lat = lastKnownLat
            val lon = lastKnownLon
            if (lat != null && lon != null) {
                json.put("startLat", lat)
                json.put("startLon", lon)
                refHasLocation = true
            }

            val file = File(getMetadataDir(), "VID_${videoId}.ref.json")
            file.writeText(json.toString(2))
            Log.i("Dashcam", "Ref JSON: date=$date time=$startTime endTime=$endTime lat=$lat lon=$lon")
        } catch (e: Exception) {
            Log.e("Dashcam", "Error escribiendo ref.json", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun startRecording(context: Context, lifecycleOwner: LifecycleOwner) {
        if (isRecording.value) {
            Log.w("Dashcam", "startRecording ignorado: ya está grabando")
            return
        }
        // Reset explícito del flag de parada para permitir nueva grabación
        isStopping.set(false)

        // Obtener el proveedor de cámara (singleton) y arrancar
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                // Crear executor fresco cada vez
                val executor = Executors.newSingleThreadExecutor()

                val qualitySelector = QualitySelector.from(
                    Quality.HD,
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)
                )
                val recorder = Recorder.Builder()
                    .setExecutor(executor)
                    .setQualitySelector(qualitySelector)
                    .build()

                videoCapture = VideoCapture.withOutput(recorder)

                val preview = CameraPreview.Builder().build()
                activePreview.value = preview

                // Desvincula todo primero para asegurar estado limpio
                val videoId = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                currentVideoId = videoId
                refHasLocation = false
                videoPoints.clear()
                recordingStartTime = System.currentTimeMillis()

                provider.unbindAll()
                
                val selector = getSelectedCameraSelector(provider)
                
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    videoCapture,
                    preview
                )

                // Wide Angle Lens Detection and Setup
                val minZoom = camera.cameraInfo.zoomState.value?.minZoomRatio ?: 1.0f
                hasWideAngleLens.value = minZoom < 1.0f
                camera.cameraControl.setZoomRatio(minZoom)

                val videoFile = File(getVideosDir(), "VID_${videoId}.mp4")
                val outputOptions = FileOutputOptions.Builder(videoFile).build()

                recording = videoCapture!!.output
                    .prepareRecording(context, outputOptions)
                    .withAudioEnabled()
                    .start(ContextCompat.getMainExecutor(context)) { event ->
                        when (event) {
                            is VideoRecordEvent.Start -> {
                                isRecording.value = true
                                isStopping.set(false)
                                Log.i("Dashcam", "Grabación iniciada")

                                // Iniciar recolección periódica de puntos para el watermark (SRT)
                                managerScope.launch {
                                    while (isRecording.value) {
                                        val elapsed = System.currentTimeMillis() - recordingStartTime
                                        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
                                        videoPoints.add(VideoPoint(elapsed, timeStr, NavigationState.currentSpeedKmH.value))
                                        delay(1000)
                                    }
                                }
                                
                                currentVideoId = videoId
                                refHasLocation = false // Reset para intentar obtener coords

                                // Guardar referencia ligera vinculando video ↔ ruta del historial
                                // Si lastKnownLat/Lon son null, el ref se actualizará en el primer fix GPS
                                writeRefJson(videoId)

                                Log.i("Dashcam", "Grabación iniciada: $videoId")

                                // Auto-stop a los 4 minutos
                                autoStopJob?.cancel()
                                autoStopJob = managerScope.launch {
                                    delay(4 * 60 * 1000L)
                                    if (isRecording.value) stopRecording()
                                }
                            }
                            is VideoRecordEvent.Finalize -> {
                                Log.i("Dashcam", "Grabación finalizada")

                                // Guardar punto final para subtítulos
                                if (isRecording.value) {
                                    val elapsed = System.currentTimeMillis() - recordingStartTime
                                    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
                                    videoPoints.add(VideoPoint(elapsed, timeStr, NavigationState.currentSpeedKmH.value))
                                }

                                // Guardar hora de fin real en el .ref.json
                                val vid = currentVideoId
                                if (vid != null) {
                                    val endTime = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
                                    writeRefJson(vid, endTime)
                                    generateSrtFile(vid, videoPoints)
                                }

                                videoPoints.clear()
                                isRecording.value = false
                                currentVideoId = null
                                activePreview.value = null

                                // Desvincular la cámara para liberar el hardware
                                try {
                                    cameraProvider?.unbindAll()
                                } catch (e: Exception) {
                                    Log.e("Dashcam", "Error al unbindAll en Finalize", e)
                                }
                                cameraProvider = null
                                videoCapture = null

                                // Apagar el executor en background
                                CoroutineScope(Dispatchers.IO).launch {
                                    try { executor.shutdown() }
                                    catch (e: Exception) { Log.e("Dashcam", "Error shutdown executor", e) }
                                }

                                isStopping.set(false) // Listo para siguiente grabación
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e("Dashcam", "Error iniciando grabación", e)
                isRecording.value = false
                isStopping.set(false)
                try { cameraProvider?.unbindAll() } catch (_: Exception) {}
                cameraProvider = null
                videoCapture = null
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stopRecording() {
        if (!isRecording.value && !isStopping.get()) {
            Log.w("Dashcam", "stopRecording: no hay grabación activa")
            return
        }
        Log.i("Dashcam", "Deteniendo grabación...")
        autoStopJob?.cancel()
        autoStopJob = null
        isStopping.set(true)
        recording?.stop()
        recording = null
        // El unbindAll y limpieza se hace en VideoRecordEvent.Finalize
    }

    /**
     * Llamar cuando la actividad se destruye definitivamente.
     */
    fun release() {
        stopRecording()
        try { cameraProvider?.unbindAll() } catch (_: Exception) {}
        cameraProvider = null
        videoCapture = null
        managerScope.cancel()
        managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }
    private fun generateSrtFile(videoId: String?, points: List<VideoPoint>) {
        if (videoId == null || points.isEmpty()) return
        
        val srtFile = File(getVideosDir(), "VID_$videoId.srt")
        try {
            val writer = srtFile.bufferedWriter()
            points.forEachIndexed { index, point ->
                val nextPoint = points.getOrNull(index + 1)
                val startTime = formatSrtTime(point.elapsedMillis)
                val endTime = if (nextPoint != null) formatSrtTime(nextPoint.elapsedMillis) else formatSrtTime(point.elapsedMillis + 1000)
                
                writer.write("${index + 1}\n")
                writer.write("$startTime --> $endTime\n")
                writer.write("${point.timestamp} | ${String.format("%.0f", point.speed)} km/h\n\n")
            }
            writer.close()
            Log.d("DashcamManager", "SRT generated: ${srtFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("DashcamManager", "Error generating SRT: ${e.message}")
        }
    }

    private fun formatSrtTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        val ms = millis % 1000
        return String.format("%02d:%02d:%02d,%03d", h, m, s, ms)
    }
}
