package com.example.meetai

// ════════════════════════════════════════════════════════════════════════════
//  MainActivity.kt  —  MeetAI
//
//  Replaces Material BottomNavigationView with a Compose floating pill nav.
//  The pill floats above all fragment content via an overlay ComposeView,
//  so no fragment needs to know about navigation chrome.
//
//  Visual:  frosted glass dark pill · cyan indicator dot · spring animations
//           · icon scale on select · soft glow under active item
// ════════════════════════════════════════════════════════════════════════════

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment

// ── Palette (matches Homefragment) ──────────────────────────────────────────
private val NavBg      = Color(0xCC0E1422)   // semi-transparent dark
private val NavBorder  = Color(0x2200D4FF)   // subtle cyan border
private val NavCyan    = Color(0xFF00D4FF)
private val NavGreen   = Color(0xFF00E5A0)
private val NavInk     = Color(0xFF060A12)
private val NavText    = Color(0xFFF0F6FF)
private val NavMuted   = Color(0xFF3A4665)
private val NavSub     = Color(0xFF6B7FA3)

private sealed class NavItem(val id: Int, val label: String) {
    object Home    : NavItem(0, "Home")
    object History : NavItem(1, "History")
    object Profile : NavItem(2, "Profile")
}

private val navItems = listOf(NavItem.Home, NavItem.History, NavItem.Profile)

// ════════════════════════════════════════════════════════════════════════════
//  ACTIVITY
// ════════════════════════════════════════════════════════════════════════════
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw fully edge-to-edge — background paints behind notch and nav bar
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContentView(R.layout.activity_main)

        // Seed first fragment
        loadFragment(Homefragment())

        // Inflate the floating nav overlay on top of everything
        val overlay = findViewById<FrameLayout>(R.id.navOverlay)
        val composeNav = ComposeView(this).apply {
            setContent {
                var selected by remember { mutableStateOf(0) }

                FloatingPillNav(
                    selected = selected,
                    onSelect = { id ->
                        if (id != selected) {
                            selected = id
                            when (id) {
                                0 -> loadFragment(Homefragment())
                                1 -> loadFragment(Historyfragment())
                                2 -> loadFragment(Profilefragment())
                            }
                        }
                    }
                )
            }
        }
        overlay.addView(composeNav)
    }

    fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  FLOATING PILL NAV
// ════════════════════════════════════════════════════════════════════════════
@Composable
fun FloatingPillNav(
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Soft drop shadow glow beneath the pill
        Box(
            Modifier
                .padding(bottom = 20.dp)
                .width(220.dp)
                .height(24.dp)
                .background(
                    Brush.radialGradient(
                        listOf(NavCyan.copy(0.18f), Color.Transparent),
                        radius = 300f
                    ),
                    CircleShape
                )
                .blur(16.dp)
        )

        // The pill itself
        Row(
            Modifier
                .padding(bottom = 24.dp)
                .width(220.dp)
                .height(60.dp)
                // Frosted glass: semi-transparent dark bg + border
                .clip(RoundedCornerShape(50.dp))
                .background(NavBg)
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            NavCyan.copy(0.35f),
                            NavBorder,
                            NavGreen.copy(0.2f)
                        )
                    ),
                    shape = RoundedCornerShape(50.dp)
                )
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            navItems.forEach { item ->
                NavPillItem(
                    item     = item,
                    isActive = selected == item.id,
                    onClick  = { onSelect(item.id) }
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  SINGLE NAV ITEM
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun NavPillItem(
    item:     NavItem,
    isActive: Boolean,
    onClick:  () -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    // Icon scale spring
    val iconScale by animateFloatAsState(
        targetValue   = when {
            pressed  -> 0.85f
            isActive -> 1.18f
            else     -> 1f
        },
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label         = "iconScale${item.id}"
    )

    // Active glow size
    val glowRadius by animateFloatAsState(
        targetValue   = if (isActive) 28f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label         = "glow${item.id}"
    )

    // Label alpha
    val labelAlpha by animateFloatAsState(
        targetValue   = if (isActive) 1f else 0f,
        animationSpec = tween(250),
        label         = "label${item.id}"
    )

    Box(
        Modifier
            .size(60.dp)
            .clickable(interactionSource, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // Glow blob behind active icon
        if (glowRadius > 0f) {
            Box(
                Modifier
                    .size((glowRadius * 1.4f).dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                NavCyan.copy(0.22f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Icon (canvas-drawn, zero drawable dependency)
            Box(
                Modifier
                    .graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                    .size(22.dp),
                contentAlignment = Alignment.Center
            ) {
                NavIcon(item = item, active = isActive)
            }

            // Dot indicator + label row
            if (isActive) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier              = Modifier.graphicsLayer { alpha = labelAlpha }
                ) {
                    Box(
                        Modifier
                            .size(3.dp)
                            .background(NavCyan, CircleShape)
                    )
                    Text(
                        item.label,
                        fontSize      = 8.sp,
                        fontWeight    = FontWeight.Bold,
                        color         = NavCyan,
                        letterSpacing = 0.3.sp
                    )
                    Box(
                        Modifier
                            .size(3.dp)
                            .background(NavCyan, CircleShape)
                    )
                }
            } else {
                // Placeholder height so inactive items don't shift layout
                Spacer(Modifier.height(9.dp))
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  CANVAS ICONS  (no drawable files needed)
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun NavIcon(item: NavItem, active: Boolean) {
    val color = if (active) NavCyan else NavMuted
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val stroke = androidx.compose.ui.graphics.Paint().apply {
            this.color    = color
            style         = PaintingStyle.Stroke
            strokeWidth   = if (active) 2f else 1.6f
            strokeCap     = StrokeCap.Round
            strokeJoin    = StrokeJoin.Round
        }
        val fill = androidx.compose.ui.graphics.Paint().apply {
            this.color = color.copy(alpha = if (active) 0.18f else 0f)
            style      = PaintingStyle.Fill
        }

        drawContext.canvas.apply {
            when (item) {
                NavItem.Home -> {
                    // House shape
                    val path = Path().apply {
                        moveTo(w / 2, 1f)
                        lineTo(w - 1f, h * 0.48f)
                        lineTo(w - 1f, h - 1f)
                        lineTo(w * 0.62f, h - 1f)
                        lineTo(w * 0.62f, h * 0.65f)
                        lineTo(w * 0.38f, h * 0.65f)
                        lineTo(w * 0.38f, h - 1f)
                        lineTo(1f, h - 1f)
                        lineTo(1f, h * 0.48f)
                        close()
                    }
                    drawPath(path, fill)
                    drawPath(path, stroke)
                }
                NavItem.History -> {
                    // Clock circle + hands
                    drawCircle(Offset(w / 2, h / 2), w / 2 - 1.5f, fill)
                    drawCircle(Offset(w / 2, h / 2), w / 2 - 1.5f, stroke)
                    // Hour hand
                    drawLine(Offset(w / 2, h / 2), Offset(w / 2, h * 0.25f), stroke)
                    // Minute hand
                    drawLine(Offset(w / 2, h / 2), Offset(w * 0.72f, h * 0.52f), stroke)
                    // Center dot
                    val dotP = androidx.compose.ui.graphics.Paint().apply {
                        this.color = color; style = PaintingStyle.Fill
                    }
                    drawCircle(Offset(w / 2, h / 2), 1.5f, dotP)
                }
                NavItem.Profile -> {
                    // Head circle
                    drawCircle(Offset(w / 2, h * 0.33f), w * 0.26f, fill)
                    drawCircle(Offset(w / 2, h * 0.33f), w * 0.26f, stroke)
                    // Shoulders arc
                    val shoulderPath = Path().apply {
                        moveTo(0f, h + 2f)
                        cubicTo(0f, h * 0.58f, w * 0.22f, h * 0.62f, w / 2, h * 0.62f)
                        cubicTo(w * 0.78f, h * 0.62f, w, h * 0.58f, w, h + 2f)
                        close()
                    }
                    drawPath(shoulderPath, fill)
                    drawPath(shoulderPath, stroke)
                }
            }
        }
    }
}