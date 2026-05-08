package com.example.meetai

import android.content.Context

object UserPrefs {

    private const val PREFS_NAME = "meetai_prefs"

    fun save(
        context: Context,
        isLoggedIn: Boolean,
        email: String?,
        username: String?,
        userId: String?,
        token: String?
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putBoolean("is_logged_in", isLoggedIn)
            putString("user_email", email)
            putString("user_name", username)
            putString("user_id", userId)
            putString("auth_token", token)
            apply()
        }
    }

    fun isLoggedIn(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("is_logged_in", false)

    fun getEmail(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("user_email", null)

    fun getUsername(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("user_name", null)

    fun getUserId(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("user_id", null)

    fun getToken(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("auth_token", null)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}