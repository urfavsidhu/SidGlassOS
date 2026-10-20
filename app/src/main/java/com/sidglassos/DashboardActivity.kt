package com.sidglassos

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidglassos.core.design.*
import com.sidglassos.core.storage.*
import com.sidglassos.features.controlcenter.ControlCenterScreen
import com.sidglassos.features.dashboard.*
import com.sidglassos.features.wallpapers.ForestBackdrop
import com.sidglassos.features.wallpapers.WallpapersScreen
import com.sidglassos.features.widgets.WidgetsScreen

class DashboardActivity : ComponentActivity() {
    private var requestedRoute by mutableStateOf("home")
    private var routeRequest by mutableLongStateOf(0L)
    private val model by viewModels<DashboardViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app has dark surfaces even when Android is using its light theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        requestedRoute = intent.getStringExtra("route") ?: "home"
        routeRequest = savedInstanceState?.getLong("route_request") ?: 0L
        setContent {
            GlassTheme {
                val settings by model.settings.collectAsStateWithLifecycle()
                // Capture the same immutable baseline passed to the screens below.
                val baseline = settings
                val save: (GlassSettings) -> Unit = { updated ->
                    if (baseline != null) model.save(baseline, updated)
                }
                val notice = model.message
                LaunchedEffect(notice) {
                    if (notice != null) {
                        Toast.makeText(this@DashboardActivity, notice, Toast.LENGTH_LONG).show()
                        model.dismissMessage()
                    }
                }
                Box(Modifier.fillMaxSize().background(GlassColors.Navy)) {
                    ForestBackdrop(Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(GlassColors.Navy.copy(alpha = .48f)))
                    val current = settings
                    if (current == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    else Dashboard(current, requestedRoute, routeRequest, save, model::reset)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedRoute = intent.getStringExtra("route") ?: "home"
        routeRequest++
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("route_request", routeRequest)
        super.onSaveInstanceState(outState)
    }
}

private data class Destination(val route: String, val title: String, val icon: ImageVector)
private val navigation = listOf(
    Destination("home", "Home", Icons.Outlined.Home),
    Destination("wallpapers", "Scenes", Icons.Outlined.Landscape),
    Destination("icons", "Icons", Icons.Outlined.Apps),
    Destination("controlcenter", "Controls", Icons.Outlined.Tune),
    Destination("settings", "Settings", Icons.Outlined.Settings),
)

@Composable
private fun Dashboard(settings: GlassSettings, requestedRoute: String, routeRequest: Long, save: (GlassSettings) -> Unit, reset: () -> Unit) {
    var route by rememberSaveable { mutableStateOf(requestedRoute) }
    var handledRequest by rememberSaveable { mutableLongStateOf(routeRequest) }
    LaunchedEffect(routeRequest) {
        if (handledRequest != routeRequest) {
            route = requestedRoute
            handledRequest = routeRequest
        }
    }
    BackHandler(route != "home") { route = "home" }
    val navigate: (String) -> Unit = { route = it }
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(containerColor = GlassColors.Navy.copy(alpha = .9f), tonalElevation = 0.dp) {
                navigation.forEach { destination ->
                    NavigationBarItem(selected = route == destination.route, onClick = { route = destination.route },
                        icon = { Icon(destination.icon, contentDescription = null) }, label = { Text(destination.title) })
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (route) {
                "wallpapers" -> WallpapersScreen(settings, onBack = { route = "home" }, onSettingsChange = save)
                "widgets" -> WidgetsScreen(settings, onBack = { route = "home" }, onSettingsChange = save)
                "controlcenter" -> ControlCenterScreen(settings, onBack = { route = "home" })
                "icons" -> IconsScreen(settings, save)
                "settings" -> SettingsScreen(settings, save, reset, navigate)
                "lockscreen" -> LockPreviewScreen(settings)
                "notifications" -> NotificationScreen(settings)
                "guide" -> GuideScreen()
                else -> HomeScreen(settings, navigate)
            }
        }
    }
}

@Composable
fun ScreenColumn(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(subtitle, color = GlassColors.Ice.copy(alpha = .72f), style = MaterialTheme.typography.bodyMedium)
        content()
        Spacer(Modifier.height(12.dp))
    }
}
