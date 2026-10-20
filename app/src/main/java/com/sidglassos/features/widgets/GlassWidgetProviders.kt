package com.sidglassos.features.widgets

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.BatteryManager
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.widget.RemoteViews
import android.widget.Toast
import android.view.View
import androidx.core.content.edit
import androidx.core.graphics.scale
import androidx.core.net.toUri
import com.sidglassos.R
import com.sidglassos.core.storage.GlassSettings
import com.sidglassos.core.storage.SettingsRepository
import com.sidglassos.features.media.MediaBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Native RemoteViews. No foreground service, permanent receiver, or polling loop. */
abstract class GlassWidgetProvider : AppWidgetProvider() {
    abstract val layout: Int
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = runCatching {
                    withTimeout(6_000) { SettingsRepository(context).settings.first() }
                }.getOrDefault(GlassSettings())
                for (id in appWidgetIds) {
                    try { manager.updateAppWidget(id, buildViews(context, settings, id)) }
                    catch (_: IllegalArgumentException) { /* Widget may have been removed during this update. */ }
                    catch (_: SecurityException) { /* A launcher/profile policy can revoke host access. */ }
                }
            } finally { pending.finish() }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager,
        appWidgetId: Int, newOptions: Bundle) { onUpdate(context, manager, intArrayOf(appWidgetId)) }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            onUpdate(context, manager, manager.getAppWidgetIds(ComponentName(context, javaClass)))
        }
    }

    private fun buildViews(context: Context, settings: GlassSettings, id: Int): RemoteViews {
        val views = RemoteViews(context.packageName, layout)
        views.setInt(R.id.widget_background, "setImageAlpha", (settings.widgetOpacity * 255).toInt())
        val tint = settings.widgetTint.toInt()
        when (this) {
            is ClockWidgetProvider -> {
                views.setTextColor(R.id.widget_clock, tint)
                views.setTextColor(R.id.widget_date, tint)
                views.setOnClickPendingIntent(R.id.widget_root, activityAction(context, id, "clock"))
            }
            is MusicWidgetProvider -> {
                val snapshot = MediaBridge.state.value
                // Revocation can race a previously published snapshot. Never show it
                // unless access is still granted at the moment this widget is built.
                val connected = MediaBridge.hasAccess(context) && snapshot.accessGranted
                val active = connected && snapshot.active
                views.setTextViewText(R.id.widget_title,
                    if (active) snapshot.title.ifBlank { context.getString(R.string.widget_music_unknown_title) }
                    else context.getString(R.string.widget_music_title))
                views.setTextViewText(R.id.widget_subtitle, when {
                    active -> snapshot.artist.ifBlank { context.getString(R.string.widget_music_active) }
                    connected -> context.getString(R.string.widget_music_no_session)
                    else -> context.getString(R.string.widget_music_access_off)
                })
                views.setTextColor(R.id.widget_title, tint)
                views.setTextColor(R.id.widget_subtitle, tint)
                views.setTextColor(R.id.widget_media_open, tint)
                val artwork = snapshot.artwork?.takeIf { active && !it.isRecycled }
                if (artwork == null) views.setImageViewResource(R.id.widget_artwork, R.drawable.widget_music_note)
                else views.setImageViewBitmap(R.id.widget_artwork, boundedArtwork(artwork))
                val previous = active && snapshot.canPrevious
                val toggle = active && snapshot.canPlayPause
                val next = active && snapshot.canNext
                views.setViewVisibility(R.id.widget_media_previous, if (previous) View.VISIBLE else View.GONE)
                views.setViewVisibility(R.id.widget_media_toggle, if (toggle) View.VISIBLE else View.GONE)
                views.setViewVisibility(R.id.widget_media_next, if (next) View.VISIBLE else View.GONE)
                views.setImageViewResource(R.id.widget_media_toggle,
                    if (snapshot.playing && active) R.drawable.widget_media_pause else R.drawable.widget_media_play)
                views.setContentDescription(R.id.widget_media_toggle,
                    context.getString(if (snapshot.playing && active) R.string.widget_media_pause else R.string.widget_media_play))
                views.setOnClickPendingIntent(R.id.widget_media_previous, activityAction(context, id, "media_previous"))
                views.setOnClickPendingIntent(R.id.widget_media_toggle, activityAction(context, id, "media_toggle"))
                views.setOnClickPendingIntent(R.id.widget_media_next, activityAction(context, id, "media_next"))
                views.setOnClickPendingIntent(R.id.widget_media_open, activityAction(context, id, "music"))
                views.setOnClickPendingIntent(R.id.widget_root, activityAction(context, id, "music"))
            }
            is BatteryWidgetProvider -> {
                val battery = batteryState(context)
                views.setTextViewText(R.id.widget_title, battery.percent?.let { "$it%" } ?: "—")
                views.setTextViewText(R.id.widget_subtitle,
                    "${battery.description} · ${updatedAt(context)}\nTap to refresh")
                views.setTextColor(R.id.widget_title, tint)
                views.setTextColor(R.id.widget_subtitle, tint)
                views.setProgressBar(R.id.widget_battery_progress, 100, battery.percent ?: 0, false)
                val refresh = Intent(context, BatteryWidgetProvider::class.java).setAction(ACTION_REFRESH)
                views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getBroadcast(context, id,
                    refresh, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            is InfoWidgetProvider -> {
                val battery = batteryState(context)
                views.setTextViewText(R.id.widget_subtitle,
                    battery.percent?.let { "$it% · ${battery.description} · ${updatedAt(context)}" } ?: battery.description)
                views.setTextColor(R.id.widget_clock, tint)
                views.setTextColor(R.id.widget_date, tint)
                views.setTextColor(R.id.widget_subtitle, tint)
                val shortcut = WidgetShortcuts.selected(context)
                views.setTextViewText(R.id.widget_action, "Open ${shortcut.label}  ›")
                views.setTextColor(R.id.widget_action, tint)
                views.setOnClickPendingIntent(R.id.widget_root, activityAction(context, id, shortcut.action))
            }
        }
        return views
    }

    private fun activityAction(context: Context, id: Int, action: String): PendingIntent {
        val intent = Intent(context, WidgetActionActivity::class.java).putExtra("action", action)
        // Different actions and widget IDs cannot overwrite one another's PendingIntent.
        intent.data = "sidglassos://widget/$id/$action".toUri()
        return PendingIntent.getActivity(context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun boundedArtwork(source: Bitmap): Bitmap {
        val largest = maxOf(source.width, source.height)
        if (largest <= 128) return source
        val factor = 128f / largest
        return source.scale((source.width * factor).toInt().coerceAtLeast(1),
            (source.height * factor).toInt().coerceAtLeast(1), true)
    }

    companion object { const val ACTION_REFRESH = "com.sidglassos.action.REFRESH_WIDGET" }
}

class ClockWidgetProvider : GlassWidgetProvider() { override val layout = R.layout.widget_clock }
class BatteryWidgetProvider : GlassWidgetProvider() { override val layout = R.layout.widget_battery }
class MusicWidgetProvider : GlassWidgetProvider() { override val layout = R.layout.widget_music }
class InfoWidgetProvider : GlassWidgetProvider() { override val layout = R.layout.widget_info }

object WidgetRefresh {
    fun refreshMusic(context: Context) {
        refresh(context, MusicWidgetProvider::class.java)
    }
    fun resetShortcut(context: Context) {
        context.getSharedPreferences("widget_shortcuts", Context.MODE_PRIVATE).edit { clear() }
        refreshAll(context)
    }
    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        for (type in listOf(ClockWidgetProvider::class.java, BatteryWidgetProvider::class.java,
            MusicWidgetProvider::class.java, InfoWidgetProvider::class.java)) {
            refresh(context, type, manager)
        }
    }
    private fun refresh(context: Context, type: Class<out GlassWidgetProvider>,
        manager: AppWidgetManager = AppWidgetManager.getInstance(context)) {
        val ids = manager.getAppWidgetIds(ComponentName(context, type))
        if (ids.isNotEmpty()) context.sendBroadcast(Intent(context, type)
            .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
    }
}

internal data class WidgetBattery(val percent: Int?, val description: String)
private fun updatedAt(context: Context): String =
    android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date())

internal fun batteryState(context: Context): WidgetBattery {
    val battery = try { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }
        catch (_: SecurityException) { null }
    val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val percentage = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else null
    return WidgetBattery(percentage, when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
        BatteryManager.BATTERY_STATUS_FULL -> "Fully charged"
        else -> if (percentage == null) "Battery unavailable" else "On battery"
    })
}

internal enum class WidgetShortcut(val action: String, val label: String) {
    CALENDAR("calendar", "Calendar"), SETTINGS("settings", "Settings"), DASHBOARD("dashboard", "Sid Glass OS")
}
internal object WidgetShortcuts {
    fun selected(context: Context): WidgetShortcut {
        val name = context.getSharedPreferences("widget_shortcuts", Context.MODE_PRIVATE)
            .getString("action", WidgetShortcut.CALENDAR.name)
        return WidgetShortcut.entries.firstOrNull { it.name == name } ?: WidgetShortcut.CALENDAR
    }
    fun select(context: Context, shortcut: WidgetShortcut) {
        context.getSharedPreferences("widget_shortcuts", Context.MODE_PRIVATE).edit {
            putString("action", shortcut.name)
        }
        WidgetRefresh.refreshAll(context)
    }
}

/** An explicit activity PendingIntent gives widget clicks a safe, visible failure path. */
class WidgetActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent.getStringExtra("action")
        if (action in setOf("media_previous", "media_toggle", "media_next")) {
            MediaBridge.refreshAccess(this)
            val handled = when (action) {
                "media_previous" -> MediaBridge.previous()
                "media_toggle" -> MediaBridge.playPause()
                else -> MediaBridge.next()
            }
            if (!handled) Toast.makeText(this, R.string.widget_media_action_unavailable, Toast.LENGTH_LONG).show()
            WidgetRefresh.refreshMusic(this)
            finish()
            return
        }
        val target = when (action) {
            "music" -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
            "clock" -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
            "calendar" -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
            "settings" -> Intent(Settings.ACTION_SETTINGS)
            else -> packageManager.getLaunchIntentForPackage(packageName)
        }
        if (target == null) Toast.makeText(this, "This shortcut is unavailable.", Toast.LENGTH_LONG).show()
        else try { startActivity(target) }
        catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No app is available for this shortcut.", Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Your phone restricted this shortcut.", Toast.LENGTH_LONG).show()
        }
        finish()
    }
}
