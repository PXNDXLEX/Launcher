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
import androidx.compose.runtime.mutableStateOf

object DashcamManager {
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var cameraExecutor: ExecutorService? = null
    
    var isRecording = mutableStateOf(false)
    var currentVideoId: String? = null

    fun getVideosDir(): File {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "CarLauncher/Videos")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    @SuppressLint("MissingPermission")
    fun startRecording(context: Context, lifecycleOwner: LifecycleOwner) {
        if (isRecording.value) return
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                
                val qualitySelector = QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.HD))
                val recorder = Recorder.Builder()
                    .setExecutor(cameraExecutor!!)
                    .setQualitySelector(qualitySelector)
                    .build()
                    
                videoCapture = VideoCapture.withOutput(recorder)

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, videoCapture)
                
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
                                
                                // Detener automáticamente a los 4 minutos
                                CoroutineScope(Dispatchers.Main).launch {
                                    delay(4 * 60 * 1000L) // 4 min
                                    if (isRecording.value) stopRecording()
                                }
                            }
                            is VideoRecordEvent.Finalize -> {
                                isRecording.value = false
                                currentVideoId = null
                                DashcamRouteTracker.stopSession()
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e("Dashcam", "Error binding camera", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stopRecording() {
        recording?.stop()
        recording = null
        cameraExecutor?.shutdown()
    }
}
