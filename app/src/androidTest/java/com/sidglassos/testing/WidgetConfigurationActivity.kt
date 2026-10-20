package com.sidglassos.testing

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView

/** Real second-package provider/configuration fixture, present only in the instrumentation APK. */
class ConfigurableWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, RemoteViews(context.packageName, com.sidglassos.test.R.layout.test_widget)) }
    }
}

class WidgetConfigurationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 80, 32, 32) }
        layout.addView(TextView(this).apply { text = "Configure test widget"; textSize = 24f })
        layout.addView(Button(this).apply {
            text = "Save test widget"
            setOnClickListener {
                AppWidgetManager.getInstance(this@WidgetConfigurationActivity).updateAppWidget(id,
                    RemoteViews(packageName, com.sidglassos.test.R.layout.test_widget))
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)); finish()
            }
        })
        layout.addView(Button(this).apply { text = "Cancel test widget"; setOnClickListener { finish() } })
        setContentView(layout)
    }
}
