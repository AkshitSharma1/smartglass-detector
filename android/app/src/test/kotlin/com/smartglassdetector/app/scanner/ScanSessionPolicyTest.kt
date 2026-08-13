package com.smartglassdetector.app.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanSessionPolicyTest {
    @Test
    fun restoresRequestedSessionWhenActivityReturnsAndServiceIsGone() {
        assertTrue(
            ScanSessionPolicy.shouldStartService(
                scanningRequested = true,
                serviceAlive = false,
                permissionsGranted = true,
                bluetoothEnabled = true,
            ),
        )
    }

    @Test
    fun doesNotRestoreStoppedBlockedOrAlreadyRunningSession() {
        assertFalse(
            ScanSessionPolicy.shouldStartService(false, false, true, true),
        )
        assertFalse(
            ScanSessionPolicy.shouldStartService(true, true, true, true),
        )
        assertFalse(
            ScanSessionPolicy.shouldStartService(true, false, false, true),
        )
        assertFalse(
            ScanSessionPolicy.shouldStartService(true, false, true, false),
        )
    }
}
