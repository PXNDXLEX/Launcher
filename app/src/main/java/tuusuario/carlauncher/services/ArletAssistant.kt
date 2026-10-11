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
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

/**
 * Asistente de Voz Arlet
 *
 * Controla:
 * - Grabación de Dashcam (start/stop)
 * - Control de Música (pausa, play, siguiente, anterior)
 * - Navegación a lugares guardados con desambiguación inteligente
 * - Activación por voz ("Arlet, [comando]") o por botón
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

    // Indicador si la escucha actual fue disparada manualmente por el botón del micrófono
    private var isManualPushToTalk = false

    // Control de hilos y reinicio
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var recognizerIntent: Intent? = null
    private var isContinuousRunning = false

    // Text To Speech
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    fun init(appContext: Context, owner: LifecycleOwner) {
        this.context = appContext.applicationContext
        this.lifecycleOwner = owner

        if (tts == null) {
            tts = TextToSpeech(appContext.applicationContext, this)
        }

        setupRecognizer()
        
        // Si el usuario tiene la escucha de voz activada en ajustes, iniciamos el modo oreja
        if (AppSettings.arletVoiceWakeEnabled.value) {
            startContinuousListening()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val localeEs = Locale("es", "ES")
            val langResult = tts?.setLanguage(localeEs)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale("es"))
            }

            // Buscar y asignar una voz femenina en español disponible en el sistema
            try {
                val voices = tts?.voices
                val femaleVoice = voices?.firstOrNull { voice ->
                    voice.locale.language == "es" &&
                    !voice.isNetworkConnectionRequired &&
                    (voice.name.contains("female", ignoreCase = true) ||
                     voice.name.contains("fem", ignoreCase = true) ||
                     voice.name.contains("es-es-x-eed-network", ignoreCase = true) ||
                     voice.name.contains("es-es-x-ana", ignoreCase = true) ||
                     voice.features.contains("gender=female"))
                } ?: voices?.firstOrNull { voice ->
                    voice.locale.language == "es" &&
                    (voice.name.contains("female", ignoreCase = true) ||
                     voice.name.contains("fem", ignoreCase = true) ||
                     voice.features.contains("gender=female"))
                }

                if (femaleVoice != null) {
                    tts?.voice = femaleVoice
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo consultar lista de voces TTS: ${e.message}")
            }

            // Timbre agudo femenino y velocidad fluida para el coche
            tts?.setPitch(1.22f)
            tts?.setSpeechRate(1.06f)
            isTtsInitialized = true
            Log.i(TAG, "TextToSpeech femenino inicializado correctamente")
        } else {
            Log.e(TAG, "Fallo al inicializar TextToSpeech: status=$status")
        }
    }

    private fun setupRecognizer() {
        mainHandler.post {
            try {
                val ctx = context ?: return@post
                if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
                    Log.w(TAG, "SpeechRecognizer no disponible en este dispositivo")
                    return@post
                }

                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(ctx).apply {
                    setRecognitionListener(arletRecognitionListener)
                }

                recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra("android.speech.extra.PREFER_OFFLINE", true)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error configurando SpeechRecognizer", e)
            }
        }
    }

    /**
     * Inicia la escucha continua de fondo para detectar "Arlet"
     */
    fun startContinuousListening() {
        if (isSpeaking.value) return
        isContinuousRunning = true
        mainHandler.post {
            startListeningInternal()
        }
    }

    /**
     * Detiene la escucha continua
     */
    fun stopContinuousListening() {
        isContinuousRunning = false
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (_: Exception) {}
            isListening.value = false
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
     * Activación manual mediante el botón del micrófono en pantalla
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

        isManualPushToTalk = true
        showOverlay.value = true
        assistantResponse.value = "Te escucho..."
        lastSpokenQuery.value = ""
        isAwaitingDisambiguation.value = false
        disambiguationOptions.clear()

        mainHandler.post {
            try {
                speechRecognizer?.cancel()
            } catch (_: Exception) {}
            startListeningInternal()
        }
    }

    fun closeOverlay() {
        showOverlay.value = false
        isAwaitingDisambiguation.value = false
        disambiguationOptions.clear()
        isManualPushToTalk = false
        if (AppSettings.arletVoiceWakeEnabled.value && !isListening.value && !isSpeaking.value) {
            startContinuousListening()
        }
    }

    private fun startListeningInternal() {
        if (!hasMicrophonePermission()) {
            Log.w(TAG, "Permiso RECORD_AUDIO no concedido aún. No se inicia la escucha.")
            isListening.value = false
            return
        }

        val recognizer = speechRecognizer ?: return
        val intent = recognizerIntent ?: return
        if (isSpeaking.value) return

        try {
            recognizer.startListening(intent)
            isListening.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Error en startListening", e)
            isListening.value = false
            // Reintentar si la escucha continua está activa
            if (isContinuousRunning && !isSpeaking.value) {
                mainHandler.postDelayed({ startListeningInternal() }, 600)
            }
        }
    }

    /**
     * Sintetiza la respuesta por voz femenina
     */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        assistantResponse.value = text
        showOverlay.value = true
        isSpeaking.value = true

        // Pausar el micrófono temporalmente para evitar que Arlet se escuche a sí misma
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (_: Exception) {}
            isListening.value = false
        }

        if (!isTtsInitialized || tts == null) {
            Log.w(TAG, "TTS no listo para hablar")
            isSpeaking.value = false
            onDone?.invoke()
            if (isContinuousRunning) {
                mainHandler.postDelayed({ startListeningInternal() }, 500)
            }
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
                        // Reanudar escucha si aplica
                        if (isContinuousRunning || isAwaitingDisambiguation.value) {
                            mainHandler.postDelayed({ startListeningInternal() }, 300)
                        } else {
                            // Si fue manual y terminó, ocultar overlay tras 3 segundos
                            mainHandler.postDelayed({
                                if (!isAwaitingDisambiguation.value && !isSpeaking.value) {
                                    showOverlay.value = false
                                }
                            }, 3500)
                        }
                    }
                }
            }
            override fun onError(id: String?) {
                if (id == utteranceId) {
                    mainHandler.post {
                        isSpeaking.value = false
                        onDone?.invoke()
                        if (isContinuousRunning) {
                            mainHandler.postDelayed({ startListeningInternal() }, 400)
                        }
                    }
                }
            }
        })

        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    // ── Listener de Reconocimiento de Voz ────────────────────────────────────

    private val arletRecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isListening.value = true
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            // Normalizar dB (-2 a 10) a rango 0f .. 1f para ondas visuales
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            audioRms.value = normalized
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            isListening.value = false
            audioRms.value = 0f
        }

        override fun onError(error: Int) {
            isListening.value = false
            audioRms.value = 0f

            // Si es timeout o no match, es normal (el usuario no dijo nada en ese lapso)
            val isNormalSilence = error == SpeechRecognizer.ERROR_NO_MATCH || 
                                  error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT

            if (isNormalSilence) {
                if (isContinuousRunning && !isSpeaking.value) {
                    mainHandler.postDelayed({ startListeningInternal() }, 200)
                } else if (isManualPushToTalk && !isAwaitingDisambiguation.value) {
                    // Ocultar overlay tras silencio
                    mainHandler.postDelayed({ showOverlay.value = false }, 1500)
                }
            } else {
                Log.w(TAG, "Error en SpeechRecognizer: $error")
                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    try { speechRecognizer?.cancel() } catch (_: Exception) {}
                }
                if (isContinuousRunning && !isSpeaking.value) {
                    mainHandler.postDelayed({ startListeningInternal() }, 600)
                }
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val spokenText = matches[0]
                handleSpeechInput(spokenText)
            } else {
                if (isContinuousRunning && !isSpeaking.value) {
                    mainHandler.postDelayed({ startListeningInternal() }, 200)
                }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!partials.isNullOrEmpty()) {
                val partial = partials[0]
                // Si en los parciales detectamos "Arlet", abrimos el HUD de inmediato
                val norm = normalize(partial)
                if (norm.contains("arlet") || norm.contains("arleth") || norm.contains("arlete") || norm.contains("harlet")) {
                    showOverlay.value = true
                    lastSpokenQuery.value = partial
                    assistantResponse.value = "Te escucho..."
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // ── Motor de Intenciones y Comandos ──────────────────────────────────────

    private fun handleSpeechInput(rawText: String) {
        val normalized = normalize(rawText)
        Log.i(TAG, "Audio capturado: '$rawText' -> Normalizado: '$normalized'")

        lastSpokenQuery.value = rawText
        showOverlay.value = true

        // 1. Caso: Esperando desambiguación de destino ("el primero", "el segundo", o nombre)
        if (isAwaitingDisambiguation.value && disambiguationOptions.isNotEmpty()) {
            handleDisambiguationChoice(normalized)
            return
        }

        // 2. Comprobar si el texto contiene la palabra clave "Arlet" o si fue activación manual por botón
        val hasWakeWord = normalized.contains("arlet") ||
                          normalized.contains("arleth") ||
                          normalized.contains("arlete") ||
                          normalized.contains("harlet")

        if (!hasWakeWord && !isManualPushToTalk) {
            // No nos estaban hablando a nosotros; ignorar y seguir escuchando
            showOverlay.value = false
            if (isContinuousRunning && !isSpeaking.value) {
                mainHandler.postDelayed({ startListeningInternal() }, 200)
            }
            return
        }

        // Limpiar "arlet" del comando
        var command = normalized
            .replace("arlet", "")
            .replace("arleth", "")
            .replace("arlete", "")
            .replace("harlet", "")
            .trim()

        // Eliminar posibles palabras de enlace ("por favor", "puedes", etc.)
        command = command.removePrefix("por favor").removePrefix("puedes").trim()

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
        mainHandler.post {
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
