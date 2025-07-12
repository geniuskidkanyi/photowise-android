package com.photowise

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class WelcomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        findViewById<Button>(R.id.get_started_button).setOnClickListener {
            // Mark welcome screen as seen
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            prefs.edit().putBoolean("seen_welcome", true).apply()

            // Proceed to Intro steps
            startActivity(Intent(this, IntroStepsActivity::class.java))
            finish()
        }
    }
}
