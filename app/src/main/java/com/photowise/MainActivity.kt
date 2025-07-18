package com.photowise

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    // Define permissions needed by your app
    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    // Speech recognition components
    private lateinit var speechHelper: SpeechHelper
    private lateinit var webView: WebView

    // Permission launcher using the new Activity Result API
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Check if all permissions are granted
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Timber.d("All permissions granted")
            // Initialize speech helper after permissions are granted
            if (::webView.isInitialized) {
                initializeSpeechHelper(webView)
            }
        } else {
            Timber.w("Some permissions were denied: ${permissions.filter { !it.value }.keys}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Apply window insets for edge-to-edge display
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize WebView
        setupWebView()

        // Request permissions when the activity is created
        checkAndRequestPermissions()
    }

    private fun setupWebView() {
        // Find WebView in layout
        webView = findViewById(R.id.webview)

        // IMPORTANT: Setup speech functionality BEFORE loading URL
        setupWebViewSpeech(webView)

        // Configure WebView settings
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        // Set up WebView client
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Timber.d("Page started loading: $url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Timber.d("Page finished loading: $url")

                // Re-add JavaScript interface after page loads
                addJavaScriptInterface()

                // Wait a bit then test the interface
                Handler(Looper.getMainLooper()).postDelayed({
                    testAndroidSpeechInterface()
                }, 1000)
            }
        }

        // Load the initial URL
        val startUrl = intent.getStringExtra("start_location") ?: "https://photowise.app/"
        webView.loadUrl(startUrl)

        Timber.d("WebView setup completed, loading: $startUrl")
    }

    private fun setupWebViewSpeech(webView: WebView) {
        try {
            // Always enable debugging for now
            WebView.setWebContentsDebuggingEnabled(true)
            Timber.d("WebView debugging enabled")

            // Initialize speech helper
            initializeSpeechHelper(webView)

            Timber.d("WebView speech setup completed")
        } catch (e: Exception) {
            Timber.e(e, "Error setting up WebView speech")
        }
    }

    private fun initializeSpeechHelper(webView: WebView) {
        try {
            Timber.d("🔧 Initializing speech helper...")

            // Initialize speech helper
            if (!::speechHelper.isInitialized) {
                speechHelper = SpeechHelper(this, webView)
                Timber.d("✅ Speech helper created")
            }

            // Add JavaScript interface
            addJavaScriptInterface()

        } catch (e: Exception) {
            Timber.e(e, "❌ Error initializing speech helper")
        }
    }

    private fun addJavaScriptInterface() {
        try {
            if (::speechHelper.isInitialized) {
                // Remove existing interface if it exists
                webView.removeJavascriptInterface("AndroidSpeech")

                // Add JavaScript interface
                webView.addJavascriptInterface(
                    WebViewJSInterface(speechHelper),
                    "AndroidSpeech"
                )

                Timber.d("✅ JavaScript interface 'AndroidSpeech' added")
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Error adding JavaScript interface")
        }
    }

    private fun testAndroidSpeechInterface() {
        val testScript = """
            console.log('=== AndroidSpeech Interface Test ===');
            console.log('AndroidSpeech type:', typeof AndroidSpeech);
            if (typeof AndroidSpeech !== 'undefined') {
                console.log('AndroidSpeech object:', AndroidSpeech);
                console.log('AndroidSpeech methods:', Object.getOwnPropertyNames(AndroidSpeech));
                
                // Test methods
                try {
                    console.log('isSpeechAvailable:', AndroidSpeech.isSpeechAvailable());
                } catch (e) {
                    console.error('Error testing isSpeechAvailable:', e);
                }
                
                // Force speech controller to use native
                const speechElement = document.querySelector('[data-controller*="speech"]');
                if (speechElement && speechElement.application) {
                    speechElement.application.controllers.forEach(controller => {
                        if (controller.identifier === 'speech') {
                            controller.useNative = true;
                            console.log('✅ Forced speech controller to use native');
                        }
                    });
                }
            } else {
                console.error('❌ AndroidSpeech interface not found');
            }
        """.trimIndent()

        webView.evaluateJavascript(testScript, null)
    }

    private fun checkAndRequestPermissions() {
        // Check which permissions we need to request
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        // If there are permissions to request, launch the permission request
        if (permissionsToRequest.isNotEmpty()) {
            Timber.d("Requesting permissions: ${permissionsToRequest.joinToString()}")
            requestPermissionLauncher.launch(permissionsToRequest)
        } else {
            Timber.d("All permissions already granted")
        }
    }

    private fun hasAllPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Method to check if speech recognition is available
    fun isSpeechRecognitionAvailable(): Boolean {
        return hasAllPermissions() && android.speech.SpeechRecognizer.isRecognitionAvailable(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up speech helper
        if (::speechHelper.isInitialized) {
            speechHelper.cleanup()
        }

        // Clean up WebView
        webView.destroy()
    }

    override fun onPause() {
        super.onPause()
        // Stop speech recognition when app goes to background
        if (::speechHelper.isInitialized) {
            speechHelper.stopSpeechRecognition()
        }

        // Pause WebView
        webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        // Resume WebView
        webView.onResume()
    }

    override fun onBackPressed() {
        // Handle back button for WebView navigation
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    // Method to manually reinitialize speech if needed
    fun reinitializeSpeech() {
        if (hasAllPermissions()) {
            initializeSpeechHelper(webView)
        }
    }
}