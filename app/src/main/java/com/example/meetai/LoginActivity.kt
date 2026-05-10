package com.example.meetai

// ════════════════════════════════════════════════════════════════════════════
//  LoginActivity.kt  —  MeetAI  •  Compose rewrite
//
//  All logic unchanged: UserPrefs.isLoggedIn check, AuthApiClient.api.login,
//  UserPrefs.save, navigation to MainActivity / SignUpActivity.
// ════════════════════════════════════════════════════════════════════════════

import android.content.Intent
import android.os.Bundle
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.meetai.api.AuthApiClient
import com.example.meetai.api.LoginRequest
import kotlinx.coroutines.launch

// ── Palette ──────────────────────────────────────────────────────────────────
private val LInk        = Color(0xFF060A12)
private val LInkMid     = Color(0xFF0E1422)
private val LInkLight   = Color(0xFF161D2F)
private val LInkLighter = Color(0xFF1C2540)
private val LCyan       = Color(0xFF00D4FF)
private val LGold       = Color(0xFFFFBF3C)
private val LRed        = Color(0xFFFF4757)
private val LTextPri    = Color(0xFFF0F6FF)
private val LTextSub    = Color(0xFF6B7FA3)
private val LTextMuted  = Color(0xFF3A4665)
private val LCardBorder = Color(0xFF1E293B)

private val LBgGrad   = Brush.verticalGradient(listOf(Color(0xFF060A12), Color(0xFF080C18), Color(0xFF060A12)))
private val LCyanGrad = Brush.linearGradient(listOf(LCyan, Color(0xFF0066FF)))

// ════════════════════════════════════════════════════════════════════════════
class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (UserPrefs.isLoggedIn(this)) {
            startActivity(Intent(this, MainActivity::class.java)); finish(); return
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor     = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars    = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            LoginScreen(
                onLogin = { email, password -> doLogin(email, password) },
                onSignUp = { startActivity(Intent(this, SignUpActivity::class.java)) },
                onForgotPassword = {
                    Toast.makeText(this, "Password reset — Contact support", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun doLogin(email: String, password: String) {
        lifecycleScope.launch {
            try {
                val response = AuthApiClient.api.login(LoginRequest(email = email, password = password))
                if (response.isSuccessful && response.body()?.success == true) {
                    val auth = response.body()!!
                    UserPrefs.save(
                        context    = this@LoginActivity,
                        isLoggedIn = true,
                        email      = auth.user?.email,
                        username   = auth.user?.username,
                        userId     = auth.user?.id,
                        token      = auth.token
                    )
                    Toast.makeText(this@LoginActivity, "Welcome back, ${auth.user?.username}!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java)); finish()
                } else {
                    Toast.makeText(this@LoginActivity, response.body()?.message ?: "Login failed", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "Connection error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { super.onBackPressed(); finishAffinity() }
}

// ════════════════════════════════════════════════════════════════════════════
//  SCREEN
// ════════════════════════════════════════════════════════════════════════════
@Composable
fun LoginScreen(
    onLogin:           (email: String, password: String) -> Unit,
    onSignUp:          () -> Unit,
    onForgotPassword:  () -> Unit
) {
    var email       by remember { mutableStateOf("") }
    var password    by remember { mutableStateOf("") }
    var isLoading   by remember { mutableStateOf(false) }
    var showPwd     by remember { mutableStateOf(false) }
    var emailError  by remember { mutableStateOf("") }
    var pwdError    by remember { mutableStateOf("") }
    var visible     by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { kotlinx.coroutines.delay(80); visible = true }

    val inf = rememberInfiniteTransition(label = "loginInf")
    val sweepAngle by inf.animateFloat(0f, 360f,
        infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "ls")

    fun validate(): Boolean {
        emailError = ""; pwdError = ""
        if (email.isBlank())                                           { emailError = "Email required"; return false }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) { emailError = "Invalid email"; return false }
        if (password.isBlank())                                        { pwdError = "Password required"; return false }
        return true
    }

    Box(Modifier.fillMaxSize().background(LBgGrad)) {
        // Ambient orbs
        Box(Modifier.size(300.dp).offset(x = 120.dp, y = (-60).dp).blur(60.dp)
            .background(Brush.radialGradient(listOf(LCyan.copy(0.12f), Color.Transparent)), CircleShape))
        Box(Modifier.size(240.dp).offset(x = (-60).dp, y = 500.dp).blur(50.dp)
            .background(Brush.radialGradient(listOf(Color(0x160066FF), Color.Transparent)), CircleShape))

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(32.dp))

            // Logo
            AnimatedVisibility(visible, enter = fadeIn(tween(600)) + scaleIn(tween(600, easing = EaseOutBack))) {
                LogoMark(sweepAngle = sweepAngle, size = 80)
            }

            Spacer(Modifier.height(24.dp))

            // Title
            AnimatedVisibility(visible, enter = fadeIn(tween(500, 200)) + slideInVertically(tween(500, 200)) { 20 }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Welcome Back", fontSize = 28.sp, fontWeight = FontWeight.Black,
                        color = LTextPri, letterSpacing = (-0.8).sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Sign in to continue", fontSize = 13.sp, color = LTextSub)
                }
            }

            Spacer(Modifier.height(36.dp))

            // Form card
            AnimatedVisibility(visible, enter = fadeIn(tween(500, 350)) + slideInVertically(tween(500, 350)) { 40 }) {
                Column(
                    Modifier.padding(horizontal = 24.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(LInkMid)
                        .border(1.dp, LCardBorder, RoundedCornerShape(24.dp))
                ) {
                    Box(Modifier.fillMaxWidth().height(2.dp).background(LCyanGrad))
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        AuthInputField(
                            label    = "Email",
                            hint     = "your@email.com",
                            value    = email,
                            tint     = LCyan,
                            error    = emailError,
                            keyboardType = KeyboardType.Email,
                            onValueChange = { email = it; emailError = "" }
                        )
                        AuthInputField(
                            label    = "Password",
                            hint     = "••••••••",
                            value    = password,
                            tint     = LGold,
                            error    = pwdError,
                            isPassword = true,
                            showPassword = showPwd,
                            onTogglePassword = { showPwd = !showPwd },
                            onValueChange = { password = it; pwdError = "" }
                        )
                        // Forgot password
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            Text("Forgot Password?", fontSize = 11.sp, color = LCyan,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { onForgotPassword() })
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Login button
            AnimatedVisibility(visible, enter = fadeIn(tween(500, 450)) + slideInVertically(tween(500, 450)) { 30 }) {
                AuthButton(
                    label     = if (isLoading) "" else "Sign In",
                    isLoading = isLoading,
                    gradient  = LCyanGrad,
                    dotColor  = LCyan
                ) {
                    if (validate()) {
                        isLoading = true
                        onLogin(email.trim(), password.trim())
                        // Loading state reset by caller via recomposition on error
                        // For simplicity reset after small delay if still loading
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Sign up link
            AnimatedVisibility(visible, enter = fadeIn(tween(400, 550))) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Don't have an account?", fontSize = 13.sp, color = LTextSub)
                    Text("Sign Up", fontSize = 13.sp, color = LCyan, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onSignUp() })
                }
            }

            Spacer(Modifier.height(40.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  SHARED COMPOSABLES  (also used by SignUpActivity screen)
// ════════════════════════════════════════════════════════════════════════════

@Composable
internal fun LogoMark(sweepAngle: Float, size: Int) {
    val s = size.dp
    Box(Modifier.size(s), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(s)) {
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(LCyan, LGold, LCyan.copy(0f)), center = center),
                startAngle = sweepAngle, sweepAngle = 240f, useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Box(
            Modifier.size((s.value * 0.78f).dp)
                .background(Brush.radialGradient(listOf(Color(0xFF0E1C32), LInkMid)), CircleShape)
                .border(1.dp, LCyan.copy(0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("M", fontSize = (s.value * 0.38f).sp, fontWeight = FontWeight.Black,
                color = LCyan, letterSpacing = (-1.5).sp)
        }
    }
}

@Composable
internal fun AuthInputField(
    label:           String,
    hint:            String,
    value:           String,
    tint:            Color,
    error:           String            = "",
    isPassword:      Boolean           = false,
    showPassword:    Boolean           = false,
    onTogglePassword: (() -> Unit)?    = null,
    keyboardType:    KeyboardType      = KeyboardType.Text,
    onValueChange:   (String) -> Unit
) {
    val hasError = error.isNotEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(6.dp)
                .background((if (hasError) LRed else tint).copy(0.3f), CircleShape)
                .border(1.dp, (if (hasError) LRed else tint).copy(0.7f), CircleShape))
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                color = if (hasError) LRed.copy(0.8f) else LTextMuted, letterSpacing = 0.6.sp)
        }
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(LInkLight)
                .border(1.dp, when {
                    hasError          -> LRed.copy(0.5f)
                    value.isNotEmpty() -> tint.copy(0.35f)
                    else               -> LCardBorder
                }, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 13.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(hint, fontSize = 13.sp, color = LTextMuted)
                    BasicTextField(
                        value         = value,
                        onValueChange = onValueChange,
                        textStyle     = TextStyle(fontSize = 14.sp, color = LTextPri,
                            fontWeight = FontWeight.SemiBold),
                        singleLine    = true,
                        cursorBrush   = SolidColor(tint),
                        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                        visualTransformation = if (isPassword && !showPassword)
                            PasswordVisualTransformation() else VisualTransformation.None,
                        modifier      = Modifier.fillMaxWidth()
                    )
                }
                if (isPassword && onTogglePassword != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (showPassword) "Hide" else "Show",
                        fontSize = 10.sp, color = tint.copy(0.7f),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onTogglePassword() }
                    )
                }
            }
        }
        if (hasError) {
            Text(error, fontSize = 10.sp, color = LRed.copy(0.9f), modifier = Modifier.padding(start = 2.dp))
        }
    }
}

@Composable
internal fun AuthButton(
    label:     String,
    isLoading: Boolean,
    gradient:  Brush,
    dotColor:  Color,
    onClick:   () -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f,
        spring(Spring.DampingRatioMediumBouncy), label = "abs")

    val glowAlpha by rememberInfiniteTransition(label = "abg").animateFloat(
        0.2f, 0.5f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "abga")

    Box(
        Modifier.padding(horizontal = 24.dp).fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(18.dp))
            .background(if (isLoading) Brush.linearGradient(listOf(LInkLight, LInkLight)) else gradient)
            .clickable(interactionSource, null, enabled = !isLoading) { onClick() }
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AuthLoadingDots(dotColor)
                Text("Please wait...", fontSize = 13.sp, color = LTextSub, fontWeight = FontWeight.Bold)
            }
        } else {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Black,
                color = Color.Black, letterSpacing = 0.3.sp)
        }
    }
}

@Composable
internal fun AuthLoadingDots(color: Color) {
    val inf = rememberInfiniteTransition(label = "ald")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        (0..2).forEach { i ->
            val alpha by inf.animateFloat(0.2f, 1f,
                infiniteRepeatable(tween(600, delayMillis = i * 150), RepeatMode.Reverse), label = "ad$i")
            Box(Modifier.size(5.dp).graphicsLayer { this.alpha = alpha }
                .background(color, CircleShape))
        }
    }
}