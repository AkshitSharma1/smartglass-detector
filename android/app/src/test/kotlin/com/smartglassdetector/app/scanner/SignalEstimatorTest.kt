package com.smartglassdetector.app.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalEstimatorTest {
    @Test
    fun fallbackDistanceIsLowConfidenceAndGetsFartherForWeakerSignal() {
        val close = SignalEstimator().add(-59, null)
        val far = SignalEstimator().add(-80, null)

        assertEquals("low", close.confidence)
        assertTrue(far.distanceMeters > close.distanceMeters)
        assertTrue(close.distanceMinMeters <= close.distanceMeters)
        assertTrue(close.distanceMaxMeters >= close.distanceMeters)
    }

    @Test
    fun advertisedTxPowerCanReachHighConfidenceWithStableSamples() {
        val estimator = SignalEstimator()
        estimator.add(-60, 0)
        estimator.add(-60, 0)
        val estimate = estimator.add(-60, 0)

        assertEquals("high", estimate.confidence)
        assertEquals(3, estimate.sampleCount)
    }
}
