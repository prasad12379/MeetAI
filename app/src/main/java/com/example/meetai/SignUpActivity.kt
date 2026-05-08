package com.example.meetai

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.example.meetai.api.AuthApiClient
import com.example.meetai.api.SignUpRequest
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class SignUpActivity : AppCompatActivity() {

    private lateinit var etName: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var btnSignUp: CardView
    private lateinit var tvLogin: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnBack: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        etName = findViewById(R.id.etName)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnSignUp = findViewById(R.id.btnSignUp)
        tvLogin = findViewById(R.id.tvLogin)
        progressBar = findViewById(R.id.progressBar)
        btnBack = findViewById(R.id.btnBack)

        btnBack.setOnClickListener { finish() }
        btnSignUp.setOnClickListener { signUp() }
        tvLogin.setOnClickListener { finish() }
    }

    private fun signUp() {
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val confirmPassword = etConfirmPassword.text.toString().trim()

        if (name.isEmpty()) { etName.error = "Username required"; etName.requestFocus(); return }
        if (name.length < 3) { etName.error = "Username must be at least 3 characters"; etName.requestFocus(); return }
        if (email.isEmpty()) { etEmail.error = "Email required"; etEmail.requestFocus(); return }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) { etEmail.error = "Invalid email"; etEmail.requestFocus(); return }
        if (password.isEmpty()) { etPassword.error = "Password required"; etPassword.requestFocus(); return }
        if (password.length < 6) { etPassword.error = "Password must be at least 6 characters"; etPassword.requestFocus(); return }
        if (confirmPassword.isEmpty()) { etConfirmPassword.error = "Confirm password required"; etConfirmPassword.requestFocus(); return }
        if (password != confirmPassword) { etConfirmPassword.error = "Passwords don't match"; etConfirmPassword.requestFocus(); return }

        progressBar.visibility = View.VISIBLE
        btnSignUp.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = AuthApiClient.api.signup(
                    SignUpRequest(email = email, username = name, password = password)
                )

                progressBar.visibility = View.GONE
                btnSignUp.isEnabled = true

                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(
                        this@SignUpActivity,
                        "Account created! Please login.",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Go to login — don't save is_logged_in here
                    startActivity(Intent(this@SignUpActivity, LoginActivity::class.java))
                    finish()

                } else {
                    val errorMsg = response.body()?.message ?: "Sign up failed"
                    Toast.makeText(this@SignUpActivity, errorMsg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                btnSignUp.isEnabled = true
                Toast.makeText(this@SignUpActivity, "Connection error: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }
}