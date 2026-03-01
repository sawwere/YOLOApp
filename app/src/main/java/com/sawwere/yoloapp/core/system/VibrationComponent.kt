package com.sawwere.yoloapp.core.system

import android.annotation.SuppressLint
import android.app.Activity.VIBRATOR_MANAGER_SERVICE
import android.app.Activity.VIBRATOR_SERVICE
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class VibrationComponent(
    private val vibrator: Vibrator
) {
    @SuppressLint("ObsoleteSdkInt")
    fun triggerHapticFeedback() {
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        30,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(30)
            }
        }
    }

    companion object {
        fun getFromContext(context: Context): VibrationComponent {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    context.getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            return VibrationComponent(vibrator)
        }
    }
}