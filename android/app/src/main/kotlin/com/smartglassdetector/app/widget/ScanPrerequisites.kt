package com.smartglassdetector.app.widget

import android.annotation.SuppressLint
import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.smartglassdetector.app.util.PreferencesManager

enum class ScanPrerequisiteIssue {
    NONE,
    SETUP_REQUIRED,
    PERMISSION_REQUIRED,
    BLUETOOTH_DISABLED,
    UNSUPPORTED,
}

data class ScanPrerequisiteSnapshot(
    val onboardingComplete: Boolean,
    val bleSupported: Boolean,
    val permissionsGranted: Boolean,
    val bluetoothEnabled: Boolean,
) {
    val issue: ScanPrerequisiteIssue
        get() = ScanPrerequisitePolicy.issue(
            onboardingComplete = onboardingComplete,
            bleSupported = bleSupported,
            permissionsGranted = permissionsGranted,
            bluetoothEnabled = bluetoothEnabled,
        )
}

object ScanPrerequisitePolicy {
    fun issue(
        onboardingComplete: Boolean,
        bleSupported: Boolean,
        permissionsGranted: Boolean,
        bluetoothEnabled: Boolean,
    ): ScanPrerequisiteIssue = when {
        !onboardingComplete -> ScanPrerequisiteIssue.SETUP_REQUIRED
        !bleSupported -> ScanPrerequisiteIssue.UNSUPPORTED
        !permissionsGranted -> ScanPrerequisiteIssue.PERMISSION_REQUIRED
        !bluetoothEnabled -> ScanPrerequisiteIssue.BLUETOOTH_DISABLED
        else -> ScanPrerequisiteIssue.NONE
    }
}

class ScanPrerequisiteEvaluator(private val context: Context) {
    private val appContext = context.applicationContext

    fun evaluate(): ScanPrerequisiteSnapshot {
        val preferences = PreferencesManager(appContext)
        return ScanPrerequisiteSnapshot(
            onboardingComplete =
                preferences.onboardingVersion >= PreferencesManager.CURRENT_ONBOARDING_VERSION,
            bleSupported = isBleSupported(),
            permissionsGranted = hasRequiredScanPermissions(),
            bluetoothEnabled = isBluetoothEnabled(),
        )
    }

    fun isBleSupported(): Boolean =
        appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    fun hasBaseScanPermissions(): Boolean = baseScanPermissions().all(::hasPermission)

    fun hasRequiredScanPermissions(): Boolean = requiredScanPermissions().all(::hasPermission)

    fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) ==
            PackageManager.PERMISSION_GRANTED

    fun isBluetoothEnabled(): Boolean {
        val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return try {
            manager?.adapter?.isEnabled == true
        } catch (_: SecurityException) {
            false
        }
    }

    fun baseScanPermissions(): List<String> = baseScanPermissions(Build.VERSION.SDK_INT)

    fun backgroundScanPermission(): String? = backgroundScanPermission(Build.VERSION.SDK_INT)

    fun requiredScanPermissions(): List<String> = buildList {
        addAll(baseScanPermissions())
        backgroundScanPermission()?.let(::add)
    }

    companion object {
        @SuppressLint("InlinedApi")
        fun baseScanPermissions(sdkInt: Int): List<String> =
            if (sdkInt >= Build.VERSION_CODES.S) {
                listOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                )
            } else {
                listOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                )
            }

        @SuppressLint("InlinedApi")
        fun backgroundScanPermission(sdkInt: Int): String? =
            if (sdkInt in Build.VERSION_CODES.Q..Build.VERSION_CODES.R) {
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            } else {
                null
            }
    }
}
