package com.tuusuario.carlauncher.services

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LifecycleOwner
import com.mapbox.geojson.Point
import com.tuusuario.carlauncher.ui.AppSettings
import com.tuusuario.carlauncher.ui.NavigationState
import com.tuusuario.carlauncher.ui.map.FavoritesManager
import com.tuusuario.carlauncher.ui.map.PlaceResult
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener as VoskRecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

/**
 * Asistente de Voz Arlet
 *
 * Controla:
 * - Detección manos libres offline continua mediante Vosk ("Arlet, [comando]") sin bips de sistema
 * - Activación manual instantánea por botón
 * - Grabación de Dashcam (start/stop)
 * - Control de Música (pausa, play, siguiente, anterior)
 * - Navegación a lugares guardados con desambiguación inteligente
 * - Respuestas rápidas con voz femenina en español
 */
object ArletAssistant : TextToSpeech.OnInitListener {

    private const val TAG = "ArletAssistant"

    // Contexto y Lifecycle para activar la cámara
    @SuppressLint("StaticFieldLeak")
    var context: Context? = null
    var lifecycleOwner: LifecycleOwner? = null

    // Estados observables para la UI de Compose
    val isListening = mutableStateOf(false)
    val isSpeaking = mutableStateOf(false)
    val audioRms = mutableStateOf(0f)
    val lastSpokenQuery = mutableStateOf("")
    val assistantResponse = mutableStateOf("")
    val showOverlay = mutableStateOf(false)
    val isAwaitingDisambiguation = mutableStateOf(false)
    val disambiguationOptions = mutableStateListOf<PlaceResult>()
    val isVoskModelReady = mutableStateOf(false)

    // Control de hilos
    private val mainHandler = Handler(Looper.getMainLooper())

    // Vosk Offline Speech Engine (Silencioso, continuo, sin bips ni internet)
    private var voskModel: Model? = null
    private var voskSpeechService: SpeechService? = null
    private var isManualPushToTalk = false

    // Fallback Android SpeechRecognizer (solo si Vosk no está disponible o para toque manual)
    private var speechRecognizer: SpeechRecognizer? = null

    // Text To Speech
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    fun init(appContext: Context, owner: LifecycleOwner) {
        this.context = appContext.applicationContext
        this.lifecycleOwner = owner

        if (tts == null) {
            tts = TextToSpeech(appContext.applicationContext, this)
        }

        initVosk(appContext)
    }

    private fun initVosk(appContext: Context) {
        try {
            StorageService.unpack(
                appContext,
                "model-es",
                "model",
                { model: Model? ->
                    voskModel = model
                    isVoskModelReady.value = true
                    Log.i(TAG, "Vosk Model español cargado exitosamente en almacenamiento interno")
                    if (hasMicrophonePermission() && AppSettings.arletVoiceWakeEnabled.value) {
                        startContinuousListening()
                    }
                },
                { exception: IOException ->
                    Log.w(TAG, "Modelo Vosk offline no encontrado en assets o error al desempaquetar", exception)
                    isVoskModelReady.value = false
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando StorageService de Vosk", e)
            isVoskModelReady.value = false
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Configurar Español Latinoamericano Neutro
            val latamLocale = Locale("es", "419")
            val res419 = tts?.setLanguage(latamLocale)
            if (res419 == TextToSpeech.LANG_MISSING_DATA || res419 == TextToSpeech.LANG_NOT_SUPPORTED) {
                val mxLocale = Locale("es", "MX")
                val resMx = tts?.setLanguage(mxLocale)
                if (resMx == TextToSpeech.LANG_MISSING_DATA || resMx == TextToSpeech.LANG_NOT_SUPPORTED) {
                    val usLocale = Locale("es", "US")
                    val resUs = tts?.setLanguage(usLocale)
                    if (resUs == TextToSpeech.LANG_MISSING_DATA || resUs == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(Locale("es"))
                    }
                }
            }

            // Buscar voz femenina de español neutro / latinoamericano en el motor TTS
            try {
                val voices = tts?.voices
                val latamFemaleVoice = voices?.firstOrNull { voice ->
                    val lang = voice.locale.language
                    val country = voice.locale.country.uppercase(Locale.ROOT)
                    val name = voice.name.lowercase(Locale.ROOT)
                    val isEs = lang == "es"
                    val isLatam = country == "419" || country == "MX" || country == "US" || country == "CO" ||
                                  name.contains("419") || name.contains("mx") || name.contains("us") || name.contains("latam")
                    val isFemale = name.contains("female") || name.contains("fem") || name.contains("sfd") || voice.features.contains("gender=female")
                    isEs && isLatam && isFemale && !voice.isNetworkConnectionRequired
                } ?: voices?.firstOrNull { voice ->
                    val lang = voice.locale.language
                    val country = voice.locale.country.uppercase(Locale.ROOT)
                    val name = voice.name.lowercase(Locale.ROOT)
                    val isEs = lang == "es"
                    val isLatam = country == "419" || country == "MX" || country == "US" || country == "CO" ||
                                  name.contains("419") || name.contains("mx") || name.contains("us") || name.contains("latam")
                    val isFemale = name.contains("female") || name.contains("fem") || name.contains("sfd") || voice.features.contains("gender=female")
                    isEs && isLatam && isFemale
                } ?: voices?.firstOrNull { voice ->
                    val lang = voice.locale.language
                    val name = voice.name.lowercase(Locale.ROOT)
                    lang == "es" && (name.contains("female") || name.contains("fem") || voice.features.contains("gender=female"))
                }

                if (latamFemaleVoice != null) {
                    tts?.voice = latamFemaleVoice
                    Log.i(TAG, "Voz femenina latino neutro asignada: ${latamFemaleVoice.name} (${latamFemaleVoice.locale})")
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo consultar lista de voces TTS: ${e.message}")
            }

            // Tono y velocidad naturales (1.0f) para evitar efecto robótico
            tts?.setPitch(1.0f)
            tts?.setSpeechRate(1.0f)
            isTtsInitialized = true
            Log.i(TAG, "TextToSpeech latino neutro inicializado correctamente")
        } else {
            Log.e(TAG, "Fallo al inicializar TextToSpeech: status=$status")
        }
    }

    /**
     * Inicia la escucha continua manos libres usando el motor offline Vosk.
     * CERO bips de sistema, CERO tiempo de espera, 100% silencioso y en segundo plano.
     */
    fun startContinuousListening() {
        if (!hasMicrophonePermission()) {
            Log.w(TAG, "No hay permiso de microfono para escucha continua")
            return
        }
        val model = voskModel ?: return
        if (voskSpeechService != null) return

        mainHandler.post {
            try {
                val recognizer = Recognizer(model, 16000.0f)
                voskSpeechService = SpeechService(recognizer, 16000.0f)
                voskSpeechService?.startListening(voskListener)
                Log.i(TAG, "Escucha continua manos libres (Vosk) iniciada: di 'Arlet' para activar comandos")
            } catch (e: Exception) {
                Log.e(TAG, "Error al iniciar Vosk SpeechService", e)
            }
        }
    }

    fun stopContinuousListening() {
        mainHandler.post {
            try {
                voskSpeechService?.stop()
                voskSpeechService?.shutdown()
                voskSpeechService = null
            } catch (_: Exception) {}
        }
    }

    fun hasMicrophonePermission(): Boolean {
        val ctx = context ?: return false
        return androidx.core.content.ContextCompat.checkSelfPermission(
            ctx,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /**
     * Activación manual mediante el botón del micrófono en pantalla.
     */
    fun triggerManualListening() {
        if (!hasMicrophonePermission()) {
            val act = com.tuusuario.carlauncher.MainActivity.instance
            if (act != null) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    act,
                    arrayOf(android.Manifest.permission.RECORD_AUDIO),
                    2001
                )
            }
            assistantResponse.value = "Se necesita permiso de micrófono para usar a Arlet."
            showOverlay.value = true
            return
        }

        // Detener voz previa si estaba hablando
        try { tts?.stop() } catch (_: Exception) {}
        isSpeaking.value = false
        mainHandler.removeCallbacksAndMessages(null)

        isManualPushToTalk = true
        showOverlay.value = true
        assistantResponse.value = "Te escucho..."
        lastSpokenQuery.value = ""
        isListening.value = true
        isAwaitingDisambiguation.value = false
        disambiguationOptions.clear()

        // Si Vosk está disponible, usarlo
        if (voskSpeechService != null) {
            voskSpeechService?.setPause(false)
        } else if (voskModel != null) {
            startContinuousListening()
        } else {
            // Fallback a Android SpeechRecognizer
            startAndroidRecognitionSession()
        }
    }

    /**
     * Crea un cliente SpeechRecognizer como respaldo si Vosk no está disponible aún
     */
    private fun startAndroidRecognitionSession() {
        mainHandler.post {
            val ctx = context ?: return@post

            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
            speechRecognizer = null

            try {
                if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
                    assistantResponse.value = "Reconocimiento de voz no disponible."
                    isListening.value = false
                    return@post
                }

                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(ctx).apply {
                    setRecognitionListener(arletRecognitionListener)
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-419")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-419")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
                }

                speechRecognizer?.startListening(intent)
                isListening.value = true
            } catch (e: Exception) {
                Log.e(TAG, "Error iniciando sesión de SpeechRecognizer", e)
                isListening.value = false
                assistantResponse.value = "Error al abrir el micrófono. Toca de nuevo."
            }
        }
    }

    fun closeOverlay() {
        showOverlay.value = false
        isAwaitingDisambiguation.value = false
        disambiguationOptions.clear()
        isListening.value = false
        isSpeaking.value = false
        isManualPushToTalk = false
        audioRms.value = 0f
        mainHandler.removeCallbacksAndMessages(null)
        try { tts?.stop() } catch (_: Exception) {}
        if (AppSettings.arletVoiceWakeEnabled.value) {
            voskSpeechService?.setPause(false)
        }
    }

    /**
     * Sintetiza la respuesta por voz femenina
     */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        assistantResponse.value = text
        showOverlay.value = true
        isSpeaking.value = true
        isListening.value = false
        audioRms.value = 0f

        // Pausar escucha mientras Arlet habla para que no capture su propio eco
        voskSpeechService?.setPause(true)
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (_: Exception) {}
        }

        if (!isTtsInitialized || tts == null) {
            Log.w(TAG, "TTS no listo para hablar")
            isSpeaking.value = false
            voskSpeechService?.setPause(false)
            onDone?.invoke()
            return
        }

        val utteranceId = UUID.randomUUID().toString()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId) {
                    mainHandler.post {
                        isSpeaking.value = false
                        onDone?.invoke()
                        // Reanudar escucha en Vosk
                        voskSpeechService?.setPause(false)
                        if (isAwaitingDisambiguation.value) {
                            isListening.value = true
                        } else {
                            // Cerrar overlay suavemente tras terminar de hablar
                            mainHandler.postDelayed({
                                if (!isAwaitingDisambiguation.value && !isSpeaking.value) {
                                    showOverlay.value = false
                                    isListening.value = false
                                    isManualPushToTalk = false
                                }
                            }, 3200)
                        }
                    }
                }
            }
            override fun onError(id: String?) {
                if (id == utteranceId) {
                    mainHandler.post {
                        isSpeaking.value = false
                        voskSpeechService?.setPause(false)
                        onDone?.invoke()
                    }
                }
            }
        })

        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    // ── Listener de Vosk (Motor Offline Manos Libres) ────────────────────────

    private var lastHandledTime = 0L
    private var lastHandledText = ""

    private val silenceWatchdog = Runnable {
        val query = lastSpokenQuery.value.trim()
        if (query.isNotBlank() && (showOverlay.value || isListening.value)) {
            Log.i(TAG, "Watchdog de silencio activado tras pausa del usuario: '$query'")
            handleSpeechInput(query)
        }
    }

    private fun containsWakeWord(norm: String): Boolean {
        val triggers = listOf(
            "arlet", "arleth", "arlete", "harlet", "arled",
            "arlette", "arlety", "aleta", "aletta", "atlet", "adlet", "arle"
        )
        val words = norm.split(" ")
        return words.any { triggers.contains(it) } ||
               triggers.any { norm.contains(it) } ||
               norm.startsWith("ar le") || norm.contains("oye arlet") || norm.contains("hola arlet")
    }

    private fun stripWakeWord(norm: String): String {
        var command = norm
        val triggers = listOf(
            "oye arlet", "hola arlet", "arlet", "arleth", "arlete",
            "harlet", "arled", "arlette", "arlety", "aleta", "aletta",
            "atlet", "adlet", "ar le", "arle"
        )
        for (t in triggers) {
            command = command.replace(t, " ")
        }
        return command
            .replace("por favor", " ")
            .replace("puedes", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun isActionableCommand(norm: String): Boolean {
        val c = stripWakeWord(norm)
        if (c.isBlank()) return false

        // Cámara / Dashcam
        if (c.contains("graba") || c.contains("grabar") || c.contains("grabacion") ||
            c.contains("dashcam") || c.contains("camara") || c.contains("video")) return true
        if (c.contains("deten") || c.contains("para") || c.contains("cancela") || c.contains("apaga")) return true

        // Música
        if (c.contains("pausa") || c.contains("pausar") || c.contains("reproduce") ||
            c.contains("reproducir") || c.contains("play") || c.contains("siguiente") ||
            c.contains("cancion") || c.contains("anterior") || c.contains("adelanta") ||
            c.contains("retrocede") || c.contains("musica")) return true

        // Navegación
        if (c.contains("inicia ruta") || c.contains("iniciar ruta") || c.contains("ruta a") ||
            c.contains("ruta hacia") || c.contains("ir a") || c.contains("ir al") ||
            c.contains("llevame") || c.contains("navega") || c.contains("conduce a") ||
            c.contains("destino")) return true

        // Desambiguación si está esperando
        if (isAwaitingDisambiguation.value) {
            if (c.contains("primero") || c.contains("primera") || c.contains("segundo") ||
                c.contains("segunda") || c.contains("tercero") || c.contains("tercera") ||
                c.contains("uno") || c.contains("dos") || c.contains("tres")) return true
        }

        // Saludos y preguntas
        if (c.contains("hola") || c.contains("buenos dias") || c.contains("buenas tardes") ||
            c.contains("quien eres") || c.contains("como te llamas") || c.contains("ayuda") ||
            c.contains("que puedes hacer") || c.contains("comandos") || c.contains("gracias")) return true

        return false
    }

    private val voskListener = object : VoskRecognitionListener {
        override fun onPartialResult(hypothesis: String) {
            val partial = try {
                JSONObject(hypothesis).optString("partial", "").trim()
            } catch (_: Exception) { "" }

            if (partial.isNotBlank()) {
                val norm = normalize(partial)
                val hasWakeWord = containsWakeWord(norm)
                val isAlreadyActive = showOverlay.value || isManualPushToTalk || isAwaitingDisambiguation.value

                if (hasWakeWord || isAlreadyActive) {
                    showOverlay.value = true
                    isListening.value = true
                    lastSpokenQuery.value = partial
                    if (assistantResponse.value.isBlank() || assistantResponse.value == "Listo") {
                        assistantResponse.value = "Te escucho..."
                    }

                    // Si ya se identificó un comando claro, ejecutarlo DE INMEDIATO sin requerir silencio prolongado
                    if (isActionableCommand(norm)) {
                        mainHandler.removeCallbacks(silenceWatchdog)
                        handleSpeechInput(partial)
                        return
                    }

                    // Watchdog: Si el usuario para de hablar durante 900ms, procesar lo capturado
                    mainHandler.removeCallbacks(silenceWatchdog)
                    mainHandler.postDelayed(silenceWatchdog, 900)
                }
            }
        }

        override fun onResult(hypothesis: String) {
            val text = try {
                JSONObject(hypothesis).optString("text", "").trim()
            } catch (_: Exception) { "" }

            if (text.isNotBlank()) {
                Log.i(TAG, "Vosk onResult: '$text'")
                val norm = normalize(text)
                val hasWakeWord = containsWakeWord(norm)

                if (hasWakeWord || isAwaitingDisambiguation.value || isManualPushToTalk || showOverlay.value) {
                    handleSpeechInput(text)
                }
            }
        }

        override fun onFinalResult(hypothesis: String) {
            val text = try {
                JSONObject(hypothesis).optString("text", "").trim()
            } catch (_: Exception) { "" }

            if (text.isNotBlank()) {
                val norm = normalize(text)
                val hasWakeWord = containsWakeWord(norm)

                if (hasWakeWord || isAwaitingDisambiguation.value || isManualPushToTalk || showOverlay.value) {
                    handleSpeechInput(text)
                }
            }
        }

        override fun onError(exception: Exception) {
            Log.w(TAG, "Vosk error: ${exception.message}")
        }

        override fun onTimeout() {}
    }

    // ── Fallback Listener de Android SpeechRecognizer ────────────────────────

    private val arletRecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { isListening.value = true }
        override fun onBeginningOfSpeech() { isListening.value = true }
        override fun onRmsChanged(rmsdB: Float) {
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            audioRms.value = normalized
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { isListening.value = false; audioRms.value = 0f }
        override fun onError(error: Int) {
            isListening.value = false
            audioRms.value = 0f
            val isSilence = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            if (isSilence) {
                if (isAwaitingDisambiguation.value) {
                    assistantResponse.value = "Toca tu destino en pantalla o di 'la primera'."
                } else {
                    assistantResponse.value = "No te escuché bien. Toca el micrófono para hablar."
                }
            } else {
                assistantResponse.value = "Intenta de nuevo."
            }
            mainHandler.postDelayed({
                if (!isSpeaking.value && !isAwaitingDisambiguation.value) {
                    showOverlay.value = false
                    isManualPushToTalk = false
                }
            }, 2600)
        }
        override fun onResults(results: Bundle?) {
            isListening.value = false
            audioRms.value = 0f
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val spokenText = matches?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                handleSpeechInput(spokenText)
            } else {
                assistantResponse.value = "No te escuché bien."
                mainHandler.postDelayed({
                    if (!isSpeaking.value && !isAwaitingDisambiguation.value) {
                        showOverlay.value = false
                        isManualPushToTalk = false
                    }
                }, 2500)
            }
        }
        override fun onPartialResults(partialResults: Bundle?) {
            val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = partials?.firstOrNull()
            if (!partial.isNullOrBlank()) lastSpokenQuery.value = partial
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // ── Motor de Intenciones y Comandos ──────────────────────────────────────

    private fun handleSpeechInput(rawText: String) {
        val normalized = normalize(rawText)
        val now = System.currentTimeMillis()
        if (now - lastHandledTime < 1800 && normalized == lastHandledText) {
            return
        }
        lastHandledTime = now
        lastHandledText = normalized
        mainHandler.removeCallbacks(silenceWatchdog)

        Log.i(TAG, "Audio capturado: '$rawText' -> Normalizado: '$normalized'")

        lastSpokenQuery.value = rawText
        showOverlay.value = true
        isListening.value = false
        audioRms.value = 0f

        // 1. Caso: Esperando desambiguación de destino ("el primero", "el segundo", o nombre)
        if (isAwaitingDisambiguation.value && disambiguationOptions.isNotEmpty()) {
            handleDisambiguationChoice(normalized)
            return
        }

        // Limpiar "arlet" y variantes fonéticas
        var command = stripWakeWord(normalized)

        if (command.isBlank() || command == "oye" || command == "hola") {
            speak("Dime, ¿en qué te puedo ayudar?")
            return
        }

        // ── COMANDOS DE CÁMARA (DASHCAM) ────────────────────────────────────
        val isCamStart = (command.contains("graba") || command.contains("grabar") ||
                          command.contains("grabacion") || command.contains("dashcam") ||
                          command.contains("camara")) &&
                         !command.contains("deten") && !command.contains("para") &&
                         !command.contains("deja") && !command.contains("cancela")

        val isCamStop = (command.contains("deten") || command.contains("para") ||
                         command.contains("deja") || command.contains("cancela") ||
                         command.contains("apaga")) &&
                        (command.contains("graba") || command.contains("grabacion") ||
                         command.contains("camara") || command.contains("dashcam") ||
                         command.contains("video"))

        if (isCamStart) {
            handleStartRecording()
            return
        }

        if (isCamStop) {
            handleStopRecording()
            return
        }

        // ── COMANDOS DE MÚSICA ──────────────────────────────────────────────
        val isMusicPause = command.contains("pausa") || command.contains("pausar") ||
                           command.contains("pon pausa") ||
                           ((command.contains("para") || command.contains("deten")) && (command.contains("musica") || command.contains("cancion")))

        val isMusicPlay = command.contains("reproduce") || command.contains("reproducir") ||
                          command.contains("play") || command.contains("continua la musica") ||
                          command.contains("pon musica") || command.contains("sigue la musica")

        val isMusicNext = command.contains("siguiente") || command.contains("pasa la cancion") ||
                          command.contains("pasa de cancion") || command.contains("adelanta") ||
                          command.contains("otra cancion")

        val isMusicPrev = command.contains("anterior") || command.contains("cancion anterior") ||
                          command.contains("retrocede") || command.contains("vuelve")

        if (isMusicPause) {
            GlobalState.mediaController?.transportControls?.pause()
            speak("Música pausada")
            return
        }

        if (isMusicPlay) {
            GlobalState.mediaController?.transportControls?.play()
            speak("Reproduciendo música")
            return
        }

        if (isMusicNext) {
            GlobalState.skipToNext()
            speak("Siguiente canción")
            return
        }

        if (isMusicPrev) {
            GlobalState.skipToPrevious()
            speak("Canción anterior")
            return
        }

        // ── COMANDOS DE NAVEGACIÓN A LUGARES GUARDADOS ───────────────────────
        val navTriggers = listOf(
            "inicia ruta a", "iniciar ruta a", "inicia la ruta a", "iniciar la ruta a",
            "ruta a", "ruta hacia", "ir a", "ir al", "ir hacia",
            "llevame a", "llevame al", "llevame hacia",
            "navega a", "navegar a", "conduce a", "destino"
        )

        var destinationQuery: String? = null
        for (trigger in navTriggers) {
            if (command.contains(trigger)) {
                destinationQuery = command.substringAfter(trigger).trim()
                break
            }
        }

        if (destinationQuery != null && destinationQuery.isNotBlank()) {
            handleNavigationToSavedPlace(destinationQuery)
            return
        }

        // ── SALUDOS / AYUDA ─────────────────────────────────────────────────
        if (command.contains("hola") || command.contains("buenos dias") || command.contains("buenas tardes")) {
            speak("¡Hola! Estoy lista para el camino. ¿A dónde vamos o qué deseas hacer?")
            return
        }

        if (command.contains("quien eres") || command.contains("como te llamas")) {
            speak("Soy Arlet, tu copiloto virtual para este viaje.")
            return
        }

        if (command.contains("ayuda") || command.contains("que puedes hacer") || command.contains("comandos")) {
            speak("Puedo iniciar la grabación de la cámara, controlar la música y guiarte a tus lugares favoritos.")
            return
        }

        if (command.contains("gracias")) {
            speak("¡De nada! Maneja con cuidado.")
            return
        }

        // Comando no reconocido
        speak("No entendí esa orden. Puedes pedirme grabar con la cámara, controlar la música o ir a un lugar guardado.")
    }

    // ── Acciones de Cámara ──────────────────────────────────────────────────

    private fun handleStartRecording() {
        val ctx = context ?: return
        val owner = lifecycleOwner ?: return

        if (DashcamManager.isRecording.value) {
            speak("La cámara ya está grabando")
        } else {
            mainHandler.post {
                DashcamManager.startRecording(ctx, owner)
            }
            speak("Iniciando grabación")
        }
    }

    private fun handleStopRecording() {
        if (DashcamManager.isRecording.value) {
            mainHandler.post {
                DashcamManager.stopRecording()
            }
            speak("Grabación detenida y guardada")
        } else {
            speak("La cámara no está grabando")
        }
    }

    // ── Búsqueda y Desambiguación de Lugares ─────────────────────────────────

    private fun handleNavigationToSavedPlace(query: String) {
        val ctx = context ?: return

        // Limpiar artículos determinantes ("el", "la", "mi", "los", "las", etc.)
        var cleanTarget = query
        val stopWords = listOf("el ", "la ", "mi ", "mis ", "los ", "las ", "un ", "una ", "hacia ")
        for (sw in stopWords) {
            if (cleanTarget.startsWith(sw)) {
                cleanTarget = cleanTarget.removePrefix(sw).trim()
            }
        }

        val favManager = FavoritesManager(ctx)
        val allFavorites = favManager.getFavorites()

        if (allFavorites.isEmpty()) {
            speak("No tienes ningún lugar guardado en favoritos todavía.")
            return
        }

        // Buscar coincidencias
        val targetNorm = normalize(cleanTarget)
        val matches = allFavorites.filter { fav ->
            val favNorm = normalize(fav.name)
            val addrNorm = normalize(fav.address)
            
            favNorm == targetNorm ||
            favNorm.contains(targetNorm) ||
            targetNorm.contains(favNorm) ||
            addrNorm.contains(targetNorm) ||
            hasSignificantWordOverlap(favNorm, targetNorm)
        }

        when {
            matches.isEmpty() -> {
                speak("No encontré ningún lugar guardado que coincida con $cleanTarget")
            }
            matches.size == 1 -> {
                val selected = matches.first()
                selectDestination(selected)
            }
            else -> {
                // Hay múltiples destinos coincidentes -> Iniciar modo de desambiguación
                disambiguationOptions.clear()
                disambiguationOptions.addAll(matches)
                isAwaitingDisambiguation.value = true
                showOverlay.value = true

                if (matches.size == 2) {
                    speak("Encontré 2 opciones: ${matches[0].name} y ${matches[1].name}. ¿A cuál nos dirigimos?")
                } else {
                    val nombres = matches.take(3).joinToString(", ") { it.name }
                    speak("Encontré varias opciones: $nombres. ¿Cuál prefieres?")
                }
            }
        }
    }

    private fun handleDisambiguationChoice(text: String) {
        val options = disambiguationOptions.toList()
        if (options.isEmpty()) {
            isAwaitingDisambiguation.value = false
            return
        }

        // 1. Opciones por número ordinal ("primera", "segunda", "tercera", etc.)
        val isFirst = text.contains("primero") || text.contains("primera") || text.contains("uno") || text.contains("1")
        val isSecond = text.contains("segundo") || text.contains("segunda") || text.contains("dos") || text.contains("2")
        val isThird = text.contains("tercero") || text.contains("tercera") || text.contains("tres") || text.contains("3")

        when {
            isFirst && options.isNotEmpty() -> {
                selectDestination(options[0])
            }
            isSecond && options.size >= 2 -> {
                selectDestination(options[1])
            }
            isThird && options.size >= 3 -> {
                selectDestination(options[2])
            }
            text.contains("cancela") || text.contains("ninguno") || text.contains("ninguna") -> {
                isAwaitingDisambiguation.value = false
                disambiguationOptions.clear()
                speak("De acuerdo, ruta cancelada")
            }
            else -> {
                // Intentar buscar por nombre entre las opciones
                val matched = options.firstOrNull { fav ->
                    val favNorm = normalize(fav.name)
                    favNorm.contains(text) || text.contains(favNorm)
                }

                if (matched != null) {
                    selectDestination(matched)
                } else {
                    speak("No alcancé a distinguir cuál destino. Puedes tocar la opción en pantalla o decir 'la primera' o 'la segunda'.")
                }
            }
        }
    }

    /**
     * Establece el destino e inicia el cálculo de la ruta
     */
    fun selectDestination(place: PlaceResult) {
        isAwaitingDisambiguation.value = false
        disambiguationOptions.clear()

        val destPoint = Point.fromLngLat(place.lon, place.lat)
        NavigationState.startRoute(destPoint)

        speak("Iniciando ruta hacia ${place.name}") {
            mainHandler.postDelayed({
                showOverlay.value = false
            }, 3000)
        }
    }

    // ── Utilidades de Texto y Normalización ──────────────────────────────────

    private fun normalize(str: String): String {
        val decomposed = Normalizer.normalize(str.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun hasSignificantWordOverlap(a: String, b: String): Boolean {
        val wordsA = a.split(" ").filter { it.length >= 3 }
        val wordsB = b.split(" ").filter { it.length >= 3 }
        return wordsA.any { it in wordsB }
    }

    fun destroy() {
        stopContinuousListening()
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            try {
                voskModel?.close()
                voskModel = null
            } catch (_: Exception) {}
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
                tts?.stop()
                tts?.shutdown()
                tts = null
            } catch (_: Exception) {}
        }
    }
}
