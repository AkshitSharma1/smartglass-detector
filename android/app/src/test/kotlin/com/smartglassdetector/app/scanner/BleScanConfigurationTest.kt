package com.smartglassdetector.app.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BleScanConfigurationTest {
    @Test
    fun backgroundScanUsesThreeOrFilterSpecifications() {
        val specs = BleScanConfiguration.backgroundFilterSpecs

        assertEquals(3, specs.size)
        assertEquals(
            setOf(
                MetaRayBanClassifier.META_COMPANY_ID_1,
                MetaRayBanClassifier.META_COMPANY_ID_2,
            ),
            specs.mapNotNull { it.manufacturerId }.toSet(),
        )
        assertTrue(
            specs.any { it.serviceUuid == MetaRayBanClassifier.META_SERVICE_UUID },
        )
        assertTrue(specs.all { (it.manufacturerId == null) != (it.serviceUuid == null) })
    }
}
