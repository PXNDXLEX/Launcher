package com.tuusuario.carlauncher.services

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateOf

// Objeto global para compartir la música con la Interfaz (Jetpack Compose)
object MusicState {
    val title = mutableStateOf("Música detenida")
    val artist = mutableStateOf("")
    val isPlaying = mutableStateOf(false)
}

class MusicNotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val extras = notification.extras

        // Verificamos si la notificación tiene una sesión multimedia (Spotify, YT Music, etc.)
        if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION) || extras.containsKey(Notification.EXTRA_TITLE)) {
            val title = extras.getString(Notification.EXTRA_TITLE)
            val artist = extras.getString(Notification.EXTRA_TEXT)

            if (!title.isNullOrEmpty()) {
                MusicState.title.value = title
                MusicState.artist.value = artist ?: "Desconocido"
                MusicState.isPlaying.value = true
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Opcional: Podríamos limpiar la información aquí si se cierra la app de música
    }
}