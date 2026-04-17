package com.tuusuario.carlauncher.services

import android.app.Notification
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateOf

object GlobalState {
    val songTitle = mutableStateOf("Música detenida")
    val songArtist = mutableStateOf("")
    val songAlbumArt = mutableStateOf<Bitmap?>(null) // Nuevo: Guardamos la imagen
    
    val showPopup = mutableStateOf(false)
    val popupMessage = mutableStateOf("")
    val popupApp = mutableStateOf("")
}

class MusicNotificationService : NotificationListenerService() {

    // Función para "despertar" el servicio desde la MainActivity
    companion object {
        fun reconnect(context: android.content.Context) {
            requestRebind(ComponentName(context, MusicNotificationService::class.java))
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val extras = notification.extras

        if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) {
            val title = extras.getString(Notification.EXTRA_TITLE)
            val artist = extras.getString(Notification.EXTRA_TEXT)
            
            // Extraemos la carátula (Large Icon)
            val largeIcon = extras.getParcelable<Bitmap>(Notification.EXTRA_LARGE_ICON) 
                ?: extractBitmapFromIcon(notification.getLargeIcon())

            if (!title.isNullOrEmpty()) {
                GlobalState.songTitle.value = title
                GlobalState.songArtist.value = artist ?: "Desconocido"
                GlobalState.songAlbumArt.value = largeIcon // Actualizamos la imagen
            }
        } else {
            val title = extras.getString(Notification.EXTRA_TITLE)
            val text = extras.getString(Notification.EXTRA_TEXT)
            if (!title.isNullOrEmpty() && !text.isNullOrEmpty() && sbn.packageName != packageName && sbn.packageName != "android") {
                GlobalState.popupApp.value = title
                GlobalState.popupMessage.value = text
                GlobalState.showPopup.value = true
            }
        }
    }

    // Helper para convertir Icon a Bitmap si es necesario
    private fun extractBitmapFromIcon(icon: Icon?): Bitmap? {
        try {
            val drawable = icon?.loadDrawable(this)
            if (drawable is android.graphics.drawable.BitmapDrawable) {
                return drawable.bitmap
            }
        } catch (e: Exception) { e.printStackTrace() }
        return null
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Cuando se conecta, intentamos capturar lo que ya está sonando
        val activeNotifications = activeNotifications
        activeNotifications?.forEach { onNotificationPosted(it) }
    }
}