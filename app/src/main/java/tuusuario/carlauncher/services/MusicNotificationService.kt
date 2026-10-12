package com.tuusuario.carlauncher.services

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.KeyEvent
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

object GlobalState {
    private const val TAG = "GlobalState"
    private const val PREFS_NAME = "MusicStatePrefs"
    private const val KEY_TITLE = "last_song_title"
    private const val KEY_ARTIST = "last_song_artist"
    private const val KEY_DURATION = "last_song_duration"
    private const val KEY_PACKAGE = "last_package_name"
    private const val ALBUM_ART_FILE = "last_album_art.png"

    val songTitle = mutableStateOf("Música detenida")
    val songArtist = mutableStateOf("")
    val songAlbumArt = mutableStateOf<Bitmap?>(null)

    val isPlaying = mutableStateOf(false)
    var mediaController: MediaController? = null
    var lastPackageName: String? = null
    var appContext: Context? = null

    val songDuration = mutableStateOf(0L)   // duración total en ms
    val songPosition = mutableStateOf(0L)   // posición actual en ms

    val showPopup = mutableStateOf(false)
    val popupMessage = mutableStateOf("")
    val popupApp = mutableStateOf("")

    private val mainHandler = Handler(Looper.getMainLooper())
    private var controllerCallback: MediaController.Callback? = null

    fun init(context: Context) {
        this.appContext = context.applicationContext
        loadPersistedState(context.applicationContext)
    }

    private fun loadPersistedState(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedTitle = prefs.getString(KEY_TITLE, null)
            val savedArtist = prefs.getString(KEY_ARTIST, null)
            val savedDuration = prefs.getLong(KEY_DURATION, 0L)
            val savedPackage = prefs.getString(KEY_PACKAGE, null)

            if (!savedTitle.isNullOrBlank()) {
                songTitle.value = savedTitle
                songArtist.value = savedArtist.orEmpty()
                if (savedDuration > 0) songDuration.value = savedDuration
                lastPackageName = savedPackage
                Log.i(TAG, "Pista previa restaurada: '$savedTitle' - '$savedArtist' (app: $savedPackage)")
            }

            // Cargar carátula persistida desde almacenamiento interno
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val file = File(context.filesDir, ALBUM_ART_FILE)
                    if (file.exists() && file.length() > 0) {
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                        if (bitmap != null) {
                            mainHandler.post {
                                if (songAlbumArt.value == null) {
                                    songAlbumArt.value = bitmap
                                    Log.d(TAG, "Carátula previa restaurada desde disco")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error cargando carátula previa de disco", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error leyendo preferencias de música", e)
        }
    }

    fun saveTrack(context: Context?, title: String, artist: String, duration: Long, pkg: String?, art: Bitmap?) {
        val ctx = context ?: appContext ?: return
        if (title.isBlank() || isGenericWebTitle(title) || title == "Sin título" || title == "Música detenida") {
            return
        }

        try {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_TITLE, title)
                .putString(KEY_ARTIST, artist)
                .putLong(KEY_DURATION, duration)
                .putString(KEY_PACKAGE, pkg ?: lastPackageName.orEmpty())
                .apply()

            if (!pkg.isNullOrBlank()) {
                lastPackageName = pkg
            }

            if (art != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val file = File(ctx.filesDir, ALBUM_ART_FILE)
                        FileOutputStream(file).use { out ->
                            art.compress(Bitmap.CompressFormat.PNG, 95, out)
                        }
                        Log.d(TAG, "Carátula guardada en disco exitosamente")
                    } catch (e: Exception) {
                        Log.w(TAG, "Error guardando carátula en disco", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando información de la canción", e)
        }
    }

    fun isGenericWebTitle(title: String): Boolean {
        val lower = title.lowercase()
        return lower.contains("un sitio está reproducien") ||
               lower.contains("un sitio web") ||
               lower.contains("reproduciendo audio")
    }

    fun setController(controller: MediaController?, context: Context?) {
        if (controller == null) return

        try {
            controllerCallback?.let { mediaController?.unregisterCallback(it) }
        } catch (_: Exception) {}

        mediaController = controller
        val pkg = controller.packageName
        if (!pkg.isNullOrBlank() && !pkg.contains("chrome") && !pkg.contains("browser")) {
            lastPackageName = pkg
        }

        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                mainHandler.post {
                    val playing = state?.state == PlaybackState.STATE_PLAYING
                    isPlaying.value = playing
                    state?.let { songPosition.value = it.position }
                }
            }

            override fun onMetadataChanged(meta: MediaMetadata?) {
                if (meta == null) return
                val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE)
                    ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                    ?: meta.getString(MediaMetadata.METADATA_KEY_AUTHOR)
                val dur = meta.getLong(MediaMetadata.METADATA_KEY_DURATION)
                val art = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

                mainHandler.post {
                    if (!title.isNullOrBlank() && !isGenericWebTitle(title)) {
                        songTitle.value = title
                        if (!artist.isNullOrBlank()) songArtist.value = artist
                        if (dur > 0) songDuration.value = dur
                        if (art != null) songAlbumArt.value = art
                        saveTrack(context, songTitle.value, songArtist.value, songDuration.value, pkg, art)
                    }
                }
            }
        }

        controllerCallback = callback
        try {
            controller.registerCallback(callback)
        } catch (e: Exception) {
            Log.w(TAG, "Error registrando callback en controller", e)
        }

        // Leer estado actual
        val state = controller.playbackState
        isPlaying.value = state?.state == PlaybackState.STATE_PLAYING
        state?.let { songPosition.value = it.position }

        val meta = controller.metadata
        if (meta != null) {
            val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            val dur = meta.getLong(MediaMetadata.METADATA_KEY_DURATION)
            val art = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)

            if (!title.isNullOrBlank() && !isGenericWebTitle(title)) {
                songTitle.value = title
                if (!artist.isNullOrBlank()) songArtist.value = artist
                if (dur > 0) songDuration.value = dur
                if (art != null) songAlbumArt.value = art
                saveTrack(context, songTitle.value, songArtist.value, songDuration.value, pkg, art)
            }
        }
    }

    fun togglePlayPause() {
        if (isPlaying.value) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        val controller = mediaController
        if (controller != null) {
            try {
                controller.transportControls.play()
                isPlaying.value = true
                Log.i(TAG, "play() enviado a MediaController (${controller.packageName})")
                return
            } catch (e: Exception) {
                Log.w(TAG, "Fallo al enviar play a mediaController, intentando despertar reproductor...", e)
            }
        }

        // Si no hay controller o la app está cerrada en segundo plano
        wakeUpAndPlayLastPlayer()
    }

    fun pause() {
        val controller = mediaController
        if (controller != null) {
            try {
                controller.transportControls.pause()
                isPlaying.value = false
                Log.i(TAG, "pause() enviado a MediaController (${controller.packageName})")
                return
            } catch (e: Exception) {
                Log.w(TAG, "Fallo al enviar pause a mediaController", e)
            }
        }
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
        isPlaying.value = false
    }

    fun skipToNext() {
        val controller = mediaController
        if (controller != null) {
            try {
                controller.transportControls.skipToNext()
                return
            } catch (_: Exception) {}
        }
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun skipToPrevious() {
        val controller = mediaController
        if (controller != null) {
            try {
                controller.transportControls.skipToPrevious()
                return
            } catch (_: Exception) {}
        }
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    fun seekTo(positionMs: Long) {
        mediaController?.transportControls?.seekTo(positionMs)
    }

    private fun wakeUpAndPlayLastPlayer() {
        val ctx = appContext ?: return
        val pkg = lastPackageName
        Log.i(TAG, "Despertando última app de música: pkg='$pkg'")

        // 1. Enviar ACTION_MEDIA_BUTTON dirigido directamente al paquete guardado
        if (!pkg.isNullOrBlank()) {
            try {
                val downIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                    setPackage(pkg)
                    putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
                }
                ctx.sendOrderedBroadcast(downIntent, null)

                val upIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                    setPackage(pkg)
                    putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
                }
                ctx.sendOrderedBroadcast(upIntent, null)
                Log.i(TAG, "Enviado ACTION_MEDIA_BUTTON explícito a $pkg")
            } catch (e: Exception) {
                Log.w(TAG, "Error enviando intent de media button a $pkg", e)
            }
        }

        // 2. Despachar KeyEvent a nivel de hardware/sistema AudioManager
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)

        // Marcar optimísticamente como reproduciendo
        isPlaying.value = true

        // 3. Forzar refresco para capturar la nueva sesión si la app responde
        mainHandler.postDelayed({
            MusicNotificationService.instance?.refreshCurrentMedia()
        }, 1200)
    }

    private fun dispatchMediaKey(keyCode: Int) {
        val ctx = appContext ?: return
        try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (am != null) {
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
                Log.d(TAG, "Despachado KeyEvent $keyCode vía AudioManager")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error despachando KeyEvent $keyCode", e)
        }
    }
}

class MusicNotificationService : NotificationListenerService() {

    companion object {
        var instance: MusicNotificationService? = null

        fun reconnect(context: Context) {
            try {
                // TRUCO MAESTRO: Apagamos y encendemos el componente a nivel de sistema.
                // Esto fuerza a Android a reiniciar la lectura de notificaciones sin que el usuario tenga que ir a los ajustes.
                val pm = context.packageManager
                val componentName = ComponentName(context, MusicNotificationService::class.java)

                pm.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                pm.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        GlobalState.init(this)

        try {
            val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val componentName = ComponentName(this, MusicNotificationService::class.java)
            mediaSessionManager?.addOnActiveSessionsChangedListener({ controllers ->
                findAndSetBestController(controllers)
            }, componentName)
        } catch (e: Exception) {
            Log.w("MusicNotification", "No se pudo registrar listener de sesiones activas", e)
        }

        refreshCurrentMedia()
    }

    override fun onListenerDisconnected() {
        instance = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
            val notification = sbn?.notification ?: return
            val extras = notification.extras ?: return

            if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) {
                val tokenObj = extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
                val controller = if (tokenObj != null) MediaController(this, tokenObj) else null

                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Sin título"
                val artist = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "Artista desconocido"

                val isBrowser = sbn.packageName.contains("chrome") ||
                                sbn.packageName.contains("browser") ||
                                sbn.packageName.contains("webview")

                // Si es un audio genérico de navegador y no está reproduciendo activamente, no sobreescribir la música real
                if (GlobalState.isGenericWebTitle(title) || (isBrowser && controller?.playbackState?.state != PlaybackState.STATE_PLAYING)) {
                    if (GlobalState.songTitle.value != "Música detenida" && !GlobalState.isGenericWebTitle(GlobalState.songTitle.value)) {
                        Log.d("MusicNotification", "Ignorando audio genérico de navegador: $title ($artist)")
                        return
                    }
                }

                if (controller != null) {
                    GlobalState.setController(controller, this)
                }

                var largeIcon: Bitmap? = null
                val iconObj = notification.getLargeIcon()
                if (iconObj != null) largeIcon = extractBitmapFromIcon(iconObj)
                if (largeIcon == null) {
                    val extraIcon = extras.get(Notification.EXTRA_LARGE_ICON)
                    if (extraIcon is Bitmap) largeIcon = extraIcon else if (extraIcon is Icon) largeIcon = extractBitmapFromIcon(extraIcon)
                }

                if (!GlobalState.isGenericWebTitle(title) && title != "Sin título") {
                    GlobalState.songTitle.value = title
                    GlobalState.songArtist.value = artist
                    if (largeIcon != null) {
                        GlobalState.songAlbumArt.value = largeIcon
                    }
                    GlobalState.saveTrack(this, title, artist, GlobalState.songDuration.value, sbn.packageName, largeIcon)
                }
            } else {
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                if (!title.isNullOrEmpty() && !text.isNullOrEmpty() && sbn.packageName != packageName && sbn.packageName != "android") {
                    GlobalState.popupApp.value = title
                    GlobalState.popupMessage.value = text
                    GlobalState.showPopup.value = true

                    CoroutineScope(Dispatchers.Main).launch {
                        delay(2500)
                        GlobalState.showPopup.value = false
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        try {
            val extras = sbn?.notification?.extras
            if (extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true) {
                // Si se quita la notificación, marcar pausado pero PRESERVAR los datos de la última pista y carátula
                GlobalState.isPlaying.value = false
            }
        } catch (_: Exception) {}
    }

    private fun extractBitmapFromIcon(icon: Icon?): Bitmap? {
        try {
            val drawable = icon?.loadDrawable(this)
            if (drawable is android.graphics.drawable.BitmapDrawable) return drawable.bitmap
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun refreshCurrentMedia() {
        try {
            // 1. Consultar MediaSessionManager para enlazar la sesión de música activa
            try {
                val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
                val componentName = ComponentName(this, MusicNotificationService::class.java)
                val controllers = mediaSessionManager?.getActiveSessions(componentName)
                if (!controllers.isNullOrEmpty()) {
                    findAndSetBestController(controllers)
                }
            } catch (e: Exception) {
                Log.w("MusicNotification", "Error consultando getActiveSessions", e)
            }

            // 2. Procesar notificaciones activas dando prioridad a apps de música reales
            val notifications = activeNotifications ?: return
            val sorted = notifications.sortedByDescending { sbn ->
                val hasMedia = sbn.notification?.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true
                val isBrowser = sbn.packageName.contains("chrome") || sbn.packageName.contains("browser")
                if (hasMedia && !isBrowser) 2 else if (hasMedia) 1 else 0
            }

            for (sbn in sorted) {
                if (sbn.notification?.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true) {
                    onNotificationPosted(sbn)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun findAndSetBestController(controllers: List<MediaController>?) {
        if (controllers.isNullOrEmpty()) return

        // Priorizar el que esté reproduciendo actualmente
        val active = controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING &&
            !it.packageName.contains("chrome") && !it.packageName.contains("browser")
        } ?: controllers.firstOrNull {
            !it.packageName.contains("chrome") && !it.packageName.contains("browser")
        } ?: controllers.firstOrNull()

        if (active != null) {
            GlobalState.setController(active, this)
        }
    }
}