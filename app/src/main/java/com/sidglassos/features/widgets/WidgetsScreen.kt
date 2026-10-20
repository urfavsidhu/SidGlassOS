package com.sidglassos.features.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sidglassos.core.design.GlassCard
import com.sidglassos.core.design.GlassSlider
import com.sidglassos.core.storage.GlassSettings
import com.sidglassos.features.media.GlassMediaCard
import com.sidglassos.features.launcher.LauncherActivity
import com.sidglassos.features.launcher.LauncherWidgetHost
import com.sidglassos.core.platform.openSafely

@Composable
fun WidgetsScreen(settings: GlassSettings, onBack: () -> Unit,
    onSettingsChange: (GlassSettings) -> Unit) {
    val context = LocalContext.current
    val manager = remember(context) { AppWidgetManager.getInstance(context) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var opacityDraft by remember(settings.widgetOpacity) { mutableFloatStateOf(settings.widgetOpacity) }
    var shortcut by remember { mutableStateOf(WidgetShortcuts.selected(context)) }
    LaunchedEffect(settings.widgetOpacity, settings.widgetTint) { WidgetRefresh.refreshAll(context) }
    fun pin(provider: Class<out GlassWidgetProvider>) {
        message = if (manager.isRequestPinAppWidgetSupported) {
            try {
                if (manager.requestPinAppWidget(ComponentName(context, provider), null, null))
                    "Confirm placement in your launcher's widget dialog."
                else "Open your launcher's Widgets menu and choose Sid Glass OS."
            } catch (_: IllegalStateException) { "Open Widgets from your home screen to add this card." }
            catch (_: SecurityException) { "Your launcher restricted pinning. Add the card from its Widgets menu." }
        } else "Long-press an empty space on your home screen, open Widgets, then choose Sid Glass OS."
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Small moments, beautifully", style = MaterialTheme.typography.headlineSmall)
        }
        Text("Native Android widgets for a home screen that feels like yours.", style = MaterialTheme.typography.bodyLarge)
        Button(onClick = { context.openSafely(Intent(context, LauncherActivity::class.java)
            .putExtra(LauncherWidgetHost.OPEN_PICKER, true)) }, modifier = Modifier.fillMaxWidth()) {
            Text("Add widgets inside Sid Home")
        }
        WidgetCard("Digital clock", "Large native clock and date. Follows the phone's 12/24-hour preference and opens Alarms.",
            settings) { pin(ClockWidgetProvider::class.java) }
        WidgetCard("Battery", "Battery percentage, charge state and a glass progress bar. Tap the widget to refresh its reading.",
            settings) { pin(BatteryWidgetProvider::class.java) }
        GlassMediaCard(settings)
        WidgetCard("Music", "Track, artist, album art and supported playback actions from an active media session after you explicitly connect media access. Without access or an active player, the widget opens your music app.",
            settings) { pin(MusicWidgetProvider::class.java) }
        WidgetCard("At a glance", "Time, date, battery and your chosen shortcut. Resize cards using your launcher's widget handles.",
            settings) { pin(InfoWidgetProvider::class.java) }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        GlassCard(opacity = settings.glassOpacity) {
            Text("Make the glass yours", style = MaterialTheme.typography.titleLarge)
            Text("Style changes update all installed Sid Glass OS widgets.",
                modifier = Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium)
            GlassSlider("Card opacity · ${(opacityDraft * 100).toInt()}%", opacityDraft, .25f..1f,
                onValueChange = { opacityDraft = it },
                onValueChangeFinished = { onSettingsChange(settings.copy(widgetOpacity = opacityDraft)) })
            Text("Text color", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Ice" to 0xFFDDE5F4L, "Lilac" to 0xFFD1C4FFL, "White" to 0xFFFFFFFFL).forEach { (label, tint) ->
                    FilterChip(selected = settings.widgetTint == tint,
                        onClick = { onSettingsChange(settings.copy(widgetTint = tint)) },
                        label = { Text(label, color = Color(tint)) })
                }
            }
            Text("Information card shortcut", style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp))
            WidgetShortcut.entries.forEach { option ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = shortcut == option, onClick = {
                        shortcut = option; WidgetShortcuts.select(context, option)
                    })
                    Text(option.label)
                }
            }
        }
        Text("Battery readings refresh when added, resized, tapped, or at the system's periodic widget update (about 30 minutes, possibly deferred). The information card refreshes periodically. No battery polling service runs.",
            style = MaterialTheme.typography.bodySmall)
        Text("Use Add widgets inside Sid Home to select Sid or other installed widgets. The Add buttons above use your default launcher's pinning flow when supported.",
            style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun WidgetCard(title: String, description: String, settings: GlassSettings, onAdd: () -> Unit) {
    GlassCard(opacity = settings.glassOpacity) {
        Box(Modifier.size(width = 96.dp, height = 6.dp)
            .background(Color(settings.widgetTint).copy(alpha = .6f), RoundedCornerShape(3.dp)))
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 14.dp))
        Text(description, modifier = Modifier.padding(vertical = 10.dp))
        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("Add $title") }
    }
}
