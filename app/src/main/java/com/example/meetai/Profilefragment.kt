package com.example.meetai

// ════════════════════════════════════════════════════════════════════════════
//  Profilefragment.kt  —  MeetAI  •  Compose rewrite
//
//  All logic unchanged: UserPrefs.getUsername / getEmail / clear,
//  SharedPrefs display-name override, LoginActivity navigation.
//
//  Design: full-screen dark ink • avatar hero with sweep-gradient ring
//  glowing stat chips • edit-name sheet • sign-out confirmation
// ════════════════════════════════════════════════════════════════════════════

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import kotlinx.coroutines.delay

// ── Palette (shared with Homefragment) ──────────────────────────────────────
private val Ink         = Color(0xFF080C14)
private val InkMid      = Color(0xFF0E1422)
private val InkLight    = Color(0xFF161D2F)
private val InkLighter  = Color(0xFF1C2540)
private val Cyan        = Color(0xFF00D4FF)
private val CyanDim     = Color(0xFF0099BF)
private val Gold        = Color(0xFFFFBF3C)
private val RedAccent   = Color(0xFFFF4757)
private val RedDim      = Color(0xFF3D1520)
private val TextPri     = Color(0xFFF0F6FF)
private val TextSub     = Color(0xFF6B7FA3)
private val TextMuted   = Color(0xFF3A4665)
private val CardBorder  = Color(0xFF1E293B)

private val BgGradient   = Brush.verticalGradient(listOf(Color(0xFF060A12), Ink, Color(0xFF0A1020)))
private val CyanGradient = Brush.linearGradient(listOf(Cyan, Color(0xFF0066FF)))
private val GoldGradient = Brush.linearGradient(listOf(Gold, Color(0xFFFF9500)))
private val RedGradient  = Brush.linearGradient(listOf(RedAccent, Color(0xFFFF6B35)))

// ════════════════════════════════════════════════════════════════════════════
//  FRAGMENT
// ════════════════════════════════════════════════════════════════════════════
class Profilefragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        activity?.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }

        return ComposeView(requireContext()).apply {
            setContent {
                val context = LocalContext.current
                val prefs   = context.getSharedPreferences("meetai_prefs", 0)

                // Reactive state — updates instantly after edit
                var displayName by remember {
                    mutableStateOf(
                        prefs.getString("user_name", null)
                            ?: UserPrefs.getUsername(context)
                            ?: "User"
                    )
                }
                val email = remember { UserPrefs.getEmail(context) ?: "No email" }

                ProfileScreen(
                    displayName = displayName,
                    email       = email,
                    onSaveName  = { newName ->
                        prefs.edit().putString("user_name", newName).apply()
                        displayName = newName
                    },
                    onSignOut   = {
                        UserPrefs.clear(context)
                        Toast.makeText(context, "Signed out successfully", Toast.LENGTH_SHORT).show()
                        startActivity(
                            Intent(context, LoginActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                        )
                        requireActivity().finish()
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
fun ProfileScreen(
    displayName: String,
    email:       String,
    onSaveName:  (String) -> Unit,
    onSignOut:   () -> Unit
) {
    var visible         by remember { mutableStateOf(false) }
    var showEditDialog  by remember { mutableStateOf(false) }
    var showSignOutConf by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { delay(60); visible = true }

    // Ambient sweep animation for avatar ring
    val sweepAngle by rememberInfiniteTransition(label = "ring").animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "sweep"
    )

    Box(Modifier.fillMaxSize().background(BgGradient)) {

        // Ambient glow
        Box(
            Modifier.size(280.dp).offset(x = 100.dp, y = (-40).dp)
                .background(
                    Brush.radialGradient(listOf(Color(0x1A00D4FF), Color.Transparent), radius = 500f),
                    CircleShape
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(24.dp))

            // ── PAGE TITLE ────────────────────────────────────────────────
            AnimatedVisibility(visible, enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { -20 }) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Profile", fontSize = 26.sp, fontWeight = FontWeight.Black,
                            color = TextPri, letterSpacing = (-0.5).sp)
                        Text("Manage your account", fontSize = 11.sp, color = TextSub)
                    }
                    // Small cyan dot badge
                    Box(
                        Modifier.size(8.dp)
                            .background(Cyan, CircleShape)
                            .drawBehind {
                                drawCircle(Cyan.copy(0.3f), radius = size.minDimension)
                            }
                    )
                }
            }

            Spacer(Modifier.height(36.dp))

            // ── AVATAR HERO ───────────────────────────────────────────────
            AnimatedVisibility(visible, enter = fadeIn(tween(500, 100)) + scaleIn(tween(500, 100))) {
                AvatarHero(name = displayName, sweepAngle = sweepAngle)
            }

            Spacer(Modifier.height(14.dp))

            // Name + email under avatar
            AnimatedVisibility(visible, enter = fadeIn(tween(400, 200))) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(displayName, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        color = TextPri, letterSpacing = (-0.3).sp)
                    Spacer(Modifier.height(4.dp))
                    Text(email, fontSize = 12.sp, color = TextSub)
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── INFO CARD ─────────────────────────────────────────────────
            AnimatedVisibility(visible, enter = fadeIn(tween(400, 280)) + slideInVertically(tween(400, 280)) { 30 }) {
                InfoCard(
                    name  = displayName,
                    email = email,
                    onEditName = { showEditDialog = true }
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── SIGN OUT ──────────────────────────────────────────────────
            AnimatedVisibility(visible, enter = fadeIn(tween(400, 360)) + slideInVertically(tween(400, 360)) { 30 }) {
                SignOutButton(onClick = { showSignOutConf = true })
            }

            Spacer(Modifier.height(120.dp))
        }
    }

    // ── EDIT NAME DIALOG ──────────────────────────────────────────────────
    if (showEditDialog) {
        EditNameDialog(
            currentName = displayName,
            onDismiss   = { showEditDialog = false },
            onSave      = { newName ->
                onSaveName(newName)
                showEditDialog = false
            }
        )
    }

    // ── SIGN OUT CONFIRMATION ─────────────────────────────────────────────
    if (showSignOutConf) {
        ConfirmDialog(
            title     = "Sign Out",
            message   = "Are you sure you want to sign out?",
            confirmLabel = "Sign Out",
            confirmBrush = RedGradient,
            onDismiss = { showSignOutConf = false },
            onConfirm = {
                showSignOutConf = false
                onSignOut()
            }
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  AVATAR HERO
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun AvatarHero(name: String, sweepAngle: Float) {
    Box(Modifier.size(108.dp), contentAlignment = Alignment.Center) {
        // Animated sweep gradient ring
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            val strokePx = 2.5f
            drawArc(
                brush      = Brush.sweepGradient(
                    listOf(Cyan, Gold, Cyan.copy(0f)),
                    center = Offset(size.width / 2, size.height / 2)
                ),
                startAngle = sweepAngle,
                sweepAngle = 270f,
                useCenter  = false,
                style      = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokePx,
                    cap   = StrokeCap.Round
                )
            )
        }

        // Inner circle
        Box(
            Modifier
                .size(92.dp)
                .background(
                    Brush.radialGradient(listOf(Color(0xFF0E1C32), InkMid)),
                    CircleShape
                )
                .border(1.dp, CardBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                name.firstOrNull()?.uppercase() ?: "U",
                fontSize      = 34.sp,
                fontWeight    = FontWeight.Black,
                color         = Cyan,
                letterSpacing = (-1).sp
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  INFO CARD
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun InfoCard(name: String, email: String, onEditName: () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 22.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(InkMid)
            .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
    ) {
        // Accent bar
        Box(Modifier.fillMaxWidth().height(2.dp).background(CyanGradient))

        ProfileRow(
            label    = "Display Name",
            value    = name,
            tint     = Cyan,
            isAction = true,
            onClick  = onEditName
        )

        Divider()

        ProfileRow(
            label    = "Email",
            value    = email,
            tint     = Gold,
            isAction = false
        )
    }
}

@Composable
private fun ProfileRow(
    label:    String,
    value:    String,
    tint:     Color,
    isAction: Boolean,
    onClick:  () -> Unit = {}
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val bg by animateColorAsState(
        if (pressed && isAction) InkLighter else Color.Transparent,
        tween(150), label = "rowBg"
    )

    Row(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .then(
                if (isAction) Modifier.clickable(interactionSource, null) { onClick() }
                else Modifier
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Tinted dot
            Box(
                Modifier.size(8.dp)
                    .background(tint.copy(0.25f), CircleShape)
                    .border(1.dp, tint.copy(0.6f), CircleShape)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp)
                Text(value, fontSize = 14.sp, color = TextPri, fontWeight = FontWeight.SemiBold)
            }
        }

        if (isAction) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(tint.copy(0.1f))
                    .border(1.dp, tint.copy(0.25f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("Edit", fontSize = 10.sp, color = tint, fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp)
            }
        }
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier.fillMaxWidth().height(1.dp)
            .padding(horizontal = 18.dp)
            .background(CardBorder)
    )
}

// ════════════════════════════════════════════════════════════════════════════
//  SIGN OUT BUTTON
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun SignOutButton(onClick: () -> Unit) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f,
        spring(Spring.DampingRatioMediumBouncy), label = "soScale")

    Box(
        Modifier
            .padding(horizontal = 22.dp)
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(RedDim)
            .border(1.dp, RedAccent.copy(0.35f), RoundedCornerShape(16.dp))
            .clickable(interactionSource, null) { onClick() }
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Exit arrow icon
            androidx.compose.foundation.Canvas(Modifier.size(16.dp)) {
                val w = size.width; val h = size.height
                val p = androidx.compose.ui.graphics.Paint().apply {
                    color       = RedAccent
                    style       = PaintingStyle.Stroke
                    strokeWidth = 2f
                    strokeCap   = StrokeCap.Round
                    strokeJoin  = StrokeJoin.Round
                }
                drawContext.canvas.apply {
                    drawLine(Offset(2f, h / 2), Offset(w - 4f, h / 2), p)
                    drawLine(Offset(w - 9f, 3f), Offset(w - 4f, h / 2), p)
                    drawLine(Offset(w - 9f, h - 3f), Offset(w - 4f, h / 2), p)
                    drawLine(Offset(2f, 2f), Offset(2f, h - 2f), p)
                }
            }
            Text("Sign Out", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = RedAccent, letterSpacing = 0.2.sp)
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  EDIT NAME DIALOG
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun EditNameDialog(
    currentName: String,
    onDismiss:   () -> Unit,
    onSave:      (String) -> Unit
) {
    var text by remember { mutableStateOf(currentName) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(InkMid)
                .border(1.dp, CardBorder, RoundedCornerShape(24.dp))
        ) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(CyanGradient))

            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Change Display Name", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPri)
                Text("This name is shown in meetings and your profile.", fontSize = 11.sp, color = TextSub, lineHeight = 16.sp)

                // Input field
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(InkLight)
                        .border(1.dp, if (text.isNotBlank()) Cyan.copy(0.4f) else CardBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    if (text.isEmpty()) {
                        Text("Enter your name", fontSize = 14.sp, color = TextMuted)
                    }
                    BasicTextField(
                        value         = text,
                        onValueChange = { text = it },
                        textStyle     = TextStyle(fontSize = 14.sp, color = TextPri, fontWeight = FontWeight.SemiBold),
                        singleLine    = true,
                        cursorBrush   = SolidColor(Cyan),
                        modifier      = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Cancel
                    Box(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(InkLighter)
                            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                            .clickable { onDismiss() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Cancel", fontSize = 12.sp, color = TextSub, fontWeight = FontWeight.SemiBold)
                    }
                    // Save
                    Box(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (text.isNotBlank()) CyanGradient else Brush.linearGradient(listOf(TextMuted, TextMuted)))
                            .clickable { if (text.isNotBlank()) onSave(text.trim()) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Save", fontSize = 12.sp, color = if (text.isNotBlank()) Color.Black else TextMuted,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  CONFIRM DIALOG (generic — used for sign out)
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun ConfirmDialog(
    title:        String,
    message:      String,
    confirmLabel: String,
    confirmBrush: Brush,
    onDismiss:    () -> Unit,
    onConfirm:    () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(InkMid)
                .border(1.dp, RedAccent.copy(0.3f), RoundedCornerShape(24.dp))
        ) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(RedGradient))

            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPri)
                Text(message, fontSize = 12.sp, color = TextSub, lineHeight = 18.sp)

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(InkLighter)
                            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                            .clickable { onDismiss() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Cancel", fontSize = 12.sp, color = TextSub, fontWeight = FontWeight.SemiBold)
                    }
                    Box(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(confirmBrush)
                            .clickable { onConfirm() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(confirmLabel, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}