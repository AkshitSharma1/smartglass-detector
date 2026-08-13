package com.smartglassdetector.app.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

class WifiObservationSchedulePolicyTest {
    @Test
    fun settingChangeUpdatesBothObservationDelaysButNotRecoveryWatchdog() {
        var configuredSeconds = 10
        val policy = WifiObservationSchedulePolicy { configuredSeconds }

        assertEquals(10_000L, policy.peerSampleDelayMs())
        assertEquals(10_000L, policy.networkScanDelayMs())
        assertEquals(10_000L, policy.discoveryRecoveryDelayMs)

        configuredSeconds = 3

        assertEquals(3_000L, policy.peerSampleDelayMs())
        assertEquals(3_000L, policy.networkScanDelayMs())
        assertEquals(10_000L, policy.discoveryRecoveryDelayMs)
    }

    @Test
    fun unsupportedStoredValueUsesFiveSecondDefault() {
        val policy = WifiObservationSchedulePolicy { 7 }

        assertEquals(5, policy.intervalSeconds())
        assertEquals(5_000L, policy.peerSampleDelayMs())
        assertEquals(5_000L, policy.networkScanDelayMs())
    }
}
