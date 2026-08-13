package com.smartglassdetector.app.widget

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class SmartglassWidgetUpdateTargetsTest {
    @Test
    fun updatesEveryInstalledWidgetWhenNoSubsetIsRequested() {
        assertArrayEquals(
            intArrayOf(4, 8, 15),
            SmartglassWidgetUpdateTargets.resolve(null, intArrayOf(4, 8, 15)),
        )
    }

    @Test
    fun updatesOnlyDistinctRequestedWidgetIds() {
        assertArrayEquals(
            intArrayOf(16, 23),
            SmartglassWidgetUpdateTargets.resolve(
                intArrayOf(16, 23, 16),
                intArrayOf(4, 8, 15, 16, 23, 42),
            ),
        )
    }
}
