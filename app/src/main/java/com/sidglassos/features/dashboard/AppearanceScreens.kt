package com.sidglassos.features.dashboard

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sidglassos.ScreenColumn
import com.sidglassos.core.design.*
import com.sidglassos.core.platform.*
import com.sidglassos.core.storage.*

@Composable
fun IconsScreen(settings: GlassSettings, save: (GlassSettings) -> Unit) {
    ScreenColumn("Made of light", "An original glass frame for every app.") {
        GlassCard(Modifier.fillMaxWidth(), settings.glassOpacity) {
            GlassSymbols.categories.entries.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { (name, symbol) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
                            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(settings.iconCornerRadius.dp), color = if (settings.lightGlass) GlassColors.Ice.copy(alpha = .35f) else GlassColors.Violet.copy(alpha = .2f)) {
                                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) { Icon(symbol, name, tint = GlassColors.Ice) }
                            }
                            Text(name, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.width(64.dp)) }
                }
            }
        }
        GlassCard(Modifier.fillMaxWidth(), settings.glassOpacity) {
            PreferenceSlider("Glass intensity", settings.glassOpacity, 0.08f..0.85f) { save(settings.copy(glassOpacity = it)) }
            PreferenceSlider("Icon size · ${settings.iconSize}dp", settings.iconSize.toFloat(), 40f..72f) { save(settings.copy(iconSize = it.toInt())) }
            PreferenceSlider("Corner radius · ${settings.iconCornerRadius}dp", settings.iconCornerRadius.toFloat(), 8f..32f) { save(settings.copy(iconCornerRadius = it.toInt())) }
            PreferenceSwitch("Light glass", "Use a lighter icon surface", settings.lightGlass) { save(settings.copy(lightGlass = it)) }
        }
        GlassCard(Modifier.fillMaxWidth(), settings.glassOpacity) {
            Text("Assign an icon", style = MaterialTheme.typography.titleMedium)
            Text("Open the launcher, then long-press an app to choose a glass symbol or restore its original icon. Assignments stay inside Sid Glass OS.", Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun SettingsScreen(settings: GlassSettings, save: (GlassSettings) -> Unit, reset: () -> Unit, navigate: (String) -> Unit) {
    val context = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }
    ScreenColumn("Your preferences", "Small adjustments. A space that feels right.") {
        GlassCard(Modifier.fillMaxWidth(), settings.glassOpacity) {
            Text("Home screen", style = MaterialTheme.typography.titleLarge)
            PreferenceSlider("Grid · ${settings.gridColumns} columns", settings.gridColumns.toFloat(), 3f..6f) { save(settings.copy(gridColumns = it.toInt())) }
            PreferenceSlider("Icon spacing · ${settings.iconSpacing}dp", settings.iconSpacing.toFloat(), 4f..24f) { save(settings.copy(iconSpacing = it.toInt())) }
            PreferenceSwitch("App labels", "Show names under icons", settings.showLabels) { save(settings.copy(showLabels = it)) }
            PreferenceSlider("Label type size · ${settings.labelSize}sp", settings.labelSize.toFloat(), 10f..18f) { save(settings.copy(labelSize = it.toInt())) }
        }
        GlassCard(Modifier.fillMaxWidth(), settings.glassOpacity) {
            Text("Motion & energy", style = MaterialTheme.typography.titleLarge)
            Column(Modifier.selectableGroup()) {
                PerformanceMode.entries.forEach { mode ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                        selected = settings.performance == mode, role = Role.RadioButton,
                        onClick = { save(settings.copy(performance = mode)) }),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = settings.performance == mode, onClick = null)
                        Text(mode.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }, Modifier.padding(start = 12.dp))
                    }
                }
            }
            Text("Balanced limits live wallpaper to 24 fps. Battery Saver uses a still scene.", style = MaterialTheme.typography.bodySmall)
            PreferenceSwitch("Reduce motion", "Keep the wallpaper still", settings.reducedMotion) { save(settings.copy(reducedMotion = it)) }
            PreferenceSwitch("High-contrast surfaces", "Increase glass opacity for readability", settings.glassOpacity >= .55f) { save(settings.copy(glassOpacity = if (it) .65f else .16f)) }
        }
        GlassCard(Modifier.fillMaxWidth(), settings.glassOpacity) {
            Text("System & restore", style = MaterialTheme.typography.titleLarge)
            GlassButton("Choose default home", { (context as? Activity)?.chooseHome() }, Modifier.fillMaxWidth().padding(top = 10.dp))
            TextButton(onClick = { context.openSafely(Intent(Settings.ACTION_HOME_SETTINGS)) }) { Text("Return to OPPO launcher") }
            TextButton(onClick = { navigate("guide") }) { Text("Setup & restore guide") }
            TextButton(onClick = { confirmReset = true }) { Text("Reset Sid Glass OS preferences") }
            Text("Reset restores app preferences and layout. Use Android Wallpaper settings to change an applied wallpaper.", style = MaterialTheme.typography.bodySmall)
        }
        GlassCard(Modifier.fillMaxWidth(), settings.glassOpacity) {
            Text("This device", style = MaterialTheme.typography.titleMedium)
            Text(deviceFacts(), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
            Text("Firmware compatibility must be checked on the handset. No telemetry or ads.", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false },
        title = { Text("Reset preferences?") },
        text = { Text("Your icon assignments, home layout, and appearance preferences will return to their defaults.") },
        confirmButton = { TextButton(onClick = { reset(); confirmReset = false }) { Text("Reset") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Keep preferences") } })
}

@Composable
fun PreferenceSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, commit: (Float) -> Unit) {
    var draft by remember(value) { mutableFloatStateOf(value) }
    GlassSlider(label, draft, range, { draft = it }, { commit(draft) })
}

@Composable
fun PreferenceSwitch(title: String, description: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .toggleable(value = checked, role = Role.Switch, onValueChange = change).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title)
            Text(description, style = MaterialTheme.typography.bodySmall, color = GlassColors.Ice.copy(alpha = .75f))
        }
        Switch(checked, onCheckedChange = null)
    }
}

@Composable
fun GuideScreen() {
    ScreenColumn("Settle into glass", "Everything can be changed back through Android Settings.") {
        listOf(
            "1 · Set your home" to "From Home, choose Use as home screen and select Sid Glass OS in Android's chooser. Open launcher lets you try it first.",
            "2 · Add your scene" to "Choose Scenes, then open the live wallpaper picker or apply a still wallpaper. Lock-screen choices depend on ColorOS and remain in its official picker.",
            "3 · Arrange your apps" to "Open All apps in the launcher. Long-press an app to add it to home or the dock and assign a symbol. Long-press and drag home icons to rearrange; the menu also offers move actions.",
            "4 · Place widgets" to "Open Glass widgets on Home. Pin a widget to a compatible current launcher, or use that launcher's Widgets picker. Sid Glass OS has built-in clock/info cards; external widget hosting is not included in this release.",
            "5 · Find your controls" to "Controls opens the glass panel. Add its optional tile to Android Quick Settings. Wi-Fi, Bluetooth, airplane mode and global dark mode open the system's settings.",
            "Restore your OPPO setup" to "Open Settings → Apps → Default apps → Home app (wording may vary), then select the original OPPO launcher. Set your previous wallpaper in Android Settings, remove glass widgets, and optionally uninstall Sid Glass OS.",
            "About system visuals" to "The lock-screen design and notification design are labeled previews. Android owns the secure lock screen, notification shade and notification templates. The app never changes protected system packages.",
        ).forEach { (title, body) ->
            GlassCard(Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
