package com.sidglassos.features.wallpapers

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.sidglassos.core.design.GlassCard
import com.sidglassos.core.design.GlassSlider
import com.sidglassos.core.storage.GlassSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WallpapersScreen(settings: GlassSettings, onBack: () -> Unit,
    onSettingsChange: (GlassSettings) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var applying by remember { mutableStateOf(false) }
    var particleDraft by remember(settings.particleCount) { mutableFloatStateOf(settings.particleCount.toFloat()) }
    var intensityDraft by remember(settings.animationIntensity) { mutableFloatStateOf(settings.animationIntensity) }
    fun apply(which: Int, destination: String) {
        applying = true
        scope.launch {
            message = try {
                applyStaticWallpaper(context, which)
                "Forest wallpaper applied to $destination."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                "Wallpaper could not be applied. ${error.message ?: "Try your phone's Wallpaper settings."}"
            } finally { applying = false }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Moonlit forest", style = MaterialTheme.typography.headlineSmall)
        }
        Box(Modifier.fillMaxWidth().height(330.dp).clip(RoundedCornerShape(32.dp))) {
            ForestBackdrop(Modifier.fillMaxSize())
            Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
                Text("AFTER HOURS", style = MaterialTheme.typography.labelSmall)
                Text("A quieter kind of glow.", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Light)
                Text("Original procedural artwork · static preview", style = MaterialTheme.typography.bodySmall)
            }
        }
        GlassCard(opacity = settings.glassOpacity) {
            Text("Live atmosphere", style = MaterialTheme.typography.titleLarge)
            Text("Drifting light, layered pines and optional home-page parallax. The Android preview lets you choose the available destination.",
                modifier = Modifier.padding(vertical = 10.dp))
            Button(onClick = {
                message = openLiveWallpaperPicker(context)
            }, modifier = Modifier.fillMaxWidth()) { Text("Preview live wallpaper") }
            Text("Balanced: up to 24 fps. Battery Saver and Reduced motion show a still frame. Rendering pauses when hidden or the screen turns off.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
        }
        GlassCard(opacity = settings.glassOpacity) {
            Text("Keep it still", style = MaterialTheme.typography.titleLarge)
            Text("Apply this original forest image to a destination you choose.", modifier = Modifier.padding(vertical = 10.dp))
            OutlinedButton(enabled = !applying, onClick = { apply(WallpaperManager.FLAG_SYSTEM, "your home screen") },
                modifier = Modifier.fillMaxWidth()) { Text("Set home wallpaper") }
            OutlinedButton(enabled = !applying, onClick = { apply(WallpaperManager.FLAG_LOCK, "your lock screen") },
                modifier = Modifier.fillMaxWidth()) { Text("Set lock wallpaper") }
            OutlinedButton(enabled = !applying, onClick = {
                apply(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK, "home and lock screens")
            }, modifier = Modifier.fillMaxWidth()) { Text("Set both wallpapers") }
            if (applying) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            Text("The system still manages your secure lock screen, clock and notifications. Live lock-screen availability depends on the ColorOS wallpaper picker.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        GlassCard(opacity = settings.glassOpacity) {
            Text("Atmosphere settings", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp))
            GlassSlider("Particles · ${particleDraft.toInt()}", particleDraft, 0f..60f,
                onValueChange = { particleDraft = it },
                onValueChangeFinished = { onSettingsChange(settings.copy(particleCount = particleDraft.toInt())) })
            GlassSlider("Motion intensity · ${(intensityDraft * 100).toInt()}%", intensityDraft, 0f..1f,
                onValueChange = { intensityDraft = it },
                onValueChangeFinished = { onSettingsChange(settings.copy(animationIntensity = intensityDraft)) })
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Home-page parallax", modifier = Modifier.weight(1f))
                Switch(checked = settings.parallax, onCheckedChange = { onSettingsChange(settings.copy(parallax = it)) })
            }
            Text("Parallax follows launcher page offsets when the launcher supplies them. Device tilt sensors are not used.",
                style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** Writes only after a destination button is tapped. Bitmap work and Binder I/O stay off main. */
suspend fun applyStaticWallpaper(context: Context, which: Int) = withContext(Dispatchers.IO) {
    require(which == WallpaperManager.FLAG_SYSTEM || which == WallpaperManager.FLAG_LOCK ||
        which == (WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK))
    val manager = WallpaperManager.getInstance(context)
    check(manager.isWallpaperSupported) { "This device does not support custom wallpapers." }
    check(manager.isSetWallpaperAllowed) { "Wallpaper changes are disabled by a device policy." }
    val metrics = context.resources.displayMetrics
    val width = metrics.widthPixels.coerceIn(720, 1440)
    val height = (width.toFloat() * metrics.heightPixels / metrics.widthPixels.coerceAtLeast(1))
        .toInt().coerceIn(1280, 3200)
    val bitmap = createBitmap(width, height)
    try {
        ForestRenderer.draw(Canvas(bitmap), width, height)
        manager.setBitmap(bitmap, null, true, which)
    } finally { bitmap.recycle() }
}

private fun openLiveWallpaperPicker(context: Context): String? {
    val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(context, ForestWallpaperService::class.java))
    return try { context.startActivity(direct); null }
    catch (_: ActivityNotFoundException) {
        try { context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
            "Choose Moonlit Forest in the Android wallpaper picker." }
        catch (_: ActivityNotFoundException) { "No live-wallpaper picker is available. Use a static wallpaper or your phone's Wallpaper settings." }
        catch (_: SecurityException) { "Your device has restricted the live-wallpaper picker. Static wallpapers are available above." }
    } catch (_: SecurityException) { "Your device has restricted the live-wallpaper picker. Static wallpapers are available above." }
}
