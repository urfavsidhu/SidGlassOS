package com.sidglassos.features.launcher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.util.SizeF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil

/** Separate from appearance preferences: resetting colors must not silently delete bound widgets. */
internal class WidgetHostStore(context: Context) {
    private val prefs = context.getSharedPreferences("launcher_widget_host", Context.MODE_PRIVATE)
    fun widgets(): List<HostedWidget> = runCatching {
        val array = JSONArray(prefs.getString("widgets", "[]"))
        WidgetLayoutPolicy.normalize((0 until array.length()).map {
            val item = array.getJSONObject(it); HostedWidget(item.getInt("id"), item.optInt("height", 220))
        })
    }.getOrDefault(emptyList())
    fun save(items: List<HostedWidget>) {
        val array = JSONArray()
        WidgetLayoutPolicy.normalize(items).forEach { array.put(JSONObject().put("id", it.id).put("height", it.heightDp)) }
        check(prefs.edit().putString("widgets", array.toString()).commit()) { "Widget layout could not be saved." }
    }
    var pendingId: Int
        get() = prefs.getInt("pending_id", AppWidgetManager.INVALID_APPWIDGET_ID)
        set(value) { check(prefs.edit().putInt("pending_id", value).commit()) }
    var configuring: Boolean
        get() = prefs.getBoolean("configuring", false)
        set(value) { check(prefs.edit().putBoolean("configuring", value).commit()) }
}

class SidWidgetHostView(context: Context) : AppWidgetHostView(context) {
    private var lastSize: Pair<Int, Int>? = null
    fun updateSize(width: Int, height: Int) {
        if (lastSize == (width to height)) return
        lastSize = width to height
        // The platform subtracts its widget padding and dispatches options changes to the provider.
        if (Build.VERSION.SDK_INT >= 31) updateAppWidgetSize(Bundle(), listOf(SizeF(width.toFloat(), height.toFloat())))
        else updateLegacySize(width, height)
    }
    @Suppress("DEPRECATION") // Required on API 29/30; API 31+ receives responsive sizes.
    private fun updateLegacySize(width: Int, height: Int) { updateAppWidgetSize(Bundle(), width, height, width, height) }
}

/** A stable host ID, user-approved binding, required configuration and durable widget IDs. */
class LauncherWidgetHost(private val activity: LauncherActivity) {
    companion object {
        const val HOST_ID = 0x534944
        const val REQUEST_CONFIGURE = 4102
        const val OPEN_PICKER = "open_widget_picker"
    }
    private val manager = AppWidgetManager.getInstance(activity)
    private val store = WidgetHostStore(activity)
    var widgets by mutableStateOf(store.widgets())
        private set
    var pendingId by mutableIntStateOf(store.pendingId)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var lastAddedId by mutableIntStateOf(0)
        private set
    var navigationRequest by mutableIntStateOf(0)
        private set
    var providerGeneration by mutableIntStateOf(0)
        private set
    private val host = object : AppWidgetHost(activity, HOST_ID) {
        override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo): AppWidgetHostView = SidWidgetHostView(context)
        override fun onProvidersChanged() { providerGeneration++ }
        override fun onProviderChanged(appWidgetId: Int, appWidget: AppWidgetProviderInfo) {
            super.onProviderChanged(appWidgetId, appWidget); providerGeneration++
        }
        override fun onAppWidgetRemoved(appWidgetId: Int) {
            super.onAppWidgetRemoved(appWidgetId)
            persist(widgets.filterNot { it.id == appWidgetId })
            if (pendingId == appWidgetId) clearPending()
        }
        fun releaseViews() { clearViews() }
    }

    fun start() {
        try {
            // Recover only orphan IDs owned by this host after an interrupted add/remove.
            val known = widgets.map { it.id }.toSet() + pendingId
            host.appWidgetIds.filterNot { it in known }.forEach { host.deleteAppWidgetId(it) }
            host.startListening()
        }
        catch (_: RuntimeException) { message = "Widget updates are unavailable. Reopen Home to retry." }
    }
    fun stop() { runCatching { host.stopListening() } }
    fun dispose() { stop(); host.releaseViews() }
    fun provider(id: Int): AppWidgetProviderInfo? = runCatching { manager.getAppWidgetInfo(id) }.getOrNull()
    fun installedProviders(): List<AppWidgetProviderInfo> = manager.getInstalledProvidersForProfile(android.os.Process.myUserHandle())
    fun createView(id: Int, info: AppWidgetProviderInfo): SidWidgetHostView = host.createView(activity, id, info) as SidWidgetHostView

    fun add(info: AppWidgetProviderInfo) {
        if (pendingId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            message = "Finish or cancel the pending widget first."; return
        }
        if (widgets.size >= WidgetLayoutPolicy.MAX_WIDGETS) {
            message = "Home holds up to ${WidgetLayoutPolicy.MAX_WIDGETS} widgets. Remove one first."; return
        }
        var allocated = AppWidgetManager.INVALID_APPWIDGET_ID
        try {
            allocated = host.allocateAppWidgetId()
            store.pendingId = allocated
            store.configuring = false
            pendingId = allocated
            val options = Bundle().apply { putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) }
            if (manager.bindAppWidgetIdIfAllowed(allocated, info.profile, info.provider, options)) bindingComplete()
            else activity.requestWidgetBinding(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, allocated)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options)
            })
        } catch (_: RuntimeException) {
            if (allocated != AppWidgetManager.INVALID_APPWIDGET_ID) runCatching { host.deleteAppWidgetId(allocated) }
            clearPending()
            message = "Android could not add this widget. Choose another provider or try again."
        }
    }

    fun bindingResult(result: Int) {
        if (pendingId == AppWidgetManager.INVALID_APPWIDGET_ID || store.configuring) return
        if (result == Activity.RESULT_OK && provider(pendingId) != null) bindingComplete()
        else cancelPending()
    }

    @Suppress("DEPRECATION") // AppWidgetHost brokers provider configuration through this public API.
    private fun bindingComplete() {
        val info = provider(pendingId) ?: return cancelPending()
        if (info.configure != null) {
            try {
                store.configuring = true
                host.startAppWidgetConfigureActivityForResult(activity, pendingId, 0, REQUEST_CONFIGURE, null)
            } catch (_: RuntimeException) {
                cancelPending(); message = "This widget's setup could not be opened. It was not added."
            }
        } else finishAdding(info)
    }

    fun configurationResult(result: Int) {
        if (pendingId == AppWidgetManager.INVALID_APPWIDGET_ID || !store.configuring) return
        val info = provider(pendingId)
        if (result == Activity.RESULT_OK && info != null) finishAdding(info) else cancelPending()
    }

    private fun finishAdding(info: AppWidgetProviderInfo) {
        val id = pendingId
        val padding = AppWidgetHostView.getDefaultPaddingForWidget(activity, info.provider, null)
        val density = activity.resources.displayMetrics.density
        val height = ceil((info.minHeight + padding.top + padding.bottom) / density).toInt().coerceIn(120, 1200)
        if (persist(widgets + HostedWidget(id, height))) {
            clearPending(); lastAddedId = id; navigationRequest++; message = "Widget added to its own Home page."
        } else cancelPending()
    }

    fun cancelPending() {
        val id = pendingId
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) runCatching { host.deleteAppWidgetId(id) }
        clearPending()
        message = "Widget addition canceled."
    }
    private fun clearPending() {
        store.pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
        store.configuring = false
        pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
    }
    fun remove(id: Int) {
        // Persist removal first: recreation cannot resurrect a widget after the system ID is released.
        if (persist(widgets.filterNot { it.id == id })) {
            runCatching { host.deleteAppWidgetId(id) }
            message = "Widget removed."
        }
    }
    fun resize(id: Int, height: Int) = persist(widgets.map { if (it.id == id) it.copy(heightDp = height) else it })
    fun move(id: Int, delta: Int) {
        if (persist(WidgetLayoutPolicy.move(widgets, id, delta))) { lastAddedId = id; navigationRequest++ }
    }
    private fun persist(items: List<HostedWidget>): Boolean = try {
        val normalized = WidgetLayoutPolicy.normalize(items)
        store.save(normalized); widgets = normalized; true
    } catch (_: RuntimeException) { message = "Widget layout could not be saved. Please try again."; false }
    fun dismissMessage() { message = null }
}
