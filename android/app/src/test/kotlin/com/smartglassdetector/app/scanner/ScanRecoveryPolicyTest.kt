package com.smartglassdetector.app.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanRecoveryPolicyTest {
    @Test
    fun classifiesRetryAndPrerequisiteFailures() {
        val policy = ScanRecoveryPolicy()

        assertEquals(ScanFailureAction.RETRY, policy.actionFor(errorCode = 3))
        assertEquals(
            ScanFailureAction.WAIT_FOR_PREREQUISITE,
            policy.actionFor(BluetoothScanner.ERROR_DISABLED),
        )
        assertEquals(
            ScanFailureAction.WAIT_FOR_PREREQUISITE,
            policy.actionFor(BluetoothScanner.ERROR_PERMISSION),
        )
        assertEquals(
            ScanFailureAction.STOP_RETRYING,
            policy.actionFor(BluetoothScanner.ERROR_UNSUPPORTED),
        )
        assertEquals(
            ScanFailureAction.STOP_RETRYING,
            policy.actionFor(BluetoothScanner.ERROR_FEATURE_UNSUPPORTED),
        )
    }

    @Test
    fun increasesRetryDelayAndCapsAtThirtySeconds() {
        val policy = ScanRecoveryPolicy()

        assertEquals(2_000L, policy.nextDelayMs(errorCode = 3))
        assertEquals(5_000L, policy.nextDelayMs(errorCode = 3))
        assertEquals(15_000L, policy.nextDelayMs(errorCode = 3))
        assertEquals(30_000L, policy.nextDelayMs(errorCode = 3))
        assertEquals(30_000L, policy.nextDelayMs(errorCode = 3))
    }

    @Test
    fun scanningTooFrequentlyAlwaysWaitsAtLeastThirtySeconds() {
        val policy = ScanRecoveryPolicy()

        assertEquals(
            30_000L,
            policy.nextDelayMs(BluetoothScanner.ERROR_SCANNING_TOO_FREQUENTLY),
        )
    }

    @Test
    fun stableScanResetsBackoff() {
        val policy = ScanRecoveryPolicy()
        policy.nextDelayMs(errorCode = 3)
        policy.nextDelayMs(errorCode = 3)

        policy.markStable()

        assertEquals(2_000L, policy.nextDelayMs(errorCode = 3))
    }
}
