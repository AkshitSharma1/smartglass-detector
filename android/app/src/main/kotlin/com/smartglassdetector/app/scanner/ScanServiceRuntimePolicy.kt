package com.smartglassdetector.app.scanner

import android.app.Service
import android.content.pm.ServiceInfo
import android.os.Build

object ScanServiceRuntimePolicy {
    fun foregroundServiceTypes(
        sdkInt: Int,
        foregroundLocationAvailable: Boolean,
    ): Int {
        if (sdkInt < Build.VERSION_CODES.Q) {
            return 0
        }
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        if (foregroundLocationAvailable) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        return types
    }

    fun shouldRestoreAfterProcessRecreation(
        sdkInt: Int,
        scanningRequested: Boolean,
    ): Boolean = scanningRequested && sdkInt < Build.VERSION_CODES.S

    fun restartMode(sdkInt: Int, scanningRequested: Boolean): Int =
        if (shouldRestoreAfterProcessRecreation(sdkInt, scanningRequested)) {
            Service.START_STICKY
        } else {
            Service.START_NOT_STICKY
        }
}
