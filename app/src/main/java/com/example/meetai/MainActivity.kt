package com.example.meetai

import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNav   = findViewById<BottomNavigationView>(R.id.bottomNav)
        val glowWrapper = findViewById<FrameLayout>(R.id.glowWrapper)

        glowWrapper.startNeonPulse()

        loadFragment(Homefragment())

        bottomNav.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.navHome    -> loadFragment(Homefragment())
                R.id.navHistory -> loadFragment(Historyfragment())
                R.id.navProfile -> loadFragment(Profilefragment())
            }
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}