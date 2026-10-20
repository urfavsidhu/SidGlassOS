package com.sidglassos.features.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator

data class LaunchableApp(val id: String, val label: String, val packageName: String, val iconVersion: String = "")

/** Current user's launchable activities only. No broad package or work-profile access. */
class AppCatalog(context: Context) {
    private val packageManager = context.applicationContext.packageManager
    private val icons = object : LruCache<String, Bitmap>(8 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    suspend fun query(): List<LaunchableApp> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
        val collator = Collator.getInstance()
        resolved.mapNotNull { result ->
            val activity = result.activityInfo ?: return@mapNotNull null
            if (!activity.enabled || !activity.applicationInfo.enabled) return@mapNotNull null
            val component = ComponentName(activity.packageName, activity.name)
            val label = try { result.loadLabel(packageManager).toString().ifBlank { activity.packageName } }
            catch (_: RuntimeException) { activity.packageName }
            LaunchableApp(component.flattenToString(), label, activity.packageName, activity.applicationInfo.sourceDir.orEmpty())
        }.distinctBy { it.id }.sortedWith { a, b ->
            collator.compare(a.label, b.label).takeIf { it != 0 } ?: a.id.compareTo(b.id)
        }
    }

    suspend fun icon(componentId: String, sizePx: Int, densityDpi: Int, iconVersion: String = ""): Bitmap? = withContext(Dispatchers.IO) {
        val size = sizePx.coerceIn(32, 384)
        val key = "$componentId:$size:$densityDpi:$iconVersion"
        icons.get(key)?.let { return@withContext it }
        val component = ComponentName.unflattenFromString(componentId) ?: return@withContext null
        try {
            val drawable = packageManager.getActivityIcon(component)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(Canvas(bitmap))
            icons.put(key, bitmap)
            bitmap
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: RuntimeException) {
            // A malformed third-party drawable must not take down the home activity.
            null
        }
    }

    fun invalidateIcons() = icons.evictAll()
    fun trimMemory() = icons.trimToSize(2 * 1024)
}
