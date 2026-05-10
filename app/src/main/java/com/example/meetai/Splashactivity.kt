package com.example.meetai

// ════════════════════════════════════════════════════════════════════════════
//  Splashactivity.kt  —  MeetAI  •  Compose rewrite
//
//  Logic unchanged: navigate to LoginActivity after animation completes.
//  Design: full-screen edge-to-edge dark ink, animated logo ring sweep,
//  particle orbs, staggered text reveal, shimmer loading bar.
// ════════════════════════════════════════════════════════════════════════════

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay

// ── Palette ──────────────────────────────────────────────────────────────────
private val Ink         = Color(0xFF060A12)
private val InkMid      = Color(0xFF0E1422)
private val InkLight    = Color(0xFF161D2F)
private val Cyan        = Color(0xFF00D4FF)
private val CyanDim     = Color(0xFF005F72)
private val Gold        = Color(0xFFFFBF3C)
private val TextPri     = Color(0xFFF0F6FF)
private val TextSub     = Color(0xFF6B7FA3)

private val BgGradient   = Brush.verticalGradient(listOf(Color(0xFF060A12), Color(0xFF080C18), Color(0xFF060A12)))
private val CyanGradient = Brush.linearGradient(listOf(Cyan, Color(0xFF0066FF)))

// ════════════════════════════════════════════════════════════════════════════
//  ACTIVITY
// ════════════════════════════════════════════════════════════════════════════
class Splashactivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor     = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars    = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            SplashScreen(
                onComplete = {
                    startActivity(Intent(this, LoginActivity::class.java))
                    @Suppress("DEPRECATION")
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                }
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  SPLASH SCREEN
// ════════════════════════════════════════════════════════════════════════════
@Composable
fun SplashScreen(onComplete: () -> Unit) {

    // ── Animation state ───────────────────────────────────────────────
    var logoVisible    by remember { mutableStateOf(false) }
    var textVisible    by remember { mutableStateOf(false) }
    var taglineVisible by remember { mutableStateOf(false) }
    var barVisible     by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(200);  logoVisible    = true
        delay(600);  textVisible    = true
        delay(200);  taglineVisible = true
        delay(300);  barVisible     = true
        delay(1600); onComplete()
    }

    // ── Infinite animations ───────────────────────────────────────────
    val inf = rememberInfiniteTransition(label = "inf")

    // Rotating sweep for logo ring
    val sweepAngle by inf.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "sweep"
    )

    // Pulsing outer glow
    val glowScale by inf.animateFloat(
        0.88f, 1.12f,
        infiniteRepeatable(tween(2000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "glow"
    )
    val glowAlpha by inf.animateFloat(
        0.3f, 0.7f,
        infiniteRepeatable(tween(2000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "ga"
    )

    // Shimmer for loading bar
    val shimmerOffset by inf.animateFloat(
        -1f, 2f,
        infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing)),
        label = "shimmer"
    )

    // Staggered logo scale in
    val logoScale by animateFloatAsState(
        targetValue   = if (logoVisible) 1f else 0.55f,
        animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow),
        label         = "logoScale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue   = if (logoVisible) 1f else 0f,
        animationSpec = tween(700),
        label         = "logoAlpha"
    )

    // Text slide + fade
    val textOffsetY by animateFloatAsState(
        targetValue   = if (textVisible) 0f else 32f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label         = "textY"
    )
    val textAlpha by animateFloatAsState(
        targetValue   = if (textVisible) 1f else 0f,
        animationSpec = tween(500),
        label         = "textA"
    )

    val tagAlpha by animateFloatAsState(
        targetValue   = if (taglineVisible) 1f else 0f,
        animationSpec = tween(500),
        label         = "tagA"
    )

    val barAlpha by animateFloatAsState(
        targetValue   = if (barVisible) 1f else 0f,
        animationSpec = tween(400),
        label         = "barA"
    )

    // ── UI ────────────────────────────────────────────────────────────
    Box(
        Modifier.fillMaxSize().background(BgGradient),
        contentAlignment = Alignment.Center
    ) {
        // Background particle orbs
        ParticleOrbs()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier            = Modifier.fillMaxSize()
        ) {

            // ── LOGO ─────────────────────────────────────────────────
            Box(
                Modifier
                    .graphicsLayer {
                        scaleX = logoScale; scaleY = logoScale; alpha = logoAlpha
                    },
                contentAlignment = Alignment.Center
            ) {
                // Outer pulsing glow
                Box(
                    Modifier
                        .size(160.dp)
                        .graphicsLayer { scaleX = glowScale; scaleY = glowScale; alpha = glowAlpha }
                        .background(
                            Brush.radialGradient(listOf(Cyan.copy(0.25f), Color.Transparent)),
                            CircleShape
                        )
                )

                // Rotating arc ring
                androidx.compose.foundation.Canvas(Modifier.size(120.dp)) {
                    drawArc(
                        brush      = Brush.sweepGradient(
                            listOf(Cyan, Gold, Cyan.copy(0f)),
                            center = center
                        ),
                        startAngle = sweepAngle,
                        sweepAngle = 240f,
                        useCenter  = false,
                        style      = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 3.dp.toPx(),
                            cap   = StrokeCap.Round
                        )
                    )
                }

                // Inner dark circle
                Box(
                    Modifier
                        .size(100.dp)
                        .background(
                            Brush.radialGradient(listOf(InkLight, InkMid)),
                            CircleShape
                        )
                        .border(1.dp, CyanDim.copy(0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // M lettermark
                    Text(
                        "M",
                        fontSize      = 40.sp,
                        fontWeight    = FontWeight.Black,
                        color         = Cyan,
                        letterSpacing = (-2).sp
                    )
                }
            }

            Spacer(Modifier.height(36.dp))

            // ── APP NAME ──────────────────────────────────────────────
            Box(
                Modifier.graphicsLayer {
                    translationY = textOffsetY; alpha = textAlpha
                }
            ) {
                Text(
                    "MeetAI",
                    fontSize      = 38.sp,
                    fontWeight    = FontWeight.Black,
                    color         = TextPri,
                    letterSpacing = (-1.5).sp,
                    textAlign     = TextAlign.Center
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── TAGLINE ───────────────────────────────────────────────
            Box(Modifier.graphicsLayer { alpha = tagAlpha }) {
                Text(
                    "AI-powered meetings",
                    fontSize      = 13.sp,
                    color         = TextSub,
                    letterSpacing = 1.2.sp,
                    fontWeight    = FontWeight.Medium,
                    textAlign     = TextAlign.Center
                )
            }

            Spacer(Modifier.height(60.dp))

            // ── SHIMMER LOADING BAR ───────────────────────────────────
            Box(
                Modifier
                    .width(140.dp)
                    .height(2.dp)
                    .graphicsLayer { alpha = barAlpha }
                    .clip(RoundedCornerShape(1.dp))
                    .background(InkLight)
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    Cyan.copy(0.9f),
                                    Gold.copy(0.7f),
                                    Color.Transparent
                                ),
                                startX = shimmerOffset * 300f,
                                endX   = shimmerOffset * 300f + 200f
                            )
                        )
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── LOADING DOTS ──────────────────────────────────────────
            Box(Modifier.graphicsLayer { alpha = barAlpha }) {
                LoadingDots()
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  LOADING DOTS  — staggered blink
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun LoadingDots() {
    val inf = rememberInfiniteTransition(label = "ld")
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        (0..2).forEach { i ->
            val alpha by inf.animateFloat(
                0.15f, 1f,
                infiniteRepeatable(
                    tween(600, delayMillis = i * 180, easing = EaseInOutSine),
                    RepeatMode.Reverse
                ),
                label = "dot$i"
            )
            val scale by inf.animateFloat(
                0.6f, 1f,
                infiniteRepeatable(
                    tween(600, delayMillis = i * 180, easing = EaseInOutSine),
                    RepeatMode.Reverse
                ),
                label = "ds$i"
            )
            Box(
                Modifier
                    .size(5.dp)
                    .graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale }
                    .background(Cyan, CircleShape)
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  BACKGROUND PARTICLE ORBS
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun ParticleOrbs() {
    val inf = rememberInfiniteTransition(label = "orbs")

    // Slowly drifting orb 1
    val orb1Y by inf.animateFloat(
        -20f, 20f,
        infiniteRepeatable(tween(4000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "o1y"
    )
    // Orb 2 opposite phase
    val orb2Y by inf.animateFloat(
        20f, -20f,
        infiniteRepeatable(tween(5000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "o2y"
    )
    val orb3X by inf.animateFloat(
        -15f, 15f,
        infiniteRepeatable(tween(6000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "o3x"
    )

    Box(Modifier.fillMaxSize()) {
        // Top-right cyan orb
        Box(
            Modifier
                .size(300.dp)
                .align(Alignment.TopEnd)
                .offset(x = 80.dp, y = ((-60) + orb1Y).dp)
                .blur(60.dp)
                .background(
                    Brush.radialGradient(listOf(Cyan.copy(0.12f), Color.Transparent)),
                    CircleShape
                )
        )
        // Bottom-left blue orb
        Box(
            Modifier
                .size(260.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-60).dp, y = (60 + orb2Y).dp)
                .blur(50.dp)
                .background(
                    Brush.radialGradient(listOf(Color(0x180066FF), Color.Transparent)),
                    CircleShape
                )
        )
        // Center-bottom gold accent
        Box(
            Modifier
                .size(180.dp)
                .align(Alignment.BottomCenter)
                .offset(x = orb3X.dp, y = 60.dp)
                .blur(40.dp)
                .background(
                    Brush.radialGradient(listOf(Gold.copy(0.08f), Color.Transparent)),
                    CircleShape
                )
        )
    }
}