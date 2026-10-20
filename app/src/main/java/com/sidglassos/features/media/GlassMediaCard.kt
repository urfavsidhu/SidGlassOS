package com.sidglassos.features.media

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidglassos.core.design.GlassCard
import com.sidglassos.core.design.GlassColors
import com.sidglassos.core.storage.GlassSettings

/** Local media-session UI. Transport requests never change displayed state optimistically. */
@Composable
fun GlassMediaCard(settings: GlassSettings, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val snapshot by MediaBridge.state.collectAsStateWithLifecycle()
    var explainAccess by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { MediaBridge.refreshAccess(context) }
    val artwork = remember(snapshot.artwork) {
        snapshot.artwork?.takeUnless { it.isRecycled }?.asImageBitmap()
    }

    fun openAccess() {
        message = null
        if (!MediaBridge.openAccessSettings(context)) {
            message = "Android could not open notification access settings. Open Android Settings and search for Notification access to manage Sid Glass OS."
        }
    }

    fun requestTransport(request: () -> Boolean) {
        message = null
        if (!request()) message = "This playback action is unavailable. Open your music app to continue."
    }

    GlassCard(modifier = modifier.fillMaxWidth(), opacity = settings.glassOpacity) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("MUSIC", style = MaterialTheme.typography.labelSmall, color = GlassColors.Ice.copy(alpha = .8f))
        }
        Spacer(Modifier.height(18.dp))

        if (snapshot.accessGranted && snapshot.active) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .1f)), contentAlignment = Alignment.Center) {
                    if (artwork != null) Image(artwork, contentDescription = "Album artwork", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Icon(Icons.Outlined.MusicNote, contentDescription = null, modifier = Modifier.size(32.dp), tint = GlassColors.Ice)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(snapshot.title.ifBlank { "Active media session" }, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (snapshot.artist.isNotBlank()) Text(snapshot.artist, style = MaterialTheme.typography.bodyMedium, color = GlassColors.Ice.copy(alpha = .72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { requestTransport(MediaBridge::previous) }, enabled = snapshot.canPrevious) {
                    Icon(Icons.Outlined.SkipPrevious, contentDescription = "Previous track")
                }
                FilledTonalIconButton(onClick = { requestTransport(MediaBridge::playPause) }, enabled = snapshot.canPlayPause, modifier = Modifier.size(56.dp)) {
                    Icon(if (snapshot.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (snapshot.playing) "Pause playback" else "Play media")
                }
                IconButton(onClick = { requestTransport(MediaBridge::next) }, enabled = snapshot.canNext) {
                    Icon(Icons.Outlined.SkipNext, contentDescription = "Next track")
                }
            }
        } else {
            Text(if (snapshot.accessGranted) "No active music session" else "Your music, within reach", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                if (snapshot.accessGranted) "Start playback in a music app, then return here. Some players do not expose Android media controls."
                else "Open a music app to start playback. Optionally connect media to see the current track and supported playback controls here.",
                style = MaterialTheme.typography.bodyMedium, color = GlassColors.Ice.copy(alpha = .75f),
            )
        }

        Spacer(Modifier.height(8.dp))
        // Separate rows keep both actions usable with large text and narrow windows.
        TextButton(onClick = { message = openMusicApp(context) }, modifier = Modifier.fillMaxWidth()) { Text("Open music") }
        if (snapshot.accessGranted) {
            TextButton(onClick = ::openAccess, modifier = Modifier.fillMaxWidth()) { Text("Manage media access") }
        } else {
            OutlinedButton(onClick = { explainAccess = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) { Text("Connect media") }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
    }

    if (explainAccess) {
        AlertDialog(
            onDismissRequest = { explainAccess = false },
            title = { Text("Connect media controls?") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text("Android grants notification access, which can expose notifications from every app.")
                    Spacer(Modifier.height(12.dp))
                    Text("Sid Glass OS uses this permission only to discover active media sessions. It ignores notification contents and processes media metadata locally in memory. It never saves or uploads that metadata.")
                    Spacer(Modifier.height(12.dp))
                    Text("Access is optional. Continue opens Android Settings, where you can enable access yourself. You can revoke it at any time using Manage media access.")
                }
            },
            confirmButton = { TextButton(onClick = { explainAccess = false; openAccess() }) { Text("Continue") } },
            dismissButton = { TextButton(onClick = { explainAccess = false }) { Text("Cancel") } },
        )
    }
}

/** A real installed music-app selector; an unavailable handler is an explicit fallback state. */
private fun openMusicApp(context: Context): String? = try {
    context.startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC))
    null
} catch (_: ActivityNotFoundException) {
    "No music app handles this shortcut. Open your preferred player from All apps."
} catch (_: SecurityException) {
    "Android did not allow opening the music app. Open it from All apps."
}
