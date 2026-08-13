package com.smartglassdetector.app.model

data class MediaTransferObservation(
    val observedName: String,
    val address: String?,
    val source: String,
    val rssi: Int?,
    val frequencyMhz: Int?,
    val channel: Int?,
    val timestampMs: Long,
    val nearbyMetaBle: Boolean = false,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "observedName" to observedName,
        "address" to address,
        "source" to source,
        "rssi" to rssi,
        "frequencyMhz" to frequencyMhz,
        "channel" to channel,
        "timestampMs" to timestampMs,
        "nearbyMetaBle" to nearbyMetaBle,
    )
}

data class MediaTransferCandidate(
    val sessionId: String,
    val observedName: String,
    val address: String?,
    val sources: List<String>,
    val rssi: Int?,
    val frequencyMhz: Int?,
    val channel: Int?,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val active: Boolean,
    val evidence: List<String>,
) {
    val durationMs: Long
        get() = (lastSeenMs - firstSeenMs).coerceAtLeast(0L)

    fun toMap(): Map<String, Any?> = mapOf(
        "sessionId" to sessionId,
        "observedName" to observedName,
        "address" to address,
        "sources" to sources,
        "rssi" to rssi,
        "frequencyMhz" to frequencyMhz,
        "channel" to channel,
        "firstSeenMs" to firstSeenMs,
        "lastSeenMs" to lastSeenMs,
        "durationMs" to durationMs,
        "active" to active,
        "evidence" to evidence,
    )
}

data class WifiDiscoveryState(
    val status: String,
    val message: String? = null,
    val updatedAtMs: Long = System.currentTimeMillis(),
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "status" to status,
        "message" to message,
        "updatedAtMs" to updatedAtMs,
    )
}
