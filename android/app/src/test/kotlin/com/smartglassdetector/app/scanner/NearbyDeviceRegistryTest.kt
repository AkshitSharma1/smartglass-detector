package com.smartglassdetector.app.scanner

import com.smartglassdetector.app.model.DetectionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyDeviceRegistryTest {
    @Test
    fun keepsMultipleDevicesRegardlessOfThreshold() {
        val registry = NearbyDeviceRegistry()

        registry.record(event("AA", -60), thresholdRssi = -75, nowMs = 1_000L)
        val update = registry.record(event("BB", -95), thresholdRssi = -75, nowMs = 1_100L)

        assertEquals(2, update.devices.size)
        assertTrue(update.devices.any { it.deviceAddress == "BB" && it.smoothedRssi < -75 })
    }

    @Test
    fun alertsOnceUntilTenSecondsBelowThresholdWithHysteresis() {
        val registry = NearbyDeviceRegistry()

        assertNotNull(registry.record(event("AA", -60), -75, 1_000L).alertDevice)
        assertNull(registry.record(event("AA", -60), -75, 2_000L).alertDevice)
        (3_000L..8_000L step 1_000L).forEach { time ->
            assertNull(registry.record(event("AA", -90), -75, time).alertDevice)
        }
        assertNull(registry.record(event("AA", -90), -75, 17_999L).alertDevice)
        assertNull(registry.record(event("AA", -90), -75, 18_000L).alertDevice)

        var returnAlert = registry.record(event("AA", -50), -75, 18_100L).alertDevice
        var time = 18_200L
        while (returnAlert == null && time <= 20_000L) {
            returnAlert = registry.record(event("AA", -50), -75, time).alertDevice
            time += 100L
        }
        assertNotNull(returnAlert)
    }

    @Test
    fun absenceExpiresAndReturnAlertsAgain() {
        val registry = NearbyDeviceRegistry()

        assertNotNull(registry.record(event("AA", -60), -75, 1_000L).alertDevice)
        assertTrue(registry.prune(11_000L))
        assertEquals(0, registry.snapshot().size)
        assertNotNull(registry.record(event("AA", -60), -75, 12_000L).alertDevice)
    }

    @Test
    fun retainsLastKnownNameWhenLaterAdvertisementOmitsIt() {
        val registry = NearbyDeviceRegistry()

        registry.record(
            event("AA", -60, deviceName = "Meta Ray-Ban"),
            thresholdRssi = -75,
            nowMs = 1_000L,
        )
        val update = registry.record(
            event("AA", -55, deviceName = null),
            thresholdRssi = -75,
            nowMs = 2_000L,
        )

        assertEquals("Meta Ray-Ban", update.updatedDevice.deviceName)
        assertEquals("Meta Ray-Ban", update.devices.single().deviceName)
        assertEquals("Meta Ray-Ban", registry.snapshot().single().deviceName)
        assertEquals(-55, update.updatedDevice.rawRssi)
    }

    @Test
    fun treatsBlankNameAsMissingAndAcceptsLaterNonBlankName() {
        val registry = NearbyDeviceRegistry()

        registry.record(
            event("AA", -60, deviceName = "Meta Ray-Ban"),
            thresholdRssi = -75,
            nowMs = 1_000L,
        )
        val blankUpdate = registry.record(
            event("AA", -58, deviceName = "   "),
            thresholdRssi = -75,
            nowMs = 2_000L,
        )
        val renamedUpdate = registry.record(
            event("AA", -56, deviceName = " New smartglass name "),
            thresholdRssi = -75,
            nowMs = 3_000L,
        )

        assertEquals("Meta Ray-Ban", blankUpdate.updatedDevice.deviceName)
        assertEquals("New smartglass name", renamedUpdate.updatedDevice.deviceName)
        assertEquals("New smartglass name", registry.snapshot().single().deviceName)
    }

    private fun event(
        address: String,
        rssi: Int,
        deviceName: String? = "Meta Ray-Ban",
    ) = DetectionEvent(
        timestampMs = 0L,
        deviceAddress = address,
        deviceName = deviceName,
        companyId = "0x01AB",
        companyName = "Meta Platforms, Inc.",
        manufacturerDataHex = "A0FF",
        reasonCodes = listOf("meta_company_01ab"),
        reasonText = "Meta Company ID (0x01AB)",
        confidence = "medium",
        rssi = rssi,
        txPower = null,
        serviceUuids = emptyList(),
    )
}
