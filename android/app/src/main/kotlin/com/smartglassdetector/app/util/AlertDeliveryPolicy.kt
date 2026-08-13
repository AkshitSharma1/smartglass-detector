package com.smartglassdetector.app.util

data class AlertDelivery(
    val playSound: Boolean,
    val playVibration: Boolean,
)

object AlertDeliveryPolicy {
    fun resolve(alertMode: String, soundUri: String): AlertDelivery {
        val normalizedMode = PreferencesManager.normalizeAlertMode(alertMode)
        return AlertDelivery(
            playSound = normalizedMode != "vibrationOnly" && soundUri.isNotEmpty(),
            playVibration = normalizedMode != "soundOnly",
        )
    }
}
