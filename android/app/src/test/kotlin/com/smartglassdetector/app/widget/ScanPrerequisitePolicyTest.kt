package com.smartglassdetector.app.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanPrerequisitePolicyTest {
    @Test
    fun reportsIssuesInTheOrderTheUserCanResolveThem() {
        assertEquals(
            ScanPrerequisiteIssue.SETUP_REQUIRED,
            ScanPrerequisitePolicy.issue(false, false, false, false),
        )
        assertEquals(
            ScanPrerequisiteIssue.UNSUPPORTED,
            ScanPrerequisitePolicy.issue(true, false, false, false),
        )
        assertEquals(
            ScanPrerequisiteIssue.PERMISSION_REQUIRED,
            ScanPrerequisitePolicy.issue(true, true, false, false),
        )
        assertEquals(
            ScanPrerequisiteIssue.BLUETOOTH_DISABLED,
            ScanPrerequisitePolicy.issue(true, true, true, false),
        )
    }

    @Test
    fun readyDeviceHasNoBlockingIssue() {
        assertEquals(
            ScanPrerequisiteIssue.NONE,
            ScanPrerequisitePolicy.issue(true, true, true, true),
        )
    }
}
