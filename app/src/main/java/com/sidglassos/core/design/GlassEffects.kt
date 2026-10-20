package com.sidglassos.core.design

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import android.os.PowerManager
import android.view.View
import android.view.ViewParent
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sidglassos.core.storage.GlassSettings
import com.sidglassos.features.wallpapers.ForestRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.function.Consumer

internal val LocalGlassRadius = staticCompositionLocalOf { 0f }
private data class Scene(val bitmap: Bitmap?, val size: IntSize, val origin: Offset)
private val LocalGlassScene = staticCompositionLocalOf<Scene?> { null }

@Composable
fun GlassRuntime(settings: GlassSettings, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    var saver by remember { mutableStateOf(power.isPowerSaveMode) }
    var accelerated by remember(view) { mutableStateOf(view.isHardwareAccelerated) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        saver = power.isPowerSaveMode
        accelerated = view.isHardwareAccelerated
    }
    DisposableEffect(context, power) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { saver = power.isPowerSaveMode }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    val radius = GlassEffectPolicy.blurRadiusPx(Build.VERSION.SDK_INT, accelerated, saver, settings.performance, LocalDensity.current.density)
    CompositionLocalProvider(LocalGlassRadius provides radius, content = content)
}

/** Shares one bounded original texture. Cards sample only this app-owned scene, never the screen. */
@Composable
fun GlassScene(settings: GlassSettings, content: @Composable BoxScope.() -> Unit) {
    val bitmap by produceState<Bitmap?>(null) {
        value = withContext(Dispatchers.Default) {
            createBitmap(720, 1440).also { ForestRenderer.draw(android.graphics.Canvas(it), it.width, it.height) }
        }
    }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    GlassRuntime(settings) {
        Box(Modifier.fillMaxSize().background(GlassColors.Navy).onGloballyPositioned {
            size = it.size; origin = it.positionInWindow()
        }) {
            bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Box(Modifier.fillMaxSize().background(GlassColors.Navy.copy(alpha = .35f)))
            CompositionLocalProvider(LocalGlassScene provides Scene(bitmap, size, origin)) { content() }
        }
    }
}

/** Blur only the background sibling. Text, controls and widget RemoteViews remain sharp. */
@Composable
internal fun GlassBackdropLayer(modifier: Modifier) {
    val scene = LocalGlassScene.current ?: return
    val bitmap = scene.bitmap ?: return
    val radius = LocalGlassRadius.current
    if (radius <= 0f || scene.size == IntSize.Zero) return
    var position by remember { mutableStateOf(Offset.Zero) }
    val paint = remember { android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG) }
    val effect = remember(radius) { BlurEffect(radius, radius, TileMode.Clamp) }
    Canvas(modifier.onGloballyPositioned { position = it.positionInWindow() }
        .graphicsLayer { renderEffect = effect }) {
        val mapping = BackdropMapping.crop(bitmap.width, bitmap.height, scene.size.width.toFloat(), scene.size.height.toFloat())
        val offset = position - scene.origin
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.translate(mapping.left - offset.x, mapping.top - offset.y)
            native.scale(mapping.scale, mapping.scale)
            native.drawBitmap(bitmap, 0f, 0f, paint)
            native.restore()
        }
        drawRect(GlassColors.Navy.copy(alpha = .25f))
    }
}

/** Public, capability-gated system blur behind this app's own modal window only. */
@Composable
fun ModalWindowBlur() {
    val view = LocalView.current
    val radius = LocalGlassRadius.current.toInt()
    var window by remember(view) { mutableStateOf(dialogWindow(view)) }
    DisposableEffect(view) {
        val attached = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { window = dialogWindow(v) }
            override fun onViewDetachedFromWindow(v: View) { window = null }
        }
        view.addOnAttachStateChangeListener(attached)
        window = dialogWindow(view)
        onDispose { view.removeOnAttachStateChangeListener(attached) }
    }
    DisposableEffect(window, radius) {
        val attachedWindow = window
        val cleanup = if (Build.VERSION.SDK_INT >= 31 && attachedWindow != null) installWindowBlur(attachedWindow, radius) else ({})
        onDispose { cleanup() }
    }
}

private fun dialogWindow(view: View): Window? {
    if (view is DialogWindowProvider) return view.window
    var parent: ViewParent? = view.parent
    while (parent != null) {
        if (parent is DialogWindowProvider) return parent.window
        parent = parent.parent
    }
    return null
}

@RequiresApi(31)
private fun installWindowBlur(window: Window, radius: Int): () -> Unit {
    val manager = window.context.getSystemService(WindowManager::class.java)
    val oldRadius = window.attributes.blurBehindRadius
    val oldFlag = window.attributes.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND
    val listener = Consumer<Boolean> { supported ->
        val active = supported && radius > 0
        window.attributes = window.attributes.apply { blurBehindRadius = if (active) radius else 0 }
        if (active) window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    }
    return try {
        manager.addCrossWindowBlurEnabledListener(window.context.mainExecutor, listener)
        val cleanup: () -> Unit = {
            manager.removeCrossWindowBlurEnabledListener(listener)
            window.attributes = window.attributes.apply { blurBehindRadius = oldRadius }
            if (oldFlag != 0) window.addFlags(oldFlag) else window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        }
        cleanup
    } catch (_: RuntimeException) { ({}) }
}
