package com.example.meetai

// ════════════════════════════════════════════════════════════════════════════
//  Meetingroomactivity.kt  —  MeetAI  •  Hybrid rewrite
//
//  Strategy:
//    • RecyclerView + ParticipantAdapter (Agora SurfaceView) → unchanged XML
//    • ALL control chrome (top bar, bottom controls, transcript badge,
//      end-call dialog) → Compose overlay via ComposeView
//
//  All Agora, recording, permission, and navigation logic is 100% unchanged.
// ════════════════════════════════════════════════════════════════════════════

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
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
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

// ── Palette ──────────────────────────────────────────────────────────────────
private val MInk        = Color(0xFF060A12)
private val MInkMid     = Color(0xCC0E1422)   // semi-transparent for overlays
private val MInkLight   = Color(0xFF161D2F)
private val MCyan       = Color(0xFF00D4FF)
private val MGold       = Color(0xFFFFBF3C)
private val MRed        = Color(0xFFFF4757)
private val MRedDim     = Color(0xCC3D1520)
private val MGreen      = Color(0xFF00E5A0)
private val MTextPri    = Color(0xFFF0F6FF)
private val MTextSub    = Color(0xFF6B7FA3)
private val MTextMuted  = Color(0xFF3A4665)
private val MCardBorder = Color(0xFF1E293B)

private val MCyanGrad  = Brush.linearGradient(listOf(MCyan, Color(0xFF0066FF)))
private val MRedGrad   = Brush.linearGradient(listOf(MRed, Color(0xFFFF6B35)))
private val MGoldGrad  = Brush.linearGradient(listOf(MGold, Color(0xFFFF9500)))

// ════════════════════════════════════════════════════════════════════════════
//  ACTIVITY
// ════════════════════════════════════════════════════════════════════════════
class Meetingroomactivity : AppCompatActivity() {

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

    // ── Compose-observable state ──────────────────────────────────────
    private var micOn             by mutableStateOf(true)
    private var cameraOn          by mutableStateOf(true)
    private var timerText         by mutableStateOf("00:00")
    private var participantCount  by mutableStateOf(1)
    private var transcriptEnabled by mutableStateOf(false)
    private var showEndDialog     by mutableStateOf(false)

    // ── Non-reactive state ────────────────────────────────────────────
    private var seconds      = 0
    private var channelName  = ""
    private var displayName  = ""
    private var userEmail    = ""

    // Agora
    private var rtcEngine: RtcEngine? = null

    // Recording
    private var mediaRecorder: MediaRecorder? = null
    private var audioFilePath: String = ""
    private var isRecording = false

    // Participants (RecyclerView - unchanged)
    private val participants = mutableListOf<ParticipantInfo>()
    private lateinit var adapter: ParticipantAdapter
    private lateinit var rvParticipants: RecyclerView

    private val handler = Handler(Looper.getMainLooper())

    private val timerRunnable = object : Runnable {
        override fun run() {
            seconds++
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60
            timerText = if (h > 0) String.format("%d:%02d:%02d", h, m, s)
            else       String.format("%02d:%02d", m, s)
            handler.postDelayed(this, 1000)
        }
    }

    // ── Agora events (logic unchanged) ───────────────────────────────
    private val rtcEventHandler = object : IRtcEngineEventHandler() {

        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
            runOnUiThread {
                handler.post(timerRunnable)
                participants.add(0, ParticipantInfo(
                    uid = uid, name = displayName.ifEmpty { "You" },
                    isLocal = true, videoOn = cameraOn))
                adapter.notifyDataSetChanged()
                updateGridLayout()
                Toast.makeText(this@Meetingroomactivity,
                    "Connected to $channel", Toast.LENGTH_SHORT).show()
                if (userEmail.isNotEmpty()) memberEmails.add(userEmail)
                if (transcriptEnabled) startAudioRecording()
            }
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            runOnUiThread {
                participantCount++
                participants.add(ParticipantInfo(
                    uid = uid, name = "User $uid", isLocal = false, videoOn = true))
                adapter.notifyDataSetChanged()
                updateGridLayout()
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            runOnUiThread {
                participantCount = maxOf(1, participantCount - 1)
                participants.removeAll { it.uid == uid }
                adapter.notifyDataSetChanged()
                updateGridLayout()
                Toast.makeText(this@Meetingroomactivity,
                    "A participant left", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onRemoteVideoStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
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

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor     = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars    = false
            isAppearanceLightNavigationBars = false
        }

        setContentView(R.layout.activity_meetingroomactivity)

        // Read intent
        channelName       = intent.getStringExtra("room_code")    ?: "default-room"
        displayName       = intent.getStringExtra("display_name") ?: "You"
        micOn             = intent.getBooleanExtra("mic_on",    true)
        cameraOn          = intent.getBooleanExtra("cam_on",    true)
        transcriptEnabled = intent.getBooleanExtra("transcript_enabled", false)
        userEmail         = intent.getStringExtra("user_email") ?: ""

        // Bind the RecyclerView (the ONLY XML view we touch directly)
        rvParticipants = findViewById(R.id.rvParticipants)
        adapter = ParticipantAdapter(participants, rtcEngine)
        rvParticipants.adapter = adapter
        rvParticipants.layoutManager = GridLayoutManager(this, 2)

        // Attach Compose overlay (top bar + bottom controls)
        val overlay = findViewById<FrameLayout>(R.id.composeControlOverlay)
        overlay.addView(ComposeView(this).apply {
            setContent {
                MeetingRoomOverlay(
                    roomCode          = channelName,
                    timerText         = timerText,
                    participantCount  = participantCount,
                    micOn             = micOn,
                    cameraOn          = cameraOn,
                    transcriptEnabled = transcriptEnabled,
                    showEndDialog     = showEndDialog,
                    onToggleMic       = {
                        micOn = !micOn
                        rtcEngine?.muteLocalAudioStream(!micOn)
                    },
                    onToggleCam       = {
                        cameraOn = !cameraOn
                        rtcEngine?.muteLocalVideoStream(!cameraOn)
                        participants.find { it.isLocal }?.videoOn = cameraOn
                        adapter.notifyDataSetChanged()
                    },
                    onEndCall         = { showEndDialog = true },
                    onParticipants    = {
                        Toast.makeText(this@Meetingroomactivity,
                            "$participantCount participant(s) in this meeting",
                            Toast.LENGTH_SHORT).show()
                    },
                    onChat            = {
                        Toast.makeText(this@Meetingroomactivity,
                            "Chat — coming soon", Toast.LENGTH_SHORT).show()
                    },
                    onShare           = {
                        startActivity(Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT,
                                    "Join my MeetAI meeting. Code: $channelName")
                            }, "Share code"))
                    },
                    onConfirmLeave    = { leaveAndFinish() },
                    onDismissDialog   = { showEndDialog = false }
                )
            }
        })

        if (checkPermissions()) initAgoraAndJoin()
        else ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQ_ID)
    }

    // ─────────────────────────── Permissions ─────────────────────────
    private fun checkPermissions() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQ_ID) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) initAgoraAndJoin()
            else { Toast.makeText(this, "Camera and microphone permission required", Toast.LENGTH_LONG).show(); finish() }
        }
    }

    // ─────────────────────────── Agora ───────────────────────────────
    private fun initAgoraAndJoin() {
        if (APP_ID.isBlank()) {
            android.app.AlertDialog.Builder(this)
                .setTitle("App ID Missing").setMessage("Please add your Agora App ID")
                .setPositiveButton("OK") { _, _ -> finish() }.show()
            return
        }
        try {
            val config = RtcEngineConfig().apply {
                mContext = applicationContext; mAppId = APP_ID; mEventHandler = rtcEventHandler
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
            val options = ChannelMediaOptions().apply {
                channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
                clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            }
            rtcEngine?.joinChannel(TOKEN, channelName, 0, options)
            if (!micOn)    rtcEngine?.muteLocalAudioStream(true)
            if (!cameraOn) rtcEngine?.muteLocalVideoStream(true)
        } catch (e: Exception) {
            Toast.makeText(this, "Agora init failed: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    // ── Recording (unchanged) ─────────────────────────────────────────
    private fun startAudioRecording() {
        try {
            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            audioFilePath = File(cacheDir, "meeting_${channelName}_$stamp.mp3").absolutePath
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this)
            else @Suppress("DEPRECATION") MediaRecorder()
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(audioFilePath)
                prepare(); start(); isRecording = true
            }
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Recording failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopAudioRecording() {
        if (!isRecording) return
        try { mediaRecorder?.apply { stop(); release() } } catch (e: Exception) { e.printStackTrace() }
        finally { mediaRecorder = null; isRecording = false }
    }

    // ── Leave (unchanged) ─────────────────────────────────────────────
    private fun leaveAndFinish() {
        if (isLeavingMeeting) return
        isLeavingMeeting = true
        stopAudioRecording()
        rtcEngine?.leaveChannel()

        if (transcriptEnabled) {
            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            startActivity(Intent(this, Transcriptactivity::class.java).apply {
                putExtra("audio_file_path",     audioFilePath)
                putExtra("transcript_filename", "Transcript_${channelName}_$stamp.txt")
                putExtra("room_code",           channelName)
                putExtra("duration",            formatDuration(seconds))
                putStringArrayListExtra("member_emails", ArrayList(memberEmails))
            })
        }
        finish()
    }

    private fun formatDuration(t: Int): String {
        val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
        return if (h > 0) String.format("%dh %02dm %02ds", h, m, s)
        else       String.format("%02dm %02ds", m, s)
    }

    private fun updateGridLayout() {
        val span = when { participants.size == 1 -> 1; participants.size <= 4 -> 2; else -> 3 }
        (rvParticipants.layoutManager as GridLayoutManager).spanCount = span
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timerRunnable)
        if (!isLeavingMeeting) { stopAudioRecording(); rtcEngine?.leaveChannel() }
        RtcEngine.destroy(); rtcEngine = null
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  COMPOSE OVERLAY  — top bar + bottom control bar + dialogs
// ════════════════════════════════════════════════════════════════════════════
@Composable
fun MeetingRoomOverlay(
    roomCode:          String,
    timerText:         String,
    participantCount:  Int,
    micOn:             Boolean,
    cameraOn:          Boolean,
    transcriptEnabled: Boolean,
    showEndDialog:     Boolean,
    onToggleMic:       () -> Unit,
    onToggleCam:       () -> Unit,
    onEndCall:         () -> Unit,
    onParticipants:    () -> Unit,
    onChat:            () -> Unit,
    onShare:           () -> Unit,
    onConfirmLeave:    () -> Unit,
    onDismissDialog:   () -> Unit
) {
    Box(Modifier.fillMaxSize()) {

        // ── TOP BAR ──────────────────────────────────────────────────
        TopBar(
            roomCode          = roomCode,
            timerText         = timerText,
            participantCount  = participantCount,
            transcriptEnabled = transcriptEnabled,
            modifier          = Modifier.align(Alignment.TopCenter)
        )

        // ── BOTTOM CONTROL BAR ────────────────────────────────────────
        BottomControlBar(
            micOn        = micOn,
            cameraOn     = cameraOn,
            onToggleMic  = onToggleMic,
            onToggleCam  = onToggleCam,
            onEndCall    = onEndCall,
            onParticipants = onParticipants,
            onChat       = onChat,
            onShare      = onShare,
            modifier     = Modifier.align(Alignment.BottomCenter)
        )

        // ── END CALL DIALOG ───────────────────────────────────────────
        if (showEndDialog) {
            EndCallDialog(
                onConfirm = onConfirmLeave,
                onDismiss = onDismissDialog
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  TOP BAR
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun TopBar(
    roomCode:          String,
    timerText:         String,
    participantCount:  Int,
    transcriptEnabled: Boolean,
    modifier:          Modifier
) {
    // Recording pulse
    val inf = rememberInfiniteTransition(label = "recPulse")
    val recAlpha by inf.animateFloat(
        0.4f, 1f,
        infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "ra"
    )

    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Frosted glass pill
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xCC080C14))
                .border(1.dp, Color(0x2200D4FF), RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Room code
            Column {
                Text("Room", fontSize = 9.sp, color = MTextMuted,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
                Text(roomCode, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = MCyan, letterSpacing = 0.5.sp)
            }

            // Timer (centred)
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Live dot
                Box(Modifier.size(6.dp).background(MGreen, CircleShape)
                    .graphicsLayer { alpha = recAlpha })
                Text(timerText, fontSize = 16.sp, fontWeight = FontWeight.Black,
                    color = MTextPri, letterSpacing = 1.sp)
            }

            // Participant chip + transcript badge
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Participant count
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x22FFFFFF))
                        .border(1.dp, Color(0x15FFFFFF), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // People icon (canvas)
                        androidx.compose.foundation.Canvas(Modifier.size(10.dp)) {
                            // Head 1
                            drawCircle(
                                color  = MTextSub,
                                radius = size.width * 0.18f,
                                center = androidx.compose.ui.geometry.Offset(size.width * 0.35f, size.height * 0.3f)
                            )
                            // Head 2 (smaller, behind)
                            drawCircle(
                                color  = MTextSub,
                                radius = size.width * 0.15f,
                                center = androidx.compose.ui.geometry.Offset(size.width * 0.7f, size.height * 0.3f)
                            )
                            // Shoulders arc
                            drawArc(
                                color      = MTextSub,
                                startAngle = 0f,
                                sweepAngle = 180f,
                                useCenter  = false,
                                topLeft    = androidx.compose.ui.geometry.Offset(0f, size.height * 0.52f),
                                size       = androidx.compose.ui.geometry.Size(size.width * 0.65f, size.height * 0.48f),
                                style      = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 1.2f,
                                    cap   = StrokeCap.Round
                                )
                            )
                        }
                        Text("$participantCount", fontSize = 11.sp, color = MTextSub,
                            fontWeight = FontWeight.Bold)
                    }
                }

                // Transcript recording badge
                if (transcriptEnabled) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MRed.copy(0.2f))
                            .border(1.dp, MRed.copy(0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(Modifier.size(5.dp).background(MRed.copy(recAlpha), CircleShape))
                            Text("REC", fontSize = 8.sp, color = MRed,
                                fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  BOTTOM CONTROL BAR
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun BottomControlBar(
    micOn:         Boolean,
    cameraOn:      Boolean,
    onToggleMic:   () -> Unit,
    onToggleCam:   () -> Unit,
    onEndCall:     () -> Unit,
    onParticipants: () -> Unit,
    onChat:        () -> Unit,
    onShare:       () -> Unit,
    modifier:      Modifier
) {
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xE6080C14))
                .border(
                    1.dp,
                    Brush.linearGradient(listOf(MCyan.copy(0.25f), Color.Transparent, MGold.copy(0.15f))),
                    RoundedCornerShape(28.dp)
                )
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Mic
            ControlButton(
                label   = if (micOn) "Mute" else "Unmute",
                active  = micOn,
                tint    = MCyan,
                icon    = { MicIcon(micOn = micOn) },
                onClick = onToggleMic
            )

            // Camera
            ControlButton(
                label   = if (cameraOn) "Camera" else "Cam Off",
                active  = cameraOn,
                tint    = MGold,
                icon    = { CamIcon(camOn = cameraOn) },
                onClick = onToggleCam
            )

            // End Call — centre, larger, red
            EndCallButton(onClick = onEndCall)

            // Participants
            ControlButton(
                label   = "People",
                active  = true,
                tint    = MTextSub,
                icon    = { PeopleIcon() },
                onClick = onParticipants
            )

            // Share
            ControlButton(
                label   = "Share",
                active  = true,
                tint    = MTextSub,
                icon    = { ShareIcon() },
                onClick = onShare
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  CONTROL BUTTON
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun ControlButton(
    label:   String,
    active:  Boolean,
    tint:    Color,
    icon:    @Composable () -> Unit,
    onClick: () -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f,
        spring(Spring.DampingRatioMediumBouncy), label = "cbs")

    Column(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource, null) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(
                    if (!active && tint != MTextSub) MRed.copy(0.18f)
                    else tint.copy(0.15f)
                )
                .border(
                    1.dp,
                    if (!active && tint != MTextSub) MRed.copy(0.4f) else tint.copy(0.3f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Text(label, fontSize = 9.sp,
            color = if (!active && tint != MTextSub) MRed else MTextSub,
            fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp)
    }
}

@Composable
private fun EndCallButton(onClick: () -> Unit) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f,
        spring(Spring.DampingRatioMediumBouncy), label = "ebs")
    val glow by rememberInfiniteTransition(label = "eg").animateFloat(
        0.3f, 0.7f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "ega")

    Column(
        Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource, null) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Glow ring
            Box(Modifier.size(66.dp).background(
                Brush.radialGradient(listOf(MRed.copy(glow * 0.4f), Color.Transparent)),
                CircleShape))
            Box(
                Modifier.size(54.dp).clip(CircleShape)
                    .background(MRedGrad)
                    .border(2.dp, MRed.copy(0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                PhoneHangIcon()
            }
        }
        Text("End", fontSize = 9.sp, color = MRed, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  END CALL DIALOG
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun EndCallDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(0.6f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF0E1422))
                .border(1.dp, MRed.copy(0.3f), RoundedCornerShape(24.dp))
                .clickable { /* consume so backdrop doesn't close */ }
        ) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(MRedGrad))
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Leave Meeting?", fontSize = 18.sp, fontWeight = FontWeight.Black, color = MTextPri)
                Text("Are you sure you want to end this session?",
                    fontSize = 12.sp, color = MTextSub, lineHeight = 18.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Stay
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                            .background(MInkLight).border(1.dp, MCardBorder, RoundedCornerShape(14.dp))
                            .clickable { onDismiss() }.padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("Stay", fontSize = 13.sp, color = MTextSub, fontWeight = FontWeight.SemiBold) }
                    // Leave
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                            .background(MRedGrad).clickable { onConfirm() }.padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("Leave", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  CANVAS ICONS
// ════════════════════════════════════════════════════════════════════════════
@Composable private fun MicIcon(micOn: Boolean) {
    val c = if (micOn) MCyan else MRed
    androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
        val p = androidx.compose.ui.graphics.Paint().apply {
            color = c; style = PaintingStyle.Stroke
            strokeWidth = 1.8f; strokeCap = StrokeCap.Round; strokeJoin = StrokeJoin.Round
        }
        val fp = androidx.compose.ui.graphics.Paint().apply { color = c; style = PaintingStyle.Fill }
        drawContext.canvas.apply {
            drawRoundRect(size.width * 0.3f, 0f, size.width * 0.7f, size.height * 0.6f, 6f, 6f, fp)
            drawArc(size.width * 0.1f, size.height * 0.3f, size.width * 0.9f, size.height * 0.85f,
                0f, 180f, false, p)
            drawLine(androidx.compose.ui.geometry.Offset(size.width / 2, size.height * 0.85f),
                androidx.compose.ui.geometry.Offset(size.width / 2, size.height), p)
            drawLine(androidx.compose.ui.geometry.Offset(size.width * 0.3f, size.height),
                androidx.compose.ui.geometry.Offset(size.width * 0.7f, size.height), p)
            if (!micOn) {
                val xp = androidx.compose.ui.graphics.Paint().apply {
                    color = MRed; style = PaintingStyle.Stroke; strokeWidth = 2f; strokeCap = StrokeCap.Round
                }
                drawLine(androidx.compose.ui.geometry.Offset(2f, 2f),
                    androidx.compose.ui.geometry.Offset(size.width - 2f, size.height - 2f), xp)
            }
        }
    }
}

@Composable private fun CamIcon(camOn: Boolean) {
    val c = if (camOn) MGold else MRed
    androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
        val p = androidx.compose.ui.graphics.Paint().apply {
            color = c; style = PaintingStyle.Stroke; strokeWidth = 1.8f; strokeCap = StrokeCap.Round; strokeJoin = StrokeJoin.Round
        }
        val fp = androidx.compose.ui.graphics.Paint().apply { color = c; style = PaintingStyle.Fill }
        drawContext.canvas.apply {
            drawRoundRect(1f, size.height * 0.25f, size.width * 0.7f, size.height * 0.82f, 4f, 4f, p)
            val path = Path().apply {
                moveTo(size.width * 0.7f, size.height * 0.35f)
                lineTo(size.width - 1f, size.height * 0.2f)
                lineTo(size.width - 1f, size.height * 0.8f)
                lineTo(size.width * 0.7f, size.height * 0.65f); close()
            }
            drawPath(path, p)
            if (!camOn) {
                val xp = androidx.compose.ui.graphics.Paint().apply {
                    color = MRed; style = PaintingStyle.Stroke; strokeWidth = 2f; strokeCap = StrokeCap.Round
                }
                drawLine(androidx.compose.ui.geometry.Offset(2f, 2f),
                    androidx.compose.ui.geometry.Offset(size.width - 2f, size.height - 2f), xp)
            }
        }
    }
}

@Composable private fun PeopleIcon() {
    androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
        // Head 1 (front)
        drawCircle(
            color  = MTextSub,
            radius = size.width * 0.2f,
            center = androidx.compose.ui.geometry.Offset(size.width * 0.35f, size.height * 0.28f)
        )
        // Head 2 (back, smaller)
        drawCircle(
            color  = MTextSub,
            radius = size.width * 0.16f,
            center = androidx.compose.ui.geometry.Offset(size.width * 0.72f, size.height * 0.28f)
        )
        // Front person shoulders
        drawArc(
            color      = MTextSub,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter  = false,
            topLeft    = androidx.compose.ui.geometry.Offset(0f, size.height * 0.54f),
            size       = androidx.compose.ui.geometry.Size(size.width * 0.65f, size.height * 0.46f),
            style      = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f, cap = StrokeCap.Round)
        )
        // Back person shoulders
        drawArc(
            color      = MTextSub,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter  = false,
            topLeft    = androidx.compose.ui.geometry.Offset(size.width * 0.4f, size.height * 0.56f),
            size       = androidx.compose.ui.geometry.Size(size.width * 0.6f, size.height * 0.44f),
            style      = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f, cap = StrokeCap.Round)
        )
    }
}

@Composable private fun ShareIcon() {
    androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
        val p = androidx.compose.ui.graphics.Paint().apply {
            color = MTextSub; style = PaintingStyle.Stroke
            strokeWidth = 1.8f; strokeCap = StrokeCap.Round; strokeJoin = StrokeJoin.Round
        }
        val fp = androidx.compose.ui.graphics.Paint().apply { color = MTextSub; style = PaintingStyle.Fill }
        drawContext.canvas.apply {
            val cx1 = size.width * 0.18f; val cy1 = size.height / 2
            val cx2 = size.width * 0.82f; val cy2 = size.height * 0.18f
            val cx3 = size.width * 0.82f; val cy3 = size.height * 0.82f
            drawCircle(androidx.compose.ui.geometry.Offset(cx1, cy1), size.width * 0.14f, fp)
            drawCircle(androidx.compose.ui.geometry.Offset(cx2, cy2), size.width * 0.14f, fp)
            drawCircle(androidx.compose.ui.geometry.Offset(cx3, cy3), size.width * 0.14f, fp)
            drawLine(androidx.compose.ui.geometry.Offset(cx1, cy1), androidx.compose.ui.geometry.Offset(cx2, cy2), p)
            drawLine(androidx.compose.ui.geometry.Offset(cx1, cy1), androidx.compose.ui.geometry.Offset(cx3, cy3), p)
        }
    }
}

@Composable private fun PhoneHangIcon() {
    androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
        val p = androidx.compose.ui.graphics.Paint().apply {
            color = Color.White; style = PaintingStyle.Stroke
            strokeWidth = 2.5f; strokeCap = StrokeCap.Round; strokeJoin = StrokeJoin.Round
        }
        drawContext.canvas.apply {
            val path = Path().apply {
                moveTo(size.width * 0.1f, size.height * 0.45f)
                cubicTo(size.width * 0.1f, size.height * 0.25f,
                    size.width * 0.4f, size.height * 0.15f,
                    size.width / 2, size.height * 0.15f)
                cubicTo(size.width * 0.6f, size.height * 0.15f,
                    size.width * 0.9f, size.height * 0.25f,
                    size.width * 0.9f, size.height * 0.45f)
            }
            drawPath(path, p)
            val lp = androidx.compose.ui.graphics.Paint().apply {
                color = Color.White; style = PaintingStyle.Fill
            }
            drawRoundRect(size.width * 0.05f, size.height * 0.42f,
                size.width * 0.35f, size.height * 0.7f, 5f, 5f, lp)
            drawRoundRect(size.width * 0.65f, size.height * 0.42f,
                size.width * 0.95f, size.height * 0.7f, 5f, 5f, lp)
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  DATA + ADAPTER (unchanged)
// ════════════════════════════════════════════════════════════════════════════
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
        val videoContainer:  FrameLayout = view.findViewById(R.id.videoContainer)
        val avatarContainer: FrameLayout = view.findViewById(R.id.avatarContainer)
        val tvAvatar:        TextView    = view.findViewById(R.id.tvAvatar)
        val tvName:          TextView    = view.findViewById(R.id.tvParticipantName)
        val ivMicStatus:     ImageView   = view.findViewById(R.id.ivMicStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_participant, parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val p = participants[position]
        holder.tvName.text   = p.name
        holder.tvAvatar.text = p.name.firstOrNull()?.uppercase() ?: "U"

        if (p.videoOn) {
            holder.videoContainer.visibility  = View.VISIBLE
            holder.avatarContainer.visibility = View.GONE
            holder.videoContainer.removeAllViews()
            val sv = SurfaceView(holder.itemView.context)
            if (p.isLocal) {
                sv.setZOrderMediaOverlay(true)
                rtcEngine?.setupLocalVideo(VideoCanvas(sv, VideoCanvas.RENDER_MODE_HIDDEN, 0))
                rtcEngine?.startPreview()
            } else {
                rtcEngine?.setupRemoteVideo(VideoCanvas(sv, VideoCanvas.RENDER_MODE_HIDDEN, p.uid))
            }
            holder.videoContainer.addView(sv)
        } else {
            holder.videoContainer.visibility  = View.GONE
            holder.avatarContainer.visibility = View.VISIBLE
        }
    }

    override fun getItemCount() = participants.size

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.videoContainer.removeAllViews()
    }
}