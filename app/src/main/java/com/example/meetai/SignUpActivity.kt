package com.example.meetai

// ════════════════════════════════════════════════════════════════════════════
//  SignUpActivity.kt  —  MeetAI  •  Compose rewrite
//
//  All logic unchanged: field validation rules, AuthApiClient.api.signup,
//  SignUpRequest, navigation to LoginActivity on success.
// ════════════════════════════════════════════════════════════════════════════

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.meetai.api.AuthApiClient
import com.example.meetai.api.SignUpRequest
import kotlinx.coroutines.launch

// ── Palette (inherits from LoginActivity shared values) ───────────────────────
private val SInk        = Color(0xFF060A12)
private val SInkMid     = Color(0xFF0E1422)
private val SInkLight   = Color(0xFF161D2F)
private val SCyan       = Color(0xFF00D4FF)
private val SGold       = Color(0xFFFFBF3C)
private val SGreen      = Color(0xFF00E5A0)
private val SRed        = Color(0xFFFF4757)
private val STextPri    = Color(0xFFF0F6FF)
private val STextSub    = Color(0xFF6B7FA3)
private val SCardBorder = Color(0xFF1E293B)

private val SBgGrad    = Brush.verticalGradient(listOf(Color(0xFF060A12), Color(0xFF080C18), Color(0xFF060A12)))
private val SGreenGrad = Brush.linearGradient(listOf(SGreen, Color(0xFF0099FF)))
private val SCyanGrad  = Brush.linearGradient(listOf(SCyan, Color(0xFF0066FF)))

// ════════════════════════════════════════════════════════════════════════════
class SignUpActivity : AppCompatActivity() {

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
            SignUpScreen(
                onSignUp = { name, email, password -> doSignUp(name, email, password) },
                onBack   = { finish() }
            )
        }
    }

    private fun doSignUp(name: String, email: String, password: String) {
        lifecycleScope.launch {
            try {
                val response = AuthApiClient.api.signup(
                    SignUpRequest(email = email, username = name, password = password)
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(this@SignUpActivity, "Account created! Please login.", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@SignUpActivity, LoginActivity::class.java)); finish()
                } else {
                    Toast.makeText(this@SignUpActivity, response.body()?.message ?: "Sign up failed", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SignUpActivity, "Connection error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  SCREEN
// ════════════════════════════════════════════════════════════════════════════
@Composable
fun SignUpScreen(
    onSignUp: (name: String, email: String, password: String) -> Unit,
    onBack:   () -> Unit
) {
    var name            by remember { mutableStateOf("") }
    var email           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading       by remember { mutableStateOf(false) }
    var showPwd         by remember { mutableStateOf(false) }
    var showConfirmPwd  by remember { mutableStateOf(false) }
    var nameError       by remember { mutableStateOf("") }
    var emailError      by remember { mutableStateOf("") }
    var pwdError        by remember { mutableStateOf("") }
    var confirmError    by remember { mutableStateOf("") }
    var visible         by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { kotlinx.coroutines.delay(80); visible = true }

    val inf = rememberInfiniteTransition(label = "suInf")
    val sweepAngle by inf.animateFloat(0f, 360f,
        infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "sus")

    fun validate(): Boolean {
        nameError = ""; emailError = ""; pwdError = ""; confirmError = ""
        if (name.isBlank())            { nameError = "Username required"; return false }
        if (name.length < 3)           { nameError = "At least 3 characters"; return false }
        if (email.isBlank())           { emailError = "Email required"; return false }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) { emailError = "Invalid email"; return false }
        if (password.isBlank())        { pwdError = "Password required"; return false }
        if (password.length < 6)       { pwdError = "At least 6 characters"; return false }
        if (confirmPassword.isBlank()) { confirmError = "Please confirm your password"; return false }
        if (password != confirmPassword){ confirmError = "Passwords don't match"; return false }
        return true
    }

    Box(Modifier.fillMaxSize().background(SBgGrad)) {
        // Ambient orbs
        Box(Modifier.size(280.dp).offset(x = (-60).dp, y = 80.dp).blur(55.dp)
            .background(Brush.radialGradient(listOf(SGreen.copy(0.1f), Color.Transparent)), CircleShape))
        Box(Modifier.size(220.dp).offset(x = 200.dp, y = 400.dp).blur(50.dp)
            .background(Brush.radialGradient(listOf(SCyan.copy(0.1f), Color.Transparent)), CircleShape))

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(20.dp))

            // Back + logo row
            AnimatedVisibility(visible, enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { -20 }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    // Back button
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                            .background(SInkLight).border(1.dp, SCardBorder, RoundedCornerShape(12.dp))
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Canvas(Modifier.size(16.dp)) {
                            val p = androidx.compose.ui.graphics.Paint().apply {
                                color = STextSub; style = PaintingStyle.Stroke
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
                    LogoMark(sweepAngle = sweepAngle, size = 52)
                    Spacer(Modifier.width(40.dp)) // balance
                }
            }

            Spacer(Modifier.height(20.dp))

            // Title
            AnimatedVisibility(visible, enter = fadeIn(tween(500, 150)) + slideInVertically(tween(500, 150)) { 20 }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Create Account", fontSize = 28.sp, fontWeight = FontWeight.Black,
                        color = STextPri, letterSpacing = (-0.8).sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Join MeetAI today", fontSize = 13.sp, color = STextSub)
                }
            }

            Spacer(Modifier.height(28.dp))

            // Form card
            AnimatedVisibility(visible, enter = fadeIn(tween(500, 280)) + slideInVertically(tween(500, 280)) { 40 }) {
                Column(
                    Modifier.padding(horizontal = 24.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(SInkMid)
                        .border(1.dp, SCardBorder, RoundedCornerShape(24.dp))
                ) {
                    Box(Modifier.fillMaxWidth().height(2.dp).background(SGreenGrad))
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        AuthInputField(
                            label = "Username", hint = "At least 3 characters",
                            value = name, tint = SGreen, error = nameError,
                            onValueChange = { name = it; nameError = "" }
                        )
                        AuthInputField(
                            label = "Email", hint = "your@email.com",
                            value = email, tint = SCyan, error = emailError,
                            keyboardType = KeyboardType.Email,
                            onValueChange = { email = it; emailError = "" }
                        )
                        AuthInputField(
                            label = "Password", hint = "At least 6 characters",
                            value = password, tint = SGold, error = pwdError,
                            isPassword = true, showPassword = showPwd,
                            onTogglePassword = { showPwd = !showPwd },
                            onValueChange = { password = it; pwdError = "" }
                        )
                        AuthInputField(
                            label = "Confirm Password", hint = "Repeat your password",
                            value = confirmPassword, tint = SGold, error = confirmError,
                            isPassword = true, showPassword = showConfirmPwd,
                            onTogglePassword = { showConfirmPwd = !showConfirmPwd },
                            onValueChange = { confirmPassword = it; confirmError = "" }
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Sign up button
            AnimatedVisibility(visible, enter = fadeIn(tween(500, 400)) + slideInVertically(tween(500, 400)) { 30 }) {
                AuthButton(
                    label     = if (isLoading) "" else "Create Account",
                    isLoading = isLoading,
                    gradient  = SGreenGrad,
                    dotColor  = SGreen
                ) {
                    if (validate()) {
                        isLoading = true
                        onSignUp(name.trim(), email.trim(), password.trim())
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Login link
            AnimatedVisibility(visible, enter = fadeIn(tween(400, 500))) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Already have an account?", fontSize = 13.sp, color = STextSub)
                    Text("Sign In", fontSize = 13.sp, color = SCyan, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onBack() })
                }
            }

            Spacer(Modifier.height(40.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}