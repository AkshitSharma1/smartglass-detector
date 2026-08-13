package com.smartglassdetector.app.scanner

enum class ScanFailureAction {
    RETRY,
    WAIT_FOR_PREREQUISITE,
    STOP_RETRYING,
}

class ScanRecoveryPolicy(
    private val retryDelaysMs: List<Long> = DEFAULT_RETRY_DELAYS_MS,
    private val tooFrequentMinimumDelayMs: Long = TOO_FREQUENT_MINIMUM_DELAY_MS,
) {
    private var failureCount = 0

    fun actionFor(errorCode: Int): ScanFailureAction = when (errorCode) {
        BluetoothScanner.ERROR_DISABLED,
        BluetoothScanner.ERROR_PERMISSION,
        -> ScanFailureAction.WAIT_FOR_PREREQUISITE

        BluetoothScanner.ERROR_UNSUPPORTED,
        BluetoothScanner.ERROR_FEATURE_UNSUPPORTED,
        -> ScanFailureAction.STOP_RETRYING

        else -> ScanFailureAction.RETRY
    }

    fun nextDelayMs(errorCode: Int): Long {
        val delay = retryDelaysMs[
            failureCount.coerceAtMost(retryDelaysMs.lastIndex)
        ]
        failureCount += 1
        return if (errorCode == BluetoothScanner.ERROR_SCANNING_TOO_FREQUENTLY) {
            maxOf(delay, tooFrequentMinimumDelayMs)
        } else {
            delay
        }
    }

    fun markStable() {
        failureCount = 0
    }

    companion object {
        val DEFAULT_RETRY_DELAYS_MS = listOf(2_000L, 5_000L, 15_000L, 30_000L)
        const val TOO_FREQUENT_MINIMUM_DELAY_MS = 30_000L
    }
}
