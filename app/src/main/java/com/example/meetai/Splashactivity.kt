package com.example.meetai

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.meetai.MainActivity

class Splashactivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splashactivity)

        val logoGlow  = findViewById<View>(R.id.logoGlow)
        val tvName    = findViewById<TextView>(R.id.tvAppName)
        val tvTagline = findViewById<TextView>(R.id.tvTagline)
        val dots      = findViewById<View>(R.id.loadingDots)

        // Animate logo in
        logoGlow.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(700).setStartDelay(200)
            .withEndAction {
                // Then app name
                tvName.animate().alpha(1f).translationY(0f)
                    .setDuration(500).start()
                tvTagline.animate().alpha(1f)
                    .setDuration(500).setStartDelay(150).start()
                dots.animate().alpha(1f)
                    .setDuration(400).setStartDelay(300)
                    .withEndAction {
                        // Navigate to MainActivity after 2 seconds
                        logoGlow.postDelayed({
                            startActivity(Intent(this, LoginActivity::class.java))
                            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                            finish()
                        }, 1400)
                    }.start()
            }.start()

        logoGlow.scaleX = 0.6f
        logoGlow.scaleY = 0.6f
        tvName.translationY = 30f
    }
}