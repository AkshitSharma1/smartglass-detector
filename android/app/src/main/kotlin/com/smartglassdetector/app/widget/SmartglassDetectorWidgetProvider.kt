package com.smartglassdetector.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.smartglassdetector.app.BluetoothScanService
import com.smartglassdetector.app.MainActivity
import com.smartglassdetector.app.R
import com.smartglassdetector.app.util.PreferencesManager

class SmartglassDetectorWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_STOP -> {
                handleStop(context)
                return
            }
        }
        super.onReceive(context, intent)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        SmartglassWidgetUpdater.refreshPrerequisites(context, force = true)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        SmartglassWidgetUpdater.refreshPrerequisites(
            context = context,
            force = true,
            requestedIds = appWidgetIds,
        )
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        SmartglassWidgetUpdater.updateWidgets(context, intArrayOf(appWidgetId))
    }

    private fun handleStop(context: Context) {
        val appContext = context.applicationContext
        val preferences = PreferencesManager(appContext)
        preferences.scanningRequested = false

        if (!BluetoothScanService.serviceAlive) {
            appContext.stopService(Intent(appContext, BluetoothScanService::class.java))
            SmartglassWidgetUpdater.showStopped(appContext)
            return
        }

        SmartglassWidgetUpdater.showStopping(appContext)
        val intent = Intent(appContext, BluetoothScanService::class.java).apply {
            action = BluetoothScanService.ACTION_STOP_SCAN
        }
        try {
            appContext.startService(intent)
        } catch (_: RuntimeException) {
            appContext.stopService(intent)
            SmartglassWidgetUpdater.showStopped(appContext)
        }
    }

    companion object {
        private const val ACTION_STOP =
            "com.smartglassdetector.app.widget.action.STOP_DETECTION"

        internal fun actionPendingIntent(
            context: Context,
            appWidgetId: Int,
            action: SmartglassWidgetAction,
        ): PendingIntent? = when (SmartglassWidgetActionRouting.target(action)) {
            SmartglassWidgetActionTarget.VISIBLE_ACTIVITY_START ->
                startAppPendingIntent(context, appWidgetId)
            SmartglassWidgetActionTarget.STOP_BROADCAST -> {
                val intent = Intent(context, SmartglassDetectorWidgetProvider::class.java).apply {
                    this.action = ACTION_STOP
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                }
                PendingIntent.getBroadcast(
                    context,
                    STOP_REQUEST_CODE_BASE + appWidgetId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

            SmartglassWidgetActionTarget.OPEN_ACTIVITY ->
                openAppPendingIntent(context, appWidgetId)
            SmartglassWidgetActionTarget.NONE -> null
        }

        internal fun startAppPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_START_SCAN_WHEN_VISIBLE, true)
            }
            return PendingIntent.getActivity(
                context,
                START_REQUEST_CODE_BASE + appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        internal fun openAppPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            return PendingIntent.getActivity(
                context,
                OPEN_APP_REQUEST_CODE_BASE + appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private const val START_REQUEST_CODE_BASE = 20_000
        private const val STOP_REQUEST_CODE_BASE = 40_000
        private const val OPEN_APP_REQUEST_CODE_BASE = 60_000
    }
}

object SmartglassWidgetUpdater {
    fun refreshPrerequisites(
        context: Context,
        force: Boolean = false,
        requestedIds: IntArray? = null,
    ) {
        val appContext = context.applicationContext
        val store = SmartglassWidgetStateStore(appContext)
        val previous = store.read()
        val scanningRequested = PreferencesManager(appContext).scanningRequested
        val issue = ScanPrerequisiteEvaluator(appContext).evaluate().issue.toWidgetIssue()
        val scannerState = when {
            scanningRequested && !BluetoothScanService.serviceAlive ->
                SmartglassWidgetScannerState.STARTING
            !scanningRequested -> SmartglassWidgetScannerState.STOPPED
            else -> previous.scannerState
        }
        val effectiveIssue = if (
            previous.scannerState == SmartglassWidgetScannerState.ERROR &&
            issue == SmartglassWidgetIssue.NONE
        ) {
            SmartglassWidgetIssue.ATTENTION
        } else {
            issue
        }
        val snapshot = previous.copy(
            scannerState = scannerState,
            scanningRequested = scanningRequested,
            recentCount = if (scanningRequested && BluetoothScanService.serviceAlive) {
                previous.recentCount
            } else {
                0
            },
            issue = effectiveIssue,
            lastUpdatedMs = System.currentTimeMillis(),
        )
        val changed = store.write(snapshot)
        if (force || changed) {
            updateWidgets(appContext, requestedIds)
        }
    }

    fun publishScanState(
        context: Context,
        state: String,
        scanningRequested: Boolean,
    ) {
        val appContext = context.applicationContext
        val store = SmartglassWidgetStateStore(appContext)
        val previous = store.read()
        val scannerState = state.toWidgetScannerState()
        val prerequisiteIssue = ScanPrerequisiteEvaluator(appContext).evaluate().issue.toWidgetIssue()
        val issue = if (scannerState == SmartglassWidgetScannerState.ERROR) {
            prerequisiteIssue.takeUnless { it == SmartglassWidgetIssue.NONE }
                ?: SmartglassWidgetIssue.ATTENTION
        } else {
            prerequisiteIssue
        }
        writeAndUpdate(
            appContext,
            previous.copy(
                scannerState = scannerState,
                scanningRequested = scanningRequested,
                recentCount = if (scannerState == SmartglassWidgetScannerState.STOPPED) {
                    0
                } else {
                    previous.recentCount
                },
                issue = issue,
                lastUpdatedMs = System.currentTimeMillis(),
            ),
        )
    }

    fun publishRecentCount(context: Context, recentCount: Int) {
        val appContext = context.applicationContext
        val store = SmartglassWidgetStateStore(appContext)
        val previous = store.read()
        writeAndUpdate(
            appContext,
            previous.copy(
                recentCount = recentCount.coerceAtLeast(0),
                lastUpdatedMs = System.currentTimeMillis(),
            ),
        )
    }

    fun showStarting(context: Context) {
        val store = SmartglassWidgetStateStore(context)
        writeAndUpdate(
            context,
            store.read().copy(
                scannerState = SmartglassWidgetScannerState.STARTING,
                scanningRequested = true,
                recentCount = 0,
                issue = SmartglassWidgetIssue.NONE,
                lastUpdatedMs = System.currentTimeMillis(),
            ),
        )
    }

    fun showStopping(context: Context) {
        val store = SmartglassWidgetStateStore(context)
        writeAndUpdate(
            context,
            store.read().copy(
                scannerState = SmartglassWidgetScannerState.STOPPING,
                scanningRequested = false,
                issue = SmartglassWidgetIssue.NONE,
                lastUpdatedMs = System.currentTimeMillis(),
            ),
        )
    }

    fun showStopped(context: Context) {
        val store = SmartglassWidgetStateStore(context)
        val issue = ScanPrerequisiteEvaluator(context).evaluate().issue.toWidgetIssue()
        writeAndUpdate(
            context,
            store.read().copy(
                scannerState = SmartglassWidgetScannerState.STOPPED,
                scanningRequested = false,
                recentCount = 0,
                issue = issue,
                lastUpdatedMs = System.currentTimeMillis(),
            ),
        )
    }

    fun showPrerequisiteIssue(context: Context, issue: ScanPrerequisiteIssue) {
        val store = SmartglassWidgetStateStore(context)
        writeAndUpdate(
            context,
            store.read().copy(
                scannerState = SmartglassWidgetScannerState.STOPPED,
                scanningRequested = false,
                recentCount = 0,
                issue = issue.toWidgetIssue(),
                lastUpdatedMs = System.currentTimeMillis(),
            ),
        )
    }

    fun showStartFailure(context: Context) {
        val store = SmartglassWidgetStateStore(context)
        writeAndUpdate(
            context,
            store.read().copy(
                scannerState = SmartglassWidgetScannerState.ERROR,
                scanningRequested = false,
                recentCount = 0,
                issue = SmartglassWidgetIssue.ATTENTION,
                lastUpdatedMs = System.currentTimeMillis(),
            ),
        )
    }

    fun onServiceDestroyed(context: Context, scanningRequested: Boolean) {
        val store = SmartglassWidgetStateStore(context)
        val issue = ScanPrerequisiteEvaluator(context).evaluate().issue.toWidgetIssue()
        writeAndUpdate(
            context,
            store.read().copy(
                scannerState = if (scanningRequested) {
                    SmartglassWidgetScannerState.STARTING
                } else {
                    SmartglassWidgetScannerState.STOPPED
                },
                scanningRequested = scanningRequested,
                recentCount = 0,
                issue = issue,
                lastUpdatedMs = System.currentTimeMillis(),
            ),
        )
    }

    fun updateWidgets(context: Context, requestedIds: IntArray? = null) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val component = ComponentName(appContext, SmartglassDetectorWidgetProvider::class.java)
        val installedIds = manager.getAppWidgetIds(component)
        val targets = SmartglassWidgetUpdateTargets.resolve(requestedIds, installedIds)
        if (targets.isEmpty()) {
            return
        }
        val snapshot = SmartglassWidgetStateStore(appContext).read()
        val viewState = SmartglassWidgetStateReducer.reduce(
            snapshot = snapshot,
            serviceAlive = BluetoothScanService.serviceAlive,
        )
        targets.forEach { appWidgetId ->
            manager.updateAppWidget(
                appWidgetId,
                remoteViewsFor(appContext, manager, appWidgetId, viewState),
            )
        }
    }

    private fun writeAndUpdate(context: Context, snapshot: SmartglassWidgetSnapshot) {
        if (SmartglassWidgetStateStore(context).write(snapshot)) {
            updateWidgets(context)
        }
    }

    private fun remoteViewsFor(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        state: SmartglassWidgetViewState,
    ): RemoteViews {
        val compact = configuredRemoteViews(
            context,
            R.layout.widget_detection_compact,
            appWidgetId,
            state,
        )
        val expanded = configuredRemoteViews(
            context,
            R.layout.widget_detection_expanded,
            appWidgetId,
            state,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return RemoteViews(
                mapOf(
                    SizeF(COMPACT_MIN_WIDTH_DP, COMPACT_MIN_HEIGHT_DP) to compact,
                    SizeF(EXPANDED_MIN_WIDTH_DP, EXPANDED_MIN_HEIGHT_DP) to expanded,
                ),
            )
        }

        val options = manager.getAppWidgetOptions(appWidgetId)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        return if (minWidth >= EXPANDED_MIN_WIDTH_DP && minHeight >= EXPANDED_MIN_HEIGHT_DP) {
            expanded
        } else {
            compact
        }
    }

    private fun configuredRemoteViews(
        context: Context,
        layoutId: Int,
        appWidgetId: Int,
        state: SmartglassWidgetViewState,
    ): RemoteViews {
        val resources = context.resources
        val status = resources.getString(state.status.statusTextRes)
        val detail = when (state.status) {
            SmartglassWidgetStatus.SCANNING_DETECTED -> resources.getQuantityString(
                R.plurals.widget_detected_detail,
                state.recentCount,
                state.recentCount,
            )

            else -> resources.getString(state.status.detailTextRes)
        }
        val actionLabel = resources.getString(state.action.labelRes)
        val views = RemoteViews(context.packageName, layoutId).apply {
            setTextViewText(R.id.widget_count, state.recentCount.toString())
            setTextViewText(R.id.widget_status, status)
            setTextViewText(R.id.widget_detail, detail)
            setTextViewText(R.id.widget_action, actionLabel)
            setInt(R.id.widget_root, "setBackgroundResource", state.tone.backgroundRes)
            setInt(R.id.widget_action, "setBackgroundResource", state.action.buttonBackgroundRes)
            setTextColor(
                R.id.widget_action,
                ContextCompat.getColor(context, state.action.buttonTextColorRes),
            )
            setContentDescription(
                R.id.widget_root,
                resources.getQuantityString(
                    R.plurals.widget_accessibility_summary,
                    state.recentCount,
                    state.recentCount,
                    detail,
                ),
            )
            setContentDescription(R.id.widget_action, actionLabel)
            setOnClickPendingIntent(
                R.id.widget_root,
                SmartglassDetectorWidgetProvider.openAppPendingIntent(context, appWidgetId),
            )
            setBoolean(
                R.id.widget_action,
                "setEnabled",
                state.action != SmartglassWidgetAction.NONE,
            )
        }

        val actionPendingIntent = SmartglassDetectorWidgetProvider.actionPendingIntent(
            context,
            appWidgetId,
            state.action,
        )
        if (actionPendingIntent != null) {
            views.setOnClickPendingIntent(R.id.widget_action, actionPendingIntent)
            views.setViewVisibility(R.id.widget_action, View.VISIBLE)
        }
        return views
    }

    private val SmartglassWidgetStatus.statusTextRes: Int
        get() = when (this) {
            SmartglassWidgetStatus.STOPPED -> R.string.widget_status_stopped
            SmartglassWidgetStatus.STARTING -> R.string.widget_status_starting
            SmartglassWidgetStatus.PAUSED -> R.string.widget_status_paused
            SmartglassWidgetStatus.SCANNING_CLEAR,
            SmartglassWidgetStatus.SCANNING_DETECTED,
            -> R.string.widget_status_scanning
            SmartglassWidgetStatus.STOPPING -> R.string.widget_status_stopping
            SmartglassWidgetStatus.SETUP_REQUIRED -> R.string.widget_status_setup_required
            SmartglassWidgetStatus.PERMISSION_REQUIRED -> R.string.widget_status_permission_required
            SmartglassWidgetStatus.BLUETOOTH_DISABLED -> R.string.widget_status_bluetooth_off
            SmartglassWidgetStatus.UNSUPPORTED -> R.string.widget_status_unsupported
            SmartglassWidgetStatus.ATTENTION -> R.string.widget_status_attention
        }

    private val SmartglassWidgetStatus.detailTextRes: Int
        get() = when (this) {
            SmartglassWidgetStatus.STOPPED -> R.string.widget_detail_stopped
            SmartglassWidgetStatus.STARTING -> R.string.widget_detail_starting
            SmartglassWidgetStatus.PAUSED -> R.string.widget_detail_paused
            SmartglassWidgetStatus.SCANNING_CLEAR -> R.string.widget_detail_scanning_clear
            SmartglassWidgetStatus.SCANNING_DETECTED -> R.string.widget_detail_scanning_clear
            SmartglassWidgetStatus.STOPPING -> R.string.widget_detail_stopping
            SmartglassWidgetStatus.SETUP_REQUIRED -> R.string.widget_detail_setup_required
            SmartglassWidgetStatus.PERMISSION_REQUIRED -> R.string.widget_detail_permission_required
            SmartglassWidgetStatus.BLUETOOTH_DISABLED -> R.string.widget_detail_bluetooth_off
            SmartglassWidgetStatus.UNSUPPORTED -> R.string.widget_detail_unsupported
            SmartglassWidgetStatus.ATTENTION -> R.string.widget_detail_attention
        }

    private val SmartglassWidgetAction.labelRes: Int
        get() = when (this) {
            SmartglassWidgetAction.START -> R.string.widget_action_start
            SmartglassWidgetAction.STOP -> R.string.widget_action_stop
            SmartglassWidgetAction.OPEN_APP -> R.string.widget_action_open_app
            SmartglassWidgetAction.NONE -> R.string.widget_action_stopping
        }

    private val SmartglassWidgetTone.backgroundRes: Int
        get() = when (this) {
            SmartglassWidgetTone.NEUTRAL -> R.drawable.widget_background_neutral
            SmartglassWidgetTone.SAFE -> R.drawable.widget_background_safe
            SmartglassWidgetTone.DANGER -> R.drawable.widget_background_danger
            SmartglassWidgetTone.WARNING -> R.drawable.widget_background_warning
            SmartglassWidgetTone.INFO -> R.drawable.widget_background_info
        }

    private val SmartglassWidgetAction.buttonBackgroundRes: Int
        get() = when (this) {
            SmartglassWidgetAction.STOP -> R.drawable.widget_button_danger
            SmartglassWidgetAction.NONE -> R.drawable.widget_button_disabled
            else -> R.drawable.widget_button_primary
        }

    private val SmartglassWidgetAction.buttonTextColorRes: Int
        get() = if (this == SmartglassWidgetAction.NONE) {
            R.color.widget_button_disabled_text
        } else {
            R.color.widget_button_text
        }

    private fun String.toWidgetScannerState(): SmartglassWidgetScannerState = when (this) {
        BluetoothScanService.STATE_STARTING -> SmartglassWidgetScannerState.STARTING
        BluetoothScanService.STATE_SCANNING -> SmartglassWidgetScannerState.SCANNING
        BluetoothScanService.STATE_STOPPING -> SmartglassWidgetScannerState.STOPPING
        BluetoothScanService.STATE_ERROR -> SmartglassWidgetScannerState.ERROR
        else -> SmartglassWidgetScannerState.STOPPED
    }

    private const val COMPACT_MIN_WIDTH_DP = 110f
    private const val COMPACT_MIN_HEIGHT_DP = 50f
    private const val EXPANDED_MIN_WIDTH_DP = 180f
    private const val EXPANDED_MIN_HEIGHT_DP = 110f
}
