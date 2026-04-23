package com.tuusuario.carlauncher.services

import android.annotation.SuppressLint
import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.view.PreviewView
import androidx.compose.runtime.mutableStateOf

object DashcamManager {
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var cameraExecutor: ExecutorService? = null
    private var autoStopJob: Job? = null
    private val isStopping = AtomicBoolean(false)

    // Scope dedicado con Job propio para poder cancelarlo limpiamente
    private var managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var isRecording = mutableStateOf(false)
    var currentVideoId: String? = null
    var activePreview = mutableStateOf<CameraPreview?>(null)

    fun getVideosDir(): File {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "CarLauncher/Videos")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    @SuppressLint("MissingPermission")
    fun startRecording(context: Context, lifecycleOwner: LifecycleOwner) {
        if (isRecording.value) return
        if (isStopping.get()) {
            // Si la parada todavía está en progreso, forzamos un reset para permitir una nueva grabación
            Log.w("Dashcam", "startRecording: reseteando isStopping previo")
            isStopping.set(false)
        }

        // Crear un executor fresco; nunca reutilizar uno ya cerrado
        cameraExecutor = Executors.newSingleThreadExecutor()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val executor = cameraExecutor
                if (executor == null || executor.isShutdown) {
                    Log.w("Dashcam", "Executor no disponible al intentar iniciar la cámara")
                    return@addListener
                }

                val cameraProvider = cameraProviderFuture.get()

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

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, videoCapture, preview)

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

                                // Auto-stop a los 4 minutos usando el scope del manager
                                autoStopJob?.cancel()
                                autoStopJob = managerScope.launch {
                                    delay(4 * 60 * 1000L)
                                    if (isRecording.value) stopRecording()
                                }
                            }
                            is VideoRecordEvent.Finalize -> {
                                isRecording.value = false
                                currentVideoId = null
                                activePreview.value = null
                                isStopping.set(false)  // Siempre resetear para permitir siguiente grabación
                                DashcamRouteTracker.stopSession()

                                // Apagar el executor en un hilo secundario para no bloquear el main
                                val executorToShutdown = cameraExecutor
                                cameraExecutor = null
                                executorToShutdown?.let {
                                    if (!it.isShutdown) {
                                        CoroutineScope(Dispatchers.IO).launch { it.shutdown() }
                                    }
                                }
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e("Dashcam", "Error binding camera", e)
                isStopping.set(false)
                // Limpiar executor si hubo error antes de conectar
                cameraExecutor?.let { if (!it.isShutdown) it.shutdown() }
                cameraExecutor = null
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stopRecording() {
        if (!isRecording.value && !isStopping.get()) return
        autoStopJob?.cancel()
        autoStopJob = null
        isStopping.set(true)
        recording?.stop()
        recording = null
        // El executor se cierra en el callback Finalize para evitar la RejectedExecutionException
    }

    /**
     * Llamar cuando la actividad/servicio que posee este manager se destruye definitivamente.
     * Cancela el scope para evitar fugas de coroutines.
     */
    fun release() {
        stopRecording()
        managerScope.cancel()
        // Reinicializar el scope para que pueda volver a usarse si la app sigue viva
        managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }
}
