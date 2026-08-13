package com.smartglassdetector.app.scanner

class WifiObservationSchedulePolicy(
    private val configuredIntervalSeconds: () -> Int,
) {
    fun intervalSeconds(): Int =
        configuredIntervalSeconds().takeIf { it in SUPPORTED_INTERVALS_SECONDS }
            ?: DEFAULT_INTERVAL_SECONDS

    fun peerSampleDelayMs(): Long = intervalSeconds() * 1_000L

    fun networkScanDelayMs(): Long = intervalSeconds() * 1_000L

    val discoveryRecoveryDelayMs: Long
        get() = DISCOVERY_RECOVERY_DELAY_MS

    companion object {
        const val DEFAULT_INTERVAL_SECONDS = 5
        const val DISCOVERY_RECOVERY_DELAY_MS = 10_000L
        private val SUPPORTED_INTERVALS_SECONDS = setOf(3, 5, 10, 15)
    }
}
