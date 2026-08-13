package com.smartglassdetector.app.util

import android.content.Context
import kotlin.math.abs

class PreferencesManager(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    val alertsEnabled: Boolean
        get() = preferences.getBoolean(
            KEY_ALERTS_ENABLED,
            preferences.getBoolean(LEGACY_KEY_NOTIFICATIONS, true),
        )

    val alertThresholdRssi: Int
        get() = clampAlertThreshold(
            preferences.getInt(KEY_ALERT_THRESHOLD_RSSI, DEFAULT_ALERT_THRESHOLD_RSSI),
        )

    val vibrationPreset: String
        get() = normalizeVibrationPreset(
            preferences.getString(KEY_VIBRATION_PRESET, DEFAULT_VIBRATION_PRESET),
        )

    val alertMode: String
        get() = normalizeAlertMode(
            preferences.getString(KEY_ALERT_MODE, DEFAULT_ALERT_MODE),
        )

    val alertSoundUri: String
        get() = normalizeAlertSoundUri(
            preferences.getString(KEY_ALERT_SOUND_URI, DEFAULT_ALERT_SOUND_URI),
        )

    val alertSoundName: String
        get() = normalizeAlertSoundName(
            preferences.getString(KEY_ALERT_SOUND_NAME, null),
            alertSoundUri,
        )

    val alertDurationMs: Long
        get() = normalizeAlertDuration(
            preferences.getLong(KEY_ALERT_DURATION_MS, DEFAULT_ALERT_DURATION_MS),
        )

    val loggingEnabled: Boolean
        get() = preferences.getBoolean(KEY_LOGGING, true)

    val debugEnabled: Boolean
        get() = preferences.getBoolean(KEY_DEBUG, false)

    val wifiScanIntervalSeconds: Int
        get() = normalizeWifiScanIntervalSeconds(
            preferences.getInt(KEY_WIFI_SCAN_INTERVAL_SECONDS, DEFAULT_WIFI_SCAN_INTERVAL_SECONDS),
        )

    val themePreference: String
        get() = normalizeThemePreference(
            preferences.getString(KEY_THEME_PREFERENCE, DEFAULT_THEME_PREFERENCE),
        )

    val accentColor: String
        get() = normalizeAccentColor(
            preferences.getString(KEY_ACCENT_COLOR, DEFAULT_ACCENT_COLOR),
        )

    var onboardingVersion: Int
        get() = preferences.getInt(KEY_ONBOARDING_VERSION, 0)
        set(value) {
            preferences.edit().putInt(KEY_ONBOARDING_VERSION, value.coerceAtLeast(0)).apply()
        }

    var corePermissionRequested: Boolean
        get() = preferences.getBoolean(KEY_CORE_PERMISSION_REQUESTED, false)
        set(value) {
            preferences.edit().putBoolean(KEY_CORE_PERMISSION_REQUESTED, value).apply()
        }

    var wifiPermissionRequested: Boolean
        get() = preferences.getBoolean(KEY_WIFI_PERMISSION_REQUESTED, false)
        set(value) {
            preferences.edit().putBoolean(KEY_WIFI_PERMISSION_REQUESTED, value).apply()
        }

    var notificationPermissionRequested: Boolean
        get() = preferences.getBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, false)
        set(value) {
            preferences.edit().putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, value).apply()
        }

    var scanningRequested: Boolean
        get() = preferences.getBoolean(KEY_SCANNING_REQUESTED, false)
        set(value) {
            preferences.edit().putBoolean(KEY_SCANNING_REQUESTED, value).apply()
        }

    fun settingsMap(): Map<String, Any> = mapOf(
        "alertsEnabled" to alertsEnabled,
        "alertThresholdRssi" to alertThresholdRssi,
        "alertMode" to alertMode,
        "alertSoundUri" to alertSoundUri,
        "alertSoundName" to alertSoundName,
        "vibrationPreset" to vibrationPreset,
        "alertDurationMs" to alertDurationMs,
        "loggingEnabled" to loggingEnabled,
        "debugEnabled" to debugEnabled,
        "wifiScanIntervalSeconds" to wifiScanIntervalSeconds,
        "themePreference" to themePreference,
        "accentColor" to accentColor,
    )

    fun update(values: Map<*, *>) {
        val editor = preferences.edit()
        (values["alertsEnabled"] as? Boolean)?.let {
            editor.putBoolean(KEY_ALERTS_ENABLED, it)
        }
        (values["alertThresholdRssi"] as? Number)?.toInt()?.let {
            editor.putInt(KEY_ALERT_THRESHOLD_RSSI, clampAlertThreshold(it))
        }
        (values["alertMode"] as? String)?.let {
            editor.putString(KEY_ALERT_MODE, normalizeAlertMode(it))
        }
        (values["alertSoundUri"] as? String)?.let {
            editor.putString(KEY_ALERT_SOUND_URI, normalizeAlertSoundUri(it))
        }
        (values["alertSoundName"] as? String)?.let {
            val soundUri = (values["alertSoundUri"] as? String) ?: alertSoundUri
            editor.putString(
                KEY_ALERT_SOUND_NAME,
                normalizeAlertSoundName(it, soundUri),
            )
        }
        (values["vibrationPreset"] as? String)?.let {
            editor.putString(KEY_VIBRATION_PRESET, normalizeVibrationPreset(it))
        }
        (values["alertDurationMs"] as? Number)?.toLong()?.let {
            editor.putLong(KEY_ALERT_DURATION_MS, normalizeAlertDuration(it))
        }
        (values["loggingEnabled"] as? Boolean)?.let {
            editor.putBoolean(KEY_LOGGING, it)
        }
        (values["debugEnabled"] as? Boolean)?.let {
            editor.putBoolean(KEY_DEBUG, it)
        }
        (values["wifiScanIntervalSeconds"] as? Number)?.toInt()?.let {
            editor.putInt(
                KEY_WIFI_SCAN_INTERVAL_SECONDS,
                normalizeWifiScanIntervalSeconds(it),
            )
        }
        (values["themePreference"] as? String)?.let {
            editor.putString(KEY_THEME_PREFERENCE, normalizeThemePreference(it))
        }
        (values["accentColor"] as? String)?.let {
            editor.putString(KEY_ACCENT_COLOR, normalizeAccentColor(it))
        }
        editor.apply()
    }

    companion object {
        const val DEFAULT_ALERT_THRESHOLD_RSSI = -75
        const val MIN_ALERT_THRESHOLD_RSSI = -100
        const val MAX_ALERT_THRESHOLD_RSSI = -30
        const val DEFAULT_VIBRATION_PRESET = "doublePulse"
        const val DEFAULT_ALERT_MODE = "both"
        const val DEFAULT_ALERT_SOUND_URI = "content://settings/system/notification_sound"
        const val DEFAULT_ALERT_SOUND_NAME = "Default notification sound"
        const val DEFAULT_ALERT_DURATION_MS = 10_000L
        const val CURRENT_ONBOARDING_VERSION = 1
        const val DEFAULT_WIFI_SCAN_INTERVAL_SECONDS = 5
        const val DEFAULT_THEME_PREFERENCE = "system"
        const val DEFAULT_ACCENT_COLOR = "blue"
        val ALERT_DURATIONS_MS = listOf(5_000L, 10_000L, 20_000L, 30_000L, 60_000L)
        val WIFI_SCAN_INTERVALS_SECONDS = listOf(3, 5, 10, 15)
        val VIBRATION_PRESETS = setOf("gentle", "doublePulse", "heartbeat", "urgent")
        val ALERT_MODES = setOf("soundOnly", "vibrationOnly", "both")
        val THEME_PREFERENCES = setOf("system", "light", "dark", "amoled")
        val ACCENT_COLORS = setOf("blue", "purple", "teal", "orange", "rose")

        fun clampAlertThreshold(value: Int): Int =
            value.coerceIn(MIN_ALERT_THRESHOLD_RSSI, MAX_ALERT_THRESHOLD_RSSI)

        fun normalizeVibrationPreset(value: String?): String =
            value?.takeIf(VIBRATION_PRESETS::contains) ?: DEFAULT_VIBRATION_PRESET

        fun normalizeAlertMode(value: String?): String =
            value?.takeIf(ALERT_MODES::contains) ?: DEFAULT_ALERT_MODE

        fun normalizeAlertSoundUri(value: String?): String =
            value ?: DEFAULT_ALERT_SOUND_URI

        fun normalizeAlertSoundName(value: String?, soundUri: String): String = when {
            value?.isNotBlank() == true -> value
            soundUri.isEmpty() -> "None"
            soundUri == DEFAULT_ALERT_SOUND_URI -> DEFAULT_ALERT_SOUND_NAME
            else -> "Selected notification sound"
        }

        fun normalizeAlertDuration(value: Long): Long = ALERT_DURATIONS_MS
            .minByOrNull { candidate -> abs(candidate - value) }
            ?: DEFAULT_ALERT_DURATION_MS

        fun normalizeWifiScanIntervalSeconds(value: Int): Int =
            WIFI_SCAN_INTERVALS_SECONDS.minByOrNull { candidate -> abs(candidate - value) }
                ?: DEFAULT_WIFI_SCAN_INTERVAL_SECONDS

        fun normalizeThemePreference(value: String?): String =
            value?.takeIf(THEME_PREFERENCES::contains) ?: DEFAULT_THEME_PREFERENCE

        fun normalizeAccentColor(value: String?): String =
            value?.takeIf(ACCENT_COLORS::contains) ?: DEFAULT_ACCENT_COLOR

        private const val PREFERENCES_NAME = "smartglass_detector_settings"
        private const val KEY_ALERTS_ENABLED = "alerts_enabled"
        private const val KEY_ALERT_THRESHOLD_RSSI = "alert_threshold_rssi"
        private const val KEY_ALERT_MODE = "alert_mode"
        private const val KEY_ALERT_SOUND_URI = "alert_sound_uri"
        private const val KEY_ALERT_SOUND_NAME = "alert_sound_name"
        private const val KEY_VIBRATION_PRESET = "vibration_preset"
        private const val KEY_ALERT_DURATION_MS = "alert_duration_ms"
        private const val KEY_LOGGING = "logging"
        private const val KEY_DEBUG = "debug"
        private const val KEY_WIFI_SCAN_INTERVAL_SECONDS = "wifi_scan_interval_seconds"
        private const val KEY_THEME_PREFERENCE = "theme_preference"
        private const val KEY_ACCENT_COLOR = "accent_color"
        private const val KEY_ONBOARDING_VERSION = "onboarding_version"
        private const val KEY_CORE_PERMISSION_REQUESTED = "core_permission_requested"
        private const val KEY_WIFI_PERMISSION_REQUESTED = "wifi_permission_requested"
        private const val KEY_NOTIFICATION_PERMISSION_REQUESTED =
            "notification_permission_requested"
        private const val KEY_SCANNING_REQUESTED = "scanning_requested"
        private const val LEGACY_KEY_NOTIFICATIONS = "notifications"
    }
}
