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

class Newmeetingactivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQ_ID = 33
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_newmeetingactivity)

        // ── Bind views (only once) ────────────────────────────────────
        tvRoomCode       = findViewById(R.id.tvRoomCode)
        btnCopyCode      = findViewById(R.id.btnCopyCode)
        btnRefreshCode   = findViewById(R.id.btnRefreshCode)
        btnStartMeeting  = findViewById(R.id.btnStartMeeting)
        switchMic        = findViewById(R.id.switchMic)
        switchCamera     = findViewById(R.id.switchCamera)
        switchTranscript = findViewById(R.id.switchTranscript)
        btnBack          = findViewById(R.id.btnBack)

        // ── Generate initial room code ────────────────────────────────
        generateCode()

        // ── Listeners (only once) ─────────────────────────────────────
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
            if (checkPermissions()) {
                launchMeetingRoom()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                    PERMISSION_REQ_ID
                )
            }
        }
    }

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
                launchMeetingRoom()
            } else {
                Toast.makeText(
                    this,
                    "Camera and mic permission required to start a meeting",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun launchMeetingRoom() {
        // Log to confirm transcript switch state before launching
        android.util.Log.d("MEETAI", "transcript_enabled = ${switchTranscript.isChecked}")

        val intent = Intent(this, Meetingroomactivity::class.java).apply {
            putExtra("room_code",          currentCode)
            putExtra("display_name",       "Host")        // ← add display name
            putExtra("mic_on",             switchMic.isChecked)
            putExtra("cam_on",             switchCamera.isChecked)
            putExtra("is_host",            true)
            putExtra("transcript_enabled", switchTranscript.isChecked)
        }
        startActivity(intent)
    }

    private fun generateCode() {
        val num    = (1000..9999).random()
        val suffix = ('A'..'Z').shuffled().take(2).joinToString("")
        currentCode = "NXM-$num-$suffix"
        tvRoomCode.text = currentCode
    }
}