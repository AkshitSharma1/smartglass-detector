package com.smartglassdetector.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PreferencesManagerTest {
    @Test
    fun clampsAlertThreshold() {
        assertEquals(-100, PreferencesManager.clampAlertThreshold(-150))
        assertEquals(-75, PreferencesManager.clampAlertThreshold(-75))
        assertEquals(-30, PreferencesManager.clampAlertThreshold(0))
    }

    @Test
    fun normalizesVibrationPreset() {
        assertEquals("gentle", PreferencesManager.normalizeVibrationPreset("gentle"))
        assertEquals("doublePulse", PreferencesManager.normalizeVibrationPreset("unknown"))
        assertEquals("doublePulse", PreferencesManager.normalizeVibrationPreset(null))
    }

    @Test
    fun defaultsAndNormalizesAlertModeToSoundAndVibration() {
        assertEquals("soundOnly", PreferencesManager.normalizeAlertMode("soundOnly"))
        assertEquals("vibrationOnly", PreferencesManager.normalizeAlertMode("vibrationOnly"))
        assertEquals("both", PreferencesManager.normalizeAlertMode("both"))
        assertEquals("both", PreferencesManager.normalizeAlertMode("unknown"))
        assertEquals("both", PreferencesManager.normalizeAlertMode(null))
    }

    @Test
    fun preservesNoneAndDefaultsNotificationSoundMetadata() {
        assertEquals("", PreferencesManager.normalizeAlertSoundUri(""))
        assertEquals(
            PreferencesManager.DEFAULT_ALERT_SOUND_URI,
            PreferencesManager.normalizeAlertSoundUri(null),
        )
        assertEquals("None", PreferencesManager.normalizeAlertSoundName(null, ""))
        assertEquals(
            PreferencesManager.DEFAULT_ALERT_SOUND_NAME,
            PreferencesManager.normalizeAlertSoundName(
                null,
                PreferencesManager.DEFAULT_ALERT_SOUND_URI,
            ),
        )
    }

    @Test
    fun normalizesAlertDurationToSupportedChoice() {
        assertEquals(5_000L, PreferencesManager.normalizeAlertDuration(1_000L))
        assertEquals(30_000L, PreferencesManager.normalizeAlertDuration(26_000L))
        assertEquals(60_000L, PreferencesManager.normalizeAlertDuration(90_000L))
    }

    @Test
    fun defaultsAndNormalizesWifiScanInterval() {
        assertEquals(3, PreferencesManager.normalizeWifiScanIntervalSeconds(1))
        assertEquals(5, PreferencesManager.normalizeWifiScanIntervalSeconds(5))
        assertEquals(10, PreferencesManager.normalizeWifiScanIntervalSeconds(11))
        assertEquals(15, PreferencesManager.normalizeWifiScanIntervalSeconds(99))
    }

    @Test
    fun defaultsAndNormalizesAppearanceSettings() {
        assertEquals("dark", PreferencesManager.normalizeThemePreference("dark"))
        assertEquals("amoled", PreferencesManager.normalizeThemePreference("amoled"))
        assertEquals("system", PreferencesManager.normalizeThemePreference("unknown"))
        assertEquals("system", PreferencesManager.normalizeThemePreference(null))

        assertEquals("purple", PreferencesManager.normalizeAccentColor("purple"))
        assertEquals("blue", PreferencesManager.normalizeAccentColor("unknown"))
        assertEquals("blue", PreferencesManager.normalizeAccentColor(null))
    }
}
