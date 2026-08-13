package com.smartglassdetector.app.widget

import android.content.Context
import androidx.core.content.edit

enum class SmartglassWidgetIssue {
    NONE,
    SETUP_REQUIRED,
    PERMISSION_REQUIRED,
    BLUETOOTH_DISABLED,
    UNSUPPORTED,
    ATTENTION,
}

enum class SmartglassWidgetScannerState {
    STOPPED,
    STARTING,
    SCANNING,
    STOPPING,
    ERROR,
}

enum class SmartglassWidgetStatus {
    STOPPED,
    STARTING,
    PAUSED,
    SCANNING_CLEAR,
    SCANNING_DETECTED,
    STOPPING,
    SETUP_REQUIRED,
    PERMISSION_REQUIRED,
    BLUETOOTH_DISABLED,
    UNSUPPORTED,
    ATTENTION,
}

enum class SmartglassWidgetAction {
    START,
    STOP,
    OPEN_APP,
    NONE,
}

enum class SmartglassWidgetActionTarget {
    VISIBLE_ACTIVITY_START,
    STOP_BROADCAST,
    OPEN_ACTIVITY,
    NONE,
}

object SmartglassWidgetActionRouting {
    fun target(action: SmartglassWidgetAction): SmartglassWidgetActionTarget = when (action) {
        SmartglassWidgetAction.START -> SmartglassWidgetActionTarget.VISIBLE_ACTIVITY_START
        SmartglassWidgetAction.STOP -> SmartglassWidgetActionTarget.STOP_BROADCAST
        SmartglassWidgetAction.OPEN_APP -> SmartglassWidgetActionTarget.OPEN_ACTIVITY
        SmartglassWidgetAction.NONE -> SmartglassWidgetActionTarget.NONE
    }
}

enum class SmartglassWidgetTone {
    NEUTRAL,
    SAFE,
    DANGER,
    WARNING,
    INFO,
}

data class SmartglassWidgetSnapshot(
    val scannerState: SmartglassWidgetScannerState = SmartglassWidgetScannerState.STOPPED,
    val scanningRequested: Boolean = false,
    val recentCount: Int = 0,
    val issue: SmartglassWidgetIssue = SmartglassWidgetIssue.NONE,
    val lastUpdatedMs: Long = 0L,
)

data class SmartglassWidgetViewState(
    val status: SmartglassWidgetStatus,
    val action: SmartglassWidgetAction,
    val tone: SmartglassWidgetTone,
    val recentCount: Int,
)

object SmartglassWidgetStateReducer {
    fun reduce(
        snapshot: SmartglassWidgetSnapshot,
        serviceAlive: Boolean,
    ): SmartglassWidgetViewState {
        val safeCount = snapshot.recentCount.coerceAtLeast(0)
        val count = if (serviceAlive) safeCount else 0

        if (snapshot.issue != SmartglassWidgetIssue.NONE) {
            return SmartglassWidgetViewState(
                status = snapshot.issue.status,
                action = if (snapshot.scanningRequested) {
                    SmartglassWidgetAction.STOP
                } else {
                    SmartglassWidgetAction.OPEN_APP
                },
                tone = snapshot.issue.tone,
                recentCount = count,
            )
        }

        if (snapshot.scanningRequested && !serviceAlive) {
            return SmartglassWidgetViewState(
                status = SmartglassWidgetStatus.PAUSED,
                action = SmartglassWidgetAction.OPEN_APP,
                tone = SmartglassWidgetTone.INFO,
                recentCount = 0,
            )
        }

        return when (snapshot.scannerState) {
            SmartglassWidgetScannerState.STARTING -> SmartglassWidgetViewState(
                status = SmartglassWidgetStatus.STARTING,
                action = SmartglassWidgetAction.STOP,
                tone = SmartglassWidgetTone.INFO,
                recentCount = count,
            )

            SmartglassWidgetScannerState.SCANNING -> SmartglassWidgetViewState(
                status = if (count == 0) {
                    SmartglassWidgetStatus.SCANNING_CLEAR
                } else {
                    SmartglassWidgetStatus.SCANNING_DETECTED
                },
                action = SmartglassWidgetAction.STOP,
                tone = if (count == 0) {
                    SmartglassWidgetTone.SAFE
                } else {
                    SmartglassWidgetTone.DANGER
                },
                recentCount = count,
            )

            SmartglassWidgetScannerState.STOPPING -> SmartglassWidgetViewState(
                status = SmartglassWidgetStatus.STOPPING,
                action = SmartglassWidgetAction.NONE,
                tone = SmartglassWidgetTone.INFO,
                recentCount = count,
            )

            SmartglassWidgetScannerState.ERROR -> SmartglassWidgetViewState(
                status = SmartglassWidgetStatus.ATTENTION,
                action = if (snapshot.scanningRequested) {
                    SmartglassWidgetAction.STOP
                } else {
                    SmartglassWidgetAction.OPEN_APP
                },
                tone = SmartglassWidgetTone.WARNING,
                recentCount = count,
            )

            SmartglassWidgetScannerState.STOPPED -> SmartglassWidgetViewState(
                status = SmartglassWidgetStatus.STOPPED,
                action = SmartglassWidgetAction.START,
                tone = SmartglassWidgetTone.NEUTRAL,
                recentCount = 0,
            )
        }
    }

    private val SmartglassWidgetIssue.status: SmartglassWidgetStatus
        get() = when (this) {
            SmartglassWidgetIssue.NONE -> SmartglassWidgetStatus.STOPPED
            SmartglassWidgetIssue.SETUP_REQUIRED -> SmartglassWidgetStatus.SETUP_REQUIRED
            SmartglassWidgetIssue.PERMISSION_REQUIRED -> SmartglassWidgetStatus.PERMISSION_REQUIRED
            SmartglassWidgetIssue.BLUETOOTH_DISABLED -> SmartglassWidgetStatus.BLUETOOTH_DISABLED
            SmartglassWidgetIssue.UNSUPPORTED -> SmartglassWidgetStatus.UNSUPPORTED
            SmartglassWidgetIssue.ATTENTION -> SmartglassWidgetStatus.ATTENTION
        }

    private val SmartglassWidgetIssue.tone: SmartglassWidgetTone
        get() = when (this) {
            SmartglassWidgetIssue.UNSUPPORTED -> SmartglassWidgetTone.DANGER
            SmartglassWidgetIssue.NONE -> SmartglassWidgetTone.NEUTRAL
            else -> SmartglassWidgetTone.WARNING
        }
}

class SmartglassWidgetStateStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun read(): SmartglassWidgetSnapshot = SmartglassWidgetSnapshot(
        scannerState = enumValueOrDefault(
            preferences.getString(KEY_SCANNER_STATE, null),
            SmartglassWidgetScannerState.STOPPED,
        ),
        scanningRequested = preferences.getBoolean(KEY_SCANNING_REQUESTED, false),
        recentCount = preferences.getInt(KEY_RECENT_COUNT, 0).coerceAtLeast(0),
        issue = enumValueOrDefault(
            preferences.getString(KEY_ISSUE, null),
            SmartglassWidgetIssue.NONE,
        ),
        lastUpdatedMs = preferences.getLong(KEY_LAST_UPDATED_MS, 0L),
    )

    fun write(snapshot: SmartglassWidgetSnapshot): Boolean {
        val normalized = snapshot.copy(recentCount = snapshot.recentCount.coerceAtLeast(0))
        val current = read()
        if (current.visibleStateEquals(normalized)) {
            return false
        }
        preferences.edit {
            putString(KEY_SCANNER_STATE, normalized.scannerState.name)
            putBoolean(KEY_SCANNING_REQUESTED, normalized.scanningRequested)
            putInt(KEY_RECENT_COUNT, normalized.recentCount)
            putString(KEY_ISSUE, normalized.issue.name)
            putLong(KEY_LAST_UPDATED_MS, normalized.lastUpdatedMs)
        }
        return true
    }

    private fun SmartglassWidgetSnapshot.visibleStateEquals(
        other: SmartglassWidgetSnapshot,
    ): Boolean = scannerState == other.scannerState &&
        scanningRequested == other.scanningRequested &&
        recentCount == other.recentCount &&
        issue == other.issue

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, fallback: T): T =
        value?.let { candidate -> enumValues<T>().firstOrNull { it.name == candidate } } ?: fallback

    companion object {
        private const val PREFERENCES_NAME = "smartglass_detector_widget"
        private const val KEY_SCANNER_STATE = "scanner_state"
        private const val KEY_SCANNING_REQUESTED = "scanning_requested"
        private const val KEY_RECENT_COUNT = "recent_count"
        private const val KEY_ISSUE = "issue"
        private const val KEY_LAST_UPDATED_MS = "last_updated_ms"
    }
}

object SmartglassWidgetUpdateTargets {
    fun resolve(requestedIds: IntArray?, installedIds: IntArray): IntArray =
        (requestedIds?.takeIf { it.isNotEmpty() } ?: installedIds)
            .distinct()
            .toIntArray()
}

internal fun ScanPrerequisiteIssue.toWidgetIssue(): SmartglassWidgetIssue = when (this) {
    ScanPrerequisiteIssue.NONE -> SmartglassWidgetIssue.NONE
    ScanPrerequisiteIssue.SETUP_REQUIRED -> SmartglassWidgetIssue.SETUP_REQUIRED
    ScanPrerequisiteIssue.PERMISSION_REQUIRED -> SmartglassWidgetIssue.PERMISSION_REQUIRED
    ScanPrerequisiteIssue.BLUETOOTH_DISABLED -> SmartglassWidgetIssue.BLUETOOTH_DISABLED
    ScanPrerequisiteIssue.UNSUPPORTED -> SmartglassWidgetIssue.UNSUPPORTED
}
