package com.sidglassos.features.wallpapers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sin

/** Original artwork: layered pine silhouettes, a moonlit clearing and drifting motes. */
object ForestRenderer {
    /** One-shot rendering for static exports. Live wallpaper owns a reusable [ForestScene]. */
    fun draw(canvas: Canvas, width: Int, height: Int, timeSeconds: Float = 0f,
             particles: Int = 24, parallax: Float = 0f) {
        val scene = ForestScene()
        try { scene.drawOnce(canvas, width, height, timeSeconds, particles, parallax) }
        finally { scene.release() }
    }
}

/** Keeps the forest bitmap and drawing objects out of the live wallpaper's frame loop. */
internal class ForestScene {
    private var backdrop: Bitmap? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val source = Rect()
    private val target = Rect()
    private val path = Path()

    /** Export directly into the caller's bitmap, avoiding a second full-sized bitmap. */
    fun drawOnce(canvas: Canvas, width: Int, height: Int, seconds: Float, particles: Int, parallax: Float) {
        if (width <= 0 || height <= 0) return
        canvas.withTranslation(width * (-.028f + parallax.coerceIn(-1f, 1f) * .025f), 0f) {
            scale(1.056f, 1f)
            buildForest(this, width.toFloat(), height.toFloat())
        }
        drawParticles(canvas, width, height, seconds, particles)
    }

    fun draw(canvas: Canvas, width: Int, height: Int, seconds: Float, particles: Int, parallax: Float) {
        if (width <= 0 || height <= 0) return
        // A bounded texture keeps large wallpaper surfaces from consuming excessive RAM.
        val cacheWidth = width.coerceAtMost(1440)
        val cacheHeight = (height.toFloat() * cacheWidth / width).toInt().coerceIn(1, 3200)
        var base = backdrop
        if (base == null || base.width != cacheWidth || base.height != cacheHeight) {
            base?.recycle()
            base = createBitmap(cacheWidth, cacheHeight)
            buildForest(Canvas(base), cacheWidth.toFloat(), cacheHeight.toFloat())
            backdrop = base
        }
        paint.shader = null
        paint.alpha = 255
        source.set(0, 0, base.width, base.height)
        val offset = (parallax.coerceIn(-1f, 1f) * width * .025f).toInt()
        val overscan = (width * .028f).toInt()
        target.set(-overscan + offset, 0, width + overscan + offset, height)
        canvas.drawBitmap(base, source, target, paint)
        drawParticles(canvas, width, height, seconds, particles)
    }

    private fun drawParticles(canvas: Canvas, width: Int, height: Int, seconds: Float, particles: Int) {
        paint.shader = null
        paint.color = Color.rgb(205, 228, 255)
        for (index in 0 until particles.coerceIn(0, 80)) {
            val seed = index * 2.3999632f
            val x = ((index * .618034f + .09f * sin(seconds * .035f + seed)) % 1f + 1f) % 1f
            val y = ((index * .381966f - seconds * (.0018f + index % 4 * .0003f)) % 1f + 1f) % 1f
            paint.alpha = (35 + 85 * (.5f + .5f * sin(seconds * .32f + seed))).toInt()
            canvas.drawCircle(x * width, (.25f + y * .7f) * height,
                width * (.0007f + index % 3 * .00035f), paint)
        }
        paint.alpha = 255
    }

    private fun buildForest(canvas: Canvas, w: Float, h: Float) {
        paint.alpha = 255
        paint.shader = LinearGradient(0f, 0f, w * .25f, h,
            intArrayOf(0xFF0B1220.toInt(), 0xFF242348.toInt(), 0xFF314981.toInt(),
                0xFF356FA3.toInt(), 0xFF0B182B.toInt()),
            floatArrayOf(0f, .26f, .52f, .70f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = RadialGradient(w * .64f, h * .48f, w * .85f,
            intArrayOf(0x706CA5E8, 0x005A65AB), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        paint.color = 0xFFD6DDF7.toInt()
        paint.alpha = 145
        canvas.drawCircle(w * .69f, h * .265f, w * .026f, paint)
        paint.alpha = 255
        // Stars are deterministic and are generated only when the cache size changes.
        for (i in 0 until 56) {
            paint.color = Color.argb(35 + i % 5 * 13, 220, 229, 252)
            canvas.drawCircle(fract(i * .618034f) * w,
                fract(i * .7548777f) * h * .49f, w * (.0005f + i % 2 * .0003f), paint)
        }
        for (layer in 0..3) {
            paint.color = intArrayOf(0xFF364E7C.toInt(), 0xFF243B62.toInt(),
                0xFF172C4B.toInt(), 0xFF0A1B30.toInt())[layer]
            val count = 24 - layer * 3
            for (i in 0..count) {
                val x = (i.toFloat() / count * 1.12f - .06f) * w
                val treeHeight = h * (.14f + .12f * fract(i * .618034f + layer * .27f) + layer * .04f)
                val bottom = h * (.76f + layer * .085f)
                pine(canvas, x, bottom, treeHeight, treeHeight * .24f)
            }
            // Mist softens each layer without an expensive real-time blur.
            paint.shader = LinearGradient(0f, h * (.56f + layer * .08f), 0f,
                h * (.82f + layer * .065f), 0x003881C4, 0x223881C4, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, h * .5f, w, h, paint)
            paint.shader = null
        }
        // Strong foreground framing leaves the center open for clock and app labels.
        paint.color = 0xFF081423.toInt()
        pine(canvas, w * -.015f, h * 1.08f, h * .90f, w * .25f)
        pine(canvas, w * .12f, h * 1.05f, h * .57f, w * .20f)
        pine(canvas, w * 1.01f, h * 1.1f, h * .97f, w * .27f)
        paint.shader = LinearGradient(0f, h * .75f, 0f, h,
            0x00050D18, 0xD9050D18.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .75f, w, h, paint)
        paint.shader = null
    }

    private fun pine(canvas: Canvas, x: Float, bottom: Float, height: Float, halfWidth: Float) {
        path.reset()
        val top = bottom - height
        path.moveTo(x, top)
        for (branch in 1..9) {
            val fraction = branch / 10f
            val edge = halfWidth * fraction
            val y = top + height * fraction
            path.lineTo(x + edge * .55f, y - height * .045f)
            path.lineTo(x + edge, y + height * .075f)
            path.lineTo(x + edge * .36f, y + height * .055f)
        }
        path.lineTo(x + halfWidth * .12f, bottom)
        path.lineTo(x - halfWidth * .12f, bottom)
        for (branch in 9 downTo 1) {
            val fraction = branch / 10f
            val edge = halfWidth * fraction
            val y = top + height * fraction
            path.lineTo(x - edge * .36f, y + height * .055f)
            path.lineTo(x - edge, y + height * .075f)
            path.lineTo(x - edge * .55f, y - height * .045f)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    fun release() { backdrop?.recycle(); backdrop = null }
    private fun fract(value: Float): Float = value - kotlin.math.floor(value)
}

/** Static, off-main preview. It never starts a frame timer in the dashboard/launcher. */
@Composable
fun ForestBackdrop(modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(null) {
        value = withContext(Dispatchers.Default) {
            createBitmap(720, 1440).also {
                ForestRenderer.draw(Canvas(it), it.width, it.height)
            }
        }
    }
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    image?.let { Image(it, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop) }
}
