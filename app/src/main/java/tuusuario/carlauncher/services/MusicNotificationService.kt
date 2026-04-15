package com.tuusuario.carlauncher.services

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateOf

object GlobalState {
    // Estado de Música
    val songTitle = mutableStateOf("Música detenida")
    val songArtist = mutableStateOf("")
    
    // Estado de Popups (WhatsApp, Mensajes, etc)
    val showPopup = mutableStateOf(false)
    val popupMessage = mutableStateOf("")
    val popupApp = mutableStateOf("")
}

class MusicNotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val extras = notification.extras

        // ¿Es un reproductor de música? (Tiene Media Session)
        if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) {
            val title = extras.getString(Notification.EXTRA_TITLE)
            val artist = extras.getString(Notification.EXTRA_TEXT)
            if (!title.isNullOrEmpty()) {
                GlobalState.songTitle.value = title
                GlobalState.songArtist.value = artist ?: "Desconocido"
            }
        } 
        // Si NO es música, pero es un mensaje importante (ej. WhatsApp)
        else {
            val title = extras.getString(Notification.EXTRA_TITLE)
            val text = extras.getString(Notification.EXTRA_TEXT)
            if (!title.isNullOrEmpty() && !text.isNullOrEmpty() && sbn.packageName != packageName) {
                GlobalState.popupApp.value = title // Suele ser el nombre del contacto
                GlobalState.popupMessage.value = text
                GlobalState.showPopup.value = true
            }
        }
    }
}