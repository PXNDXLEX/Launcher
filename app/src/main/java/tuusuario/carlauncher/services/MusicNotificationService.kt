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
        // ESCUDO PROTECTOR: Evita que la app se cierre si una notificación viene defectuosa
        try {
            val notification = sbn?.notification ?: return
            val extras = notification.extras

            // Verificamos si es una notificación de música
            if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) {
                // Usamos getCharSequence para evitar errores si el texto viene con formato
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Sin título"
                val artist = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "Artista desconocido"
                
                // --- CORRECCIÓN CRÍTICA DE LA IMAGEN ---
                var largeIcon: Bitmap? = null

                // Intento 1: Método moderno (Android 6.0+)
                val iconObj = notification.getLargeIcon()
                if (iconObj != null) {
                    largeIcon = extractBitmapFromIcon(iconObj)
                }

                // Intento 2: Fallback para el formato clásico en los extras
                if (largeIcon == null) {
                    val extraIcon = extras.get(Notification.EXTRA_LARGE_ICON)
                    if (extraIcon is Bitmap) {
                        largeIcon = extraIcon
                    } else if (extraIcon is Icon) {
                        largeIcon = extractBitmapFromIcon(extraIcon)
                    }
                }

                GlobalState.songTitle.value = title
                GlobalState.songArtist.value = artist
                GlobalState.songAlbumArt.value = largeIcon
            } else {
                // Notificaciones normales (WhatsApp, etc.)
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                if (!title.isNullOrEmpty() && !text.isNullOrEmpty() && sbn.packageName != packageName && sbn.packageName != "android") {
                    GlobalState.popupApp.value = title
                    GlobalState.popupMessage.value = text
                    GlobalState.showPopup.value = true
                }
            }
        } catch (e: Exception) {
            // Si algo falla, lo ignoramos pero NO cerramos la app
            e.printStackTrace()
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
        try {
            // Al conectar, revisamos qué está sonando ahora mismo de forma segura
            activeNotifications?.forEach { onNotificationPosted(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}