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
import com.tuusuario.carlauncher.ui.map.searchPlaces
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    // Control de hilos y prevención de eco
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechMutedUntil = 0L
    private var lastActionExecutedTime = 0L

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
            val candidates = listOf(
                Locale("es", "MX"),
                Locale("es", "US"),
                Locale("es", "ES"),
                Locale("es")
            )
            var langConfigured = false
            for (candidate in candidates) {
                val res = tts?.setLanguage(candidate)
                if (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED) {
                    langConfigured = true
                    Log.i(TAG, "Idioma TTS configurado exitosamente: $candidate")
                    break
                }
            }
            if (!langConfigured) {
                tts?.setLanguage(Locale.getDefault())
            }

            // Buscar y asignar una voz femenina en español disponible en el sistema
            try {
                val voices = tts?.voices
                if (!voices.isNullOrEmpty()) {
                    val candidateVoice = voices.firstOrNull { voice ->
                        val lang = voice.locale.language
                        val country = voice.locale.country.uppercase(Locale.ROOT)
                        val name = voice.name.lowercase(Locale.ROOT)
                        val isNotInstalled = voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true
                        val isFemale = name.contains("female") || name.contains("fem") || name.contains("sfd") || voice.features?.contains("gender=female") == true
                        val isLatam = country == "MX" || country == "US" || country == "CO" || name.contains("mx") || name.contains("us")
                        lang == "es" && isLatam && isFemale && !isNotInstalled && !voice.isNetworkConnectionRequired
                    } ?: voices.firstOrNull { voice ->
                        val lang = voice.locale.language
                        val name = voice.name.lowercase(Locale.ROOT)
                        val isNotInstalled = voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true
                        val isFemale = name.contains("female") || name.contains("fem") || name.contains("sfd") || voice.features?.contains("gender=female") == true
                        lang == "es" && isFemale && !isNotInstalled
                    } ?: voices.firstOrNull { voice ->
                        val lang = voice.locale.language
                        val isNotInstalled = voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true
                        lang == "es" && !isNotInstalled
                    }

                    if (candidateVoice != null) {
                        try {
                            tts?.voice = candidateVoice
                            Log.i(TAG, "Voz femenina TTS asignada: ${candidateVoice.name} (${candidateVoice.locale})")
                        } catch (e: Exception) {
                            Log.w(TAG, "No se pudo asignar voz específica, usando por defecto: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo consultar lista de voces TTS: ${e.message}")
            }

            // Tono y velocidad naturales (1.0f) para evitar efecto robótico
            tts?.setPitch(1.0f)
            tts?.setSpeechRate(1.0f)
            isTtsInitialized = true
            Log.i(TAG, "TextToSpeech inicializado correctamente")
        } else {
            Log.e(TAG, "Fallo al inicializar TextToSpeech: status=$status")
            isTtsInitialized = false
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

                val langTag = if (Locale.getDefault().language == "es") Locale.getDefault().toLanguageTag() else "es-MX"
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
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
        speechMutedUntil = System.currentTimeMillis() + 600L
        voskSpeechService?.reset()
        if (AppSettings.arletVoiceWakeEnabled.value) {
            mainHandler.postDelayed({
                if (!isSpeaking.value) {
                    voskSpeechService?.setPause(false)
                }
            }, 600)
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
        speechMutedUntil = Long.MAX_VALUE // Silencio absoluto de reconocimiento durante habla

        // Pausar y resetear Vosk de forma segura a través del método de servicio
        voskSpeechService?.setPause(true)
        voskSpeechService?.reset()
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (_: Exception) {}
        }

        if (!isTtsInitialized || tts == null) {
            Log.w(TAG, "TTS no listo para hablar")
            isSpeaking.value = false
            speechMutedUntil = System.currentTimeMillis() + 1500L
            voskSpeechService?.reset()
            onDone?.invoke()
            if (!isAwaitingDisambiguation.value) {
                showOverlay.value = false
                isListening.value = false
                isManualPushToTalk = false
                mainHandler.postDelayed({
                    if (AppSettings.arletVoiceWakeEnabled.value) {
                        voskSpeechService?.reset()
                        voskSpeechService?.setPause(false)
                    }
                }, 1500)
            }
            return
        }

        val utteranceId = UUID.randomUUID().toString()

        // Temporizador de seguridad: si TTS se congela o tarda más de 4s, liberar el asistente
        val safetyTimeout = Runnable {
            if (isSpeaking.value) {
                Log.w(TAG, "Timeout de seguridad TTS: liberando asistente")
                isSpeaking.value = false
                speechMutedUntil = System.currentTimeMillis() + 1500L
                voskSpeechService?.reset()
                onDone?.invoke()
                if (!isAwaitingDisambiguation.value) {
                    showOverlay.value = false
                    isListening.value = false
                    isManualPushToTalk = false
                    mainHandler.postDelayed({
                        if (AppSettings.arletVoiceWakeEnabled.value) {
                            voskSpeechService?.reset()
                            voskSpeechService?.setPause(false)
                        }
                    }, 1500)
                }
            }
        }
        mainHandler.postDelayed(safetyTimeout, 4000)

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId) {
                    mainHandler.removeCallbacks(safetyTimeout)
                    mainHandler.post {
                        isSpeaking.value = false
                        speechMutedUntil = System.currentTimeMillis() + 1500L
                        voskSpeechService?.reset()
                        onDone?.invoke()

                        if (!isAwaitingDisambiguation.value) {
                            // Cerrar overlay y apagar el micrófono de inmediato al terminar la orden
                            showOverlay.value = false
                            isListening.value = false
                            isManualPushToTalk = false
                            assistantResponse.value = ""
                            lastSpokenQuery.value = ""

                            // Esperar 1.5s para disipar cualquier eco de los altavoces antes de reanudar escucha offline
                            mainHandler.postDelayed({
                                if (AppSettings.arletVoiceWakeEnabled.value) {
                                    voskSpeechService?.reset()
                                    voskSpeechService?.setPause(false)
                                }
                            }, 1500)
                        } else {
                            mainHandler.postDelayed({
                                if (isAwaitingDisambiguation.value) {
                                    voskSpeechService?.reset()
                                    voskSpeechService?.setPause(false)
                                    isListening.value = true
                                }
                            }, 1000)
                        }
                    }
                }
            }
            override fun onError(id: String?) {
                if (id == utteranceId) {
                    mainHandler.removeCallbacks(safetyTimeout)
                    mainHandler.post {
                        isSpeaking.value = false
                        speechMutedUntil = System.currentTimeMillis() + 1500L
                        voskSpeechService?.reset()
                        onDone?.invoke()
                        if (!isAwaitingDisambiguation.value) {
                            showOverlay.value = false
                            isListening.value = false
                            isManualPushToTalk = false
                            mainHandler.postDelayed({
                                if (AppSettings.arletVoiceWakeEnabled.value) {
                                    voskSpeechService?.reset()
                                    voskSpeechService?.setPause(false)
                                }
                            }, 1500)
                        }
                    }
                }
            }
            override fun onError(id: String?, errorCode: Int) {
                onError(id)
            }
        })

        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        val res = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (res != TextToSpeech.SUCCESS) {
            Log.w(TAG, "tts.speak devolvio error ($res)")
            mainHandler.removeCallbacks(safetyTimeout)
            mainHandler.post {
                isSpeaking.value = false
                speechMutedUntil = System.currentTimeMillis() + 1500L
                voskSpeechService?.reset()
                onDone?.invoke()
                if (!isAwaitingDisambiguation.value) {
                    showOverlay.value = false
                    isListening.value = false
                    isManualPushToTalk = false
                    mainHandler.postDelayed({
                        if (AppSettings.arletVoiceWakeEnabled.value) {
                            voskSpeechService?.reset()
                            voskSpeechService?.setPause(false)
                        }
                    }, 1500)
                }
            }
        }
    }

    // ── Listener de Vosk (Motor Offline Manos Libres) ────────────────────────

    private var lastHandledTime = 0L
    private var lastHandledText = ""

    private val silenceWatchdog = Runnable {
        val query = lastSpokenQuery.value.trim()
        if (query.isNotBlank() && (showOverlay.value || isListening.value)) {
            val now = System.currentTimeMillis()
            if (!isSpeaking.value && now >= speechMutedUntil) {
                Log.i(TAG, "Watchdog de silencio activado tras pausa del usuario: '$query'")
                handleSpeechInput(query)
            }
        }
    }

    private val wakeWordTokens = setOf(
        "arlet", "arleth", "arlete", "harlet", "harleth", "arled",
        "arlette", "arlety", "aleta", "aletta", "atlet", "adlet",
        "darlet"
    )

    private fun containsWakeWord(norm: String): Boolean {
        val trimmed = norm.trim()
        if (trimmed.startsWith("arlet") || trimmed.startsWith("harlet") ||
            trimmed.startsWith("ar le") || trimmed.startsWith("oye arlet") ||
            trimmed.startsWith("hola arlet") || trimmed.startsWith("hey arlet") ||
            trimmed.startsWith("ok arlet")) {
            return true
        }

        val words = trimmed.split(Regex("\\s+"))
        return words.any { wakeWordTokens.contains(it) } ||
               (words.size >= 2 && words[0] == "ar" && (words[1] == "le" || words[1] == "let" || words[1] == "led"))
    }

    private fun stripWakeWord(norm: String): String {
        var command = norm
        val triggers = listOf(
            "oye arlet", "hola arlet", "hey arlet", "ok arlet",
            "ar let", "ar le", "ar led",
            "arlet", "arleth", "arlete", "harlet", "harleth", "arled",
            "arlette", "arlety", "aleta", "aletta", "atlet", "adlet", "darlet"
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
        if (c.contains("graba video") || c.contains("grabar video") ||
            c.contains("comienza a grabar") || c.contains("comenzar a grabar") ||
            c.contains("inicia la grabacion") || c.contains("iniciar la grabacion") ||
            c.contains("inicia grabacion") || c.contains("iniciar grabacion") ||
            c.contains("graba la camara") || c.contains("grabar con la camara") ||
            c.contains("grabar dashcam") || c.contains("graba dashcam") ||
            c == "graba" || c == "grabar") return true

        if (c.contains("deten la grabacion") || c.contains("detener grabacion") ||
            c.contains("para la grabacion") || c.contains("parar grabacion") ||
            c.contains("deja de grabar") || c.contains("cancela la grabacion") ||
            c.contains("deten el video") || c.contains("para el video") ||
            c.contains("apaga la camara") || c.contains("apagar la camara")) return true

        // Música
        if (c.contains("pon pausa") || c.contains("pausa la musica") || c.contains("pausar la musica") ||
            c.contains("para la musica") || c.contains("deten la musica") ||
            c == "pausa" || c == "pausar") return true

        if (c.contains("reproduce") || c.contains("reproducir") || c.contains("continua la musica") ||
            c.contains("pon musica") || c.contains("sigue la musica") || c == "play") return true

        if (c.contains("siguiente cancion") || c.contains("pasa la cancion") || c.contains("cambia la cancion") ||
            c.contains("siguiente pista") || c.contains("proxima cancion") || c.contains("otra cancion") ||
            c.contains("adelanta la cancion") || c == "siguiente" || c == "adelanta") return true

        if (c.contains("cancion anterior") || c.contains("pista anterior") ||
            c.contains("retrocede la cancion") || c.contains("vuelve la cancion") ||
            c == "anterior" || c == "retrocede") return true

        // Navegación
        if (c.contains("inicia ruta") || c.contains("iniciar ruta") || c.contains("inicia la ruta") ||
            c.contains("iniciar la ruta") || c.contains("traza la ruta") || c.contains("trazar la ruta") ||
            c.contains("traza ruta") || c.contains("trazar ruta") || c.contains("ruta para") ||
            c.contains("ruta hacia") || c.contains("ruta a") || c.contains("vamos para") ||
            c.contains("vamos hacia") || c.contains("vamos a") || c.contains("ir para") ||
            c.contains("ir a") || c.contains("ir al") || c.contains("llevame") ||
            c.contains("guiame") || c.contains("navega") || c.contains("conduce") ||
            c.contains("pon la ruta") || c.contains("pon ruta") || c.contains("como llego") ||
            c.contains("destino") || c.contains("rumbo a")) return true

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
            val now = System.currentTimeMillis()
            if (isSpeaking.value || now < speechMutedUntil) {
                return
            }

            val partial = try {
                JSONObject(hypothesis).optString("partial", "").trim()
            } catch (_: Exception) { "" }

            if (partial.isNotBlank()) {
                val norm = normalize(partial)
                val hasWakeWord = containsWakeWord(norm)
                val allowWithoutWakeWord = isManualPushToTalk || isAwaitingDisambiguation.value

                // SOLO responder si se dijo "Arlet" O si el usuario presionó el botón de hablar en pantalla
                if (!hasWakeWord && !allowWithoutWakeWord) {
                    return
                }

                showOverlay.value = true
                isListening.value = true
                lastSpokenQuery.value = partial
                if (assistantResponse.value.isBlank() || assistantResponse.value == "Listo") {
                    assistantResponse.value = "Te escucho..."
                }

                // Si ya se identificó un comando claro, procesarlo
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

        override fun onResult(hypothesis: String) {
            val now = System.currentTimeMillis()
            if (isSpeaking.value || now < speechMutedUntil) {
                return
            }

            val text = try {
                JSONObject(hypothesis).optString("text", "").trim()
            } catch (_: Exception) { "" }

            if (text.isNotBlank()) {
                Log.i(TAG, "Vosk onResult: '$text'")
                val norm = normalize(text)
                val hasWakeWord = containsWakeWord(norm)
                val allowWithoutWakeWord = isManualPushToTalk || isAwaitingDisambiguation.value

                if (hasWakeWord || allowWithoutWakeWord) {
                    handleSpeechInput(text)
                }
            }
        }

        override fun onFinalResult(hypothesis: String) {
            val now = System.currentTimeMillis()
            if (isSpeaking.value || now < speechMutedUntil) {
                return
            }

            val text = try {
                JSONObject(hypothesis).optString("text", "").trim()
            } catch (_: Exception) { "" }

            if (text.isNotBlank()) {
                val norm = normalize(text)
                val hasWakeWord = containsWakeWord(norm)
                val allowWithoutWakeWord = isManualPushToTalk || isAwaitingDisambiguation.value

                if (hasWakeWord || allowWithoutWakeWord) {
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
        val now = System.currentTimeMillis()
        if (isSpeaking.value || now < speechMutedUntil) {
            Log.d(TAG, "Ignorando audio: Arlet hablando o en silencio post-voz")
            return
        }

        // Enfriamiento de 2.5s entre ejecuciones de acciones para evitar doble disparo (partial + final)
        if (now - lastActionExecutedTime < 2500L) {
            Log.d(TAG, "Ignorando orden dentro del periodo de enfriamiento ($now - $lastActionExecutedTime)")
            return
        }

        val normalized = normalize(rawText)
        if (now - lastHandledTime < 2000L && normalized == lastHandledText) {
            return
        }

        val hasWakeWord = containsWakeWord(normalized)
        val allowWithoutWakeWord = isManualPushToTalk || isAwaitingDisambiguation.value

        // Garantía estricta: No procesar órdenes si no se nombró a Arlet y no es toque manual ni desambiguación
        if (!hasWakeWord && !allowWithoutWakeWord) {
            Log.i(TAG, "Ignorando orden sin nombre de invocación Arlet: '$rawText'")
            return
        }

        lastHandledTime = now
        lastHandledText = normalized
        mainHandler.removeCallbacks(silenceWatchdog)
        isManualPushToTalk = false

        // Inmediatamente apagar escucha, silenciar y pausar Vosk
        isListening.value = false
        audioRms.value = 0f
        voskSpeechService?.setPause(true)
        voskSpeechService?.reset()

        Log.i(TAG, "Audio capturado: '$rawText' -> Normalizado: '$normalized'")

        lastSpokenQuery.value = rawText
        showOverlay.value = true

        // 1. Caso: Esperando desambiguación de destino ("el primero", "el segundo", o nombre)
        if (isAwaitingDisambiguation.value && disambiguationOptions.isNotEmpty()) {
            lastActionExecutedTime = now
            handleDisambiguationChoice(normalized)
            return
        }

        // Limpiar "arlet" y variantes fonéticas
        var command = stripWakeWord(normalized)

        if (command.isBlank() || command == "oye" || command == "hola") {
            lastActionExecutedTime = now
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
            lastActionExecutedTime = now
            handleStartRecording()
            return
        }

        if (isCamStop) {
            lastActionExecutedTime = now
            handleStopRecording()
            return
        }

        // ── COMANDOS DE MÚSICA ──────────────────────────────────────────────
        val isMusicPause = command.contains("pon pausa") || command.contains("pausa la musica") ||
                           command.contains("pausar la musica") || command.contains("para la musica") ||
                           command.contains("deten la musica") || command == "pausa" || command == "pausar"

        val isMusicPlay = command.contains("reproduce") || command.contains("reproducir") ||
                          command.contains("continua la musica") || command.contains("pon musica") ||
                          command.contains("sigue la musica") || command == "play"

        val isMusicNext = command.contains("siguiente cancion") || command.contains("pasa la cancion") ||
                          command.contains("cambia la cancion") || command.contains("siguiente pista") ||
                          command.contains("proxima cancion") || command.contains("otra cancion") ||
                          command.contains("adelanta la cancion") || command == "siguiente" || command == "adelanta"

        val isMusicPrev = command.contains("cancion anterior") || command.contains("pista anterior") ||
                          command.contains("retrocede la cancion") || command.contains("vuelve la cancion") ||
                          command == "anterior" || command == "retrocede"

        if (isMusicPause) {
            lastActionExecutedTime = now
            GlobalState.mediaController?.transportControls?.pause()
            speak("Música pausada")
            return
        }

        if (isMusicPlay) {
            lastActionExecutedTime = now
            GlobalState.mediaController?.transportControls?.play()
            speak("Reproduciendo música")
            return
        }

        if (isMusicNext) {
            lastActionExecutedTime = now
            GlobalState.skipToNext()
            speak("Siguiente canción")
            return
        }

        if (isMusicPrev) {
            lastActionExecutedTime = now
            GlobalState.skipToPrevious()
            speak("Canción anterior")
            return
        }

        // ── COMANDOS DE NAVEGACIÓN A LUGARES GUARDADOS O MAPA ────────────────
        val navTriggers = listOf(
            // Traza / Trazar ruta
            "traza la ruta para", "traza la ruta hacia", "traza la ruta a", "traza la ruta al",
            "traza ruta para", "traza ruta hacia", "traza ruta a", "traza ruta al",
            "trazar la ruta para", "trazar la ruta hacia", "trazar la ruta a", "trazar la ruta al",
            "trazar ruta para", "trazar ruta hacia", "trazar ruta a", "trazar ruta al",
            // Inicia / Iniciar ruta
            "inicia la ruta para", "inicia la ruta hacia", "inicia la ruta a", "inicia la ruta al",
            "inicia ruta para", "inicia ruta hacia", "inicia ruta a", "inicia ruta al",
            "iniciar la ruta para", "iniciar la ruta hacia", "iniciar la ruta a", "iniciar la ruta al",
            "iniciar ruta para", "iniciar ruta hacia", "iniciar ruta a", "iniciar ruta al",
            // Pon / Poner ruta
            "pon la ruta para", "pon la ruta hacia", "pon la ruta a", "pon la ruta al",
            "pon ruta para", "pon ruta hacia", "pon ruta a", "pon ruta al",
            // Vamos
            "vamos para", "vamos hacia", "vamos a la", "vamos al", "vamos a",
            // Llévame / Guíame / Conduce / Navega
            "llevame para", "llevame hacia", "llevame al", "llevame a la", "llevame a",
            "guiame para", "guiame hacia", "guiame al", "guiame a la", "guiame a",
            "conduce para", "conduce hacia", "conduce a la", "conduce al", "conduce a",
            "navega para", "navega hacia", "navega a la", "navega al", "navega a",
            "navegar para", "navegar hacia", "navegar a",
            // Ir / Cómo llego
            "ir para", "ir hacia", "ir al", "ir a la", "ir a",
            "como llego a la", "como llego al", "como llego a",
            "como llegar a", "como voy para", "como voy a",
            // Destino / Dirección / Rumbo
            "rumbo a", "rumbo hacia", "rumbo para",
            "direccion a", "direccion hacia", "direccion para",
            "destino a", "destino"
        )

        var destinationQuery: String? = null
        for (trigger in navTriggers) {
            if (command.contains(trigger)) {
                destinationQuery = command.substringAfter(trigger).trim()
                break
            }
        }

        if (destinationQuery != null && destinationQuery.isNotBlank()) {
            lastActionExecutedTime = now
            handleNavigationToSavedPlace(destinationQuery)
            return
        }

        // ── SALUDOS / AYUDA ─────────────────────────────────────────────────
        if (command.contains("hola") || command.contains("buenos dias") || command.contains("buenas tardes")) {
            lastActionExecutedTime = now
            speak("¡Hola! Estoy lista para el camino. ¿A dónde vamos o qué deseas hacer?")
            return
        }

        if (command.contains("quien eres") || command.contains("como te llamas")) {
            lastActionExecutedTime = now
            speak("Soy Arlet, tu copiloto virtual para este viaje.")
            return
        }

        if (command.contains("ayuda") || command.contains("que puedes hacer") || command.contains("comandos")) {
            lastActionExecutedTime = now
            speak("Puedo iniciar la grabación de la cámara, controlar la música y guiarte a tus lugares favoritos.")
            return
        }

        if (command.contains("gracias")) {
            lastActionExecutedTime = now
            speak("¡De nada! Maneja con cuidado.")
            return
        }

        // Comando no reconocido
        lastActionExecutedTime = now
        speak("No entendí esa orden. Puedes pedirme grabar con la cámara, controlar la música o trazar una ruta.")
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

        // Limpiar preposiciones y artículos determinantes
        var cleanTarget = query.trim()
        val stopWords = listOf(
            "para la ", "para el ", "para los ", "para las ", "para ",
            "hacia la ", "hacia el ", "hacia los ", "hacia las ", "hacia ",
            "a la ", "a las ", "al ", "a los ", "a ",
            "el ", "la ", "los ", "las ", "mi ", "mis ", "un ", "una "
        )
        for (sw in stopWords) {
            if (cleanTarget.startsWith(sw)) {
                cleanTarget = cleanTarget.removePrefix(sw).trim()
            }
        }

        if (cleanTarget.isBlank()) {
            speak("¿A qué destino deseas ir?")
            return
        }

        val favManager = FavoritesManager(ctx)
        val allFavorites = favManager.getFavorites()
        val targetNorm = normalize(cleanTarget)

        // 1. Buscar en favoritos guardados
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
            matches.size == 1 -> {
                val selected = matches.first()
                selectDestination(selected)
            }
            matches.size > 1 -> {
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
            else -> {
                // 2. Si no está en favoritos, buscar en el mapa en línea
                speak("Buscando ruta para $cleanTarget...")
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val searchResults = searchPlaces(cleanTarget, null)
                        if (searchResults.isEmpty()) {
                            speak("No encontré ningún lugar que coincida con $cleanTarget")
                        } else if (searchResults.size == 1) {
                            selectDestination(searchResults.first())
                        } else {
                            val topResults = searchResults.take(3)
                            disambiguationOptions.clear()
                            disambiguationOptions.addAll(topResults)
                            isAwaitingDisambiguation.value = true
                            showOverlay.value = true
                            speak("Encontré varias opciones en el mapa. Selecciona una en pantalla o di 'la primera'.")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error buscando en mapa", e)
                        speak("No encontré ningún lugar guardado que coincida con $cleanTarget")
                    }
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
