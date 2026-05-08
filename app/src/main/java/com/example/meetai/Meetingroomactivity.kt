package com.example.meetai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.VideoCanvas
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class Meetingroomactivity : AppCompatActivity() {

    // ── NEW: member emails list (Step 2) ──────────────────────────────
    private val memberEmails = mutableListOf<String>()

    private val APP_ID = "07ee35268cf04c65adb62e48d8ba430a"
    private val TOKEN  = ""
    private var isLeavingMeeting = false

    companion object {
        private const val PERMISSION_REQ_ID = 22
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
    }

    // Views
    private lateinit var tvRoomCode: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvParticipantCount: TextView
    private lateinit var btnMicWrapper: FrameLayout
    private lateinit var btnCamWrapper: FrameLayout
    private lateinit var btnEndCall: FrameLayout
    private lateinit var btnMic: View
    private lateinit var btnCamera: View
    private lateinit var rvParticipants: RecyclerView
    private lateinit var tvTranscriptBadge: TextView

    // Agora
    private var rtcEngine: RtcEngine? = null

    // State
    private var micOn = true
    private var cameraOn = true
    private var seconds = 0
    private var participantCount = 1
    private var channelName = ""
    private var displayName = ""

    // ── NEW: userEmail read from Intent (Step 2) ──────────────────────
    private var userEmail = ""

    private val handler = Handler(Looper.getMainLooper())

    // ── Audio Recording ───────────────────────────────────────────────
    private var transcriptEnabled = false
    private var mediaRecorder: MediaRecorder? = null
    private var audioFilePath: String = ""
    private var isRecording = false

    // Participants
    private val participants = mutableListOf<ParticipantInfo>()
    private lateinit var adapter: ParticipantAdapter

    // ── Timer ─────────────────────────────────────────────────────────
    private val timerRunnable = object : Runnable {
        override fun run() {
            seconds++
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60
            tvTimer.text = if (h > 0) String.format("%d:%02d:%02d", h, m, s)
            else String.format("%02d:%02d", m, s)
            handler.postDelayed(this, 1000)
        }
    }

    // ── Agora Events ──────────────────────────────────────────────────
    private val rtcEventHandler = object : IRtcEngineEventHandler() {

        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
            runOnUiThread {
                // ── EXISTING logic (unchanged) ────────────────────────
                handler.post(timerRunnable)
                participants.add(0, ParticipantInfo(
                    uid = uid,
                    name = displayName.ifEmpty { "You" },
                    isLocal = true,
                    videoOn = cameraOn
                ))
                adapter.notifyDataSetChanged()
                updateGridLayout()
                Toast.makeText(this@Meetingroomactivity,
                    "Connected to $channel", Toast.LENGTH_SHORT).show()

                // ── NEW: add admin/user email to memberEmails (Step 2) ─
                if (userEmail.isNotEmpty()) memberEmails.add(userEmail)

                // Start recording after joining
                if (transcriptEnabled) startAudioRecording()
            }
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            runOnUiThread {
                participantCount++
                tvParticipantCount.text = participantCount.toString()
                participants.add(ParticipantInfo(
                    uid = uid, name = "User $uid",
                    isLocal = false, videoOn = true))
                adapter.notifyDataSetChanged()
                updateGridLayout()
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            runOnUiThread {
                participantCount = maxOf(1, participantCount - 1)
                tvParticipantCount.text = participantCount.toString()
                participants.removeAll { it.uid == uid }
                adapter.notifyDataSetChanged()
                updateGridLayout()
                Toast.makeText(this@Meetingroomactivity,
                    "A participant left", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onRemoteVideoStateChanged(
            uid: Int, state: Int, reason: Int, elapsed: Int
        ) {
            runOnUiThread {
                participants.find { it.uid == uid }?.let {
                    it.videoOn = (state == Constants.REMOTE_VIDEO_STATE_DECODING)
                    adapter.notifyDataSetChanged()
                }
            }
        }

        override fun onError(err: Int) {
            runOnUiThread {
                Toast.makeText(this@Meetingroomactivity,
                    "Error code: $err", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ─────────────────────────── onCreate ────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meetingroomactivity)

        channelName       = intent.getStringExtra("room_code") ?: "default-room"
        displayName       = intent.getStringExtra("display_name") ?: "You"
        micOn             = intent.getBooleanExtra("mic_on", true)
        cameraOn          = intent.getBooleanExtra("cam_on", true)
        transcriptEnabled = intent.getBooleanExtra("transcript_enabled", false)

        // ── NEW: read userEmail from Intent (Step 2) ──────────────────
        userEmail         = intent.getStringExtra("user_email") ?: ""

        bindViews()
        setupRecyclerView()

        tvTranscriptBadge.visibility =
            if (transcriptEnabled) View.VISIBLE else View.GONE

        if (checkPermissions()) {
            initAgoraAndJoin()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, PERMISSION_REQ_ID)
        }

        setupListeners()
        tvRoomCode.text = channelName
    }

    // ─────────────────────────── Views ───────────────────────────────
    private fun bindViews() {
        tvRoomCode         = findViewById(R.id.tvRoomCode)
        tvTimer            = findViewById(R.id.tvTimer)
        tvParticipantCount = findViewById(R.id.tvParticipantCount)
        btnMicWrapper      = findViewById(R.id.btnMicWrapper)
        btnCamWrapper      = findViewById(R.id.btnCamWrapper)
        btnEndCall         = findViewById(R.id.btnEndCall)
        btnMic             = findViewById(R.id.btnMic)
        btnCamera          = findViewById(R.id.btnCamera)
        rvParticipants     = findViewById(R.id.rvParticipants)
        tvTranscriptBadge  = findViewById(R.id.tvTranscriptBadge)
    }

    private fun setupRecyclerView() {
        adapter = ParticipantAdapter(participants, rtcEngine)
        rvParticipants.adapter = adapter
        rvParticipants.layoutManager = GridLayoutManager(this, 2)
    }

    private fun updateGridLayout() {
        val spanCount = when {
            participants.size == 1 -> 1
            participants.size <= 4 -> 2
            else -> 3
        }
        (rvParticipants.layoutManager as GridLayoutManager).spanCount = spanCount
    }

    // ─────────────────────────── Permissions ─────────────────────────
    private fun checkPermissions(): Boolean = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQ_ID) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initAgoraAndJoin()
            } else {
                Toast.makeText(this,
                    "Camera and microphone permission required",
                    Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    // ─────────────────────────── Agora ───────────────────────────────
    private fun initAgoraAndJoin() {
        if (APP_ID.isBlank()) {
            AlertDialog.Builder(this)
                .setTitle("App ID Missing")
                .setMessage("Please add your Agora App ID")
                .setPositiveButton("OK") { _, _ -> finish() }
                .show()
            return
        }

        try {
            val config = RtcEngineConfig().apply {
                mContext = applicationContext
                mAppId = APP_ID
                mEventHandler = rtcEventHandler
            }
            rtcEngine = RtcEngine.create(config)
            rtcEngine?.enableVideo()
            rtcEngine?.setVideoEncoderConfiguration(
                io.agora.rtc2.video.VideoEncoderConfiguration(
                    io.agora.rtc2.video.VideoEncoderConfiguration.VD_640x360,
                    io.agora.rtc2.video.VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15,
                    io.agora.rtc2.video.VideoEncoderConfiguration.STANDARD_BITRATE,
                    io.agora.rtc2.video.VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_ADAPTIVE
                )
            )
            adapter.setRtcEngine(rtcEngine)
            joinChannel()
        } catch (e: Exception) {
            Toast.makeText(this,
                "Agora init failed: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun joinChannel() {
        val options = ChannelMediaOptions().apply {
            channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
        }
        rtcEngine?.joinChannel(TOKEN, channelName, 0, options)
        if (!micOn) rtcEngine?.muteLocalAudioStream(true)
        if (!cameraOn) rtcEngine?.muteLocalVideoStream(true)
        updateMicUI()
        updateCameraUI()
    }

    // ── Audio Recording ───────────────────────────────────────────────
    private fun startAudioRecording() {
        try {
            val dateStamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",
                Locale.getDefault()).format(Date())
            val fileName = "meeting_${channelName}_$dateStamp.mp3"
            audioFilePath = File(cacheDir, fileName).absolutePath

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(audioFilePath)
                prepare()
                start()
                isRecording = true
            }

            Toast.makeText(this,
                "🔴 Recording started", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this,
                "Recording failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopAudioRecording() {
        if (!isRecording) return
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
        } catch (e: Exception) {
            e.printStackTrace()
            mediaRecorder = null
            isRecording = false
        }
    }

    // ─────────────────────────── Listeners ───────────────────────────
    private fun setupListeners() {
        btnMicWrapper.setOnClickListener {
            micOn = !micOn
            rtcEngine?.muteLocalAudioStream(!micOn)
            updateMicUI()
        }

        btnCamWrapper.setOnClickListener {
            cameraOn = !cameraOn
            rtcEngine?.muteLocalVideoStream(!cameraOn)
            participants.find { it.isLocal }?.videoOn = cameraOn
            adapter.notifyDataSetChanged()
            updateCameraUI()
        }

        btnEndCall.setOnClickListener { showEndCallDialog() }

        findViewById<View>(R.id.btnParticipants).setOnClickListener {
            Toast.makeText(this,
                "$participantCount participant(s) in this meeting",
                Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.btnChat).setOnClickListener {
            Toast.makeText(this, "Chat — coming soon", Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.btnShare).setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT,
                    "Join my MeetAI meeting. Code: $channelName")
            }
            startActivity(Intent.createChooser(shareIntent, "Share code"))
        }
    }

    // ─────────────────────────── End Call ────────────────────────────
    private fun showEndCallDialog() {
        AlertDialog.Builder(this)
            .setTitle("End Meeting?")
            .setMessage("Are you sure you want to leave?")
            .setPositiveButton("Leave") { _, _ -> leaveAndFinish() }
            .setNegativeButton("Stay", null)
            .show()
    }

    private fun leaveAndFinish() {
        if (isLeavingMeeting) return
        isLeavingMeeting = true

        android.util.Log.d("MEETAI_DEBUG", "=== leaveAndFinish called ===")
        android.util.Log.d("MEETAI_DEBUG", "transcriptEnabled = $transcriptEnabled")
        android.util.Log.d("MEETAI_DEBUG", "audioFilePath = $audioFilePath")
        android.util.Log.d("MEETAI_DEBUG", "isRecording = $isRecording")
        android.util.Log.d("MEETAI_DEBUG", "seconds = $seconds")

        stopAudioRecording()
        rtcEngine?.leaveChannel()

        android.util.Log.d("MEETAI_DEBUG", "after stopRecording, transcriptEnabled = $transcriptEnabled")

        if (transcriptEnabled) {
            android.util.Log.d("MEETAI_DEBUG", "→ Opening TranscriptActivity")
            val dateStamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",
                Locale.getDefault()).format(Date())
            val transcriptFileName = "Transcript_${channelName}_$dateStamp.txt"

            val intent = Intent(this, Transcriptactivity::class.java).apply {
                putExtra("audio_file_path",     audioFilePath)
                putExtra("transcript_filename", transcriptFileName)
                putExtra("room_code",           channelName)
                putExtra("duration",            formatDuration(seconds))
                // ── NEW: pass memberEmails to TranscriptActivity (Step 2) ──
                putStringArrayListExtra("member_emails", ArrayList(memberEmails))
            }
            startActivity(intent)
            finish()
            return
        }

        android.util.Log.d("MEETAI_DEBUG", "→ transcriptEnabled is FALSE, going back normally")
        finish()
    }

    private fun formatDuration(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format("%dh %02dm %02ds", h, m, s)
        else String.format("%02dm %02ds", m, s)
    }

    // ─────────────────────────── UI helpers ──────────────────────────
    private fun updateMicUI() {
        val tint = if (micOn) 0xFF2ACFD9.toInt() else 0xFFFF5252.toInt()
        val bg   = if (micOn) "#1A2ACFD9" else "#1AFF5252"
        btnMicWrapper.background?.setTint(android.graphics.Color.parseColor(bg))
        (btnMic as? ImageView)?.apply {
            setColorFilter(tint)
            setImageResource(if (micOn) R.drawable.ic_mic else R.drawable.ic_mic_off)
        }
    }

    private fun updateCameraUI() {
        val tint = if (cameraOn) 0xFFFFC107.toInt() else 0xFFFF5252.toInt()
        val bg   = if (cameraOn) "#1AFFC107" else "#1AFF5252"
        btnCamWrapper.background?.setTint(android.graphics.Color.parseColor(bg))
        (btnCamera as? ImageView)?.apply {
            setColorFilter(tint)
            setImageResource(
                if (cameraOn) R.drawable.ic_videocam else R.drawable.ic_videocam_off)
        }
    }

    // ─────────────────────────── Lifecycle ───────────────────────────
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timerRunnable)
        if (!isLeavingMeeting) {
            stopAudioRecording()
            rtcEngine?.leaveChannel()
        }
        RtcEngine.destroy()
        rtcEngine = null
    }

} // ← end of Meetingroomactivity

// ── Data & Adapter ────────────────────────────────────────────────────────────

data class ParticipantInfo(
    val uid: Int,
    val name: String,
    val isLocal: Boolean = false,
    var videoOn: Boolean = true
)

class ParticipantAdapter(
    private val participants: MutableList<ParticipantInfo>,
    private var rtcEngine: RtcEngine?
) : RecyclerView.Adapter<ParticipantAdapter.ViewHolder>() {

    fun setRtcEngine(engine: RtcEngine?) { rtcEngine = engine }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val videoContainer: FrameLayout  = view.findViewById(R.id.videoContainer)
        val avatarContainer: FrameLayout = view.findViewById(R.id.avatarContainer)
        val tvAvatar: TextView           = view.findViewById(R.id.tvAvatar)
        val tvName: TextView             = view.findViewById(R.id.tvParticipantName)
        val ivMicStatus: ImageView       = view.findViewById(R.id.ivMicStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_participant, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val participant = participants[position]
        holder.tvName.text   = participant.name
        holder.tvAvatar.text = participant.name.firstOrNull()?.uppercase() ?: "U"

        if (participant.videoOn) {
            holder.videoContainer.visibility  = View.VISIBLE
            holder.avatarContainer.visibility = View.GONE
            holder.videoContainer.removeAllViews()
            val surfaceView = SurfaceView(holder.itemView.context)
            if (participant.isLocal) {
                surfaceView.setZOrderMediaOverlay(true)
                rtcEngine?.setupLocalVideo(
                    VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, 0))
                rtcEngine?.startPreview()
            } else {
                rtcEngine?.setupRemoteVideo(
                    VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, participant.uid))
            }
            holder.videoContainer.addView(surfaceView)
        } else {
            holder.videoContainer.visibility  = View.GONE
            holder.avatarContainer.visibility = View.VISIBLE
        }
    }

    override fun getItemCount(): Int = participants.size

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.videoContainer.removeAllViews()
    }
}