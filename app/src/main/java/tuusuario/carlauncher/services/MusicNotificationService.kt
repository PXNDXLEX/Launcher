package com.tuusuario.carlauncher.services

import android.app.Notification
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateOf

// Objeto global para compartir el estado de la música con toda la aplicación
object GlobalState {
    val songTitle = mutableStateOf("Música detenida")
    val songArtist = mutableStateOf("")
    val songAlbumArt = mutableStateOf<Bitmap?>(null)
    
    val showPopup = mutableStateOf(false)
    val popupMessage = mutableStateOf("")
    val popupApp = mutableStateOf("")
}

class MusicNotificationService : NotificationListenerService() {

    companion object {
        // Método para forzar la reconexión del servicio
        fun reconnect(context: android.content.Context) {
            try {
                requestRebind(ComponentName(context, MusicNotificationService::class.java))
            } catch (e: Exception) { 
                e.printStackTrace() 
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val extras = notification.extras

        // Verificamos si es una notificación de música
        if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) {
            val title = extras.getString(Notification.EXTRA_TITLE) ?: "Sin título"
            val artist = extras.getString(Notification.EXTRA_TEXT) ?: "Artista desconocido"
            
            // Extraer la carátula
            val largeIcon = extras.getParcelable<Bitmap>(Notification.EXTRA_LARGE_ICON) 
                ?: extractBitmapFromIcon(notification.getLargeIcon())

            GlobalState.songTitle.value = title
            GlobalState.songArtist.value = artist
            GlobalState.songAlbumArt.value = largeIcon
        } else {
            // Notificaciones normales (WhatsApp, etc.)
            val title = extras.getString(Notification.EXTRA_TITLE)
            val text = extras.getString(Notification.EXTRA_TEXT)
            if (!title.isNullOrEmpty() && !text.isNullOrEmpty() && sbn.packageName != packageName && sbn.packageName != "android") {
                GlobalState.popupApp.value = title
                GlobalState.popupMessage.value = text
                GlobalState.showPopup.value = true
            }
        }
    }

    private fun extractBitmapFromIcon(icon: Icon?): Bitmap? {
        try {
            val drawable = icon?.loadDrawable(this)
            if (drawable is android.graphics.drawable.BitmapDrawable) {
                return drawable.bitmap
            }
        } catch (e: Exception) { 
            e.printStackTrace() 
        }
        return null
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Al conectar, revisamos qué está sonando ahora mismo
        activeNotifications?.forEach { onNotificationPosted(it) }
    }
}