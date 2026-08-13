package com.smartglassdetector.app.scanner

import com.smartglassdetector.app.model.DetectionEvent
import com.smartglassdetector.app.model.NearbyDevice

data class RegistryUpdate(
    val devices: List<NearbyDevice>,
    val updatedDevice: NearbyDevice,
    val alertDevice: NearbyDevice?,
)

class NearbyDeviceRegistry(
    private val presenceTimeoutMs: Long = PRESENCE_TIMEOUT_MS,
    private val rearmBelowThresholdMs: Long = REARM_BELOW_THRESHOLD_MS,
    private val hysteresisDb: Int = HYSTERESIS_DB,
) {
    private data class Tracker(
        val deviceId: String,
        val estimator: SignalEstimator = SignalEstimator(),
        var latestEvent: DetectionEvent,
        var latestEstimate: SignalEstimate,
        var lastSeenMs: Long,
        var armed: Boolean = true,
        var belowThresholdSinceMs: Long? = null,
    )

    private val trackers = linkedMapOf<String, Tracker>()

    @Synchronized
    fun record(
        event: DetectionEvent,
        thresholdRssi: Int,
        nowMs: Long = event.timestampMs,
    ): RegistryUpdate {
        pruneLocked(nowMs)
        val normalizedEvent = event.copy(
            deviceName = event.deviceName?.trim()?.takeIf(String::isNotEmpty),
        )
        val deviceId = stableDeviceId(normalizedEvent)
        var tracker = trackers[deviceId]
        if (tracker == null) {
            val estimator = SignalEstimator()
            val estimate = estimator.add(normalizedEvent.rssi, normalizedEvent.txPower)
            tracker = Tracker(
                deviceId = deviceId,
                estimator = estimator,
                latestEvent = normalizedEvent,
                latestEstimate = estimate,
                lastSeenMs = nowMs,
            )
            trackers[deviceId] = tracker
        } else {
            tracker.latestEvent = normalizedEvent.copy(
                deviceName = normalizedEvent.deviceName ?: tracker.latestEvent.deviceName,
            )
            tracker.latestEstimate = tracker.estimator.add(
                normalizedEvent.rssi,
                normalizedEvent.txPower,
            )
            tracker.lastSeenMs = nowMs
        }

        val exitThreshold = thresholdRssi - hysteresisDb
        if (!tracker.armed) {
            if (tracker.latestEstimate.smoothedRssi <= exitThreshold) {
                val belowSince = tracker.belowThresholdSinceMs ?: nowMs.also {
                    tracker.belowThresholdSinceMs = it
                }
                if (nowMs - belowSince >= rearmBelowThresholdMs) {
                    tracker.armed = true
                    tracker.belowThresholdSinceMs = null
                }
            } else {
                tracker.belowThresholdSinceMs = null
            }
        }

        val updatedDevice = tracker.toNearbyDevice()
        var alertDevice: NearbyDevice? = null
        if (tracker.armed && tracker.latestEstimate.smoothedRssi >= thresholdRssi) {
            tracker.armed = false
            tracker.belowThresholdSinceMs = null
            alertDevice = updatedDevice
        }
        return RegistryUpdate(snapshotLocked(), updatedDevice, alertDevice)
    }

    @Synchronized
    fun prune(nowMs: Long): Boolean = pruneLocked(nowMs)

    @Synchronized
    fun snapshot(): List<NearbyDevice> = snapshotLocked()

    @Synchronized
    fun clear() {
        trackers.clear()
    }

    private fun pruneLocked(nowMs: Long): Boolean {
        val before = trackers.size
        trackers.entries.removeAll { (_, tracker) ->
            nowMs - tracker.lastSeenMs >= presenceTimeoutMs
        }
        return trackers.size != before
    }

    private fun snapshotLocked(): List<NearbyDevice> = trackers.values
        .map { tracker -> tracker.toNearbyDevice() }
        .sortedWith(compareBy<NearbyDevice> { it.distanceMeters }.thenBy { it.deviceId })

    private fun Tracker.toNearbyDevice(): NearbyDevice {
        val event = latestEvent
        val estimate = latestEstimate
        return NearbyDevice(
            deviceId = deviceId,
            deviceAddress = event.deviceAddress,
            deviceName = event.deviceName,
            companyId = event.companyId,
            companyName = event.companyName,
            manufacturerDataHex = event.manufacturerDataHex,
            serviceUuids = event.serviceUuids,
            reasonText = event.reasonText,
            confidence = event.confidence,
            rawRssi = estimate.rawRssi,
            smoothedRssi = estimate.smoothedRssi,
            txPower = estimate.txPower,
            distanceMeters = estimate.distanceMeters,
            distanceMinMeters = estimate.distanceMinMeters,
            distanceMaxMeters = estimate.distanceMaxMeters,
            distanceConfidence = estimate.confidence,
            sampleCount = estimate.sampleCount,
            lastSeenMs = lastSeenMs,
        )
    }

    private fun stableDeviceId(event: DetectionEvent): String {
        if (event.deviceAddress != "Unavailable") {
            return event.deviceAddress
        }
        return listOf(
            event.companyId ?: "none",
            event.deviceName ?: "unnamed",
            event.manufacturerDataHex ?: "no-payload",
        ).joinToString(separator = ":")
    }

    companion object {
        const val PRESENCE_TIMEOUT_MS = 10_000L
        const val REARM_BELOW_THRESHOLD_MS = 10_000L
        const val HYSTERESIS_DB = 6
    }
}
