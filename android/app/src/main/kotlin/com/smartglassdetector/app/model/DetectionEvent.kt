package com.smartglassdetector.app.model

data class DetectionEvent(
    val timestampMs: Long,
    val deviceAddress: String,
    val deviceName: String?,
    val companyId: String?,
    val companyName: String?,
    val manufacturerDataHex: String?,
    val reasonCodes: List<String>,
    val reasonText: String,
    val confidence: String,
    val rssi: Int,
    val txPower: Int?,
    val serviceUuids: List<String>,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "type" to "detection",
        "timestampMs" to timestampMs,
        "deviceAddress" to deviceAddress,
        "deviceName" to deviceName,
        "companyId" to companyId,
        "companyName" to companyName,
        "manufacturerDataHex" to manufacturerDataHex,
        "reasonCodes" to reasonCodes,
        "reasonText" to reasonText,
        "confidence" to confidence,
        "rssi" to rssi,
        "txPower" to txPower,
        "serviceUuids" to serviceUuids,
    )
}
