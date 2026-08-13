package com.smartglassdetector.app.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.smartglassdetector.app.AlertDismissReceiver
import com.smartglassdetector.app.BluetoothScanService
import com.smartglassdetector.app.MainActivity
import com.smartglassdetector.app.R
import com.smartglassdetector.app.model.MediaTransferCandidate
import com.smartglassdetector.app.model.NearbyDevice
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.roundToInt

class NotificationHelper(private val context: Context) {
    private val activeDetectionIds = CopyOnWriteArraySet<Int>()

    init {
        createChannels()
    }

    fun serviceNotification(): Notification = NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_bluetooth_scan)
        .setContentTitle(PresentationCopy.SERVICE_STATUS)
        .setContentText("Smartglass Detector is active in the background")
        .setContentIntent(contentIntent())
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()

    fun canPostDetectionNotifications(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        val channelEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.getNotificationChannel(DETECTION_CHANNEL_ID)?.importance !=
                NotificationManager.IMPORTANCE_NONE
        } else {
            true
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled() && channelEnabled
    }

    @SuppressLint("MissingPermission")
    fun showDetectionNotification(device: NearbyDevice): Int? {
        if (!canPostDetectionNotifications()) {
            return null
        }
        val notificationId = detectionNotificationId(device.deviceId)
        val identity = device.deviceName ?: PresentationCopy.SMARTGLASS_FALLBACK_NAME
        val distance = formatDistance(device.distanceMeters)
        val evidence = PresentationCopy.smartglassEvidence(device.reasonText, device.companyId)
        val detail = buildString {
            append("$identity · Estimated distance: $distance")
            append("\n$evidence")
            append(
                "\nSignal: ${device.smoothedRssi.roundToInt()} dBm · " +
                    "Detection confidence: ${PresentationCopy.confidenceLabel(device.confidence)}",
            )
        }
        val notification = NotificationCompat.Builder(context, DETECTION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bluetooth_scan)
            .setContentTitle(PresentationCopy.SMARTGLASS_ALERT_TITLE)
            .setContentText("$identity · Estimated distance: $distance")
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(contentIntent())
            .addAction(
                R.drawable.ic_bluetooth_scan,
                "Dismiss all",
                dismissIntent(),
            )
            .setDeleteIntent(dismissIntent())
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(0)
            .setAutoCancel(true)
            .setGroup(DETECTION_GROUP_KEY)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            return null
        }
        activeDetectionIds.add(notificationId)
        return notificationId
    }

    @SuppressLint("MissingPermission")
    fun showMediaTransferNotification(candidate: MediaTransferCandidate): Int? {
        if (!canPostDetectionNotifications()) {
            return null
        }
        val notificationId = mediaTransferNotificationId(candidate.sessionId)
        val evidence = candidate.evidence.joinToString(separator = " · ")
        val detail = buildString {
            append(candidate.observedName)
            if (evidence.isNotBlank()) {
                append("\n$evidence")
            }
            append("\nImage/video transfer detected from nearby smartglasses")
            append("\n${PresentationCopy.MEDIA_ALERT_BODY}")
        }
        val notification = NotificationCompat.Builder(context, DETECTION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bluetooth_scan)
            .setContentTitle(PresentationCopy.MEDIA_ALERT_TITLE)
            .setContentText(PresentationCopy.MEDIA_ALERT_BODY)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(contentIntent())
            .addAction(
                R.drawable.ic_bluetooth_scan,
                "Dismiss all",
                dismissIntent(),
            )
            .setDeleteIntent(dismissIntent())
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(0)
            .setAutoCancel(true)
            .setGroup(DETECTION_GROUP_KEY)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            return null
        }
        activeDetectionIds.add(notificationId)
        return notificationId
    }

    fun cancelDetectionNotification(notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
        activeDetectionIds.remove(notificationId)
    }

    fun cancelAllDetectionNotifications() {
        val manager = NotificationManagerCompat.from(context)
        activeDetectionIds.forEach(manager::cancel)
        activeDetectionIds.clear()
    }

    private fun createChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val serviceChannel = NotificationChannel(
            SERVICE_CHANNEL_ID,
            "Background scanning",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows when Bluetooth and Wi-Fi scanning are active"
            setShowBadge(false)
        }
        val detectionChannel = NotificationChannel(
            DETECTION_CHANNEL_ID,
            PresentationCopy.ALERT_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Smartglass proximity and image/video activity alerts"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(true)
        }
        manager.createNotificationChannels(listOf(serviceChannel, detectionChannel))
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun dismissIntent(): PendingIntent {
        val intent = Intent(context, AlertDismissReceiver::class.java).apply {
            action = BluetoothScanService.ACTION_DISMISS_ALERTS
        }
        return PendingIntent.getBroadcast(
            context,
            9001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun formatDistance(meters: Double): String = when {
        meters < 1.0 -> "${(meters * 100).roundToInt()} cm"
        meters < 10.0 -> "%.1f m".format(meters)
        else -> "${meters.roundToInt()} m"
    }

    private fun detectionNotificationId(deviceId: String): Int =
        DETECTION_NOTIFICATION_ID_BASE + (deviceId.hashCode() and 0x0FFFFF)

    private fun mediaTransferNotificationId(sessionId: String): Int =
        MEDIA_TRANSFER_NOTIFICATION_ID_BASE + (sessionId.hashCode() and 0x0FFFFF)

    companion object {
        const val SERVICE_NOTIFICATION_ID = 1001

        fun cancelPostedDetectionNotifications(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.activeNotifications
                .filter { it.notification.group == DETECTION_GROUP_KEY }
                .forEach { manager.cancel(it.id) }
        }

        private const val DETECTION_NOTIFICATION_ID_BASE = 2000
        private const val MEDIA_TRANSFER_NOTIFICATION_ID_BASE = 1_100_000
        private const val SERVICE_CHANNEL_ID = "smartglass_scan_service"
        private const val DETECTION_CHANNEL_ID = "smartglass_detections_visual_v3"
        private const val DETECTION_GROUP_KEY = "smartglass_detection_group"
    }
}
