package com.example.meetai

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment

class Profilefragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_profilefragment, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvName    = view.findViewById<TextView>(R.id.tvProfileName)
        val tvEmail   = view.findViewById<TextView>(R.id.tvProfileEmail)
        val tvAvatar  = view.findViewById<TextView>(R.id.tvAvatarLarge)
        val rowName   = view.findViewById<View>(R.id.rowEditName)
        val btnSignOut = view.findViewById<CardView>(R.id.btnSignOut)

        // Load real user data from UserPrefs
        val name  = UserPrefs.getUsername(requireContext()) ?: "User"
        val email = UserPrefs.getEmail(requireContext()) ?: "No email"

        tvName.text   = name
        tvAvatar.text = name.firstOrNull()?.uppercase() ?: "U"
        tvEmail.text  = email

        rowName.setOnClickListener {
            val input = android.widget.EditText(requireContext()).apply {
                hint = "Enter your name"
                setText(name)
            }
            AlertDialog.Builder(requireContext())
                .setTitle("Change Display Name")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val newName = input.text.toString().trim()
                    if (newName.isNotEmpty()) {
                        // Save locally only (display name override)
                        requireContext()
                            .getSharedPreferences("meetai_prefs", 0)
                            .edit()
                            .putString("user_name", newName)
                            .apply()

                        tvName.text   = newName
                        tvAvatar.text = newName.firstOrNull()?.uppercase() ?: "U"
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnSignOut.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out?")
                .setPositiveButton("Sign Out") { _, _ ->
                    // Clear all saved user data
                    UserPrefs.clear(requireContext())

                    Toast.makeText(requireContext(), "Signed out successfully", Toast.LENGTH_SHORT).show()

                    // Go back to login and clear back stack
                    val intent = Intent(requireContext(), LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    requireActivity().finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}