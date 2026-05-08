package com.example.meetai

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.meetai.MainActivity

class Homefragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_homefragment, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvUserName    = view.findViewById<TextView>(R.id.tvUserName)
        val btnNewMeeting = view.findViewById<View>(R.id.btnNewMeeting)
        val btnJoinMeeting = view.findViewById<View>(R.id.btnJoinMeeting)
        val btnSchedule   = view.findViewById<View>(R.id.btnSchedule)
        val btnShareLink  = view.findViewById<View>(R.id.btnShareLink)
        val btnProfile    = view.findViewById<FrameLayout>(R.id.btnProfile)
        val tvSeeAll      = view.findViewById<TextView>(R.id.tvSeeAll)
        val tvAvatar      = view.findViewById<TextView>(R.id.tvAvatarInitial)

        // Load saved name
        val prefs = requireContext().getSharedPreferences("nexmeet_prefs", 0)
        val name  = prefs.getString("user_name", "User") ?: "User"
        tvUserName.text = name
        tvAvatar.text   = name.firstOrNull()?.uppercase() ?: "U"

        // Greeting based on hour — set directly on the existing TextView
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> "Good Morning \uD83D\uDC4B"
            hour < 17 -> "Good Afternoon \u2600\uFE0F"
            else      -> "Good Evening \uD83C\uDF19"
        }
        // Find the greeting TextView that already exists in the layout
        view.findViewById<TextView>(R.id.tvGreeting)?.text = greeting

        btnNewMeeting.setOnClickListener {
            startActivity(Intent(requireContext(), Newmeetingactivity::class.java))
        }

        btnJoinMeeting.setOnClickListener {
            startActivity(Intent(requireContext(), Joinmeetingactivity::class.java))
        }

        btnSchedule.setOnClickListener {
            Toast.makeText(requireContext(), "Schedule — coming soon", Toast.LENGTH_SHORT).show()
        }

        btnShareLink.setOnClickListener {
            val code = "NXM-" + (1000..9999).random() + "-" +
                    ('A'..'Z').shuffled().take(2).joinToString("")
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Join my MeetAI: $code")
            }
            startActivity(Intent.createChooser(shareIntent, "Share meeting link"))
        }

        btnProfile.setOnClickListener {
            (activity as? MainActivity)?.let {
                it.supportFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, Profilefragment())
                    .commit()
            }
        }

        tvSeeAll.setOnClickListener {
            (activity as? MainActivity)?.let {
                it.supportFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, Historyfragment())
                    .commit()
            }
        }
    }
}