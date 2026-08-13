package com.smartglassdetector.app.util

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object VibrationPresets {
    fun timings(preset: String): LongArray = when (
        PreferencesManager.normalizeVibrationPreset(preset)
    ) {
        "gentle" -> longArrayOf(0, 180, 420)
        "heartbeat" -> longArrayOf(0, 120, 100, 220, 650)
        "urgent" -> longArrayOf(0, 450, 180)
        else -> longArrayOf(0, 180, 120, 180, 620)
    }

    fun maximumAmplitudes(preset: String): IntArray =
        IntArray(timings(preset).size) { index ->
            if (index % 2 == 0) 0 else MAXIMUM_AMPLITUDE
        }

    const val MAXIMUM_AMPLITUDE = 255
}

class VibrationPlayer(context: Context) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun playRepeating(preset: String) {
        if (!vibrator.hasVibrator()) {
            return
        }
        val effect = VibrationEffect.createWaveform(
            VibrationPresets.timings(preset),
            VibrationPresets.maximumAmplitudes(preset),
            0,
        )
        vibrator.vibrate(effect, attributes)
    }

    fun preview(preset: String) {
        if (!vibrator.hasVibrator()) {
            return
        }
        val effect = VibrationEffect.createWaveform(
            VibrationPresets.timings(preset),
            VibrationPresets.maximumAmplitudes(preset),
            -1,
        )
        vibrator.vibrate(effect, attributes)
    }

    fun cancel() {
        vibrator.cancel()
    }
}
