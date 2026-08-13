package com.smartglassdetector.app.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartglassWidgetStateReducerTest {
    @Test
    fun stoppedReadyWidgetOffersStart() {
        val viewState = SmartglassWidgetStateReducer.reduce(
            SmartglassWidgetSnapshot(),
            serviceAlive = false,
        )

        assertEquals(SmartglassWidgetStatus.STOPPED, viewState.status)
        assertEquals(SmartglassWidgetAction.START, viewState.action)
        assertEquals(0, viewState.recentCount)
    }

    @Test
    fun scanningWidgetShowsRecentCountAndStop() {
        val viewState = SmartglassWidgetStateReducer.reduce(
            SmartglassWidgetSnapshot(
                scannerState = SmartglassWidgetScannerState.SCANNING,
                scanningRequested = true,
                recentCount = 3,
            ),
            serviceAlive = true,
        )

        assertEquals(SmartglassWidgetStatus.SCANNING_DETECTED, viewState.status)
        assertEquals(SmartglassWidgetAction.STOP, viewState.action)
        assertEquals(SmartglassWidgetTone.DANGER, viewState.tone)
        assertEquals(3, viewState.recentCount)
    }

    @Test
    fun missingPrerequisiteOpensAppWithoutStarting() {
        val viewState = SmartglassWidgetStateReducer.reduce(
            SmartglassWidgetSnapshot(issue = SmartglassWidgetIssue.PERMISSION_REQUIRED),
            serviceAlive = false,
        )

        assertEquals(SmartglassWidgetStatus.PERMISSION_REQUIRED, viewState.status)
        assertEquals(SmartglassWidgetAction.OPEN_APP, viewState.action)
    }

    @Test
    fun requestedSessionCanAlwaysBeStoppedWhenBlocked() {
        val viewState = SmartglassWidgetStateReducer.reduce(
            SmartglassWidgetSnapshot(
                scannerState = SmartglassWidgetScannerState.ERROR,
                scanningRequested = true,
                issue = SmartglassWidgetIssue.BLUETOOTH_DISABLED,
            ),
            serviceAlive = true,
        )

        assertEquals(SmartglassWidgetStatus.BLUETOOTH_DISABLED, viewState.status)
        assertEquals(SmartglassWidgetAction.STOP, viewState.action)
    }

    @Test
    fun deadServiceSuppressesStaleCountAndRequiresVisibleResume() {
        val viewState = SmartglassWidgetStateReducer.reduce(
            SmartglassWidgetSnapshot(
                scannerState = SmartglassWidgetScannerState.SCANNING,
                scanningRequested = true,
                recentCount = 4,
            ),
            serviceAlive = false,
        )

        assertEquals(SmartglassWidgetStatus.PAUSED, viewState.status)
        assertEquals(SmartglassWidgetAction.OPEN_APP, viewState.action)
        assertEquals(0, viewState.recentCount)
    }

    @Test
    fun stoppedSessionNeverShowsPersistedCount() {
        val viewState = SmartglassWidgetStateReducer.reduce(
            SmartglassWidgetSnapshot(recentCount = 7),
            serviceAlive = true,
        )

        assertEquals(0, viewState.recentCount)
    }

    @Test
    fun startActionRoutesThroughVisibleActivity() {
        assertEquals(
            SmartglassWidgetActionTarget.VISIBLE_ACTIVITY_START,
            SmartglassWidgetActionRouting.target(SmartglassWidgetAction.START),
        )
        assertEquals(
            SmartglassWidgetActionTarget.STOP_BROADCAST,
            SmartglassWidgetActionRouting.target(SmartglassWidgetAction.STOP),
        )
    }
}
