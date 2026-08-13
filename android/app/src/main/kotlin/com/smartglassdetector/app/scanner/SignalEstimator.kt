package com.smartglassdetector.app.scanner

import java.util.ArrayDeque
import kotlin.math.pow
import kotlin.math.sqrt

data class SignalEstimate(
    val rawRssi: Int,
    val smoothedRssi: Double,
    val txPower: Int?,
    val distanceMeters: Double,
    val distanceMinMeters: Double,
    val distanceMaxMeters: Double,
    val confidence: String,
    val sampleCount: Int,
)

class SignalEstimator {
    private val recentRssi = ArrayDeque<Int>()
    private var smoothedRssi: Double? = null
    private var latestTxPower: Int? = null
    private var sampleCount = 0

    fun add(rssi: Int, txPower: Int?): SignalEstimate {
        recentRssi.addLast(rssi)
        if (recentRssi.size > WINDOW_SIZE) {
            recentRssi.removeFirst()
        }
        if (txPower != null) {
            latestTxPower = txPower
        }
        sampleCount += 1

        val sorted = recentRssi.sorted()
        val median = if (sorted.size % 2 == 0) {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        } else {
            sorted[sorted.size / 2].toDouble()
        }
        val filtered = smoothedRssi?.let { previous ->
            EMA_ALPHA * median + (1.0 - EMA_ALPHA) * previous
        } ?: median
        smoothedRssi = filtered

        val sourcePower = latestTxPower
        val numerator = if (sourcePower != null) {
            (sourcePower - filtered) - REFERENCE_PATH_LOSS_AT_ONE_METER_DB
        } else {
            FALLBACK_RSSI_AT_ONE_METER_DB - filtered
        }
        val center = distanceFor(numerator, DEFAULT_PATH_LOSS_EXPONENT)
        val candidateA = distanceFor(numerator, MIN_PATH_LOSS_EXPONENT)
        val candidateB = distanceFor(numerator, MAX_PATH_LOSS_EXPONENT)
        val spread = standardDeviation()
        val variability = (1.0 + spread / 18.0).coerceIn(1.0, 1.8)
        val lower = (minOf(candidateA, candidateB, center) / variability).coerceIn(MIN_DISTANCE, MAX_DISTANCE)
        val upper = (maxOf(candidateA, candidateB, center) * variability).coerceIn(MIN_DISTANCE, MAX_DISTANCE)
        val confidence = when {
            sourcePower == null -> "low"
            sampleCount < 3 -> "low"
            spread <= 4.0 -> "high"
            else -> "medium"
        }

        return SignalEstimate(
            rawRssi = rssi,
            smoothedRssi = filtered,
            txPower = sourcePower,
            distanceMeters = center,
            distanceMinMeters = lower,
            distanceMaxMeters = upper,
            confidence = confidence,
            sampleCount = sampleCount,
        )
    }

    private fun distanceFor(numerator: Double, exponent: Double): Double =
        10.0.pow(numerator / (10.0 * exponent)).coerceIn(MIN_DISTANCE, MAX_DISTANCE)

    private fun standardDeviation(): Double {
        if (recentRssi.size < 2) {
            return 0.0
        }
        val mean = recentRssi.average()
        val variance = recentRssi.sumOf { value ->
            val difference = value - mean
            difference * difference
        } / recentRssi.size
        return sqrt(variance)
    }

    companion object {
        private const val WINDOW_SIZE = 7
        private const val EMA_ALPHA = 0.35
        private const val DEFAULT_PATH_LOSS_EXPONENT = 2.2
        private const val MIN_PATH_LOSS_EXPONENT = 1.8
        private const val MAX_PATH_LOSS_EXPONENT = 3.5
        private const val REFERENCE_PATH_LOSS_AT_ONE_METER_DB = 40.0
        private const val FALLBACK_RSSI_AT_ONE_METER_DB = -59.0
        private const val MIN_DISTANCE = 0.1
        private const val MAX_DISTANCE = 100.0
    }
}
