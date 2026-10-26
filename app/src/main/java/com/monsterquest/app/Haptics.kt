package com.monsterquest.app

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Short vibration patterns for game events. */
object Haptics {
    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    /** A long, hard buzz: you've been ambushed. */
    fun ambush(context: Context) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        runCatching { v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 120, 300, 120, 300), -1)) }
    }

    /** Two quick pulses: a wild monster appeared nearby. */
    fun spawn(context: Context) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        runCatching { v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 90, 90, 90), -1)) }
    }
}
