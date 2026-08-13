package com.smartglassdetector.app.scanner

import com.smartglassdetector.app.model.MediaTransferCandidate
import com.smartglassdetector.app.model.MediaTransferObservation

object MediaTransferMatcher {
    private val candidatePattern = Regex("^DIRECT-FB-[a-z]{4}$")

    fun matches(value: String): Boolean {
        val name = value.trim()
        return candidatePattern.matches(name) ||
            name.startsWith("rb meta", ignoreCase = true) ||
            name.startsWith("meta rb", ignoreCase = true)
    }
}

data class MediaTransferRegistryUpdate(
    val candidates: List<MediaTransferCandidate>,
    val alertCandidate: MediaTransferCandidate?,
)

class MediaTransferRegistry(
    private val absenceTimeoutMs: Long = ABSENCE_TIMEOUT_MS,
    private val retentionMs: Long = RETENTION_MS,
) {
    private data class Session(
        val sessionId: String,
        val observedName: String,
        var address: String?,
        val sources: LinkedHashSet<String>,
        var rssi: Int?,
        var frequencyMhz: Int?,
        var channel: Int?,
        val firstSeenMs: Long,
        var lastSeenMs: Long,
        var active: Boolean,
        var nearbyMetaBle: Boolean,
    )

    private val sessions = mutableListOf<Session>()
    private var sessionSequence = 0L

    @Synchronized
    fun record(observation: MediaTransferObservation): MediaTransferRegistryUpdate? {
        val name = observation.observedName.trim()
        if (!MediaTransferMatcher.matches(name)) {
            return null
        }

        finalizeAndPruneLocked(observation.timestampMs)
        var session = sessions.lastOrNull { it.active && it.observedName == name }
        val isNewSession = session == null
        if (session == null) {
            sessionSequence += 1
            session = Session(
                sessionId = "$name:${observation.timestampMs}:$sessionSequence",
                observedName = name,
                address = observation.address,
                sources = linkedSetOf(observation.source),
                rssi = observation.rssi,
                frequencyMhz = observation.frequencyMhz,
                channel = observation.channel,
                firstSeenMs = observation.timestampMs,
                lastSeenMs = observation.timestampMs,
                active = true,
                nearbyMetaBle = observation.nearbyMetaBle,
            )
            sessions.add(session)
        } else {
            if (!observation.address.isNullOrBlank()) {
                session.address = observation.address
            }
            session.sources.add(observation.source)
            observation.rssi?.let { session.rssi = it }
            observation.frequencyMhz?.let { session.frequencyMhz = it }
            observation.channel?.let { session.channel = it }
            session.lastSeenMs = maxOf(session.lastSeenMs, observation.timestampMs)
            session.nearbyMetaBle = session.nearbyMetaBle || observation.nearbyMetaBle
        }

        val updated = session.toCandidate()
        return MediaTransferRegistryUpdate(
            candidates = snapshotLocked(),
            alertCandidate = if (isNewSession) updated else null,
        )
    }

    @Synchronized
    fun tick(nowMs: Long): Boolean = finalizeAndPruneLocked(nowMs)

    @Synchronized
    fun snapshot(nowMs: Long? = null): List<MediaTransferCandidate> {
        nowMs?.let(::finalizeAndPruneLocked)
        return snapshotLocked()
    }

    @Synchronized
    fun clear() {
        sessions.clear()
    }

    private fun finalizeAndPruneLocked(nowMs: Long): Boolean {
        var changed = false
        sessions.forEach { session ->
            if (session.active && nowMs - session.lastSeenMs >= absenceTimeoutMs) {
                session.active = false
                changed = true
            }
        }
        if (sessions.removeAll { session -> nowMs - session.lastSeenMs >= retentionMs }) {
            changed = true
        }
        return changed
    }

    private fun snapshotLocked(): List<MediaTransferCandidate> = sessions
        .map { session -> session.toCandidate() }
        .sortedWith(
            compareByDescending<MediaTransferCandidate> { it.lastSeenMs }
                .thenBy { it.sessionId },
        )

    private fun Session.toCandidate(): MediaTransferCandidate {
        val evidence = buildList {
            add("Name matches DIRECT-FB-<four lowercase letters>")
            if (frequencyMhz != null && frequencyMhz in 5_000..5_899) {
                add("Observed on 5 GHz")
            }
            if (nearbyMetaBle) {
                add("Nearby smartglass Bluetooth signal detected")
            }
            if (sources.size > 1) {
                add("Observed through Wi-Fi Direct and Wi-Fi scan")
            }
        }
        return MediaTransferCandidate(
            sessionId = sessionId,
            observedName = observedName,
            address = address,
            sources = sources.toList().sorted(),
            rssi = rssi,
            frequencyMhz = frequencyMhz,
            channel = channel,
            firstSeenMs = firstSeenMs,
            lastSeenMs = lastSeenMs,
            active = active,
            evidence = evidence,
        )
    }

    companion object {
        const val ABSENCE_TIMEOUT_MS = 20_000L
        const val RETENTION_MS = 5 * 60 * 1_000L
    }
}
