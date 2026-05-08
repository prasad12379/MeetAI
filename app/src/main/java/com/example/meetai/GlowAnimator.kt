package com.example.meetai

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View

fun View.startNeonPulse() {

    val alphaPulse = ObjectAnimator.ofFloat(this, "alpha", 0.85f, 1.0f)
    alphaPulse.duration = 1800L
    alphaPulse.repeatMode = ValueAnimator.REVERSE
    alphaPulse.repeatCount = ValueAnimator.INFINITE

    val elevationPulse = ObjectAnimator.ofFloat(this, "elevation", 16f, 28f)
    elevationPulse.duration = 1800L
    elevationPulse.repeatMode = ValueAnimator.REVERSE
    elevationPulse.repeatCount = ValueAnimator.INFINITE

    AnimatorSet().apply {
        playTogether(alphaPulse, elevationPulse)
        start()
    }
}