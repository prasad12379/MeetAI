package com.example.meetai

// ════════════════════════════════════════════════════════════════════════════
//  Joinmeetingactivity.kt  —  MeetAI  •  Compose rewrite
//
//  All logic unchanged: registerMemberAndJoin, launchMeetingRoom,
//  SharedPrefs email/name read-write, OkHttp calls, validation toasts.
// ════════════════════════════════════════════════════════════════════════════

import android.content.Intent
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

// ── Palette ──────────────────────────────────────────────────────────────────
private val Ink2         = Color(0xFF080C14)
private val InkMid2      = Color(0xFF0E1422)
private val InkLight2    = Color(0xFF161D2F)
private val InkLighter2  = Color(0xFF1C2540)
private val Cyan2        = Color(0xFF00D4FF)
private val Green2       = Color(0xFF00E5A0)
private val TextPri2     = Color(0xFFF0F6FF)
private val TextSub2     = Color(0xFF6B7FA3)
private val TextMuted2   = Color(0xFF3A4665)
private val CardBorder2  = Color(0xFF1E293B)

private val BgGradient2    = Brush.verticalGradient(listOf(Color(0xFF060A12), Ink2, Color(0xFF0A1020)))
private val GreenGradient2 = Brush.linearGradient(listOf(Green2, Color(0xFF0099FF)))
private val CyanGradient2  = Brush.linearGradient(listOf(Cyan2, Color(0xFF0066FF)))

private const val BASE_URL2 = "https://newmeetaibackend.onrender.com"

// ════════════════════════════════════════════════════════════════════════════
//  ACTIVITY
// ════════════════════════════════════════════════════════════════════════════
class Joinmeetingactivity : AppCompatActivity() {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private var micOn     by mutableStateOf(true)
    private var camOn     by mutableStateOf(true)
    private var isLoading by mutableStateOf(false)
    private var userEmail = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = false

        val prefs    = getSharedPreferences("meetai_prefs", MODE_PRIVATE)
        userEmail    = prefs.getString("user_email", "") ?: ""
        val savedName = prefs.getString("user_name", "") ?: ""

        setContent {
            JoinMeetingScreen(
                prefillName   = savedName,
                isLoading     = isLoading,
                micOn         = micOn,
                camOn         = camOn,
                onMicToggle   = { micOn = it },
                onCamToggle   = { camOn = it },
                onBack        = { finish() },
                onJoin        = { code, name -> handleJoin(code, name) }
            )
        }
    }

    private fun handleJoin(code: String, name: String) {
        if (code.isEmpty()) { toast("Please enter a meeting code"); return }
        if (name.isEmpty()) { toast("Please enter your name"); return }
        if (userEmail.isEmpty()) { toast("User email not found. Please login again."); return }

        // Save display name
        getSharedPreferences("meetai_prefs", MODE_PRIVATE).edit().putString("user_name", name).apply()
        registerMemberAndJoin(code.uppercase(), name)
    }

    private fun registerMemberAndJoin(code: String, displayName: String) {
        isLoading = true

        val body = JSONObject().apply {
            put("room_code", code)
            put("email",     userEmail)
            put("is_admin",  false)
        }

        val request = Request.Builder()
            .url("$BASE_URL2/meetings")
            .post(body.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { isLoading = false; toast("Network error: ${e.message}") }
            }

            override fun onResponse(call: Call, response: Response) {
                val rb = response.body?.string() ?: ""
                runOnUiThread {
                    isLoading = false
                    when (response.code) {
                        200, 201 -> { Log.d("MEETAI", "Joined: $rb"); launchMeetingRoom(code, displayName) }
                        404      -> toast("Meeting not found. Please check the room code.")
                        else     -> { toast("Failed to join meeting. Try again."); Log.e("MEETAI", "Error: $rb") }
                    }
                }
            }
        })
    }

    private fun launchMeetingRoom(code: String, displayName: String) {
        startActivity(Intent(this, Meetingroomactivity::class.java).apply {
            putExtra("room_code",          code)
            putExtra("display_name",       displayName)
            putExtra("user_email",         userEmail)
            putExtra("mic_on",             micOn)
            putExtra("cam_on",             camOn)
            putExtra("is_host",            false)
            putExtra("transcript_enabled", false)
        })
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

// ════════════════════════════════════════════════════════════════════════════
//  SCREEN
// ════════════════════════════════════════════════════════════════════════════
@Composable
fun JoinMeetingScreen(
    prefillName:  String,
    isLoading:    Boolean,
    micOn:        Boolean,
    camOn:        Boolean,
    onMicToggle:  (Boolean) -> Unit,
    onCamToggle:  (Boolean) -> Unit,
    onBack:       () -> Unit,
    onJoin:       (code: String, name: String) -> Unit
) {
    var code    by remember { mutableStateOf("") }
    var name    by remember { mutableStateOf(prefillName) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); visible = true }

    Box(Modifier.fillMaxSize().background(BgGradient2)) {
        // Ambient glows
        Box(Modifier.size(280.dp).offset(x = (-60).dp, y = 80.dp)
            .background(Brush.radialGradient(listOf(Color(0x1500E5A0), Color.Transparent), radius = 450f), CircleShape))
        Box(Modifier.size(220.dp).offset(x = 220.dp, y = 360.dp)
            .background(Brush.radialGradient(listOf(Color(0x1200D4FF), Color.Transparent), radius = 350f), CircleShape))

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Background bleeds behind notch; content starts below it
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(16.dp))

            // Header
            AnimatedVisibility(visible, enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { -20 }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    JoinBackButton(onBack)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Join Meeting", fontSize = 20.sp, fontWeight = FontWeight.Black,
                            color = TextPri2, letterSpacing = (-0.4).sp)
                        Text("Enter a room to get started", fontSize = 11.sp, color = TextSub2)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // Input card
            AnimatedVisibility(visible, enter = fadeIn(tween(500, 100)) + slideInVertically(tween(500, 100)) { 40 }) {
                InputCard(code = code, name = name, onCodeChange = { code = it }, onNameChange = { name = it })
            }

            Spacer(Modifier.height(20.dp))

            // Toggles
            AnimatedVisibility(visible, enter = fadeIn(tween(500, 220)) + slideInVertically(tween(500, 220)) { 40 }) {
                JoinTogglesCard(micOn = micOn, camOn = camOn, onMicToggle = onMicToggle, onCamToggle = onCamToggle)
            }

            Spacer(Modifier.height(28.dp))

            // Join button
            AnimatedVisibility(visible, enter = fadeIn(tween(500, 320)) + slideInVertically(tween(500, 320)) { 40 }) {
                JoinButton(isLoading = isLoading, enabled = code.isNotBlank() && name.isNotBlank()) {
                    onJoin(code.trim(), name.trim())
                }
            }

            Spacer(Modifier.height(100.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  INPUT CARD
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun InputCard(
    code:         String,
    name:         String,
    onCodeChange: (String) -> Unit,
    onNameChange: (String) -> Unit
) {
    Column(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(InkMid2)
            .border(1.dp, CardBorder2, RoundedCornerShape(22.dp))
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(GreenGradient2))

        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PremiumInputField(
                label       = "Meeting Code",
                hint        = "e.g. NXM-1234-AB",
                value       = code,
                tint        = Green2,
                capitalize  = KeyboardCapitalization.Characters,
                onValueChange = onCodeChange
            )
            JoinFieldDivider()
            PremiumInputField(
                label       = "Your Name",
                hint        = "How you'll appear in the meeting",
                value       = name,
                tint        = Cyan2,
                capitalize  = KeyboardCapitalization.Words,
                onValueChange = onNameChange
            )
        }
    }
}

@Composable
private fun PremiumInputField(
    label:         String,
    hint:          String,
    value:         String,
    tint:          Color,
    capitalize:    KeyboardCapitalization,
    onValueChange: (String) -> Unit
) {
    val isFocused = value.isNotEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(6.dp)
                .background(if (isFocused) tint.copy(0.4f) else TextMuted2.copy(0.4f), CircleShape)
                .border(1.dp, if (isFocused) tint.copy(0.8f) else TextMuted2, CircleShape))
            Text(label, fontSize = 10.sp, color = TextMuted2, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
        }

        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(InkLight2)
                .border(1.dp, if (isFocused) tint.copy(0.4f) else CardBorder2, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 13.dp)
        ) {
            if (value.isEmpty()) {
                Text(hint, fontSize = 13.sp, color = TextMuted2)
            }
            BasicTextField(
                value         = value,
                onValueChange = onValueChange,
                textStyle     = TextStyle(fontSize = 14.sp, color = TextPri2, fontWeight = FontWeight.SemiBold),
                singleLine    = true,
                cursorBrush   = SolidColor(tint),
                keyboardOptions = KeyboardOptions(capitalization = capitalize),
                modifier      = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun JoinFieldDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(CardBorder2))
}

// ════════════════════════════════════════════════════════════════════════════
//  TOGGLES CARD (mic + camera only — no transcript for members)
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun JoinTogglesCard(
    micOn:       Boolean,
    camOn:       Boolean,
    onMicToggle: (Boolean) -> Unit,
    onCamToggle: (Boolean) -> Unit
) {
    Column(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(InkMid2)
            .border(1.dp, CardBorder2, RoundedCornerShape(22.dp))
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(CyanGradient2))

        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text("Before you join", fontSize = 10.sp, color = TextMuted2,
                fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(14.dp))
            JoinToggleRow("Microphone", "Join with audio on",  micOn, Cyan2,  onMicToggle)
            JoinFieldDivider()
            JoinToggleRow("Camera",     "Join with video on",  camOn, Green2, onCamToggle)
        }
    }
}

@Composable
private fun JoinToggleRow(
    title:    String,
    subtitle: String,
    checked:  Boolean,
    tint:     Color,
    onToggle: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(8.dp)
                .background(if (checked) tint.copy(0.3f) else TextMuted2.copy(0.3f), CircleShape)
                .border(1.dp, if (checked) tint.copy(0.7f) else TextMuted2, CircleShape))
            Column {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPri2)
                Text(subtitle, fontSize = 10.sp, color = TextSub2)
            }
        }
        JoinSwitch(checked = checked, tint = tint, onToggle = onToggle)
    }
}

@Composable
private fun JoinSwitch(checked: Boolean, tint: Color, onToggle: (Boolean) -> Unit) {
    val thumbOffset by animateDpAsState(if (checked) 18.dp else 2.dp,
        spring(Spring.DampingRatioMediumBouncy), label = "jt")
    val trackBg    by animateColorAsState(if (checked) tint.copy(0.25f) else InkLight2, tween(200), label = "jtb")
    val borderCol  by animateColorAsState(if (checked) tint.copy(0.6f) else TextMuted2.copy(0.3f), tween(200), label = "jbc")

    Box(Modifier.width(42.dp).height(24.dp).clip(RoundedCornerShape(12.dp))
        .background(trackBg).border(1.dp, borderCol, RoundedCornerShape(12.dp))
        .clickable { onToggle(!checked) }) {
        Box(Modifier.offset(x = thumbOffset, y = 2.dp).size(20.dp)
            .background(if (checked) tint else TextMuted2, CircleShape))
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  JOIN BUTTON
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun JoinButton(isLoading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f,
        spring(Spring.DampingRatioMediumBouncy), label = "jbs")

    val glowAlpha by rememberInfiniteTransition(label = "jg").animateFloat(
        0.25f, 0.65f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "jga"
    )
    val activeGradient = GreenGradient2
    val dimGradient    = Brush.linearGradient(listOf(InkLight2, InkLight2))

    Box(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                if (enabled && !isLoading) drawRoundRect(
                    color        = Green2.copy(glowAlpha * 0.2f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
                    size         = size.copy(width = size.width + 8.dp.toPx(), height = size.height + 8.dp.toPx()),
                    topLeft      = androidx.compose.ui.geometry.Offset(-4.dp.toPx(), -4.dp.toPx())
                )
            }
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled && !isLoading) activeGradient else dimGradient)
            .clickable(interactionSource, null, enabled = enabled && !isLoading) { onClick() }
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                JoinLoadingDots()
                Text("Joining...", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSub2)
            }
        } else {
            Text(
                "Join Meeting",
                fontSize   = 15.sp,
                fontWeight = FontWeight.Black,
                color      = if (enabled) Color.Black else TextMuted2,
                letterSpacing = 0.3.sp
            )
        }
    }
}

@Composable
private fun JoinLoadingDots() {
    val inf = rememberInfiniteTransition(label = "jld")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        (0..2).forEach { i ->
            val alpha by inf.animateFloat(0.2f, 1f,
                infiniteRepeatable(tween(600, delayMillis = i * 150), RepeatMode.Reverse), label = "jd$i")
            Box(Modifier.size(5.dp).graphicsLayer { this.alpha = alpha }
                .background(Green2, CircleShape))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  BACK BUTTON
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun JoinBackButton(onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
            .background(InkLight2).border(1.dp, CardBorder2, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(16.dp)) {
            val p = androidx.compose.ui.graphics.Paint().apply {
                color = TextSub2; style = PaintingStyle.Stroke
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