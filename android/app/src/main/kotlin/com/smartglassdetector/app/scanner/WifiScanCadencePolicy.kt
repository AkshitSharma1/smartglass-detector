package com.smartglassdetector.app.scanner

class WifiScanCadencePolicy(
    private val inFlightTimeoutMs: Long = DEFAULT_IN_FLIGHT_TIMEOUT_MS,
) {
    private var inFlightSinceMs: Long? = null

    fun canAttempt(nowElapsedMs: Long): Boolean {
        val startedAt = inFlightSinceMs ?: return true
        if (nowElapsedMs - startedAt >= inFlightTimeoutMs) {
            inFlightSinceMs = null
            return true
        }
        return false
    }

    fun markAttempt(nowElapsedMs: Long, accepted: Boolean) {
        inFlightSinceMs = if (accepted) nowElapsedMs else null
    }

    fun markResultsAvailable() {
        inFlightSinceMs = null
    }

    fun reset() {
        inFlightSinceMs = null
    }

    companion object {
        const val DEFAULT_IN_FLIGHT_TIMEOUT_MS = 20_000L
    }
}
