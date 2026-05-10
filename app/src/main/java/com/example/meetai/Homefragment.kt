package com.example.meetai

// ════════════════════════════════════════════════════════════════════════════
//  Homefragment.kt  —  MeetAI  •  Premium Redesign
//
//  Layout:
//    1. Full-screen (including camera cutout area)
//    2. Logo + App name centered at top
//    3. Two large action cards: New Meeting / Join Meeting
//    4. Animated feature highlights with blinking dot bullets + connector lines
//
//  All navigation targets unchanged.
// ════════════════════════════════════════════════════════════════════════════

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import kotlinx.coroutines.delay

// ════════════════════════════════════════════════════════════════════════════
//  PALETTE  — deep navy / midnight ink with electric cyan accent
// ════════════════════════════════════════════════════════════════════════════
private val Ink          = Color(0xFF080C14)   // deepest background
private val InkMid       = Color(0xFF0E1422)   // card bg
private val InkLight     = Color(0xFF161D2F)   // lighter surface
private val Cyan         = Color(0xFF00D4FF)   // primary accent
private val CyanDim      = Color(0xFF0099BF)   // muted accent
private val CyanGlow     = Color(0x3300D4FF)   // glow
private val Gold         = Color(0xFFFFBF3C)   // secondary accent (logo ring)
private val TextPrimary  = Color(0xFFF0F6FF)
private val TextSub      = Color(0xFF6B7FA3)
private val TextMuted    = Color(0xFF3A4665)
private val CardBorder   = Color(0xFF1E293B)
private val GreenAccent  = Color(0xFF00E5A0)

private val CyanGradient  = Brush.linearGradient(listOf(Cyan, Color(0xFF0066FF)))
private val GoldGradient  = Brush.linearGradient(listOf(Gold, Color(0xFFFF9500)))
private val GreenGradient = Brush.linearGradient(listOf(GreenAccent, Color(0xFF0099FF)))
private val BgGradient    = Brush.verticalGradient(listOf(Color(0xFF060A12), Ink, Color(0xFF0A1020)))

// ════════════════════════════════════════════════════════════════════════════
//  FEATURE POINTS — shown in animated bullets section
// ════════════════════════════════════════════════════════════════════════════
private data class FeaturePoint(val headline: String, val detail: String)

private val featurePoints = listOf(
    FeaturePoint("Admin-Only Summaries",    "Meeting summaries are delivered exclusively to the admin after every session"),
    FeaturePoint("Audio to Text",           "Recording ends, our engine transcribes the full meeting audio automatically"),
    FeaturePoint("AI-Powered Analysis",     "Transcript is processed by our AI model for key insights and action items"),
    FeaturePoint("Summary in Minutes",      "Receive a clean, structured summary within minutes of the meeting ending"),
    FeaturePoint("Zero Manual Effort",      "Everything happens automatically — no note-taking required from participants"),
)

// ════════════════════════════════════════════════════════════════════════════
//  FRAGMENT
// ════════════════════════════════════════════════════════════════════════════
class Homefragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Allow content to draw behind status bar (camera notch area)
        activity?.window?.let { win ->
            WindowCompat.setDecorFitsSystemWindows(win, false)
        }

        return ComposeView(requireContext()).apply {
            setContent {
                val prefs    = requireContext().getSharedPreferences("nexmeet_prefs", 0)
                val userName = prefs.getString("user_name", "User") ?: "User"

                HomeScreen(
                    userName      = userName,
                    onNewMeeting  = { startActivity(Intent(requireContext(), Newmeetingactivity::class.java)) },
                    onJoinMeeting = { startActivity(Intent(requireContext(), Joinmeetingactivity::class.java)) },
                    onProfile     = {
                        (activity as? MainActivity)?.supportFragmentManager?.beginTransaction()
                            ?.replace(R.id.fragmentContainer, Profilefragment())?.commit()
                    }
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  ROOT SCREEN
// ════════════════════════════════════════════════════════════════════════════
@Composable
fun HomeScreen(
    userName:      String,
    onNewMeeting:  () -> Unit,
    onJoinMeeting: () -> Unit,
    onProfile:     () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(80); visible = true }

    Box(
        Modifier
            .fillMaxSize()
            .background(BgGradient)
    ) {
        // Ambient glow orbs
        Box(
            Modifier
                .size(300.dp)
                .offset(x = (-80).dp, y = 60.dp)
                .background(
                    Brush.radialGradient(listOf(CyanGlow, Color.Transparent), radius = 500f),
                    CircleShape
                )
        )
        Box(
            Modifier
                .size(260.dp)
                .offset(x = 200.dp, y = 400.dp)
                .background(
                    Brush.radialGradient(listOf(Color(0x220066FF), Color.Transparent), radius = 400f),
                    CircleShape
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // Draw into the camera/status bar area
                .windowInsetsPadding(WindowInsets(0)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Reserve space for camera notch / status bar
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(16.dp))

            // ── LOGO + APP NAME ───────────────────────────────────────────
            AnimatedVisibility(
                visible = visible,
                enter   = fadeIn(tween(600)) + slideInVertically(tween(600)) { -40 }
            ) {
                LogoBrand(userName = userName, onProfile = onProfile)
            }

            Spacer(Modifier.height(44.dp))

            // ── ACTION CARDS ──────────────────────────────────────────────
            AnimatedVisibility(
                visible = visible,
                enter   = fadeIn(tween(600, 150)) + slideInVertically(tween(600, 150)) { 60 }
            ) {
                Column(
                    Modifier.padding(horizontal = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ActionCard(
                        title       = "New Meeting",
                        subtitle    = "Create a new meeting room",
                        description = "Start instantly and invite your team. Get AI-powered summaries delivered to you after every session.",
                        gradient    = CyanGradient,
                        glowColor   = Cyan,
                        iconSymbol  = "camera",
                        onClick     = onNewMeeting
                    )
                    ActionCard(
                        title       = "Join Meeting",
                        subtitle    = "Join an existing meeting",
                        description = "Enter a room code or tap a shared link to join your team in seconds from anywhere.",
                        gradient    = GreenGradient,
                        glowColor   = GreenAccent,
                        iconSymbol  = "join",
                        onClick     = onJoinMeeting
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            // ── ANIMATED FEATURE BULLETS ──────────────────────────────────
            AnimatedVisibility(
                visible = visible,
                enter   = fadeIn(tween(600, 400))
            ) {
                FeatureBullets()
            }

            Spacer(Modifier.height(40.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  LOGO + BRAND
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun LogoBrand(userName: String, onProfile: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Logo mark + name
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Logo circle with M
            Box(
                Modifier
                    .size(48.dp)
                    .background(Ink, CircleShape)
                    .border(
                        width = 2.dp,
                        brush = Brush.sweepGradient(listOf(Cyan, Gold, Cyan)),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Inner fill
                Box(
                    Modifier
                        .size(40.dp)
                        .background(
                            Brush.radialGradient(listOf(Color(0xFF0E1C32), InkMid)),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "M",
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.Black,
                        color      = Cyan,
                        letterSpacing = (-1).sp
                    )
                }
            }

            Column {
                Text(
                    "MeetAI",
                    fontSize      = 22.sp,
                    fontWeight    = FontWeight.Black,
                    color         = TextPrimary,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    "AI-powered meetings",
                    fontSize = 10.sp,
                    color    = TextSub,
                    letterSpacing = 0.3.sp
                )
            }
        }

        // Profile avatar
        Box(
            Modifier
                .size(42.dp)
                .background(InkLight, CircleShape)
                .border(1.dp, CardBorder, CircleShape)
                .clickable { onProfile() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                userName.firstOrNull()?.uppercase() ?: "U",
                fontSize   = 15.sp,
                fontWeight = FontWeight.Bold,
                color      = Cyan
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  ACTION CARD
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun ActionCard(
    title:       String,
    subtitle:    String,
    description: String,
    gradient:    Brush,
    glowColor:   Color,
    iconSymbol:  String,
    onClick:     () -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label = "cardScale"
    )

    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            // outer glow ring
            .drawBehind {
                drawRoundRect(
                    color        = glowColor.copy(alpha = 0.18f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(28.dp.toPx()),
                    size         = size.copy(width = size.width + 4.dp.toPx(), height = size.height + 4.dp.toPx()),
                    topLeft      = Offset(-2.dp.toPx(), -2.dp.toPx())
                )
            }
            .clip(RoundedCornerShape(24.dp))
            .background(InkMid)
            .border(1.dp, glowColor.copy(0.22f), RoundedCornerShape(24.dp))
            .clickable(interactionSource, null) { onClick() }
    ) {
        // Gradient accent bar at top
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(gradient)
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon box
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(glowColor.copy(0.2f), glowColor.copy(0.05f))
                        )
                    )
                    .border(1.dp, glowColor.copy(0.3f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Draw icon via canvas lines for zero dependency
                if (iconSymbol == "camera") {
                    CameraIcon(tint = glowColor)
                } else {
                    JoinIcon(tint = glowColor)
                }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    fontSize      = 20.sp,
                    fontWeight    = FontWeight.ExtraBold,
                    color         = TextPrimary,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    color    = glowColor.copy(0.8f),
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.2.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    description,
                    fontSize   = 12.sp,
                    color      = TextSub,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(12.dp))

                // CTA pill
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(gradient)
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                ) {
                    Text(
                        if (iconSymbol == "camera") "Start Now" else "Enter Room",
                        fontSize      = 11.sp,
                        fontWeight    = FontWeight.Bold,
                        color         = Color.Black,
                        letterSpacing = 0.3.sp
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  INLINE VECTOR ICONS  (no drawable dependency)
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun CameraIcon(tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(28.dp)) {
        val w = size.width; val h = size.height
        val p = androidx.compose.ui.graphics.Paint().apply {
            color         = tint
            style         = PaintingStyle.Stroke
            strokeWidth   = 2.2f
            strokeCap     = StrokeCap.Round
            strokeJoin    = StrokeJoin.Round
        }
        drawContext.canvas.apply {
            // Camera body
            drawRoundRect(2f, 7f, w - 2f, h - 5f, 5f, 5f, p)
            // Lens
            val cx = w / 2; val cy = h / 2 + 1f
            drawCircle(Offset(cx, cy), 5.5f, p)
            // Viewfinder bump
            val bp = androidx.compose.ui.graphics.Paint().apply {
                color       = tint
                style       = PaintingStyle.Fill
            }
            drawRoundRect(w * 0.35f, 4f, w * 0.65f, 8f, 3f, 3f, bp)
        }
    }
}

@Composable
private fun JoinIcon(tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(28.dp)) {
        val w = size.width; val h = size.height
        val p = androidx.compose.ui.graphics.Paint().apply {
            color       = tint
            style       = PaintingStyle.Stroke
            strokeWidth = 2.2f
            strokeCap   = StrokeCap.Round
            strokeJoin  = StrokeJoin.Round
        }
        drawContext.canvas.apply {
            // Arrow pointing right into a box
            val mid = h / 2
            drawLine(Offset(4f, mid), Offset(w - 8f, mid), p)
            drawLine(Offset(w - 14f, mid - 6f), Offset(w - 8f, mid), p)
            drawLine(Offset(w - 14f, mid + 6f), Offset(w - 8f, mid), p)
            // Vertical line on left (door frame)
            drawLine(Offset(4f, 5f), Offset(4f, h - 5f), p)
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  ANIMATED FEATURE BULLETS
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun FeatureBullets() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
    ) {
        // Section header
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .background(CyanGradient, RoundedCornerShape(2.dp))
            )
            Text(
                "How it works",
                fontSize      = 14.sp,
                fontWeight    = FontWeight.Bold,
                color         = TextPrimary,
                letterSpacing = 0.2.sp
            )
        }

        Spacer(Modifier.height(20.dp))

        featurePoints.forEachIndexed { index, feature ->
            BulletRow(
                feature    = feature,
                index      = index,
                isLast     = index == featurePoints.lastIndex
            )
        }
    }
}

@Composable
private fun BulletRow(
    feature: FeaturePoint,
    index:   Int,
    isLast:  Boolean
) {
    // Staggered entrance
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(600L + index * 180L)
        entered = true
    }

    // Blinking animation for the dot
    val infiniteTransition = rememberInfiniteTransition(label = "blink$index")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue   = 0.3f,
        targetValue    = 1f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(900 + index * 120, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha$index"
    )
    val dotScale by infiniteTransition.animateFloat(
        initialValue   = 0.7f,
        targetValue    = 1.3f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(1100 + index * 100, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotScale$index"
    )

    val lineProgress by animateFloatAsState(
        targetValue   = if (entered) 1f else 0f,
        animationSpec = tween(500, delayMillis = 100),
        label         = "line$index"
    )

    AnimatedVisibility(
        visible = entered,
        enter   = fadeIn(tween(400)) + slideInHorizontally(tween(400)) { -20 }
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Left: dot + vertical connector line
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier            = Modifier.width(32.dp)
            ) {
                // Blinking dot
                Box(
                    Modifier
                        .size(12.dp)
                        .graphicsLayer { scaleX = dotScale; scaleY = dotScale; alpha = dotAlpha }
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(listOf(Cyan, CyanDim))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .background(Color.White.copy(0.9f), CircleShape)
                    )
                }

                // Connector line (animated draw)
                if (!isLast) {
                    Box(
                        Modifier
                            .width(1.5.dp)
                            .height((54 * lineProgress).dp)
                            .background(
                                Brush.verticalGradient(listOf(Cyan.copy(0.5f), TextMuted.copy(0.3f)))
                            )
                    )
                }
            }

            // Right: text content
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
                    .then(if (!isLast) Modifier.padding(bottom = 20.dp) else Modifier)
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        feature.headline,
                        fontSize      = 13.sp,
                        fontWeight    = FontWeight.Bold,
                        color         = TextPrimary,
                        letterSpacing = (-0.1).sp
                    )
                    // Step number badge
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(InkLight)
                            .border(1.dp, CardBorder, RoundedCornerShape(50.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            "${index + 1}",
                            fontSize = 9.sp,
                            color    = TextSub,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    feature.detail,
                    fontSize   = 11.sp,
                    color      = TextSub,
                    lineHeight = 16.sp
                )
            }
        }
    }

    // Reserve space even before entrance so layout doesn't jump
    if (!entered) {
        Spacer(Modifier.height(if (!isLast) 66.dp else 46.dp))
    }
}