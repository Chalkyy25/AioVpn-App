/*
 * Copyright © 2017-2026 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package uk.co.aiovpn.android.util

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * Reusable animator for the VPN Connect button on Android TV.
 * Handles scale and alpha pulsing for the "Connecting" state.
 */
class ConnectButtonAnimator(private val targetView: View) {

    private var pulseAnimatorSet: AnimatorSet? = null

    /**
     * Starts the premium pulsing animation.
     * Scale: 1.0 -> 1.08 -> 1.0
     * Alpha: 0.6 -> 1.0 -> 0.6 (pulse effect)
     */
    fun startConnectingAnimation() {
        if (pulseAnimatorSet?.isRunning == true) return

        val scaleX = ObjectAnimator.ofFloat(targetView, View.SCALE_X, 1.0f, 1.08f).apply {
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
        
        val scaleY = ObjectAnimator.ofFloat(targetView, View.SCALE_Y, 1.0f, 1.08f).apply {
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }

        val alpha = ObjectAnimator.ofFloat(targetView, View.ALPHA, 0.5f, 1.0f).apply {
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }

        pulseAnimatorSet = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 1400 // Balanced between 1200-1500ms
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    /**
     * Stops the animation and resets the view properties.
     * @param alpha The target alpha to reset to (default 1.0)
     */
    fun stop(resetAlpha: Float = 0f) {
        pulseAnimatorSet?.apply {
            removeAllListeners()
            cancel()
        }
        pulseAnimatorSet = null

        // Reset with a slight animation for smoothness
        targetView.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .alpha(resetAlpha)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }
    
    /**
     * Sets a stable state (e.g. Connected)
     */
    fun setStable(alpha: Float = 1f) {
        stop(alpha)
    }
}
