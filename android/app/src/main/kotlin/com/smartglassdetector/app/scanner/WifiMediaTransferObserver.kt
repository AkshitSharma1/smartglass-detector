package com.smartglassdetector.app.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.smartglassdetector.app.model.MediaTransferObservation
import com.smartglassdetector.app.model.WifiDiscoveryState

class WifiMediaTransferObserver(
    private val context: Context,
    private val listener: Listener,
    private val debugEnabled: Boolean,
    private val wifiScanIntervalSeconds: () -> Int,
) {
    interface Listener {
        fun onMediaTransferObservation(observation: MediaTransferObservation)
        fun onWifiDiscoveryStateChanged(state: WifiDiscoveryState)
        fun onWifiDebugMessage(message: String)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
    private val p2pManager = context.getSystemService(WifiP2pManager::class.java)
    private var channel: WifiP2pManager.Channel? = null
    private var receiverRegistered = false
    private var running = false
    private val wifiScanCadencePolicy = WifiScanCadencePolicy()
    private val observationSchedule =
        WifiObservationSchedulePolicy(wifiScanIntervalSeconds)
    private var currentState = WifiDiscoveryState(STATUS_INACTIVE)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val enabled = intent.getIntExtra(
                        WifiP2pManager.EXTRA_WIFI_STATE,
                        WifiP2pManager.WIFI_P2P_STATE_DISABLED,
                    ) == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    if (enabled) {
                        runDiscoveryCycle(requestWifiScan = true)
                    } else {
                        publishState(
                            STATUS_WIFI_DISABLED,
                            "Wi-Fi Direct is unavailable while Wi-Fi is off.",
                        )
                    }
                }

                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> requestPeers()
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> {
                    wifiScanCadencePolicy.markResultsAvailable()
                    if (intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)) {
                        processWifiScanResults()
                    } else {
                        debug("Ignoring stale conventional Wi-Fi scan results")
                    }
                }
            }
        }
    }

    private val watchdogRunnable = object : Runnable {
        override fun run() {
            if (!running) {
                return
            }
            runDiscoveryCycle(requestWifiScan = false, requestCurrentPeers = false)
            handler.postDelayed(this, observationSchedule.discoveryRecoveryDelayMs)
        }
    }

    private val peerSampleRunnable = object : Runnable {
        override fun run() {
            if (!running) {
                return
            }
            if (canSampleWifiDirectPeers()) {
                requestPeers()
            }
            scheduleNextPeerSample()
        }
    }

    private val wifiScanRunnable = object : Runnable {
        override fun run() {
            if (!running) {
                return
            }
            requestConventionalWifiScanIfAvailable()
            scheduleNextWifiScan()
        }
    }

    fun start() {
        if (running) {
            return
        }
        running = true
        registerReceiver()
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT) ||
            p2pManager == null
        ) {
            publishState(
                STATUS_UNSUPPORTED,
                "Wi-Fi Direct is unsupported; nearby Wi-Fi network scans will continue.",
            )
            requestConventionalWifiScanIfAvailable()
            scheduleNextWifiScan()
            return
        }
        initializeChannel()
        runDiscoveryCycle(requestWifiScan = true)
        handler.removeCallbacks(watchdogRunnable)
        handler.postDelayed(
            watchdogRunnable,
            observationSchedule.discoveryRecoveryDelayMs,
        )
        scheduleNextPeerSample()
        scheduleNextWifiScan()
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        wifiScanCadencePolicy.reset()
        stopPeerDiscovery()
        channel = null
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
                // Receiver had already been removed by the platform.
            }
            receiverRegistered = false
        }
        publishState(STATUS_INACTIVE)
    }

    fun state(): WifiDiscoveryState = currentState

    fun onScanIntervalChanged() {
        if (!running) {
            return
        }
        handler.removeCallbacks(wifiScanRunnable)
        handler.removeCallbacks(peerSampleRunnable)
        debug("Wi-Fi observation interval changed to ${scanIntervalSeconds()}s")
        scheduleNextPeerSample()
        scheduleNextWifiScan()
    }

    private fun runDiscoveryCycle(
        requestWifiScan: Boolean,
        requestCurrentPeers: Boolean = true,
    ) {
        when {
            !hasDiscoveryPermission() -> {
                publishState(
                    STATUS_PERMISSION_REQUIRED,
                    "Nearby Wi-Fi permission is required for media-transfer discovery.",
                )
            }

            wifiManager?.isWifiEnabled != true -> {
                publishState(STATUS_WIFI_DISABLED, "Turn on Wi-Fi to enable discovery.")
            }

            !isLocationModeEnabled() -> {
                publishState(
                    STATUS_LOCATION_DISABLED,
                    "Android requires Location services for Wi-Fi Direct peer discovery.",
                )
            }

            else -> {
                publishState(STATUS_DISCOVERING)
                if (requestCurrentPeers) {
                    requestPeers()
                }
                discoverPeers()
                if (requestWifiScan) {
                    requestConventionalWifiScanIfAvailable()
                }
            }
        }
    }

    private fun registerReceiver() {
        if (receiverRegistered) {
            return
        }
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    private fun initializeChannel() {
        channel = p2pManager?.initialize(context, Looper.getMainLooper()) {
            channel = null
            publishState(STATUS_ERROR, "Wi-Fi Direct channel was lost; retrying.")
            if (running) {
                handler.postDelayed(
                    {
                        if (running) {
                            initializeChannel()
                            discoverPeers()
                        }
                    },
                    CHANNEL_RETRY_MS,
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun discoverPeers() {
        if (!running || !hasDiscoveryPermission()) {
            return
        }
        val activeChannel = channel ?: return
        try {
            p2pManager?.discoverPeers(
                activeChannel,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        publishState(STATUS_DISCOVERING)
                        debug("Wi-Fi Direct peer discovery active")
                        // Some vendor implementations only expose their refreshed peer list
                        // after discovery has been accepted. Keep this immediate refresh in
                        // addition to the configured recurring sampler.
                        requestPeers()
                    }

                    override fun onFailure(reason: Int) {
                        if (reason == WifiP2pManager.P2P_UNSUPPORTED) {
                            publishState(
                                STATUS_UNSUPPORTED,
                                "Wi-Fi Direct discovery is not supported by this device.",
                            )
                        } else {
                            publishState(
                                STATUS_DISCOVERING,
                                "Wi-Fi Direct discovery is temporarily unavailable; retrying.",
                            )
                            debug("Wi-Fi Direct discovery failed: ${reasonLabel(reason)}")
                        }
                    }
                },
            )
        } catch (error: SecurityException) {
            publishState(STATUS_PERMISSION_REQUIRED, error.message)
        } catch (error: RuntimeException) {
            publishState(STATUS_ERROR, error.message ?: "Wi-Fi Direct discovery failed.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestPeers() {
        if (!running || !hasDiscoveryPermission()) {
            return
        }
        val activeChannel = channel ?: return
        try {
            p2pManager?.requestPeers(activeChannel, ::processPeers)
        } catch (error: SecurityException) {
            publishState(STATUS_PERMISSION_REQUIRED, error.message)
        } catch (error: RuntimeException) {
            debug("Could not request Wi-Fi Direct peers: ${error.message}")
        }
    }

    private fun processPeers(peerList: WifiP2pDeviceList) {
        val nowMs = System.currentTimeMillis()
        peerList.deviceList.forEach { device ->
            val name = device.deviceName?.trim().orEmpty()
            debug("P2P peer ${name.ifBlank { "<unnamed>" }} ${device.deviceAddress.orEmpty()}")
            if (MediaTransferMatcher.matches(name)) {
                listener.onMediaTransferObservation(
                    MediaTransferObservation(
                        observedName = name,
                        address = device.deviceAddress?.takeUnless(String::isBlank),
                        source = SOURCE_WIFI_P2P,
                        rssi = null,
                        frequencyMhz = null,
                        channel = null,
                        timestampMs = nowMs,
                    ),
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun requestConventionalWifiScanIfAvailable() {
        when {
            !hasFineLocationAccess() -> publishState(
                STATUS_PERMISSION_REQUIRED,
                "Precise location access is required for nearby Wi-Fi network scans.",
            )

            wifiManager?.isWifiEnabled != true -> publishState(
                STATUS_WIFI_DISABLED,
                "Turn on Wi-Fi to enable discovery.",
            )

            !isLocationModeEnabled() -> publishState(
                STATUS_LOCATION_DISABLED,
                "Turn on Location services to enable nearby Wi-Fi network scans.",
            )

            else -> requestConventionalWifiScan()
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun requestConventionalWifiScan() {
        val intervalSeconds = scanIntervalSeconds()
        val nowElapsedMs = SystemClock.elapsedRealtime()
        if (!wifiScanCadencePolicy.canAttempt(nowElapsedMs)) {
            debug(
                "Nearby Wi-Fi network scan skipped: a previous request is still in flight " +
                    "(configured interval=${intervalSeconds}s)",
            )
            return
        }
        try {
            val accepted = wifiManager?.startScan() == true
            wifiScanCadencePolicy.markAttempt(nowElapsedMs, accepted)
            debug(
                "Nearby Wi-Fi network scan attempted: accepted=$accepted " +
                    "interval=${intervalSeconds}s throttle=${scanThrottleLabel()}",
            )
        } catch (error: SecurityException) {
            wifiScanCadencePolicy.markAttempt(nowElapsedMs, false)
            debug("Nearby Wi-Fi network scan permission unavailable: ${error.message}")
            publishState(
                STATUS_PERMISSION_REQUIRED,
                "Precise location access is unavailable for background Wi-Fi scans.",
            )
        } catch (error: RuntimeException) {
            wifiScanCadencePolicy.markAttempt(nowElapsedMs, false)
            debug("Nearby Wi-Fi network scan failed: ${error.message}")
        }
    }

    private fun scheduleNextWifiScan() {
        handler.removeCallbacks(wifiScanRunnable)
        handler.postDelayed(wifiScanRunnable, observationSchedule.networkScanDelayMs())
    }

    private fun scheduleNextPeerSample() {
        handler.removeCallbacks(peerSampleRunnable)
        handler.postDelayed(peerSampleRunnable, observationSchedule.peerSampleDelayMs())
    }

    private fun canSampleWifiDirectPeers(): Boolean =
        p2pManager != null &&
            channel != null &&
            hasDiscoveryPermission() &&
            wifiManager?.isWifiEnabled == true &&
            isLocationModeEnabled()

    private fun scanIntervalSeconds(): Int = observationSchedule.intervalSeconds()

    private fun scanThrottleLabel(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                if (wifiManager?.isScanThrottleEnabled == true) "enabled" else "disabled"
            } catch (_: RuntimeException) {
                "unknown"
            }
        } else {
            "unavailable"
        }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun processWifiScanResults() {
        val results = try {
            wifiManager?.scanResults.orEmpty()
        } catch (error: SecurityException) {
            debug("Could not read Wi-Fi scan results: ${error.message}")
            publishState(
                STATUS_PERMISSION_REQUIRED,
                "Precise location access is unavailable for Wi-Fi scan results.",
            )
            return
        }
        val nowMs = System.currentTimeMillis()
        results.forEach { result ->
            val ssid = result.SSID?.trim()?.trim('"').orEmpty()
            if (MediaTransferMatcher.matches(ssid)) {
                listener.onMediaTransferObservation(
                    MediaTransferObservation(
                        observedName = ssid,
                        address = result.BSSID?.takeUnless(String::isBlank),
                        source = SOURCE_WIFI_SCAN,
                        rssi = result.level,
                        frequencyMhz = result.frequency,
                        channel = channelForFrequency(result.frequency),
                        timestampMs = nowMs,
                    ),
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopPeerDiscovery() {
        val activeChannel = channel ?: return
        if (!hasDiscoveryPermission()) {
            return
        }
        try {
            p2pManager?.stopPeerDiscovery(activeChannel, null)
        } catch (_: RuntimeException) {
            // Discovery is best effort and may already be stopped.
        }
    }

    private fun hasDiscoveryPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return PermissionChecker.checkSelfPermission(context, permission) ==
            PermissionChecker.PERMISSION_GRANTED
    }

    private fun hasFineLocationAccess(): Boolean =
        PermissionChecker.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PermissionChecker.PERMISSION_GRANTED

    private fun isLocationModeEnabled(): Boolean {
        val locationManager = context.getSystemService(LocationManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager?.isLocationEnabled == true
        } else {
            @Suppress("DEPRECATION")
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_OFF,
            ) != Settings.Secure.LOCATION_MODE_OFF
        }
    }

    private fun publishState(status: String, message: String? = null) {
        val nextState = WifiDiscoveryState(status = status, message = message)
        if (nextState.status == currentState.status && nextState.message == currentState.message) {
            return
        }
        currentState = nextState
        listener.onWifiDiscoveryStateChanged(nextState)
    }

    private fun debug(message: String) {
        if (debugEnabled) {
            listener.onWifiDebugMessage(message)
        }
    }

    private fun reasonLabel(reason: Int): String = when (reason) {
        WifiP2pManager.BUSY -> "busy"
        WifiP2pManager.P2P_UNSUPPORTED -> "unsupported"
        else -> "error"
    }

    companion object {
        const val STATUS_INACTIVE = "inactive"
        const val STATUS_DISCOVERING = "discovering"
        const val STATUS_PERMISSION_REQUIRED = "permissionRequired"
        const val STATUS_WIFI_DISABLED = "wifiDisabled"
        const val STATUS_LOCATION_DISABLED = "locationDisabled"
        const val STATUS_UNSUPPORTED = "unsupported"
        const val STATUS_ERROR = "error"

        const val SOURCE_WIFI_P2P = "wifiP2p"
        const val SOURCE_WIFI_SCAN = "wifiScan"

        private const val CHANNEL_RETRY_MS = 2_000L

        fun channelForFrequency(frequencyMhz: Int): Int? = when (frequencyMhz) {
            2_484 -> 14
            in 2_412..2_472 -> (frequencyMhz - 2_407) / 5
            in 5_000..5_899 -> (frequencyMhz - 5_000) / 5
            in 5_955..7_115 -> (frequencyMhz - 5_950) / 5
            else -> null
        }
    }
}
