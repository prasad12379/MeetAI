package com.example.meetai

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView

class Joinmeetingactivity : AppCompatActivity() {

    private lateinit var etMeetingCode: EditText
    private lateinit var etDisplayName: EditText
    private lateinit var switchMic: Switch
    private lateinit var switchCamera: Switch
    private lateinit var btnJoin: CardView
    private lateinit var btnBack: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_joinmeetingactivity)

        etMeetingCode = findViewById(R.id.etMeetingCode)
        etDisplayName = findViewById(R.id.etDisplayName)
        switchMic     = findViewById(R.id.switchMic)
        switchCamera  = findViewById(R.id.switchCamera)
        btnJoin       = findViewById(R.id.btnJoin)
        btnBack       = findViewById(R.id.btnBack)

        // Pre-fill saved name
        val prefs = getSharedPreferences("nexmeet_prefs", 0)
        etDisplayName.setText(prefs.getString("user_name", ""))

        btnBack.setOnClickListener { finish() }

        btnJoin.setOnClickListener {
            val code = etMeetingCode.text.toString().trim().uppercase()
            val name = etDisplayName.text.toString().trim()

            if (code.isEmpty()) {
                Toast.makeText(this, "Please enter a meeting code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Save name for next time
            prefs.edit().putString("user_name", name).apply()

            val intent = Intent(this, Meetingroomactivity::class.java).apply {
                putExtra("room_code", code)
                putExtra("display_name", name)
                putExtra("mic_on", switchMic.isChecked)
                putExtra("cam_on", switchCamera.isChecked)
                putExtra("is_host", false)
            }
            startActivity(intent)
        }
    }
}