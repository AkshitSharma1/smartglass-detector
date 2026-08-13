package com.smartglassdetector.app.scanner

object ScanSessionPolicy {
    fun shouldStartService(
        scanningRequested: Boolean,
        serviceAlive: Boolean,
        permissionsGranted: Boolean,
        bluetoothEnabled: Boolean,
    ): Boolean = scanningRequested &&
        !serviceAlive &&
        permissionsGranted &&
        bluetoothEnabled
}
