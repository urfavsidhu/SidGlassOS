package com.sidglassos.features.dashboard

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.sidglassos.DashboardActivity
import com.sidglassos.R
import com.sidglassos.ScreenColumn
import com.sidglassos.core.design.*
import com.sidglassos.core.platform.openSafely
import com.sidglassos.core.storage.GlassSettings
import com.sidglassos.features.media.GlassMediaCard
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun LockPreviewScreen(settings: GlassSettings) {
    val context = LocalContext.current
    val now = rememberVisibleTime()
    ScreenColumn("After hours", "Lock-screen design preview") {
        GlassCard(Modifier.fillMaxWidth(), settings.glassOpacity) {
            Text("DESIGN PREVIEW", style = MaterialTheme.typography.labelSmall)
            Text(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), Modifier.padding(top = 28.dp).align(Alignment.CenterHorizontally))
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), Modifier.align(Alignment.CenterHorizontally), fontSize = 78.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = (-4).sp)
            Spacer(Modifier.height(48.dp))
            GlassMediaCard(settings)
            Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { context.openSafely(Intent(context, DashboardActivity::class.java).putExtra("route", "controlcenter")) }) { Icon(Icons.Outlined.FlashlightOn, "Open glass controls") }
                IconButton(onClick = { context.openSafely(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) }) { Icon(Icons.Outlined.PhotoCamera, "Open camera") }
            }
        }
        GlassCard(Modifier.fillMaxWidth(), settings.glassOpacity) {
            Text("Keep Android's protection", style = MaterialTheme.typography.titleMedium)
            Text("This screen previews the visual direction. ColorOS controls the real clock, music card and secure lock screen. You can apply the forest wallpaper from Scenes, then choose any clock styles offered by ColorOS.", Modifier.padding(top = 10.dp))
            GlassButton("Open device security settings", { context.openSafely(Intent(Settings.ACTION_SECURITY_SETTINGS)) }, Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
fun NotificationScreen(settings: GlassSettings) {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(NotificationManager::class.java) }
    fun postTest() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "Notification permission is off. The in-app preview still works.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            manager.createNotificationChannel(NotificationChannel("glass_preview", "Local preview", NotificationManager.IMPORTANCE_DEFAULT))
        } catch (_: SecurityException) {
            Toast.makeText(context, "Android did not allow the preview notification channel.", Toast.LENGTH_LONG).show()
            return
        }
        if (!manager.areNotificationsEnabled()) {
            Toast.makeText(context, "Notifications are disabled. You can enable them in system Settings.", Toast.LENGTH_LONG).show()
            return
        }
        if (manager.getNotificationChannel("glass_preview")?.importance == NotificationManager.IMPORTANCE_NONE) {
            Toast.makeText(context, "The Local preview channel is disabled. Open system notification settings to enable it.", Toast.LENGTH_LONG).show()
            return
        }
        val launch = PendingIntent.getActivity(context, 901, Intent(context, DashboardActivity::class.java).putExtra("route", "notifications"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, "glass_preview")
            .setSmallIcon(R.drawable.ic_glass).setContentTitle("A little clarity")
            .setContentText("Your Sid Glass OS local notification is ready.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("This is a local test. Android and ColorOS choose how this notification appears in the system shade."))
            .setContentIntent(launch).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        runCatching { manager.notify(901, notification) }.onSuccess {
            Toast.makeText(context, "Test posted. Channel settings determine visibility.", Toast.LENGTH_LONG).show()
        }.onFailure { Toast.makeText(context, "Android did not allow the test notification.", Toast.LENGTH_LONG).show() }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) postTest() else Toast.makeText(context, "Notification permission was declined. The preview still works.", Toast.LENGTH_LONG).show()
    }
    ScreenColumn("A moment of clarity", "Notification design & a local system test") {
        GlassCard(Modifier.fillMaxWidth(), settings.glassOpacity) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.NotificationsNone, null)
                Text("  SID GLASS OS", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                Text("Preview", style = MaterialTheme.typography.labelSmall)
            }
            Text("A little clarity", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleLarge)
            Text("A softer surface for the things that matter.", Modifier.padding(top = 8.dp))
        }
        Text("The card above is an in-app design preview. Your system notifications keep Android's standard template. This test needs no access to other apps' notifications; notification mirroring is not used.")
        GlassButton("Send a local test notification", {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else postTest()
        }, Modifier.fillMaxWidth())
        OutlinedButton(onClick = { context.openSafely(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }, modifier = Modifier.fillMaxWidth()) { Text("System notification settings") }
        TextButton(onClick = { manager.cancel(901) }) { Text("Dismiss test notification") }
    }
}

/** Minute precision without a timer continuing while the Activity is stopped. */
@Composable
internal fun rememberVisibleTime(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = LocalDateTime.now()
                delay(60_000L - System.currentTimeMillis().mod(60_000L))
            }
        }
    }
    return now
}
