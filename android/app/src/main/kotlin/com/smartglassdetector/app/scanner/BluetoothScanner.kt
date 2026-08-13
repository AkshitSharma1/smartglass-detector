package com.smartglassdetector.app.scanner

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.smartglassdetector.app.model.DetectionEvent

class BluetoothScanner(
    private val context: Context,
    private val listener: Listener,
    private val debugEnabled: Boolean,
) {
    enum class Source(val label: String) {
        BACKGROUND_FILTERED("background-filtered"),
        FOREGROUND_SUPPLEMENTAL("foreground-supplemental"),
    }

    interface Listener {
        fun onDeviceDetected(event: DetectionEvent)
        fun onScanResultObserved()
        fun onDebugMessage(message: String)
        fun onScanError(source: Source, code: Int, message: String)
    }

    private var bleScanner: BluetoothLeScanner? = null
    private var backgroundStarted = false
    private var supplementalStarted = false
    private var backgroundStartedAtElapsedMs = 0L
    private var lastResultElapsedMs = 0L
    private val lastProcessedTimestampByAddress = linkedMapOf<String, Long>()

    private val backgroundCallback = callbackFor(Source.BACKGROUND_FILTERED)
    private val supplementalCallback = callbackFor(Source.FOREGROUND_SUPPLEMENTAL)

    fun start(enableSupplemental: Boolean): Boolean {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter
        if (adapter == null) {
            reportStartError(
                Source.BACKGROUND_FILTERED,
                ERROR_UNSUPPORTED,
                "Bluetooth Low Energy is not supported",
            )
            return false
        }
        if (!adapter.isEnabled) {
            reportStartError(
                Source.BACKGROUND_FILTERED,
                ERROR_DISABLED,
                "Bluetooth is disabled",
            )
            return false
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            reportStartError(
                Source.BACKGROUND_FILTERED,
                ERROR_UNAVAILABLE,
                "Bluetooth scanner is unavailable",
            )
            return false
        }

        bleScanner = scanner
        if (!startBackgroundScan(scanner)) {
            bleScanner = null
            return false
        }
        if (enableSupplemental) {
            setSupplementalEnabled(true)
        }
        return true
    }

    fun setSupplementalEnabled(enabled: Boolean) {
        val scanner = bleScanner ?: return
        if (enabled == supplementalStarted) {
            return
        }
        if (enabled) {
            startSupplementalScan(scanner)
        } else {
            stopCallback(scanner, supplementalCallback, Source.FOREGROUND_SUPPLEMENTAL)
            supplementalStarted = false
            listener.onDebugMessage("Stopped foreground supplemental BLE scan")
        }
    }

    fun isBackgroundActive(): Boolean = backgroundStarted

    fun isBackgroundStale(nowElapsedMs: Long, staleAfterMs: Long): Boolean {
        if (!backgroundStarted) {
            return true
        }
        val lastActivity = maxOf(backgroundStartedAtElapsedMs, lastResultElapsedMs)
        return nowElapsedMs - lastActivity >= staleAfterMs
    }

    fun stop() {
        val scanner = bleScanner ?: return
        if (supplementalStarted) {
            stopCallback(scanner, supplementalCallback, Source.FOREGROUND_SUPPLEMENTAL)
        }
        if (backgroundStarted) {
            stopCallback(scanner, backgroundCallback, Source.BACKGROUND_FILTERED)
        }
        supplementalStarted = false
        backgroundStarted = false
        bleScanner = null
        synchronized(lastProcessedTimestampByAddress) {
            lastProcessedTimestampByAddress.clear()
        }
    }

    private fun callbackFor(source: Source): ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result, source)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { result -> handleScanResult(result, source) }
        }

        override fun onScanFailed(errorCode: Int) {
            when (source) {
                Source.BACKGROUND_FILTERED -> backgroundStarted = false
                Source.FOREGROUND_SUPPLEMENTAL -> supplementalStarted = false
            }
            listener.onScanError(source, errorCode, scanErrorMessage(errorCode))
        }
    }

    private fun startBackgroundScan(scanner: BluetoothLeScanner): Boolean = try {
        scanner.startScan(
            BleScanConfiguration.backgroundFilters(),
            scanSettings(),
            backgroundCallback,
        )
        backgroundStarted = true
        backgroundStartedAtElapsedMs = SystemClock.elapsedRealtime()
        listener.onDebugMessage(
            "Started background BLE scan with Meta company and service filters",
        )
        true
    } catch (_: SecurityException) {
        reportStartError(
            Source.BACKGROUND_FILTERED,
            ERROR_PERMISSION,
            "Nearby devices permission is required",
        )
        false
    } catch (error: IllegalStateException) {
        reportStartError(
            Source.BACKGROUND_FILTERED,
            ERROR_UNAVAILABLE,
            error.message ?: "Bluetooth scanner could not start",
        )
        false
    }

    private fun startSupplementalScan(scanner: BluetoothLeScanner) {
        try {
            scanner.startScan(null, scanSettings(), supplementalCallback)
            supplementalStarted = true
            listener.onDebugMessage("Started foreground supplemental BLE scan for name matches")
        } catch (_: SecurityException) {
            reportStartError(
                Source.FOREGROUND_SUPPLEMENTAL,
                ERROR_PERMISSION,
                "Nearby devices permission is required",
            )
        } catch (error: IllegalStateException) {
            reportStartError(
                Source.FOREGROUND_SUPPLEMENTAL,
                ERROR_UNAVAILABLE,
                error.message ?: "Supplemental Bluetooth scan could not start",
            )
        }
    }

    private fun stopCallback(
        scanner: BluetoothLeScanner,
        callback: ScanCallback,
        source: Source,
    ) {
        try {
            scanner.stopScan(callback)
        } catch (_: SecurityException) {
            listener.onDebugMessage("${source.label} scan stopped after permission changed")
        } catch (_: IllegalStateException) {
            listener.onDebugMessage("${source.label} scan was already unavailable")
        }
    }

    private fun handleScanResult(result: ScanResult, source: Source) {
        lastResultElapsedMs = SystemClock.elapsedRealtime()
        listener.onScanResultObserved()

        val deviceAddress = try {
            result.device.address
        } catch (_: SecurityException) {
            "Unavailable"
        }
        if (isDuplicate(deviceAddress, result.timestampNanos)) {
            return
        }
        processScanResult(result, deviceAddress, source)
    }

    private fun isDuplicate(deviceAddress: String, timestampNanos: Long): Boolean {
        if (timestampNanos <= 0L) {
            return false
        }
        synchronized(lastProcessedTimestampByAddress) {
            val previous = lastProcessedTimestampByAddress[deviceAddress]
            if (previous != null && timestampNanos <= previous) {
                return true
            }
            if (lastProcessedTimestampByAddress.size >= MAX_DEDUPLICATION_DEVICES) {
                lastProcessedTimestampByAddress.clear()
            }
            lastProcessedTimestampByAddress[deviceAddress] = timestampNanos
        }
        return false
    }

    private fun processScanResult(
        result: ScanResult,
        deviceAddress: String,
        source: Source,
    ) {
        val advertisedName = result.scanRecord?.deviceName
        val canReadIdentity = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED

        val deviceName = AdvertisementValues.selectName(
            advertisedName = advertisedName,
            bluetoothNameReader = {
                if (canReadIdentity) result.device.name else null
            },
            aliasReader = {
                if (canReadIdentity && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    result.device.alias
                } else {
                    null
                }
            },
        )

        val scanRecord = result.scanRecord
        val manufacturerData = scanRecord?.manufacturerSpecificData
        var companyId: Int? = null
        var manufacturerDataHex: String? = null
        if (manufacturerData != null && manufacturerData.size() > 0) {
            val metaIndex = (0 until manufacturerData.size()).firstOrNull { index ->
                val candidate = manufacturerData.keyAt(index)
                candidate == MetaRayBanClassifier.META_COMPANY_ID_1 ||
                    candidate == MetaRayBanClassifier.META_COMPANY_ID_2
            } ?: 0
            companyId = manufacturerData.keyAt(metaIndex)
            manufacturerDataHex = AdvertisementValues.formatPayload(manufacturerData.valueAt(metaIndex))
        }

        val serviceUuids = scanRecord?.serviceUuids
            ?.map { it.uuid.toString() }
            .orEmpty()
        val classification = MetaRayBanClassifier.classify(
            companyId = companyId,
            deviceName = deviceName,
            serviceUuids = serviceUuids,
        )

        if (debugEnabled) {
            val company = companyId?.let(AdvertisementValues::formatCompanyId) ?: "none"
            val name = deviceName ?: "unnamed"
            listener.onDebugMessage(
                "BLE ${source.label} · $deviceAddress · $name · $company · RSSI ${result.rssi}",
            )
        }

        if (!classification.isMatch) {
            return
        }

        val scanResultTxPower = result.txPower.takeUnless {
            it == ScanResult.TX_POWER_NOT_PRESENT
        }
        val recordTxPower = scanRecord?.txPowerLevel?.takeUnless {
            it == Int.MIN_VALUE
        }
        val confidence = if (
            classification.reasonCodes.any { it.startsWith("name_") }
        ) {
            "high"
        } else {
            "medium"
        }

        listener.onDeviceDetected(
            DetectionEvent(
                timestampMs = System.currentTimeMillis(),
                deviceAddress = deviceAddress,
                deviceName = deviceName,
                companyId = companyId?.let(AdvertisementValues::formatCompanyId),
                companyName = AdvertisementValues.companyName(companyId),
                manufacturerDataHex = manufacturerDataHex,
                reasonCodes = classification.reasonCodes,
                reasonText = classification.reasonText,
                confidence = confidence,
                rssi = result.rssi,
                txPower = scanResultTxPower ?: recordTxPower,
                serviceUuids = serviceUuids,
            ),
        )
    }

    private fun reportStartError(source: Source, code: Int, message: String) {
        listener.onScanError(source, code, message)
    }

    private fun scanSettings(): ScanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
        .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
        .setReportDelay(0)
        .build()

    private fun scanErrorMessage(errorCode: Int): String = when (errorCode) {
        ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "Bluetooth scan is already running"
        ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED ->
            "Bluetooth scanner registration failed"
        ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "This Bluetooth scan mode is unsupported"
        ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "Android reported an internal Bluetooth scan error"
        ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES ->
            "Bluetooth scan hardware resources are unavailable"
        ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY ->
            "Bluetooth scans were started too frequently"
        else -> "Bluetooth scan failed with code $errorCode"
    }

    companion object {
        const val ERROR_UNSUPPORTED = -100
        const val ERROR_DISABLED = -101
        const val ERROR_UNAVAILABLE = -102
        const val ERROR_PERMISSION = -103
        const val ERROR_SCANNING_TOO_FREQUENTLY =
            ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY
        const val ERROR_FEATURE_UNSUPPORTED = ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED
        private const val MAX_DEDUPLICATION_DEVICES = 512
    }
}
