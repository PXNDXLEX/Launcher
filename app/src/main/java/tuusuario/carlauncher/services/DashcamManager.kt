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
import androidx.camera.camera2.interop.Camera2CameraInfo
import android.hardware.camera2.CameraCharacteristics
import androidx.camera.core.CameraEffect
import androidx.camera.effects.OverlayEffect
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.tuusuario.carlauncher.ui.NavigationState
import androidx.camera.core.UseCaseGroup
import android.os.Handler
import android.os.Looper

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

                // --- CONFIGURACIÓN DE MARCA DE AGUA ---
                val paint = Paint().apply {
                    color = Color.WHITE
                    textSize = 34f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    setShadowLayer(3f, 2f, 2f, Color.BLACK)
                }
                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

                val watermarkEffect = OverlayEffect(
                    CameraEffect.VIDEO_CAPTURE,
                    10, // Prioridad de la capa
                    Handler(Looper.getMainLooper())
                ) { frameInfo ->
                    val canvas = frameInfo.canvas
                    val width = canvas.width
                    val height = canvas.height
                    
                    val dateStr = sdf.format(Date())
                    val speedStr = String.format("%.0f km/h", NavigationState.currentSpeedKmH.value)
                    val fullText = "$dateStr | $speedStr"
                    
                    // Dibujar en la esquina inferior derecha
                    val textWidth = paint.measureText(fullText)
                    canvas.drawText(fullText, width - textWidth - 20f, height - 30f, paint)
                    
                    // Opcional: Nombre de la app en la otra esquina
                    canvas.drawText("CAR LAUNCHER DASHCAM", 20f, height - 30f, paint)
                }

                // Desvincula todo primero para asegurar estado limpio
                provider.unbindAll()
                
                val selector = getSelectedCameraSelector(provider)
                
                // Usamos UseCaseGroup para aplicar el efecto
                val useCaseGroup = UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(videoCapture!!)
                    .addEffect(watermarkEffect)
                    .build()

                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    useCaseGroup
                )

                // Wide Angle Lens Detection and Setup
                val minZoom = camera.cameraInfo.zoomState.value?.minZoomRatio ?: 1.0f
                hasWideAngleLens.value = minZoom < 1.0f
                camera.cameraControl.setZoomRatio(minZoom)

                val videoId = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val videoFile = File(getVideosDir(), "VID_${videoId}.mp4")
                val outputOptions = FileOutputOptions.Builder(videoFile).build()

                recording = videoCapture!!.output
                    .prepareRecording(context, outputOptions)
                    .withAudioEnabled()
                    .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                        when (recordEvent) {
                            is VideoRecordEvent.Start -> {
                                isRecording.value = true
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

                                // Guardar hora de fin real en el .ref.json
                                val vid = currentVideoId
                                if (vid != null) {
                                    val endTime = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
                                    writeRefJson(vid, endTime)
                                }

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
}
