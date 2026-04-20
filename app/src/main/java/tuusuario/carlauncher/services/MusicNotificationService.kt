package com.tuusuario.carlauncher.services

import android.app.Notification
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object GlobalState {
    val songTitle = mutableStateOf("Música detenida")
    val songArtist = mutableStateOf("")
    val songAlbumArt = mutableStateOf<Bitmap?>(null)
    
    val isPlaying = mutableStateOf(false)
    var mediaController: MediaController? = null
    
    val showPopup = mutableStateOf(false)
    val popupMessage = mutableStateOf("")
    val popupApp = mutableStateOf("")

    fun togglePlayPause() {
        if (isPlaying.value) mediaController?.transportControls?.pause() else mediaController?.transportControls?.play()
    }
    fun skipToNext() { mediaController?.transportControls?.skipToNext() }
    fun skipToPrevious() { mediaController?.transportControls?.skipToPrevious() }
}

class MusicNotificationService : NotificationListenerService() {

    companion object {
        var instance: MusicNotificationService? = null

        fun reconnect(context: android.content.Context) {
            try {
                // TRUCO MAESTRO: Apagamos y encendemos el componente a nivel de sistema.
                // Esto fuerza a Android a reiniciar la lectura de notificaciones sin que el usuario tenga que ir a los ajustes.
                val pm = context.packageManager
                val componentName = ComponentName(context, MusicNotificationService::class.java)
                
                // Deshabilitar
                pm.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                // Habilitar inmediatamente
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

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
            val notification = sbn?.notification ?: return
            val extras = notification.extras

            if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) {
                val tokenObj = extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
                if (tokenObj != null) {
                    val controller = MediaController(this, tokenObj)
                    GlobalState.mediaController = controller
                    val state = controller.playbackState
                    GlobalState.isPlaying.value = state?.state == PlaybackState.STATE_PLAYING
                }

                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Sin título"
                val artist = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "Artista desconocido"
                var largeIcon: Bitmap? = null

                val iconObj = notification.getLargeIcon()
                if (iconObj != null) largeIcon = extractBitmapFromIcon(iconObj)
                if (largeIcon == null) {
                    val extraIcon = extras.get(Notification.EXTRA_LARGE_ICON)
                    if (extraIcon is Bitmap) largeIcon = extraIcon else if (extraIcon is Icon) largeIcon = extractBitmapFromIcon(extraIcon)
                }

                GlobalState.songTitle.value = title
                GlobalState.songArtist.value = artist
                GlobalState.songAlbumArt.value = largeIcon
            } else {
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                if (!title.isNullOrEmpty() && !text.isNullOrEmpty() && sbn.packageName != packageName && sbn.packageName != "android") {
                    GlobalState.popupApp.value = title
                    GlobalState.popupMessage.value = text
                    GlobalState.showPopup.value = true
                    
                    // ARREGLO INYECTADO: Auto-ocultar notificación después de 2.5 segundos
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(2500)
                        GlobalState.showPopup.value = false
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun extractBitmapFromIcon(icon: Icon?): Bitmap? {
        try {
            val drawable = icon?.loadDrawable(this)
            if (drawable is android.graphics.drawable.BitmapDrawable) return drawable.bitmap
        } catch (e: Exception) { e.printStackTrace() }
        return null
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        refreshCurrentMedia()
    }

    override fun onListenerDisconnected() {
        instance = null
        super.onListenerDisconnected()
    }

    fun refreshCurrentMedia() {
        try {
            activeNotifications?.forEach { onNotificationPosted(it) }
        } catch (e: Exception) { e.printStackTrace() }
    }
}