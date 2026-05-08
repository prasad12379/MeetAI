package com.example.meetai

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class Newmeetingactivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQ_ID = 33
        private const val BASE_URL = "https://newmeetaibackend.onrender.com"
    }

    private lateinit var tvRoomCode: TextView
    private lateinit var btnCopyCode: CardView
    private lateinit var btnRefreshCode: CardView
    private lateinit var btnStartMeeting: CardView
    private lateinit var switchMic: Switch
    private lateinit var switchCamera: Switch
    private lateinit var switchTranscript: Switch
    private lateinit var btnBack: ImageView

    private var currentCode = ""
    private var userEmail   = ""

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_newmeetingactivity)

        // ── Bind views ────────────────────────────────────────────────
        tvRoomCode       = findViewById(R.id.tvRoomCode)
        btnCopyCode      = findViewById(R.id.btnCopyCode)
        btnRefreshCode   = findViewById(R.id.btnRefreshCode)
        btnStartMeeting  = findViewById(R.id.btnStartMeeting)
        switchMic        = findViewById(R.id.switchMic)
        switchCamera     = findViewById(R.id.switchCamera)
        switchTranscript = findViewById(R.id.switchTranscript)
        btnBack          = findViewById(R.id.btnBack)

        // ── Get saved email from SharedPreferences (set during login) ─
        val prefs = getSharedPreferences("meetai_prefs", MODE_PRIVATE)
        userEmail = prefs.getString("user_email", "") ?: ""

        generateCode()

        // ── Listeners ─────────────────────────────────────────────────
        btnBack.setOnClickListener { finish() }

        btnCopyCode.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Meeting Code", currentCode))
            Toast.makeText(this, "Code copied!", Toast.LENGTH_SHORT).show()
        }

        btnRefreshCode.setOnClickListener {
            generateCode()
            tvRoomCode.animate().alpha(0f).setDuration(150).withEndAction {
                tvRoomCode.text = currentCode
                tvRoomCode.animate().alpha(1f).setDuration(200).start()
            }.start()
        }

        btnStartMeeting.setOnClickListener {
            if (userEmail.isEmpty()) {
                Toast.makeText(this,
                    "User email not found. Please login again.",
                    Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (checkPermissions()) {
                registerAdminAndLaunch()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                    PERMISSION_REQ_ID
                )
            }
        }
    }

    // ── Register admin in MongoDB then launch meeting ─────────────────
    private fun registerAdminAndLaunch() {
        btnStartMeeting.isEnabled = false
        Toast.makeText(this, "Creating meeting...", Toast.LENGTH_SHORT).show()

        val body = JSONObject().apply {
            put("room_code", currentCode)
            put("email",     userEmail)
            put("is_admin",  true)
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
                    btnStartMeeting.isEnabled = true
                    Toast.makeText(
                        this@Newmeetingactivity,
                        "Network error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                runOnUiThread {
                    btnStartMeeting.isEnabled = true
                    when (response.code) {
                        200, 201 -> {
                            // Success — launch meeting room
                            android.util.Log.d("MEETAI",
                                "Meeting created: $responseBody")
                            launchMeetingRoom()
                        }
                        409 -> {
                            // Room already exists — generate new code and retry
                            Toast.makeText(
                                this@Newmeetingactivity,
                                "Room code conflict, generating new code...",
                                Toast.LENGTH_SHORT
                            ).show()
                            generateCode()
                            registerAdminAndLaunch()
                        }
                        else -> {
                            Toast.makeText(
                                this@Newmeetingactivity,
                                "Failed to create meeting. Try again.",
                                Toast.LENGTH_SHORT
                            ).show()
                            android.util.Log.e("MEETAI",
                                "Create meeting error: $responseBody")
                        }
                    }
                }
            }
        })
    }

    // ── Launch meeting room activity ──────────────────────────────────
    private fun launchMeetingRoom() {
        val displayName = userEmail.substringBefore("@").ifEmpty { "Host" }

        val intent = Intent(this, Meetingroomactivity::class.java).apply {
            putExtra("room_code",          currentCode)
            putExtra("display_name",       displayName)
            putExtra("user_email",         userEmail)
            putExtra("mic_on",             switchMic.isChecked)
            putExtra("cam_on",             switchCamera.isChecked)
            putExtra("is_host",            true)
            putExtra("transcript_enabled", switchTranscript.isChecked)
        }
        startActivity(intent)
    }

    // ── Permissions ───────────────────────────────────────────────────
    private fun checkPermissions(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQ_ID) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                registerAdminAndLaunch()
            } else {
                Toast.makeText(
                    this,
                    "Camera and mic permission required to start a meeting",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun generateCode() {
        val num    = (1000..9999).random()
        val suffix = ('A'..'Z').shuffled().take(2).joinToString("")
        currentCode = "NXM-$num-$suffix"
        tvRoomCode.text = currentCode
    }
}