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

object YouTubeState {
    var webView: WebView? = null
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeWidget() {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            if (YouTubeState.webView == null) {
                YouTubeState.webView = object : WebView(context) {
                    override fun onWindowVisibilityChanged(visibility: Int) {
                        super.onWindowVisibilityChanged(View.VISIBLE)
                    }
                    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                        super.onWindowFocusChanged(true)
                    }
                }.apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false 
                        userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                    }
                    
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            
                            // INYECCIÓN VISUAL Y PROTECCIÓN DE PAUSA
                            view?.evaluateJavascript(
                                """
                                // 1. FORZAMOS EL DISEÑO RESPONSIVO (Evita que se corte en vertical)
                                let style = document.createElement('style');
                                style.innerHTML = `
                                    html, body, ytm-app { 
                                        width: 100vw !important; 
                                        overflow-x: hidden !important; 
                                    }
                                    .player-container { width: 100% !important; margin: 0 auto !important; }
                                `;
                                document.head.appendChild(style);

                                // Alineamos la vista a la esquina superior cada vez que cambia el tamaño
                                window.addEventListener('resize', () => { window.scrollTo(0, 0); });

                                // 2. DIOS MODE: PROTECCIÓN DE PAUSA
                                Object.defineProperty(document, 'hidden', {value: false, writable: false});
                                Object.defineProperty(document, 'visibilityState', {value: 'visible', writable: false});
                                window.addEventListener('visibilitychange', e => e.stopImmediatePropagation(), true);
                                window.addEventListener('blur', e => e.stopImmediatePropagation(), true);
                                document.hasFocus = () => true;
                                document.addEventListener('fullscreenchange', e => e.stopImmediatePropagation(), true);
                                document.addEventListener('webkitfullscreenchange', e => e.stopImmediatePropagation(), true);
                                
                                window.isUserClicking = false;
                                window.userPaused = false;
                                
                                document.addEventListener('pointerdown', () => { window.isUserClicking = true; });
                                document.addEventListener('pointerup', () => { 
                                    setTimeout(() => { window.isUserClicking = false; }, 300); 
                                });

                                const originalPause = HTMLMediaElement.prototype.pause;
                                HTMLMediaElement.prototype.pause = function() {
                                    if (window.isUserClicking) {
                                        window.userPaused = true;
                                        originalPause.call(this);
                                    }
                                };

                                const originalPlay = HTMLMediaElement.prototype.play;
                                HTMLMediaElement.prototype.play = function() {
                                    window.userPaused = false;
                                    return originalPlay.call(this);
                                };

                                setInterval(() => {
                                    let v = document.querySelector('video');
                                    if (v && v.paused && !window.userPaused) {
                                        v.play();
                                    }
                                }, 250);
                                """.trimIndent(), 
                                null
                            )
                        }
                    }
                    
                    webChromeClient = WebChromeClient()
                    loadUrl("https://m.youtube.com")
                }
            }
            
            (YouTubeState.webView?.parent as? ViewGroup)?.removeView(YouTubeState.webView)
            YouTubeState.webView!!
        }
    )
}