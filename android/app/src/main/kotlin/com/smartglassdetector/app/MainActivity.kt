package com.smartglassdetector.app

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.media.RingtoneManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.smartglassdetector.app.model.DetectionEvent
import com.smartglassdetector.app.model.MediaTransferCandidate
import com.smartglassdetector.app.model.MediaTransferObservation
import com.smartglassdetector.app.model.NearbyDevice
import com.smartglassdetector.app.model.WifiDiscoveryState
import com.smartglassdetector.app.scanner.WifiMediaTransferObserver
import com.smartglassdetector.app.scanner.ScanSessionPolicy
import com.smartglassdetector.app.util.PreferencesManager
import com.smartglassdetector.app.util.AlertSoundPlayer
import com.smartglassdetector.app.util.VibrationPlayer
import com.smartglassdetector.app.widget.ScanPrerequisiteEvaluator
import com.smartglassdetector.app.widget.ScanPrerequisiteIssue
import com.smartglassdetector.app.widget.SmartglassWidgetUpdater
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.File

class MainActivity : FlutterActivity(), BluetoothScanService.Listener {
    private lateinit var preferences: PreferencesManager
    private var eventSink: EventChannel.EventSink? = null
    private var scanService: BluetoothScanService? = null
    private var serviceBound = false
    private var pendingScanPermissionResult: MethodChannel.Result? = null
    private var pendingWifiPermissionResult: MethodChannel.Result? = null
    private var pendingNotificationResult: MethodChannel.Result? = null
    private var pendingBluetoothResult: MethodChannel.Result? = null
    private var pendingSoundPickerResult: MethodChannel.Result? = null
    private val previewSoundPlayer by lazy { AlertSoundPlayer(this) }
    private val scanPrerequisites by lazy { ScanPrerequisiteEvaluator(this) }
    private var activityVisible = false
    private var startScanWhenVisible = false
    private var bluetoothStateReceiverRegistered = false

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) {
                return
            }
            val adapterState = intent.getIntExtra(
                BluetoothAdapter.EXTRA_STATE,
                BluetoothAdapter.ERROR,
            )
            if (adapterState == BluetoothAdapter.STATE_ON ||
                adapterState == BluetoothAdapter.STATE_OFF
            ) {
                eventSink?.success(
                    mapOf(
                        "type" to "bluetoothState",
                        "enabled" to (adapterState == BluetoothAdapter.STATE_ON),
                    ),
                )
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? BluetoothScanService.LocalBinder ?: return
            scanService = localBinder.service()
            serviceBound = true
            scanService?.addListener(this@MainActivity)
            scanService?.onAppVisibilityChanged(activityVisible)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            scanService = null
            serviceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = PreferencesManager(this)
        captureVisibleScanRequest(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureVisibleScanRequest(intent)
        if (activityVisible) {
            prepareVisibleScanRequest()
            SmartglassWidgetUpdater.refreshPrerequisites(this, force = true)
            reconcileRequestedScanSession()
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, METHOD_CHANNEL).setMethodCallHandler(
            ::handleMethodCall,
        )
        EventChannel(flutterEngine.dartExecutor.binaryMessenger, EVENT_CHANNEL).setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    eventSink = events
                    if (BluetoothScanService.serviceAlive) {
                        bindScanService()
                    }
                }

                override fun onCancel(arguments: Any?) {
                    eventSink = null
                }
            },
        )
    }

    override fun onStart() {
        super.onStart()
        activityVisible = true
        prepareVisibleScanRequest()
        registerBluetoothStateReceiver()
        SmartglassWidgetUpdater.refreshPrerequisites(this, force = true)
        reconcileRequestedScanSession()
    }

    override fun onStop() {
        activityVisible = false
        unregisterBluetoothStateReceiver()
        scanService?.onAppVisibilityChanged(false)
        super.onStop()
    }

    override fun onDestroy() {
        unregisterBluetoothStateReceiver()
        unbindScanService()
        pendingScanPermissionResult = null
        pendingWifiPermissionResult = null
        pendingNotificationResult = null
        pendingBluetoothResult = null
        pendingSoundPickerResult = null
        startScanWhenVisible = false
        previewSoundPlayer.stop()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            SCAN_PERMISSION_REQUEST_CODE -> {
                if (hasBaseScanPermissions()) {
                    requestBackgroundPermissionIfNeeded()
                } else {
                    finishScanPermissionRequest()
                }
            }

            BACKGROUND_PERMISSION_REQUEST_CODE -> {
                finishScanPermissionRequest()
            }
            WIFI_PERMISSION_REQUEST_CODE -> finishWifiPermissionRequest()
            NOTIFICATION_PERMISSION_REQUEST_CODE -> finishNotificationPermissionRequest()
        }
        SmartglassWidgetUpdater.refreshPrerequisites(this, force = true)
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            BLUETOOTH_ENABLE_REQUEST_CODE -> finishBluetoothEnableRequest(resultCode)
            ALERT_SOUND_REQUEST_CODE -> finishAlertSoundRequest(resultCode, data)
        }
        SmartglassWidgetUpdater.refreshPrerequisites(this, force = true)
    }

    private fun finishBluetoothEnableRequest(resultCode: Int) {
        val result = pendingBluetoothResult ?: return
        pendingBluetoothResult = null
        result.success(
            mapOf(
                "status" to if (resultCode == Activity.RESULT_OK && isBluetoothEnabled()) {
                    "enabled"
                } else {
                    "declined"
                },
            ),
        )
    }

    @Suppress("DEPRECATION")
    private fun finishAlertSoundRequest(resultCode: Int, data: Intent?) {
        val result = pendingSoundPickerResult ?: return
        pendingSoundPickerResult = null
        if (resultCode != Activity.RESULT_OK) {
            result.success(mapOf("cancelled" to true))
            return
        }

        val pickedUri = data?.getParcelableExtra(
            RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
        ) as? Uri
        val uriValue = pickedUri?.toString().orEmpty()
        val displayName = when {
            pickedUri == null -> "None"
            uriValue == PreferencesManager.DEFAULT_ALERT_SOUND_URI ->
                PreferencesManager.DEFAULT_ALERT_SOUND_NAME
            else -> RingtoneManager.getRingtone(this, pickedUri)
                ?.getTitle(this)
                ?.takeIf(String::isNotBlank)
                ?: "Selected notification sound"
        }
        result.success(
            mapOf(
                "cancelled" to false,
                "uri" to uriValue,
                "name" to displayName,
            ),
        )
    }

    override fun onDetection(event: DetectionEvent) {
        runOnUiThread { eventSink?.success(event.toMap()) }
    }

    override fun onNearbyDevicesChanged(devices: List<NearbyDevice>) {
        runOnUiThread {
            eventSink?.success(
                mapOf(
                    "type" to "nearbyDevices",
                    "devices" to devices.map(NearbyDevice::toMap),
                ),
            )
        }
    }

    override fun onRecentDevicesChanged(devices: List<NearbyDevice>) {
        runOnUiThread {
            eventSink?.success(
                mapOf(
                    "type" to "recentDevices",
                    "devices" to devices.map(NearbyDevice::toMap),
                ),
            )
        }
    }

    override fun onMediaTransferObservation(observation: MediaTransferObservation) {
        runOnUiThread {
            eventSink?.success(
                observation.toMap() + mapOf("type" to "mediaTransferObservation"),
            )
        }
    }

    override fun onMediaTransferCandidatesChanged(candidates: List<MediaTransferCandidate>) {
        runOnUiThread {
            eventSink?.success(
                mapOf(
                    "type" to "mediaTransferCandidates",
                    "candidates" to candidates.map(MediaTransferCandidate::toMap),
                ),
            )
        }
    }

    override fun onWifiDiscoveryStateChanged(state: WifiDiscoveryState) {
        runOnUiThread {
            eventSink?.success(
                mapOf(
                    "type" to "wifiDiscoveryState",
                    "state" to state.toMap(),
                ),
            )
        }
    }

    override fun onScanStateChanged(state: String, errorCode: Int?, message: String?) {
        runOnUiThread {
            eventSink?.success(
                mapOf(
                    "type" to "scanState",
                    "state" to state,
                    "errorCode" to errorCode,
                    "message" to message,
                ),
            )
        }
    }

    override fun onDebugMessage(message: String) {
        runOnUiThread {
            eventSink?.success(
                mapOf(
                    "type" to "debug",
                    "timestampMs" to System.currentTimeMillis(),
                    "message" to message,
                ),
            )
        }
    }

    private fun handleMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getAppState" -> result.success(appState())
            "getOnboardingState" -> result.success(onboardingState())
            "requestPermissions", "requestCoreScanPermissions" ->
                requestCoreScanPermissions(result)
            "requestWifiDiscoveryPermission" -> requestWifiDiscoveryPermission(result)
            "requestNotificationPermission" -> requestNotificationPermission(result)
            "requestEnableBluetooth" -> requestEnableBluetooth(result)
            "startScan" -> startScan(result)
            "stopScan" -> stopScan(result)
            "getSettings" -> result.success(preferences.settingsMap())
            "updateSettings" -> updateSettings(call, result)
            "completeOnboarding" -> completeOnboarding(result)
            "previewVibration" -> previewVibration(call, result)
            "pickAlertSound" -> pickAlertSound(call, result)
            "previewAlertSound" -> previewAlertSound(call, result)
            "openAppSettings" -> openAppSettings(result)
            "openAboutPhone" -> openAboutPhone(result)
            "openDeveloperOptions" -> openDeveloperOptions(result)
            "requestBatteryOptimizationExemption" ->
                requestBatteryOptimizationExemption(result)
            "exportLog" -> exportLog(call, result)
            else -> result.notImplemented()
        }
    }

    private fun appState(): Map<String, Any> = mapOf(
        "bleSupported" to scanPrerequisites.isBleSupported(),
        "bluetoothEnabled" to isBluetoothEnabled(),
        "permissionsGranted" to hasRequiredScanPermissions(),
        "notificationPermissionGranted" to hasNotificationPermission(),
        "scanning" to BluetoothScanService.scanning,
        "serviceAlive" to BluetoothScanService.serviceAlive,
        "nearbyDevices" to (scanService?.currentNearbyDevices()?.map(NearbyDevice::toMap) ?: emptyList()),
        "recentDevices" to (scanService?.currentRecentDevices()?.map(NearbyDevice::toMap) ?: emptyList()),
        "mediaTransferCandidates" to (
            scanService?.currentMediaTransferCandidates()?.map(MediaTransferCandidate::toMap)
                ?: emptyList()
            ),
        "wifiDiscoveryPermissionGranted" to hasWifiDiscoveryPermission(),
        "wifiDiscoveryState" to (
            scanService?.currentWifiDiscoveryState()?.toMap()
                ?: WifiDiscoveryState(
                    status = if (hasWifiDiscoveryPermission()) {
                        WifiMediaTransferObserver.STATUS_INACTIVE
                    } else {
                        WifiMediaTransferObserver.STATUS_PERMISSION_REQUIRED
                    },
                ).toMap()
            ),
    )

    private fun requestCoreScanPermissions(result: MethodChannel.Result) {
        if (hasRequiredScanPermissions()) {
            result.success(corePermissionResult())
            return
        }
        if (pendingScanPermissionResult != null) {
            result.error("permission_request_active", "A scan permission request is already active", null)
            return
        }
        pendingScanPermissionResult = result
        val deniedBasePermissions = baseScanPermissions().filterNot(::hasPermission)
        if (deniedBasePermissions.isNotEmpty()) {
            preferences.corePermissionRequested = true
            ActivityCompat.requestPermissions(
                this,
                deniedBasePermissions.toTypedArray(),
                SCAN_PERMISSION_REQUEST_CODE,
            )
        } else {
            requestBackgroundPermissionIfNeeded()
        }
    }

    private fun requestBackgroundPermissionIfNeeded() {
        val backgroundPermission = backgroundScanPermission()
        if (backgroundPermission != null && !hasPermission(backgroundPermission)) {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                preferences.corePermissionRequested = true
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(backgroundPermission),
                    BACKGROUND_PERMISSION_REQUEST_CODE,
                )
            } else {
                // Android 11 removes "Allow all the time" from the runtime dialog.
                // Onboarding directs the user to the app's permission settings instead.
                finishScanPermissionRequest()
            }
        } else {
            finishScanPermissionRequest()
        }
    }

    private fun finishScanPermissionRequest() {
        val result = pendingScanPermissionResult ?: return
        pendingScanPermissionResult = null
        result.success(corePermissionResult())
    }

    private fun corePermissionResult(): Map<String, Any> {
        val denied = requiredScanPermissions().filterNot(::hasPermission)
        val backgroundPermission = backgroundScanPermission()
        val deniedForDialog = denied.filterNot {
            Build.VERSION.SDK_INT == Build.VERSION_CODES.R && it == backgroundPermission
        }
        val permanentlyDenied = preferences.corePermissionRequested && deniedForDialog.any {
            !ActivityCompat.shouldShowRequestPermissionRationale(this, it)
        }
        return mapOf(
            "granted" to denied.isEmpty(),
            "permanentlyDenied" to permanentlyDenied,
            "deniedPermissions" to denied,
            "basePermissionsGranted" to hasBaseScanPermissions(),
            "backgroundLocationRequired" to (backgroundPermission != null),
            "backgroundLocationGranted" to (
                backgroundPermission == null || hasPermission(backgroundPermission)
                ),
            "backgroundLocationSettingsRequired" to (
                Build.VERSION.SDK_INT == Build.VERSION_CODES.R &&
                    hasBaseScanPermissions() && backgroundPermission != null &&
                    !hasPermission(backgroundPermission)
                ),
        )
    }

    private fun requestWifiDiscoveryPermission(result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            hasWifiDiscoveryPermission()
        ) {
            result.success(wifiPermissionResult())
            return
        }
        if (pendingWifiPermissionResult != null) {
            result.error("wifi_permission_request_active", "A Wi-Fi permission request is active", null)
            return
        }
        pendingWifiPermissionResult = result
        preferences.wifiPermissionRequested = true
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES),
            WIFI_PERMISSION_REQUEST_CODE,
        )
    }

    private fun finishWifiPermissionRequest() {
        val result = pendingWifiPermissionResult ?: return
        pendingWifiPermissionResult = null
        result.success(wifiPermissionResult())
    }

    private fun wifiPermissionResult(): Map<String, Any> {
        val granted = hasWifiDiscoveryPermission()
        val permanentlyDenied = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            preferences.wifiPermissionRequested && !granted &&
            !ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.NEARBY_WIFI_DEVICES,
            )
        return mapOf(
            "granted" to granted,
            "permanentlyDenied" to permanentlyDenied,
            "runtimePermissionRequired" to (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU),
        )
    }

    private fun requestNotificationPermission(result: MethodChannel.Result) {
        if (hasNotificationPermission()) {
            result.success(notificationPermissionResult())
            return
        }
        if (pendingNotificationResult != null) {
            result.error("notification_request_active", "A notification permission request is active", null)
            return
        }
        pendingNotificationResult = result
        preferences.notificationPermissionRequested = true
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            NOTIFICATION_PERMISSION_REQUEST_CODE,
        )
    }

    private fun finishNotificationPermissionRequest() {
        val result = pendingNotificationResult ?: return
        pendingNotificationResult = null
        result.success(notificationPermissionResult())
    }

    private fun notificationPermissionResult(): Map<String, Any> {
        val granted = hasNotificationPermission()
        val permanentlyDenied = !granted && preferences.notificationPermissionRequested &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        return mapOf(
            "granted" to granted,
            "permanentlyDenied" to permanentlyDenied,
        )
    }

    private fun requestEnableBluetooth(result: MethodChannel.Result) {
        if (!scanPrerequisites.isBleSupported()) {
            result.success(mapOf("status" to "unsupported"))
            return
        }
        if (!hasRequiredScanPermissions()) {
            result.success(mapOf("status" to "permissionRequired"))
            return
        }
        if (isBluetoothEnabled()) {
            result.success(mapOf("status" to "enabled"))
            return
        }
        if (pendingBluetoothResult != null) {
            result.error("bluetooth_request_active", "A Bluetooth request is already active", null)
            return
        }
        pendingBluetoothResult = result
        try {
            startActivityForResult(
                Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE),
                BLUETOOTH_ENABLE_REQUEST_CODE,
            )
        } catch (error: SecurityException) {
            pendingBluetoothResult = null
            result.error("bluetooth_permission", error.message, null)
        }
    }

    private fun startScan(result: MethodChannel.Result) {
        when {
            !scanPrerequisites.isBleSupported() -> {
                result.success(mapOf("status" to "unsupported"))
            }

            !hasRequiredScanPermissions() -> {
                result.success(mapOf("status" to "permissionRequired"))
            }

            !isBluetoothEnabled() -> {
                result.success(mapOf("status" to "bluetoothDisabled"))
            }

            else -> {
                val intent = Intent(this, BluetoothScanService::class.java).apply {
                    action = BluetoothScanService.ACTION_START_SCAN
                }
                preferences.scanningRequested = true
                SmartglassWidgetUpdater.showStarting(this)
                try {
                    ContextCompat.startForegroundService(this, intent)
                    bindScanService()
                    result.success(mapOf("status" to "started"))
                } catch (error: RuntimeException) {
                    preferences.scanningRequested = false
                    SmartglassWidgetUpdater.showStartFailure(this)
                    result.error("scan_start_failed", error.message, null)
                }
            }
        }
    }

    private fun stopScan(result: MethodChannel.Result) {
        preferences.scanningRequested = false
        if (BluetoothScanService.serviceAlive) {
            SmartglassWidgetUpdater.showStopping(this)
            val intent = Intent(this, BluetoothScanService::class.java).apply {
                action = BluetoothScanService.ACTION_STOP_SCAN
            }
            startService(intent)
        } else {
            scanService?.stopScanning()
            SmartglassWidgetUpdater.showStopped(this)
        }
        unbindScanService()
        result.success(mapOf("status" to "stopped"))
    }

    private fun updateSettings(call: MethodCall, result: MethodChannel.Result) {
        val values = call.arguments as? Map<*, *>
        if (values == null) {
            result.error("invalid_settings", "Settings must be a map", null)
            return
        }
        val previousWifiInterval = preferences.wifiScanIntervalSeconds
        preferences.update(values)
        if (preferences.wifiScanIntervalSeconds != previousWifiInterval) {
            scanService?.onSettingsChanged()
        }
        result.success(preferences.settingsMap())
    }

    private fun completeOnboarding(result: MethodChannel.Result) {
        preferences.onboardingVersion = PreferencesManager.CURRENT_ONBOARDING_VERSION
        SmartglassWidgetUpdater.refreshPrerequisites(this, force = true)
        result.success(onboardingState())
    }

    private fun previewVibration(call: MethodCall, result: MethodChannel.Result) {
        val preset = call.argument<String>("preset")
        VibrationPlayer(this).preview(PreferencesManager.normalizeVibrationPreset(preset))
        result.success(true)
    }

    private fun pickAlertSound(call: MethodCall, result: MethodChannel.Result) {
        if (pendingSoundPickerResult != null) {
            result.error("sound_picker_active", "The notification sound picker is already open", null)
            return
        }
        val currentUri = call.argument<String>("currentUri")
            ?: PreferencesManager.DEFAULT_ALERT_SOUND_URI
        val existingUri = currentUri.takeIf(String::isNotEmpty)?.let(Uri::parse)
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                Settings.System.DEFAULT_NOTIFICATION_URI,
            )
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existingUri)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Choose alert sound")
        }
        pendingSoundPickerResult = result
        try {
            startActivityForResult(intent, ALERT_SOUND_REQUEST_CODE)
        } catch (error: Exception) {
            pendingSoundPickerResult = null
            result.error("sound_picker_unavailable", error.message, null)
        }
    }

    private fun previewAlertSound(call: MethodCall, result: MethodChannel.Result) {
        val soundUri = PreferencesManager.normalizeAlertSoundUri(
            call.argument<String>("uri"),
        )
        result.success(previewSoundPlayer.preview(soundUri))
    }

    private fun openAppSettings(result: MethodChannel.Result) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
        startActivity(intent)
        result.success(true)
    }

    private fun openAboutPhone(result: MethodChannel.Result) {
        try {
            startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
        result.success(true)
    }

    private fun openDeveloperOptions(result: MethodChannel.Result) {
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
        result.success(true)
    }

    private fun requestBatteryOptimizationExemption(result: MethodChannel.Result) {
        if (isBatteryOptimizationExempt()) {
            result.success(true)
            return
        }

        val directIntent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName"),
        )
        try {
            startActivity(directIntent)
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
        result.success(true)
    }

    private fun onboardingState(): Map<String, Any?> {
        val core = corePermissionResult()
        val wifi = wifiPermissionResult()
        val notification = notificationPermissionResult()
        val wifiManager = applicationContext.getSystemService(WifiManager::class.java)
        val throttleQuerySupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val throttleEnabled = if (throttleQuerySupported) {
            try {
                wifiManager?.isScanThrottleEnabled
            } catch (_: RuntimeException) {
                null
            }
        } else {
            null
        }
        return mapOf(
            "onboardingVersion" to preferences.onboardingVersion,
            "completed" to (
                preferences.onboardingVersion >= PreferencesManager.CURRENT_ONBOARDING_VERSION
                ),
            "sdkInt" to Build.VERSION.SDK_INT,
            "corePermissionsGranted" to core["granted"],
            "corePermanentlyDenied" to core["permanentlyDenied"],
            "basePermissionsGranted" to core["basePermissionsGranted"],
            "backgroundLocationRequired" to core["backgroundLocationRequired"],
            "backgroundLocationGranted" to core["backgroundLocationGranted"],
            "backgroundLocationSettingsRequired" to core["backgroundLocationSettingsRequired"],
            "wifiRuntimePermissionRequired" to wifi["runtimePermissionRequired"],
            "wifiPermissionGranted" to wifi["granted"],
            "wifiPermissionPermanentlyDenied" to wifi["permanentlyDenied"],
            "notificationRuntimePermissionRequired" to (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ),
            "notificationPermissionGranted" to notification["granted"],
            "notificationPermanentlyDenied" to notification["permanentlyDenied"],
            "batteryOptimizationExempt" to isBatteryOptimizationExempt(),
            "wifiScanThrottleQuerySupported" to throttleQuerySupported,
            "wifiScanThrottleEnabled" to throttleEnabled,
        )
    }

    private fun exportLog(call: MethodCall, result: MethodChannel.Result) {
        val content = call.argument<String>("content")
        if (content.isNullOrBlank()) {
            result.error("empty_log", "There are no detection events to export", null)
            return
        }
        try {
            val directory = File(cacheDir, "shared_logs").apply { mkdirs() }
            val file = File(directory, "smartglass-detections-${System.currentTimeMillis()}.txt")
            file.writeText(content)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Smartglass Detector log")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Export detection log"))
            result.success(true)
        } catch (error: Exception) {
            result.error("export_failed", error.message, null)
        }
    }

    private fun bindScanService() {
        if (serviceBound) {
            return
        }
        bindService(
            Intent(this, BluetoothScanService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE,
        )
    }

    private fun captureVisibleScanRequest(sourceIntent: Intent?) {
        if (sourceIntent?.getBooleanExtra(EXTRA_START_SCAN_WHEN_VISIBLE, false) == true) {
            startScanWhenVisible = true
            sourceIntent.removeExtra(EXTRA_START_SCAN_WHEN_VISIBLE)
        }
    }

    private fun prepareVisibleScanRequest() {
        if (!startScanWhenVisible) {
            return
        }
        startScanWhenVisible = false
        val issue = scanPrerequisites.evaluate().issue
        if (issue != ScanPrerequisiteIssue.NONE) {
            preferences.scanningRequested = false
            SmartglassWidgetUpdater.showPrerequisiteIssue(this, issue)
            return
        }
        preferences.scanningRequested = true
        SmartglassWidgetUpdater.showStarting(this)
    }

    private fun reconcileRequestedScanSession() {
        if (BluetoothScanService.serviceAlive) {
            bindScanService()
            scanService?.onAppVisibilityChanged(true)
            return
        }
        if (!ScanSessionPolicy.shouldStartService(
                scanningRequested = preferences.scanningRequested,
                serviceAlive = BluetoothScanService.serviceAlive,
                permissionsGranted = hasRequiredScanPermissions(),
                bluetoothEnabled = isBluetoothEnabled(),
            )
        ) {
            return
        }

        val intent = Intent(this, BluetoothScanService::class.java).apply {
            action = BluetoothScanService.ACTION_START_SCAN
        }
        try {
            ContextCompat.startForegroundService(this, intent)
            bindScanService()
        } catch (_: SecurityException) {
            // The controller will surface the current permission state.
        } catch (_: IllegalStateException) {
            // A later visible lifecycle transition can retry the requested session.
        }
    }

    private fun registerBluetoothStateReceiver() {
        if (bluetoothStateReceiverRegistered) {
            return
        }
        ContextCompat.registerReceiver(
            this,
            bluetoothStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        bluetoothStateReceiverRegistered = true
    }

    private fun unregisterBluetoothStateReceiver() {
        if (!bluetoothStateReceiverRegistered) {
            return
        }
        try {
            unregisterReceiver(bluetoothStateReceiver)
        } catch (_: IllegalArgumentException) {
            // The receiver may already have been removed during activity teardown.
        }
        bluetoothStateReceiverRegistered = false
    }

    private fun unbindScanService() {
        if (!serviceBound) {
            return
        }
        scanService?.removeListener(this)
        unbindService(serviceConnection)
        scanService = null
        serviceBound = false
    }

    private fun baseScanPermissions(): List<String> = scanPrerequisites.baseScanPermissions()

    private fun backgroundScanPermission(): String? = scanPrerequisites.backgroundScanPermission()

    private fun requiredScanPermissions(): List<String> = scanPrerequisites.requiredScanPermissions()

    private fun hasBaseScanPermissions(): Boolean = scanPrerequisites.hasBaseScanPermissions()

    private fun hasRequiredScanPermissions(): Boolean =
        scanPrerequisites.hasRequiredScanPermissions()

    private fun hasWifiDiscoveryPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            hasPermission(Manifest.permission.NEARBY_WIFI_DEVICES)

    private fun hasPermission(permission: String): Boolean =
        scanPrerequisites.hasPermission(permission)

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            hasPermission(Manifest.permission.POST_NOTIFICATIONS)

    private fun isBatteryOptimizationExempt(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(packageName) ?: false
    }

    private fun isBluetoothEnabled(): Boolean = scanPrerequisites.isBluetoothEnabled()

    companion object {
        private const val METHOD_CHANNEL = "com.smartglassdetector.app/control"
        private const val EVENT_CHANNEL = "com.smartglassdetector.app/events"
        private const val SCAN_PERMISSION_REQUEST_CODE = 4101
        private const val BACKGROUND_PERMISSION_REQUEST_CODE = 4102
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 4103
        private const val BLUETOOTH_ENABLE_REQUEST_CODE = 4104
        private const val WIFI_PERMISSION_REQUEST_CODE = 4105
        private const val ALERT_SOUND_REQUEST_CODE = 4106
        const val EXTRA_START_SCAN_WHEN_VISIBLE =
            "com.smartglassdetector.app.extra.START_SCAN_WHEN_VISIBLE"
    }
}
