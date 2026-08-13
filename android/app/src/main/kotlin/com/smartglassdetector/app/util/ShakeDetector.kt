package com.smartglassdetector.app.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class ShakeGestureDetector(
    private val thresholdG: Double = 2.2,
    private val requiredPeaks: Int = 2,
    private val windowMs: Long = 800L,
    private val minimumPeakGapMs: Long = 120L,
) {
    private var firstPeakMs = 0L
    private var lastPeakMs = 0L
    private var peakCount = 0

    fun onSample(accelerationG: Double, timestampMs: Long): Boolean {
        if (peakCount > 0 && timestampMs - firstPeakMs > windowMs) {
            reset()
        }
        if (accelerationG < thresholdG || timestampMs - lastPeakMs < minimumPeakGapMs) {
            return false
        }
        if (peakCount == 0) {
            firstPeakMs = timestampMs
        }
        lastPeakMs = timestampMs
        peakCount += 1
        if (peakCount < requiredPeaks) {
            return false
        }
        reset()
        return true
    }

    private fun reset() {
        firstPeakMs = 0L
        lastPeakMs = 0L
        peakCount = 0
    }
}

class ShakeDetector(
    context: Context,
    private val onShake: () -> Unit,
) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gestureDetector = ShakeGestureDetector()
    private var listening = false

    fun start() {
        if (listening || accelerometer == null) {
            return
        }
        listening = sensorManager.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_GAME,
        )
    }

    fun stop() {
        if (!listening) {
            return
        }
        sensorManager.unregisterListener(this)
        listening = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitudeG = sqrt((x * x + y * y + z * z).toDouble()) / SensorManager.GRAVITY_EARTH
        if (gestureDetector.onSample(magnitudeG, event.timestamp / 1_000_000L)) {
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
