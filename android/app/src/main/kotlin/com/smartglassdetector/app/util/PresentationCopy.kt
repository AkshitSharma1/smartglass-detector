package com.smartglassdetector.app.util

object PresentationCopy {
    const val SERVICE_STATUS = "Scanning for smartglasses and image/video activity"
    const val SMARTGLASS_ALERT_TITLE = "Smartglass detected nearby"
    const val MEDIA_ALERT_TITLE = "Nearby smartglass image/video transfer detected"
    const val MEDIA_ALERT_BODY =
        "Transferred media may have been recorded during the detected activity."
    const val SMARTGLASS_FALLBACK_NAME = "Unnamed smartglass"
    const val ALERT_CHANNEL_NAME = "Smartglass alerts"

    fun smartglassEvidence(reasonText: String, companyId: String?): String {
        val normalized = reasonText.lowercase()
        return when {
            "service uuid" in normalized -> "Smartglass Bluetooth service"
            "device name" in normalized -> "Smartglass device-name signature"
            companyId != null -> "Smartglass manufacturer signature"
            else -> "Smartglass signal signature"
        }
    }

    fun confidenceLabel(confidence: String): String =
        confidence.replaceFirstChar { character -> character.uppercase() }
}
