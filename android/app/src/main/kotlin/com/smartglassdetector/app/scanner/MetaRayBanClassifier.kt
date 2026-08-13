package com.smartglassdetector.app.scanner

import java.util.Locale

data class Classification(
    val isMatch: Boolean,
    val reasonCodes: List<String>,
    val reasonText: String,
)

object MetaRayBanClassifier {
    const val META_COMPANY_ID_1 = 0x01AB
    const val META_COMPANY_ID_2 = 0x058E
    const val META_SERVICE_UUID = "0000fd5f-0000-1000-8000-00805f9b34fb"

    fun classify(
        companyId: Int?,
        deviceName: String?,
        serviceUuids: Collection<String> = emptyList(),
    ): Classification {
        val reasonCodes = mutableListOf<String>()
        val reasons = mutableListOf<String>()

        if (companyId == META_COMPANY_ID_1) {
            reasonCodes.add("meta_company_01ab")
            reasons.add("Meta Company ID (0x01AB)")
        }

        if (companyId == META_COMPANY_ID_2) {
            reasonCodes.add("meta_company_058e")
            reasons.add("Meta Company ID (0x058E)")
        }

        if (serviceUuids.any { it.equals(META_SERVICE_UUID, ignoreCase = true) }) {
            reasonCodes.add("meta_service_fd5f")
            reasons.add("Meta service UUID (0xFD5F)")
        }

        deviceName?.let { name ->
            val normalizedName = name.lowercase(Locale.ROOT)
            when {
                normalizedName.contains("rayban") -> {
                    reasonCodes.add("name_rayban")
                    reasons.add("Device name contains rayban")
                }

                normalizedName.contains("ray-ban") -> {
                    reasonCodes.add("name_ray_ban")
                    reasons.add("Device name contains ray-ban")
                }
            }
        }

        return Classification(
            isMatch = reasons.isNotEmpty(),
            reasonCodes = reasonCodes,
            reasonText = reasons.joinToString(", "),
        )
    }
}

object AdvertisementValues {
    fun selectName(
        advertisedName: String?,
        bluetoothNameReader: () -> String?,
        aliasReader: () -> String?,
    ): String? {
        advertisedName.visibleName()?.let { return it }
        readVisibleName(bluetoothNameReader)?.let { return it }
        return readVisibleName(aliasReader)
    }

    private fun readVisibleName(reader: () -> String?): String? = try {
        reader().visibleName()
    } catch (_: SecurityException) {
        null
    }

    private fun String?.visibleName(): String? = this?.trim()?.takeIf(String::isNotEmpty)

    fun formatCompanyId(companyId: Int): String = "0x%04X".format(Locale.US, companyId)

    fun formatPayload(payload: ByteArray): String =
        payload.joinToString(separator = "") { "%02X".format(Locale.US, it.toInt() and 0xFF) }

    fun companyName(companyId: Int?): String? = when (companyId) {
        MetaRayBanClassifier.META_COMPANY_ID_1 -> "Meta Platforms, Inc."
        MetaRayBanClassifier.META_COMPANY_ID_2 -> "Meta Platforms Technologies, LLC"
        null -> null
        else -> "Unknown company"
    }
}
