package com.sidglassos.core.platform

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast

fun Context.openSafely(intent: Intent): Boolean = try {
    startActivity(intent.apply { if (this@openSafely !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    true
} catch (_: android.content.ActivityNotFoundException) {
    Toast.makeText(this, "This action is unavailable on this device.", Toast.LENGTH_LONG).show(); false
} catch (_: SecurityException) {
    Toast.makeText(this, "Android did not allow this action. Use system Settings.", Toast.LENGTH_LONG).show(); false
}

fun Activity.chooseHome() {
    val manager = getSystemService(RoleManager::class.java)
    if (manager.isRoleAvailable(RoleManager.ROLE_HOME) && !manager.isRoleHeld(RoleManager.ROLE_HOME)) {
        if (!openSafely(manager.createRequestRoleIntent(RoleManager.ROLE_HOME))) openSafely(Intent(Settings.ACTION_HOME_SETTINGS))
    } else openSafely(Intent(Settings.ACTION_HOME_SETTINGS))
}

fun deviceFacts(): String = "${Build.MANUFACTURER} ${Build.MODEL}\nAndroid ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}\nBuild ${Build.DISPLAY}"
