package com.smartglassdetector.app.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertUtilitiesTest {
    @Test
    fun vibrationPresetsAreDistinctAndStartImmediately() {
        val gentle = VibrationPresets.timings("gentle")
        val doublePulse = VibrationPresets.timings("doublePulse")

        assertTrue(gentle.first() == 0L)
        assertTrue(doublePulse.first() == 0L)
        assertFalse(gentle.contentEquals(doublePulse))
        assertArrayEquals(doublePulse, VibrationPresets.timings("invalid"))
    }

    @Test
    fun everyActiveVibrationSegmentUsesMaximumAmplitude() {
        PreferencesManager.VIBRATION_PRESETS.forEach { preset ->
            val amplitudes = VibrationPresets.maximumAmplitudes(preset)

            assertTrue(amplitudes.isNotEmpty())
            amplitudes.forEachIndexed { index, amplitude ->
                assertEquals(
                    if (index % 2 == 0) 0 else VibrationPresets.MAXIMUM_AMPLITUDE,
                    amplitude,
                )
            }
        }
    }

    @Test
    fun alertModesControlSoundAndVibrationIndependently() {
        assertEquals(
            AlertDelivery(playSound = true, playVibration = true),
            AlertDeliveryPolicy.resolve("both", "content://tone"),
        )
        assertEquals(
            AlertDelivery(playSound = true, playVibration = false),
            AlertDeliveryPolicy.resolve("soundOnly", "content://tone"),
        )
        assertEquals(
            AlertDelivery(playSound = false, playVibration = true),
            AlertDeliveryPolicy.resolve("vibrationOnly", "content://tone"),
        )
        assertEquals(
            AlertDelivery(playSound = false, playVibration = false),
            AlertDeliveryPolicy.resolve("soundOnly", ""),
        )
    }

    @Test
    fun shakeRequiresTwoStrongSeparatedPeaks() {
        val detector = ShakeGestureDetector()

        assertFalse(detector.onSample(2.3, 1_000L))
        assertFalse(detector.onSample(2.3, 1_050L))
        assertTrue(detector.onSample(2.3, 1_150L))
    }
}
