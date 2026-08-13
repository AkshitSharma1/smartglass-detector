package com.smartglassdetector.app.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiScanCadencePolicyTest {
    @Test
    fun preventsOverlapUntilResultsArrive() {
        val policy = WifiScanCadencePolicy(inFlightTimeoutMs = 20_000L)

        assertTrue(policy.canAttempt(1_000L))
        policy.markAttempt(nowElapsedMs = 1_000L, accepted = true)
        assertFalse(policy.canAttempt(6_000L))

        policy.markResultsAvailable()
        assertTrue(policy.canAttempt(6_001L))
    }

    @Test
    fun rejectedRequestCanRetryAtNextConfiguredTick() {
        val policy = WifiScanCadencePolicy()

        policy.markAttempt(nowElapsedMs = 1_000L, accepted = false)

        assertTrue(policy.canAttempt(4_000L))
    }

    @Test
    fun staleInFlightRequestExpires() {
        val policy = WifiScanCadencePolicy(inFlightTimeoutMs = 20_000L)
        policy.markAttempt(nowElapsedMs = 1_000L, accepted = true)

        assertFalse(policy.canAttempt(20_999L))
        assertTrue(policy.canAttempt(21_000L))
    }

    @Test
    fun resetClearsPendingRequest() {
        val policy = WifiScanCadencePolicy()
        policy.markAttempt(nowElapsedMs = 1_000L, accepted = true)

        policy.reset()

        assertTrue(policy.canAttempt(1_001L))
    }
}
