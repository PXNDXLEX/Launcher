package com.tuusuario.carlauncher.ui.widgets

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

// Objeto global para mantener vivo el video aunque cambiemos de pestaña
object YouTubeState {
    var webView: WebView? = null
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeWidget() {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            // Solo creamos el YouTube UNA vez en toda la vida de la app
            if (YouTubeState.webView == null) {
                YouTubeState.webView = WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false // No pausar automáticamente
                        userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                    }
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    loadUrl("https://m.youtube.com")
                }
            }
            
            // MAGIA: Si el reproductor estaba en otra pestaña, lo "despegamos" de allá para traerlo aquí sin reiniciarlo
            (YouTubeState.webView?.parent as? ViewGroup)?.removeView(YouTubeState.webView)
            
            YouTubeState.webView!!
        }
    )
}