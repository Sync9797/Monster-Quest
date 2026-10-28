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

    /** One long buzz: a monster leapt out at you while walking. */
    fun secretAmbush(context: Context) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        runCatching { v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 900, 150, 250), -1)) }
    }

    private fun play(context: Context, vararg pattern: Long) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        runCatching { v.vibrate(VibrationEffect.createWaveform(pattern, -1)) }
    }

    /** A rising triple: a monster was tamed. */
    fun tamed(context: Context) = play(context, 0, 60, 60, 80, 60, 160)

    /** A big burst: a critical catch. */
    fun criticalCatch(context: Context) = play(context, 0, 40, 40, 40, 40, 40, 40, 260)

    /** A firm double: a level-up. */
    fun levelUp(context: Context) = play(context, 0, 120, 80, 120)

    /** One short tap: a new move learned. */
    fun learned(context: Context) = play(context, 0, 70)

    /** Two quick pulses: a wild monster appeared nearby. */
    fun spawn(context: Context) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        runCatching { v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 90, 90, 90), -1)) }
    }
}
