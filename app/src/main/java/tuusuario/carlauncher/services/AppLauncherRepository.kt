package com.tuusuario.carlauncher.services

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap?
)

/**
 * Caché asíncrono de aplicaciones instaladas para el cajón "Todas las apps".
 * Pre-rasteriza los íconos en segundo plano (96x96) para que el scroll sea a 120 FPS sin tirones.
 */
object AppLauncherRepository {

    val installedApps = mutableStateOf<List<InstalledAppInfo>>(emptyList())
    val isLoading = mutableStateOf(false)

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var hasLoadedOnce = false

    fun preload(context: Context, force: Boolean = false) {
        if (isLoading.value) return
        if (hasLoadedOnce && !force) return

        isLoading.value = true
        val appCtx = context.applicationContext
        ioScope.launch {
            try {
                val pm = appCtx.packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveList = pm.queryIntentActivities(mainIntent, 0)
                val ownPkg = appCtx.packageName

                val mapped = resolveList
                    .asSequence()
                    .filter { it.activityInfo.packageName != ownPkg }
                    .distinctBy { it.activityInfo.packageName }
                    .mapNotNull { info ->
                        try {
                            val label = info.loadLabel(pm).toString().trim()
                            val drawable = info.loadIcon(pm)
                            val bmp = drawableToSmallBitmap(drawable, 108)
                            InstalledAppInfo(
                                label = label.ifEmpty { info.activityInfo.packageName },
                                packageName = info.activityInfo.packageName,
                                icon = bmp?.asImageBitmap()
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }
                    .sortedBy { it.label.lowercase() }
                    .toList()

                withContext(Dispatchers.Main) {
                    installedApps.value = mapped
                    hasLoadedOnce = true
                }
            } catch (_: Exception) {
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading.value = false
                }
            }
        }
    }

    fun launchApp(context: Context, packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent != null) {
                context.startActivity(intent)
                true
            } else false
        } catch (_: Exception) {
            false
        }
    }

    private fun drawableToSmallBitmap(drawable: Drawable, maxPx: Int): Bitmap? {
        return try {
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                val src = drawable.bitmap
                if (src.width <= maxPx && src.height <= maxPx) return src
                Bitmap.createScaledBitmap(src, maxPx, maxPx, true)
            } else {
                val bmp = Bitmap.createBitmap(maxPx, maxPx, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }
        } catch (_: Exception) {
            null
        }
    }
}
