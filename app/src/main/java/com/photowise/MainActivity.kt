package com.photowise

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import dev.hotwire.navigation.activities.HotwireActivity
import dev.hotwire.navigation.navigator.Navigator
import dev.hotwire.navigation.navigator.NavigatorConfiguration
import dev.hotwire.navigation.util.applyDefaultImeWindowInsets
import timber.log.Timber

class MainActivity : HotwireActivity() {

    // Define permissions needed by your app
    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    // Permission launcher using the new Activity Result API
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Check if all permissions are granted
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Timber.d("All permissions granted")
        } else {
            Timber.w("Some permissions were denied: ${permissions.filter { !it.value }.keys}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.main_nav_host).applyDefaultImeWindowInsets()

        // Request permissions when the activity is created
        checkAndRequestPermissions()
    }

    override fun navigatorConfigurations() = listOf(
        NavigatorConfiguration(
            name = "main",
            startLocation = intent.getStringExtra("start_location") ?: "https://photowise.app/",
            navigatorHostId = R.id.main_nav_host
        )
    )

    override fun onNavigatorReady(navigator: Navigator) {
        super.onNavigatorReady(navigator)
        Timber.d("Navigator ready: ${navigator.configuration.name}")
        // The speech bridge will be automatically initialized by the Hotwire bridge system
        // when the web page requests it
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

    // Optional: Method to check if speech recognition is available
    fun isSpeechRecognitionAvailable(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        } && android.speech.SpeechRecognizer.isRecognitionAvailable(this)
    }
}