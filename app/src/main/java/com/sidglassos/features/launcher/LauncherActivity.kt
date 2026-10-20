package com.sidglassos.features.launcher

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.sidglassos.core.design.GlassColors
import com.sidglassos.core.design.GlassSymbols
import com.sidglassos.core.design.GlassTheme
import com.sidglassos.core.design.GlassRuntime
import com.sidglassos.core.design.GlassSurface
import com.sidglassos.core.design.ModalWindowBlur
import com.sidglassos.core.storage.GlassSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Standard Android HOME activity; default-home selection belongs to the system role flow. */
class LauncherActivity : ComponentActivity() {
    private val launcher: LauncherViewModel by viewModels()
    private var homeGeneration by mutableIntStateOf(0)
    private var widgetPickerRequest by mutableIntStateOf(0)
    internal lateinit var widgetHost: LauncherWidgetHost
        private set
    private val widgetBinding = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (::widgetHost.isInitialized) widgetHost.bindingResult(it.resultCode)
    }
    private var receiverRegistered = false
    private val packageChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { launcher.refresh(invalidateIcons = true) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep light system icons over the dark launcher without covering its wallpaper.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        widgetHost = LauncherWidgetHost(this)
        if (intent.getBooleanExtra(LauncherWidgetHost.OPEN_PICKER, false)) widgetPickerRequest++
        setContent { GlassTheme { LauncherScreen(launcher, homeGeneration, widgetHost, widgetPickerRequest) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        homeGeneration++
        if (intent.getBooleanExtra(LauncherWidgetHost.OPEN_PICKER, false)) widgetPickerRequest++
    }

    internal fun requestWidgetBinding(intent: Intent) { widgetBinding.launch(intent) }

    @Deprecated("AppWidgetHost configuration uses the platform's request-code result API")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == LauncherWidgetHost.REQUEST_CONFIGURE) widgetHost.configurationResult(resultCode)
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageChanges, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
        launcher.refresh()
        widgetHost.start()
    }

    override fun onStop() {
        if (receiverRegistered) { unregisterReceiver(packageChanges); receiverRegistered = false }
        widgetHost.stop()
        super.onStop()
    }

    override fun onDestroy() { widgetHost.dispose(); super.onDestroy() }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        launcher.catalog.trimMemory()
    }
}

private fun launchSafely(context: Context, intent: Intent) {
    try { context.startActivity(intent) }
    catch (_: ActivityNotFoundException) { Toast.makeText(context, "This app or settings screen is no longer available.", Toast.LENGTH_LONG).show() }
    catch (_: SecurityException) { Toast.makeText(context, "Android does not allow opening this screen.", Toast.LENGTH_LONG).show() }
    catch (_: IllegalArgumentException) { Toast.makeText(context, "This app shortcut is invalid. Remove it and add the app again.", Toast.LENGTH_LONG).show() }
}

private fun openApp(context: Context, app: LaunchableApp) {
    val component = ComponentName.unflattenFromString(app.id) ?: return
    launchSafely(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LauncherScreen(model: LauncherViewModel, homeGeneration: Int, widgetHost: LauncherWidgetHost, widgetPickerRequest: Int) {
    val context = LocalContext.current
    val settings by model.settings.collectAsStateWithLifecycle()
    val state by model.appState.collectAsStateWithLifecycle()
    val byId = remember(state.apps) { state.apps.associateBy { it.id } }
    val home = remember(settings.homeOrder, byId) { settings.homeOrder.mapNotNull(byId::get) }
    val dock = remember(settings.dock, byId) { settings.dock.mapNotNull(byId::get) }
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<LaunchableApp?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var widgetPickerOpen by rememberSaveable { mutableStateOf(false) }
    val windowSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val windowWidthDp = with(density) { windowSize.width.toDp().value }
    val windowHeightDp = with(density) { windowSize.height.toDp().value }
    val contentWidth = (windowWidthDp.toInt() - 32).coerceAtLeast(96)
    // Keep touch targets and artwork inside their cells on narrow or split-screen windows.
    val columns = settings.gridColumns.coerceIn(3, 6).coerceAtMost((contentWidth / 48).coerceAtLeast(1))
    val fittedSpacing = settings.iconSpacing.coerceAtMost(((contentWidth - columns * 48) / (columns - 1).coerceAtLeast(1)).coerceAtLeast(0))
    val fittedIconSize = settings.iconSize.coerceAtMost((contentWidth - fittedSpacing * (columns - 1)) / columns)
    val displaySettings = settings.copy(iconSize = fittedIconSize, iconSpacing = fittedSpacing)
    val compactHeight = windowHeightDp < 540
    val rowHeight = fittedIconSize + (if (settings.showLabels) settings.labelSize * density.fontScale else 0f) + 34
    val rows = ((windowHeightDp - if (compactHeight) 280 else 370) / rowHeight).toInt().coerceIn(1, 4)
    val pageCapacity = columns * rows
    val appPageCount = ((home.size + pageCapacity - 1) / pageCapacity).coerceAtLeast(1)
    val pageCount = appPageCount + widgetHost.widgets.size
    val pager = rememberPagerState { pageCount }
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    var dragged by remember { mutableStateOf<String?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    LaunchedEffect(homeGeneration) { drawerOpen = false; selected = null; editing = false; query = ""; widgetPickerOpen = false }
    LaunchedEffect(widgetPickerRequest) { if (widgetPickerRequest > 0) widgetPickerOpen = true }
    LaunchedEffect(widgetHost.navigationRequest) {
        if (widgetHost.navigationRequest > 0) {
            val index = widgetHost.widgets.indexOfFirst { it.id == widgetHost.lastAddedId }
            if (index >= 0) pager.scrollToPage(appPageCount + index)
        }
    }
    LaunchedEffect(widgetHost.message) {
        widgetHost.message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); widgetHost.dismissMessage() }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) { now = LocalDateTime.now(); delay(15_000) }
        }
    }
    LaunchedEffect(model, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            model.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        }
    }
    LaunchedEffect(pageCount) { if (pager.currentPage >= pageCount) pager.scrollToPage(pageCount - 1) }
    BackHandler { drawerOpen = false; selected = null; editing = false; widgetPickerOpen = false }

    GlassRuntime(settings) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .08f))) {
        // The home activity intentionally follows the actual Android wallpaper, including other wallpaper apps.
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("SID / GLASS", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), color = GlassColors.Ice.copy(alpha = .7f))
                TextButton(onClick = { editing = !editing }) { Text(if (editing) "Done" else "Arrange") }
                IconButton(onClick = { launchSafely(context, Intent().setClassName(context.packageName, "com.sidglassos.DashboardActivity")) }) {
                    Icon(Icons.Outlined.Tune, "Open customization dashboard")
                }
            }
            Text(now.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)), fontSize = if (compactHeight) 32.sp else 58.sp, fontWeight = FontWeight.Light, color = Color.White)
            Text(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), style = MaterialTheme.typography.bodyMedium, color = GlassColors.Ice.copy(alpha = .8f))
            if (editing) Text("Hold and drag an icon to move it. App options include accessible move buttons.", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 8.dp))
            if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
                TextButton(onClick = { model.refresh(invalidateIcons = true) }) { Text("Retry loading apps") }
            }
            if (state.loading && home.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
            PendingWidgetNotice(widgetHost)
            HorizontalPager(pager, modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.Top, userScrollEnabled = dragged == null) { page ->
                if (page >= appPageCount) {
                    widgetHost.widgets.getOrNull(page - appPageCount)?.let {
                        HostedWidgetPage(it, widgetHost, page - appPageCount, widgetHost.widgets.size)
                    }
                    return@HorizontalPager
                }
                val pageApps = home.drop(page * pageCapacity).take(pageCapacity)
                if (pageApps.isEmpty()) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("A little space to make yours.", style = MaterialTheme.typography.titleMedium)
                        Text("Open All apps, then hold an app to add it here.", textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
                    }
                } else {
                    LazyVerticalGrid(columns = GridCells.Fixed(columns), horizontalArrangement = Arrangement.spacedBy(fittedSpacing.dp), verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(pageApps, key = { it.id }) { app ->
                            val dragModifier = if (editing) Modifier.pointerInput(app.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        dragged = app.id
                                        dragOffset = Offset.Zero
                                        dragPosition = (bounds[app.id]?.topLeft ?: Offset.Zero) + offset
                                    },
                                    onDrag = { change, amount -> change.consume(); dragOffset += amount; dragPosition += amount },
                                    onDragEnd = {
                                        val target = pageApps.firstOrNull { it.id != app.id && bounds[it.id]?.contains(dragPosition) == true }
                                        if (target != null) model.moveHome(app.id, target.id)
                                        dragged = null; dragOffset = Offset.Zero
                                    },
                                    onDragCancel = { dragged = null; dragOffset = Offset.Zero },
                                )
                            } else Modifier
                            DisposableEffect(app.id) { onDispose { bounds.remove(app.id) } }
                            LauncherAppCell(app, displaySettings, model.catalog, state.iconGeneration,
                                onClick = { if (editing) selected = app else openApp(context, app) },
                                onOptions = { selected = app },
                                modifier = Modifier.onGloballyPositioned { bounds[app.id] = it.boundsInRoot() }
                                    .zIndex(if (dragged == app.id) 1f else 0f)
                                    .graphicsLayer {
                                        translationX = if (dragged == app.id) dragOffset.x else 0f
                                        translationY = if (dragged == app.id) dragOffset.y else 0f
                                        scaleX = if (dragged == app.id) 1.08f else 1f
                                        scaleY = if (dragged == app.id) 1.08f else 1f
                                    }
                                    .then(dragModifier),
                                longPressOptions = !editing,
                            )
                        }
                    }
                }
            }
            if (pageCount > 1) Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) {
                repeat(pageCount) { page -> Box(Modifier.padding(3.dp).size(if (page == pager.currentPage) 7.dp else 5.dp).clip(CircleShape).background(Color.White.copy(alpha = if (page == pager.currentPage) .95f else .35f))) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { drawerOpen = true }) {
                Icon(Icons.Outlined.Apps, null); Spacer(Modifier.width(8.dp)); Text("All apps")
            }
            TextButton(onClick = { widgetPickerOpen = true }) {
                Icon(Icons.Outlined.Widgets, null); Spacer(Modifier.width(8.dp)); Text("Widgets")
            }
            }
            GlassSurface(opacity = settings.glassOpacity.coerceIn(.08f, .5f), shape = RoundedCornerShape(30.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    dock.forEach { app ->
                        LauncherAppCell(app, settings.copy(showLabels = false, iconSize = settings.iconSize.coerceAtMost(60)), model.catalog, state.iconGeneration, { openApp(context, app) }, { selected = app }, Modifier.weight(1f))
                    }
                    if (dock.isEmpty()) Text("Hold an app in All apps to add it to the dock.", modifier = Modifier.padding(12.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (drawerOpen) {
        ModalBottomSheet(onDismissRequest = { drawerOpen = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = GlassColors.Navy.copy(alpha = .94f)) {
            ModalWindowBlur()
            Column(Modifier.fillMaxHeight(.94f).padding(horizontal = 16.dp)) {
                Text("Your apps", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text("Search apps or packages") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Clear search") } }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), shape = RoundedCornerShape(24.dp))
                val filtered = remember(state.apps, query) { LayoutOrder.search(state.apps, query) }
                if (filtered.isEmpty()) Text(if (state.loading) "Loading apps…" else "No matching apps.", modifier = Modifier.padding(20.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(columns), verticalArrangement = Arrangement.spacedBy(20.dp), horizontalArrangement = Arrangement.spacedBy(fittedSpacing.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
                    items(filtered, key = { it.id }) { app ->
                        LauncherAppCell(app, displaySettings, model.catalog, state.iconGeneration, { drawerOpen = false; openApp(context, app) }, { selected = app })
                    }
                }
            }
        }
    }
    if (widgetPickerOpen) WidgetPicker(widgetHost, onClose = { widgetPickerOpen = false })
    selected?.let { app ->
        AppOptions(app, settings, onDismiss = { selected = null }, onOpen = { selected = null; drawerOpen = false; openApp(context, app) },
            onHome = { if (app.id in settings.homeOrder) model.removeHome(app.id) else model.addHome(app.id); selected = null },
            onDock = { if (app.id in settings.dock) model.removeDock(app.id) else model.addDock(app.id); selected = null },
            onMove = { model.moveHomeBy(app.id, it) },
            onAssign = { model.assignIcon(app.id, it) },
            onInfo = { selected = null; launchSafely(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))) },
        )
    }
    }
}

@Composable
private fun LauncherAppCell(
    app: LaunchableApp,
    settings: GlassSettings,
    catalog: AppCatalog,
    iconGeneration: Int,
    onClick: () -> Unit,
    onOptions: () -> Unit,
    modifier: Modifier = Modifier,
    longPressOptions: Boolean = true,
) {
    Column(modifier.fillMaxWidth().semantics {
        contentDescription = app.label
        customActions = listOf(CustomAccessibilityAction("App options for ${app.label}") { onOptions(); true })
    }.combinedClickable(onClick = onClick, onLongClickLabel = "App options", onLongClick = if (longPressOptions) onOptions else null).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        GlassAppIcon(app, settings, catalog, iconGeneration = iconGeneration)
        if (settings.showLabels) Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = settings.labelSize.sp, textAlign = TextAlign.Center, color = Color.White, modifier = Modifier.padding(top = 7.dp).fillMaxWidth())
    }
}

/** Original glass framing; optional open Material category glyphs never modify protected app resources. */
@Composable
fun GlassAppIcon(app: LaunchableApp, settings: GlassSettings, catalog: AppCatalog, modifier: Modifier = Modifier, iconGeneration: Int = 0) {
    val density = LocalDensity.current
    val densityDpi = LocalResources.current.displayMetrics.densityDpi
    val sizePx = with(density) { settings.iconSize.dp.roundToPx() }
    val assigned = settings.iconAssignments[app.id]
    val bitmap by produceState<ImageBitmap?>(null, app.id, sizePx, Triple(assigned, densityDpi, iconGeneration)) {
        value = if (assigned == null) catalog.icon(app.id, sizePx, densityDpi, app.iconVersion)?.asImageBitmap() else null
    }
    val shape = RoundedCornerShape(settings.iconCornerRadius.dp)
    Box(modifier.size(settings.iconSize.dp).clip(shape)
        .background(Brush.linearGradient(if (settings.lightGlass) listOf(Color(0xFFC4D4FF).copy(alpha = .7f), Color.White.copy(alpha = .28f)) else listOf(Color.White.copy(alpha = .2f), Color(0xFF5062A0).copy(alpha = .12f))))
        .border(BorderStroke(1.dp, Color.White.copy(alpha = .35f)), shape).padding(9.dp), contentAlignment = Alignment.Center) {
        if (assigned != null) Icon(GlassSymbols.icon(assigned), null, tint = if (settings.lightGlass) GlassColors.Navy else GlassColors.Ice, modifier = Modifier.fillMaxSize())
        else if (bitmap != null) Image(bitmap!!, contentDescription = null, modifier = Modifier.fillMaxSize())
        else Text(app.label.take(1).uppercase(), color = GlassColors.Ice, fontSize = 24.sp)
    }
}

@Composable
private fun AppOptions(app: LaunchableApp, settings: GlassSettings, onDismiss: () -> Unit, onOpen: () -> Unit, onHome: () -> Unit, onDock: () -> Unit, onMove: (Int) -> Unit, onAssign: (String?) -> Unit, onInfo: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(app.label) }, confirmButton = { TextButton(onClick = onOpen) { Text("Open app") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } }, text = {
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
            TextButton(onClick = onHome) { Text(if (app.id in settings.homeOrder) "Remove from home" else "Add to home") }
            TextButton(onClick = onDock) { Text(if (app.id in settings.dock) "Remove from dock" else "Add to dock") }
            if (app.id in settings.homeOrder) {
                Row {
                    TextButton(onClick = { onMove(-1) }, enabled = settings.homeOrder.firstOrNull() != app.id) { Text("Move earlier") }
                    TextButton(onClick = { onMove(1) }, enabled = settings.homeOrder.lastOrNull() != app.id) { Text("Move later") }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Launcher icon", style = MaterialTheme.typography.titleSmall)
            Text("Category artwork affects this launcher only.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { onAssign(null) }) { Text(if (settings.iconAssignments[app.id] == null) "✓ Original app icon" else "Use original app icon") }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(GlassSymbols.categories.keys.toList()) { symbol ->
                    FilterChip(selected = settings.iconAssignments[app.id] == symbol, onClick = { onAssign(symbol) }, label = { Text(symbol) }, leadingIcon = { Icon(GlassSymbols.icon(symbol), null, modifier = Modifier.size(18.dp)) })
                }
            }
            TextButton(onClick = onInfo) { Text("Android app information") }
        }
    })
}
