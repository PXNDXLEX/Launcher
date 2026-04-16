package com.tuusuario.carlauncher.ui.widgets

import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun YouTubeWidget() {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false // Permite reproducir automáticamente
                webViewClient = WebViewClient() // Abre los enlaces dentro del widget, no en Chrome
                webChromeClient = WebChromeClient() // Soporte completo para video
                loadUrl("https://m.youtube.com")
            }
        }
    )
}