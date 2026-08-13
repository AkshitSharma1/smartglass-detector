package com.smartglassdetector.app.scanner

import com.smartglassdetector.app.model.NearbyDevice

class RecentDeviceRegistry(
    private val retentionMs: Long = RETENTION_MS,
) {
    private val devices = linkedMapOf<String, NearbyDevice>()

    @Synchronized
    fun record(device: NearbyDevice, nowMs: Long = device.lastSeenMs): List<NearbyDevice> {
        pruneLocked(nowMs)
        val key = if (device.deviceAddress == "Unavailable") {
            device.deviceId
        } else {
            device.deviceAddress
        }
        devices[key] = device
        return snapshotLocked()
    }

    @Synchronized
    fun prune(nowMs: Long): Boolean = pruneLocked(nowMs)

    @Synchronized
    fun snapshot(): List<NearbyDevice> = snapshotLocked()

    @Synchronized
    fun snapshot(nowMs: Long): List<NearbyDevice> {
        pruneLocked(nowMs)
        return snapshotLocked()
    }

    @Synchronized
    fun clear() {
        devices.clear()
    }

    private fun pruneLocked(nowMs: Long): Boolean {
        val before = devices.size
        devices.entries.removeAll { (_, device) ->
            nowMs - device.lastSeenMs >= retentionMs
        }
        return devices.size != before
    }

    private fun snapshotLocked(): List<NearbyDevice> = devices.values
        .sortedWith(compareByDescending<NearbyDevice> { it.lastSeenMs }.thenBy { it.deviceId })

    companion object {
        const val RETENTION_MS = 5 * 60 * 1_000L
    }
}
