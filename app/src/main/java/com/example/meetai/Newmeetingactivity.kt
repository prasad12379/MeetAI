package com.example.meetai

// ════════════════════════════════════════════════════════════════════════════
//  Newmeetingactivity.kt  —  MeetAI  •  Compose rewrite
//
//  All logic unchanged: generateCode, registerAdminAndLaunch, launchMeetingRoom,
//  permission handling, OkHttp calls, SharedPrefs email read.
// ════════════════════════════════════════════════════════════════════════════

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

// ── Palette ──────────────────────────────────────────────────────────────────
private val Ink         = Color(0xFF080C14)
private val InkMid      = Color(0xFF0E1422)
private val InkLight    = Color(0xFF161D2F)
private val InkLighter  = Color(0xFF1C2540)
private val Cyan        = Color(0xFF00D4FF)
private val CyanDim     = Color(0xFF0099BF)
private val Gold        = Color(0xFFFFBF3C)
private val TextPri     = Color(0xFFF0F6FF)
private val TextSub     = Color(0xFF6B7FA3)
private val TextMuted   = Color(0xFF3A4665)
private val CardBorder  = Color(0xFF1E293B)

private val BgGradient   = Brush.verticalGradient(listOf(Color(0xFF060A12), Ink, Color(0xFF0A1020)))
private val CyanGradient = Brush.linearGradient(listOf(Cyan, Color(0xFF0066FF)))

private const val BASE_URL        = "https://newmeetaibackend.onrender.com"
private const val PERMISSION_REQ  = 33

// ════════════════════════════════════════════════════════════════════════════
//  ACTIVITY
// ════════════════════════════════════════════════════════════════════════════
class Newmeetingactivity : AppCompatActivity() {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // Compose state hoisted to activity so permission callback can trigger launch
    private var micOn         by mutableStateOf(true)
    private var camOn         by mutableStateOf(true)
    private var transcriptOn  by mutableStateOf(true)
    private var currentCode   by mutableStateOf("")
    private var isLoading     by mutableStateOf(false)
    private var userEmail     = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Transparent status bar — icons light on dark bg
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = false

        val prefs = getSharedPreferences("meetai_prefs", MODE_PRIVATE)
        userEmail = prefs.getString("user_email", "") ?: ""

        generateCode()

        setContent {
            NewMeetingScreen(
                roomCode      = currentCode,
                isLoading     = isLoading,
                micOn         = micOn,
                camOn         = camOn,
                transcriptOn  = transcriptOn,
                onMicToggle   = { micOn = it },
                onCamToggle   = { camOn = it },
                onTransToggle = { transcriptOn = it },
                onBack        = { finish() },
                onCopy        = {
                    val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cb.setPrimaryClip(ClipData.newPlainText("Meeting Code", currentCode))
                    Toast.makeText(this, "Code copied!", Toast.LENGTH_SHORT).show()
                },
                onRefresh     = { refreshCode() },
                onStart       = { handleStartTap() }
            )
        }
    }

    private fun generateCode() {
        val num    = (1000..9999).random()
        val suffix = ('A'..'Z').shuffled().take(2).joinToString("")
        currentCode = "NXM-$num-$suffix"
    }

    private fun refreshCode() {
        generateCode()
    }

    private fun handleStartTap() {
        if (userEmail.isEmpty()) {
            Toast.makeText(this, "User email not found. Please login again.", Toast.LENGTH_SHORT).show()
            return
        }
        if (checkPermissions()) registerAdminAndLaunch()
        else ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            PERMISSION_REQ
        )
    }

    private fun checkPermissions() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQ) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) registerAdminAndLaunch()
            else Toast.makeText(this, "Camera and mic permission required to start a meeting", Toast.LENGTH_LONG).show()
        }
    }

    private fun registerAdminAndLaunch() {
        isLoading = true

        val body = JSONObject().apply {
            put("room_code", currentCode)
            put("email",     userEmail)
            put("is_admin",  true)
        }

        val request = Request.Builder()
            .url("$BASE_URL/meetings")
            .post(body.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    isLoading = false
                    Toast.makeText(this@Newmeetingactivity, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                runOnUiThread {
                    isLoading = false
                    when (response.code) {
                        200, 201 -> { Log.d("MEETAI", "Meeting created: $body"); launchMeetingRoom() }
                        409      -> { Toast.makeText(this@Newmeetingactivity, "Code conflict, retrying...", Toast.LENGTH_SHORT).show(); generateCode(); registerAdminAndLaunch() }
                        else     -> { Toast.makeText(this@Newmeetingactivity, "Failed to create meeting. Try again.", Toast.LENGTH_SHORT).show(); Log.e("MEETAI", "Error: $body") }
                    }
                }
            }
        })
    }

    private fun launchMeetingRoom() {
        val displayName = userEmail.substringBefore("@").ifEmpty { "Host" }
        startActivity(android.content.Intent(this, Meetingroomactivity::class.java).apply {
            putExtra("room_code",          currentCode)
            putExtra("display_name",       displayName)
            putExtra("user_email",         userEmail)
            putExtra("mic_on",             micOn)
            putExtra("cam_on",             camOn)
            putExtra("is_host",            true)
            putExtra("transcript_enabled", transcriptOn)
        })
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  SCREEN
// ════════════════════════════════════════════════════════════════════════════
@Composable
fun NewMeetingScreen(
    roomCode:      String,
    isLoading:     Boolean,
    micOn:         Boolean,
    camOn:         Boolean,
    transcriptOn:  Boolean,
    onMicToggle:   (Boolean) -> Unit,
    onCamToggle:   (Boolean) -> Unit,
    onTransToggle: (Boolean) -> Unit,
    onBack:        () -> Unit,
    onCopy:        () -> Unit,
    onRefresh:     () -> Unit,
    onStart:       () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); visible = true }

    Box(Modifier.fillMaxSize().background(BgGradient)) {
        // Ambient glow
        Box(Modifier.size(260.dp).offset(x = 160.dp, y = (-40).dp)
            .background(Brush.radialGradient(listOf(Color(0x1A00D4FF), Color.Transparent), radius = 400f), CircleShape))
        Box(Modifier.size(200.dp).offset(x = (-40).dp, y = 300.dp)
            .background(Brush.radialGradient(listOf(Color(0x120066FF), Color.Transparent), radius = 350f), CircleShape))

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Push content below status bar / notch — background paints behind it
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(16.dp))

            // Header
            AnimatedVisibility(visible, enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { -20 }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    BackButton(onBack)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("New Meeting", fontSize = 20.sp, fontWeight = FontWeight.Black,
                            color = TextPri, letterSpacing = (-0.4).sp)
                        Text("Set up your room", fontSize = 11.sp, color = TextSub)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // Room code card
            AnimatedVisibility(visible, enter = fadeIn(tween(500, 100)) + slideInVertically(tween(500, 100)) { 40 }) {
                RoomCodeCard(roomCode = roomCode, onCopy = onCopy, onRefresh = onRefresh)
            }

            Spacer(Modifier.height(20.dp))

            // Toggles card
            AnimatedVisibility(visible, enter = fadeIn(tween(500, 200)) + slideInVertically(tween(500, 200)) { 40 }) {
                TogglesCard(
                    micOn        = micOn,
                    camOn        = camOn,
                    transcriptOn = transcriptOn,
                    onMicToggle  = onMicToggle,
                    onCamToggle  = onCamToggle,
                    onTransToggle = onTransToggle
                )
            }

            Spacer(Modifier.height(28.dp))

            // Start button
            AnimatedVisibility(visible, enter = fadeIn(tween(500, 320)) + slideInVertically(tween(500, 320)) { 40 }) {
                StartButton(isLoading = isLoading, onClick = onStart)
            }

            Spacer(Modifier.height(100.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  ROOM CODE CARD
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun RoomCodeCard(roomCode: String, onCopy: () -> Unit, onRefresh: () -> Unit) {
    Column(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(InkMid)
            .border(1.dp, CardBorder, RoundedCornerShape(22.dp))
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(CyanGradient))

        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Room Code", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp)

            // Code display
            AnimatedContent(
                targetState = roomCode,
                transitionSpec = {
                    fadeIn(tween(200)) + slideInVertically(tween(200)) { -10 } togetherWith
                            fadeOut(tween(150)) + slideOutVertically(tween(150)) { 10 }
                }, label = "codeAnim"
            ) { code ->
                Text(
                    code,
                    fontSize      = 30.sp,
                    fontWeight    = FontWeight.Black,
                    color         = Cyan,
                    letterSpacing = 2.sp
                )
            }

            Text("Share this code with meeting participants", fontSize = 11.sp, color = TextSub)

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CodeActionButton(
                    label    = "Copy Code",
                    modifier = Modifier.weight(1f),
                    gradient = CyanGradient,
                    onClick  = onCopy
                )
                CodeActionButton(
                    label    = "New Code",
                    modifier = Modifier.weight(1f),
                    gradient = Brush.linearGradient(listOf(InkLighter, InkLight)),
                    border   = CardBorder,
                    textColor = TextSub,
                    onClick  = onRefresh
                )
            }
        }
    }
}

@Composable
private fun CodeActionButton(
    label:     String,
    modifier:  Modifier,
    gradient:  Brush,
    border:    Color   = Color.Transparent,
    textColor: Color   = Color.Black,
    onClick:   () -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "cab")

    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(12.dp))
            .background(gradient)
            .then(if (border != Color.Transparent) Modifier.border(1.dp, border, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(interactionSource, null) { onClick() }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor)
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  TOGGLES CARD
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun TogglesCard(
    micOn:         Boolean,
    camOn:         Boolean,
    transcriptOn:  Boolean,
    onMicToggle:   (Boolean) -> Unit,
    onCamToggle:   (Boolean) -> Unit,
    onTransToggle: (Boolean) -> Unit
) {
    Column(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(InkMid)
            .border(1.dp, CardBorder, RoundedCornerShape(22.dp))
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp)
            .background(Brush.linearGradient(listOf(Gold, Color(0xFFFF9500)))))

        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text("Before you start", fontSize = 10.sp, color = TextMuted,
                fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(14.dp))
            ToggleRow("Microphone",  "Join with audio on",    micOn,        Cyan,  onMicToggle)
            RowDivider()
            ToggleRow("Camera",      "Join with video on",    camOn,        Gold,  onCamToggle)
            RowDivider()
            ToggleRow("Transcription","Auto-record & transcribe", transcriptOn, Cyan, onTransToggle)
        }
    }
}

@Composable
private fun ToggleRow(
    title:    String,
    subtitle: String,
    checked:  Boolean,
    tint:     Color,
    onToggle: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(8.dp)
                .background(if (checked) tint.copy(0.3f) else TextMuted.copy(0.3f), CircleShape)
                .border(1.dp, if (checked) tint.copy(0.7f) else TextMuted, CircleShape))
            Column {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPri)
                Text(subtitle, fontSize = 10.sp, color = TextSub)
            }
        }
        PremiumSwitch(checked = checked, tint = tint, onToggle = onToggle)
    }
}

@Composable
private fun PremiumSwitch(checked: Boolean, tint: Color, onToggle: (Boolean) -> Unit) {
    val thumbOffset by animateDpAsState(if (checked) 18.dp else 2.dp,
        spring(Spring.DampingRatioMediumBouncy), label = "thumb")
    val trackBg by animateColorAsState(if (checked) tint.copy(0.25f) else InkLight,
        tween(200), label = "track")
    val borderColor by animateColorAsState(if (checked) tint.copy(0.6f) else TextMuted.copy(0.3f),
        tween(200), label = "border")

    Box(
        Modifier.width(42.dp).height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(trackBg)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onToggle(!checked) },
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset, y = 2.dp)
                .size(20.dp)
                .background(if (checked) tint else TextMuted, CircleShape)
        )
    }
}

@Composable
private fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(CardBorder))
}

// ════════════════════════════════════════════════════════════════════════════
//  START BUTTON
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun StartButton(isLoading: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f,
        spring(Spring.DampingRatioMediumBouncy), label = "start")

    // Pulse glow when not loading
    val glowAlpha by rememberInfiniteTransition(label = "glow").animateFloat(
        0.3f, 0.7f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "ga"
    )

    Box(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                drawRoundRect(
                    color        = Cyan.copy(if (isLoading) 0f else glowAlpha * 0.25f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
                    size         = size.copy(width = size.width + 8.dp.toPx(), height = size.height + 8.dp.toPx()),
                    topLeft      = androidx.compose.ui.geometry.Offset(-4.dp.toPx(), -4.dp.toPx())
                )
            }
            .clip(RoundedCornerShape(18.dp))
            .background(if (isLoading) Brush.linearGradient(listOf(InkLight, InkLight)) else CyanGradient)
            .clickable(interactionSource, null, enabled = !isLoading) { onClick() }
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LoadingDots()
                Text("Creating meeting...", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSub)
            }
        } else {
            Text("Start Meeting", fontSize = 15.sp, fontWeight = FontWeight.Black,
                color = Color.Black, letterSpacing = 0.3.sp)
        }
    }
}

@Composable
private fun LoadingDots() {
    val inf = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        (0..2).forEach { i ->
            val alpha by inf.animateFloat(0.2f, 1f,
                infiniteRepeatable(tween(600, delayMillis = i * 150), RepeatMode.Reverse), label = "d$i")
            Box(Modifier.size(5.dp).graphicsLayer { this.alpha = alpha }
                .background(Cyan, CircleShape))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  BACK BUTTON
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun BackButton(onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(InkLight)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(16.dp)) {
            val p = androidx.compose.ui.graphics.Paint().apply {
                color = TextSub; style = PaintingStyle.Stroke
                strokeWidth = 2f; strokeCap = StrokeCap.Round; strokeJoin = StrokeJoin.Round
            }
            drawContext.canvas.apply {
                drawLine(androidx.compose.ui.geometry.Offset(size.width - 2f, size.height / 2),
                    androidx.compose.ui.geometry.Offset(3f, size.height / 2), p)
                drawLine(androidx.compose.ui.geometry.Offset(9f, 3f),
                    androidx.compose.ui.geometry.Offset(3f, size.height / 2), p)
                drawLine(androidx.compose.ui.geometry.Offset(9f, size.height - 3f),
                    androidx.compose.ui.geometry.Offset(3f, size.height / 2), p)
            }
        }
    }
}