package com.smartglassdetector.app

import android.Manifest
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.smartglassdetector.app.model.DetectionEvent
import com.smartglassdetector.app.model.MediaTransferCandidate
import com.smartglassdetector.app.model.MediaTransferObservation
import com.smartglassdetector.app.model.NearbyDevice
import com.smartglassdetector.app.model.WifiDiscoveryState
import com.smartglassdetector.app.scanner.BluetoothScanner
import com.smartglassdetector.app.scanner.MediaTransferRegistry
import com.smartglassdetector.app.scanner.NearbyDeviceRegistry
import com.smartglassdetector.app.scanner.RecentDeviceRegistry
import com.smartglassdetector.app.scanner.ScanRecoveryPolicy
import com.smartglassdetector.app.scanner.ScanFailureAction
import com.smartglassdetector.app.scanner.ScanServiceRuntimePolicy
import com.smartglassdetector.app.scanner.WifiMediaTransferObserver
import com.smartglassdetector.app.util.AlertCoordinator
import com.smartglassdetector.app.util.NotificationHelper
import com.smartglassdetector.app.util.PreferencesManager
import com.smartglassdetector.app.widget.SmartglassWidgetUpdater
import java.util.concurrent.CopyOnWriteArraySet

class BluetoothScanService : Service(),
    BluetoothScanner.Listener,
    WifiMediaTransferObserver.Listener {
    interface Listener {
        fun onDetection(event: DetectionEvent)
        fun onNearbyDevicesChanged(devices: List<NearbyDevice>)
        fun onRecentDevicesChanged(devices: List<NearbyDevice>)
        fun onMediaTransferObservation(observation: MediaTransferObservation)
        fun onMediaTransferCandidatesChanged(candidates: List<MediaTransferCandidate>)
        fun onWifiDiscoveryStateChanged(state: WifiDiscoveryState)
        fun onScanStateChanged(state: String, errorCode: Int? = null, message: String? = null)
        fun onDebugMessage(message: String)
    }

    inner class LocalBinder : Binder() {
        fun service(): BluetoothScanService = this@BluetoothScanService
    }

    private val binder = LocalBinder()
    private val listeners = CopyOnWriteArraySet<Listener>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val nearbyRegistry = NearbyDeviceRegistry()
    private val recentRegistry = RecentDeviceRegistry()
    private val mediaTransferRegistry = MediaTransferRegistry()
    private val recoveryPolicy = ScanRecoveryPolicy()
    private lateinit var preferences: PreferencesManager
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var alertCoordinator: AlertCoordinator
    private lateinit var wifiObserver: WifiMediaTransferObserver
    private var scanner: BluetoothScanner? = null
    private var foregroundActive = false
    private var activeForegroundServiceTypes = 0
    private var appVisible = false
    private var currentState = STATE_STOPPED
    private var rosterPublishScheduled = false
    private var receiverRegistered = false
    private var lastLifecycleRestartElapsedMs = Long.MIN_VALUE

    private val cleanupRunnable = object : Runnable {
        override fun run() {
            if (!preferences.scanningRequested) {
                return
            }
            val nowMs = System.currentTimeMillis()
            if (nearbyRegistry.prune(nowMs)) {
                publishNearbyDevices()
            }
            if (recentRegistry.prune(nowMs)) {
                publishRecentDevices()
            }
            if (mediaTransferRegistry.tick(nowMs)) {
                publishMediaTransferCandidates()
            }
            mainHandler.postDelayed(this, CLEANUP_INTERVAL_MS)
        }
    }

    private val retryRunnable = Runnable {
        if (!preferences.scanningRequested) {
            return@Runnable
        }
        debug("Retrying background BLE scanner")
        attemptScannerStart("scheduled recovery")
    }

    private val stableScanRunnable = Runnable {
        recoveryPolicy.markStable()
        debug("Background BLE scanner remained stable; retry backoff reset")
    }

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) {
                return
            }
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> {
                    if (preferences.scanningRequested) {
                        debug("Bluetooth enabled; resuming requested scan session")
                        recoveryPolicy.markStable()
                        startScanning()
                    }
                }

                BluetoothAdapter.STATE_TURNING_OFF,
                BluetoothAdapter.STATE_OFF,
                -> pauseForBluetoothDisabled()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        preferences = PreferencesManager(this)
        notificationHelper = NotificationHelper(this)
        alertCoordinator = AlertCoordinator(this, preferences, notificationHelper)
        wifiObserver = WifiMediaTransferObserver(
            context = this,
            listener = this,
            debugEnabled = preferences.debugEnabled,
            wifiScanIntervalSeconds = { preferences.wifiScanIntervalSeconds },
        )
        ContextCompat.registerReceiver(
            this,
            bluetoothStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
        serviceAlive = true
        SmartglassWidgetUpdater.publishRecentCount(this, 0)
        SmartglassWidgetUpdater.publishScanState(
            context = this,
            state = if (preferences.scanningRequested) STATE_STARTING else STATE_STOPPED,
            scanningRequested = preferences.scanningRequested,
        )
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SCAN -> {
                preferences.scanningRequested = true
                if (startInForeground()) {
                    startScanning()
                } else {
                    preferences.scanningRequested = false
                    stopSelf()
                }
            }

            ACTION_STOP_SCAN -> {
                preferences.scanningRequested = false
                stopServiceAndScan()
            }

            ACTION_DISMISS_ALERTS -> {
                alertCoordinator.dismissAll()
                if (!foregroundActive) {
                    stopSelf()
                }
            }

            null -> {
                if (ScanServiceRuntimePolicy.shouldRestoreAfterProcessRecreation(
                        sdkInt = Build.VERSION.SDK_INT,
                        scanningRequested = preferences.scanningRequested,
                    ) && startInForeground()
                ) {
                    debug("Restoring requested scan session after process recreation")
                    startScanning()
                } else {
                    if (preferences.scanningRequested) {
                        debug("Scan session paused until the app is visible again")
                    }
                    stopSelf()
                }
            }
        }
        return ScanServiceRuntimePolicy.restartMode(
            sdkInt = Build.VERSION.SDK_INT,
            scanningRequested = preferences.scanningRequested,
        )
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
        listener.onScanStateChanged(currentState)
        listener.onNearbyDevicesChanged(nearbyRegistry.snapshot())
        listener.onRecentDevicesChanged(recentRegistry.snapshot(System.currentTimeMillis()))
        listener.onMediaTransferCandidatesChanged(
            mediaTransferRegistry.snapshot(System.currentTimeMillis()),
        )
        listener.onWifiDiscoveryStateChanged(wifiObserver.state())
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun onAppVisibilityChanged(visible: Boolean) {
        if (appVisible != visible) {
            appVisible = visible
            debug(if (visible) "App entered foreground" else "App entered background")
        }
        scanner?.setSupplementalEnabled(visible)
        if (visible && preferences.scanningRequested) {
            if (!startInForeground()) {
                return
            }
            reconcileScannerAfterForegroundResume()
        }
    }

    fun startScanning() {
        if (!preferences.scanningRequested) {
            return
        }
        ensureSessionMaintenance()
        wifiObserver.start()

        val activeScanner = scanner
        if (activeScanner != null && activeScanner.isBackgroundActive()) {
            activeScanner.setSupplementalEnabled(appVisible)
            publishState(STATE_SCANNING)
            return
        }
        attemptScannerStart("scan requested")
    }

    fun stopScanning() {
        cancelRecoveryCallbacks()
        mainHandler.removeCallbacks(cleanupRunnable)
        wifiObserver.stop()
        if (currentState != STATE_STOPPED) {
            publishState(STATE_STOPPING)
        }
        scanner?.stop()
        scanner = null
        alertCoordinator.dismissAll()
        nearbyRegistry.clear()
        recentRegistry.clear()
        mediaTransferRegistry.clear()
        publishNearbyDevices()
        publishRecentDevices()
        publishMediaTransferCandidates()
        recoveryPolicy.markStable()
        publishState(STATE_STOPPED)
    }

    fun currentNearbyDevices(): List<NearbyDevice> = nearbyRegistry.snapshot()

    fun currentRecentDevices(): List<NearbyDevice> =
        recentRegistry.snapshot(System.currentTimeMillis())

    fun currentMediaTransferCandidates(): List<MediaTransferCandidate> =
        mediaTransferRegistry.snapshot(System.currentTimeMillis())

    fun currentWifiDiscoveryState(): WifiDiscoveryState = wifiObserver.state()

    fun onSettingsChanged() {
        wifiObserver.onScanIntervalChanged()
    }

    override fun onDeviceDetected(event: DetectionEvent) {
        listeners.forEach { it.onDetection(event) }
        val update = nearbyRegistry.record(
            event = event,
            thresholdRssi = preferences.alertThresholdRssi,
        )
        recentRegistry.record(update.updatedDevice)
        scheduleNearbyPublish()
        update.alertDevice?.let(alertCoordinator::enqueue)
    }

    override fun onScanResultObserved() {
        recoveryPolicy.markStable()
        mainHandler.removeCallbacks(stableScanRunnable)
    }

    override fun onDebugMessage(message: String) {
        debug(message)
    }

    override fun onScanError(source: BluetoothScanner.Source, code: Int, message: String) {
        if (source == BluetoothScanner.Source.FOREGROUND_SUPPLEMENTAL) {
            debug("Foreground supplemental BLE scan failed: $message")
            return
        }

        scanner?.stop()
        scanner = null
        mainHandler.removeCallbacks(stableScanRunnable)
        if (!preferences.scanningRequested) {
            return
        }

        debug("Background BLE scan failed ($code): $message")
        when (recoveryPolicy.actionFor(code)) {
            ScanFailureAction.WAIT_FOR_PREREQUISITE,
            ScanFailureAction.STOP_RETRYING,
            -> {
                mainHandler.removeCallbacks(retryRunnable)
                publishState(STATE_ERROR, code, message)
            }

            ScanFailureAction.RETRY -> scheduleScannerRetry(code, message)
        }
    }

    override fun onMediaTransferObservation(observation: MediaTransferObservation) {
        val enrichedObservation = observation.copy(
            nearbyMetaBle = nearbyRegistry.snapshot().isNotEmpty(),
        )
        if (preferences.debugEnabled) {
            debug(
                "Wi-Fi candidate ${enrichedObservation.observedName} " +
                    "source=${enrichedObservation.source} " +
                    "rssi=${enrichedObservation.rssi ?: "unavailable"} " +
                    "frequency=${enrichedObservation.frequencyMhz ?: "unavailable"}",
            )
        }
        listeners.forEach { it.onMediaTransferObservation(enrichedObservation) }
        val update = mediaTransferRegistry.record(enrichedObservation) ?: return
        listeners.forEach { it.onMediaTransferCandidatesChanged(update.candidates) }
        update.alertCandidate?.let(alertCoordinator::enqueue)
    }

    override fun onWifiDiscoveryStateChanged(state: WifiDiscoveryState) {
        listeners.forEach { it.onWifiDiscoveryStateChanged(state) }
    }

    override fun onWifiDebugMessage(message: String) {
        debug("Wi-Fi: $message")
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        scanner?.stop()
        scanner = null
        wifiObserver.stop()
        alertCoordinator.dismissAll()
        nearbyRegistry.clear()
        recentRegistry.clear()
        mediaTransferRegistry.clear()
        if (receiverRegistered) {
            try {
                unregisterReceiver(bluetoothStateReceiver)
            } catch (_: IllegalArgumentException) {
                // The receiver was already detached during service teardown.
            }
            receiverRegistered = false
        }
        serviceAlive = false
        scanning = false
        foregroundActive = false
        activeForegroundServiceTypes = 0
        currentState = STATE_STOPPED
        SmartglassWidgetUpdater.onServiceDestroyed(
            context = this,
            scanningRequested = preferences.scanningRequested,
        )
        super.onDestroy()
    }

    private fun attemptScannerStart(reason: String) {
        if (!preferences.scanningRequested) {
            return
        }
        mainHandler.removeCallbacks(retryRunnable)
        mainHandler.removeCallbacks(stableScanRunnable)
        scanner?.stop()
        scanner = null
        publishState(STATE_STARTING)
        debug("Starting background BLE scanner: $reason")

        val newScanner = BluetoothScanner(
            context = this,
            listener = this,
            debugEnabled = preferences.debugEnabled,
        )
        scanner = newScanner
        if (newScanner.start(enableSupplemental = appVisible)) {
            if (scanner === newScanner) {
                publishState(STATE_SCANNING)
                mainHandler.postDelayed(stableScanRunnable, STABLE_SCAN_RESET_MS)
            }
        } else if (scanner === newScanner) {
            scanner = null
        }
    }

    private fun scheduleScannerRetry(code: Int, message: String) {
        val delayMs = recoveryPolicy.nextDelayMs(code)
        publishState(STATE_STARTING, code, message)
        mainHandler.removeCallbacks(retryRunnable)
        mainHandler.postDelayed(retryRunnable, delayMs)
        debug("Background BLE recovery scheduled in ${delayMs / 1_000}s")
    }

    private fun reconcileScannerAfterForegroundResume() {
        val activeScanner = scanner
        if (activeScanner == null || !activeScanner.isBackgroundActive()) {
            debug("Foreground return found no active BLE scanner; restoring it")
            startScanning()
            return
        }

        val nowElapsedMs = SystemClock.elapsedRealtime()
        val restartRateLimitElapsed = if (lastLifecycleRestartElapsedMs == Long.MIN_VALUE) {
            Long.MAX_VALUE
        } else {
            nowElapsedMs - lastLifecycleRestartElapsedMs
        }
        if (
            activeScanner.isBackgroundStale(nowElapsedMs, FOREGROUND_STALE_SCAN_MS) &&
            restartRateLimitElapsed >= LIFECYCLE_RESTART_MIN_INTERVAL_MS
        ) {
            lastLifecycleRestartElapsedMs = nowElapsedMs
            debug("Foreground return found a stale BLE scanner; re-registering it")
            attemptScannerStart("foreground stale-scan recovery")
        }
    }

    private fun pauseForBluetoothDisabled() {
        if (!preferences.scanningRequested) {
            return
        }
        cancelRecoveryCallbacks()
        scanner?.stop()
        scanner = null
        debug("Bluetooth disabled; scan recovery paused until Bluetooth is enabled")
        publishState(STATE_ERROR, BluetoothScanner.ERROR_DISABLED, "Bluetooth is disabled")
    }

    private fun ensureSessionMaintenance() {
        mainHandler.removeCallbacks(cleanupRunnable)
        mainHandler.postDelayed(cleanupRunnable, CLEANUP_INTERVAL_MS)
    }

    private fun cancelRecoveryCallbacks() {
        mainHandler.removeCallbacks(retryRunnable)
        mainHandler.removeCallbacks(stableScanRunnable)
    }

    private fun debug(message: String) {
        listeners.forEach { it.onDebugMessage(message) }
    }

    private fun startInForeground(): Boolean {
        val notification = notificationHelper.serviceNotification()
        val requestedTypes = ScanServiceRuntimePolicy.foregroundServiceTypes(
            sdkInt = Build.VERSION.SDK_INT,
            foregroundLocationAvailable = hasForegroundLocationAccess(),
        )
        if (foregroundActive && activeForegroundServiceTypes == requestedTypes) {
            return true
        }

        return try {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.SERVICE_NOTIFICATION_ID,
                notification,
                requestedTypes,
            )
            foregroundActive = true
            activeForegroundServiceTypes = requestedTypes
            true
        } catch (error: SecurityException) {
            debug("Could not activate foreground scanner types: ${error.message}")
            publishState(
                STATE_ERROR,
                message = "Scanning access is unavailable. Open the app and review permissions.",
            )
            false
        } catch (error: RuntimeException) {
            debug("Could not promote scanner service: ${error.message}")
            publishState(
                STATE_ERROR,
                message = "Android could not start background scanning.",
            )
            false
        }
    }

    private fun hasForegroundLocationAccess(): Boolean {
        val permissionGranted = PermissionChecker.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PermissionChecker.PERMISSION_GRANTED
        val locationManager = getSystemService(LocationManager::class.java)
        val locationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager?.isLocationEnabled == true
        } else {
            true
        }
        return permissionGranted && locationEnabled
    }

    private fun stopServiceAndScan() {
        stopScanning()
        if (foregroundActive) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundActive = false
            activeForegroundServiceTypes = 0
        }
        stopSelf()
    }

    private fun scheduleNearbyPublish() {
        if (rosterPublishScheduled) {
            return
        }
        rosterPublishScheduled = true
        mainHandler.postDelayed(
            {
                rosterPublishScheduled = false
                publishNearbyDevices()
                publishRecentDevices()
            },
            ROSTER_PUBLISH_INTERVAL_MS,
        )
    }

    private fun publishNearbyDevices() {
        val snapshot = nearbyRegistry.snapshot()
        listeners.forEach { it.onNearbyDevicesChanged(snapshot) }
    }

    private fun publishRecentDevices() {
        val snapshot = recentRegistry.snapshot(System.currentTimeMillis())
        listeners.forEach { it.onRecentDevicesChanged(snapshot) }
        SmartglassWidgetUpdater.publishRecentCount(this, snapshot.size)
    }

    private fun publishMediaTransferCandidates() {
        val snapshot = mediaTransferRegistry.snapshot(System.currentTimeMillis())
        listeners.forEach { it.onMediaTransferCandidatesChanged(snapshot) }
    }

    private fun publishState(state: String, errorCode: Int? = null, message: String? = null) {
        currentState = state
        scanning = state == STATE_STARTING || state == STATE_SCANNING
        listeners.forEach { it.onScanStateChanged(state, errorCode, message) }
        SmartglassWidgetUpdater.publishScanState(
            context = this,
            state = state,
            scanningRequested = preferences.scanningRequested,
        )
    }

    companion object {
        const val ACTION_START_SCAN = "com.smartglassdetector.app.action.START_SCAN"
        const val ACTION_STOP_SCAN = "com.smartglassdetector.app.action.STOP_SCAN"
        const val ACTION_DISMISS_ALERTS = "com.smartglassdetector.app.action.DISMISS_ALERTS"

        const val STATE_STOPPED = "stopped"
        const val STATE_STARTING = "starting"
        const val STATE_SCANNING = "scanning"
        const val STATE_STOPPING = "stopping"
        const val STATE_ERROR = "error"

        private const val CLEANUP_INTERVAL_MS = 1_000L
        private const val ROSTER_PUBLISH_INTERVAL_MS = 350L
        private const val STABLE_SCAN_RESET_MS = 60_000L
        private const val FOREGROUND_STALE_SCAN_MS = 15_000L
        private const val LIFECYCLE_RESTART_MIN_INTERVAL_MS = 30_000L

        @Volatile
        var scanning: Boolean = false
            private set

        @Volatile
        var serviceAlive: Boolean = false
            private set
    }
}
