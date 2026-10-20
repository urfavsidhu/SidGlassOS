package com.sidglassos.core.storage

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

enum class PerformanceMode { HIGH_QUALITY, BALANCED, BATTERY_SAVER }

data class GlassSettings(
    val gridColumns: Int = 4,
    val iconSize: Int = 56,
    val iconSpacing: Int = 12,
    val showLabels: Boolean = true,
    val labelSize: Int = 12,
    val glassOpacity: Float = .16f,
    val lightGlass: Boolean = false,
    val iconCornerRadius: Int = 22,
    val homeOrder: List<String> = emptyList(),
    val dock: List<String> = emptyList(),
    val iconAssignments: Map<String, String> = emptyMap(),
    val launcherInitialized: Boolean = false,
    val performance: PerformanceMode = PerformanceMode.BALANCED,
    val reducedMotion: Boolean = false,
    val particleCount: Int = 24,
    val animationIntensity: Float = .5f,
    val parallax: Boolean = false,
    val widgetOpacity: Float = .72f,
    val widgetTint: Long = 0xFFDDE5F4,
) {
    fun normalized() = copy(
        gridColumns = gridColumns.coerceIn(3, 6), iconSize = iconSize.coerceIn(40, 72),
        iconSpacing = iconSpacing.coerceIn(4, 24), labelSize = labelSize.coerceIn(10, 18),
        glassOpacity = glassOpacity.takeIf { it.isFinite() }?.coerceIn(.08f, .85f) ?: .16f,
        iconCornerRadius = iconCornerRadius.coerceIn(8, 32),
        homeOrder = homeOrder.filter { it.isNotBlank() }.distinct().take(240),
        dock = dock.filter { it.isNotBlank() }.distinct().take(4),
        particleCount = particleCount.coerceIn(0, 60),
        animationIntensity = animationIntensity.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: .5f,
        widgetOpacity = widgetOpacity.takeIf { it.isFinite() }?.coerceIn(.25f, 1f) ?: .72f,
        widgetTint = widgetTint and 0xFFFFFFFFL,
    )

    /** Apply only fields edited from a screen's snapshot to the latest stored state. */
    fun mergeEdits(baseline: GlassSettings, edited: GlassSettings): GlassSettings {
        fun <T> field(before: T, after: T, current: T) = if (before != after) after else current
        return copy(
            gridColumns = field(baseline.gridColumns, edited.gridColumns, gridColumns),
            iconSize = field(baseline.iconSize, edited.iconSize, iconSize),
            iconSpacing = field(baseline.iconSpacing, edited.iconSpacing, iconSpacing),
            showLabels = field(baseline.showLabels, edited.showLabels, showLabels),
            labelSize = field(baseline.labelSize, edited.labelSize, labelSize),
            glassOpacity = field(baseline.glassOpacity, edited.glassOpacity, glassOpacity),
            lightGlass = field(baseline.lightGlass, edited.lightGlass, lightGlass),
            iconCornerRadius = field(baseline.iconCornerRadius, edited.iconCornerRadius, iconCornerRadius),
            homeOrder = field(baseline.homeOrder, edited.homeOrder, homeOrder),
            dock = field(baseline.dock, edited.dock, dock),
            iconAssignments = field(baseline.iconAssignments, edited.iconAssignments, iconAssignments),
            launcherInitialized = field(baseline.launcherInitialized, edited.launcherInitialized, launcherInitialized),
            performance = field(baseline.performance, edited.performance, performance),
            reducedMotion = field(baseline.reducedMotion, edited.reducedMotion, reducedMotion),
            particleCount = field(baseline.particleCount, edited.particleCount, particleCount),
            animationIntensity = field(baseline.animationIntensity, edited.animationIntensity, animationIntensity),
            parallax = field(baseline.parallax, edited.parallax, parallax),
            widgetOpacity = field(baseline.widgetOpacity, edited.widgetOpacity, widgetOpacity),
            widgetTint = field(baseline.widgetTint, edited.widgetTint, widgetTint),
        )
    }
}

private val Context.glassDataStore by preferencesDataStore(
    name = "glass_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.glassDataStore
    val settings: Flow<GlassSettings> = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map(::decode)

    suspend fun update(transform: (GlassSettings) -> GlassSettings) {
        store.edit { p -> encode(p, transform(decode(p)).normalized()) }
    }
    suspend fun applyEdits(baseline: GlassSettings, edited: GlassSettings) = update {
        it.mergeEdits(baseline, edited)
    }
    suspend fun setHomeOrder(ids: List<String>) = update { it.copy(homeOrder = ids) }
    suspend fun setDock(ids: List<String>) = update { it.copy(dock = ids) }
    suspend fun setIconAssignment(component: String, symbol: String?) = update {
        it.copy(iconAssignments = it.iconAssignments.toMutableMap().apply {
            if (symbol == null) remove(component) else put(component, symbol)
        })
    }
    suspend fun reset() { store.edit { it.clear() } }

    private fun decode(p: Preferences): GlassSettings {
        fun list(key: String): List<String> = runCatching {
            val a = JSONArray(p[stringPreferencesKey(key)] ?: "[]")
            (0 until a.length()).map { a.getString(it) }
        }.getOrDefault(emptyList())
        val assignments = runCatching {
            val j = JSONObject(p[stringPreferencesKey("icons")] ?: "{}")
            j.keys().asSequence().associateWith { j.getString(it) }
        }.getOrDefault(emptyMap())
        return GlassSettings(
            gridColumns = p[intPreferencesKey("columns")] ?: 4,
            iconSize = p[intPreferencesKey("icon_size")] ?: 56,
            iconSpacing = p[intPreferencesKey("spacing")] ?: 12,
            showLabels = p[booleanPreferencesKey("labels")] ?: true,
            labelSize = p[intPreferencesKey("label_size")] ?: 12,
            glassOpacity = p[floatPreferencesKey("opacity")] ?: .16f,
            lightGlass = p[booleanPreferencesKey("light_glass")] ?: false,
            iconCornerRadius = p[intPreferencesKey("corner")] ?: 22,
            homeOrder = list("home"), dock = list("dock"), iconAssignments = assignments,
            launcherInitialized = p[booleanPreferencesKey("launcher_initialized")] ?: false,
            performance = runCatching { PerformanceMode.valueOf(p[stringPreferencesKey("performance")] ?: "BALANCED") }.getOrDefault(PerformanceMode.BALANCED),
            reducedMotion = p[booleanPreferencesKey("reduce_motion")] ?: false,
            particleCount = p[intPreferencesKey("particles")] ?: 24,
            animationIntensity = p[floatPreferencesKey("intensity")] ?: .5f,
            parallax = p[booleanPreferencesKey("parallax")] ?: false,
            widgetOpacity = p[floatPreferencesKey("widget_opacity")] ?: .72f,
            widgetTint = p[longPreferencesKey("widget_tint")] ?: 0xFFDDE5F4,
        ).normalized()
    }

    private fun encode(p: MutablePreferences, s: GlassSettings) {
        p[intPreferencesKey("columns")] = s.gridColumns
        p[intPreferencesKey("icon_size")] = s.iconSize
        p[intPreferencesKey("spacing")] = s.iconSpacing
        p[booleanPreferencesKey("labels")] = s.showLabels
        p[intPreferencesKey("label_size")] = s.labelSize
        p[floatPreferencesKey("opacity")] = s.glassOpacity
        p[booleanPreferencesKey("light_glass")] = s.lightGlass
        p[intPreferencesKey("corner")] = s.iconCornerRadius
        p[stringPreferencesKey("home")] = JSONArray(s.homeOrder).toString()
        p[stringPreferencesKey("dock")] = JSONArray(s.dock).toString()
        p[stringPreferencesKey("icons")] = JSONObject(s.iconAssignments).toString()
        p[booleanPreferencesKey("launcher_initialized")] = s.launcherInitialized
        p[stringPreferencesKey("performance")] = s.performance.name
        p[booleanPreferencesKey("reduce_motion")] = s.reducedMotion
        p[intPreferencesKey("particles")] = s.particleCount
        p[floatPreferencesKey("intensity")] = s.animationIntensity
        p[booleanPreferencesKey("parallax")] = s.parallax
        p[floatPreferencesKey("widget_opacity")] = s.widgetOpacity
        p[longPreferencesKey("widget_tint")] = s.widgetTint
    }
}
