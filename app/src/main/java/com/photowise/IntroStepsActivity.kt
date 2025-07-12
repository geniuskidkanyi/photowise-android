package com.photowise

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class IntroStepsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intro_steps)

        findViewById<Button>(R.id.get_started_button).setOnClickListener {
            // Mark intro as seen
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            prefs.edit().putBoolean("seen_intro", true).apply()

            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
