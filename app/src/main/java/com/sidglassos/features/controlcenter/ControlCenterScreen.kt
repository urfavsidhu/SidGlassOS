package com.sidglassos.features.controlcenter

import android.app.Activity
import android.app.StatusBarManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon as AndroidIcon
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.sidglassos.core.design.GlassCard
import com.sidglassos.core.storage.GlassSettings
import com.sidglassos.features.media.GlassMediaCard
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** An ordinary app screen. No notification-shade replacement or background overlay. */
@Composable
fun ControlCenterScreen(settings: GlassSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findControlActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val audio = remember(context) { context.getSystemService(AudioManager::class.java) }
    val camera = remember(context) { context.getSystemService(CameraManager::class.java) }
    val flashId = remember(camera) {
        runCatching {
            camera.cameraIdList.firstOrNull {
                camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }
    var message by remember { mutableStateOf<String?>(null) }
    var battery by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    var wifi by remember { mutableStateOf("Checking connection") }
    var airplane by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var torchAvailable by remember { mutableStateOf(flashId != null) }
    // Only undo a torch state actually initiated here, never another app's flashlight.
    val ownsTorch = remember { booleanArrayOf(false) }
    var volume by remember { mutableFloatStateOf(0f) }
    val minimumVolume = remember(audio) { audio.getStreamMinVolume(AudioManager.STREAM_MUSIC) }
    val maximumVolume = remember(audio) { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    val fixedVolume = remember(audio) { audio.isVolumeFixed || maximumVolume <= minimumVolume }
    val originalBrightness = remember(activity) { activity?.window?.attributes?.screenBrightness ?: -1f }
    var brightness by remember {
        mutableFloatStateOf(
            if (originalBrightness >= 0f) originalBrightness else
                (runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) }
                    .getOrDefault(128) / 255f).coerceIn(0.02f, 1f)
        )
    }

    fun openSetting(action: String) {
        message = openControlIntent(context, Intent(action))
    }

    fun turnOffOwnedTorch() {
        if (ownsTorch[0] && flashId != null) {
            runCatching { camera.setTorchMode(flashId, false) }
            ownsTorch[0] = false
        }
    }

    DisposableEffect(activity) {
        onDispose {
            activity?.window?.let { window ->
                window.attributes = window.attributes.apply { screenBrightness = originalBrightness }
            }
        }
    }

    DisposableEffect(context, lifecycleOwner, camera, flashId) {
        var registered = false
        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                battery = if (level >= 0 && scale > 0) (level * 100f / scale).roundToInt().coerceIn(0, 100) else -1
                val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        val torchCallback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == flashId) {
                    torchAvailable = true
                    torchOn = enabled
                    if (!enabled) ownsTorch[0] = false
                }
            }
            override fun onTorchModeUnavailable(cameraId: String) {
                if (cameraId == flashId) {
                    torchAvailable = false
                    torchOn = false
                    ownsTorch[0] = false
                }
            }
        }
        fun start() {
            if (registered) return
            registered = true
            ContextCompat.registerReceiver(
                context, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
            )
            if (flashId != null) runCatching { camera.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper())) }
        }
        fun stop() {
            turnOffOwnedTorch()
            if (!registered) return
            registered = false
            runCatching { context.unregisterReceiver(batteryReceiver) }
            runCatching { camera.unregisterTorchCallback(torchCallback) }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stop()
        }
    }

    LaunchedEffect(lifecycleOwner, audio) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                volume = runCatching { audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() }.getOrDefault(0f)
                wifi = readWifiStatus(context)
                airplane = runCatching {
                    Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
                }.getOrDefault(false)
                delay(1200)
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Column {
                Text("Control Center", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text("Your everyday controls, in glass", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFBAC9E6))
            }
        }
        Panel(settings.glassOpacity) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.BatteryFull, null, tint = Color(0xFFADC6FF))
                Text(if (battery >= 0) "$battery%" else "Battery unavailable", style = MaterialTheme.typography.headlineSmall)
                Text(if (charging) "Charging" else "On battery", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ControlTile("Wi-Fi", wifi, Icons.Rounded.Wifi, Modifier.weight(1f)) {
                openSetting(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            }
            ControlTile("Bluetooth", "Open system controls", Icons.Rounded.Bluetooth, Modifier.weight(1f)) {
                openSetting(Settings.ACTION_BLUETOOTH_SETTINGS)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ControlTile("Airplane mode", if (airplane) "On · open Settings" else "Off · open Settings", Icons.Rounded.AirplanemodeActive, Modifier.weight(1f)) {
                openSetting(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            }
            ControlTile("Display & theme", "System appearance", Icons.Rounded.DarkMode, Modifier.weight(1f)) {
                openSetting(Settings.ACTION_DISPLAY_SETTINGS)
            }
        }
        Panel(settings.glassOpacity) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.AutoMirrored.Rounded.VolumeUp, null)
                Text("Media volume", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                val range = (maximumVolume - minimumVolume).coerceAtLeast(1)
                Text("${((volume - minimumVolume) * 100 / range).roundToInt().coerceIn(0, 100)}%")
            }
            Slider(
                modifier = Modifier.semantics { contentDescription = "Media volume" },
                value = volume.coerceIn(minimumVolume.toFloat(), maximumVolume.coerceAtLeast(minimumVolume + 1).toFloat()),
                onValueChange = { value ->
                    runCatching {
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, value.roundToInt(), 0)
                        audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                    }.onSuccess { volume = it }
                        .onFailure { message = "Media volume is managed by the system. Open sound settings." }
                },
                valueRange = minimumVolume.toFloat()..maximumVolume.coerceAtLeast(minimumVolume + 1).toFloat(),
                enabled = !fixedVolume
            )
            if (fixedVolume) Text("Volume is fixed for this audio route.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { openSetting(Settings.Panel.ACTION_VOLUME) }) { Text("All sound controls") }
        }
        Panel(settings.glassOpacity) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.WbSunny, null)
                Text("App brightness", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text("${(brightness * 100).roundToInt()}%")
            }
            Slider(modifier = Modifier.semantics { contentDescription = "Sid Glass OS app brightness" }, value = brightness, onValueChange = { value ->
                brightness = value
                activity?.window?.let { window -> window.attributes = window.attributes.apply { screenBrightness = value } }
            }, valueRange = 0.02f..1f, enabled = activity != null)
            Text("Affects Sid Glass OS while open. Your device brightness setting stays under system control.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { openSetting(Settings.ACTION_DISPLAY_SETTINGS) }) { Text("Device display settings") }
        }
        Panel(settings.glassOpacity) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.FlashlightOn, null)
                Column(Modifier.weight(1f)) {
                    Text("Flashlight", style = MaterialTheme.typography.titleMedium)
                    Text(when { flashId == null -> "No flash hardware available"; !torchAvailable -> "Busy or unavailable"; torchOn -> "On"; else -> "Off" }, style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = {
                    if (flashId != null) {
                        runCatching { camera.setTorchMode(flashId, !torchOn) }
                            .onSuccess { ownsTorch[0] = !torchOn }
                            .onFailure { message = "Flashlight unavailable. The camera may be busy or access restricted." }
                    }
                }, enabled = flashId != null && torchAvailable) { Text(if (torchOn) "Turn off" else "Turn on") }
            }
            Text("Turns off when you leave this panel if enabled here.", style = MaterialTheme.typography.bodySmall)
        }
        GlassMediaCard(settings)
        Panel(settings.glassOpacity) {
            Text("One tap from Quick Settings", style = MaterialTheme.typography.titleMedium)
            Text("Add the Sid Glass OS tile from the system tile editor. It opens this app panel; the OPPO notification shade keeps its system appearance.", style = MaterialTheme.typography.bodySmall)
            if (Build.VERSION.SDK_INT >= 33) {
                OutlinedButton(onClick = {
                    runCatching {
                        context.getSystemService(StatusBarManager::class.java).requestAddTileService(
                            ComponentName(context, ControlCenterTileService::class.java), "Sid Glass OS",
                            AndroidIcon.createWithResource(context, android.R.drawable.ic_menu_manage),
                            ContextCompat.getMainExecutor(context)
                        ) { result ->
                            message = if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                                result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED)
                                "Tile available in Quick Settings." else "Tile was not added. You can use the system tile editor."
                        }
                    }.onFailure { message = "Open Quick Settings and edit tiles to add Sid Glass OS." }
                }) { Text("Add Quick Settings tile") }
            }
        }
        message?.let { Text(it, color = Color(0xFFD4DCFF), style = MaterialTheme.typography.bodyMedium) }
        Text("This is an in-app panel. System shortcuts ask Android to manage protected settings. Floating overlays and notification mirroring are not enabled.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB9C8DF))
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Panel(opacity: Float, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth(), opacity = opacity) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun ControlTile(title: String, subtitle: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier.height(138.dp), shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF284875).copy(alpha = 0.8f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, null, Modifier.size(27.dp), tint = Color(0xFFBBCFFF))
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun Context.findControlActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findControlActivity()
    else -> null
}

private fun readWifiStatus(context: Context): String = runCatching {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
    when {
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Connected · system controls"
        context.applicationContext.getSystemService(WifiManager::class.java).isWifiEnabled -> "On · system controls"
        else -> "Off · system controls"
    }
}.getOrDefault("Status unavailable · Settings")

/** Returns a user-facing status only on failure; a successful launch never implies a setting changed. */
private fun openControlIntent(context: Context, intent: Intent, settingsFallback: Boolean = true): String? {
    return runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        null
    }.getOrElse {
        if (settingsFallback) runCatching {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Specific controls unavailable; opened system Settings."
        }.getOrDefault("System Settings could not be opened on this device.")
        else "No music app is available for this shortcut. Open your preferred player from the app drawer."
    }
}
