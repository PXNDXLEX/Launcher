package com.tuusuario.carlauncher.ui.widgets

import android.annotation.SuppressLint
import android.view.View
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
                // MAGIA REBELDE: Creamos un WebView personalizado que ignora los bloqueos de Android
                YouTubeState.webView = object : WebView(context) {
                    
                    // Mentira #1: Siempre estoy visible, nunca me he ocultado
                    override fun onWindowVisibilityChanged(visibility: Int) {
                        super.onWindowVisibilityChanged(View.VISIBLE)
                    }

                    // Mentira #2: Siempre me están mirando, no pauses la multimedia
                    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                        super.onWindowFocusChanged(true)
                    }
                    
                }.apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false // Permite reproducir sin tocar
                        // Engañamos a YouTube para que crea que somos un navegador real
                        userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                    }
                    
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            // Inyección JavaScript: Cegamos a YouTube para que no pueda detectar si la pestaña está oculta o minimizada
                            view?.evaluateJavascript(
                                """
                                Object.defineProperty(document, 'hidden', {value: false, writable: false});
                                Object.defineProperty(document, 'visibilityState', {value: 'visible', writable: false});
                                window.addEventListener('visibilitychange', e => e.stopImmediatePropagation(), true);
                                """.trimIndent(), 
                                null
                            )
                        }
                    }
                    
                    webChromeClient = WebChromeClient()
                    loadUrl("https://m.youtube.com")
                }
            }
            
            // Despegamos el WebView de su padre anterior si lo tenía para moverlo a la nueva pestaña
            (YouTubeState.webView?.parent as? ViewGroup)?.removeView(YouTubeState.webView)
            
            YouTubeState.webView!!
        }
        // ¡Se eliminó el bloque 'update' para evitar que el reloj del Dashboard interrumpa el video!
    )
}