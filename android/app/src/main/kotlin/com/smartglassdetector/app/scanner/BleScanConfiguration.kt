package com.smartglassdetector.app.scanner

import android.bluetooth.le.ScanFilter
import android.os.ParcelUuid

data class BleScanFilterSpec(
    val manufacturerId: Int? = null,
    val serviceUuid: String? = null,
)

object BleScanConfiguration {
    val backgroundFilterSpecs: List<BleScanFilterSpec> = listOf(
        BleScanFilterSpec(manufacturerId = MetaRayBanClassifier.META_COMPANY_ID_1),
        BleScanFilterSpec(manufacturerId = MetaRayBanClassifier.META_COMPANY_ID_2),
        BleScanFilterSpec(serviceUuid = MetaRayBanClassifier.META_SERVICE_UUID),
    )

    fun backgroundFilters(): List<ScanFilter> = backgroundFilterSpecs.map { spec ->
        when {
            spec.manufacturerId != null -> ScanFilter.Builder()
                // A non-null, zero-length payload matches any payload for this company ID.
                .setManufacturerData(spec.manufacturerId, byteArrayOf())
                .build()

            spec.serviceUuid != null -> ScanFilter.Builder()
                .setServiceUuid(ParcelUuid.fromString(spec.serviceUuid))
                .build()

            else -> error("A BLE scan filter must contain an identifier")
        }
    }
}
