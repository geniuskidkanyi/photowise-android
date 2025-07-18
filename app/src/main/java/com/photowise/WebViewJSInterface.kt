package com.photowise

import android.webkit.JavascriptInterface
import timber.log.Timber

class WebViewJSInterface(private val speechHelper: SpeechHelper) {

    @JavascriptInterface
    fun startSpeechRecognition() {
        Timber.d("🎤 JS Interface: startSpeechRecognition() called")
        speechHelper.startSpeechRecognition()
    }

    @JavascriptInterface
    fun stopSpeechRecognition() {
        Timber.d("🎤 JS Interface: stopSpeechRecognition() called")
        speechHelper.stopSpeechRecognition()
    }

    @JavascriptInterface
    fun isSpeechAvailable(): Boolean {
        val available = speechHelper.hasPermissions()
        Timber.d("🎤 JS Interface: isSpeechAvailable() = $available")
        return available
    }
}