package com.smartglassdetector.app.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.smartglassdetector.app.model.MediaTransferCandidate
import com.smartglassdetector.app.model.NearbyDevice
import java.util.ArrayDeque

class AlertCoordinator(
    context: Context,
    private val preferences: PreferencesManager,
    private val notificationHelper: NotificationHelper,
) {
    private sealed interface AlertItem {
        val key: String

        data class BleDevice(val device: NearbyDevice) : AlertItem {
            override val key: String = "ble:${device.deviceId}"
        }

        data class MediaTransfer(val candidate: MediaTransferCandidate) : AlertItem {
            override val key: String = "media:${candidate.sessionId}"
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val vibrationPlayer = VibrationPlayer(context)
    private val soundPlayer = AlertSoundPlayer(context)
    private val queue = ArrayDeque<AlertItem>()
    private val shakeDetector = ShakeDetector(context, ::dismissAll)
    private val wakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmartglassDetector:ActiveAlert")
        .apply { setReferenceCounted(false) }
    private var activeNotificationId: Int? = null
    private var activeAlertKey: String? = null
    private val finishRunnable = Runnable(::finishActiveAlert)

    @Synchronized
    fun enqueue(device: NearbyDevice) {
        enqueue(AlertItem.BleDevice(device))
    }

    @Synchronized
    fun enqueue(candidate: MediaTransferCandidate) {
        enqueue(AlertItem.MediaTransfer(candidate))
    }

    private fun enqueue(item: AlertItem) {
        if (!preferences.alertsEnabled) {
            return
        }
        if (activeAlertKey == item.key || queue.any { it.key == item.key }) {
            return
        }
        queue.addLast(item)
        startNextIfIdle()
    }

    @Synchronized
    fun dismissAll() {
        handler.removeCallbacks(finishRunnable)
        queue.clear()
        vibrationPlayer.cancel()
        soundPlayer.stop()
        shakeDetector.stop()
        notificationHelper.cancelAllDetectionNotifications()
        activeNotificationId = null
        activeAlertKey = null
        releaseWakeLock()
    }

    @Synchronized
    private fun startNextIfIdle() {
        if (activeAlertKey != null) {
            return
        }
        val item = queue.pollFirst() ?: return
        activeAlertKey = item.key
        val notificationId = when (item) {
            is AlertItem.BleDevice -> notificationHelper.showDetectionNotification(item.device)
            is AlertItem.MediaTransfer ->
                notificationHelper.showMediaTransferNotification(item.candidate)
        }
        activeNotificationId = notificationId
        val durationMs = preferences.alertDurationMs
        val delivery = AlertDeliveryPolicy.resolve(
            alertMode = preferences.alertMode,
            soundUri = preferences.alertSoundUri,
        )
        if (delivery.playSound) {
            soundPlayer.playLooping(preferences.alertSoundUri)
        }
        if (delivery.playVibration) {
            vibrationPlayer.playRepeating(preferences.vibrationPreset)
        }
        shakeDetector.start()
        acquireWakeLock(durationMs + 1_000L)
        handler.postDelayed(finishRunnable, durationMs)
    }

    @Synchronized
    private fun finishActiveAlert() {
        val notificationId = activeNotificationId
        if (notificationId != null) {
            notificationHelper.cancelDetectionNotification(notificationId)
        }
        vibrationPlayer.cancel()
        soundPlayer.stop()
        shakeDetector.stop()
        activeNotificationId = null
        activeAlertKey = null
        releaseWakeLock()
        startNextIfIdle()
    }

    @Suppress("WakelockTimeout")
    private fun acquireWakeLock(timeoutMs: Long) {
        wakeLock.acquire(timeoutMs)
    }

    private fun releaseWakeLock() {
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }
}
