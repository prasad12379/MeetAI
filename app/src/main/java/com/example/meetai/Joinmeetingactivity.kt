package com.example.meetai

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class Joinmeetingactivity : AppCompatActivity() {

    companion object {
        private const val BASE_URL = "https://newmeetaibackend.onrender.com"
    }

    private lateinit var etMeetingCode: EditText
    private lateinit var etDisplayName: EditText
    private lateinit var switchMic: Switch
    private lateinit var switchCamera: Switch
    private lateinit var btnJoin: CardView
    private lateinit var btnBack: ImageView

    private var userEmail = ""

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_joinmeetingactivity)

        // ── Bind views ────────────────────────────────────────────────
        etMeetingCode = findViewById(R.id.etMeetingCode)
        etDisplayName = findViewById(R.id.etDisplayName)
        switchMic     = findViewById(R.id.switchMic)
        switchCamera  = findViewById(R.id.switchCamera)
        btnJoin       = findViewById(R.id.btnJoin)
        btnBack       = findViewById(R.id.btnBack)

        // ── Get saved email and name from SharedPreferences ───────────
        val prefs = getSharedPreferences("meetai_prefs", MODE_PRIVATE)
        userEmail = prefs.getString("user_email", "") ?: ""

        // Pre-fill saved display name
        etDisplayName.setText(prefs.getString("user_name", ""))

        // ── Listeners ─────────────────────────────────────────────────
        btnBack.setOnClickListener { finish() }

        btnJoin.setOnClickListener {
            val code = etMeetingCode.text.toString().trim().uppercase()
            val name = etDisplayName.text.toString().trim()

            if (code.isEmpty()) {
                Toast.makeText(this,
                    "Please enter a meeting code",
                    Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (name.isEmpty()) {
                Toast.makeText(this,
                    "Please enter your name",
                    Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (userEmail.isEmpty()) {
                Toast.makeText(this,
                    "User email not found. Please login again.",
                    Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Save display name for next time
            prefs.edit().putString("user_name", name).apply()

            registerMemberAndJoin(code, name)
        }
    }

    // ── Register member in MongoDB then launch meeting ────────────────
    private fun registerMemberAndJoin(code: String, displayName: String) {
        btnJoin.isEnabled = false
        Toast.makeText(this, "Joining meeting...", Toast.LENGTH_SHORT).show()

        val body = JSONObject().apply {
            put("room_code", code)
            put("email",     userEmail)
            put("is_admin",  false)
        }

        val request = Request.Builder()
            .url("$BASE_URL/meetings")
            .post(
                body.toString()
                    .toRequestBody("application/json".toMediaTypeOrNull())
            )
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    btnJoin.isEnabled = true
                    Toast.makeText(
                        this@Joinmeetingactivity,
                        "Network error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                runOnUiThread {
                    btnJoin.isEnabled = true
                    when (response.code) {
                        200, 201 -> {
                            // Success — launch meeting room
                            android.util.Log.d("MEETAI",
                                "Joined meeting: $responseBody")
                            launchMeetingRoom(code, displayName)
                        }
                        404 -> {
                            // Room not found
                            Toast.makeText(
                                this@Joinmeetingactivity,
                                "Meeting not found. Please check the room code.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        else -> {
                            Toast.makeText(
                                this@Joinmeetingactivity,
                                "Failed to join meeting. Try again.",
                                Toast.LENGTH_SHORT
                            ).show()
                            android.util.Log.e("MEETAI",
                                "Join meeting error: $responseBody")
                        }
                    }
                }
            }
        })
    }

    // ── Launch meeting room activity ──────────────────────────────────
    private fun launchMeetingRoom(code: String, displayName: String) {
        val intent = Intent(this, Meetingroomactivity::class.java).apply {
            putExtra("room_code",          code)
            putExtra("display_name",       displayName)
            putExtra("user_email",         userEmail)
            putExtra("mic_on",             switchMic.isChecked)
            putExtra("cam_on",             switchCamera.isChecked)
            putExtra("is_host",            false)
            putExtra("transcript_enabled", false) // members don't control transcript
        }
        startActivity(intent)
    }
}