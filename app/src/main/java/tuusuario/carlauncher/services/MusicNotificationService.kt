package tuusuario.carlauncher.services

import android.app.Notification
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateOf

// Objeto global para compartir el estado de la música con los widgets
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
        // Método para forzar la reconexión del servicio y evitar que Android lo "duerma"
        fun reconnect(context: android.content.Context) {
            try {
                requestRebind(ComponentName(context, MusicNotificationService::class.java))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val extras = notification.extras

        // Verificamos si la notificación es de un reproductor multimedia
        if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) {
            val title = extras.getString(Notification.EXTRA_TITLE)
            val artist = extras.getString(Notification.EXTRA_TEXT)
            
            // Intentamos extraer la carátula del álbum (Large Icon)
            val largeIcon = extras.getParcelable<Bitmap>(Notification.EXTRA_LARGE_ICON) 
                ?: extractBitmapFromIcon(notification.getLargeIcon())

            if (!title.isNullOrEmpty()) {
                GlobalState.songTitle.value = title
                GlobalState.songArtist.value = artist ?: "Desconocido"
                GlobalState.songAlbumArt.value = largeIcon
            }
        } else {
            // Si no es música, la tratamos como una notificación normal (ej: WhatsApp)
            val title = extras.getString(Notification.EXTRA_TITLE)
            val text = extras.getString(Notification.EXTRA_TEXT)
            if (!title.isNullOrEmpty() && !text.isNullOrEmpty() && sbn.packageName != packageName && sbn.packageName != "android") {
                GlobalState.popupApp.value = title
                GlobalState.popupMessage.value = text
                GlobalState.showPopup.value = true
            }
        }
    }

    // Convierte el objeto Icon de Android en un Bitmap que Compose pueda dibujar
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
        // Al conectar, escaneamos las notificaciones actuales para mostrar lo que ya esté sonando
        activeNotifications?.forEach { onNotificationPosted(it) }
    }
}