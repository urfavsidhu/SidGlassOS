package com.sidglassos.features.launcher

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sidglassos.core.design.GlassCard
import com.sidglassos.core.design.ModalWindowBlur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil

private data class WidgetChoice(val info: AppWidgetProviderInfo, val label: String, val app: String)

@Composable
fun WidgetPicker(host: LauncherWidgetHost, onClose: () -> Unit) {
    val context = LocalContext.current
    var choices by remember { mutableStateOf<List<WidgetChoice>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(host, host.providerGeneration, reload) {
        loading = true; failed = false
        val result = withContext(Dispatchers.IO) {
            runCatching {
                host.installedProviders().filter { it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 }
                    .map { info ->
                        WidgetChoice(info, info.loadLabel(context.packageManager).orEmpty().ifBlank { "Widget" },
                            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(info.provider.packageName, 0)).toString())
                    }.sortedWith(compareBy({ it.app.lowercase() }, { it.label.lowercase() }))
            }
        }
        choices = result.getOrDefault(emptyList()); failed = result.isFailure
        loading = false
    }
    AlertDialog(onDismissRequest = onClose, title = { Text("Add a widget") },
        text = {
            ModalWindowBlur()
            Column {
                Text("Each widget gets a Home page. Android asks before connecting another app's widget.")
                OutlinedTextField(query, { query = it }, label = { Text("Search widgets") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (failed) TextButton(onClick = { reload++ }) { Text("Retry widget list") }
                val filtered = choices.filter { it.label.contains(query, true) || it.app.contains(query, true) }
                if (!loading && !failed && filtered.isEmpty()) Text("No matching widgets.")
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.info.provider.flattenToString() }) { choice ->
                        OutlinedButton(onClick = { onClose(); host.add(choice.info) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) {
                                Text("Add ${choice.label}")
                                Text(choice.app, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onClose) { Text("Close widget picker") } })
}

@Composable
fun HostedWidgetPage(item: HostedWidget, host: LauncherWidgetHost, index: Int, count: Int) {
    val context = LocalContext.current
    // Provider callbacks invalidate this lookup after an update/removal.
    val info = remember(item.id, host.providerGeneration) { host.provider(item.id) }
    var confirmRemove by rememberSaveable(item.id) { mutableStateOf(false) }
    val label = remember(info) { runCatching { info?.loadLabel(context.packageManager) }.getOrNull() ?: "Unavailable widget" }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth.value.toInt().coerceAtLeast(1)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            if (info == null) {
                GlassCard(Modifier.fillMaxWidth()) { Text("This widget's app is unavailable. Reinstall or enable its app, or remove the widget.") }
            } else {
                val density = LocalDensity.current.density
                val padding = AppWidgetHostView.getDefaultPaddingForWidget(context, info.provider, null)
                val canResize = info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
                val minimumPixels = if (canResize && info.minResizeHeight > 0) info.minResizeHeight else info.minHeight
                val minimum = ceil((minimumPixels + padding.top + padding.bottom) / density).toInt()
                val maximum = if (Build.VERSION.SDK_INT >= 31 && info.maxResizeHeight > 0)
                    ceil((info.maxResizeHeight + padding.top + padding.bottom) / density).toInt() else 1200
                val height = WidgetLayoutPolicy.height(item.heightDp, minimum, maximum)
                val minWidth = if (info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0 && info.minResizeWidth > 0) info.minResizeWidth else info.minWidth
                if (ceil((minWidth + padding.left + padding.right) / density) > width || minimum > 1200) {
                    Text("This widget needs more space. Rotate to a wider Home window or choose a smaller widget.")
                } else {
                    AndroidView(factory = {
                        try { host.createView(item.id, info) }
                        catch (_: RuntimeException) { TextView(it).apply { setText(com.sidglassos.R.string.widget_host_error) } }
                    }, update = { view ->
                        if (view is SidWidgetHostView) view.updateSize(width, height)
                    }, modifier = Modifier.fillMaxWidth().height(height.dp))
                    if (canResize) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(enabled = height > minimum.coerceAtLeast(48), onClick = { host.resize(item.id, height - 48) }) { Text("Shorter widget") }
                        TextButton(enabled = height < maximum.coerceIn(minimum.coerceIn(48, 1200), 1200), onClick = { host.resize(item.id, height + 48) }) { Text("Taller widget") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(enabled = index > 0, onClick = { host.move(item.id, -1) }) { Text("Move earlier") }
                TextButton(enabled = index < count - 1, onClick = { host.move(item.id, 1) }) { Text("Move later") }
                TextButton(onClick = { confirmRemove = true }) { Text("Remove widget") }
            }
        }
    }
    if (confirmRemove) AlertDialog(onDismissRequest = { confirmRemove = false },
        title = { Text("Remove this widget?") }, text = { Text("$label will be removed from Sid Home. Its app stays installed.") },
        confirmButton = { TextButton(onClick = { confirmRemove = false; host.remove(item.id) }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Keep widget") } })
}

@Composable
fun PendingWidgetNotice(host: LauncherWidgetHost) {
    if (host.pendingId != AppWidgetManager.INVALID_APPWIDGET_ID) {
        Row(Modifier.fillMaxWidth()) {
            Text("Widget setup is pending.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = host::cancelPending) { Text("Cancel pending widget") }
        }
    }
}
