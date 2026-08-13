package com.smartglassdetector.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PresentationCopyTest {
    @Test
    fun alertCopyIsConfidentAndBrandNeutral() {
        val copy = listOf(
            PresentationCopy.SERVICE_STATUS,
            PresentationCopy.SMARTGLASS_ALERT_TITLE,
            PresentationCopy.MEDIA_ALERT_TITLE,
            PresentationCopy.MEDIA_ALERT_BODY,
            PresentationCopy.SMARTGLASS_FALLBACK_NAME,
            PresentationCopy.ALERT_CHANNEL_NAME,
        ).joinToString(" ")

        val forbidden = Regex(
            "\\b(possible|candidate|experimental|heuristic|best-effort|meta|ray-?ban)\\b",
            RegexOption.IGNORE_CASE,
        )
        assertFalse(forbidden.containsMatchIn(copy))
    }

    @Test
    fun internalClassifierReasonsBecomeSmartglassEvidence() {
        assertEquals(
            "Smartglass manufacturer signature",
            PresentationCopy.smartglassEvidence("Meta Company ID (0x01AB)", "0x01AB"),
        )
        assertEquals(
            "Smartglass Bluetooth service",
            PresentationCopy.smartglassEvidence("Meta service UUID (0xFD5F)", null),
        )
        assertEquals(
            "Smartglass device-name signature",
            PresentationCopy.smartglassEvidence("Device name contains ray-ban", null),
        )
    }
}
