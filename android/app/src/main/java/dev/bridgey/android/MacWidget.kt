package dev.bridgey.android

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews

/** The widget's Mac action buttons and the action each one runs. */
internal val MAC_WIDGET_BUTTONS = listOf(
    R.id.widget_lock to MacActionIds.LOCK,
    R.id.widget_display_sleep to MacActionIds.DISPLAY_SLEEP,
    R.id.widget_sleep to MacActionIds.SLEEP,
    R.id.widget_volume_down to MacActionIds.VOLUME_DOWN,
    R.id.widget_volume_mute to MacActionIds.VOLUME_MUTE,
    R.id.widget_volume_up to MacActionIds.VOLUME_UP,
)

private val MEDIA_WIDGET_ACTIONS = setOf("toggle", "previous", "next")

/** The Mac the widget shows: the connected one, otherwise the first paired Mac with a saved list. */
internal fun widgetMac(macs: List<ControlMac>): ControlMac? =
    macs.firstOrNull { it.connected } ?: macs.firstOrNull { it.actions != null } ?: macs.firstOrNull()

/**
 * Responsive Material 3 Expressive home-screen widget (4x1 Compact, 4x2 Standard, 4x3 Expanded,
 * plus 4x1 Not Connected) drawn with native RemoteViews and automatic Light/Dark theme resources.
 */
class MacWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        updateMacWidgets(context, manager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        updateMacWidgets(context, manager, intArrayOf(appWidgetId))
    }
}

class MacWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? BridgeyApplication ?: return
        if (!app.isPrimaryUser) return
        when (intent.action) {
            ACTION_RUN -> {
                val actionId = intent.getStringExtra(EXTRA_ACTION)
                    ?.takeIf { it in MAC_WIDGET_BUTTONS.map { button -> button.second } } ?: return
                app.pairing.quickActions.runMacAction(actionId)
            }
            ACTION_MEDIA -> {
                val mediaAction = intent.getStringExtra(EXTRA_ACTION)
                    ?.takeIf { it in MEDIA_WIDGET_ACTIONS } ?: return
                app.pairing.quickActions.request(BridgeyFeature.MEDIA, mediaAction)
                updateMacWidgets(context)
            }
        }
    }

    companion object {
        const val ACTION_RUN = "dev.bridgey.android.action.RUN_MAC_ACTION"
        const val ACTION_MEDIA = "dev.bridgey.android.action.RUN_MEDIA_ACTION"
        const val EXTRA_ACTION = "action"
    }
}

internal fun updateMacWidgets(
    context: Context,
    manager: AppWidgetManager = AppWidgetManager.getInstance(context),
    ids: IntArray = manager.getAppWidgetIds(ComponentName(context, MacWidgetProvider::class.java)),
) {
    if (ids.isEmpty()) return
    val app = context.applicationContext as? BridgeyApplication ?: return
    val mac = if (app.isPrimaryUser) widgetMac(controlMacs(app)) else null
    val connected = mac != null && mac.connected

    if (!connected) {
        val offlineViews = buildOfflineWidget(context, app)
        manager.updateAppWidget(ids, offlineViews)
        return
    }

    val battery = app.pairing.remoteBattery.value
    val media = app.pairing.quickActions.media.value
    val clipboardSyncing = app.pairing.isFeatureAvailable(BridgeyFeature.CLIPBOARD) &&
        !app.pairing.clipboardSyncPaused.value
    val muted = mac.actions?.volume?.muted == true

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val compact = buildCompactWidget(context, mac.name, battery, media.playing)
        val standard = buildStandardWidget(context, mac.name, battery, clipboardSyncing, media.playing, muted)
        val expanded = buildExpandedWidget(context, mac.name, battery, clipboardSyncing, media, muted)
        val responsive = RemoteViews(
            mapOf(
                SizeF(220f, 64f) to compact,
                SizeF(220f, 175f) to standard,
                SizeF(220f, 295f) to expanded,
            ),
        )
        manager.updateAppWidget(ids, responsive)
    } else {
        ids.forEach { id ->
            val minHeight = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
            val views = when {
                minHeight < 140 -> buildCompactWidget(context, mac.name, battery, media.playing)
                minHeight < 280 -> buildStandardWidget(context, mac.name, battery, clipboardSyncing, media.playing, muted)
                else -> buildExpandedWidget(context, mac.name, battery, clipboardSyncing, media, muted)
            }
            manager.updateAppWidget(id, views)
        }
    }
}

private fun buildCompactWidget(
    context: Context,
    macName: String,
    battery: RemoteBatteryStatus?,
    playing: Boolean,
): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.mac_widget_compact)
    views.setTextViewText(R.id.widget_title, macName)
    val statusText = if (battery != null) "${battery.level}% · Ready" else "Ready"
    views.setTextViewText(R.id.widget_status, statusText)
    views.setImageViewResource(
        R.id.widget_play_pause,
        if (playing) R.drawable.ic_widget_pause_tonal else R.drawable.ic_widget_play_tonal,
    )
    views.setInt(
        R.id.widget_play_pause,
        "setBackgroundResource",
        if (playing) R.drawable.widget_btn_active_16 else R.drawable.widget_btn_tonal_16,
    )
    views.setOnClickPendingIntent(R.id.widget_header, openAppIntent(context))
    views.setOnClickPendingIntent(R.id.widget_send_files, pickFilesIntent(context))
    views.setOnClickPendingIntent(R.id.widget_lock, macActionIntent(context, MacActionIds.LOCK, 1))
    views.setOnClickPendingIntent(R.id.widget_play_pause, mediaActionIntent(context, "toggle", 10))
    views.setOnClickPendingIntent(R.id.widget_sleep, macActionIntent(context, MacActionIds.SLEEP, 3))
    return views
}

private fun buildStandardWidget(
    context: Context,
    macName: String,
    battery: RemoteBatteryStatus?,
    clipboardSyncing: Boolean,
    playing: Boolean,
    muted: Boolean,
): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.mac_widget)
    views.setTextViewText(R.id.widget_title, macName)
    views.setTextViewText(
        R.id.widget_status,
        if (clipboardSyncing) "Ready · Clipboard syncing" else "Ready",
    )
    if (battery != null) {
        views.setViewVisibility(R.id.widget_battery, View.VISIBLE)
        views.setTextViewText(R.id.widget_battery, "${battery.level}%")
    } else {
        views.setViewVisibility(R.id.widget_battery, View.GONE)
    }
    views.setImageViewResource(
        R.id.widget_play_pause,
        if (playing) R.drawable.ic_widget_pause_tonal else R.drawable.ic_widget_play_tonal,
    )
    views.setInt(
        R.id.widget_play_pause,
        "setBackgroundResource",
        if (playing) R.drawable.widget_btn_active_20 else R.drawable.widget_btn_tonal_20,
    )
    views.setTextViewText(R.id.widget_mute_label, if (muted) "Muted" else "Mute")
    views.setInt(
        R.id.widget_volume_mute,
        "setBackgroundResource",
        if (muted) R.drawable.widget_btn_active_18 else R.drawable.widget_btn_tonal_18,
    )
    views.setOnClickPendingIntent(R.id.widget_header, openAppIntent(context))
    views.setOnClickPendingIntent(R.id.widget_send_files, pickFilesIntent(context))
    views.setOnClickPendingIntent(R.id.widget_play_pause, mediaActionIntent(context, "toggle", 10))
    views.setOnClickPendingIntent(R.id.widget_lock, macActionIntent(context, MacActionIds.LOCK, 1))
    views.setOnClickPendingIntent(R.id.widget_sleep, macActionIntent(context, MacActionIds.SLEEP, 3))
    views.setOnClickPendingIntent(R.id.widget_volume_mute, macActionIntent(context, MacActionIds.VOLUME_MUTE, 5))
    return views
}

private fun buildExpandedWidget(
    context: Context,
    macName: String,
    battery: RemoteBatteryStatus?,
    clipboardSyncing: Boolean,
    media: MacMediaState,
    muted: Boolean,
): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.mac_widget_expanded)
    views.setTextViewText(R.id.widget_title, macName)
    views.setTextViewText(
        R.id.widget_status,
        if (clipboardSyncing) "Ready · Clipboard syncing" else "Ready",
    )
    if (battery != null) {
        views.setViewVisibility(R.id.widget_battery, View.VISIBLE)
        views.setTextViewText(R.id.widget_battery, "${battery.level}%")
    } else {
        views.setViewVisibility(R.id.widget_battery, View.GONE)
    }

    val hasTrack = media.title.isNotBlank()
    views.setTextViewText(R.id.widget_track_title, if (hasTrack) media.title else "Not playing")
    views.setTextViewText(
        R.id.widget_track_artist,
        media.artist.ifBlank { media.player.ifBlank { "Mac media" } },
    )
    views.setImageViewResource(
        R.id.widget_play_pause,
        if (media.playing) R.drawable.ic_widget_pause_primary else R.drawable.ic_widget_play_primary,
    )

    views.setTextViewText(R.id.widget_mute_label, if (muted) "Muted" else "Mute")
    views.setInt(
        R.id.widget_volume_mute,
        "setBackgroundResource",
        if (muted) R.drawable.widget_vol_mid_active else R.drawable.widget_vol_mid_tonal,
    )

    views.setOnClickPendingIntent(R.id.widget_header, openAppIntent(context))
    views.setOnClickPendingIntent(R.id.widget_media_prev, mediaActionIntent(context, "previous", 11))
    views.setOnClickPendingIntent(R.id.widget_play_pause, mediaActionIntent(context, "toggle", 10))
    views.setOnClickPendingIntent(R.id.widget_media_next, mediaActionIntent(context, "next", 12))
    views.setOnClickPendingIntent(R.id.widget_send_files, pickFilesIntent(context))
    views.setOnClickPendingIntent(R.id.widget_clipboard, clipboardIntent(context))
    views.setOnClickPendingIntent(R.id.widget_lock, macActionIntent(context, MacActionIds.LOCK, 1))
    views.setOnClickPendingIntent(R.id.widget_display_sleep, macActionIntent(context, MacActionIds.DISPLAY_SLEEP, 2))
    views.setOnClickPendingIntent(R.id.widget_sleep, macActionIntent(context, MacActionIds.SLEEP, 3))
    views.setOnClickPendingIntent(R.id.widget_volume_down, macActionIntent(context, MacActionIds.VOLUME_DOWN, 4))
    views.setOnClickPendingIntent(R.id.widget_volume_mute, macActionIntent(context, MacActionIds.VOLUME_MUTE, 5))
    views.setOnClickPendingIntent(R.id.widget_volume_up, macActionIntent(context, MacActionIds.VOLUME_UP, 6))
    return views
}

private fun buildOfflineWidget(context: Context, app: BridgeyApplication): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.mac_widget_offline)
    views.setTextViewText(R.id.widget_title, "Mac not connected")
    val droppedAt = app.pairing.connectionHealth.value.lastDisconnectAtMillis
    val subtitle = if (droppedAt != null && droppedAt > 0L) {
        val mins = ((System.currentTimeMillis() - droppedAt) / 60_000L).coerceAtLeast(1L)
        if (mins < 60L) "Last seen $mins min ago" else "Last seen ${mins / 60L} hr ago"
    } else {
        "Open Bridgey to reconnect"
    }
    views.setTextViewText(R.id.widget_status, subtitle)
    views.setOnClickPendingIntent(R.id.widget_reconnect, openAppIntent(context))
    views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
    return views
}

private fun macActionIntent(context: Context, actionId: String, offset: Int): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        WIDGET_REQUEST_BASE + offset,
        Intent(context, MacWidgetActionReceiver::class.java)
            .setAction(MacWidgetActionReceiver.ACTION_RUN)
            .putExtra(MacWidgetActionReceiver.EXTRA_ACTION, actionId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

private fun mediaActionIntent(context: Context, mediaAction: String, offset: Int): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        WIDGET_REQUEST_BASE + offset,
        Intent(context, MacWidgetActionReceiver::class.java)
            .setAction(MacWidgetActionReceiver.ACTION_MEDIA)
            .putExtra(MacWidgetActionReceiver.EXTRA_ACTION, mediaAction),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

private fun openAppIntent(context: Context): PendingIntent =
    PendingIntent.getActivity(
        context,
        WIDGET_REQUEST_BASE + 20,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

private fun pickFilesIntent(context: Context): PendingIntent =
    PendingIntent.getActivity(
        context,
        WIDGET_REQUEST_BASE + 21,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MainActivity.EXTRA_PICK_FILES, true),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

private fun clipboardIntent(context: Context): PendingIntent =
    PendingIntent.getActivity(
        context,
        WIDGET_REQUEST_BASE + 22,
        Intent(context, ClipboardCaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

private const val WIDGET_REQUEST_BASE = 7_700
