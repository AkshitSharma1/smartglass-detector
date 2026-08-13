package com.smartglassdetector.app.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetaRayBanClassifierTest {
    @Test
    fun matchesFirstMetaCompanyIdWithoutName() {
        val result = MetaRayBanClassifier.classify(0x01AB, null)

        assertTrue(result.isMatch)
        assertEquals(listOf("meta_company_01ab"), result.reasonCodes)
    }

    @Test
    fun matchesSecondMetaCompanyIdWithoutName() {
        val result = MetaRayBanClassifier.classify(0x058E, null)

        assertTrue(result.isMatch)
        assertEquals(listOf("meta_company_058e"), result.reasonCodes)
    }

    @Test
    fun matchesMetaServiceUuidWithoutCompanyOrName() {
        val result = MetaRayBanClassifier.classify(
            companyId = null,
            deviceName = null,
            serviceUuids = listOf("0000FD5F-0000-1000-8000-00805F9B34FB"),
        )

        assertTrue(result.isMatch)
        assertEquals(listOf("meta_service_fd5f"), result.reasonCodes)
        assertEquals("Meta service UUID (0xFD5F)", result.reasonText)
    }

    @Test
    fun matchesRaybanNameWithoutCompanyIdCaseInsensitively() {
        val result = MetaRayBanClassifier.classify(null, "META RAYBAN 001")

        assertTrue(result.isMatch)
        assertEquals(listOf("name_rayban"), result.reasonCodes)
    }

    @Test
    fun matchesHyphenatedName() {
        val result = MetaRayBanClassifier.classify(null, "Meta Ray-Ban")

        assertTrue(result.isMatch)
        assertEquals(listOf("name_ray_ban"), result.reasonCodes)
    }

    @Test
    fun recordsCompanyThenOnlyFirstNameSpelling() {
        val result = MetaRayBanClassifier.classify(0x01AB, "rayban ray-ban")

        assertEquals(
            listOf("meta_company_01ab", "name_rayban"),
            result.reasonCodes,
        )
    }

    @Test
    fun recordsCompanyServiceAndNameEvidenceInStableOrder() {
        val result = MetaRayBanClassifier.classify(
            companyId = 0x01AB,
            deviceName = "Meta Ray-Ban",
            serviceUuids = listOf(MetaRayBanClassifier.META_SERVICE_UUID),
        )

        assertEquals(
            listOf("meta_company_01ab", "meta_service_fd5f", "name_ray_ban"),
            result.reasonCodes,
        )
    }

    @Test
    fun rejectsNullBlankAndNearMissNames() {
        assertFalse(MetaRayBanClassifier.classify(null, null).isMatch)
        assertFalse(MetaRayBanClassifier.classify(null, "").isMatch)
        assertFalse(MetaRayBanClassifier.classify(null, "ray ban").isMatch)
        assertFalse(MetaRayBanClassifier.classify(0x004C, "ordinary device").isMatch)
        assertFalse(
            MetaRayBanClassifier.classify(
                companyId = null,
                deviceName = null,
                serviceUuids = listOf("0000180f-0000-1000-8000-00805f9b34fb"),
            ).isMatch,
        )
    }

    @Test
    fun selectsAdvertisedNameBeforeBluetoothNameAndAlias() {
        var bluetoothNameRead = false
        var aliasRead = false
        val name = AdvertisementValues.selectName(
            advertisedName = " Advertised ",
            bluetoothNameReader = {
                bluetoothNameRead = true
                "Bluetooth name"
            },
            aliasReader = {
                aliasRead = true
                "Alias"
            },
        )

        assertEquals("Advertised", name)
        assertFalse(bluetoothNameRead)
        assertFalse(aliasRead)
    }

    @Test
    fun fallsBackToBluetoothNameBeforeAlias() {
        var aliasRead = false
        val name = AdvertisementValues.selectName(
            advertisedName = " ",
            bluetoothNameReader = { " Bluetooth name " },
            aliasReader = {
                aliasRead = true
                "Alias"
            },
        )

        assertEquals("Bluetooth name", name)
        assertFalse(aliasRead)
    }

    @Test
    fun fallsBackToAliasWhenBluetoothNameIsUnavailable() {
        assertEquals(
            "Alias",
            AdvertisementValues.selectName(
                advertisedName = null,
                bluetoothNameReader = { null },
                aliasReader = { "Alias" },
            ),
        )
    }

    @Test
    fun handlesSecurityExceptionsWhileReadingNames() {
        assertEquals(
            "Alias",
            AdvertisementValues.selectName(
                advertisedName = null,
                bluetoothNameReader = { throw SecurityException("denied") },
                aliasReader = { "Alias" },
            ),
        )
        assertNull(
            AdvertisementValues.selectName(
                advertisedName = null,
                bluetoothNameReader = { throw SecurityException("denied") },
                aliasReader = { throw SecurityException("denied") },
            ),
        )
    }

    @Test
    fun formatsCompanyAndPayloadWithoutMixingThem() {
        assertEquals("0x01AB", AdvertisementValues.formatCompanyId(0x01AB))
        assertEquals(
            "00A5FF",
            AdvertisementValues.formatPayload(byteArrayOf(0x00, 0xA5.toByte(), 0xFF.toByte())),
        )
    }
}
