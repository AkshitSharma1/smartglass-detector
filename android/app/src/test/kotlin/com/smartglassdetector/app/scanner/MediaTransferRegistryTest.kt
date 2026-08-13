package com.smartglassdetector.app.scanner

import com.smartglassdetector.app.model.MediaTransferObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaTransferRegistryTest {
    @Test
    fun matcherPreservesTheStrictP2pCandidatePattern() {
        assertTrue(MediaTransferMatcher.matches("DIRECT-FB-rqsk"))
        assertFalse(MediaTransferMatcher.matches("DIRECT-FB-RQSK"))
        assertFalse(MediaTransferMatcher.matches("DIRECT-XY-rqsk"))
        assertFalse(MediaTransferMatcher.matches("DIRECT-FB-rqs"))
        assertFalse(MediaTransferMatcher.matches("DIRECT-FB-rqsk-extra"))
    }

    @Test
    fun matcherAcceptsCaseInsensitiveSoftApPrefixes() {
        assertTrue(MediaTransferMatcher.matches("rb meta"))
        assertTrue(MediaTransferMatcher.matches("RB META 1234"))
        assertTrue(MediaTransferMatcher.matches("Meta RB-abcd"))
        assertTrue(MediaTransferMatcher.matches("  mEtA rB Camera  "))
    }

    @Test
    fun matcherRejectsInvalidOrNonPrefixSoftApNames() {
        assertFalse(MediaTransferMatcher.matches("Nearby rb meta"))
        assertFalse(MediaTransferMatcher.matches("Nearby meta rb"))
        assertFalse(MediaTransferMatcher.matches("meta"))
        assertFalse(MediaTransferMatcher.matches("rb-meta"))
        assertFalse(MediaTransferMatcher.matches("rbmeta"))
        assertFalse(MediaTransferMatcher.matches("Unrelated camera"))
    }

    @Test
    fun acceptsSoftApNameFromWifiScanThroughExistingRegistryFlow() {
        val registry = MediaTransferRegistry()

        val update = registry.record(
            observation(
                name = "RB Meta 1234",
                source = WifiMediaTransferObserver.SOURCE_WIFI_SCAN,
            ),
        )

        assertNotNull(update?.alertCandidate)
        val candidate = update!!.candidates.single()
        assertEquals("RB Meta 1234", candidate.observedName)
        assertEquals(listOf("wifiScan"), candidate.sources)
    }

    @Test
    fun rejectsSupportingEvidenceWithoutANameMatch() {
        val registry = MediaTransferRegistry()

        val update = registry.record(
            observation(
                name = "Unrelated camera",
                source = WifiMediaTransferObserver.SOURCE_WIFI_SCAN,
                frequencyMhz = 5_180,
                nearbyMetaBle = true,
            ),
        )

        assertNull(update)
        assertTrue(registry.snapshot().isEmpty())
    }

    @Test
    fun mergesSourcesAndSupportingEvidenceIntoOneSession() {
        val registry = MediaTransferRegistry()

        val first = registry.record(observation(timestampMs = 1_000L))
        val merged = registry.record(
            observation(
                source = WifiMediaTransferObserver.SOURCE_WIFI_SCAN,
                timestampMs = 2_000L,
                frequencyMhz = 5_180,
                nearbyMetaBle = true,
            ),
        )

        assertNotNull(first?.alertCandidate)
        assertNull(merged?.alertCandidate)
        val candidate = merged!!.candidates.single()
        assertEquals(listOf("wifiP2p", "wifiScan"), candidate.sources)
        assertTrue(candidate.evidence.contains("Observed on 5 GHz"))
        assertTrue(candidate.evidence.contains("Nearby smartglass Bluetooth signal detected"))
        assertTrue(candidate.evidence.contains("Observed through Wi-Fi Direct and Wi-Fi scan"))
        assertEquals(1_000L, candidate.durationMs)
    }

    @Test
    fun repeatedPeerSamplesIncreaseConfirmedObservationDuration() {
        val registry = MediaTransferRegistry()

        registry.record(observation(timestampMs = 1_000L))
        registry.record(observation(timestampMs = 4_000L))
        val latest = registry.record(observation(timestampMs = 7_000L))

        assertEquals(6_000L, latest?.candidates?.single()?.durationMs)
    }

    @Test
    fun alertsOnceAndCreatesANewSessionAfterTwentySecondsAbsent() {
        val registry = MediaTransferRegistry()

        assertNotNull(registry.record(observation(timestampMs = 1_000L))?.alertCandidate)
        assertNull(registry.record(observation(timestampMs = 5_000L))?.alertCandidate)
        assertTrue(registry.tick(25_000L))

        val returned = registry.record(observation(timestampMs = 25_100L))

        assertNotNull(returned?.alertCandidate)
        assertEquals(2, returned?.candidates?.size)
        assertEquals(1, returned?.candidates?.count { it.active })
    }

    @Test
    fun retainsMultipleSessionsForFiveMinutesThenExpiresThem() {
        val registry = MediaTransferRegistry()
        registry.record(observation(name = "DIRECT-FB-rqsk", timestampMs = 1_000L))
        registry.record(observation(name = "DIRECT-FB-evwj", timestampMs = 2_000L))

        assertEquals(2, registry.snapshot().size)
        assertTrue(registry.tick(22_000L))
        assertFalse(registry.tick(300_999L))
        assertTrue(registry.tick(302_000L))
        assertTrue(registry.snapshot().isEmpty())
    }

    @Test
    fun convertsObservedFrequenciesToChannels() {
        assertEquals(36, WifiMediaTransferObserver.channelForFrequency(5_180))
        assertEquals(149, WifiMediaTransferObserver.channelForFrequency(5_745))
        assertEquals(14, WifiMediaTransferObserver.channelForFrequency(2_484))
        assertNull(WifiMediaTransferObserver.channelForFrequency(9_000))
    }

    private fun observation(
        name: String = "DIRECT-FB-rqsk",
        source: String = WifiMediaTransferObserver.SOURCE_WIFI_P2P,
        timestampMs: Long = 1_000L,
        frequencyMhz: Int? = null,
        nearbyMetaBle: Boolean = false,
    ) = MediaTransferObservation(
        observedName = name,
        address = "12:34:56:78:90:AB",
        source = source,
        rssi = if (frequencyMhz == null) null else -52,
        frequencyMhz = frequencyMhz,
        channel = frequencyMhz?.let(WifiMediaTransferObserver::channelForFrequency),
        timestampMs = timestampMs,
        nearbyMetaBle = nearbyMetaBle,
    )
}
