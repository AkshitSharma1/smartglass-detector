package com.smartglassdetector.app.scanner

import com.smartglassdetector.app.model.NearbyDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentDeviceRegistryTest {
    @Test
    fun deduplicatesByDeviceAndOrdersByLastSeen() {
        val registry = RecentDeviceRegistry()

        registry.record(device("AA", lastSeenMs = 1_000L), nowMs = 1_000L)
        registry.record(device("BB", lastSeenMs = 2_000L), nowMs = 2_000L)
        val snapshot = registry.record(
            device("AA", lastSeenMs = 3_000L, rssi = -48),
            nowMs = 3_000L,
        )

        assertEquals(listOf("AA", "BB"), snapshot.map { it.deviceAddress })
        assertEquals(2, snapshot.size)
        assertEquals(-48, snapshot.first().rawRssi)
        assertEquals(3_000L, snapshot.first().lastSeenMs)
    }

    @Test
    fun retainsUntilFiveMinutesThenExpires() {
        val registry = RecentDeviceRegistry()
        registry.record(device("AA", lastSeenMs = 1_000L), nowMs = 1_000L)

        assertFalse(registry.prune(300_999L))
        assertEquals(1, registry.snapshot().size)
        assertTrue(registry.prune(301_000L))
        assertTrue(registry.snapshot().isEmpty())
    }

    @Test
    fun clearRemovesTheCurrentSession() {
        val registry = RecentDeviceRegistry()
        registry.record(device("AA", lastSeenMs = 1_000L), nowMs = 1_000L)

        registry.clear()

        assertTrue(registry.snapshot().isEmpty())
    }

    private fun device(
        address: String,
        lastSeenMs: Long,
        rssi: Int = -65,
    ) = NearbyDevice(
        deviceId = address,
        deviceAddress = address,
        deviceName = "Meta Ray-Ban",
        companyId = "0x01AB",
        companyName = "Meta Platforms, Inc.",
        manufacturerDataHex = "A0FF",
        serviceUuids = emptyList(),
        reasonText = "Meta Company ID (0x01AB)",
        confidence = "medium",
        rawRssi = rssi,
        smoothedRssi = rssi.toDouble(),
        txPower = null,
        distanceMeters = 2.5,
        distanceMinMeters = 1.2,
        distanceMaxMeters = 5.1,
        distanceConfidence = "low",
        sampleCount = 1,
        lastSeenMs = lastSeenMs,
    )
}
