package com.sidglassos.features.dashboard

import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sidglassos.ScreenColumn
import com.sidglassos.core.design.*
import com.sidglassos.core.platform.*
import com.sidglassos.core.storage.GlassSettings
import com.sidglassos.features.launcher.LauncherActivity
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(settings: GlassSettings, navigate: (String) -> Unit) {
    val context = LocalContext.current
    var isHome by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        isHome = context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)
    }
    ScreenColumn("Sid Glass OS", "A quieter kind of Android.") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("MIDNIGHT COLLECTION", style = MaterialTheme.typography.labelSmall, color = Color(0xFFACBCFF))
            Surface(color = Color.White.copy(alpha = .1f), shape = RoundedCornerShape(50)) {
                Text(settings.performance.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
        GlassCard(Modifier.fillMaxWidth(), opacity = settings.glassOpacity) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("YOUR SPACE", style = MaterialTheme.typography.labelSmall)
                Text("Theme preview", color = GlassColors.Ice.copy(alpha = .7f), style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(20.dp))
            val now = rememberVisibleTime()
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 64.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = (-3).sp)
            Text(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), color = GlassColors.Ice.copy(alpha = .8f))
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(Icons.Outlined.Call, Icons.Outlined.ChatBubbleOutline, Icons.Outlined.Language, Icons.Outlined.PhotoCamera).forEach {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(settings.iconCornerRadius.dp))
                        .background(Brush.linearGradient(listOf(Color.White.copy(alpha = .22f), GlassColors.Violet.copy(alpha = .2f)))), contentAlignment = Alignment.Center) {
                        Icon(it, contentDescription = null, tint = GlassColors.Ice)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("Soft light. Clear focus.", style = MaterialTheme.typography.bodyMedium)
        }
        GlassButton(if (isHome) "Manage default home" else "Use as home screen", { (context as? Activity)?.chooseHome() }, Modifier.fillMaxWidth())
        OutlinedButton(onClick = { context.openSafely(Intent(context, LauncherActivity::class.java)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Open launcher")
        }
        GlassCard(Modifier.fillMaxWidth(), settings.glassOpacity) {
            Text("Make it yours", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            DashboardLink("Midnight forest", "Live and still wallpapers", Icons.Outlined.Landscape) { navigate("wallpapers") }
            DashboardLink("Glass widgets", "Clock, battery, music & quick info", Icons.Outlined.Widgets) { navigate("widgets") }
            DashboardLink("Lock-screen design", "Preview and wallpaper options", Icons.Outlined.Lock) { navigate("lockscreen") }
            DashboardLink("Notifications", "Preview and a local test notification", Icons.Outlined.NotificationsNone) { navigate("notifications") }
        }
        GlassCard(Modifier.fillMaxWidth(), settings.glassOpacity) {
            Text(if (isHome) "Your glass home is active" else "Ready when you are", style = MaterialTheme.typography.titleMedium)
            Text("${settings.gridColumns} columns · ${settings.iconSize}dp icons · ${if (settings.reducedMotion) "Reduced motion" else "Gentle motion"}", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { navigate("guide") }) { Text("Setup & restore guide") }
        }
    }
}

@Composable
private fun DashboardLink(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 13.dp)) {
        Icon(icon, null, modifier = Modifier.padding(end = 14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, modifier = Modifier.align(Alignment.Start), color = GlassColors.Ice)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = GlassColors.Ice.copy(alpha = .7f))
        }
        Icon(Icons.Outlined.ChevronRight, null)
    }
}
