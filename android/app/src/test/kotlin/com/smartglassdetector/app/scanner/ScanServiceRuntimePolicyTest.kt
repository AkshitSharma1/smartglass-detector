package com.smartglassdetector.app.scanner

import android.app.Service
import android.content.pm.ServiceInfo
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanServiceRuntimePolicyTest {
    @Test
    fun activatesConnectedDeviceAndLocationTypesWhenAvailable() {
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            ScanServiceRuntimePolicy.foregroundServiceTypes(
                sdkInt = Build.VERSION_CODES.S,
                foregroundLocationAvailable = true,
            ),
        )
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            ScanServiceRuntimePolicy.foregroundServiceTypes(
                sdkInt = Build.VERSION_CODES.S,
                foregroundLocationAvailable = false,
            ),
        )
        assertEquals(
            0,
            ScanServiceRuntimePolicy.foregroundServiceTypes(
                sdkInt = Build.VERSION_CODES.P,
                foregroundLocationAvailable = true,
            ),
        )
    }

    @Test
    fun android12AndLaterWaitForAVisibleActivityAfterProcessDeath() {
        assertFalse(
            ScanServiceRuntimePolicy.shouldRestoreAfterProcessRecreation(
                sdkInt = Build.VERSION_CODES.S,
                scanningRequested = true,
            ),
        )
        assertEquals(
            Service.START_NOT_STICKY,
            ScanServiceRuntimePolicy.restartMode(
                sdkInt = Build.VERSION_CODES.S,
                scanningRequested = true,
            ),
        )
    }

    @Test
    fun olderAndroidRetainsExistingBackgroundLocationRecovery() {
        assertTrue(
            ScanServiceRuntimePolicy.shouldRestoreAfterProcessRecreation(
                sdkInt = Build.VERSION_CODES.R,
                scanningRequested = true,
            ),
        )
        assertEquals(
            Service.START_STICKY,
            ScanServiceRuntimePolicy.restartMode(
                sdkInt = Build.VERSION_CODES.R,
                scanningRequested = true,
            ),
        )
    }
}
