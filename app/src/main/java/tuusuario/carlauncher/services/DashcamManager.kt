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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import androidx.camera.core.Preview as CameraPreview

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

    fun getVideosDir(): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "CarLauncher/Videos"
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
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
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    videoCapture,
                    preview
                )

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
                                DashcamRouteTracker.startSession(videoId)
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
                                DashcamRouteTracker.stopSession()
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
