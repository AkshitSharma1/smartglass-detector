package com.smartglassdetector.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smartglassdetector.app.util.NotificationHelper

class AlertDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != BluetoothScanService.ACTION_DISMISS_ALERTS) {
            return
        }
        val serviceIntent = Intent(context, BluetoothScanService::class.java).apply {
            action = BluetoothScanService.ACTION_DISMISS_ALERTS
        }
        if (BluetoothScanService.serviceAlive) {
            try {
                context.startService(serviceIntent)
                return
            } catch (_: RuntimeException) {
                // Fall through and dismiss posted alerts without recreating the scanner.
            }
        }
        NotificationHelper.cancelPostedDetectionNotifications(context)
    }
}
