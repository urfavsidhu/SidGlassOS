package com.sidglassos.features.controlcenter

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

/** A user-added entry point into app UI, not a replacement system panel. */
class ControlCenterTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = "Sid Glass OS"
            subtitle = "Open Control Center"
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val launch = Runnable {
            runCatching {
                val intent = Intent().setClassName(packageName, "$packageName.DashboardActivity")
                    .putExtra("route", "controlcenter")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                if (Build.VERSION.SDK_INT >= 34) {
                    startActivityAndCollapse(PendingIntent.getActivity(
                        this, 4301, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    ))
                } else {
                    startOnLegacyAndroid(intent)
                }
            }.onFailure {
                Toast.makeText(this, "Open Sid Glass OS from your app drawer.", Toast.LENGTH_LONG).show()
            }
        }
        if (isLocked) unlockAndRun(launch) else launch.run()
    }

    // The PendingIntent overload was added in API 34. On API 29-33 the Intent
    // overload is the documented compatible API; lint flags it by target SDK
    // even behind our runtime guard. Keep this suppression local to that call.
    // https://developer.android.com/reference/android/service/quicksettings/TileService
    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun startOnLegacyAndroid(intent: Intent) {
        check(Build.VERSION.SDK_INT < 34) { "Use the PendingIntent tile API on Android 14 and newer." }
        startActivityAndCollapse(intent)
    }
}
