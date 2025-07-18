package com.photowise

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.webkit.WebView
import timber.log.Timber
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class SpeechHelper(private val context: Context, private val webView: WebView) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    fun startSpeechRecognition() {
        Timber.d("🎤 startSpeechRecognition() called")

        if (!hasPermissions()) {
            Timber.e("❌ Permission not granted")
            notifyWebView("error", "Permission not granted")
            return
        }

        if (isEmulator()) {
            Timber.d("🤖 Running on emulator - using mock speech recognition")
            mockSpeechRecognition()
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Timber.e("❌ Speech recognition not available")
            notifyWebView("error", "Speech recognition not available")
            return
        }

        Timber.d("✅ Starting real speech recognition")
        startRealSpeechRecognition()
    }

    fun stopSpeechRecognition() {
        Timber.d("🎤 stopSpeechRecognition() called")

        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
            notifyWebView("stopped", "Speech recognition stopped")
        }
    }

    fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                Build.HARDWARE.contains("goldfish") ||
                Build.HARDWARE.contains("ranchu") ||
                Build.PRODUCT.contains("sdk") ||
                Build.PRODUCT.contains("google_sdk") ||
                Build.PRODUCT.contains("sdk_google") ||
                Build.PRODUCT.contains("sdk_gphone64_arm64") ||
                Build.PRODUCT.contains("emulator") ||
                Build.BOARD.lowercase().contains("nox") ||
                Build.BOOTLOADER.lowercase().contains("nox") ||
                Build.HARDWARE.lowercase().contains("nox") ||
                Build.PRODUCT.lowercase().contains("nox"))
    }

    private fun mockSpeechRecognition() {
        Timber.d("🤖 Starting mock speech recognition")
        notifyWebView("started", "Mock speech recognition started")

        // Simulate speech recognition with a delay
        Handler(Looper.getMainLooper()).postDelayed({
            val mockText = "Hello this is a test speech recognition"
            Timber.d("🤖 Mock speech result: $mockText")

            // Update input field
            webView.post {
                webView.evaluateJavascript(
                    """
                    (function() {
                        const input = document.querySelector('[data-speech-target="input"]');
                        if (input) {
                            input.value = '$mockText';
                            console.log('✅ Mock speech result set:', '$mockText');
                        }
                    })();
                    """.trimIndent(),
                    null
                )
            }

            notifyWebView("result", mockText)
            notifyWebView("end", "Mock speech recognition ended")
        }, 2000)
    }

    private fun startRealSpeechRecognition() {
        try {
            Timber.d("🎤 Creating SpeechRecognizer")
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(createRecognitionListener())

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }

            Timber.d("🎤 Starting listening...")
            speechRecognizer?.startListening(intent)
            isListening = true
            notifyWebView("started", "Speech recognition started")

        } catch (e: Exception) {
            Timber.e(e, "❌ Error starting speech recognition")
            notifyWebView("error", "Failed to start: ${e.message}")
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {
                Timber.d("🎤 Ready for speech")
                notifyWebView("ready", "Ready for speech")
            }

            override fun onBeginningOfSpeech() {
                Timber.d("🎤 Beginning of speech")
                notifyWebView("beginning", "Beginning of speech")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Volume changed - optional to handle
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Buffer received - optional to handle
            }

            override fun onEndOfSpeech() {
                Timber.d("🎤 End of speech")
                notifyWebView("end", "End of speech")
            }

            override fun onError(error: Int) {
                Timber.e("🎤 Speech recognition error: $error")
                isListening = false
                notifyWebView("error", "Speech recognition error: $error")
            }

            override fun onResults(results: android.os.Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null && matches.isNotEmpty()) {
                    val result = matches[0]
                    Timber.d("🎤 Speech result: $result")

                    // Update input field
                    webView.post {
                        webView.evaluateJavascript(
                            """
                            (function() {
                                const input = document.querySelector('[data-speech-target="input"]');
                                if (input) {
                                    input.value = '$result';
                                    console.log('✅ Speech result set:', '$result');
                                }
                            })();
                            """.trimIndent(),
                            null
                        )
                    }

                    notifyWebView("result", result)
                }
                isListening = false
            }

            override fun onPartialResults(partialResults: android.os.Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null && matches.isNotEmpty()) {
                    val result = matches[0]
                    Timber.d("🎤 Partial result: $result")

                    // Update input field with partial result
                    webView.post {
                        webView.evaluateJavascript(
                            """
                            (function() {
                                const input = document.querySelector('[data-speech-target="input"]');
                                if (input) {
                                    input.value = '$result';
                                }
                            })();
                            """.trimIndent(),
                            null
                        )
                    }

                    notifyWebView("partial", result)
                }
            }

            override fun onEvent(eventType: Int, params: android.os.Bundle?) {
                // Event received - optional to handle
            }
        }
    }

    private fun notifyWebView(event: String, message: String) {
        Timber.d("📢 Notifying WebView: $event - $message")
        val jsCode = """
            (function() {
                console.log('📢 Native event: $event - $message');
                const element = document.querySelector('[data-controller*="speech"]');
                if (element) {
                    element.dispatchEvent(new CustomEvent('native-speech:$event', {
                        detail: { message: '$message' },
                        bubbles: true
                    }));
                    console.log('✅ Event dispatched: native-speech:$event');
                } else {
                    console.error('❌ Speech controller element not found');
                }
            })();
        """.trimIndent()

        webView.post {
            webView.evaluateJavascript(jsCode, null)
        }
    }

    fun cleanup() {
        Timber.d("🔧 Cleaning up speech helper")
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
    }
}